package com.matrix.engine;

import com.matrix.model.ObjectInfo;
import com.matrix.model.RawStep;
import com.matrix.model.ReferenceEdge;
import com.matrix.model.RuntimeEvent;
import com.matrix.model.Snapshot;
import com.matrix.model.StackFrameInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small program that lets you SEE the memory engine work.
 *
 * It pretends the tracer watched this little Java program:
 *
 *     main() {
 *         int count = 0;
 *         Person a = new Person("Alice", 20);   // step 1
 *         Person b = new Person("Bob", 30);     // step 2
 *         greet(a);                             // step 3: prints "Hello, Alice"
 *         a.friend = b;  a.age++;  count = 1;   // step 4
 *         b = null;                             // step 5  (Bob is still reachable through a.friend)
 *         a = null;                             // step 6  (now nobody points to Alice or Bob)
 *         print("Done");                        // step 7  (garbage collection runs)
 *     }
 *
 * It then runs the engine and prints one snapshot per step.
 */
public class EngineDemo {

    /** Garbage collection every 7 steps (it also runs at the last step). */
    public static final int GC_INTERVAL = 7;

    public static void main(String[] args) {
        List<RawStep> rawSteps = buildDemoSteps();
        List<Snapshot> snapshots = new DefaultSnapshotBuilder(GC_INTERVAL).build(rawSteps);

        System.out.println("Matrix Runtime Universe - Memory Engine demo");
        System.out.println("Input: " + rawSteps.size() + " raw steps. Garbage collection every "
                + GC_INTERVAL + " steps.");
        System.out.println();
        for (Snapshot snapshot : snapshots) {
            System.out.println(format(snapshot));
        }
    }

    // ------------------------------------------------------------------
    // The pretend recording (what the tracer would have seen)
    // ------------------------------------------------------------------

    public static List<RawStep> buildDemoSteps() {
        List<RawStep> steps = new ArrayList<>();

        // Step 0: main() has just started
        steps.add(step(0, 3, stack(mainFrame(3, null, null, null)), ""));

        // Step 1: Person a = new Person("Alice", 20);
        steps.add(step(1, 5, stack(mainFrame(5, "0", 1L, null)), "",
                person(1, "Alice", 20, null)));

        // Step 2: Person b = new Person("Bob", 30);
        steps.add(step(2, 6, stack(mainFrame(6, "0", 1L, 2L)), "",
                person(1, "Alice", 20, null),
                person(2, "Bob", 30, null)));

        // Step 3: greet(a) is running (a second frame on the stack) and prints a line
        steps.add(step(3, 11, stack(mainFrame(7, "0", 1L, 2L), greetFrame(11, 1L)), "Hello, Alice\n",
                person(1, "Alice", 20, null),
                person(2, "Bob", 30, null)));

        // Step 4: back in main: a.friend = b; a.age++; count = 1;
        steps.add(step(4, 8, stack(mainFrame(8, "1", 1L, 2L)), "",
                person(1, "Alice", 21, 2L),
                person(2, "Bob", 30, null)));

        // Step 5: b = null  (Bob is still reachable through Alice's "friend")
        steps.add(step(5, 9, stack(mainFrame(9, "1", 1L, null)), "",
                person(1, "Alice", 21, 2L),
                person(2, "Bob", 30, null)));

        // Step 6: a = null  (nobody points to Alice or Bob any more, so the tracer lists no objects)
        steps.add(step(6, 10, stack(mainFrame(10, "1", null, null)), ""));

        // Step 7: the last line prints "Done"
        steps.add(step(7, 12, stack(mainFrame(12, "1", null, null)), "Done\n"));

        return steps;
    }

    private static RawStep step(int stepNumber, int lineNumber, List<StackFrameInfo> stack,
                                String output, ObjectInfo... objects) {
        return new RawStep(stepNumber, lineNumber, stack,
                new ArrayList<>(Arrays.asList(objects)), new LinkedHashMap<>(), output);
    }

    private static List<StackFrameInfo> stack(StackFrameInfo... frames) {
        return new ArrayList<>(Arrays.asList(frames));
    }

