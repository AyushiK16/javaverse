package com.matrix.storage;

import com.matrix.model.EventType;
import com.matrix.model.ObjectInfo;
import com.matrix.model.ObjectStatus;
import com.matrix.model.ReferenceEdge;
import com.matrix.model.RuntimeEvent;
import com.matrix.model.Snapshot;
import com.matrix.model.StackFrameInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates realistic fake Snapshots by hand so Person 3's storage and timeline
 * modules can be thoroughly tested without waiting for Person 1 or Person 2.
 */
public class FakeSnapshotFactory {

    /**
     * Builds a realistic 12-step execution timeline (steps 0..11) featuring:
     * - Stack growth and shrinkage (depth 1 -> 2 -> 3 -> 2 -> 1)
     * - Objects with both primitive and reference fields
     * - Static references
     * - An UNREACHABLE object (Node#3 at step 8)
     * - A GC_RUN and COLLECTED object (Node#3 at step 10, gone at step 11)
     * - All 9 EventType values used at least once
     */
    public static List<Snapshot> createTwelveStepTimeline() {
        List<Snapshot> steps = new ArrayList<>();

        // Shared objects reused across steps (copied per step to keep snapshots independent)
        ObjectInfo person1Initial = new ObjectInfo(
                1L, "Person",
                orderedMap("name", "Alice", "age", "20"),
                new LinkedHashMap<>(),
                ObjectStatus.LIVE, 1, -1, -1
        );
        ObjectInfo person1WithFriend = new ObjectInfo(
                1L, "Person",
                orderedMap("name", "Alice", "age", "20"),
                orderedRefMap("friend", 2L),
                ObjectStatus.LIVE, 1, -1, -1
        );
        ObjectInfo person1Aged = new ObjectInfo(
                1L, "Person",
                orderedMap("name", "Alice", "age", "21"),
                orderedRefMap("friend", 2L),
                ObjectStatus.LIVE, 1, -1, -1
        );
        ObjectInfo person2 = new ObjectInfo(
                2L, "Person",
                orderedMap("name", "Bob", "age", "22"),
                new LinkedHashMap<>(),
                ObjectStatus.LIVE, 3, -1, -1
        );
        ObjectInfo node3Live = new ObjectInfo(
                3L, "Node",
                orderedMap("value", "99"),
                new LinkedHashMap<>(),
                ObjectStatus.LIVE, 6, -1, -1
        );
        ObjectInfo node3Unreachable = new ObjectInfo(
                3L, "Node",
                orderedMap("value", "99"),
                new LinkedHashMap<>(),
                ObjectStatus.UNREACHABLE, 6, 8, -1
        );
        ObjectInfo node3Collected = new ObjectInfo(
                3L, "Node",
                orderedMap("value", "99"),
                new LinkedHashMap<>(),
                ObjectStatus.COLLECTED, 6, 8, 10
        );

        // Step 0: main() pushed (stack depth = 1)
        steps.add(new Snapshot(
                0, 10,
                List.of(frame("App", "main", 10, orderedMap("argsCount", "0"), new LinkedHashMap<>())),
                new ArrayList<>(),
                new ArrayList<>(),
                new LinkedHashMap<>(),
                "",
                List.of(new RuntimeEvent(0, EventType.FRAME_PUSHED, -1, "Frame App.main() pushed")),
                stats(0, 0, 0, 1)
        ));

        // Step 1: Person#1 created, static ref App.leader -> 1, stack ref p -> 1
        steps.add(new Snapshot(
                1, 11,
                List.of(frame("App", "main", 11, orderedMap("argsCount", "0"), orderedRefMap("p", 1L))),
                List.of(copyObj(person1Initial)),
                List.of(
                        new ReferenceEdge("STACK", 0, "p", 1L),
                        new ReferenceEdge("STATIC", -1, "App.leader", 1L)
                ),
                orderedRefMap("App.leader", 1L),
                "Init Alice\n",
                List.of(
                        new RuntimeEvent(1, EventType.OBJECT_CREATED, 1L, "Person#1 created"),
                        new RuntimeEvent(1, EventType.REFERENCE_ADDED, 1L, "main.p -> Person#1")
                ),
                stats(1, 0, 0, 1)
        ));

        // Step 2: helper() called (stack depth = 2)
        steps.add(new Snapshot(
                2, 20,
                List.of(
                        frame("App", "main", 12, orderedMap("argsCount", "0"), orderedRefMap("p", 1L)),
                        frame("App", "helper", 20, orderedMap("flag", "true"), orderedRefMap("target", 1L))
                ),
                List.of(copyObj(person1Initial)),
                List.of(
                        new ReferenceEdge("STACK", 0, "p", 1L),
                        new ReferenceEdge("STACK", 1, "target", 1L),
                        new ReferenceEdge("STATIC", -1, "App.leader", 1L)
                ),
                orderedRefMap("App.leader", 1L),
                "Init Alice\n",
                List.of(new RuntimeEvent(2, EventType.FRAME_PUSHED, -1, "Frame App.helper() pushed")),
                stats(1, 0, 0, 2)
        ));

        // Step 3: buildFriend() called (stack depth = 3) and Person#2 created
        steps.add(new Snapshot(
                3, 30,
                List.of(
                        frame("App", "main", 12, orderedMap("argsCount", "0"), orderedRefMap("p", 1L)),
                        frame("App", "helper", 21, orderedMap("flag", "true"), orderedRefMap("target", 1L)),
                        frame("App", "buildFriend", 30, new LinkedHashMap<>(), orderedRefMap("f", 2L))
                ),
                List.of(copyObj(person1Initial), copyObj(person2)),
                List.of(
                        new ReferenceEdge("STACK", 0, "p", 1L),
                        new ReferenceEdge("STACK", 1, "target", 1L),
                        new ReferenceEdge("STACK", 2, "f", 2L),
                        new ReferenceEdge("STATIC", -1, "App.leader", 1L)
                ),
                orderedRefMap("App.leader", 1L),
                "Init Alice\n",
                List.of(
                        new RuntimeEvent(3, EventType.FRAME_PUSHED, -1, "Frame App.buildFriend() pushed"),
                        new RuntimeEvent(3, EventType.OBJECT_CREATED, 2L, "Person#2 created")
                ),
                stats(2, 0, 0, 3)
        ));

        // Step 4: Person#1.friend -> Person#2 added
        steps.add(new Snapshot(
                4, 31,
                List.of(
                        frame("App", "main", 12, orderedMap("argsCount", "0"), orderedRefMap("p", 1L)),
                        frame("App", "helper", 21, orderedMap("flag", "true"), orderedRefMap("target", 1L)),
                        frame("App", "buildFriend", 31, new LinkedHashMap<>(), orderedRefMap("f", 2L))
                ),
                List.of(copyObj(person1WithFriend), copyObj(person2)),
                List.of(
                        new ReferenceEdge("STACK", 0, "p", 1L),
                        new ReferenceEdge("STACK", 1, "target", 1L),
                        new ReferenceEdge("STACK", 2, "f", 2L),
                        new ReferenceEdge("STATIC", -1, "App.leader", 1L),
                        new ReferenceEdge("OBJECT", 1L, "friend", 2L)
                ),
                orderedRefMap("App.leader", 1L),
                "Init Alice\n",
                List.of(new RuntimeEvent(4, EventType.REFERENCE_ADDED, 2L, "Person#1.friend -> Person#2")),
                stats(2, 0, 0, 3)
        ));

        // Step 5: Person#1.age changed from "20" to "21" (VALUE_CHANGED)
        steps.add(new Snapshot(
                5, 32,
                List.of(
                        frame("App", "main", 12, orderedMap("argsCount", "0"), orderedRefMap("p", 1L)),
                        frame("App", "helper", 21, orderedMap("flag", "true"), orderedRefMap("target", 1L)),
                        frame("App", "buildFriend", 32, new LinkedHashMap<>(), orderedRefMap("f", 2L))
                ),
                List.of(copyObj(person1Aged), copyObj(person2)),
                List.of(
                        new ReferenceEdge("STACK", 0, "p", 1L),
                        new ReferenceEdge("STACK", 1, "target", 1L),
                        new ReferenceEdge("STACK", 2, "f", 2L),
                        new ReferenceEdge("STATIC", -1, "App.leader", 1L),
                        new ReferenceEdge("OBJECT", 1L, "friend", 2L)
                ),
                orderedRefMap("App.leader", 1L),
                "Init Alice\n",
                List.of(new RuntimeEvent(5, EventType.VALUE_CHANGED, 1L, "Person#1.age changed 20 -> 21")),
                stats(2, 0, 0, 3)
        ));

        // Step 6: Temporary Node#3 created in buildFriend()
        steps.add(new Snapshot(
                6, 33,
                List.of(
                        frame("App", "main", 12, orderedMap("argsCount", "0"), orderedRefMap("p", 1L)),
                        frame("App", "helper", 21, orderedMap("flag", "true"), orderedRefMap("target", 1L)),
                        frame("App", "buildFriend", 33, new LinkedHashMap<>(), orderedRefMap("f", 2L, "tempNode", 3L))
                ),
                List.of(copyObj(person1Aged), copyObj(person2), copyObj(node3Live)),
                List.of(
                        new ReferenceEdge("STACK", 0, "p", 1L),
                        new ReferenceEdge("STACK", 1, "target", 1L),
                        new ReferenceEdge("STACK", 2, "f", 2L),
                        new ReferenceEdge("STACK", 2, "tempNode", 3L),
                        new ReferenceEdge("STATIC", -1, "App.leader", 1L),
                        new ReferenceEdge("OBJECT", 1L, "friend", 2L)
                ),
                orderedRefMap("App.leader", 1L),
                "Init Alice\nNode temp\n",
                List.of(
                        new RuntimeEvent(6, EventType.OBJECT_CREATED, 3L, "Node#3 created"),
                        new RuntimeEvent(6, EventType.REFERENCE_ADDED, 3L, "buildFriend.tempNode -> Node#3")
                ),
                stats(3, 0, 0, 3)
        ));

        // Step 7: buildFriend() pops (stack depth = 2), Node#3 still temporarily referenced by helper
        steps.add(new Snapshot(
                7, 22,
                List.of(
                        frame("App", "main", 12, orderedMap("argsCount", "0"), orderedRefMap("p", 1L)),
                        frame("App", "helper", 22, orderedMap("flag", "true"), orderedRefMap("target", 1L, "orphanRef", 3L))
                ),
                List.of(copyObj(person1Aged), copyObj(person2), copyObj(node3Live)),
                List.of(
                        new ReferenceEdge("STACK", 0, "p", 1L),
                        new ReferenceEdge("STACK", 1, "target", 1L),
                        new ReferenceEdge("STACK", 1, "orphanRef", 3L),
                        new ReferenceEdge("STATIC", -1, "App.leader", 1L),
                        new ReferenceEdge("OBJECT", 1L, "friend", 2L)
                ),
                orderedRefMap("App.leader", 1L),
                "Init Alice\nNode temp\n",
                List.of(new RuntimeEvent(7, EventType.FRAME_POPPED, -1, "Frame App.buildFriend() popped")),
                stats(3, 0, 0, 2)
        ));

        // Step 8: orphanRef cleared -> REFERENCE_REMOVED + Node#3 becomes UNREACHABLE
        steps.add(new Snapshot(
                8, 23,
                List.of(
                        frame("App", "main", 12, orderedMap("argsCount", "0"), orderedRefMap("p", 1L)),
                        frame("App", "helper", 23, orderedMap("flag", "true"), orderedRefMap("target", 1L))
                ),
                List.of(copyObj(person1Aged), copyObj(person2), copyObj(node3Unreachable)),
                List.of(
                        new ReferenceEdge("STACK", 0, "p", 1L),
                        new ReferenceEdge("STACK", 1, "target", 1L),
                        new ReferenceEdge("STATIC", -1, "App.leader", 1L),
                        new ReferenceEdge("OBJECT", 1L, "friend", 2L)
                ),
                orderedRefMap("App.leader", 1L),
                "Init Alice\nNode temp\n",
                List.of(
                        new RuntimeEvent(8, EventType.REFERENCE_REMOVED, 3L, "helper.orphanRef removed"),
                        new RuntimeEvent(8, EventType.OBJECT_UNREACHABLE, 3L, "Node#3 became unreachable")
                ),
                stats(2, 1, 0, 2)
        ));

        // Step 9: helper() pops (stack depth = 1), Node#3 still UNREACHABLE awaiting GC
        steps.add(new Snapshot(
                9, 13,
                List.of(frame("App", "main", 13, orderedMap("argsCount", "0"), orderedRefMap("p", 1L))),
                List.of(copyObj(person1Aged), copyObj(person2), copyObj(node3Unreachable)),
                List.of(
                        new ReferenceEdge("STACK", 0, "p", 1L),
                        new ReferenceEdge("STATIC", -1, "App.leader", 1L),
                        new ReferenceEdge("OBJECT", 1L, "friend", 2L)
                ),
                orderedRefMap("App.leader", 1L),
                "Init Alice\nNode temp\n",
                List.of(new RuntimeEvent(9, EventType.FRAME_POPPED, -1, "Frame App.helper() popped")),
                stats(2, 1, 0, 1)
        ));

        // Step 10: GC runs! GC_RUN + Node#3 becomes COLLECTED (shown once in heap)
        steps.add(new Snapshot(
                10, 14,
                List.of(frame("App", "main", 14, orderedMap("argsCount", "0"), orderedRefMap("p", 1L))),
                List.of(copyObj(person1Aged), copyObj(person2), copyObj(node3Collected)),
                List.of(
                        new ReferenceEdge("STACK", 0, "p", 1L),
                        new ReferenceEdge("STATIC", -1, "App.leader", 1L),
                        new ReferenceEdge("OBJECT", 1L, "friend", 2L)
                ),
                orderedRefMap("App.leader", 1L),
                "Init Alice\nNode temp\n",
                List.of(
                        new RuntimeEvent(10, EventType.GC_RUN, -1, "Garbage collection executed at step 10"),
                        new RuntimeEvent(10, EventType.OBJECT_COLLECTED, 3L, "Node#3 collected")
                ),
                stats(2, 0, 1, 1)
        ));

        // Step 11: Post-GC step — Node#3 is completely gone from heap
        steps.add(new Snapshot(
                11, 15,
                List.of(frame("App", "main", 15, orderedMap("argsCount", "0"), orderedRefMap("p", 1L))),
                List.of(copyObj(person1Aged), copyObj(person2)),
                List.of(
                        new ReferenceEdge("STACK", 0, "p", 1L),
                        new ReferenceEdge("STATIC", -1, "App.leader", 1L),
                        new ReferenceEdge("OBJECT", 1L, "friend", 2L)
                ),
                orderedRefMap("App.leader", 1L),
                "Init Alice\nNode temp\nDone\n",
                new ArrayList<>(),
                stats(2, 0, 1, 1)
        ));

        return steps;
    }

