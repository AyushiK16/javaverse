package com.matrix.storage;

import com.matrix.model.EventType;
import com.matrix.model.ObjectInfo;
import com.matrix.model.RuntimeEvent;
import com.matrix.model.Snapshot;
import com.matrix.model.StackFrameInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SqliteSnapshotRepository and SessionExporter.
 */
class SqliteSnapshotRepositoryTest {

    @Test
    void testRoundTripTwelveSnapshotsPreservesAllFieldsAndOrder() {
        SqliteSnapshotRepository repo = new SqliteSnapshotRepository(":memory:");
        try {
            List<Snapshot> original = FakeSnapshotFactory.createTwelveStepTimeline();
            assertEquals(12, original.size());

            // Verify all 9 EventTypes are covered in our test dataset
            Set<EventType> seenEvents = EnumSet.noneOf(EventType.class);
            for (Snapshot s : original) {
                for (RuntimeEvent e : s.getEvents()) {
                    seenEvents.add(e.getType());
                }
            }
            assertEquals(EnumSet.allOf(EventType.class), seenEvents, "All 9 EventTypes must be represented");

            long sessionId = repo.createSession("App.java", "public class App {}");
            repo.saveSnapshots(sessionId, original);

            assertEquals(12, repo.getStepCount(sessionId));

            // Check individual getSnapshot calls
            for (int step = 0; step < original.size(); step++) {
                Snapshot loaded = repo.getSnapshot(sessionId, step);
                assertNotNull(loaded, "Snapshot at step " + step + " should exist");
                assertSnapshotsEqual(original.get(step), loaded);
            }

            // Check getAllSnapshots preserves order
            List<Snapshot> allLoaded = repo.getAllSnapshots(sessionId);
            assertEquals(12, allLoaded.size());
            for (int i = 0; i < original.size(); i++) {
                assertSnapshotsEqual(original.get(i), allLoaded.get(i));
            }
        } finally {
            repo.getDatabaseManager().close();
        }
    }

