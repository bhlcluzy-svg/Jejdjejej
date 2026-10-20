package com.spawnerfinder.modules;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.LightData;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Scores every chunk the server sends from several independent signs that
 * players have been there, and flags the chunk once the score reaches
 * {@code threshold}. Each flag lists the signs behind it, so a hit can be
 * checked instead of trusted.
 *
 * Signs, strongest first:
 * - Dark light sections: an underground section whose sky light arrives as a
 *   full array of zeros. Normal dark sections usually arrive marked "empty"
 *   instead, and other base finders report zeroed sections over bases on
 *   Donut. A section that used to be sent lit, or that is solid stone with no
 *   air in or around it, has an innocent explanation and doesn't count. This
 *   is the least proven sign: confirm it with packet-logger.
 * - Player block entities: shulkers, ender chests, barrels, hoppers, furnaces
 *   and the like in the chunk data, which world generation doesn't place
 *   underground. Plain chests and spawners count for less, since dungeons and
 *   structures have them.
 * - Rotated deepslate: generated deepslate always stands upright (axis Y).
 * - Fully grown kelp: world generation sets kelp tops to age 20-23, so a kelp
 *   top at age 25 has grown (or been sheared), and plants only grow within
 *   simulation distance of a player.
 * - Placed lights underground: torches, lanterns, lamps, glowstone.
 *
 * Use packet-logger near a known base and over wild land to check which of
 * these the server you play on still sends, and tune the weights to match.
 */
public class BaseFinder extends Module {
    private static final int MAX_AGE = 25;
    private static final int MAX_MARKS = 24;
    private static final int MAX_TRACKED_CHUNKS = 40000;
    // A chunk is queued when its packet arrives, which is just before the game
    // applies it, so give it a few ticks to load before giving up on it.
    private static final int MAX_SCAN_TRIES = 40;

    private static final Direction[] SIDES = Direction.values();

    /** Block entities that world generation doesn't put underground. */
    private static final Set<String> PLAYER_BLOCK_ENTITIES = Set.of(
        "shulker_box", "ender_chest", "barrel", "hopper", "furnace", "blast_furnace", "smoker",
        "brewing_stand", "enchanting_table", "beacon", "bed", "sign", "hanging_sign", "banner",
        "jukebox", "dispenser", "dropper", "crafter", "chiseled_bookshelf", "decorated_pot", "campfire"
    );

    private static final Predicate<BlockState> LIGHT_BLOCK = s ->
        s.isOf(Blocks.TORCH) || s.isOf(Blocks.WALL_TORCH)
            || s.isOf(Blocks.LANTERN) || s.isOf(Blocks.REDSTONE_LAMP)
            || s.isOf(Blocks.GLOWSTONE) || s.isOf(Blocks.JACK_O_LANTERN)
            || s.isOf(Blocks.OCHRE_FROGLIGHT) || s.isOf(Blocks.VERDANT_FROGLIGHT) || s.isOf(Blocks.PEARLESCENT_FROGLIGHT)
            || s.isOf(Blocks.SHROOMLIGHT) || s.isOf(Blocks.END_ROD);

    private static final Predicate<BlockState> INTERESTING = s ->
        s.isOf(Blocks.DEEPSLATE) || s.isOf(Blocks.KELP) || LIGHT_BLOCK.test(s);

    /** One kind of evidence, with the label used in chat and on screen. */
    private enum Sign {
        DARK_LIGHT("dark light section"),
        PLAYER_STORAGE("player block"),
        CHEST("chest"),
        SPAWNER("spawner"),
        ROTATED_DEEPSLATE("rotated deepslate"),
        GROWN_KELP("grown kelp"),
        PLACED_LIGHT("placed light");

        final String label;

        Sign(String label) {
            this.label = label;
        }
    }

