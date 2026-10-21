package dev.xrayaudit;

/** Kinds of findings. Severity drives how the report ranks them. */
public enum Category {
    CHUNK_ORE_LEAK("Buried target block sent with its real identity in a chunk packet", Severity.FAIL),
    UPDATE_LEAK_BEYOND_RADIUS("Block update revealed a buried target block farther from any exposed face than update-radius allows", Severity.FAIL),
    UPDATE_REVEAL_WITHIN_RADIUS("Block update revealed a buried target block within update-radius (expected behaviour, but usable by chunk finders)", Severity.INFO),
    BLOCK_ENTITY_LEAK("Block entity (chest, spawner, ...) data sent for a fully buried position", Severity.FAIL),
    CHUNK_BEYOND_VIEW("Chunk packet sent for a chunk well outside the player's send-view-distance", Severity.WARN),
    ENTITY_NO_LOS("Entity spawned to a player with no line of sight to it (ESP-visible)", Severity.WARN),
    SOUND_NO_LOS("Positional sound sent from a source with no line of sight (ESP-locatable)", Severity.WARN),
    GLOBAL_WORLD_EVENT("Global world event broadcast with coordinates", Severity.INFO);

    public final String description;
    public final Severity severity;

    Category(String description, Severity severity) {
        this.description = description;
        this.severity = severity;
    }

    public enum Severity { FAIL, WARN, INFO }
}
