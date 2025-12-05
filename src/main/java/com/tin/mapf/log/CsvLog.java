package com.tin.mapf.log;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.Map;

import static java.nio.file.StandardOpenOption.*;

public final class CsvLog {
    private final Path file;
    private volatile boolean headerWritten = false;

    public CsvLog(Path dir, String name) {
        this.file = dir.resolve(name);
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
    }

    public synchronized void writeHeader(String header) {
        if (headerWritten) return;
        try (BufferedWriter w = Files.newBufferedWriter(file, CREATE, APPEND)) {
            w.write("# started=" + Instant.now().toString()); w.newLine();
            w.write(header); w.newLine();
            headerWritten = true;
        } catch (IOException e) { e.printStackTrace(); }
    }

    public synchronized void writeRow(String csvLine) {
        try (BufferedWriter w = Files.newBufferedWriter(file, CREATE, APPEND)) {
            w.write(csvLine); w.newLine();
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static String csv(Map<String, Object> f) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (var e : f.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(e.getValue());
        }
        return sb.toString();
    }
}
