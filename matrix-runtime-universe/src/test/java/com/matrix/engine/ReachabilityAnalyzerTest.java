package com.matrix.engine;

import com.matrix.model.RawStep;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.matrix.engine.TestStepFactory.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReachabilityAnalyzerTest {

    private final ReachabilityAnalyzer analyzer = new ReachabilityAnalyzer();

    @Test
    void emptyStepHasNothingReachable() {
        RawStep s = step(0, frames(frame("main")));
        assertEquals(Set.of(), analyzer.findReachableIds(s));
    }

    @Test
    void objectReferencedFromStackIsReachable() {
        RawStep s = step(0,
                frames(withLocalRef(frame("main"), "p", 1)),
                obj(1, "Person"), obj(2, "Person"));
        assertEquals(Set.of(1L), analyzer.findReachableIds(s));
    }

    @Test
    void followsAChainOfReferences() {
        RawStep s = step(0,
                frames(withLocalRef(frame("main"), "head", 1)),
                withRef(obj(1, "Node"), "next", 2),
                withRef(obj(2, "Node"), "next", 3),
                obj(3, "Node"),
                obj(4, "Node"));
        assertEquals(Set.of(1L, 2L, 3L), analyzer.findReachableIds(s));
    }

    @Test
    void cycleThatIsReachableDoesNotLoopForever() {
        RawStep s = step(0,
                frames(withLocalRef(frame("main"), "a", 1)),
                withRef(obj(1, "A"), "other", 2),
                withRef(obj(2, "B"), "other", 1));
        assertEquals(Set.of(1L, 2L), analyzer.findReachableIds(s));
    }

    @Test
    void staticReferenceIsARoot() {
        RawStep s = withStatic(
                step(0, frames(frame("main")), obj(5, "Cache"), obj(6, "Other")),
                "Main.cache", 5);
        assertEquals(Set.of(5L), analyzer.findReachableIds(s));
    }

    @Test
    void cycleWithNoRootIsGarbage() {
        RawStep s = step(0,
                frames(frame("main")),
                withRef(obj(1, "A"), "other", 2),
                withRef(obj(2, "B"), "other", 1));
        assertEquals(Set.of(), analyzer.findReachableIds(s));
    }

    @Test
    void objectOnlyReferencedByGarbageIsGarbage() {
        RawStep s = step(0,
                frames(frame("main")),
                withRef(obj(1, "A"), "child", 2),
                obj(2, "B"));
        assertEquals(Set.of(), analyzer.findReachableIds(s));
    }

    @Test
    void referencesFromLowerFramesCount() {
        RawStep s = step(0,
                frames(withLocalRef(frame("main"), "x", 7), frame("helper")),
                obj(7, "Thing"));
        assertEquals(Set.of(7L), analyzer.findReachableIds(s));
    }
}