package dev.xrayaudit;

import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Compares observations against the real world ("truth").
 *
 * <p>Runs as a once-per-tick main-thread task: it drains a bounded queue, does cheap single-block and line-of-sight
 * checks inline, and for chunk packets takes {@link ChunkSnapshot}s (the chunk plus its four neighbours, for
 * border occlusion checks) and hands the heavy per-block comparison to a background thread.
 *
 * <p>Definitions used throughout:
 * <ul>
 *   <li><b>buried</b>: all six neighbours are occluding in the real world, so no legitimate client can see the block.
 *       Exposed blocks are skipped — revealing them is correct.</li>
 *   <li><b>leak</b>: a buried target block whose sent state is its real type.</li>
 *   <li><b>decoy</b>: a buried non-target block sent as a target type (engine-mode 2/3 working). The decoy rate is the
 *       baseline a leak rate is judged against, because random fake ores land on real ores by chance too.</li>
 * </ul>
 */
public final class TruthChecker implements Runnable {

    private final Queue<Observation> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queued = new AtomicInteger();
    private final ExecutorService analysis = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "XrayAudit-Analysis");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private final Supplier<AuditConfig> config;
    private final Stats stats;
    private final FindingLog findings;
    private final Function<World, ServerConfigInspector.AntiXray> antiXray;
    private final boolean[] occluding = new boolean[Material.values().length];

    public TruthChecker(Supplier<AuditConfig> config, Stats stats, FindingLog findings,
                        Function<World, ServerConfigInspector.AntiXray> antiXray) {
        this.config = config;
        this.stats = stats;
        this.findings = findings;
        this.antiXray = antiXray;
        for (Material m : Material.values()) {
            if (!m.isLegacy() && m.isBlock()) occluding[m.ordinal()] = m.isOccluding();
        }
    }

    public boolean offer(Observation o) {
        if (queued.get() >= config.get().maxQueue) return false;
        queued.incrementAndGet();
        queue.add(o);
        return true;
    }

    public int queueSize() {
        return queued.get();
    }

    public void shutdown() {
        analysis.shutdownNow();
        queue.clear();
        queued.set(0);
    }

    @Override
    public void run() {
        AuditConfig cfg = config.get();
        int checks = 0;
        int snapshots = 0;
        Queue<Observation> deferred = new ArrayDeque<>();
        Observation o;
        while (checks < cfg.maxChecksPerTick && (o = queue.poll()) != null) {
            if (o instanceof Observation.ChunkSent c && snapshots >= cfg.maxSnapshotsPerTick) {
                deferred.add(c);
                continue;
            }
            queued.decrementAndGet();
            checks++;
            try {
                Player player = Bukkit.getPlayer(o.player());
                World world = Bukkit.getWorld(o.world());
                if (player == null || world == null) continue;
                switch (o) {
                    case Observation.ChunkSent c -> {
                        snapshots++;
                        checkChunk(cfg, player, world, c);
                    }
                    case Observation.BlockSent b -> checkBlock(cfg, player, world, b);
                    case Observation.EntitySent e -> checkEntity(cfg, player, world, e);
                    case Observation.SoundSent s -> checkSound(cfg, player, world, s);
                    case Observation.WorldEventSent w -> checkWorldEvent(player, world, w);
                }
            } catch (RuntimeException e) {
                stats.inc("check.error", o.getClass().getSimpleName());
            }
        }
        // Chunks over this tick's snapshot budget go back to the front of the line next tick.
        if (!deferred.isEmpty()) {
            deferred.addAll(queue);
            queue.clear();
            queue.addAll(deferred);
        }
    }

    // ---------------------------------------------------------------- chunks

    private void checkChunk(AuditConfig cfg, Player player, World world, Observation.ChunkSent obs) {
        Column column = obs.column();
        int cx = column.getX(), cz = column.getZ();
        String w = world.getName();
        if (!world.isChunkLoaded(cx, cz)) {
            stats.inc("chunk.skipped-unloaded", w);
            return;
        }

        if (player.getWorld().equals(world)) {
            Location l = player.getLocation();
            int dist = Math.max(Math.abs((l.getBlockX() >> 4) - cx), Math.abs((l.getBlockZ() >> 4) - cz));
            int allowed = player.getSendViewDistance() + 2; // small slack for movement between send and check
            if (dist > allowed) {
                stats.category(Category.CHUNK_BEYOND_VIEW, w, "distance>" + allowed);
                findings.record(Category.CHUNK_BEYOND_VIEW, "player", player.getName(), "world", w,
                        "chunkX", cx, "chunkZ", cz, "chunkDistance", dist, "sendViewDistance", player.getSendViewDistance());
            }
        }

        ChunkSnapshot center = world.getChunkAt(cx, cz).getChunkSnapshot(false, false, false);
        ChunkSnapshot west = snapshot(world, cx - 1, cz);
        ChunkSnapshot east = snapshot(world, cx + 1, cz);
        ChunkSnapshot north = snapshot(world, cx, cz - 1);
        ChunkSnapshot south = snapshot(world, cx, cz + 1);
        ServerConfigInspector.AntiXray ax = antiXray.apply(world);
        boolean lava = cfg.lavaOccludes != null ? cfg.lavaOccludes : ax.lavaObscures;
        TruthView truth = new TruthView(center, west, east, north, south,
                world.getMinHeight(), world.getMaxHeight(), occluding, lava);
        // Paper only obfuscates below max-block-height. Blocks above it are counted separately so they do not
        // dilute the fake-ore baseline that leak rates below it are judged against.
        int ceiling = ax.paperConfigFound && ax.enabled ? ax.maxBlockHeight : Integer.MAX_VALUE;
        String playerName = player.getName();
        stats.inc("chunk.sampled", w);

        analysis.execute(() -> {
            try {
                analyseChunk(cfg, w, playerName, column, truth, ceiling);
            } catch (RuntimeException e) {
                stats.inc("check.error", "chunk-analysis");
            }
        });
    }

    private static ChunkSnapshot snapshot(World world, int cx, int cz) {
        return world.isChunkLoaded(cx, cz) ? world.getChunkAt(cx, cz).getChunkSnapshot(false, false, false) : null;
    }

    /** Off the main thread: only snapshots and the decoded column are touched. */
    private void analyseChunk(AuditConfig cfg, String w, String playerName, Column column, TruthView truth, int ceiling) {
        BaseChunk[] sections = column.getChunks();
        int baseX = column.getX() << 4, baseZ = column.getZ() << 4;
        long buriedNonTarget = 0, unknownEdge = 0;

        for (int si = 0; si < sections.length; si++) {
            BaseChunk sent = sections[si];
            int sectionY = truth.minY + (si << 4);
            if (sectionY >= truth.maxY) break;
            for (int ly = 0; ly < 16; ly++) {
                int y = sectionY + ly;
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        Material real = truth.center.getBlockType(x, y, z);
                        boolean isTarget = cfg.targets.contains(real);
                        if (!isTarget && !truth.occludes(real)) continue;
                        int buried = truth.buried(x, y, z);
                        if (buried == TruthView.UNKNOWN) {
                            if (isTarget) unknownEdge++;
                            continue;
                        }
                        if (buried != TruthView.YES) continue;

                        boolean high = y >= ceiling;
                        if (high && !isTarget) continue;
                        String sentKey = sent == null ? "air" : PacketTap.stateName(sent.getBlockId(x, ly, z));
                        if (isTarget) {
                            String realKey = real.getKey().getKey();
                            String band = high ? "chunk.high" : "chunk";
                            stats.inc(band + ".buried", w, realKey);
                            if (sentKey.equals(realKey)) {
                                stats.inc(band + ".leaked", w, realKey);
                                stats.max(y, band + ".leak-max-y", w, realKey);
                                stats.min(y, band + ".leak-min-y", w, realKey);
                                stats.category(Category.CHUNK_ORE_LEAK, w, realKey);
                                findings.record(Category.CHUNK_ORE_LEAK, "player", playerName, "world", w,
                                        "x", baseX + x, "y", y, "z", baseZ + z, "block", realKey,
                                        "aboveMaxBlockHeight", high);
                            }
                        } else {
                            buriedNonTarget++;
                            if (cfg.targetKeys.contains(sentKey)) stats.inc("chunk.decoy", w, sentKey);
                        }
                    }
                }
            }
        }
        stats.add(buriedNonTarget, "chunk.buried-non-target", w);
        stats.add(unknownEdge, "chunk.unknown-edge", w);

        for (TileEntity te : column.getTileEntities()) {
            int lx = te.getX(), y = te.getY(), lz = te.getZ();
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || y < truth.minY || y >= truth.maxY) continue;
            Material real = truth.center.getBlockType(lx, y, lz);
            String key = real.getKey().getKey();
            stats.inc("chunk.block-entities", w, key);
            if (truth.buried(lx, y, lz) == TruthView.YES) {
                stats.category(Category.BLOCK_ENTITY_LEAK, w, key);
                findings.record(Category.BLOCK_ENTITY_LEAK, "player", playerName, "world", w, "source", "chunk_data",
                        "x", baseX + lx, "y", y, "z", baseZ + lz, "block", key);
            }
        }
    }

    // ---------------------------------------------------------------- single blocks

    private void checkBlock(AuditConfig cfg, Player player, World world, Observation.BlockSent b) {
        if (!world.isChunkLoaded(b.x() >> 4, b.z() >> 4)) return;
        Material real = world.getBlockAt(b.x(), b.y(), b.z()).getType();
        String realKey = real.getKey().getKey();
        String w = world.getName();
        boolean lava = cfg.lavaOccludes != null ? cfg.lavaOccludes : antiXray.apply(world).lavaObscures;

        if (b.source().equals("block_entity_data")) {
            stats.inc("update.block-entity-data", w, realKey);
            if (exposureDistance(world, b.x(), b.y(), b.z(), 1, lava) > 1) {
                stats.category(Category.BLOCK_ENTITY_LEAK, w, realKey);
                findings.record(Category.BLOCK_ENTITY_LEAK, "player", player.getName(), "world", w,
                        "source", b.source(), "x", b.x(), "y", b.y(), "z", b.z(), "block", realKey);
            }
            return;
        }

        stats.inc("update.target-sent", w, b.sentKey());
        if (!realKey.equals(b.sentKey())) return; // decoy or already changed again
        int radius = antiXray.apply(world).updateRadius;
        int searchTo = Math.max(cfg.exposureSearchRadius, radius + 2);
        int exposure = exposureDistance(world, b.x(), b.y(), b.z(), searchTo, lava);
        if (exposure <= 1) return; // the block itself is exposed — correct to reveal

        Category cat = exposure > radius + 1 ? Category.UPDATE_LEAK_BEYOND_RADIUS : Category.UPDATE_REVEAL_WITHIN_RADIUS;
        stats.category(cat, w, realKey);
        stats.inc("update.revealed-by", w, cat.name(), b.source());
        findings.record(cat, "player", player.getName(), "world", w, "source", b.source(),
                "x", b.x(), "y", b.y(), "z", b.z(), "block", realKey,
                "exposureDistance", exposure > searchTo ? ">" + searchTo : String.valueOf(exposure),
                "updateRadius", radius);
    }

    /**
     * Chebyshev distance from (x,y,z) to the nearest non-occluding block, 1 = a face/edge neighbour is open.
     * Returns maxRadius + 1 if none is found, and 1 if the area is not fully loaded (treated as exposed: no claim).
     */
    private int exposureDistance(World world, int x, int y, int z, int maxRadius, boolean lavaOccludes) {
        for (int r = 1; r <= maxRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != r) continue;
                        // At r = 1 only face neighbours count, matching the "buried" definition.
                        if (r == 1 && Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) continue;
                        int nx = x + dx, ny = y + dy, nz = z + dz;
                        if (ny < world.getMinHeight()) continue;
                        if (ny >= world.getMaxHeight()) return r;
                        if (!world.isChunkLoaded(nx >> 4, nz >> 4)) return 1;
                        Material m = world.getBlockAt(nx, ny, nz).getType();
                        boolean occ = occluding[m.ordinal()] || (lavaOccludes && m == Material.LAVA);
                        if (!occ) return r;
                    }
                }
            }
        }
        return maxRadius + 1;
    }

    // ---------------------------------------------------------------- entities / sounds / events

    private void checkEntity(AuditConfig cfg, Player player, World world, Observation.EntitySent e) {
        if (!player.getWorld().equals(world)) return;
        stats.inc("entity.checked", world.getName(), e.type());
        Location eye = player.getEyeLocation();
        Vector target = new Vector(e.x(), e.y() + 0.5, e.z());
        double dist = eye.toVector().distance(target);
        if (dist < cfg.entityMinDistance) return;
        if (hasLineOfSight(world, eye, target, dist)) return;
        stats.category(Category.ENTITY_NO_LOS, world.getName(), e.type());
        findings.record(Category.ENTITY_NO_LOS, "player", player.getName(), "world", world.getName(),
                "entity", e.type(), "x", round(e.x()), "y", round(e.y()), "z", round(e.z()), "distance", round(dist));
    }

    private void checkSound(AuditConfig cfg, Player player, World world, Observation.SoundSent s) {
        if (!player.getWorld().equals(world)) return;
        stats.inc("sound.checked", world.getName(), s.sound());
        Location eye = player.getEyeLocation();
        Vector source = new Vector(s.x(), s.y(), s.z());
        double dist = eye.toVector().distance(source);
        if (dist < cfg.soundMinDistance) return;
        if (hasLineOfSight(world, eye, source, dist)) return;
        stats.category(Category.SOUND_NO_LOS, world.getName(), s.sound());
        findings.record(Category.SOUND_NO_LOS, "player", player.getName(), "world", world.getName(),
                "sound", s.sound(), "x", round(s.x()), "y", round(s.y()), "z", round(s.z()), "distance", round(dist));
    }

    private void checkWorldEvent(Player player, World world, Observation.WorldEventSent ev) {
        String key = "event-" + ev.type();
        stats.category(Category.GLOBAL_WORLD_EVENT, world.getName(), key);
        double dist = player.getWorld().equals(world)
                ? player.getLocation().toVector().distance(new Vector(ev.x(), ev.y(), ev.z())) : -1;
        findings.record(Category.GLOBAL_WORLD_EVENT, "player", player.getName(), "world", world.getName(),
                "eventType", ev.type(), "x", ev.x(), "y", ev.y(), "z", ev.z(), "distance", round(dist));
    }

    private static boolean hasLineOfSight(World world, Location eye, Vector target, double dist) {
        Vector dir = target.clone().subtract(eye.toVector());
        if (dir.lengthSquared() < 1e-6) return true;
        // Stop slightly short so the block the source sits in does not count as an obstruction.
        RayTraceResult hit = world.rayTraceBlocks(eye, dir.normalize(), Math.max(0, dist - 0.75),
                FluidCollisionMode.NEVER, true);
        return hit == null || hit.getHitBlock() == null;
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    /** Read-only view over snapshots used by the analysis thread. */
    static final class TruthView {
        static final int NO = 0, YES = 1, UNKNOWN = 2;

        final ChunkSnapshot center, west, east, north, south;
        final int minY, maxY;
        private final boolean[] occluding;
        private final boolean lavaOccludes;

        TruthView(ChunkSnapshot center, ChunkSnapshot west, ChunkSnapshot east, ChunkSnapshot north,
                  ChunkSnapshot south, int minY, int maxY, boolean[] occluding, boolean lavaOccludes) {
            this.center = center;
            this.west = west;
            this.east = east;
            this.north = north;
            this.south = south;
            this.minY = minY;
            this.maxY = maxY;
            this.occluding = occluding;
            this.lavaOccludes = lavaOccludes;
        }

        boolean occludes(Material m) {
            return occluding[m.ordinal()] || (lavaOccludes && m == Material.LAVA);
        }

        /** Whether all six neighbours of local (x,z), world y are occluding. */
        int buried(int x, int y, int z) {
            int r;
            if ((r = neighbour(x + 1, y, z)) != YES) return r;
            if ((r = neighbour(x - 1, y, z)) != YES) return r;
            if ((r = neighbour(x, y, z + 1)) != YES) return r;
            if ((r = neighbour(x, y, z - 1)) != YES) return r;
            if ((r = neighbour(x, y + 1, z)) != YES) return r;
            return neighbour(x, y - 1, z);
        }

        private int neighbour(int x, int y, int z) {
            if (y < minY) return YES; // the void below the world is never visible
            if (y >= maxY) return NO;
            ChunkSnapshot s = center;
            if (x < 0) { s = west; x += 16; }
            else if (x > 15) { s = east; x -= 16; }
            else if (z < 0) { s = north; z += 16; }
            else if (z > 15) { s = south; z -= 16; }
            if (s == null) return UNKNOWN;
            return occludes(s.getBlockType(x, y, z)) ? YES : NO;
        }
    }
}
