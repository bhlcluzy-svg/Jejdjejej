package dev.xrayaudit;

import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads the effective anti-xray and entity-tracking settings for a world from Paper's and Spigot's config files,
 * so the report can say which rule is responsible for a leak. Read-only.
 */
public final class ServerConfigInspector {

    private static final String AX = "anticheat.anti-xray.";

    public static final class AntiXray {
        public boolean paperConfigFound;
        public boolean enabled;
        public int engineMode = 1;
        public int maxBlockHeight = 64;
        public int updateRadius = 2;
        public boolean lavaObscures;
        public boolean usePermission;
        public Set<String> hiddenBlocks = new HashSet<>();
        public Set<String> replacementBlocks = new HashSet<>();
    }

    private final File serverRoot;

    public ServerConfigInspector(File serverRoot) {
        this.serverRoot = serverRoot;
    }

    public AntiXray antiXray(World world) {
        AntiXray ax = new AntiXray();
        File defaults = new File(serverRoot, "config/paper-world-defaults.yml");
        if (!defaults.isFile()) return ax;
        ax.paperConfigFound = true;
        apply(ax, YamlConfiguration.loadConfiguration(defaults));
        File perWorld = new File(world.getWorldFolder(), "paper-world.yml");
        if (perWorld.isFile()) apply(ax, YamlConfiguration.loadConfiguration(perWorld));
        return ax;
    }

    /** Applies values present in y; the literal "default" (Paper's per-world placeholder) is ignored. */
    private static void apply(AntiXray ax, YamlConfiguration y) {
        if (set(y, AX + "enabled")) ax.enabled = y.getBoolean(AX + "enabled");
        if (set(y, AX + "engine-mode")) ax.engineMode = y.getInt(AX + "engine-mode");
        if (set(y, AX + "max-block-height")) ax.maxBlockHeight = y.getInt(AX + "max-block-height");
        if (set(y, AX + "update-radius")) ax.updateRadius = y.getInt(AX + "update-radius");
        if (set(y, AX + "lava-obscures")) ax.lavaObscures = y.getBoolean(AX + "lava-obscures");
        if (set(y, AX + "use-permission")) ax.usePermission = y.getBoolean(AX + "use-permission");
        if (set(y, AX + "hidden-blocks")) ax.hiddenBlocks = keys(y.getStringList(AX + "hidden-blocks"));
        if (set(y, AX + "replacement-blocks")) ax.replacementBlocks = keys(y.getStringList(AX + "replacement-blocks"));
    }

    private static boolean set(YamlConfiguration y, String path) {
        if (!y.contains(path)) return false;
        Object v = y.get(path);
        return v != null && !"default".equals(String.valueOf(v));
    }

    private static Set<String> keys(List<String> names) {
        Set<String> out = new HashSet<>();
        for (String n : names) {
            String k = n.toLowerCase(Locale.ROOT);
            int colon = k.indexOf(':');
            out.add(colon >= 0 ? k.substring(colon + 1) : k);
        }
        return out;
    }

    /** spigot.yml entity-tracking-range for the world (falls back to world-settings.default). */
    public Map<String, Integer> trackingRanges(World world) {
        Map<String, Integer> out = new LinkedHashMap<>();
        File f = new File(serverRoot, "spigot.yml");
        if (!f.isFile()) return out;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        for (String type : new String[]{"players", "animals", "monsters", "misc", "display", "other"}) {
            String own = "world-settings." + world.getName() + ".entity-tracking-range." + type;
            String def = "world-settings.default.entity-tracking-range." + type;
            if (y.contains(own)) out.put(type, y.getInt(own));
            else if (y.contains(def)) out.put(type, y.getInt(def));
        }
        return out;
    }
}
