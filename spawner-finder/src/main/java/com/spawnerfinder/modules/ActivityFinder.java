package com.spawnerfinder.modules;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
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
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockEventS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Flags spots underground where something is happening right now, from the
 * events the server sends so the game can play them, and guesses what each
 * spot is: a spawner (spawner spawn events, or the same hostile mob's sounds
 * clustering in one place), a player mining, walking or building, or storage
 * and machines. Unlike the lighting trick this only reaches a few dozen
 * blocks, but a hit means the spot is active, not an old tunnel.
 */
public class ActivityFinder extends Module {
    // Vanilla world event ids.
    private static final int WORLD_EVENT_BLOCK_BROKEN = 2001;
    private static final int WORLD_EVENT_SPAWNER_SPAWNS = 2004;

    /** What a single event points at, strongest evidence first. */
    private enum Kind {
        SPAWNER("Spawner"),
        MINING("Player mining"),
        WALKING("Player walking"),
        STORAGE("Storage / machine"),
        BUILDING("Building"),
        MOB("Mob sounds"),
        OTHER("Block activity");

        final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    private final SettingGroup sgSources = settings.getDefaultGroup();
    private final SettingGroup sgFilter = settings.createGroup("Filter");
    private final SettingGroup sgAlerts = settings.createGroup("Alerts");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> spawners = sgSources.add(new BoolSetting.Builder()
        .name("spawners")
        .description("Spawner spawn events, sent at the spawner's position whenever it spawns a mob.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> mobSounds = sgSources.add(new BoolSetting.Builder()
        .name("mob-sounds")
        .description("Hostile mob sounds. They only flag a spot when many of the same mob come from one place (a likely spawner).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> minMobSounds = sgSources.add(new IntSetting.Builder()
        .name("min-mob-sounds")
        .description("How many sounds from the same mob one spot needs before it counts as a possible spawner.")
        .defaultValue(6)
        .range(2, 50)
        .sliderRange(3, 20)
        .visible(mobSounds::get)
        .build()
    );

    private final Setting<Boolean> mining = sgSources.add(new BoolSetting.Builder()
        .name("mining")
        .description("Another player's block cracking animation and block-broken events.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> footsteps = sgSources.add(new BoolSetting.Builder()
        .name("footsteps")
        .description("Player footstep sounds.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> blockEvents = sgSources.add(new BoolSetting.Builder()
        .name("storage-and-machines")
        .description("Chests, barrels and shulkers opening, pistons moving, note blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> blockSounds = sgSources.add(new BoolSetting.Builder()
        .name("block-sounds")
        .description("Other block sounds such as placing and breaking.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxY = sgFilter.add(new IntSetting.Builder()
        .name("max-y")
        .description("Ignore anything above this height.")
        .defaultValue(40)
        .range(-64, 320)
        .sliderRange(-64, 120)
        .build()
    );

    private final Setting<Integer> minDepth = sgFilter.add(new IntSetting.Builder()
        .name("min-depth")
        .description("Only count spots at least this many blocks under the ground at that column. 0 turns this off.")
        .defaultValue(6)
        .range(0, 64)
        .sliderRange(0, 30)
        .build()
    );

    private final Setting<Integer> ignoreNearMe = sgFilter.add(new IntSetting.Builder()
        .name("ignore-near-me")
        .description("Ignore anything within this many blocks of you, so your own actions don't count.")
        .defaultValue(8)
        .range(0, 32)
        .sliderRange(0, 16)
        .build()
    );

    private final Setting<Integer> mergeRadius = sgFilter.add(new IntSetting.Builder()
        .name("merge-radius")
        .description("Events this close together count as the same spot.")
        .defaultValue(8)
        .range(1, 32)
        .sliderRange(2, 16)
        .build()
    );

    private final Setting<Integer> minEvents = sgFilter.add(new IntSetting.Builder()
        .name("min-events")
        .description("How many player or block events a spot needs before it is flagged. Spawner spawn events flag at once.")
        .defaultValue(3)
        .range(1, 50)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Integer> windowSeconds = sgFilter.add(new IntSetting.Builder()
        .name("time-window")
        .description("Events older than this many seconds stop counting towards a spot.")
        .defaultValue(60)
        .range(5, 600)
        .sliderRange(10, 180)
        .build()
    );

    private final Setting<Boolean> chatAlert = sgAlerts.add(new BoolSetting.Builder()
        .name("chat-alert")
        .description("Say in chat when a spot is flagged.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> overlay = sgAlerts.add(new BoolSetting.Builder()
        .name("screen-list")
        .description("List flagged spots in the top-right corner of the screen.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxListed = sgAlerts.add(new IntSetting.Builder()
        .name("max-listed")
        .description("How many spots the on-screen list shows, nearest first.")
        .defaultValue(6)
        .range(1, 20)
        .sliderRange(1, 12)
        .visible(overlay::get)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Draw the chunk outline, a box and a beam at flagged spots.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> chunkColor = sgRender.add(new ColorSetting.Builder()
        .name("chunk-color")
        .description("16x16 chunk outline color.")
        .defaultValue(new SettingColor(255, 200, 40, 35))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline, box and beam color.")
        .defaultValue(new SettingColor(255, 200, 40, 255))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> spawnerColor = sgRender.add(new ColorSetting.Builder()
        .name("spawner-color")
        .description("Color used for spots that look like spawners.")
        .defaultValue(new SettingColor(255, 70, 70, 255))
        .visible(render::get)
        .build()
    );

    private final List<Spot> spots = new ArrayList<>();

    public ActivityFinder(Category category) {
        super(category, "activity-finder", "Flags underground spots with live activity (spawners, players, storage) from block events and sounds.");
    }

    @Override
    public void onActivate() {
        spots.clear();
    }

    @Override
    public void onDeactivate() {
        spots.clear();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        spots.clear();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        Packet<?> packet = event.packet;

        if (packet instanceof BlockBreakingProgressS2CPacket p) {
            if (!mining.get()) return;
            int entityId = p.getEntityId();
            BlockPos pos = p.getPos().toImmutable();
            mc.execute(() -> {
                if (mc.player != null && entityId == mc.player.getId()) return;
                onActivity(center(pos), Kind.MINING, null);
            });
        } else if (packet instanceof WorldEventS2CPacket p) {
            int id = p.getEventId();
            BlockPos pos = p.getPos().toImmutable();
            if (id == WORLD_EVENT_SPAWNER_SPAWNS && spawners.get()) {
                mc.execute(() -> onActivity(center(pos), Kind.SPAWNER, null));
            } else if (id == WORLD_EVENT_BLOCK_BROKEN && mining.get()) {
                mc.execute(() -> onActivity(center(pos), Kind.MINING, null));
            }
        } else if (packet instanceof BlockEventS2CPacket p) {
            if (!blockEvents.get()) return;
            BlockPos pos = p.getPos().toImmutable();
            mc.execute(() -> onActivity(center(pos), Kind.STORAGE, null));
        } else if (packet instanceof PlaySoundS2CPacket p) {
            SoundCategory category = p.getCategory();
            String path = p.getSound().value().id().getPath();
            double[] at = {p.getX(), p.getY(), p.getZ()};
            Kind kind;
            String mob = null;
            if (category == SoundCategory.HOSTILE) {
                if (!mobSounds.get()) return;
                kind = Kind.MOB;
                mob = mobName(path);
                if (mob == null) return;
            } else if (path.endsWith(".step")) {
                if (!footsteps.get() || (category != SoundCategory.PLAYERS && category != SoundCategory.BLOCKS)) return;
                kind = Kind.WALKING;
            } else if (category == SoundCategory.BLOCKS) {
                if (!blockSounds.get()) return;
                kind = path.endsWith(".place") ? Kind.BUILDING
                    : path.endsWith(".break") ? Kind.MINING
                    : Kind.OTHER;
            } else {
                return;
            }
            Kind k = kind;
            String m = mob;
            mc.execute(() -> onActivity(at, k, m));
        }
    }

    private static double[] center(BlockPos pos) {
        return new double[] {pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5};
    }

    /** "entity.skeleton.ambient" -> "Skeleton"; null if it isn't an entity sound. */
    private static String mobName(String soundPath) {
        if (!soundPath.startsWith("entity.")) return null;
        String rest = soundPath.substring("entity.".length());
        int dot = rest.indexOf('.');
        String name = (dot < 0 ? rest : rest.substring(0, dot)).replace('_', ' ');
        if (name.isEmpty()) return null;
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    private void onActivity(double[] at, Kind kind, String mob) {
        if (!isActive() || mc.player == null || mc.world == null) return;
        double x = at[0], y = at[1], z = at[2];
        if (y > maxY.get()) return;

        double near = ignoreNearMe.get();
        if (near > 0 && mc.player.squaredDistanceTo(x, y, z) <= near * near) return;

        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        if (minDepth.get() > 0) {
            if (!mc.world.isChunkLoaded(bx >> 4, bz >> 4)) return;
            int ground = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, bx, bz);
            if (by > ground - minDepth.get()) return;
        }

        long now = System.currentTimeMillis();
        long cutoff = now - windowSeconds.get() * 1000L;
        double mergeSq = mergeRadius.get() * (double) mergeRadius.get();

        Spot spot = null;
        for (Spot s : spots) {
            if (s.distanceSq(x, y, z) <= mergeSq) {
                spot = s;
                break;
            }
        }
        if (spot == null) {
            spot = new Spot();
            spots.add(spot);
        }
        spot.add(x, y, z, now, cutoff, kind, mob);

        if (!spot.flagged && shouldFlag(spot)) {
            spot.flagged = true;
            if (chatAlert.get()) {
                info("%s underground near %d, %d, %d (%d events).",
                    spot.guess(minMobSounds.get()), spot.blockX(), spot.blockY(), spot.blockZ(), spot.recentTotal());
            }
        }
    }

    private boolean shouldFlag(Spot spot) {
        if (spot.recent(Kind.SPAWNER) > 0) return true;
        if (spot.topMobCount() >= minMobSounds.get()) return true;
        return spot.recentTotal() - spot.recent(Kind.MOB) >= minEvents.get();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!render.get() || spots.isEmpty()) return;

        for (Spot spot : spots) {
            if (!spot.flagged) continue;
            int x = spot.blockX(), y = spot.blockY(), z = spot.blockZ();
            SettingColor line = spot.looksLikeSpawner(minMobSounds.get()) ? spawnerColor.get() : lineColor.get();

            // 16x16 outline of the chunk the spot is in, at the spot's height.
            int cx = (x >> 4) << 4, cz = (z >> 4) << 4;
            event.renderer.box(cx, y, cz, cx + 16, y + 1, cz + 16, chunkColor.get(), line, ShapeMode.Both, 0);

            // Small box at the spot itself, and a beam to the sky.
            event.renderer.box(x, y, z, x + 1, y + 1, z + 1, chunkColor.get(), line, ShapeMode.Lines, 0);
            event.renderer.line(x + 0.5, y + 1, z + 0.5, x + 0.5, 320, z + 0.5, line);
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!overlay.get() || mc.player == null || mc.textRenderer == null) return;

        List<Spot> flagged = new ArrayList<>();
        for (Spot s : spots) {
            if (s.flagged) flagged.add(s);
        }
        if (flagged.isEmpty()) return;

        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        flagged.sort((a, b) -> Double.compare(a.distanceSq(px, py, pz), b.distanceSq(px, py, pz)));

        int screenWidth = event.drawContext.getScaledWindowWidth();
        int lineHeight = mc.textRenderer.fontHeight + 2;
        int y = 4;

        String title = "Activity Finder";
        event.drawContext.drawTextWithShadow(mc.textRenderer, title, screenWidth - mc.textRenderer.getWidth(title) - 4, y, 0xFFFFC828);
        y += lineHeight;

        int shown = Math.min(flagged.size(), maxListed.get());
        for (int i = 0; i < shown; i++) {
            Spot s = flagged.get(i);
            int distance = (int) Math.sqrt(s.distanceSq(px, py, pz));
            String text = String.format(Locale.ROOT, "%s  %d, %d, %d  (%dm)",
                s.guess(minMobSounds.get()), s.blockX(), s.blockY(), s.blockZ(), distance);
            int color = s.looksLikeSpawner(minMobSounds.get()) ? 0xFFFF4646 : 0xFFFFFFFF;
            event.drawContext.drawTextWithShadow(mc.textRenderer, text, screenWidth - mc.textRenderer.getWidth(text) - 4, y, color);
            y += lineHeight;
        }
    }

    @Override
    public String getInfoString() {
        long flagged = spots.stream().filter(s -> s.flagged).count();
        return flagged == 0 ? null : Long.toString(flagged);
    }

    private static final class Spot {
        private final List<Long> times = new ArrayList<>();
        private final List<Kind> kinds = new ArrayList<>();
        private final List<String> mobs = new ArrayList<>();
        private double sumX, sumY, sumZ;
        private int total;
        boolean flagged;

        void add(double x, double y, double z, long now, long cutoff, Kind kind, String mob) {
            for (int i = times.size() - 1; i >= 0; i--) {
                if (times.get(i) < cutoff) {
                    times.remove(i);
                    kinds.remove(i);
                    mobs.remove(i);
                }
            }
            times.add(now);
            kinds.add(kind);
            mobs.add(mob);
            sumX += x;
            sumY += y;
            sumZ += z;
            total++;
        }

        int recentTotal() {
            return times.size();
        }

        int recent(Kind kind) {
            int n = 0;
            for (Kind k : kinds) {
                if (k == kind) n++;
            }
            return n;
        }

        private Map.Entry<String, Integer> topMob() {
            Map<String, Integer> counts = new HashMap<>();
            for (String m : mobs) {
                if (m != null) counts.merge(m, 1, Integer::sum);
            }
            Map.Entry<String, Integer> best = null;
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                if (best == null || e.getValue() > best.getValue()) best = e;
            }
            return best;
        }

        int topMobCount() {
            Map.Entry<String, Integer> best = topMob();
            return best == null ? 0 : best.getValue();
        }

        boolean looksLikeSpawner(int minMobSounds) {
            return recent(Kind.SPAWNER) > 0 || topMobCount() >= minMobSounds;
        }

        /** Best guess of what this spot is, from the strongest evidence it has. */
        String guess(int minMobSounds) {
            Map.Entry<String, Integer> mob = topMob();
            if (recent(Kind.SPAWNER) > 0) {
                return mob != null ? mob.getKey() + " spawner" : "Spawner";
            }
            if (mob != null && mob.getValue() >= minMobSounds) {
                return "Possible " + mob.getKey().toLowerCase(Locale.ROOT) + " spawner";
            }
            EnumMap<Kind, Integer> counts = new EnumMap<>(Kind.class);
            for (Kind k : kinds) {
                if (k != Kind.MOB) counts.merge(k, 1, Integer::sum);
            }
            for (Kind k : Kind.values()) {
                if (counts.containsKey(k)) return k.label;
            }
            return Kind.OTHER.label;
        }

        int blockX() {
            return (int) Math.floor(sumX / total);
        }

        int blockY() {
            return (int) Math.floor(sumY / total);
        }

        int blockZ() {
            return (int) Math.floor(sumZ / total);
        }

        double distanceSq(double x, double y, double z) {
            double dx = sumX / total - x, dy = sumY / total - y, dz = sumZ / total - z;
            return dx * dx + dy * dy + dz * dz;
        }
    }
}
