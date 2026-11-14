package com.matrix.engine;

import com.matrix.model.ObjectInfo;
import com.matrix.model.ObjectStatus;
import com.matrix.model.ReferenceEdge;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.matrix.engine.TestStepFactory.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReferenceExtractorTest {

    private final ReferenceExtractor extractor = new ReferenceExtractor();

    private static Map<String, Long> noStatics() {
        return new LinkedHashMap<>();
    }

    private static Map<String, Long> oneStatic(String name, long target) {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put(name, target);
        return m;
    }

    @Test
    void emptyInputGivesNoEdges() {
        List<ReferenceEdge> edges = extractor.extract(new ArrayList<>(), noStatics(), new ArrayList<>());
        assertEquals(List.of(), edges);
    }

    @Test
    void stackEdgeUsesFrameIndexAsSourceId() {
        List<ReferenceEdge> edges = extractor.extract(
                frames(withLocalRef(frame("main"), "p", 1),
                       withLocalRef(frame("greet"), "who", 2)),
                noStatics(),
                new ArrayList<>());
        assertEquals(List.of(
                new ReferenceEdge("STACK", 0, "p", 1),
                new ReferenceEdge("STACK", 1, "who", 2)), edges);
    }

    @Test
    void staticEdgeUsesMinusOneAndClassFieldLabel() {
        List<ReferenceEdge> edges = extractor.extract(
                new ArrayList<>(), oneStatic("Main.cache", 5), new ArrayList<>());
        assertEquals(List.of(new ReferenceEdge("STATIC", -1, "Main.cache", 5)), edges);
    }

    @Test
    void objectEdgeUsesObjectIdAsSourceId() {
        List<ObjectInfo> heap = new ArrayList<>(List.of(
                withRef(obj(1, "Person"), "friend", 2),
                obj(2, "Person")));
        List<ReferenceEdge> edges = extractor.extract(new ArrayList<>(), noStatics(), heap);
        assertEquals(List.of(new ReferenceEdge("OBJECT", 1, "friend", 2)), edges);
    }

    @Test
    void primitiveFieldsAreNotEdges() {
        List<ObjectInfo> heap = new ArrayList<>(List.of(withValue(obj(1, "Person"), "age", "20")));
        List<ReferenceEdge> edges = extractor.extract(new ArrayList<>(), noStatics(), heap);
        assertEquals(List.of(), edges);
    }

    @Test
    void edgesOfUnreachableObjectsAreStillIncluded() {
        ObjectInfo old = withRef(obj(3, "Old"), "next", 4);
        old.setStatus(ObjectStatus.UNREACHABLE);
        List<ReferenceEdge> edges = extractor.extract(
                new ArrayList<>(), noStatics(), new ArrayList<>(List.of(old)));
        assertEquals(List.of(new ReferenceEdge("OBJECT", 3, "next", 4)), edges);
    }

    @Test
    void orderIsStackThenStaticThenObject() {
        List<ReferenceEdge> edges = extractor.extract(
                frames(withLocalRef(frame("main"), "p", 1)),
                oneStatic("Main.cache", 5),
                new ArrayList<>(List.of(withRef(obj(1, "Person"), "friend", 2))));
        assertEquals(List.of(
                new ReferenceEdge("STACK", 0, "p", 1),
                new ReferenceEdge("STATIC", -1, "Main.cache", 5),
                new ReferenceEdge("OBJECT", 1, "friend", 2)), edges);
    }

    @Test
    void nullInputsAreTreatedAsEmpty() {
        assertEquals(List.of(), extractor.extract(null, null, null));
    }
}