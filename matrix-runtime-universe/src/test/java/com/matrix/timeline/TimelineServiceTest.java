package com.matrix.timeline;

import com.matrix.model.EventType;
import com.matrix.model.ObjectInfo;
import com.matrix.model.ObjectStatus;
import com.matrix.model.ReferenceEdge;
import com.matrix.model.Snapshot;
import com.matrix.model.StackFrameInfo;
import com.matrix.storage.FakeSnapshotFactory;
import com.matrix.storage.SqliteSnapshotRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TimelineService and SnapshotDiffer.
 */
class TimelineServiceTest {

    @Test
    void testNavigationBoundsAndJumpToNextEventOfType() {
        SqliteSnapshotRepository repo = new SqliteSnapshotRepository(":memory:");
        try {
            List<Snapshot> timelineSteps = FakeSnapshotFactory.createTwelveStepTimeline();
            long sessionId = repo.createSession("App.java", "class App {}");
            repo.saveSnapshots(sessionId, timelineSteps);

            TimelineService timeline = new TimelineService(repo, sessionId);

            // Starts at step 0
            assertEquals(0, timeline.getCurrentStep());
            assertEquals(12, timeline.getTotalSteps());
            assertNotNull(timeline.current());
            assertEquals(0, timeline.current().getStepNumber());

            // Calling previous() or goTo(-5) at step 0 clamps safely to 0
            assertEquals(0, timeline.previous().getStepNumber());
            assertEquals(0, timeline.goTo(-5).getStepNumber());
            assertEquals(0, timeline.getCurrentStep());

            // Step forward with next()
            assertEquals(1, timeline.next().getStepNumber());
            assertEquals(1, timeline.getCurrentStep());

            // Jump to GC_RUN (which occurs at step 10 in FakeSnapshotFactory)
            Snapshot gcSnap = timeline.jumpToNextEventOfType(EventType.GC_RUN);
            assertNotNull(gcSnap);
            assertEquals(10, gcSnap.getStepNumber());
            assertEquals(10, timeline.getCurrentStep());

            // Searching again for GC_RUN after step 10 returns null and does NOT move currentStep
            assertNull(timeline.jumpToNextEventOfType(EventType.GC_RUN));
            assertEquals(10, timeline.getCurrentStep());

            // Calling next() or goTo(999) at the end clamps safely to 11
            assertEquals(11, timeline.next().getStepNumber());
            assertEquals(11, timeline.next().getStepNumber());
            assertEquals(11, timeline.goTo(999).getStepNumber());
            assertEquals(11, timeline.getCurrentStep());
        } finally {
            repo.getDatabaseManager().close();
        }
    }

    @Test
    void testSnapshotDifferExactAddedRemovedChangedAndEdges() {
        // Hand-built Snapshot A (step 3, stack depth 1)
        Map<String, String> obj1FieldsA = new LinkedHashMap<>();
        obj1FieldsA.put("score", "10");
        ObjectInfo obj1A = new ObjectInfo(1L, "Player", obj1FieldsA, new LinkedHashMap<>(),
                ObjectStatus.LIVE, 0, -1, -1);
        ObjectInfo obj2A = new ObjectInfo(2L, "TempItem", new LinkedHashMap<>(), new LinkedHashMap<>(),
                ObjectStatus.UNREACHABLE, 1, 3, -1);

        ReferenceEdge keptEdge = new ReferenceEdge("STACK", 0, "player", 1L);
        ReferenceEdge removedEdge = new ReferenceEdge("STACK", 0, "oldItem", 2L);

        Snapshot snapA = new Snapshot(
                3, 15,
                List.of(new StackFrameInfo("Game", "main", 15, new LinkedHashMap<>(), new LinkedHashMap<>())),
                List.of(obj1A, obj2A),
                List.of(keptEdge, removedEdge),
                new LinkedHashMap<>(),
                "",
                new ArrayList<>(),
                new LinkedHashMap<>()
        );

        // Hand-built Snapshot B (step 4, stack depth 2):
        // - obj1 has changed score ("10" -> "25") -> changedObjectIds = [1]
        // - obj2 is gone -> removedObjectIds = [2]
        // - obj3 is newly created -> addedObjectIds = [3]
        // - removedEdge is gone, addedEdge appears
        Map<String, String> obj1FieldsB = new LinkedHashMap<>();
        obj1FieldsB.put("score", "25");
        ObjectInfo obj1B = new ObjectInfo(1L, "Player", obj1FieldsB, new LinkedHashMap<>(),
                ObjectStatus.LIVE, 0, -1, -1);
        ObjectInfo obj3B = new ObjectInfo(3L, "Shield", new LinkedHashMap<>(), new LinkedHashMap<>(),
                ObjectStatus.LIVE, 4, -1, -1);

        ReferenceEdge addedEdge = new ReferenceEdge("STACK", 1, "shield", 3L);

        Snapshot snapB = new Snapshot(
                4, 22,
                List.of(
                        new StackFrameInfo("Game", "main", 16, new LinkedHashMap<>(), new LinkedHashMap<>()),
                        new StackFrameInfo("Game", "equip", 22, new LinkedHashMap<>(), new LinkedHashMap<>())
                ),
                List.of(obj1B, obj3B),
                List.of(keptEdge, addedEdge),
                new LinkedHashMap<>(),
                "",
                new ArrayList<>(),
                new LinkedHashMap<>()
        );

        SnapshotDiff diff = SnapshotDiffer.diff(snapA, snapB);

        assertEquals(3, diff.getFromStep());
        assertEquals(4, diff.getToStep());
        assertEquals(List.of(3L), diff.getAddedObjectIds());
        assertEquals(List.of(2L), diff.getRemovedObjectIds());
        assertEquals(List.of(1L), diff.getChangedObjectIds());
        assertEquals(List.of(addedEdge), diff.getAddedEdges());
        assertEquals(List.of(removedEdge), diff.getRemovedEdges());
        assertEquals(1, diff.getStackDepthFrom());
        assertEquals(2, diff.getStackDepthTo());
    }
}