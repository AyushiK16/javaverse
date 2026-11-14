package com.matrix.storage;

import com.matrix.model.EventType;
import com.matrix.model.ObjectInfo;
import com.matrix.model.ObjectStatus;
import com.matrix.model.ReferenceEdge;
import com.matrix.model.RuntimeEvent;
import com.matrix.model.Snapshot;
import com.matrix.model.StackFrameInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Standalone demonstration of saving, loading, exporting, and deleting snapshots
 * using SqliteSnapshotRepository and SessionExporter.
 */
public class StorageDemo {

    public static void main(String[] args) throws Exception {
        // Use an in-memory SQLite database so running the demo leaves no leftover .db files
        SqliteSnapshotRepository repo = new SqliteSnapshotRepository(":memory:");

        // 1. Create a new session
        String sampleCode = "public class Demo { public static void main(String[] args) { Person p = new Person(\"Alice\"); } }";
        long sessionId = repo.createSession("Demo.java", sampleCode);
        System.out.println("Created session with ID: " + sessionId);

        // 2. Build 2 sample snapshots by hand (simulating Person 2's output)
        List<Snapshot> sampleSnapshots = createSampleSnapshots();
        repo.saveSnapshots(sessionId, sampleSnapshots);
        System.out.println("Saved " + repo.getStepCount(sessionId) + " snapshots to SQLite.");

        // 3. List all sessions in the database
        System.out.println("\n--- Saved Sessions ---");
        for (SessionInfo info : repo.listSessions()) {
            System.out.println(info);
        }

        // 4. Load each snapshot back from SQLite and print details
        System.out.println("\n--- Loaded Snapshots from SQLite ---");
        for (Snapshot snap : repo.getAllSnapshots(sessionId)) {
            System.out.println("Step " + snap.getStepNumber()
                    + " (line " + snap.getLineNumber() + ")"
                    + " | Stack depth: " + snap.getStack().size()
                    + " | Heap objects: " + snap.getHeap().size()
                    + " | Edges: " + snap.getEdges().size()
                    + " | Events: " + snap.getEvents().size()
                    + " | Output: \"" + snap.getConsoleOutput().trim() + "\"");
        }

        // 5. Test JSON export and import
        Path tempJson = Files.createTempFile("matrix-demo-session-", ".json");
        SessionExporter.exportToJson(repo, sessionId, tempJson.toString());
        List<Snapshot> imported = SessionExporter.importFromJson(tempJson.toString());
        System.out.println("\nExported and re-imported " + imported.size() + " snapshots via JSON successfully.");
        Files.deleteIfExists(tempJson);

        // 6. Delete the session and confirm cleanup
        repo.deleteSession(sessionId);
        System.out.println("Deleted session " + sessionId + ". Remaining sessions: " + repo.listSessions().size());

        repo.getDatabaseManager().close();
    }

    private static List<Snapshot> createSampleSnapshots() {
        List<Snapshot> list = new ArrayList<>();

        // Step 0: main() frame pushed, no objects yet
        StackFrameInfo mainFrame0 = new StackFrameInfo("Demo", "main", 2, new LinkedHashMap<>(), new LinkedHashMap<>());
        Map<String, Integer> stats0 = new LinkedHashMap<>();
        stats0.put("liveObjects", 0);
        stats0.put("unreachableObjects", 0);
        stats0.put("collectedSoFar", 0);
        stats0.put("stackDepth", 1);

        list.add(new Snapshot(
                0,
                2,
                List.of(mainFrame0),
                new ArrayList<>(),
                new ArrayList<>(),
                new LinkedHashMap<>(),
                "",
                List.of(new RuntimeEvent(0, EventType.FRAME_PUSHED, -1, "Frame Demo.main() pushed")),
                stats0
        ));

        // Step 1: Person#1 created and referenced by local variable 'p'
        Map<String, Long> refs1 = new LinkedHashMap<>();
        refs1.put("p", 1L);
        StackFrameInfo mainFrame1 = new StackFrameInfo("Demo", "main", 3, new LinkedHashMap<>(), refs1);

        Map<String, String> personFields = new LinkedHashMap<>();
        personFields.put("name", "Alice");
        ObjectInfo person1 = new ObjectInfo(
                1L, "Person", personFields, new LinkedHashMap<>(),
                ObjectStatus.LIVE, 1, -1, -1
        );

        ReferenceEdge edge1 = new ReferenceEdge("STACK", 0, "p", 1L);

        Map<String, Integer> stats1 = new LinkedHashMap<>();
        stats1.put("liveObjects", 1);
        stats1.put("unreachableObjects", 0);
        stats1.put("collectedSoFar", 0);
        stats1.put("stackDepth", 1);

        list.add(new Snapshot(
                1,
                3,
                List.of(mainFrame1),
                List.of(person1),
                List.of(edge1),
                new LinkedHashMap<>(),
                "Created Alice\n",
                List.of(
                        new RuntimeEvent(1, EventType.OBJECT_CREATED, 1L, "Person#1 created"),
                        new RuntimeEvent(1, EventType.REFERENCE_ADDED, 1L, "main.p -> Person#1")
                ),
                stats1
        ));

        return list;
    }
}