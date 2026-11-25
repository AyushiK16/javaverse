package com.matrix.storage;

import com.matrix.model.Snapshot;
import java.util.List;

/**
 * Shared storage contract for saving and loading execution sessions and their snapshots.
 */
public interface SnapshotRepository {

    /** Creates a new session record and returns its generated sessionId. */
    long createSession(String programName, String sourceCode);

    /** Saves all snapshots belonging to a session in order. */
    void saveSnapshots(long sessionId, List<Snapshot> snapshots);

    /** Returns the total number of saved steps for the given session. */
    int getStepCount(long sessionId);

    /** Returns the snapshot at stepNumber for the given session, or null if missing. */
    Snapshot getSnapshot(long sessionId, int stepNumber);

    /** Returns all snapshots for the given session ordered by stepNumber ascending. */
    List<Snapshot> getAllSnapshots(long sessionId);

    /** Returns summary information for all saved sessions. */
    List<SessionInfo> listSessions();

    /** Deletes a session and all of its associated snapshot data. */
    void deleteSession(long sessionId);
}