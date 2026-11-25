package com.matrix.app;

import com.matrix.engine.DefaultSnapshotBuilder;
import com.matrix.model.ObjectInfo;
import com.matrix.model.RawStep;
import com.matrix.model.ReferenceEdge;
import com.matrix.model.RuntimeEvent;
import com.matrix.model.Snapshot;
import com.matrix.model.StackFrameInfo;
import com.matrix.storage.DatabaseManager;
import com.matrix.storage.SnapshotRepository;
import com.matrix.storage.SqliteSnapshotRepository;
import com.matrix.timeline.TimelineService;
import com.matrix.tracer.JdiTracer;
import com.matrix.tracer.RawStepJson;
import com.matrix.tracer.StepProducer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class MatrixRuntimeApp {

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: MatrixRuntimeApp <JavaClassName|samples-output/file.json>");
            return;
        }

        String input = args[0];
        String sourceCode = "";
        List<RawStep> steps;

        if (input.endsWith(".json")) {
            steps = new RawStepJson().load(input);
        } else {
            Path sourceFile = Path.of("samples", input + ".java");
            sourceCode = Files.readString(sourceFile);
            StepProducer tracer = new JdiTracer();
            steps = tracer.trace(sourceFile.toString(), 200);
        }

        List<Snapshot> snapshots = new DefaultSnapshotBuilder().build(steps);
        DatabaseManager database = new DatabaseManager();
        try {
            SnapshotRepository repository = new SqliteSnapshotRepository(database);
            long sessionId = repository.createSession(input, sourceCode);
            repository.saveSnapshots(sessionId, snapshots);

            TimelineService timeline = new TimelineService(repository, sessionId);
            int totalSteps = timeline.getTotalSteps();
            System.out.println("Session " + sessionId + ": " + input);
            System.out.println("Saved " + totalSteps + " snapshots to matrix.db");
            for (int step = 0; step < totalSteps; step++) {
                printSnapshot(timeline.goTo(step));
            }
        } finally {
            database.close();
        }
    }

    private static void printSnapshot(Snapshot snapshot) {
        System.out.println("\n=== Step " + snapshot.getStepNumber()
                + " (line " + snapshot.getLineNumber() + ") ===");
        System.out.println("Stack (bottom -> top):");
        for (StackFrameInfo frame : snapshot.getStack()) {
            System.out.println("  " + frame.getClassName() + "." + frame.getMethodName()
                    + "() at line " + frame.getLineNumber());
            System.out.println("    Values: " + frame.getPrimitiveLocals());
            System.out.println("    References: " + frame.getReferenceLocals());
        }
        System.out.println("Heap:");
        for (ObjectInfo object : snapshot.getHeap()) {
            System.out.println("  #" + object.getId() + " " + object.getClassName()
                    + " [" + object.getStatus() + "]");
            System.out.println("    Values: " + object.getPrimitiveFields());
            System.out.println("    References: " + object.getReferenceFields());
        }
        System.out.println("Static references: " + snapshot.getStaticReferences());
        System.out.println("Reference edges:");
        for (ReferenceEdge edge : snapshot.getEdges()) {
            System.out.println("  " + edge.getSourceKind() + "[" + edge.getSourceId()
                    + "]." + edge.getLabel() + " -> #" + edge.getTargetId());
        }
        System.out.println("Events:");
        for (RuntimeEvent event : snapshot.getEvents()) {
            System.out.println("  " + event.getType() + ": " + event.getDescription());
        }
        System.out.println("Stats: " + snapshot.getStats());
        System.out.println("Console output: " + snapshot.getConsoleOutput());
    }
}