    /** The main() frame. Pass null for a variable that does not exist (or is null) yet. */
    private static StackFrameInfo mainFrame(int line, String count, Long a, Long b) {
        StackFrameInfo frame = new StackFrameInfo("Demo", "main", line,
                new LinkedHashMap<>(), new LinkedHashMap<>());
        if (count != null) {
            frame.getPrimitiveLocals().put("count", count);
        }
        if (a != null) {
            frame.getReferenceLocals().put("a", a);
        }
        if (b != null) {
            frame.getReferenceLocals().put("b", b);
        }
        return frame;
    }

    private static StackFrameInfo greetFrame(int line, long whoId) {
        StackFrameInfo frame = new StackFrameInfo("Demo", "greet", line,
                new LinkedHashMap<>(), new LinkedHashMap<>());
        frame.getReferenceLocals().put("who", whoId);
        return frame;
    }

    private static ObjectInfo person(long id, String name, int age, Long friendId) {
        ObjectInfo person = new ObjectInfo();
        person.setId(id);
        person.setClassName("Person");
        person.getPrimitiveFields().put("name", name);
        person.getPrimitiveFields().put("age", String.valueOf(age));
        if (friendId != null) {
            person.getReferenceFields().put("friend", friendId);
        }
        return person;
    }

    // ------------------------------------------------------------------
    // Printing one snapshot as readable text
    // ------------------------------------------------------------------

    public static String format(Snapshot s) {
        StringBuilder out = new StringBuilder();
        out.append("=== Step ").append(s.getStepNumber())
                .append(" (line ").append(s.getLineNumber()).append(") ===\n");

        out.append("Stack (bottom -> top):\n");
        if (s.getStack().isEmpty()) {
            out.append("  (empty)\n");
        }
        for (int i = 0; i < s.getStack().size(); i++) {
            StackFrameInfo frame = s.getStack().get(i);
            out.append("  [").append(i).append("] ").append(frame.getMethodName()).append("()")
                    .append("   values: ").append(values(frame.getPrimitiveLocals()))
                    .append("   refs: ").append(refs(frame.getReferenceLocals())).append("\n");
        }

        out.append("Heap:\n");
        if (s.getHeap().isEmpty()) {
            out.append("  (empty)\n");
        }
        for (ObjectInfo obj : s.getHeap()) {
            out.append("  #").append(obj.getId()).append(" ").append(obj.getClassName())
                    .append(" [").append(obj.getStatus()).append("]")
                    .append("   values: ").append(values(obj.getPrimitiveFields()))
                    .append("   refs: ").append(refs(obj.getReferenceFields())).append("\n");
        }

        if (!s.getStaticReferences().isEmpty()) {
            out.append("Statics: ").append(refs(s.getStaticReferences())).append("\n");
        }

        out.append("Arrows:\n");
        if (s.getEdges().isEmpty()) {
            out.append("  (none)\n");
        }
        for (ReferenceEdge edge : s.getEdges()) {
            out.append("  ").append(edgeText(edge)).append("\n");
        }

        out.append("Events:\n");
        if (s.getEvents().isEmpty()) {
            out.append("  (none)\n");
        }
        for (RuntimeEvent event : s.getEvents()) {
            out.append("  * ").append(event.getType()).append(": ").append(event.getDescription()).append("\n");
        }

        out.append("Stats: ");
        for (Map.Entry<String, Integer> stat : s.getStats().entrySet()) {
            out.append(stat.getKey()).append("=").append(stat.getValue()).append("  ");
        }
        out.append("\n");

        out.append("Console so far: \"").append(s.getConsoleOutput().replace("\n", "\\n")).append("\"\n");
        return out.toString();
    }

    private static String values(Map<String, String> map) {
        if (map.isEmpty()) {
            return "-";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> e : map.entrySet()) {
            parts.add(e.getKey() + "=" + e.getValue());
        }
        return String.join(", ", parts);
    }

    private static String refs(Map<String, Long> map) {
        if (map.isEmpty()) {
            return "-";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Long> e : map.entrySet()) {
            parts.add(e.getKey() + "->#" + e.getValue());
        }
        return String.join(", ", parts);
    }

    private static String edgeText(ReferenceEdge e) {
        if ("STACK".equals(e.getSourceKind())) {
            return "STACK[" + e.getSourceId() + "] " + e.getLabel() + " -> #" + e.getTargetId();
        }
        if ("STATIC".equals(e.getSourceKind())) {
            return "STATIC " + e.getLabel() + " -> #" + e.getTargetId();
        }
        return "#" + e.getSourceId() + "." + e.getLabel() + " -> #" + e.getTargetId();
    }
}