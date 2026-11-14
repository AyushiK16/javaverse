package com.matrix.engine;

import com.matrix.model.ObjectInfo;
import com.matrix.model.ObjectStatus;
import com.matrix.model.ReferenceEdge;
import com.matrix.model.RuntimeEvent;
import com.matrix.model.Snapshot;
import com.matrix.model.StackFrameInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static com.matrix.engine.TestStepFactory.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EventDetectorTest {

    private final EventDetector detector = new EventDetector();
    private final ReferenceExtractor extractor = new ReferenceExtractor();

    /** Builds a snapshot by hand: stack + heap. The arrows are worked out automatically. */
    private Snapshot snap(int stepNumber, List<StackFrameInfo> stack, ObjectInfo... heap) {
        List<ObjectInfo> heapList = new ArrayList<>(List.of(heap));
        List<ReferenceEdge> edges = extractor.extract(stack, new LinkedHashMap<>(), heapList);
        Snapshot s = new Snapshot();
        s.setStepNumber(stepNumber);
        s.setStack(stack);
        s.setHeap(heapList);
        s.setEdges(edges);
        return s;
    }

    /** A GC result where nothing special happened. */
    private GcSimulator.StepResult noGc() {
        return new GcSimulator.StepResult(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), false, 0);
    }

    private ObjectInfo withStatus(ObjectInfo o, ObjectStatus status) {
        o.setStatus(status);
        return o;
    }

    /** Turns events into easy-to-read text like "FRAME_PUSHED: Frame main() pushed". */
    private List<String> summary(List<RuntimeEvent> events) {
        List<String> lines = new ArrayList<>();
        for (RuntimeEvent e : events) {
            lines.add(e.getType() + ": " + e.getDescription());
        }
        return lines;
    }

    @Test
    void firstStepReportsMainFramePushedAndObjectsCreated() {
        Snapshot cur = snap(0, frames(withLocalRef(frame("main"), "p", 1)), obj(1, "Person"));
        List<RuntimeEvent> events = detector.detect(null, cur, noGc());
        assertEquals(List.of(
                "FRAME_PUSHED: Frame main() pushed",
                "OBJECT_CREATED: Person#1 created",
                "REFERENCE_ADDED: Variable p in main() now points to Person#1"), summary(events));
    }

    @Test
    void framePushedNamesCallerAndCallee() {
        Snapshot prev = snap(0, frames(frame("main")));
        Snapshot cur = snap(1, frames(frame("main"), frame("greet")));
        assertEquals(List.of("FRAME_PUSHED: Frame main() -> greet() pushed"),
                summary(detector.detect(prev, cur, noGc())));
    }

    @Test
    void framePoppedNamesWhereWeReturnTo() {
        Snapshot prev = snap(1, frames(frame("main"), frame("greet")));
        Snapshot cur = snap(2, frames(frame("main")));
        assertEquals(List.of("FRAME_POPPED: Frame greet() popped (back to main())"),
                summary(detector.detect(prev, cur, noGc())));
    }

    @Test
    void newObjectAndNewReferenceAreReported() {
        Snapshot prev = snap(1, frames(withLocalRef(frame("main"), "p", 1)), obj(1, "Person"));
        Snapshot cur = snap(2, frames(withLocalRef(frame("main"), "p", 1)),
                withRef(obj(1, "Person"), "friend", 2), obj(2, "Person"));

        List<RuntimeEvent> events = detector.detect(prev, cur, noGc());

        assertEquals(List.of(
                "OBJECT_CREATED: Person#2 created",
                "REFERENCE_ADDED: Person#1.friend now points to Person#2"), summary(events));
        for (RuntimeEvent e : events) {
            assertEquals(2, e.getStepNumber());     // every event carries the current step
            assertEquals(2L, e.getObjectId());      // both are about object 2
        }
    }

    @Test
    void settingAVariableToNullRemovesTheReferenceAndMakesObjectUnreachable() {
        Snapshot prev = snap(3, frames(withLocalRef(frame("main"), "p", 1)), obj(1, "Person"));
        Snapshot cur = snap(4, frames(frame("main")), withStatus(obj(1, "Person"), ObjectStatus.UNREACHABLE));
        GcSimulator.StepResult gc = new GcSimulator.StepResult(
                new ArrayList<>(), new ArrayList<>(List.of(1L)), new ArrayList<>(), false, 0);

        assertEquals(List.of(
                "REFERENCE_REMOVED: Variable p in main() no longer points to Person#1",
                "OBJECT_UNREACHABLE: Person#1 became unreachable"),
                summary(detector.detect(prev, cur, gc)));
    }

    @Test
    void pointingAVariableAtAnotherObjectGivesRemovedThenAdded() {
        Snapshot prev = snap(5, frames(withLocalRef(frame("main"), "p", 1)), obj(1, "Person"), obj(2, "Person"));
        Snapshot cur = snap(6, frames(withLocalRef(frame("main"), "p", 2)), obj(1, "Person"), obj(2, "Person"));
        assertEquals(List.of(
                "REFERENCE_REMOVED: Variable p in main() no longer points to Person#1",
                "REFERENCE_ADDED: Variable p in main() now points to Person#2"),
                summary(detector.detect(prev, cur, noGc())));
    }

    @Test
    void changedFieldValueIsReported() {
        Snapshot prev = snap(1, frames(withLocalRef(frame("main"), "p", 1)),
                withValue(obj(1, "Person"), "age", "20"));
        Snapshot cur = snap(2, frames(withLocalRef(frame("main"), "p", 1)),
                withValue(obj(1, "Person"), "age", "21"));

        List<RuntimeEvent> events = detector.detect(prev, cur, noGc());

        assertEquals(List.of("VALUE_CHANGED: Person#1.age changed from 20 to 21"), summary(events));
        assertEquals(1L, events.get(0).getObjectId());
    }

    @Test
    void changedLocalVariableIsReported() {
        Snapshot prev = snap(1, frames(withLocalValue(frame("main"), "x", "1")));
        Snapshot cur = snap(2, frames(withLocalValue(frame("main"), "x", "2")));

        List<RuntimeEvent> events = detector.detect(prev, cur, noGc());

        assertEquals(List.of("VALUE_CHANGED: main(): x changed from 1 to 2"), summary(events));
        assertEquals(-1L, events.get(0).getObjectId());
    }

    @Test
    void newLocalVariableInAnExistingFrameIsReportedAsSet() {
        Snapshot prev = snap(1, frames(frame("main")));
        Snapshot cur = snap(2, frames(withLocalValue(frame("main"), "x", "5")));
        assertEquals(List.of("VALUE_CHANGED: main(): x set to 5"),
                summary(detector.detect(prev, cur, noGc())));
    }

    @Test
    void initialValuesOfANewObjectAreNotValueChanges() {
        Snapshot prev = snap(1, frames(frame("main")));
        Snapshot cur = snap(2, frames(withLocalRef(frame("main"), "p", 1)),
                withValue(obj(1, "Person"), "age", "20"));
        assertEquals(List.of(
                "OBJECT_CREATED: Person#1 created",
                "REFERENCE_ADDED: Variable p in main() now points to Person#1"),
                summary(detector.detect(prev, cur, noGc())));
    }

    @Test
    void gcRunAndCollectedObjectsAreReported() {
        Snapshot prev = snap(5, frames(frame("main")), withStatus(obj(1, "Person"), ObjectStatus.UNREACHABLE));
        Snapshot cur = snap(6, frames(frame("main")), withStatus(obj(1, "Person"), ObjectStatus.COLLECTED));
        GcSimulator.StepResult gc = new GcSimulator.StepResult(
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(List.of(1L)), true, 1);

        assertEquals(List.of(
                "GC_RUN: Garbage collection ran: 1 object(s) collected",
                "OBJECT_COLLECTED: Person#1 collected by GC"),
                summary(detector.detect(prev, cur, gc)));
    }

    @Test
    void arrowsOfAnObjectThatVanishedAreNotReportedAsRemoved() {
        // object 1 (collected last step) pointed to object 2; both are gone now
        Snapshot prev = snap(6, frames(frame("main")),
                withStatus(withRef(obj(1, "A"), "next", 2), ObjectStatus.COLLECTED),
                withStatus(obj(2, "B"), ObjectStatus.COLLECTED));
        Snapshot cur = snap(7, frames(frame("main")));
        assertEquals(List.of(), summary(detector.detect(prev, cur, noGc())));
    }

    @Test
    void arrowsOfAPoppedFrameAreNotReportedAsRemoved() {
        Snapshot prev = snap(3, frames(frame("main"), withLocalRef(frame("greet"), "who", 2)), obj(2, "Person"));
        Snapshot cur = snap(4, frames(frame("main")), obj(2, "Person"));
        assertEquals(List.of("FRAME_POPPED: Frame greet() popped (back to main())"),
                summary(detector.detect(prev, cur, noGc())));
    }

    @Test
    void nothingChangedGivesNoEvents() {
        Snapshot prev = snap(1, frames(withLocalRef(frame("main"), "p", 1)), obj(1, "Person"));
        Snapshot cur = snap(2, frames(withLocalRef(frame("main"), "p", 1)), obj(1, "Person"));
        assertEquals(List.of(), summary(detector.detect(prev, cur, noGc())));
    }
}