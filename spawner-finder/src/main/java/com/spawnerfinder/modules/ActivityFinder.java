package com.spawnerfinder.modules;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
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
import java.util.List;

/**
 * Flags spots underground where something is happening right now, from the
 * events the server sends so the game can play them: another player's block
 * cracking animation, "block broken" world events, block events (pistons,
 * chests, barrels, shulkers, note blocks) and block sounds. Mob sounds are
 * ignored. Unlike the lighting trick this only reaches a few dozen blocks, but
 * a hit means the spot is active, not an old tunnel.
 */
public class ActivityFinder extends Module {
    // Vanilla world event id for "block broken" (particles and sound).
    private static final int WORLD_EVENT_BLOCK_BROKEN = 2001;

    private final SettingGroup sgSources = settings.getDefaultGroup();
    private final SettingGroup sgFilter = settings.createGroup("Filter");
    private final SettingGroup sgAlerts = settings.createGroup("Alerts");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> mining = sgSources.add(new BoolSetting.Builder()
        .name("mining")
        .description("Another player's block cracking animation.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> blockBroken = sgSources.add(new BoolSetting.Builder()
        .name("block-broken")
        .description("The server's \"block broken\" event.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> blockEvents = sgSources.add(new BoolSetting.Builder()
        .name("block-events")
        .description("Pistons moving, chests, barrels and shulkers opening, note blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> blockSounds = sgSources.add(new BoolSetting.Builder()
        .name("block-sounds")
        .description("Block sounds such as placing, breaking and footsteps. Mob sounds are never used.")
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
        .description("How many events a spot needs before it is flagged.")
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

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Draw a box and a beam at flagged spots.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Box fill color.")
        .defaultValue(new SettingColor(255, 200, 40, 60))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Box outline and beam color.")
        .defaultValue(new SettingColor(255, 200, 40, 255))
        .visible(render::get)
        .build()
    );

    private final List<Spot> spots = new ArrayList<>();

    public ActivityFinder(Category category) {
        super(category, "activity-finder", "Flags spots underground where players or machines are active right now, from block events and sounds.");
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

        if (packet instanceof BlockBreakingProgressS2CPacket p && mining.get()) {
            int entityId = p.getEntityId();
            BlockPos pos = p.getPos().toImmutable();
            mc.execute(() -> {
                if (mc.player != null && entityId == mc.player.getId()) return;
                onActivity(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, "mining");
            });
        } else if (packet instanceof WorldEventS2CPacket p && blockBroken.get()) {
            if (p.getEventId() != WORLD_EVENT_BLOCK_BROKEN) return;
            BlockPos pos = p.getPos().toImmutable();
            mc.execute(() -> onActivity(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, "block broken"));
        } else if (packet instanceof BlockEventS2CPacket p && blockEvents.get()) {
            BlockPos pos = p.getPos().toImmutable();
            String name = p.getBlock().getName().getString();
            mc.execute(() -> onActivity(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, name));
        } else if (packet instanceof PlaySoundS2CPacket p && blockSounds.get()) {
            if (p.getCategory() != SoundCategory.BLOCKS) return;
            double x = p.getX(), y = p.getY(), z = p.getZ();
            mc.execute(() -> onActivity(x, y, z, "block sound"));
        }
    }

    private void onActivity(double x, double y, double z, String what) {
        if (!isActive() || mc.player == null || mc.world == null) return;
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
        spot.add(x, y, z, now, cutoff, what);

        if (!spot.flagged && spot.count() >= minEvents.get()) {
            spot.flagged = true;
            if (chatAlert.get()) {
                info("Activity underground near %d, %d, %d (%s, %d events).",
                    spot.blockX(), spot.blockY(), spot.blockZ(), spot.lastWhat, spot.count());
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || spots.isEmpty()) return;

        for (Spot spot : spots) {
            if (!spot.flagged) continue;
            int x = spot.blockX(), y = spot.blockY(), z = spot.blockZ();
            event.renderer.box(x, y, z, x + 1, y + 1, z + 1, sideColor.get(), lineColor.get(), ShapeMode.Both, 0);
            event.renderer.line(x + 0.5, y + 1, z + 0.5, x + 0.5, 320, z + 0.5, lineColor.get());
        }
    }

    @Override
    public String getInfoString() {
        long flagged = spots.stream().filter(s -> s.flagged).count();
        return flagged == 0 ? null : Long.toString(flagged);
    }

    private static final class Spot {
        private final List<long[]> times = new ArrayList<>();
        private double sumX, sumY, sumZ;
        private int total;
        boolean flagged;
        String lastWhat = "";

        void add(double x, double y, double z, long now, long cutoff, String what) {
            times.removeIf(t -> t[0] < cutoff);
            times.add(new long[] {now});
            sumX += x;
            sumY += y;
            sumZ += z;
            total++;
            lastWhat = what;
        }

        int count() {
            return times.size();
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
