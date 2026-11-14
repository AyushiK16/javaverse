package com.matrix.engine;

import com.matrix.model.ObjectInfo;
import com.matrix.model.ObjectStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.matrix.engine.TestStepFactory.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StatsCalculatorTest {

    private final StatsCalculator calculator = new StatsCalculator();

    private static ObjectInfo objectWithStatus(long id, ObjectStatus status) {
        ObjectInfo o = obj(id, "Thing");
        o.setStatus(status);
        return o;
    }

    @Test
    void emptyInputGivesAllZeros() {
        Map<String, Integer> stats = calculator.calculate(new ArrayList<>(), new ArrayList<>(), 0);
        assertEquals(Map.of(
                "liveObjects", 0,
                "unreachableObjects", 0,
                "collectedSoFar", 0,
                "stackDepth", 0), stats);
    }

    @Test
    void countsLiveAndUnreachableSeparately() {
        List<ObjectInfo> heap = new ArrayList<>(List.of(
                objectWithStatus(1, ObjectStatus.LIVE),
                objectWithStatus(2, ObjectStatus.LIVE),
                objectWithStatus(3, ObjectStatus.UNREACHABLE)));
        Map<String, Integer> stats = calculator.calculate(
                frames(frame("main"), frame("helper")), heap, 3);
        assertEquals(Map.of(
                "liveObjects", 2,
                "unreachableObjects", 1,
                "collectedSoFar", 3,
                "stackDepth", 2), stats);
    }

    @Test
    void objectCollectedInThisStepIsNotCountedAsLiveOrUnreachable() {
        List<ObjectInfo> heap = new ArrayList<>(List.of(objectWithStatus(1, ObjectStatus.COLLECTED)));
        Map<String, Integer> stats = calculator.calculate(frames(frame("main")), heap, 1);
        assertEquals(Map.of(
                "liveObjects", 0,
                "unreachableObjects", 0,
                "collectedSoFar", 1,
                "stackDepth", 1), stats);
    }

    @Test
    void stackDepthIsTheNumberOfFrames() {
        Map<String, Integer> stats = calculator.calculate(
                frames(frame("a"), frame("b"), frame("c")), new ArrayList<>(), 0);
        assertEquals(3, stats.get("stackDepth"));
    }

    @Test
    void keysAreInTheContractOrder() {
        Map<String, Integer> stats = calculator.calculate(new ArrayList<>(), new ArrayList<>(), 0);
        assertEquals(
                List.of("liveObjects", "unreachableObjects", "collectedSoFar", "stackDepth"),
                new ArrayList<>(stats.keySet()));
    }

    @Test
    void nullInputsGiveAllZeros() {
        Map<String, Integer> stats = calculator.calculate(null, null, 0);
        assertEquals(Map.of(
                "liveObjects", 0,
                "unreachableObjects", 0,
                "collectedSoFar", 0,
                "stackDepth", 0), stats);
    }
}