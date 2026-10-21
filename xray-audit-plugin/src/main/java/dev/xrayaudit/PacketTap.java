package dev.xrayaudit;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockEntityData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import org.bukkit.entity.Player;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Runs on Netty threads at MONITOR priority. It only decodes packets (never modifies or cancels them), filters
 * cheaply, and hands observations to the {@link TruthChecker}. Nothing here touches world state.
 */
public final class PacketTap extends PacketListenerAbstract {

    private static final ConcurrentHashMap<Integer, String> STATE_NAMES = new ConcurrentHashMap<>();

    private final Supplier<AuditConfig> config;
    private final AuditedPlayers audited;
    private final TruthChecker checker;
    private final Stats stats;

    public PacketTap(Supplier<AuditConfig> config, AuditedPlayers audited, TruthChecker checker, Stats stats) {
        super(PacketListenerPriority.MONITOR);
        this.config = config;
        this.audited = audited;
        this.checker = checker;
        this.stats = stats;
    }

    /** Block key ("diamond_ore") for a global block-state id as encoded by this server. */
    public static String stateName(int globalId) {
        return STATE_NAMES.computeIfAbsent(globalId, id -> {
            WrappedBlockState s = WrappedBlockState.getByGlobalId(id);
            return s == null ? "unknown" : s.getType().getName();
        });
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        AuditConfig cfg = config.get();
        if (!cfg.enabled) return;
        PacketTypeCommon type = event.getPacketType();
        if (!isInteresting(type)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!audited.contains(player.getUniqueId())) return;
        String world = player.getWorld().getName();
        if (!cfg.auditsWorld(world)) return;

        stats.inc("packets.seen", type.getName());
        try {
            if (type == PacketType.Play.Server.CHUNK_DATA) {
                if (!roll(cfg.chunkSampleRate)) return;
                WrapperPlayServerChunkData w = new WrapperPlayServerChunkData(event);
                offer(new Observation.ChunkSent(player.getUniqueId(), world, w.getColumn()), type);
            } else if (type == PacketType.Play.Server.BLOCK_CHANGE) {
                if (!cfg.updatesEnabled) return;
                WrapperPlayServerBlockChange w = new WrapperPlayServerBlockChange(event);
                String key = stateName(w.getBlockId());
                if (!cfg.targetKeys.contains(key)) return;
                Vector3i p = w.getBlockPosition();
                offer(new Observation.BlockSent(player.getUniqueId(), world, p.getX(), p.getY(), p.getZ(), key, "block_change"), type);
            } else if (type == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
                if (!cfg.updatesEnabled) return;
                WrapperPlayServerMultiBlockChange w = new WrapperPlayServerMultiBlockChange(event);
                for (WrapperPlayServerMultiBlockChange.EncodedBlock b : w.getBlocks()) {
                    String key = stateName(b.getBlockId());
                    if (!cfg.targetKeys.contains(key)) continue;
                    offer(new Observation.BlockSent(player.getUniqueId(), world, b.getX(), b.getY(), b.getZ(), key, "multi_block_change"), type);
                }
            } else if (type == PacketType.Play.Server.BLOCK_ENTITY_DATA) {
                WrapperPlayServerBlockEntityData w = new WrapperPlayServerBlockEntityData(event);
                Vector3i p = w.getPosition();
                offer(new Observation.BlockSent(player.getUniqueId(), world, p.getX(), p.getY(), p.getZ(), "", "block_entity_data"), type);
            } else if (type == PacketType.Play.Server.SPAWN_ENTITY) {
                if (!cfg.entitiesEnabled || !roll(cfg.entitySampleRate)) return;
                WrapperPlayServerSpawnEntity w = new WrapperPlayServerSpawnEntity(event);
                Vector3d p = w.getPosition();
                String name = w.getEntityType() == null ? "unknown" : w.getEntityType().getName().getKey();
                offer(new Observation.EntitySent(player.getUniqueId(), world, name, p.getX(), p.getY(), p.getZ()), type);
            } else if (type == PacketType.Play.Server.SOUND_EFFECT) {
                if (!cfg.soundsEnabled || !roll(cfg.soundSampleRate)) return;
                WrapperPlayServerSoundEffect w = new WrapperPlayServerSoundEffect(event);
                Vector3d p = w.getPosition();
                String name = w.getSound() == null ? "unknown" : w.getSound().getSoundId().getKey();
                offer(new Observation.SoundSent(player.getUniqueId(), world, name, p.getX(), p.getY(), p.getZ()), type);
            } else if (type == PacketType.Play.Server.EFFECT) {
                WrapperPlayServerEffect w = new WrapperPlayServerEffect(event);
                if (!w.isGlobalEvent()) return;
                Vector3i p = w.getPosition();
                offer(new Observation.WorldEventSent(player.getUniqueId(), world, w.getType(), p.getX(), p.getY(), p.getZ(), true), type);
            }
        } catch (RuntimeException e) {
            // A decode failure must never break the connection; count it and move on.
            stats.inc("packets.decode-error", type.getName());
        }
    }

    private void offer(Observation o, PacketTypeCommon type) {
        if (checker.offer(o)) stats.inc("packets.queued", type.getName());
        else stats.inc("packets.dropped", type.getName());
    }

    private static boolean isInteresting(PacketTypeCommon t) {
        return t == PacketType.Play.Server.CHUNK_DATA
                || t == PacketType.Play.Server.BLOCK_CHANGE
                || t == PacketType.Play.Server.MULTI_BLOCK_CHANGE
                || t == PacketType.Play.Server.BLOCK_ENTITY_DATA
                || t == PacketType.Play.Server.SPAWN_ENTITY
                || t == PacketType.Play.Server.SOUND_EFFECT
                || t == PacketType.Play.Server.EFFECT;
    }

    private static boolean roll(double rate) {
        return rate >= 1.0 || ThreadLocalRandom.current().nextDouble() < rate;
    }
}
