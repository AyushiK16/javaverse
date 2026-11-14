package com.matrix.engine;

import com.matrix.model.RawStep;
import com.matrix.model.Snapshot;

import java.util.List;

/**
 * Turns the tracer's raw steps into full snapshots of memory.
 * One RawStep in -> one Snapshot out, in the same order.
 */
public interface SnapshotBuilder {
    List<Snapshot> build(List<RawStep> steps);
}