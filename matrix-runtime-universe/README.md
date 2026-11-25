# Matrix Runtime Universe

Matrix Runtime Universe is a Java 17 project that records a small Java program's execution and turns it into a step-by-step memory timeline. The JDI tracer records stack frames, local variables, objects, and references as `RawStep` data. The memory engine builds snapshots with reference edges, runtime events, object states, and statistics. SQLite stores the snapshots, and the timeline service reads them back for navigation and display.

## Build

Requirements: a **JDK 17** installation and Maven. Run all commands below from the `matrix-runtime-universe` folder.

Check that Maven uses Java 17:

```bash
mvn -version
```

On macOS, select an installed JDK 17 for the current terminal if necessary:

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
```

Build and run the tests:

```bash
mvn clean test
```

Expected final result: `BUILD SUCCESS`.

## Run the main program

After building, write the dependency classpath to a generated file:

```bash
mvn dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
```

The following commands use macOS/Linux shell syntax. Run a sample by its Java class name, without `.java`:

```bash
java --add-modules jdk.jdi -cp "target/classes:$(cat target/classpath.txt)" com.matrix.app.MatrixRuntimeApp Objects1
```

Class-name input resolves to `samples/<JavaClassName>.java`. These samples have no package declaration, matching the existing tracer's source-compilation and launch behavior. The tracer records at most 200 steps per run.

Alternatively, load a recorded JSON list of `RawStep` objects without launching the tracer:

```bash
java --add-modules jdk.jdi -cp "target/classes:$(cat target/classpath.txt)" com.matrix.app.MatrixRuntimeApp samples-output/StaticReferenceDemo.json
```

`MatrixRuntimeApp` builds snapshots with `DefaultSnapshotBuilder`, creates a session in `matrix.db`, saves the snapshots through `SnapshotRepository`, and reads every stored step back through `TimelineService`. Each printed step includes its source line, stack, heap, static references, reference edges, events, statistics, and available console output. Each invocation creates a new stored session; `matrix.db` is generated runtime data, not a source file to commit.

Use recordings with consecutive step numbers starting at zero, as produced by the tracer and expected by timeline navigation. The engine simulates garbage collection; it does not observe actual JVM collection. The current tracer supplies empty `newOutput` values, so its recordings do not contain the sample's `System.out` output. Existing SQLite dependencies may print an SLF4J missing-binding warning; no logging dependency change is needed to run the app.

## Sample recordings

| Behavior | Java sample | RawStep recording |
| --- | --- | --- |
| Objects, field changes, and shared references | `samples/Objects1.java` | `samples-output/Objects1.json` |
| Four connected linked-list nodes | `samples/LinkedListDemo.java` | `samples-output/LinkedListDemo.json` |
| Recursive calls and stack frames | `samples/Recursion.java` | `samples-output/Recursion.json` |
| Objects becoming unreachable | `samples/GarbageDemo.java` | `samples-output/GarbageDemo.json` |
| An object kept reachable by a static field | `samples/StaticReferenceDemo.java` | `samples-output/StaticReferenceDemo.json` |

Regenerate these five recordings with the tracer:

```bash
java --add-modules jdk.jdi -cp "target/classes:$(cat target/classpath.txt)" com.matrix.tracer.TracerDemo
```

This overwrites those five JSON files in `samples-output/`. `Hello.java` remains an additional basic sample used by the existing tracer tests.

## Folder structure

```text
matrix-runtime-universe/
├── pom.xml
├── README.md
├── samples/                     Java programs to trace
├── samples-output/              JSON lists of RawStep recordings
├── src/
│   ├── main/java/com/matrix/
│   │   ├── app/                 MatrixRuntimeApp entry point
│   │   ├── model/               Shared data contract
│   │   ├── tracer/              JDI tracing and RawStep JSON support
│   │   ├── engine/              Snapshots, reachability, events, simulated GC
│   │   ├── storage/             SQLite persistence and snapshot JSON support
│   │   └── timeline/            Navigation and snapshot differences
│   └── test/java/com/matrix/    Existing module tests
├── tmp-compiled/                Generated sample .class files
├── target/                      Generated Maven build output
└── matrix.db                    Generated SQLite database after running the app
```

The shared `com.matrix.model` classes and the `StepProducer`, `SnapshotBuilder`, `SnapshotRepository`, and `TimelineService` contracts must be coordinated with all three contributors before changing them.
