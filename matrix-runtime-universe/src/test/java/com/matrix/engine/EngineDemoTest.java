package com.matrix.engine;

import com.matrix.model.EventType;
import com.matrix.model.ObjectInfo;
import com.matrix.model.ObjectStatus;
import com.matrix.model.RawStep;
import com.matrix.model.RuntimeEvent;
import com.matrix.model.Snapshot;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineDemoTest {

    private List<Snapshot> buildSnapshots() {
        return new DefaultSnapshotBuilder(EngineDemo.GC_INTERVAL).build(EngineDemo.buildDemoSteps());
    }

    @Test
    void demoStepsAreNumberedFromZeroWithoutGaps() {
        List<RawStep> steps = EngineDemo.buildDemoSteps();
        assertEquals(8, steps.size());
        for (int i = 0; i < steps.size(); i++) {
            assertEquals(i, steps.get(i).getStepNumber());
        }
    }

    @Test
    void demoShowsEveryTypeOfEvent() {
        Set<EventType> seen = new HashSet<>();
        for (Snapshot s : buildSnapshots()) {
            for (RuntimeEvent e : s.getEvents()) {
                seen.add(e.getType());
            }
        }
        assertEquals(new HashSet<>(Arrays.asList(EventType.values())), seen);
    }

    @Test
    void demoEndsWithAliceAndBobCollected() {
        List<Snapshot> snapshots = buildSnapshots();

        // step 6: both are garbage but still in the heap
        for (ObjectInfo obj : snapshots.get(6).getHeap()) {
            assertEquals(ObjectStatus.UNREACHABLE, obj.getStatus());
        }
        assertEquals(2, snapshots.get(6).getHeap().size());

        // step 7: the garbage collector removes both
        Snapshot last = snapshots.get(7);
        assertEquals(2, last.getHeap().size());
        for (ObjectInfo obj : last.getHeap()) {
            assertEquals(ObjectStatus.COLLECTED, obj.getStatus());
        }
        assertEquals(Map.of("liveObjects", 0, "unreachableObjects", 0,
                "collectedSoFar", 2, "stackDepth", 1), last.getStats());
        assertEquals("Hello, Alice\nDone\n", last.getConsoleOutput());
    }

    @Test
    void formattedSnapshotMentionsStepEventsAndStats() {
        String text = EngineDemo.format(buildSnapshots().get(7));
        assertTrue(text.contains("=== Step 7"));
        assertTrue(text.contains("GC_RUN"));
        assertTrue(text.contains("Person#1 collected by GC"));
        assertTrue(text.contains("collectedSoFar=2"));
    }
}