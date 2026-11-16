package com.matrix.engine;

import com.matrix.model.RawStep;
import com.matrix.model.Snapshot;
import com.matrix.model.StackFrameInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * The engine's main class: turns the tracer's RawSteps into complete Snapshots.
 *
 * For every RawStep (in order) it
 *   1. lets the GcSimulator decide which objects are LIVE / UNREACHABLE / COLLECTED,
 *   2. copies the stack and the static references,
 *   3. builds the list of arrows (ReferenceExtractor),
 *   4. adds the console output printed so far,
 *   5. works out the events by comparing with the previous snapshot (EventDetector),
 *   6. fills the statistics (StatsCalculator).
 *
 * The input list and its objects are never changed: everything is copied.
 */
public class DefaultSnapshotBuilder implements SnapshotBuilder {

    private final int gcInterval;
    private final ReferenceExtractor referenceExtractor = new ReferenceExtractor();
    private final EventDetector eventDetector = new EventDetector();
    private final StatsCalculator statsCalculator = new StatsCalculator();

    /** Garbage collection every 10 steps (and at the last step). */
    public DefaultSnapshotBuilder() {
        this(10);
    }

    public DefaultSnapshotBuilder(int gcInterval) {
        if (gcInterval < 1) {
            throw new IllegalArgumentException("gcInterval must be at least 1");
        }
        this.gcInterval = gcInterval;
    }

    @Override
    public List<Snapshot> build(List<RawStep> steps) {
        List<Snapshot> snapshots = new ArrayList<>();
        if (steps == null || steps.isEmpty()) {
            return snapshots;
        }

        GcSimulator gc = new GcSimulator(gcInterval);   // fresh memory for every build() call
        StringBuilder console = new StringBuilder();
        Snapshot previous = null;

        for (int i = 0; i < steps.size(); i++) {
            RawStep raw = steps.get(i);
            boolean isLastStep = (i == steps.size() - 1);

            GcSimulator.StepResult gcResult = gc.processStep(raw, isLastStep);

            if (raw.getNewOutput() != null) {
                console.append(raw.getNewOutput());
            }

            Snapshot snapshot = new Snapshot();
            snapshot.setStepNumber(raw.getStepNumber());
            snapshot.setLineNumber(raw.getLineNumber());
            snapshot.setStack(copyStack(raw.getStack()));
            snapshot.setHeap(gcResult.getHeap());
            snapshot.setStaticReferences(new LinkedHashMap<>(raw.getStaticReferences()));
            snapshot.setEdges(referenceExtractor.extract(
                    snapshot.getStack(), snapshot.getStaticReferences(), snapshot.getHeap()));
            snapshot.setConsoleOutput(console.toString());
            snapshot.setEvents(eventDetector.detect(previous, snapshot, gcResult));
            snapshot.setStats(statsCalculator.calculate(
                    snapshot.getStack(), snapshot.getHeap(), gcResult.getCollectedSoFar()));

            snapshots.add(snapshot);
            previous = snapshot;
        }
        return snapshots;
    }

    private List<StackFrameInfo> copyStack(List<StackFrameInfo> stack) {
        List<StackFrameInfo> copy = new ArrayList<>();
        for (StackFrameInfo frame : stack) {
            copy.add(new StackFrameInfo(
                    frame.getClassName(),
                    frame.getMethodName(),
                    frame.getLineNumber(),
                    new LinkedHashMap<>(frame.getPrimitiveLocals()),
                    new LinkedHashMap<>(frame.getReferenceLocals())));
        }
        return copy;
    }
}