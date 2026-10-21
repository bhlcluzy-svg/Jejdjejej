package dev.xrayaudit;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe counters. Keys are "|"-joined paths, e.g. "chunk.buried|world|diamond_ore".
 * Written from network threads, the main thread and the analysis pool; read when building a report.
 */
public final class Stats {

    private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> maxima = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> minima = new ConcurrentHashMap<>();
    private volatile long startedAt = System.currentTimeMillis();

    public void add(long n, String... path) {
        counters.computeIfAbsent(String.join("|", path), k -> new LongAdder()).add(n);
    }

    public void inc(String... path) {
        add(1, path);
    }

    public long get(String... path) {
        LongAdder a = counters.get(String.join("|", path));
        return a == null ? 0 : a.sum();
    }

    public void max(int v, String... path) {
        maxima.merge(String.join("|", path), v, Math::max);
    }

    public void min(int v, String... path) {
        minima.merge(String.join("|", path), v, Math::min);
    }

    public Integer getMax(String... path) {
        return maxima.get(String.join("|", path));
    }

    public Integer getMin(String... path) {
        return minima.get(String.join("|", path));
    }

    /** All counters whose key starts with prefix + "|", keyed by the remainder (sorted). */
    public Map<String, Long> withPrefix(String... prefix) {
        String p = String.join("|", prefix) + "|";
        Map<String, Long> out = new TreeMap<>();
        counters.forEach((k, v) -> {
            if (k.startsWith(p)) out.put(k.substring(p.length()), v.sum());
        });
        return out;
    }

    public void category(Category c, String world, String key) {
        inc("cat", c.name(), world, key);
    }

    public long startedAt() {
        return startedAt;
    }

    public void reset() {
        counters.clear();
        maxima.clear();
        minima.clear();
        startedAt = System.currentTimeMillis();
    }
}
