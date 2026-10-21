package dev.xrayaudit;

import com.github.retrooper.packetevents.protocol.world.chunk.Column;

import java.util.UUID;

/** Something a player was sent, captured on the network thread and checked against the world on the main thread. */
public sealed interface Observation {

    UUID player();

    String world();

    /** A decoded chunk packet, exactly as it left the server (after any anti-xray obfuscation). */
    record ChunkSent(UUID player, String world, Column column) implements Observation {}

    /** A single block whose sent state is a target block. source = packet name. */
    record BlockSent(UUID player, String world, int x, int y, int z, String sentKey, String source) implements Observation {}

    record EntitySent(UUID player, String world, String type, double x, double y, double z) implements Observation {}

    record SoundSent(UUID player, String world, String sound, double x, double y, double z) implements Observation {}

    record WorldEventSent(UUID player, String world, int type, int x, int y, int z, boolean global) implements Observation {}
}