    @Test
    void testMissingStepSessionIsolationListAndCascadeDelete() throws Exception {
        SqliteSnapshotRepository repo = new SqliteSnapshotRepository(":memory:");
        try {
            List<Snapshot> twelveSteps = FakeSnapshotFactory.createTwelveStepTimeline();

            long sessionA = repo.createSession("ProgramA.java", "class ProgramA {}");
            long sessionB = repo.createSession("ProgramB.java", "class ProgramB {}");

            repo.saveSnapshots(sessionA, twelveSteps.subList(0, 4));
            repo.saveSnapshots(sessionB, twelveSteps.subList(0, 7));

            // Missing step returns null
            assertNull(repo.getSnapshot(sessionA, -1));
            assertNull(repo.getSnapshot(sessionA, 4));
            assertNull(repo.getSnapshot(9999L, 0));

            // Sessions do not mix data
            assertEquals(4, repo.getStepCount(sessionA));
            assertEquals(7, repo.getStepCount(sessionB));

            List<SessionInfo> sessions = repo.listSessions();
            assertEquals(2, sessions.size());
            assertEquals("ProgramA.java", sessions.get(0).getProgramName());
            assertEquals(4, sessions.get(0).getStepCount());
            assertEquals("ProgramB.java", sessions.get(1).getProgramName());
            assertEquals(7, sessions.get(1).getStepCount());

            // Delete session A -> session B stays intact
            repo.deleteSession(sessionA);
            assertEquals(0, repo.getStepCount(sessionA));
            assertEquals(7, repo.getStepCount(sessionB));
            assertEquals(1, repo.listSessions().size());

            // Delete session B -> all child tables must now have 0 rows via ON DELETE CASCADE
            repo.deleteSession(sessionB);
            assertEquals(0, repo.listSessions().size());

            String[] childTables = {
                    "snapshots", "stack_frames", "frame_locals",
                    "heap_objects", "object_fields", "edges", "events", "static_refs"
            };
            try (Connection conn = repo.getDatabaseManager().getConnection();
                 Statement stmt = conn.createStatement()) {
                for (String table : childTables) {
                    try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
                        assertTrue(rs.next());
                        assertEquals(0, rs.getInt(1), "Expected 0 rows in table " + table + " after deleteSession");
                    }
                }
            }
        } finally {
            repo.getDatabaseManager().close();
        }
    }

    @Test
    void testSaveAndLoadThousandSnapshotsPerformance() {
        SqliteSnapshotRepository repo = new SqliteSnapshotRepository(":memory:");
        try {
            List<Snapshot> bulk = FakeSnapshotFactory.createBulkSnapshots(1000);
            long sessionId = repo.createSession("BulkApp.java", "class BulkApp {}");

            long startMs = System.currentTimeMillis();
            repo.saveSnapshots(sessionId, bulk);
            long elapsedMs = System.currentTimeMillis() - startMs;

            assertEquals(1000, repo.getStepCount(sessionId));
            List<Snapshot> loaded = repo.getAllSnapshots(sessionId);
            assertEquals(1000, loaded.size());
            assertSnapshotsEqual(bulk.get(0), loaded.get(0));
            assertSnapshotsEqual(bulk.get(999), loaded.get(999));

            assertTrue(elapsedMs < 10_000, "Saving 1000 snapshots took too long: " + elapsedMs + " ms");
        } finally {
            repo.getDatabaseManager().close();
        }
    }

    @Test
    void testSessionExporterJsonRoundTrip(@TempDir Path tempDir) {
        SqliteSnapshotRepository repo = new SqliteSnapshotRepository(":memory:");
        try {
            List<Snapshot> original = FakeSnapshotFactory.createTwelveStepTimeline();
            long sessionId = repo.createSession("App.java", "class App {}");
            repo.saveSnapshots(sessionId, original);

            Path jsonFile = tempDir.resolve("exported-session.json");
            SessionExporter.exportToJson(repo, sessionId, jsonFile.toString());

            List<Snapshot> imported = SessionExporter.importFromJson(jsonFile.toString());
            assertEquals(original.size(), imported.size());
            for (int i = 0; i < original.size(); i++) {
                assertSnapshotsEqual(original.get(i), imported.get(i));
            }
        } finally {
            repo.getDatabaseManager().close();
        }
    }

    static void assertSnapshotsEqual(Snapshot expected, Snapshot actual) {
        assertEquals(expected.getStepNumber(), actual.getStepNumber());
        assertEquals(expected.getLineNumber(), actual.getLineNumber());
        assertEquals(expected.getConsoleOutput(), actual.getConsoleOutput());
        assertEquals(expected.getStats(), actual.getStats());
        assertEquals(expected.getStaticReferences(), actual.getStaticReferences());
        assertEquals(new ArrayList<>(expected.getStaticReferences().keySet()),
                new ArrayList<>(actual.getStaticReferences().keySet()));

        // Stack frames & insertion order
        assertEquals(expected.getStack().size(), actual.getStack().size());
        for (int i = 0; i < expected.getStack().size(); i++) {
            StackFrameInfo f1 = expected.getStack().get(i);
            StackFrameInfo f2 = actual.getStack().get(i);
            assertEquals(f1.getClassName(), f2.getClassName());
            assertEquals(f1.getMethodName(), f2.getMethodName());
            assertEquals(f1.getLineNumber(), f2.getLineNumber());
            assertEquals(f1.getPrimitiveLocals(), f2.getPrimitiveLocals());
            assertEquals(f1.getReferenceLocals(), f2.getReferenceLocals());
        }

        // Heap objects & insertion order
        assertEquals(expected.getHeap().size(), actual.getHeap().size());
        for (int i = 0; i < expected.getHeap().size(); i++) {
            ObjectInfo o1 = expected.getHeap().get(i);
            ObjectInfo o2 = actual.getHeap().get(i);
            assertEquals(o1.getId(), o2.getId());
            assertEquals(o1.getClassName(), o2.getClassName());
            assertEquals(o1.getStatus(), o2.getStatus());
            assertEquals(o1.getCreatedAtStep(), o2.getCreatedAtStep());
            assertEquals(o1.getUnreachableAtStep(), o2.getUnreachableAtStep());
            assertEquals(o1.getCollectedAtStep(), o2.getCollectedAtStep());
            assertEquals(o1.getPrimitiveFields(), o2.getPrimitiveFields());
            assertEquals(o1.getReferenceFields(), o2.getReferenceFields());
        }

        // Edges & order
        assertEquals(expected.getEdges(), actual.getEdges());

        // Events & order
        assertEquals(expected.getEvents().size(), actual.getEvents().size());
        for (int i = 0; i < expected.getEvents().size(); i++) {
            RuntimeEvent e1 = expected.getEvents().get(i);
            RuntimeEvent e2 = actual.getEvents().get(i);
            assertEquals(e1.getStepNumber(), e2.getStepNumber());
            assertEquals(e1.getType(), e2.getType());
            assertEquals(e1.getObjectId(), e2.getObjectId());
            assertEquals(e1.getDescription(), e2.getDescription());
        }
    }
}