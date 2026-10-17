package com.spawnerfinder.modules;

import com.spawnerfinder.SpawnerFinderAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockEventS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.LightData;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * Records what the server sends into a local JSON-lines file, for comparing
 * recordings made near a known base with recordings made over untouched land.
 *
 * Each line is one event. Lighting is summarised per 16-block section so the
 * files stay small. Nothing is sent anywhere; the file only lives in
 * .minecraft/spawner-finder-logs.
 */
public class PacketLogger extends Module {
    public enum Label { Base, Wild, Test }

    private static final int MAX_BLOCKS_PER_MULTI = 64;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRecord = settings.createGroup("Record");

    private final Setting<Label> label = sgGeneral.add(new EnumSetting.Builder<Label>()
        .name("label")
        .description("What this recording is: near a known base, over untouched land, or anything else.")
        .defaultValue(Label.Test)
        .build()
    );

    private final Setting<Boolean> lighting = sgRecord.add(new BoolSetting.Builder()
        .name("lighting")
        .description("Per-section summary of sky and block light for every chunk.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> blocks = sgRecord.add(new BoolSetting.Builder()
        .name("block-updates")
        .description("Single and grouped block changes, block entities, and block events like pistons and chests.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> sounds = sgRecord.add(new BoolSetting.Builder()
        .name("sounds")
        .description("Sounds with their positions.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> entities = sgRecord.add(new BoolSetting.Builder()
        .name("entities")
        .description("Mobs and other entities appearing, with type and position.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> particles = sgRecord.add(new BoolSetting.Builder()
        .name("particles")
        .description("Particles and world events (doors, fire, smoke and so on).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> position = sgRecord.add(new BoolSetting.Builder()
        .name("position")
        .description("Your own position once a second, so events can be matched to where you were.")
        .defaultValue(true)
        .build()
    );

    private final Map<String, LongAdder> counts = new ConcurrentHashMap<>();
    private ExecutorService writerThread;
    private BufferedWriter writer;
    private Path file;
    private long startMs;
    private volatile int bottomSection = -4;
    private int tickCounter;

    public PacketLogger(Category category) {
        super(category, "packet-logger", "Records what the server sends to a local file, for comparing base areas with untouched land.");
    }

    @Override
    public void onActivate() {
        counts.clear();
        tickCounter = 0;
        startMs = System.currentTimeMillis();
        if (mc.world != null) bottomSection = mc.world.getBottomSectionCoord();

        try {
            Path dir = FabricLoader.getInstance().getGameDir().resolve("spawner-finder-logs");
            Files.createDirectories(dir);
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            file = dir.resolve(label.get().name().toLowerCase(Locale.ROOT) + "_" + stamp + ".jsonl");
            writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            error("Could not create the log file: %s", e.getMessage());
            SpawnerFinderAddon.LOG.error("[Spawner Finder] Could not create packet log", e);
            toggle();
            return;
        }

        writerThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "spawner-finder-packet-logger");
            t.setDaemon(true);
            return t;
        });
        write("{\"k\":\"start\",\"label\":\"" + label.get().name() + "\",\"time\":" + startMs + ",\"bottomSection\":" + bottomSection + "}");
        info("Recording (%s) to %s", label.get().name(), file.getFileName());
    }

    @Override
    public void onDeactivate() {
        if (writerThread == null) return;

        long seconds = (System.currentTimeMillis() - startMs) / 1000;
        Map<String, Long> sorted = new TreeMap<>();
        counts.forEach((key, value) -> sorted.put(key, value.sum()));

        StringBuilder json = new StringBuilder("{\"k\":\"summary\",\"seconds\":").append(seconds).append(",\"counts\":{");
        boolean first = true;
        for (Map.Entry<String, Long> entry : sorted.entrySet()) {
            if (!first) json.append(',');
            json.append('"').append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        write(json.append("}}").toString());

        ExecutorService thread = writerThread;
        BufferedWriter out = writer;
        writerThread = null;
        writer = null;
        thread.execute(() -> {
            try {
                out.close();
            } catch (IOException e) {
                SpawnerFinderAddon.LOG.error("[Spawner Finder] Could not close packet log", e);
            }
        });
        thread.shutdown();
        try {
            thread.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long chunks = sorted.getOrDefault("chunk", 0L);
        long sound = sorted.getOrDefault("sound", 0L);
        long entity = sorted.getOrDefault("entity", 0L);
        long block = sorted.getOrDefault("block", 0L) + sorted.getOrDefault("multi", 0L);
        info("Saved %s (%ds): %d chunks, %d block updates, %d sounds, %d entities.",
            file.getFileName(), seconds, chunks, block, sound, entity);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        bottomSection = mc.world.getBottomSectionCoord();
        if (!position.get() || ++tickCounter % 20 != 0) return;
        String dim = mc.world.getRegistryKey().getValue().toString();
        write(String.format(Locale.ROOT, "{\"k\":\"pos\",\"t\":%d,\"x\":%.1f,\"y\":%.1f,\"z\":%.1f,\"dim\":\"%s\"}",
            now(), mc.player.getX(), mc.player.getY(), mc.player.getZ(), dim));
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (writerThread == null) return;
        Packet<?> packet = event.packet;

        if (packet instanceof ChunkDataS2CPacket p) {
            count("chunk");
            if (lighting.get()) logLight("chunk", p.getChunkX(), p.getChunkZ(), p.getLightData());
        } else if (packet instanceof LightUpdateS2CPacket p) {
            count("light");
            if (lighting.get()) logLight("light", p.getChunkX(), p.getChunkZ(), p.getData());
        } else if (packet instanceof BlockUpdateS2CPacket p) {
            count("block");
            if (blocks.get()) {
                BlockPos pos = p.getPos();
                write(String.format(Locale.ROOT, "{\"k\":\"block\",\"t\":%d,\"x\":%d,\"y\":%d,\"z\":%d,\"id\":\"%s\"}",
                    now(), pos.getX(), pos.getY(), pos.getZ(), Registries.BLOCK.getId(p.getState().getBlock())));
            }
        } else if (packet instanceof ChunkDeltaUpdateS2CPacket p) {
            count("multi");
            if (blocks.get()) logMulti(p);
        } else if (packet instanceof BlockEntityUpdateS2CPacket p) {
            count("blockEntity");
            if (blocks.get()) {
                BlockPos pos = p.getPos();
                write(String.format(Locale.ROOT, "{\"k\":\"blockEntity\",\"t\":%d,\"x\":%d,\"y\":%d,\"z\":%d,\"id\":\"%s\"}",
                    now(), pos.getX(), pos.getY(), pos.getZ(), Registries.BLOCK_ENTITY_TYPE.getId(p.getBlockEntityType())));
            }
        } else if (packet instanceof BlockEventS2CPacket p) {
            count("blockEvent");
            if (blocks.get()) {
                BlockPos pos = p.getPos();
                write(String.format(Locale.ROOT, "{\"k\":\"blockEvent\",\"t\":%d,\"x\":%d,\"y\":%d,\"z\":%d,\"id\":\"%s\",\"type\":%d,\"data\":%d}",
                    now(), pos.getX(), pos.getY(), pos.getZ(), Registries.BLOCK.getId(p.getBlock()), p.getType(), p.getData()));
            }
        } else if (packet instanceof PlaySoundS2CPacket p) {
            count("sound");
            if (sounds.get()) {
                write(String.format(Locale.ROOT, "{\"k\":\"sound\",\"t\":%d,\"x\":%.1f,\"y\":%.1f,\"z\":%.1f,\"id\":\"%s\",\"cat\":\"%s\"}",
                    now(), p.getX(), p.getY(), p.getZ(), p.getSound().value().id(), p.getCategory().getName()));
            }
        } else if (packet instanceof EntitySpawnS2CPacket p) {
            count("entity");
            if (entities.get()) {
                write(String.format(Locale.ROOT, "{\"k\":\"entity\",\"t\":%d,\"x\":%.1f,\"y\":%.1f,\"z\":%.1f,\"id\":\"%s\",\"eid\":%d}",
                    now(), p.getX(), p.getY(), p.getZ(), Registries.ENTITY_TYPE.getId(p.getEntityType()), p.getEntityId()));
            }
        } else if (packet instanceof ParticleS2CPacket p) {
            count("particle");
            if (particles.get()) {
                write(String.format(Locale.ROOT, "{\"k\":\"particle\",\"t\":%d,\"x\":%.1f,\"y\":%.1f,\"z\":%.1f,\"id\":\"%s\"}",
                    now(), p.getX(), p.getY(), p.getZ(), Registries.PARTICLE_TYPE.getId(p.getParameters().getType())));
            }
        } else if (packet instanceof WorldEventS2CPacket p) {
            count("worldEvent");
            if (particles.get()) {
                BlockPos pos = p.getPos();
                write(String.format(Locale.ROOT, "{\"k\":\"worldEvent\",\"t\":%d,\"x\":%d,\"y\":%d,\"z\":%d,\"event\":%d,\"data\":%d}",
                    now(), pos.getX(), pos.getY(), pos.getZ(), p.getEventId(), p.getData()));
            }
        } else {
            // Everything else is only counted, by its runtime class name.
            count("other:" + packet.getClass().getSimpleName());
        }
    }

    private void logMulti(ChunkDeltaUpdateS2CPacket packet) {
        List<String> entries = new ArrayList<>();
        int[] total = {0};
        packet.visitUpdates((pos, state) -> {
            total[0]++;
            if (entries.size() < MAX_BLOCKS_PER_MULTI) {
                entries.add(String.format(Locale.ROOT, "[%d,%d,%d,\"%s\"]",
                    pos.getX(), pos.getY(), pos.getZ(), Registries.BLOCK.getId(state.getBlock())));
            }
        });
        write("{\"k\":\"multi\",\"t\":" + now() + ",\"n\":" + total[0] + ",\"blocks\":[" + String.join(",", entries) + "]}");
    }

    /**
     * One character per light section, bottom to top, starting one section
     * below the world (Minecraft keeps a padding section on each side):
     * '.' not sent, 'e' sent as empty, '0' all dark, 'F' all full, 'm' mixed.
     * blockLit lists how many lit nibbles each mixed block-light section has.
     */
    private void logLight(String kind, int cx, int cz, LightData data) {
        StringBuilder blockLit = new StringBuilder();
        String sky = encode(data.getInitedSky(), data.getUninitedSky(), data.getSkyNibbles(), null);
        String block = encode(data.getInitedBlock(), data.getUninitedBlock(), data.getBlockNibbles(), blockLit);
        write("{\"k\":\"" + kind + "\",\"t\":" + now() + ",\"cx\":" + cx + ",\"cz\":" + cz
            + ",\"firstSectionY\":" + ((bottomSection - 1) * 16)
            + ",\"sky\":\"" + sky + "\",\"blockLight\":\"" + block + "\",\"blockLit\":[" + blockLit + "]}");
    }

    private static String encode(BitSet inited, BitSet uninited, List<byte[]> nibbles, StringBuilder litCounts) {
        int length = Math.max(inited.length(), uninited.length());
        StringBuilder out = new StringBuilder(length);
        int next = 0;
        for (int i = 0; i < length; i++) {
            if (inited.get(i)) {
                byte[] array = next < nibbles.size() ? nibbles.get(next) : null;
                next++;
                char code = classify(array);
                out.append(code);
                if (code == 'm' && litCounts != null) {
                    if (!litCounts.isEmpty()) litCounts.append(',');
                    litCounts.append("[").append(i).append(',').append(litNibbles(array)).append("]");
                }
            } else if (uninited.get(i)) {
                out.append('e');
            } else {
                out.append('.');
            }
        }
        return out.toString();
    }

    private static char classify(byte[] array) {
        if (array == null) return '?';
        boolean allZero = true, allFull = true;
        for (byte b : array) {
            if (b != 0) allZero = false;
            if (b != (byte) 0xFF) allFull = false;
            if (!allZero && !allFull) return 'm';
        }
        return allZero ? '0' : 'F';
    }

    private static int litNibbles(byte[] array) {
        int lit = 0;
        for (byte b : array) {
            if ((b & 0x0F) != 0) lit++;
            if ((b & 0xF0) != 0) lit++;
        }
        return lit;
    }

    private void count(String key) {
        counts.computeIfAbsent(key, k -> new LongAdder()).increment();
    }

    private long now() {
        return System.currentTimeMillis() - startMs;
    }

    private void write(String line) {
        ExecutorService thread = writerThread;
        BufferedWriter out = writer;
        if (thread == null || out == null) return;
        thread.execute(() -> {
            try {
                out.write(line);
                out.newLine();
            } catch (IOException e) {
                SpawnerFinderAddon.LOG.error("[Spawner Finder] Could not write packet log", e);
            }
        });
    }
}
