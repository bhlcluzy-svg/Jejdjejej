package dev.xrayaudit;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;

/** Turns the counters into a Markdown report that ranks the anti-xray / anti-ESP rules that are failing. */
public final class ReportWriter {

    private record Rule(Category.Severity severity, String world, String title, String detail, String fix) {}

    private final Stats stats;
    private final ServerConfigInspector inspector;
    private final Function<World, ServerConfigInspector.AntiXray> antiXray;

    public ReportWriter(Stats stats, ServerConfigInspector inspector, Function<World, ServerConfigInspector.AntiXray> antiXray) {
        this.stats = stats;
        this.inspector = inspector;
        this.antiXray = antiXray;
    }

    /** Must run on the main thread (reads the world list and plugin list). */
    public String build(AuditConfig cfg, int auditedOnline, int queueSize, String findingsFile) {
        List<Rule> rules = new ArrayList<>();
        StringBuilder body = new StringBuilder();

        List<String> antiXrayPlugins = new ArrayList<>();
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            String n = p.getName().toLowerCase(Locale.ROOT);
            if (p.isEnabled() && (n.contains("xray") && !n.equals("xrayaudit") || n.contains("orebfuscator"))) {
                antiXrayPlugins.add(p.getName() + " " + p.getPluginMeta().getVersion());
            }
        }

        for (World world : Bukkit.getWorlds()) {
            String w = world.getName();
            if (!cfg.auditsWorld(w)) continue;
            if (stats.get("chunk.sampled", w) == 0 && stats.withPrefix("cat", Category.ENTITY_NO_LOS.name(), w).isEmpty()
                    && stats.withPrefix("update.target-sent", w).isEmpty()) continue;
            worldSection(cfg, world, rules, body, antiXrayPlugins);
        }

        rules.sort(Comparator.comparing(Rule::severity));
        StringBuilder out = new StringBuilder();
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        Duration span = Duration.ofMillis(System.currentTimeMillis() - stats.startedAt());
        out.append("# XrayAudit report\n\n")
                .append("- Generated: ").append(now).append('\n')
                .append("- Audit window: ").append(span.toHours()).append("h ").append(span.toMinutesPart()).append("m\n")
                .append("- Audited players online: ").append(auditedOnline)
                .append(cfg.permissionMode ? " (mode: permission `xrayaudit.audited`)" : " (mode: all players)").append('\n')
                .append("- Other anti-xray plugins enabled: ").append(antiXrayPlugins.isEmpty() ? "none" : String.join(", ", antiXrayPlugins)).append('\n')
                .append("- Detailed findings: `").append(findingsFile).append("`\n\n");

