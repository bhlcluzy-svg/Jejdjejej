package dev.xrayaudit;

import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public final class XrayAuditPlugin extends JavaPlugin implements TabExecutor {

    private final AtomicReference<AuditConfig> config = new AtomicReference<>();
    private final ConcurrentHashMap<String, ServerConfigInspector.AntiXray> antiXrayCache = new ConcurrentHashMap<>();
    private final Stats stats = new Stats();

    private ServerConfigInspector inspector;
    private FindingLog findings;
    private AuditedPlayers audited;
    private TruthChecker checker;
    private ReportWriter reports;
    private PacketTap tap;
    private BukkitTask reportTask;
    private File reportDir;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings()
                .reEncodeByDefault(false)   // read-only: never re-encode what other plugins or Paper produced
                .checkForUpdates(false)
                .bStats(false)
                .kickOnPacketException(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reportDir = new File(getDataFolder(), "reports");
        reportDir.mkdirs();
        config.set(new AuditConfig(getConfig(), getLogger()));

        // Paper's config/ directory and spigot.yml live in the server's working directory.
        inspector = new ServerConfigInspector(new File(".").getAbsoluteFile());
        findings = new FindingLog(reportDir, getLogger(), config.get().maxDetailLinesPerCategory);
        audited = new AuditedPlayers(config::get);
        checker = new TruthChecker(config::get, stats, findings, this::antiXray);
        reports = new ReportWriter(stats, inspector, this::antiXray);
        tap = new PacketTap(config::get, audited, checker, stats);

        PacketEvents.getAPI().getEventManager().registerListener(tap);
        PacketEvents.getAPI().init();

        Bukkit.getScheduler().runTaskTimer(this, audited, 1L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, checker, 2L, 1L);
        scheduleReports();

        var cmd = getCommand("xrayaudit");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        getLogger().info("Auditing outgoing packets. Reports go to " + reportDir.getPath());
    }

    @Override
    public void onDisable() {
        if (reports != null) {
            try {
                writeReport(false);
            } catch (RuntimeException e) {
                getLogger().log(Level.WARNING, "Could not write the final report", e);
            }
        }
        if (checker != null) checker.shutdown();
        if (findings != null) findings.close();
        PacketEvents.getAPI().terminate();
    }

    /** Cached per world; cleared on reload so edits to Paper's config are picked up. */
    ServerConfigInspector.AntiXray antiXray(World world) {
        return antiXrayCache.computeIfAbsent(world.getName(), n -> inspector.antiXray(world));
    }

    private void scheduleReports() {
        if (reportTask != null) reportTask.cancel();
        reportTask = null;
        int minutes = config.get().reportIntervalMinutes;
        if (minutes > 0) {
            long ticks = minutes * 60L * 20L;
            reportTask = Bukkit.getScheduler().runTaskTimer(this, () -> writeReport(true), ticks, ticks);
        }
    }

    /** Builds on the calling (main) thread; writes asynchronously unless async is false. Returns the report path. */
    private File writeReport(boolean async) {
        String text = reports.build(config.get(), audited.size(), checker.queueSize(),
                getDataFolder().toPath().relativize(findings.file().toPath()).toString());
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File dated = new File(reportDir, "report-" + stamp + ".md");
        File latest = new File(reportDir, "latest.md");
        Runnable write = () -> {
            try {
                Files.writeString(dated.toPath(), text, StandardCharsets.UTF_8);
                Files.writeString(latest.toPath(), text, StandardCharsets.UTF_8);
            } catch (IOException e) {
                getLogger().log(Level.WARNING, "Could not write report", e);
            }
        };
        if (async) Bukkit.getScheduler().runTaskAsynchronously(this, write);
        else write.run();
        return dated;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase();
        switch (sub) {
            case "status" -> {
                AuditConfig cfg = config.get();
                sender.sendMessage("§6XrayAudit§r " + (cfg.enabled ? "§aauditing" : "§cpaused")
                        + "§r — audited players online: " + audited.size() + ", queue: " + checker.queueSize());
                for (World w : Bukkit.getWorlds()) {
                    long sampled = stats.get("chunk.sampled", w.getName());
                    if (sampled == 0) continue;
                    long buried = stats.withPrefix("chunk.buried", w.getName()).values().stream().mapToLong(Long::longValue).sum();
                    long leaked = stats.withPrefix("chunk.leaked", w.getName()).values().stream().mapToLong(Long::longValue).sum();
                    sender.sendMessage(" §7" + w.getName() + "§r: " + sampled + " chunks compared, "
                            + leaked + "/" + buried + " buried targets sent as themselves");
                }
                sender.sendMessage(" Run §e/xrayaudit report§r for the full breakdown and failing rules.");
            }
            case "report" -> {
                File f = writeReport(true);
                sender.sendMessage("§6XrayAudit§r report written to " + f.getPath() + " (and reports/latest.md).");
            }
            case "reset" -> {
                stats.reset();
                findings.resetCaps();
                sender.sendMessage("§6XrayAudit§r counters reset.");
            }
            case "reload" -> {
                reloadConfig();
                config.set(new AuditConfig(getConfig(), getLogger()));
                antiXrayCache.clear();
                findings.setCap(config.get().maxDetailLinesPerCategory);
                scheduleReports();
                audited.run();
                sender.sendMessage("§6XrayAudit§r config reloaded (Paper anti-xray settings re-read).");
            }
            case "on", "off" -> {
                getConfig().set("enabled", sub.equals("on"));
                saveConfig();
                config.set(new AuditConfig(getConfig(), getLogger()));
                sender.sendMessage("§6XrayAudit§r " + (sub.equals("on") ? "auditing" : "paused") + ".");
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        return List.of("status", "report", "reset", "reload", "on", "off").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
    }
}
