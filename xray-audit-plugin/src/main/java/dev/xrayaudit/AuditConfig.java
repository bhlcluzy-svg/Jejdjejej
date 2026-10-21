package dev.xrayaudit;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/** Immutable snapshot of config.yml. Replaced wholesale on reload so network threads never see a half-updated config. */
public final class AuditConfig {

    public final boolean enabled;
    public final boolean permissionMode;
    public final Set<String> worlds;

    public final Set<Material> targets;
    /** Target material keys ("diamond_ore") for matching against decoded packet state names. */
    public final Set<String> targetKeys;

    public final double chunkSampleRate;
    public final int maxSnapshotsPerTick;
    /** null = follow Paper's lava-obscures for each world. */
    public final Boolean lavaOccludes;

    public final boolean updatesEnabled;
    public final int exposureSearchRadius;

    public final boolean entitiesEnabled;
    public final double entitySampleRate;
    public final double entityMinDistance;

    public final boolean soundsEnabled;
    public final double soundSampleRate;
    public final double soundMinDistance;

    public final int maxQueue;
    public final int maxChecksPerTick;

    public final int reportIntervalMinutes;
    public final int minSamples;
    public final double leakThreshold;
    public final int maxDetailLinesPerCategory;

    public AuditConfig(FileConfiguration c, Logger log) {
        enabled = c.getBoolean("enabled", true);
        permissionMode = "permission".equalsIgnoreCase(c.getString("audit.mode", "all"));
        Set<String> w = new HashSet<>();
        for (String s : c.getStringList("audit.worlds")) w.add(s);
        worlds = Collections.unmodifiableSet(w);

        Set<Material> t = EnumSet.noneOf(Material.class);
        Set<String> tk = new HashSet<>();
        for (String s : c.getStringList("targets")) {
            Material m = Material.matchMaterial(s);
            if (m == null || !m.isBlock()) {
                log.warning("Unknown target block in config: " + s);
                continue;
            }
            t.add(m);
            tk.add(m.getKey().getKey());
        }
        targets = Collections.unmodifiableSet(t);
        targetKeys = Collections.unmodifiableSet(tk);

        chunkSampleRate = clamp01(c.getDouble("chunks.sample-rate", 0.25));
        maxSnapshotsPerTick = Math.max(1, c.getInt("chunks.max-snapshots-per-tick", 2));
        String lava = String.valueOf(c.get("chunks.lava-occludes", "auto")).toLowerCase(Locale.ROOT);
        lavaOccludes = lava.equals("true") ? Boolean.TRUE : lava.equals("false") ? Boolean.FALSE : null;

        updatesEnabled = c.getBoolean("updates.enabled", true);
        exposureSearchRadius = Math.max(1, Math.min(8, c.getInt("updates.exposure-search-radius", 4)));

        entitiesEnabled = c.getBoolean("entities.enabled", true);
        entitySampleRate = clamp01(c.getDouble("entities.sample-rate", 0.5));
        entityMinDistance = c.getDouble("entities.min-distance", 16.0);

        soundsEnabled = c.getBoolean("sounds.enabled", true);
        soundSampleRate = clamp01(c.getDouble("sounds.sample-rate", 0.5));
        soundMinDistance = c.getDouble("sounds.min-distance", 24.0);

        maxQueue = Math.max(16, c.getInt("limits.max-queue", 512));
        maxChecksPerTick = Math.max(1, c.getInt("limits.max-checks-per-tick", 200));

        reportIntervalMinutes = Math.max(0, c.getInt("report.interval-minutes", 10));
        minSamples = Math.max(1, c.getInt("report.min-samples", 20));
        leakThreshold = c.getDouble("report.leak-threshold", 0.05);
        maxDetailLinesPerCategory = Math.max(0, c.getInt("report.max-detail-lines-per-category", 2000));
    }

    public boolean auditsWorld(String world) {
        return worlds.isEmpty() || worlds.contains(world);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
