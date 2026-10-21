package dev.xrayaudit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** The set of audited players, refreshed on the main thread so network threads never call permission checks. */
public final class AuditedPlayers implements Runnable {

    private final Set<UUID> audited = ConcurrentHashMap.newKeySet();
    private final Supplier<AuditConfig> config;

    public AuditedPlayers(Supplier<AuditConfig> config) {
        this.config = config;
    }

    public boolean contains(UUID id) {
        return audited.contains(id);
    }

    public int size() {
        return audited.size();
    }

    @Override
    public void run() {
        AuditConfig cfg = config.get();
        Set<UUID> now = ConcurrentHashMap.newKeySet();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!cfg.permissionMode || p.hasPermission("xrayaudit.audited")) now.add(p.getUniqueId());
        }
        audited.retainAll(now);
        audited.addAll(now);
    }
}