    /**
     * Generates {@code count} lightweight snapshots for performance testing (e.g., 1,000 steps).
     */
    public static List<Snapshot> createBulkSnapshots(int count) {
        List<Snapshot> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            StackFrameInfo mainFrame = frame(
                    "BulkApp", "main", 10 + (i % 20),
                    orderedMap("i", String.valueOf(i)),
                    orderedRefMap("item", 100L + i)
            );
            ObjectInfo obj = new ObjectInfo(
                    100L + i, "Item",
                    orderedMap("index", String.valueOf(i)),
                    new LinkedHashMap<>(),
                    ObjectStatus.LIVE, i, -1, -1
            );
            ReferenceEdge edge = new ReferenceEdge("STACK", 0, "item", 100L + i);
            RuntimeEvent event = new RuntimeEvent(i, EventType.OBJECT_CREATED, 100L + i, "Item#" + (100L + i));

            list.add(new Snapshot(
                    i,
                    10 + (i % 20),
                    List.of(mainFrame),
                    List.of(obj),
                    List.of(edge),
                    new LinkedHashMap<>(),
                    "Step " + i + "\n",
                    List.of(event),
                    stats(1, 0, 0, 1)
            ));
        }
        return list;
    }

    private static StackFrameInfo frame(String cls, String method, int line,
                                        Map<String, String> prims, Map<String, Long> refs) {
        return new StackFrameInfo(cls, method, line, prims, refs);
    }

    private static ObjectInfo copyObj(ObjectInfo src) {
        return new ObjectInfo(
                src.getId(),
                src.getClassName(),
                new LinkedHashMap<>(src.getPrimitiveFields()),
                new LinkedHashMap<>(src.getReferenceFields()),
                src.getStatus(),
                src.getCreatedAtStep(),
                src.getUnreachableAtStep(),
                src.getCollectedAtStep()
        );
    }

    private static Map<String, String> orderedMap(String... kv) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }

    private static Map<String, Long> orderedRefMap(Object... kv) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], (Long) kv[i + 1]);
        }
        return map;
    }

    private static Map<String, Integer> stats(int live, int unreachable, int collected, int depth) {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("liveObjects", live);
        map.put("unreachableObjects", unreachable);
        map.put("collectedSoFar", collected);
        map.put("stackDepth", depth);
        return map;
    }
}