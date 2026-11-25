package com.matrix.engine;

import com.matrix.model.ObjectInfo;
import com.matrix.model.ObjectStatus;
import com.matrix.model.RawStep;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Simulates the garbage collector (GC).
 *
 * It remembers every object it has seen. At every step it does two things, like a real GC:
 *  - MARK:  objects that can no longer be reached become UNREACHABLE
 *           (an UNREACHABLE object that can be reached again goes back to LIVE).
 *  - SWEEP: on a GC step, every UNREACHABLE object becomes COLLECTED.
 *
 * A GC step is: step number > 0 and step number % gcInterval == 0, or the last step.
 * A COLLECTED object is shown once (in the step where it is collected) and then forgotten.
 *
 * Call processStep() once per RawStep, in order, on ONE GcSimulator object.
 */
public class GcSimulator {

    private final int gcInterval;
    private final ReachabilityAnalyzer analyzer = new ReachabilityAnalyzer();

    /** Objects we currently remember (LIVE or UNREACHABLE). Insertion order is kept. */
    private final Map<Long, ObjectInfo> tracked = new LinkedHashMap<>();
    private int collectedSoFar = 0;

    public GcSimulator() {
        this(10);
    }

    public GcSimulator(int gcInterval) {
        if (gcInterval < 1) {
            throw new IllegalArgumentException("gcInterval must be at least 1");
        }
        this.gcInterval = gcInterval;
    }

    /** What happened to the heap in one step. */
    public static class StepResult {
        private final List<ObjectInfo> heap;
        private final List<Long> newlyUnreachableIds;
        private final List<Long> collectedIds;
        private final boolean gcRun;
        private final int collectedSoFar;

        public StepResult(List<ObjectInfo> heap, List<Long> newlyUnreachableIds,
                          List<Long> collectedIds, boolean gcRun, int collectedSoFar) {
            this.heap = heap;
            this.newlyUnreachableIds = newlyUnreachableIds;
            this.collectedIds = collectedIds;
            this.gcRun = gcRun;
            this.collectedSoFar = collectedSoFar;
        }

        /** Objects for the snapshot heap: LIVE, UNREACHABLE, and COLLECTED (only in the step it was collected). Copies. */
        public List<ObjectInfo> getHeap() { return heap; }

        /** Ids that became UNREACHABLE in this step. */
        public List<Long> getNewlyUnreachableIds() { return newlyUnreachableIds; }

        /** Ids that became COLLECTED in this step. */
        public List<Long> getCollectedIds() { return collectedIds; }

        /** True if a garbage collection ran in this step. */
        public boolean isGcRun() { return gcRun; }

        /** How many objects have been collected up to and including this step. */
        public int getCollectedSoFar() { return collectedSoFar; }
    }

    public StepResult processStep(RawStep raw, boolean isLastStep) {
        int step = raw.getStepNumber();
        Set<Long> reachable = analyzer.findReachableIds(raw);

        // 1) Learn from the objects the tracer saw in this step (we only work on copies)
        for (ObjectInfo seen : raw.getObjects()) {
            ObjectInfo known = tracked.get(seen.getId());
            if (known == null) {
                known = copyOf(seen);
                known.setStatus(ObjectStatus.LIVE);
                known.setCreatedAtStep(step);
                known.setUnreachableAtStep(-1);
                known.setCollectedAtStep(-1);
                tracked.put(known.getId(), known);
            } else {
                known.setPrimitiveFields(new LinkedHashMap<>(seen.getPrimitiveFields()));
                known.setReferenceFields(new LinkedHashMap<>(seen.getReferenceFields()));
            }
        }

        // 2) MARK: reachable -> LIVE, not reachable -> UNREACHABLE
        List<Long> newlyUnreachable = new ArrayList<>();
        for (ObjectInfo obj : tracked.values()) {
            if (reachable.contains(obj.getId())) {
                if (obj.getStatus() == ObjectStatus.UNREACHABLE) {
                    obj.setStatus(ObjectStatus.LIVE);          // it came back!
                    obj.setUnreachableAtStep(-1);
                }
            } else if (obj.getStatus() == ObjectStatus.LIVE) {
                obj.setStatus(ObjectStatus.UNREACHABLE);
                obj.setUnreachableAtStep(step);
                newlyUnreachable.add(obj.getId());
            }
        }

        // 3) SWEEP: on a GC step, every UNREACHABLE object becomes COLLECTED
        boolean gcRun = isLastStep || (step > 0 && step % gcInterval == 0);
        List<Long> collected = new ArrayList<>();
        if (gcRun) {
            for (ObjectInfo obj : tracked.values()) {
                if (obj.getStatus() == ObjectStatus.UNREACHABLE) {
                    obj.setStatus(ObjectStatus.COLLECTED);
                    obj.setCollectedAtStep(step);
                    collected.add(obj.getId());
                }
            }
            collectedSoFar += collected.size();
        }

        // 4) The heap of this snapshot: copies of everything we remember right now
        List<ObjectInfo> heap = new ArrayList<>();
        for (ObjectInfo obj : tracked.values()) {
            heap.add(copyOf(obj));
        }

        // 5) Collected objects are shown only once, so forget them now
        tracked.values().removeIf(o -> o.getStatus() == ObjectStatus.COLLECTED);

        return new StepResult(heap, newlyUnreachable, collected, gcRun, collectedSoFar);
    }

    private static ObjectInfo copyOf(ObjectInfo o) {
        return new ObjectInfo(o.getId(), o.getClassName(),
                new LinkedHashMap<>(o.getPrimitiveFields()),
                new LinkedHashMap<>(o.getReferenceFields()),
                o.getStatus(), o.getCreatedAtStep(), o.getUnreachableAtStep(), o.getCollectedAtStep());
    }
}