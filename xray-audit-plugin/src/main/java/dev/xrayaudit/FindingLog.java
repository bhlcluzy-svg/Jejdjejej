package dev.xrayaudit;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Writes one JSON object per finding to reports/findings-<session>.ndjson on a dedicated thread. */
public final class FindingLog {

    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "XrayAudit-FindingLog");
        t.setDaemon(true);
        return t;
    });
    private final Map<Category, AtomicInteger> written = new EnumMap<>(Category.class);
    private final Logger log;
    private final File file;
    private volatile int cap;

    public FindingLog(File reportDir, Logger log, int cap) {
        this.log = log;
        this.cap = cap;
        for (Category c : Category.values()) written.put(c, new AtomicInteger());
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        this.file = new File(reportDir, "findings-" + stamp + ".ndjson");
    }

    public File file() {
        return file;
    }

    public void setCap(int cap) {
        this.cap = cap;
    }

    public void resetCaps() {
        written.values().forEach(a -> a.set(0));
    }

    /** fields alternate key, value. Values are written as JSON numbers when they are Numbers, strings otherwise. */
    public void record(Category c, Object... fields) {
        if (written.get(c).incrementAndGet() > cap) return;
        StringBuilder sb = new StringBuilder(160);
        sb.append("{\"t\":").append(System.currentTimeMillis()).append(",\"category\":\"").append(c.name()).append('"');
        for (int i = 0; i + 1 < fields.length; i += 2) {
            sb.append(",\"").append(fields[i]).append("\":");
            Object v = fields[i + 1];
            if (v instanceof Number || v instanceof Boolean) sb.append(v);
            else sb.append('"').append(escape(String.valueOf(v))).append('"');
        }
        sb.append("}\n");
        String line = sb.toString();
        writer.execute(() -> {
            try {
                file.getParentFile().mkdirs();
                try (BufferedWriter w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                    w.write(line);
                }
            } catch (IOException e) {
                log.log(Level.WARNING, "Could not write finding", e);
            }
        });
    }

    public void close() {
        writer.shutdown();
        try {
            writer.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (ch < 0x20) b.append(String.format("\\u%04x", (int) ch));
                    else b.append(ch);
                }
            }
        }
        return b.toString();
    }
}
