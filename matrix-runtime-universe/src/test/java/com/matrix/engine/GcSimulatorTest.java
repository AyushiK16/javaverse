package com.matrix.engine;

import com.matrix.model.ObjectInfo;
import com.matrix.model.ObjectStatus;
import com.matrix.model.RawStep;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.matrix.engine.TestStepFactory.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GcSimulatorTest {

    /** A step where main has a local "p" pointing to a Person with the given id. */
    private RawStep rawWithLiveObject(int stepNumber, long id) {
        return step(stepNumber, frames(withLocalRef(frame("main"), "p", id)), obj(id, "Person"));
    }

    /** A step with nothing on the heap and no references. */
    private RawStep rawEmpty(int stepNumber) {
        return step(stepNumber, frames(frame("main")));
    }

    @Test
    void gcIntervalMustBeAtLeastOne() {
        assertThrows(IllegalArgumentException.class, () -> new GcSimulator(0));
    }

    @Test
    void reachableObjectStaysLiveAndRemembersCreationStep() {
        GcSimulator gc = new GcSimulator(100);
        gc.processStep(rawWithLiveObject(0, 1), false);
        GcSimulator.StepResult r1 = gc.processStep(rawWithLiveObject(1, 1), false);

        ObjectInfo o = r1.getHeap().get(0);
        assertEquals(ObjectStatus.LIVE, o.getStatus());
        assertEquals(0, o.getCreatedAtStep());
        assertEquals(false, r1.isGcRun());
    }

    @Test
    void garbageObjectIsUnreachableThenCollectedThenGone() {
        // Object 1 exists in steps 0-3, missing from step 4 on. gcInterval 6, 8 steps (0..7).
        GcSimulator gc = new GcSimulator(6);
        List<GcSimulator.StepResult> results = new ArrayList<>();
        for (int n = 0; n <= 7; n++) {
            RawStep raw = (n <= 3) ? rawWithLiveObject(n, 1) : rawEmpty(n);
            results.add(gc.processStep(raw, n == 7));
        }

        // steps 0-3: LIVE
        for (int n = 0; n <= 3; n++) {
            assertEquals(ObjectStatus.LIVE, results.get(n).getHeap().get(0).getStatus());
        }

        // step 4: UNREACHABLE but still in the heap, no GC yet
        GcSimulator.StepResult r4 = results.get(4);
        assertEquals(ObjectStatus.UNREACHABLE, r4.getHeap().get(0).getStatus());
        assertEquals(4, r4.getHeap().get(0).getUnreachableAtStep());
        assertEquals(List.of(1L), r4.getNewlyUnreachableIds());
        assertEquals(false, r4.isGcRun());

        // step 5: still UNREACHABLE, nothing new
        GcSimulator.StepResult r5 = results.get(5);
        assertEquals(ObjectStatus.UNREACHABLE, r5.getHeap().get(0).getStatus());
        assertEquals(List.of(), r5.getNewlyUnreachableIds());
        assertEquals(false, r5.isGcRun());

        // step 6: GC runs, object is COLLECTED (shown once)
        GcSimulator.StepResult r6 = results.get(6);
        assertEquals(ObjectStatus.COLLECTED, r6.getHeap().get(0).getStatus());
        assertEquals(6, r6.getHeap().get(0).getCollectedAtStep());
        assertEquals(List.of(1L), r6.getCollectedIds());
        assertEquals(true, r6.isGcRun());
        assertEquals(1, r6.getCollectedSoFar());

        // step 7 (last step): object is gone, collected total stays 1
        GcSimulator.StepResult r7 = results.get(7);
        assertEquals(0, r7.getHeap().size());
        assertEquals(1, r7.getCollectedSoFar());
    }

    @Test
    void objectOnlyReferencedByGarbageAlsoBecomesUnreachable() {
        GcSimulator gc = new GcSimulator(100);
        for (int n = 0; n <= 1; n++) {
            gc.processStep(step(n,
                    frames(withLocalRef(frame("main"), "a", 1)),
                    withRef(obj(1, "A"), "child", 2),
                    obj(2, "B")), false);
        }
        // step 2: main no longer points to A, so A and B (only reachable through A) are garbage
        GcSimulator.StepResult r2 = gc.processStep(rawEmpty(2), false);

        assertEquals(List.of(1L, 2L), r2.getNewlyUnreachableIds());
        assertEquals(2, r2.getHeap().size());
        assertEquals(false, r2.isGcRun());
    }

    @Test
    void unreachableObjectBecomesLiveAgainWhenReachable() {
        GcSimulator gc = new GcSimulator(100);
        gc.processStep(rawWithLiveObject(0, 1), false);

        GcSimulator.StepResult r1 = gc.processStep(rawEmpty(1), false);
        assertEquals(ObjectStatus.UNREACHABLE, r1.getHeap().get(0).getStatus());

        GcSimulator.StepResult r2 = gc.processStep(rawWithLiveObject(2, 1), false);
        assertEquals(ObjectStatus.LIVE, r2.getHeap().get(0).getStatus());
        assertEquals(-1, r2.getHeap().get(0).getUnreachableAtStep());
    }

    @Test
    void lastStepAlwaysRunsGcAndSweepsEverythingUnreachable() {
        GcSimulator gc = new GcSimulator(100);   // interval far away
        gc.processStep(rawWithLiveObject(0, 1), false);

        // step 1 is the last step: object becomes unreachable and is collected in the same step
        GcSimulator.StepResult r1 = gc.processStep(rawEmpty(1), true);

        ObjectInfo o = r1.getHeap().get(0);
        assertEquals(ObjectStatus.COLLECTED, o.getStatus());
        assertEquals(1, o.getUnreachableAtStep());
        assertEquals(1, o.getCollectedAtStep());
        assertEquals(List.of(1L), r1.getNewlyUnreachableIds());
        assertEquals(List.of(1L), r1.getCollectedIds());
        assertEquals(true, r1.isGcRun());
        assertEquals(1, r1.getCollectedSoFar());
    }

    @Test
    void heapObjectsAreCopiesAndInputIsNotChanged() {
        GcSimulator gc = new GcSimulator(100);
        RawStep raw0 = rawWithLiveObject(0, 1);
        ObjectInfo rawObject = raw0.getObjects().get(0);

        GcSimulator.StepResult r0 = gc.processStep(raw0, false);
        r0.getHeap().get(0).setClassName("HACKED");    // change the copy we got back

        GcSimulator.StepResult r1 = gc.processStep(rawWithLiveObject(1, 1), false);
        assertEquals("Person", r1.getHeap().get(0).getClassName());   // the simulator is not affected

        assertEquals(ObjectStatus.LIVE, rawObject.getStatus());       // input untouched
        assertEquals(-1, rawObject.getCreatedAtStep());               // input untouched
    }

    @Test
    void heapShowsLatestFieldValuesAndOldResultsDoNotChange() {
        GcSimulator gc = new GcSimulator(100);
        RawStep s0 = step(0, frames(withLocalRef(frame("main"), "p", 1)),
                withValue(obj(1, "Person"), "age", "20"));
        RawStep s1 = step(1, frames(withLocalRef(frame("main"), "p", 1)),
                withValue(obj(1, "Person"), "age", "21"));

        GcSimulator.StepResult r0 = gc.processStep(s0, false);
        GcSimulator.StepResult r1 = gc.processStep(s1, false);

        assertEquals("21", r1.getHeap().get(0).getPrimitiveFields().get("age"));
        assertEquals("20", r0.getHeap().get(0).getPrimitiveFields().get("age"));   // old result unchanged
    }
}