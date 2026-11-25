package com.matrix.engine;

import com.matrix.model.EventType;
import com.matrix.model.ObjectInfo;
import com.matrix.model.ObjectStatus;
import com.matrix.model.RawStep;
import com.matrix.model.ReferenceEdge;
import com.matrix.model.Snapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.matrix.engine.TestStepFactory.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultSnapshotBuilderTest {

    // ------------------------------------------------------------ helpers

    /** A step where main has a local "p" pointing to a Person with the given id. */
    private RawStep live(int stepNumber, long id) {
        return step(stepNumber, frames(withLocalRef(frame("main"), "p", id)), obj(id, "Person"));
    }

    /** A step with only the main frame: no variables, no objects. */
    private RawStep empty(int stepNumber) {
        return step(stepNumber, frames(frame("main")));
    }

    private List<EventType> types(Snapshot s) {
        List<EventType> list = new ArrayList<>();
        s.getEvents().forEach(e -> list.add(e.getType()));
        return list;
    }

    private List<ObjectStatus> statuses(Snapshot s) {
        List<ObjectStatus> list = new ArrayList<>();
        for (ObjectInfo o : s.getHeap()) {
            list.add(o.getStatus());
        }
        return list;
    }

    private Map<String, Integer> stats(int live, int unreachable, int collectedSoFar, int stackDepth) {
        return Map.of("liveObjects", live, "unreachableObjects", unreachable,
                "collectedSoFar", collectedSoFar, "stackDepth", stackDepth);
    }

    // -------------------------------------------------------------- tests

    @Test
    void gcIntervalMustBeAtLeastOne() {
        assertThrows(IllegalArgumentException.class, () -> new DefaultSnapshotBuilder(0));
    }

    @Test
    void emptyOrMissingInputGivesEmptyList() {
        assertEquals(List.of(), new DefaultSnapshotBuilder().build(new ArrayList<>()));
        assertEquals(List.of(), new DefaultSnapshotBuilder().build(null));
    }

    @Test
    void singleStepWorks() {
        List<Snapshot> result = new DefaultSnapshotBuilder().build(new ArrayList<>(List.of(live(0, 1))));

        assertEquals(1, result.size());
        Snapshot s = result.get(0);
        assertEquals(List.of(ObjectStatus.LIVE), statuses(s));
        assertEquals(stats(1, 0, 0, 1), s.getStats());
        // the last step is always a GC step, so GC_RUN shows up here too
        assertEquals(List.of(EventType.FRAME_PUSHED, EventType.OBJECT_CREATED,
                EventType.REFERENCE_ADDED, EventType.GC_RUN), types(s));
    }

    @Test
    void scenarioA_framesArePushedAndPopped() {
        List<RawStep> steps = new ArrayList<>(List.of(
                step(0, frames(frame("main"))),
                step(1, frames(frame("main"), frame("greet"))),
                step(2, frames(frame("main")))));

        List<Snapshot> result = new DefaultSnapshotBuilder(100).build(steps);

        assertEquals(3, result.size());
        assertEquals(stats(0, 0, 0, 1), result.get(0).getStats());
        assertEquals(stats(0, 0, 0, 2), result.get(1).getStats());
        assertEquals(stats(0, 0, 0, 1), result.get(2).getStats());

        assertEquals(List.of(EventType.FRAME_PUSHED), types(result.get(1)));
        assertEquals("Frame main() -> greet() pushed", result.get(1).getEvents().get(0).getDescription());
        assertEquals(1, result.get(1).getEvents().get(0).getStepNumber());

        assertEquals(List.of(EventType.FRAME_POPPED, EventType.GC_RUN), types(result.get(2)));
        assertEquals("Frame greet() popped (back to main())", result.get(2).getEvents().get(0).getDescription());
    }

    @Test
    void scenarioB_objectsAndReferences() {
        List<RawStep> steps = new ArrayList<>(List.of(
                step(0, frames(frame("main"))),
                live(1, 1),
                step(2, frames(withLocalRef(frame("main"), "p", 1)),
                        withRef(obj(1, "Person"), "friend", 2),
                        obj(2, "Person"))));

        List<Snapshot> result = new DefaultSnapshotBuilder(100).build(steps);

        assertEquals(List.of(EventType.OBJECT_CREATED, EventType.REFERENCE_ADDED), types(result.get(1)));

        Snapshot s2 = result.get(2);
        assertEquals(List.of(
                new ReferenceEdge("STACK", 0, "p", 1),
                new ReferenceEdge("OBJECT", 1, "friend", 2)), s2.getEdges());
        assertEquals(List.of(EventType.OBJECT_CREATED, EventType.REFERENCE_ADDED, EventType.GC_RUN), types(s2));
        assertEquals(List.of(ObjectStatus.LIVE, ObjectStatus.LIVE), statuses(s2));
    }

    @Test
    void scenarioC_garbageLifeCycle() {
        // Object 1 exists in steps 0-3 and is missing from step 4 on. gcInterval 6, 8 steps.
        List<RawStep> steps = new ArrayList<>();
        for (int n = 0; n <= 7; n++) {
            steps.add(n <= 3 ? live(n, 1) : empty(n));
        }

        List<Snapshot> r = new DefaultSnapshotBuilder(6).build(steps);

        assertEquals(8, r.size());

        // statuses of the heap in every step
        for (int n = 0; n <= 3; n++) {
            assertEquals(List.of(ObjectStatus.LIVE), statuses(r.get(n)));
        }
        assertEquals(List.of(ObjectStatus.UNREACHABLE), statuses(r.get(4)));
        assertEquals(List.of(ObjectStatus.UNREACHABLE), statuses(r.get(5)));
        assertEquals(List.of(ObjectStatus.COLLECTED), statuses(r.get(6)));
        assertEquals(List.of(), statuses(r.get(7)));

        // events
        assertEquals(List.of(EventType.FRAME_PUSHED, EventType.OBJECT_CREATED, EventType.REFERENCE_ADDED), types(r.get(0)));
        for (int n = 1; n <= 3; n++) {
            assertEquals(List.of(), types(r.get(n)));
        }
        assertEquals(List.of(EventType.REFERENCE_REMOVED, EventType.OBJECT_UNREACHABLE), types(r.get(4)));
        assertEquals(List.of(), types(r.get(5)));
        assertEquals(List.of(EventType.GC_RUN, EventType.OBJECT_COLLECTED), types(r.get(6)));
        assertEquals(List.of(EventType.GC_RUN), types(r.get(7)));   // last step is a GC step too

        // stats in every step
        for (int n = 0; n <= 3; n++) {
            assertEquals(stats(1, 0, 0, 1), r.get(n).getStats());
        }
        assertEquals(stats(0, 1, 0, 1), r.get(4).getStats());
        assertEquals(stats(0, 1, 0, 1), r.get(5).getStats());
        assertEquals(stats(0, 0, 1, 1), r.get(6).getStats());
        assertEquals(stats(0, 0, 1, 1), r.get(7).getStats());
    }

    @Test
    void scenarioD_cyclesDoNotLoopForever() {
        // a live cycle: main -> A <-> B
        List<RawStep> liveCycle = new ArrayList<>();
        for (int n = 0; n <= 1; n++) {
            liveCycle.add(step(n, frames(withLocalRef(frame("main"), "a", 1)),
                    withRef(obj(1, "A"), "other", 2),
                    withRef(obj(2, "B"), "other", 1)));
        }
        List<Snapshot> r1 = new DefaultSnapshotBuilder(100).build(liveCycle);
        assertEquals(List.of(ObjectStatus.LIVE, ObjectStatus.LIVE), statuses(r1.get(1)));

        // the same cycle, but nobody points to it any more: both are garbage
        List<RawStep> garbageCycle = new ArrayList<>();
        garbageCycle.add(step(0, frames(withLocalRef(frame("main"), "a", 1)),
                withRef(obj(1, "A"), "other", 2),
                withRef(obj(2, "B"), "other", 1)));
        garbageCycle.add(empty(1));
        garbageCycle.add(empty(2));
        List<Snapshot> r2 = new DefaultSnapshotBuilder(100).build(garbageCycle);
        assertEquals(List.of(ObjectStatus.UNREACHABLE, ObjectStatus.UNREACHABLE), statuses(r2.get(1)));
        assertEquals(List.of(ObjectStatus.COLLECTED, ObjectStatus.COLLECTED), statuses(r2.get(2)));
    }

    @Test
    void scenarioE_objectOnlyReferencedByGarbageBecomesUnreachableToo() {
        List<RawStep> steps = new ArrayList<>();
        for (int n = 0; n <= 1; n++) {
            steps.add(step(n, frames(withLocalRef(frame("main"), "a", 1)),
                    withRef(obj(1, "A"), "child", 2),
                    obj(2, "B")));
        }
        steps.add(empty(2));
        steps.add(empty(3));

        List<Snapshot> r = new DefaultSnapshotBuilder(100).build(steps);

        Snapshot s2 = r.get(2);
        assertEquals(List.of(ObjectStatus.UNREACHABLE, ObjectStatus.UNREACHABLE), statuses(s2));
        assertEquals(List.of(EventType.REFERENCE_REMOVED, EventType.OBJECT_UNREACHABLE,
                EventType.OBJECT_UNREACHABLE), types(s2));
        assertEquals(stats(0, 2, 0, 1), s2.getStats());
        // unreachable objects are still in the heap, so their arrows are still listed
        assertEquals(List.of(new ReferenceEdge("OBJECT", 1, "child", 2)), s2.getEdges());

        assertEquals(List.of(ObjectStatus.COLLECTED, ObjectStatus.COLLECTED), statuses(r.get(3)));
    }

    @Test
    void consoleOutputIsCumulativeAndLineNumbersAreCopied() {
        RawStep s0 = empty(0);
        s0.setNewOutput("Hello\n");
        RawStep s1 = empty(1);
        s1.setNewOutput("");
        s1.setLineNumber(42);
        RawStep s2 = empty(2);
        s2.setNewOutput("World\n");

        List<Snapshot> r = new DefaultSnapshotBuilder(100).build(new ArrayList<>(List.of(s0, s1, s2)));

        assertEquals("Hello\n", r.get(0).getConsoleOutput());
        assertEquals("Hello\n", r.get(1).getConsoleOutput());
        assertEquals("Hello\nWorld\n", r.get(2).getConsoleOutput());
        assertEquals(42, r.get(1).getLineNumber());
        assertEquals(2, r.get(2).getStepNumber());
    }

    @Test
    void staticReferencesAreCopiedAndGetArrows() {
        RawStep s0 = withStatic(step(0, frames(frame("main")), obj(5, "Cache")), "Main.cache", 5);

        Snapshot s = new DefaultSnapshotBuilder(100).build(new ArrayList<>(List.of(s0))).get(0);

        assertEquals(Map.of("Main.cache", 5L), s.getStaticReferences());
        assertEquals(List.of(new ReferenceEdge("STATIC", -1, "Main.cache", 5)), s.getEdges());
        assertEquals(List.of(ObjectStatus.LIVE), statuses(s));
    }

    @Test
    void inputIsNeverChanged() {
        List<RawStep> steps = new ArrayList<>();
        for (int n = 0; n <= 7; n++) {
            steps.add(n <= 3 ? live(n, 1) : empty(n));
        }
        String before = steps.toString();

        List<Snapshot> result = new DefaultSnapshotBuilder(6).build(steps);

        // even if we change the snapshots afterwards, the input must stay as it was
        for (Snapshot s : result) {
            if (!s.getStack().isEmpty()) {
                s.getStack().get(0).getPrimitiveLocals().put("hack", "1");
            }
            if (!s.getHeap().isEmpty()) {
                s.getHeap().get(0).setClassName("HACKED");
            }
        }

        assertEquals(8, steps.size());
        assertEquals(before, steps.toString());
    }

    @Test
    void buildingTwiceGivesTheSameResult() {
        List<RawStep> steps = new ArrayList<>();
        for (int n = 0; n <= 7; n++) {
            steps.add(n <= 3 ? live(n, 1) : empty(n));
        }
        DefaultSnapshotBuilder builder = new DefaultSnapshotBuilder(6);

        List<Snapshot> first = builder.build(steps);
        List<Snapshot> second = builder.build(steps);

        assertEquals(first.toString(), second.toString());
    }

    @Test
    void defaultGcIntervalIsTen() {
        // Object 1 exists in steps 0-2, then it is garbage. 12 steps (0..11).
        List<RawStep> steps = new ArrayList<>();
        for (int n = 0; n <= 11; n++) {
            steps.add(n <= 2 ? live(n, 1) : empty(n));
        }

        List<Snapshot> r = new DefaultSnapshotBuilder().build(steps);

        assertEquals(List.of(ObjectStatus.UNREACHABLE), statuses(r.get(3)));
        assertEquals(List.of(ObjectStatus.UNREACHABLE), statuses(r.get(9)));
        assertEquals(List.of(ObjectStatus.COLLECTED), statuses(r.get(10)));
        assertEquals(List.of(), statuses(r.get(11)));
        assertEquals(List.of(EventType.GC_RUN, EventType.OBJECT_COLLECTED), types(r.get(10)));
    }
}