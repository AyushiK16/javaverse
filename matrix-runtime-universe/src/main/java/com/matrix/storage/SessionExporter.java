package com.matrix.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.matrix.model.Snapshot;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Exports a session's snapshots from SnapshotRepository to a JSON file
 * and imports snapshots back from a JSON file using Gson.
 */
public class SessionExporter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type SNAPSHOT_LIST_TYPE = new TypeToken<List<Snapshot>>() {}.getType();

    /**
     * Loads all snapshots for the given session and writes them as formatted JSON to filePath.
     */
    public static void exportToJson(SnapshotRepository repo, long sessionId, String filePath) {
        if (repo == null) {
            throw new IllegalArgumentException("SnapshotRepository cannot be null");
        }
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath cannot be null or blank");
        }

        List<Snapshot> snapshots = repo.getAllSnapshots(sessionId);
        Path path = Paths.get(filePath);

        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            String json = GSON.toJson(snapshots, SNAPSHOT_LIST_TYPE);
            Files.writeString(path, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to export session " + sessionId + " to JSON: " + filePath, e);
        }
    }

    /**
     * Reads a JSON file created by exportToJson and returns the reconstructed List<Snapshot>.
     */
    public static List<Snapshot> importFromJson(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath cannot be null or blank");
        }

        Path path = Paths.get(filePath);
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            List<Snapshot> snapshots = GSON.fromJson(json, SNAPSHOT_LIST_TYPE);
            return snapshots == null ? new ArrayList<>() : snapshots;
        } catch (IOException e) {
            throw new RuntimeException("Failed to import snapshots from JSON: " + filePath, e);
        }
    }
}