    private final SettingGroup sgSigns = settings.getDefaultGroup();
    private final SettingGroup sgWeights = settings.createGroup("Weights");
    private final SettingGroup sgFilter = settings.createGroup("Filter");
    private final SettingGroup sgAlerts = settings.createGroup("Alerts");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> darkLight = sgSigns.add(new BoolSetting.Builder()
        .name("dark-light")
        .description("Underground sections whose sky light is sent as all zeros.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> blockEntities = sgSigns.add(new BoolSetting.Builder()
        .name("block-entities")
        .description("Shulkers, ender chests, barrels, furnaces, chests and spawners in the chunk data.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotatedDeepslate = sgSigns.add(new BoolSetting.Builder()
        .name("rotated-deepslate")
        .description("Deepslate lying on its side, which only a player can place.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> grownKelp = sgSigns.add(new BoolSetting.Builder()
        .name("grown-kelp")
        .description("Kelp at full age, which means it grew near a player.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> placedLights = sgSigns.add(new BoolSetting.Builder()
        .name("placed-lights")
        .description("Torches, lanterns, lamps and glowstone underground.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> darkWeight = sgWeights.add(new IntSetting.Builder()
        .name("dark-light")
        .description("Points per dark light section, counted for up to 2 sections.")
        .defaultValue(6)
        .range(0, 20)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Integer> storageWeight = sgWeights.add(new IntSetting.Builder()
        .name("player-block")
        .description("Points per player block entity, counted for up to 4.")
        .defaultValue(3)
        .range(0, 20)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Integer> chestWeight = sgWeights.add(new IntSetting.Builder()
        .name("chest")
        .description("Points per chest, counted for up to 4. Dungeons and structures have chests too.")
        .defaultValue(1)
        .range(0, 20)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Integer> spawnerWeight = sgWeights.add(new IntSetting.Builder()
        .name("spawner")
        .description("Points per spawner, counted for up to 2. Dungeons have spawners too.")
        .defaultValue(1)
        .range(0, 20)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Integer> deepslateWeight = sgWeights.add(new IntSetting.Builder()
        .name("rotated-deepslate")
        .description("Points per rotated deepslate block, counted for up to 3.")
        .defaultValue(3)
        .range(0, 20)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Integer> kelpWeight = sgWeights.add(new IntSetting.Builder()
        .name("grown-kelp")
        .description("Points per fully grown kelp, counted for up to 3.")
        .defaultValue(2)
        .range(0, 20)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Integer> lightWeight = sgWeights.add(new IntSetting.Builder()
        .name("placed-light")
        .description("Points per placed light underground, counted for up to 4. Strongholds have torches.")
        .defaultValue(1)
        .range(0, 20)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Integer> threshold = sgFilter.add(new IntSetting.Builder()
        .name("threshold")
        .description("Points a chunk needs before it is flagged.")
        .defaultValue(6)
        .range(1, 100)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Integer> maxY = sgFilter.add(new IntSetting.Builder()
        .name("max-y")
        .description("Ignore blocks and block entities above this height.")
        .defaultValue(40)
        .range(-64, 320)
        .sliderRange(-64, 120)
        .build()
    );

    private final Setting<Integer> lightMaxY = sgFilter.add(new IntSetting.Builder()
        .name("light-max-y")
        .description("Only light sections that end below this height count.")
        .defaultValue(62)
        .range(-64, 320)
        .sliderRange(-64, 120)
        .build()
    );

    private final Setting<Integer> chunksPerTick = sgFilter.add(new IntSetting.Builder()
        .name("chunks-per-tick")
        .description("How many chunks to scan for blocks each tick. Lower it if the game stutters.")
        .defaultValue(6)
        .range(1, 64)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Boolean> chatAlert = sgAlerts.add(new BoolSetting.Builder()
        .name("chat-alert")
        .description("Say in chat when a chunk is flagged, with the signs behind it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> overlay = sgAlerts.add(new BoolSetting.Builder()
        .name("screen-list")
        .description("List flagged chunks in the top-right corner of the screen.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxListed = sgAlerts.add(new IntSetting.Builder()
        .name("max-listed")
        .description("How many chunks the on-screen list shows, highest score first.")
        .defaultValue(6)
        .range(1, 20)
        .sliderRange(1, 12)
        .visible(overlay::get)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Outline flagged chunks and box the blocks that counted.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> chunkColor = sgRender.add(new ColorSetting.Builder()
        .name("chunk-color")
        .description("Fill color of flagged chunks.")
        .defaultValue(new SettingColor(170, 60, 255, 30))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline color of flagged chunks and the blocks that counted.")
        .defaultValue(new SettingColor(170, 60, 255, 255))
        .visible(render::get)
        .build()
    );

    private final Map<Long, ChunkInfo> chunks = new HashMap<>();
    private final Set<Long> scanQueue = new LinkedHashSet<>();

    public BaseFinder(Category category) {
        super(category, "base-finder", "Scores chunks from several signs of players (light data, block entities, rotated deepslate, grown kelp, placed lights) and flags the likely bases.");
    }

    @Override
    public void onActivate() {
        reset();
        // Chunks already loaded arrived before we were listening; their blocks
        // can still be scanned.
        if (mc.world != null && mc.player != null) {
            int view = mc.options.getClampedViewDistance();
            ChunkPos at = mc.player.getChunkPos();
            for (int x = at.x - view; x <= at.x + view; x++) {
                for (int z = at.z - view; z <= at.z + view; z++) {
                    scanQueue.add(ChunkPos.toLong(x, z));
                }
            }
        }
    }

    @Override
    public void onDeactivate() {
        reset();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        reset();
    }

    private void reset() {
        chunks.clear();
        scanQueue.clear();
    }

    // Packets arrive on the network thread. Copy what we need out of them
    // here and do the rest on the game thread.
    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        Packet<?> packet = event.packet;

        if (packet instanceof ChunkDataS2CPacket p) {
            int cx = p.getChunkX(), cz = p.getChunkZ();
            byte[] sky = darkLight.get() ? classifySky(p.getLightData()) : null;
            List<BlockEntityHit> hits = new ArrayList<>();
            if (blockEntities.get()) {
                try {
                    p.getChunkData().getBlockEntities(cx, cz).accept((pos, type, nbt) -> {
                        if (type != null) hits.add(new BlockEntityHit(pos.toImmutable(), type));
                    });
                } catch (RuntimeException ignored) {
                    // A malformed block entity shouldn't stop the rest of the chunk.
                }
            }
            mc.execute(() -> onChunk(cx, cz, sky, hits, true));
        } else if (packet instanceof LightUpdateS2CPacket p) {
            if (!darkLight.get()) return;
            int cx = p.getChunkX(), cz = p.getChunkZ();
            byte[] sky = classifySky(p.getData());
            mc.execute(() -> onChunk(cx, cz, sky, List.of(), false));
        } else if (packet instanceof BlockEntityUpdateS2CPacket p) {
            if (!blockEntities.get()) return;
            BlockEntityHit hit = new BlockEntityHit(p.getPos().toImmutable(), p.getBlockEntityType());
            mc.execute(() -> onChunk(hit.pos.getX() >> 4, hit.pos.getZ() >> 4, null, List.of(hit), false));
        } else if (packet instanceof BlockUpdateS2CPacket p) {
            long key = ChunkPos.toLong(p.getPos().getX() >> 4, p.getPos().getZ() >> 4);
            mc.execute(() -> scanQueue.add(key));
        } else if (packet instanceof ChunkDeltaUpdateS2CPacket p) {
            Set<Long> keys = new HashSet<>();
            p.visitUpdates((pos, state) -> keys.add(ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4)));
            mc.execute(() -> scanQueue.addAll(keys));
        }
    }

    // Per-section sky light status, indexed like the packet's bit sets (index 0
    // is the padding section below the world).
    private static final byte NOT_SENT = 0, ZEROED = 1, LIT = 2;

    private static byte[] classifySky(LightData data) {
        BitSet inited = data.getInitedSky();
        BitSet uninited = data.getUninitedSky();
        List<byte[]> nibbles = data.getSkyNibbles();
        int length = Math.max(inited.length(), uninited.length());
        byte[] out = new byte[length];
        int next = 0;
        for (int i = 0; i < length; i++) {
            if (inited.get(i)) {
                byte[] array = next < nibbles.size() ? nibbles.get(next) : null;
                next++;
                out[i] = isAllZero(array) ? ZEROED : LIT;
            } else if (uninited.get(i)) {
                // Sent as "empty": the server says nothing is stored here,
                // which is what a normal dark section looks like.
                out[i] = LIT;
            }
        }
        return out;
    }

    private static boolean isAllZero(byte[] array) {
        if (array == null || array.length != 2048) return false;
        for (byte b : array) {
            if (b != 0) return false;
        }
        return true;
    }

    private void onChunk(int cx, int cz, byte[] sky, List<BlockEntityHit> hits, boolean fullChunk) {
        if (!isActive() || mc.world == null) return;
        long key = ChunkPos.toLong(cx, cz);
        ChunkInfo info = chunks.get(key);
        if (info == null) {
            if (chunks.size() >= MAX_TRACKED_CHUNKS) forgetUnflagged();
            info = new ChunkInfo(cx, cz);
            chunks.put(key, info);
        }

        if (fullChunk) info.blockEntities.clear();
        int top = maxY.get();
        for (BlockEntityHit hit : hits) {
            if (hit.pos.getY() <= top) info.blockEntities.put(hit.pos.asLong(), hit.type);
        }

        if (sky != null) info.applySky(sky);
        scanQueue.add(key);
    }

    private void forgetUnflagged() {
        Iterator<ChunkInfo> it = chunks.values().iterator();
        while (it.hasNext()) {
            if (!it.next().flagged) it.remove();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || scanQueue.isEmpty()) return;
        int budget = chunksPerTick.get();
        Iterator<Long> it = scanQueue.iterator();
        while (it.hasNext() && budget > 0) {
            long key = it.next();
            int cx = ChunkPos.getPackedX(key), cz = ChunkPos.getPackedZ(key);
            WorldChunk chunk = mc.world.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, false);
            ChunkInfo info = chunks.computeIfAbsent(key, k -> new ChunkInfo(cx, cz));
            if (chunk == null) {
                // Not applied yet, or already unloaded. Score from the light
                // and block entities we have, and try the blocks again later.
                evaluate(info);
                if (++info.scanTries >= MAX_SCAN_TRIES) {
                    info.scanTries = 0;
                    it.remove();
                }
                continue;
            }
            info.scanTries = 0;
            scanBlocks(info, chunk);
            evaluate(info);
            it.remove();
            budget--;
        }
    }

    private void evaluate(ChunkInfo info) {
        info.counts.clear();
        info.marks.clear();
        info.lowestY = Integer.MAX_VALUE;

        if (darkLight.get()) countDarkSections(info);
        if (blockEntities.get()) countBlockEntities(info);
        for (ScanHit hit : info.scanned) {
            if (enabled(hit.sign)) info.add(hit.sign, hit.pos, hit.pos.getY());
        }

        int score = 0;
        score += points(info, Sign.DARK_LIGHT, darkWeight.get(), 2);
        score += points(info, Sign.PLAYER_STORAGE, storageWeight.get(), 4);
        score += points(info, Sign.CHEST, chestWeight.get(), 4);
        score += points(info, Sign.SPAWNER, spawnerWeight.get(), 2);
        score += points(info, Sign.ROTATED_DEEPSLATE, deepslateWeight.get(), 3);
        score += points(info, Sign.GROWN_KELP, kelpWeight.get(), 3);
        score += points(info, Sign.PLACED_LIGHT, lightWeight.get(), 4);
        info.score = score;

        if (!info.flagged && score >= threshold.get()) {
            info.flagged = true;
            if (chatAlert.get() && !info.alerted) {
                info.alerted = true;
                int bx = info.cx * 16 + 8, bz = info.cz * 16 + 8;
                String y = info.lowestY == Integer.MAX_VALUE ? "" : ", y " + info.lowestY;
                info("Possible base at %d, %d%s: %d points (%s).", bx, bz, y, score, info.reasons());
            }
        } else if (info.flagged && score < threshold.get()) {
            // A rescan found the evidence gone (blocks broken, light resent lit).
            info.flagged = false;
        }
    }

    /** Whether a sign found by an earlier block scan is still switched on. */
    private boolean enabled(Sign sign) {
        return switch (sign) {
            case ROTATED_DEEPSLATE -> rotatedDeepslate.get();
            case GROWN_KELP -> grownKelp.get();
            case PLACED_LIGHT -> placedLights.get();
            default -> true;
        };
    }

    private static int points(ChunkInfo info, Sign sign, int weight, int cap) {
        return Math.min(info.counts.getOrDefault(sign, 0), cap) * weight;
    }

    private void countDarkSections(ChunkInfo info) {
        int bottom = mc.world.getBottomSectionCoord();
        int sectionCount = mc.world.countVerticalSections();
        int ceiling = lightMaxY.get();
        for (int i = 1; i <= sectionCount && i < info.sky.length; i++) {
            if (info.sky[i] != ZEROED) continue;
            int sectionY = bottom + i - 1;
            if (sectionY * 16 + 15 >= ceiling) continue;
            // Explained: the server sent this section lit before.
            if (info.everLit.get(i)) continue;
            // Explained: solid rock all round, where zeros are plausible.
            if (airless(info.cx, sectionY, info.cz) && airlessAround(info.cx, sectionY, info.cz)) continue;
            info.add(Sign.DARK_LIGHT, null, sectionY * 16);
        }
    }

    private boolean airlessAround(int cx, int sectionY, int cz) {
        for (Direction side : SIDES) {
            if (!airless(cx + side.getOffsetX(), sectionY + side.getOffsetY(), cz + side.getOffsetZ())) return false;
        }
        return true;
    }

    /** True when the section is loaded and has no air at all. */
    private boolean airless(int cx, int sectionY, int cz) {
        if (sectionY < mc.world.getBottomSectionCoord() || sectionY > mc.world.getTopSectionCoord()) return true;
        WorldChunk chunk = mc.world.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, false);
        if (chunk == null) return false;
        ChunkSection section = chunk.getSection(chunk.sectionCoordToIndex(sectionY));
        if (section.isEmpty()) return false;
        return !section.getBlockStateContainer().hasAny(BlockState::isAir);
    }

    private void countBlockEntities(ChunkInfo info) {
        for (Map.Entry<Long, BlockEntityType<?>> e : info.blockEntities.entrySet()) {
            Identifier id = Registries.BLOCK_ENTITY_TYPE.getId(e.getValue());
            if (id == null) continue;
            String path = id.getPath();
            BlockPos pos = BlockPos.fromLong(e.getKey());
            if (PLAYER_BLOCK_ENTITIES.contains(path)) {
                info.add(Sign.PLAYER_STORAGE, pos, pos.getY());
            } else if (path.equals("chest") || path.equals("trapped_chest")) {
                info.add(Sign.CHEST, pos, pos.getY());
            } else if (path.equals("mob_spawner")) {
                info.add(Sign.SPAWNER, pos, pos.getY());
            }
        }
    }

    /** Counts the signs in the chunk's blocks. The result is kept until the next scan. */
    private void scanBlocks(ChunkInfo info, WorldChunk chunk) {
        info.scanned.clear();
        boolean deepslate = rotatedDeepslate.get(), kelp = grownKelp.get(), lights = placedLights.get();
        if (!deepslate && !kelp && !lights) return;

        int top = maxY.get();
        int baseX = info.cx * 16, baseZ = info.cz * 16;
        ChunkSection[] sections = chunk.getSectionArray();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int index = 0; index < sections.length; index++) {
            ChunkSection section = sections[index];
            if (section == null || section.isEmpty()) continue;
            int sectionMinY = chunk.sectionIndexToCoord(index) * 16;
            if (sectionMinY > top) break;
            // Checks the section's palette, so most sections are skipped
            // without looking at a single block.
            if (!section.getBlockStateContainer().hasAny(INTERESTING)) continue;

            for (int y = 0; y < 16; y++) {
                int worldY = sectionMinY + y;
                if (worldY > top) break;
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        Sign sign = null;
                        if (deepslate && state.isOf(Blocks.DEEPSLATE)) {
                            if (state.get(Properties.AXIS) != Direction.Axis.Y) sign = Sign.ROTATED_DEEPSLATE;
                        } else if (kelp && state.isOf(Blocks.KELP)) {
                            if (state.get(Properties.AGE_25) >= MAX_AGE) sign = Sign.GROWN_KELP;
                        } else if (lights && LIGHT_BLOCK.test(state)) {
                            sign = Sign.PLACED_LIGHT;
                        }
                        if (sign != null) {
                            pos.set(baseX + x, worldY, baseZ + z);
                            info.scanned.add(new ScanHit(sign, pos.toImmutable()));
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!render.get() || chunks.isEmpty()) return;
        SettingColor fill = chunkColor.get(), line = lineColor.get();
        for (ChunkInfo info : chunks.values()) {
            if (!info.flagged) continue;
            int x = info.cx * 16, z = info.cz * 16;
            int y = info.lowestY == Integer.MAX_VALUE ? 0 : info.lowestY;
            event.renderer.box(x, y, z, x + 16, y + 1, z + 16, fill, line, ShapeMode.Both, 0);
            for (BlockPos p : info.marks) {
                event.renderer.box(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, fill, line, ShapeMode.Lines, 0);
            }
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!overlay.get() || mc.player == null || mc.textRenderer == null) return;

        List<ChunkInfo> flagged = new ArrayList<>();
        for (ChunkInfo info : chunks.values()) {
            if (info.flagged) flagged.add(info);
        }
        if (flagged.isEmpty()) return;
        flagged.sort((a, b) -> Integer.compare(b.score, a.score));

        double px = mc.player.getX(), pz = mc.player.getZ();
        int screenWidth = event.drawContext.getScaledWindowWidth();
        int lineHeight = mc.textRenderer.fontHeight + 2;
        int y = 4;

        String title = "Base Finder";
        event.drawContext.drawTextWithShadow(mc.textRenderer, title, screenWidth - mc.textRenderer.getWidth(title) - 4, y, 0xFFAA3CFF);
        y += lineHeight;

        int shown = Math.min(flagged.size(), maxListed.get());
        for (int i = 0; i < shown; i++) {
            ChunkInfo info = flagged.get(i);
            int bx = info.cx * 16 + 8, bz = info.cz * 16 + 8;
            int distance = (int) Math.sqrt((bx - px) * (bx - px) + (bz - pz) * (bz - pz));
            String text = String.format(Locale.ROOT, "%d pts  %d, %d  (%dm)  %s", info.score, bx, bz, distance, info.reasons());
            event.drawContext.drawTextWithShadow(mc.textRenderer, text, screenWidth - mc.textRenderer.getWidth(text) - 4, y, 0xFFFFFFFF);
            y += lineHeight;
        }
    }

    @Override
    public String getInfoString() {
        long flagged = chunks.values().stream().filter(c -> c.flagged).count();
        return flagged == 0 ? null : Long.toString(flagged);
    }

    private record BlockEntityHit(BlockPos pos, BlockEntityType<?> type) {
    }

    private record ScanHit(Sign sign, BlockPos pos) {
    }

    private static final class ChunkInfo {
        final int cx, cz;
        byte[] sky = new byte[0];
        /** Sections the server has ever sent lit or empty for this chunk. */
        final BitSet everLit = new BitSet();
        final Map<Long, BlockEntityType<?>> blockEntities = new HashMap<>();
        /** Signs found in the blocks on the last scan. */
        final List<ScanHit> scanned = new ArrayList<>();
        int scanTries;

        final Map<Sign, Integer> counts = new HashMap<>();
        final List<BlockPos> marks = new ArrayList<>();
        int lowestY = Integer.MAX_VALUE;
        int score;
        boolean flagged;
        boolean alerted;

        ChunkInfo(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
        }

        /** Light update packets only carry some sections; keep the rest. */
        void applySky(byte[] update) {
            if (update.length > sky.length) sky = java.util.Arrays.copyOf(sky, update.length);
            for (int i = 0; i < update.length; i++) {
                if (update[i] == NOT_SENT) continue;
                // everLit is never cleared, so a section that goes from lit to
                // zeroed is still recognised as having been lit.
                sky[i] = update[i];
                if (update[i] == LIT) everLit.set(i);
            }
        }

        void add(Sign sign, BlockPos pos, int y) {
            counts.merge(sign, 1, Integer::sum);
            if (pos != null && marks.size() < MAX_MARKS) marks.add(pos);
            if (y < lowestY) lowestY = y;
        }

        String reasons() {
            StringBuilder out = new StringBuilder();
            for (Sign sign : Sign.values()) {
                int n = counts.getOrDefault(sign, 0);
                if (n == 0) continue;
                if (!out.isEmpty()) out.append(", ");
                out.append(n).append(' ').append(sign.label);
            }
            return out.toString();
        }
    }
}
