package com.spawnerfinder.modules;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Guesses where mob spawners are from where new mobs keep appearing.
 *
 * The server tells the client about every mob near the player, underground or
 * not, because the game needs that to draw them. A spawner spawns its mobs
 * within a few blocks of itself, so many new mobs appearing in one small spot
 * over a short time points at a spawner. A spawner only runs while a player is
 * within 16 blocks of it, and mobs are only sent within entity tracking range,
 * so this finds spawners that are in use, and only when you are fairly close.
 */
public class SpawnerFinder extends Module {
    private final SettingGroup sgDetection = settings.getDefaultGroup();
    private final SettingGroup sgAlerts = settings.createGroup("Alerts");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Set<EntityType<?>>> mobs = sgDetection.add(new EntityTypeListSetting.Builder()
        .name("mobs")
        .description("Which mobs to watch for.")
        .defaultValue(EntityType.SKELETON)
        .build()
    );

    private final Setting<Integer> maxY = sgDetection.add(new IntSetting.Builder()
        .name("max-y")
        .description("Ignore mobs that appear above this height.")
        .defaultValue(40)
        .range(-64, 320)
        .sliderRange(-64, 120)
        .build()
    );

    private final Setting<Integer> clusterRadius = sgDetection.add(new IntSetting.Builder()
        .name("cluster-radius")
        .description("How close new mobs must appear to each other to count as one spot. Spawners spawn within 4 blocks of themselves.")
        .defaultValue(5)
        .range(1, 16)
        .sliderRange(2, 10)
        .build()
    );

    private final Setting<Integer> minSpawns = sgDetection.add(new IntSetting.Builder()
        .name("min-spawns")
        .description("How many new mobs one spot needs before it is flagged. Higher means fewer false alerts.")
        .defaultValue(4)
        .range(2, 50)
        .sliderRange(2, 15)
        .build()
    );

    private final Setting<Integer> windowSeconds = sgDetection.add(new IntSetting.Builder()
        .name("time-window")
        .description("Only count mobs that appeared within this many seconds.")
        .defaultValue(90)
        .range(10, 900)
        .sliderRange(15, 300)
        .build()
    );

    private final Setting<Boolean> chatAlert = sgAlerts.add(new BoolSetting.Builder()
        .name("chat-alert")
        .description("Say in chat when a possible spawner is found.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showCoords = sgAlerts.add(new BoolSetting.Builder()
        .name("show-coordinates")
        .description("Include coordinates in the chat alert.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Draw a box and a beam at each possible spawner.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the box is drawn.")
        .defaultValue(ShapeMode.Both)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Box fill color.")
        .defaultValue(new SettingColor(255, 70, 70, 60))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Box outline and beam color.")
        .defaultValue(new SettingColor(255, 70, 70, 255))
        .visible(render::get)
        .build()
    );

    private final Setting<Boolean> beam = sgRender.add(new BoolSetting.Builder()
        .name("beam")
        .description("Draw a vertical line up to the sky so you can spot it from far away.")
        .defaultValue(true)
        .visible(render::get)
        .build()
    );

    private final Deque<Sighting> sightings = new ArrayDeque<>();
    private final Set<Integer> seenEntityIds = new HashSet<>();
    private final List<Found> found = new ArrayList<>();

    public SpawnerFinder(Category category) {
        super(category, "spawner-finder", "Finds likely mob spawners from where new mobs keep appearing underground.");
    }

    @Override
    public void onActivate() {
        clear();
    }

    @Override
    public void onDeactivate() {
        clear();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        clear();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof EntitySpawnS2CPacket packet)) return;

        EntityType<?> type = packet.getEntityType();
        int id = packet.getEntityId();
        double x = packet.getX();
        double y = packet.getY();
        double z = packet.getZ();

        // Packets can arrive off the main thread, so do the bookkeeping there.
        mc.execute(() -> onMobAppeared(type, id, x, y, z));
    }

    private void onMobAppeared(EntityType<?> type, int id, double x, double y, double z) {
        if (!isActive() || mc.world == null) return;
        if (!mobs.get().contains(type)) return;
        if (y > maxY.get()) return;
        // The same mob can be sent again when it leaves and re-enters range.
        if (!seenEntityIds.add(id)) return;

        long now = System.currentTimeMillis();
        Sighting sighting = new Sighting(x, y, z, now);
        sightings.addLast(sighting);
        pruneOld(now);

        double radiusSq = clusterRadius.get() * (double) clusterRadius.get();
        double sumX = 0, sumY = 0, sumZ = 0;
        int count = 0;
        for (Sighting other : sightings) {
            if (other.distanceSq(sighting) > radiusSq) continue;
            sumX += other.x;
            sumY += other.y;
            sumZ += other.z;
            count++;
        }
        if (count < minSpawns.get()) return;

        // Mobs spawn around the spawner, so the average spot is a good guess for it.
        int cx = (int) Math.floor(sumX / count);
        int cy = (int) Math.floor(sumY / count);
        int cz = (int) Math.floor(sumZ / count);

        for (Found existing : found) {
            if (existing.distanceSq(cx, cy, cz) <= radiusSq * 4) {
                existing.spawns = Math.max(existing.spawns, count);
                return;
            }
        }

        found.add(new Found(cx, cy, cz, count));
        if (chatAlert.get()) {
            String name = type.getName().getString();
            if (showCoords.get()) {
                info("Possible %s spawner near %d, %d, %d (%d new mobs).", name, cx, cy, cz, count);
            } else {
                info("Possible %s spawner nearby (%d new mobs).", name, count);
            }
        }
    }

    private void pruneOld(long now) {
        long cutoff = now - windowSeconds.get() * 1000L;
        while (!sightings.isEmpty() && sightings.peekFirst().time < cutoff) {
            sightings.removeFirst();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || found.isEmpty()) return;

        for (Found spot : found) {
            event.renderer.box(spot.x, spot.y, spot.z, spot.x + 1, spot.y + 1, spot.z + 1,
                sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            if (beam.get()) {
                double bx = spot.x + 0.5;
                double bz = spot.z + 0.5;
                event.renderer.line(bx, spot.y + 1, bz, bx, 320, bz, lineColor.get());
            }
        }
    }

    @Override
    public String getInfoString() {
        return found.isEmpty() ? null : Integer.toString(found.size());
    }

    private void clear() {
        sightings.clear();
        seenEntityIds.clear();
        found.clear();
    }

    private record Sighting(double x, double y, double z, long time) {
        double distanceSq(Sighting other) {
            double dx = x - other.x, dy = y - other.y, dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private static final class Found {
        final int x, y, z;
        int spawns;

        Found(int x, int y, int z, int spawns) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.spawns = spawns;
        }

        double distanceSq(int ox, int oy, int oz) {
            double dx = x - ox, dy = y - oy, dz = z - oz;
            return dx * dx + dy * dy + dz * dz;
        }
    }
}
