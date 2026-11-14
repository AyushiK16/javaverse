package com.matrix.timeline;

import com.matrix.model.EventType;
import com.matrix.model.RuntimeEvent;
import com.matrix.model.Snapshot;
import com.matrix.storage.SnapshotRepository;

/**
 * Provides time-slider navigation, event-based jumping, and snapshot diffing
 * over a saved session in a SnapshotRepository.
 */
public class TimelineService {
    private final SnapshotRepository repository;
    private final long sessionId;
    private int currentStep;

    /**
     * Creates a TimelineService positioned at step 0 of the given session.
     */
    public TimelineService(SnapshotRepository repository, long sessionId) {
        if (repository == null) {
            throw new IllegalArgumentException("SnapshotRepository cannot be null");
        }
        this.repository = repository;
        this.sessionId = sessionId;
        this.currentStep = 0;
    }

    /**
     * Returns the Snapshot at the current step, or null if the session has no steps.
     */
    public Snapshot current() {
        if (getTotalSteps() <= 0) {
            return null;
        }
        return repository.getSnapshot(sessionId, currentStep);
    }

    /**
     * Moves to the requested step, clamping safely within [0, totalSteps - 1].
     * Returns the Snapshot at the resulting current step (or null if empty).
     */
    public Snapshot goTo(int step) {
        int total = getTotalSteps();
        if (total <= 0) {
            this.currentStep = 0;
            return null;
        }

        if (step < 0) {
            this.currentStep = 0;
        } else if (step >= total) {
            this.currentStep = total - 1;
        } else {
            this.currentStep = step;
        }
        return current();
    }

    /**
     * Advances one step forward, stopping safely at the last step.
     */
    public Snapshot next() {
        return goTo(currentStep + 1);
    }

    /**
     * Moves one step backward, stopping safely at step 0.
     */
    public Snapshot previous() {
        return goTo(currentStep - 1);
    }

    /**
     * Returns the current step index (0-based).
     */
    public int getCurrentStep() {
        return currentStep;
    }

    /**
     * Returns the total number of steps in this session.
     */
    public int getTotalSteps() {
        return repository.getStepCount(sessionId);
    }

    /**
     * Searches strictly AFTER the current step for the next snapshot containing
     * at least one RuntimeEvent of the given type.
     * If found, updates currentStep to that step and returns the Snapshot.
     * If none exists, leaves currentStep unchanged and returns null.
     */
    public Snapshot jumpToNextEventOfType(EventType type) {
        if (type == null) {
            return null;
        }

        int total = getTotalSteps();
        for (int step = currentStep + 1; step < total; step++) {
            Snapshot candidate = repository.getSnapshot(sessionId, step);
            if (candidate != null && candidate.getEvents() != null) {
                for (RuntimeEvent event : candidate.getEvents()) {
                    if (event.getType() == type) {
                        this.currentStep = step;
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Compares two steps in this session and returns their SnapshotDiff.
     */
    public SnapshotDiff diff(int fromStep, int toStep) {
        Snapshot fromSnapshot = repository.getSnapshot(sessionId, fromStep);
        Snapshot toSnapshot = repository.getSnapshot(sessionId, toStep);
        if (fromSnapshot == null || toSnapshot == null) {
            throw new IllegalArgumentException(
                    "Cannot diff missing step(s): fromStep=" + fromStep + ", toStep=" + toStep);
        }
        return SnapshotDiffer.diff(fromSnapshot, toSnapshot);
    }
}