        out.append("## Failing rules\n\n");
        if (rules.isEmpty()) {
            out.append("Nothing failing in the data collected so far. Check the sample counts below before trusting this — "
                    + "a quiet report with few samples means little.\n\n");
        } else {
            int i = 1;
            for (Rule r : rules) {
                out.append(i++).append(". **[").append(r.severity()).append("] ").append(r.world()).append(": ")
                        .append(r.title()).append("**\n   ").append(r.detail()).append("\n   _Fix:_ ").append(r.fix()).append("\n\n");
            }
        }
        out.append(body);
        pipelineSection(out, queueSize);
        out.append("## Not measured by this version\n\n")
                .append("- **Light data** in chunk and light-update packets outlines caves even when every ore is hidden. Palette-based anti-xray cannot fix this.\n")
                .append("- **Heightmaps and biomes** are sent unchanged.\n")
                .append("- **Seed-derived structure locations**: change the feature/structure seeds in spigot.yml / paper-world configs to defeat seed crackers.\n");
        return out.toString();
    }

    private void worldSection(AuditConfig cfg, World world, List<Rule> rules, StringBuilder body, List<String> otherPlugins) {
        String w = world.getName();
        ServerConfigInspector.AntiXray ax = antiXray.apply(world);
        body.append("## World `").append(w).append("`\n\n");
        if (ax.paperConfigFound) {
            body.append("Paper anti-xray: enabled=").append(ax.enabled).append(", engine-mode=").append(ax.engineMode)
                    .append(", max-block-height=").append(ax.maxBlockHeight).append(", update-radius=").append(ax.updateRadius)
                    .append(", lava-obscures=").append(ax.lavaObscures).append(", use-permission=").append(ax.usePermission)
                    .append("\n\n");
        } else {
            body.append("Paper world config not found (`config/paper-world-defaults.yml`) — not a Paper server, or a non-standard layout.\n\n");
        }
        if (ax.usePermission) {
            rules.add(new Rule(Category.Severity.WARN, w, "use-permission is true",
                    "Players with `paper.antixray.bypass` (every op) receive unobfuscated chunks, so their sessions show leaks that regular players would not get.",
                    "Audit with a non-op account (`audit.mode: permission` plus `xrayaudit.audited`), or set `use-permission: false`."));
        }

        // ---- chunk palette leaks
        long sampled = stats.get("chunk.sampled", w);
        long buriedNonTarget = stats.get("chunk.buried-non-target", w);
        Map<String, Long> buried = stats.withPrefix("chunk.buried", w);
        body.append("### Chunk packets\n\n").append("Chunks compared: ").append(sampled)
                .append(", buried non-target blocks examined: ").append(buriedNonTarget)
                .append(", target blocks skipped at unloaded chunk edges: ").append(stats.get("chunk.unknown-edge", w)).append("\n\n");
        if (!buried.isEmpty()) {
            body.append("| Block | Buried samples | Sent as itself | Leak rate | Decoy rate | Excess | In hidden-blocks | Leak Y range | Verdict |\n")
                    .append("|---|---:|---:|---:|---:|---:|---|---|---|\n");
            Map<String, Double> leaking = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Long> e : buried.entrySet()) {
                String block = e.getKey();
                long n = e.getValue();
                long leaked = stats.get("chunk.leaked", w, block);
                double leakRate = n == 0 ? 0 : (double) leaked / n;
                double decoyRate = buriedNonTarget == 0 ? 0 : (double) stats.get("chunk.decoy", w, block) / buriedNonTarget;
                double excess = leakRate - decoyRate;
                boolean hidden = ax.hiddenBlocks.contains(block);
                Integer minY = stats.getMin("chunk.leak-min-y", w, block), maxY = stats.getMax("chunk.leak-max-y", w, block);
                boolean failing = n >= cfg.minSamples && excess > cfg.leakThreshold && significant(leaked, n, decoyRate);
                String verdict;
                if (n < cfg.minSamples) verdict = "too few samples";
                else if (failing) verdict = "**LEAKING**";
                else verdict = "ok";
                body.append("| ").append(block).append(" | ").append(n).append(" | ").append(leaked).append(" | ")
                        .append(pct(leakRate)).append(" | ").append(pct(decoyRate)).append(" | ").append(pct(excess)).append(" | ")
                        .append(ax.paperConfigFound ? (hidden ? "yes" : "no") : "n/a").append(" | ")
                        .append(minY == null ? "-" : minY + ".." + maxY).append(" | ").append(verdict).append(" |\n");
                if (failing) {
                    leaking.put(block, leakRate);
                    if (ax.paperConfigFound && ax.enabled) {
                        rules.add(chunkRule(w, block, leakRate, decoyRate, n, hidden, ax));
                    }
                }
            }
            if (!leaking.isEmpty() && !(ax.paperConfigFound && ax.enabled)) {
                rules.add(noAntiXrayRule(w, leaking, ax, otherPlugins));
            }
            body.append("\nLeak rate = buried blocks of this type sent as themselves. Decoy rate = buried non-target blocks sent as this type "
                    + "(the fake-ore background with engine-mode 2/3). Only the excess over the decoy rate is real leakage, and a block is "
                    + "only marked LEAKING when the excess is above `report.leak-threshold` and more than 3 standard deviations above chance.\n\n");
        }
        highBandSection(w, ax, rules, body);

        // ---- block updates
        Map<String, Long> beyond = stats.withPrefix("cat", Category.UPDATE_LEAK_BEYOND_RADIUS.name(), w);
        Map<String, Long> within = stats.withPrefix("cat", Category.UPDATE_REVEAL_WITHIN_RADIUS.name(), w);
        Map<String, Long> updatesSent = stats.withPrefix("update.target-sent", w);
        body.append("### Block updates\n\n").append("Target-block updates sent: ").append(sum(updatesSent))
                .append(". Revealed buried blocks within update-radius: ").append(sum(within))
                .append(". Revealed beyond update-radius: ").append(sum(beyond)).append(".\n\n");
        if (!beyond.isEmpty()) {
            body.append(table("Block", "Revealed beyond radius", beyond));
            Map<String, Long> by = stats.withPrefix("update.revealed-by", w, Category.UPDATE_LEAK_BEYOND_RADIUS.name());
            rules.add(new Rule(Category.Severity.FAIL, w, "block updates reveal buried blocks beyond update-radius",
                    sum(beyond) + " reveals (" + top(beyond, 5) + "), via " + top(by, 3) + ". These come from code paths that send block "
                            + "changes without going through anti-xray's neighbour-update hook: plugins calling setBlock/sendBlockChange, "
                            + "world edits, or physics such as explosions, pistons and falling blocks.",
                    "Open the findings file, filter on `UPDATE_LEAK_BEYOND_RADIUS`, and look for what happened at those coordinates "
                            + "(CoreProtect lookups help). Plugins that send fake blocks to players must skip hidden-blocks."));
        }
        if (!within.isEmpty()) {
            rules.add(new Rule(Category.Severity.INFO, w, "update-radius reveals buried blocks near every change",
                    sum(within) + " buried target blocks were revealed because something changed within update-radius=" + ax.updateRadius
                            + ". This is designed behaviour, and it is how chunk finders and 'dig a tunnel, see ores in the walls' clients still win.",
                    "Set `update-radius: 1` if the server can afford the extra obfuscation work. Smaller values hide more, at some CPU cost."));
        }

        // ---- block entities
        Map<String, Long> be = stats.withPrefix("cat", Category.BLOCK_ENTITY_LEAK.name(), w);
        body.append("### Block entities\n\n").append("Block entities in sampled chunks: ")
                .append(sum(stats.withPrefix("chunk.block-entities", w))).append(". Sent for fully buried positions: ")
                .append(sum(be)).append(".\n\n");
        if (!be.isEmpty()) {
            body.append(table("Block", "Buried block entities sent", be));
            rules.add(new Rule(Category.Severity.FAIL, w, "buried block entities are sent to clients",
                    sum(be) + " buried block entities sent (" + top(be, 5) + "). Even when the block is obfuscated in the palette, "
                            + "the block-entity list and block_entity_data packets still give the type and position away. "
                            + "This is how spawner and stash finders work.",
                    "Make sure these blocks are in `hidden-blocks`, then re-run the audit. If they still show up, the packet itself has to be filtered. "
                            + "That needs a packet-rewriting anti-xray such as RayTraceAntiXray or Orebfuscator (this plugin only observes)."));
        }

        // ---- chunk distance
        Map<String, Long> far = stats.withPrefix("cat", Category.CHUNK_BEYOND_VIEW.name(), w);
        if (!far.isEmpty()) {
            rules.add(new Rule(Category.Severity.WARN, w, "chunks sent beyond send-view-distance",
                    sum(far) + " sampled chunk packets were for chunks more than send-view-distance+2 from the player when checked. "
                            + "Some of these are teleports or fast travel. A steady stream points to a plugin or proxy sending chunks early.",
                    "Check the findings file for repeated players and coordinates. Verify `chunks.send-view-distance` / `view-distance` in the configs."));
        }

        // ---- entities
        Map<String, Long> entChecked = stats.withPrefix("entity.checked", w);
        Map<String, Long> entHidden = stats.withPrefix("cat", Category.ENTITY_NO_LOS.name(), w);
        body.append("### Entity spawns (ESP)\n\n").append("Spawns checked: ").append(sum(entChecked))
                .append(", farther than ").append(cfg.entityMinDistance).append(" blocks with no line of sight: ").append(sum(entHidden)).append(".\n\n");
        if (!entHidden.isEmpty()) {
            body.append(ratioTable("Entity", entChecked, entHidden));
            Map<String, Integer> ranges = inspector.trackingRanges(world);
            rules.add(new Rule(Category.Severity.WARN, w, "entities are sent without line of sight",
                    sum(entHidden) + " of " + sum(entChecked) + " sampled spawns (" + top(entHidden, 5) + ") reached players who could not see them. "
                            + "ESP clients draw these through walls. The server tracks entities by distance, not visibility. "
                            + "Current entity-tracking-range: " + (ranges.isEmpty() ? "unknown" : ranges) + ".",
                    "Lower `entity-tracking-range` in spigot.yml for the types that matter (players, monsters). "
                            + "For player ESP specifically, an occlusion-culling plugin that hides players with no line of sight is the real fix."));
        }

        // ---- sounds
        Map<String, Long> sndChecked = stats.withPrefix("sound.checked", w);
        Map<String, Long> sndHidden = stats.withPrefix("cat", Category.SOUND_NO_LOS.name(), w);
        body.append("### Positional sounds\n\n").append("Sounds checked: ").append(sum(sndChecked))
                .append(", farther than ").append(cfg.soundMinDistance).append(" blocks with no line of sight: ").append(sum(sndHidden)).append(".\n\n");
        if (!sndHidden.isEmpty()) {
            body.append(table("Sound", "Sent without line of sight", sndHidden));
            rules.add(new Rule(Category.Severity.WARN, w, "positional sounds locate hidden players and bases",
                    sum(sndHidden) + " sounds (" + top(sndHidden, 5) + ") came with exact coordinates from places the player could not see. "
                            + "Block-break, door, chest and anvil sounds point straight at hidden activity.",
                    "Reduce how far these sounds are broadcast (a sound-range or sound-filter plugin), or round/offset their coordinates at the packet level."));
        }

        // ---- global events
        Map<String, Long> global = stats.withPrefix("cat", Category.GLOBAL_WORLD_EVENT.name(), w);
        if (!global.isEmpty()) {
            body.append("### Global world events\n\n").append(table("Event type id", "Broadcasts", global));
            rules.add(new Rule(Category.Severity.INFO, w, "global world events broadcast coordinates",
                    sum(global) + " global events (" + top(global, 5) + "). Ids 1023/1028/1038 are the wither spawn, the dragon death and an end portal opening. "
                            + "They are sent to every player with the real position.",
                    "Usually acceptable. If it matters on this server, the EFFECT packets flagged global have to be filtered or re-positioned at the packet level."));
        }
    }

    /** One rule for a world where no anti-xray is active at all, instead of one per block type. */
    private static Rule noAntiXrayRule(String w, Map<String, Double> leaking, ServerConfigInspector.AntiXray ax,
                                       List<String> otherPlugins) {
        List<String> parts = new ArrayList<>();
        leaking.forEach((b, r) -> parts.add(b + " " + pct(r)));
        String stat = leaking.size() + " block types leak in chunk packets (" + String.join(", ", parts) + ")";
        if (!ax.paperConfigFound) {
            return new Rule(Category.Severity.FAIL, w, "buried blocks are sent unobfuscated", stat + ". No Paper anti-xray config was found"
                    + (otherPlugins.isEmpty() ? " and no anti-xray plugin is enabled." : "; the enabled anti-xray plugin is not hiding them."),
                    otherPlugins.isEmpty() ? "Run Paper and enable `anticheat.anti-xray`, or install an anti-xray plugin."
                            : "Add these blocks to the hidden list of " + String.join(", ", otherPlugins) + " and check that it covers this world.");
        }
        return new Rule(Category.Severity.FAIL, w, "anti-xray is disabled", stat + ".",
                "Set `anticheat.anti-xray.enabled: true` in `config/paper-world-defaults.yml` or `" + w + "/paper-world.yml`, "
                        + "and add any block marked 'no' under 'In hidden-blocks' to `hidden-blocks`.");
    }

    private static Rule chunkRule(String w, String block, double leakRate, double decoyRate, long n,
                                  boolean hidden, ServerConfigInspector.AntiXray ax) {
        String stat = pct(leakRate) + " of " + n + " buried " + block + " were sent as themselves (decoy baseline " + pct(decoyRate) + ")";
        if (!hidden) {
            return new Rule(Category.Severity.FAIL, w, block + " is not in hidden-blocks", stat + ".",
                    "Add `" + block + "` to `anticheat.anti-xray.hidden-blocks`.");
        }
        return new Rule(Category.Severity.FAIL, w, block + " is configured as hidden but still leaks", stat + ".",
                "Likely causes: audited players bypass anti-xray (use-permission=" + ax.usePermission + "), another plugin re-sends "
                        + "chunks without obfuscation, or engine-mode " + ax.engineMode + " uses replacement blocks that do not cover this "
                        + "context. Use the findings file to see whether the leaks cluster on one player or one region.");
    }

    /** Target blocks above max-block-height, where Paper does not obfuscate at all. */
    private void highBandSection(String w, ServerConfigInspector.AntiXray ax, List<Rule> rules, StringBuilder body) {
        Map<String, Long> buried = stats.withPrefix("chunk.high.buried", w);
        if (buried.isEmpty()) return;
        body.append("### Above max-block-height (Y ≥ ").append(ax.maxBlockHeight).append(")\n\n")
                .append("| Block | Buried samples | Sent as itself | Leak Y range |\n|---|---:|---:|---|\n");
        List<String> parts = new ArrayList<>();
        long total = 0;
        int top = Integer.MIN_VALUE;
        for (Map.Entry<String, Long> e : buried.entrySet()) {
            String block = e.getKey();
            long leaked = stats.get("chunk.high.leaked", w, block);
            Integer minY = stats.getMin("chunk.high.leak-min-y", w, block), maxY = stats.getMax("chunk.high.leak-max-y", w, block);
            body.append("| ").append(block).append(" | ").append(e.getValue()).append(" | ").append(leaked).append(" | ")
                    .append(minY == null ? "-" : minY + ".." + maxY).append(" |\n");
            if (leaked > 0) {
                total += leaked;
                parts.add(block + " ×" + leaked);
                top = Math.max(top, maxY);
            }
        }
        body.append('\n');
        if (total == 0) return;
        int suggested = (Math.floorDiv(top, 16) + 1) * 16;
        rules.add(new Rule(Category.Severity.FAIL, w, "ores above max-block-height are not obfuscated",
                total + " buried target blocks at Y ≥ " + ax.maxBlockHeight + " were sent as themselves (" + String.join(", ", parts)
                        + "), the highest at Y=" + top + ". Paper does not obfuscate anything above max-block-height.",
                "Set `anticheat.anti-xray.max-block-height: " + suggested + "` (a multiple of 16 above Y=" + top + "). "
                        + "Higher values cost more CPU per chunk sent."));
    }

    /** One-sided check that leaked is more than 3 standard deviations above what the decoy rate alone would produce. */
    private static boolean significant(long leaked, long n, double decoyRate) {
        double expected = n * decoyRate;
        double sd = Math.sqrt(Math.max(n * decoyRate * (1 - decoyRate), 1.0));
        return leaked - expected > 3 * sd;
    }

    private void pipelineSection(StringBuilder out, int queueSize) {
        out.append("## Pipeline health\n\n").append("| Packet | Seen | Queued | Dropped (queue full) | Decode errors |\n|---|---:|---:|---:|---:|\n");
        Map<String, Long> seen = stats.withPrefix("packets.seen");
        for (String p : new TreeSet<>(seen.keySet())) {
            out.append("| ").append(p).append(" | ").append(seen.get(p)).append(" | ").append(stats.get("packets.queued", p))
                    .append(" | ").append(stats.get("packets.dropped", p)).append(" | ").append(stats.get("packets.decode-error", p)).append(" |\n");
        }
        Map<String, Long> errors = stats.withPrefix("check.error");
        out.append("\nQueue depth now: ").append(queueSize).append(". Check errors: ").append(errors.isEmpty() ? "none" : errors).append(".\n")
                .append("Dropped observations mean the sample rates are higher than the server can check. Lower them in config.yml.\n\n");
    }

    private static String table(String col, String countCol, Map<String, Long> m) {
        StringBuilder b = new StringBuilder("| ").append(col).append(" | ").append(countCol).append(" |\n|---|---:|\n");
        sorted(m).forEach(e -> b.append("| ").append(e.getKey()).append(" | ").append(e.getValue()).append(" |\n"));
        return b.append('\n').toString();
    }

    private static String ratioTable(String col, Map<String, Long> total, Map<String, Long> hit) {
        StringBuilder b = new StringBuilder("| ").append(col).append(" | Checked | No line of sight | Share |\n|---|---:|---:|---:|\n");
        sorted(hit).forEach(e -> {
            long t = total.getOrDefault(e.getKey(), 0L);
            b.append("| ").append(e.getKey()).append(" | ").append(t).append(" | ").append(e.getValue()).append(" | ")
                    .append(t == 0 ? "-" : pct((double) e.getValue() / t)).append(" |\n");
        });
        return b.append('\n').toString();
    }

    private static List<Map.Entry<String, Long>> sorted(Map<String, Long> m) {
        List<Map.Entry<String, Long>> l = new ArrayList<>(m.entrySet());
        l.sort(Map.Entry.<String, Long>comparingByValue().reversed());
        return l;
    }

    private static String top(Map<String, Long> m, int n) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Long> e : sorted(m)) {
            if (parts.size() == n) break;
            parts.add(e.getKey() + " ×" + e.getValue());
        }
        return parts.isEmpty() ? "-" : String.join(", ", parts);
    }

    private static long sum(Map<String, Long> m) {
        long s = 0;
        for (long v : m.values()) s += v;
        return s;
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.1f%%", v * 100);
    }
}
