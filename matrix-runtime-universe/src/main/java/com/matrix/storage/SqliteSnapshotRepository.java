package com.matrix.storage;

import com.matrix.model.EventType;
import com.matrix.model.ObjectInfo;
import com.matrix.model.ObjectStatus;
import com.matrix.model.ReferenceEdge;
import com.matrix.model.RuntimeEvent;
import com.matrix.model.Snapshot;
import com.matrix.model.StackFrameInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQLite implementation of SnapshotRepository using JDBC PreparedStatements,
 * atomic transactions, and batch inserts.
 */
public class SqliteSnapshotRepository implements SnapshotRepository {
    private final DatabaseManager dbManager;

    /**
     * Opens or creates the default "matrix.db" database.
     */
    public SqliteSnapshotRepository() {
        this("matrix.db");
    }

    /**
     * Opens or creates a SQLite database at the given file path (or ":memory:").
     */
    public SqliteSnapshotRepository(String dbPath) {
        this.dbManager = new DatabaseManager(dbPath);
    }

    /**
     * Uses an existing DatabaseManager instance.
     */
    public SqliteSnapshotRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public DatabaseManager getDatabaseManager() {
        return dbManager;
    }

    @Override
    public long createSession(String programName, String sourceCode) {
        String sql = "INSERT INTO sessions (program_name, source_code, created_at) VALUES (?, ?, ?)";
        String createdAt = Instant.now().toString();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, programName == null ? "UnknownProgram" : programName);
            ps.setString(2, sourceCode == null ? "" : sourceCode);
            ps.setString(3, createdAt);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new RuntimeException("Creating session failed: no session_id returned.");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create session in SQLite", e);
        }
    }

    @Override
    public void saveSnapshots(long sessionId, List<Snapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }

        String deleteExistingSql = "DELETE FROM snapshots WHERE session_id = ?";
        String insertSnapshotSql = """
            INSERT INTO snapshots (
                session_id, step_number, line_number, console_output,
                live_objects, unreachable_objects, collected_so_far, stack_depth
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;
        String insertFrameSql = """
            INSERT INTO stack_frames (
                snapshot_id, frame_index, class_name, method_name, line_number
            ) VALUES (?, ?, ?, ?, ?)
        """;
        String insertLocalSql = """
            INSERT INTO frame_locals (
                frame_id, local_order, var_name, is_reference, primitive_value, target_object_id
            ) VALUES (?, ?, ?, ?, ?, ?)
        """;
        String insertHeapObjSql = """
            INSERT INTO heap_objects (
                snapshot_id, object_order, object_id, class_name, status,
                created_at_step, unreachable_at_step, collected_at_step
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;
        String insertFieldSql = """
            INSERT INTO object_fields (
                heap_obj_row_id, field_order, field_name, is_reference, primitive_value, target_object_id
            ) VALUES (?, ?, ?, ?, ?, ?)
        """;
        String insertEdgeSql = """
            INSERT INTO edges (
                snapshot_id, edge_order, source_kind, source_id, label, target_id
            ) VALUES (?, ?, ?, ?, ?, ?)
        """;
        String insertEventSql = """
            INSERT INTO events (
                snapshot_id, event_order, step_number, event_type, object_id, description
            ) VALUES (?, ?, ?, ?, ?, ?)
        """;
        String insertStaticRefSql = """
            INSERT INTO static_refs (
                snapshot_id, ref_order, ref_name, target_object_id
            ) VALUES (?, ?, ?, ?)
        """;

        try (Connection conn = dbManager.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement deletePs = conn.prepareStatement(deleteExistingSql);
                 PreparedStatement snapPs = conn.prepareStatement(insertSnapshotSql, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement framePs = conn.prepareStatement(insertFrameSql, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement localPs = conn.prepareStatement(insertLocalSql);
                 PreparedStatement objPs = conn.prepareStatement(insertHeapObjSql, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement fieldPs = conn.prepareStatement(insertFieldSql);
                 PreparedStatement edgePs = conn.prepareStatement(insertEdgeSql);
                 PreparedStatement eventPs = conn.prepareStatement(insertEventSql);
                 PreparedStatement staticPs = conn.prepareStatement(insertStaticRefSql)) {

                deletePs.setLong(1, sessionId);
                deletePs.executeUpdate();

                for (Snapshot snap : snapshots) {
                    Map<String, Integer> stats = snap.getStats();
                    int liveObjects = stats != null ? stats.getOrDefault("liveObjects", 0) : 0;
                    int unreachableObjects = stats != null ? stats.getOrDefault("unreachableObjects", 0) : 0;
                    int collectedSoFar = stats != null ? stats.getOrDefault("collectedSoFar", 0) : 0;
                    int stackDepth = stats != null
                            ? stats.getOrDefault("stackDepth", snap.getStack().size())
                            : snap.getStack().size();

                    snapPs.setLong(1, sessionId);
                    snapPs.setInt(2, snap.getStepNumber());
                    snapPs.setInt(3, snap.getLineNumber());
                    snapPs.setString(4, snap.getConsoleOutput() == null ? "" : snap.getConsoleOutput());
                    snapPs.setInt(5, liveObjects);
                    snapPs.setInt(6, unreachableObjects);
                    snapPs.setInt(7, collectedSoFar);
                    snapPs.setInt(8, stackDepth);
                    snapPs.executeUpdate();

                    long snapshotId = readGeneratedId(snapPs, "snapshots");

                    // 1. Save stack frames and their local variables
                    List<StackFrameInfo> stack = snap.getStack();
                    for (int frameIdx = 0; frameIdx < stack.size(); frameIdx++) {
                        StackFrameInfo frame = stack.get(frameIdx);
                        framePs.setLong(1, snapshotId);
                        framePs.setInt(2, frameIdx);
                        framePs.setString(3, frame.getClassName() == null ? "" : frame.getClassName());
                        framePs.setString(4, frame.getMethodName() == null ? "" : frame.getMethodName());
                        framePs.setInt(5, frame.getLineNumber());
                        framePs.executeUpdate();

                        long frameId = readGeneratedId(framePs, "stack_frames");
                        int localOrder = 0;

                        for (Map.Entry<String, String> entry : frame.getPrimitiveLocals().entrySet()) {
                            localPs.setLong(1, frameId);
                            localPs.setInt(2, localOrder++);
                            localPs.setString(3, entry.getKey());
                            localPs.setInt(4, 0);
                            localPs.setString(5, entry.getValue());
                            localPs.setNull(6, Types.INTEGER);
                            localPs.addBatch();
                        }

                        for (Map.Entry<String, Long> entry : frame.getReferenceLocals().entrySet()) {
                            localPs.setLong(1, frameId);
                            localPs.setInt(2, localOrder++);
                            localPs.setString(3, entry.getKey());
                            localPs.setInt(4, 1);
                            localPs.setNull(5, Types.VARCHAR);
                            localPs.setLong(6, entry.getValue());
                            localPs.addBatch();
                        }
                    }

                    // 2. Save heap objects and their primitive/reference fields
                    List<ObjectInfo> heap = snap.getHeap();
                    for (int objOrder = 0; objOrder < heap.size(); objOrder++) {
                        ObjectInfo obj = heap.get(objOrder);
                        ObjectStatus status = obj.getStatus() == null ? ObjectStatus.LIVE : obj.getStatus();

                        objPs.setLong(1, snapshotId);
                        objPs.setInt(2, objOrder);
                        objPs.setLong(3, obj.getId());
                        objPs.setString(4, obj.getClassName() == null ? "" : obj.getClassName());
                        objPs.setString(5, status.name());
                        objPs.setInt(6, obj.getCreatedAtStep());
                        objPs.setInt(7, obj.getUnreachableAtStep());
                        objPs.setInt(8, obj.getCollectedAtStep());
                        objPs.executeUpdate();

                        long heapObjRowId = readGeneratedId(objPs, "heap_objects");
                        int fieldOrder = 0;

                        for (Map.Entry<String, String> entry : obj.getPrimitiveFields().entrySet()) {
                            fieldPs.setLong(1, heapObjRowId);
                            fieldPs.setInt(2, fieldOrder++);
                            fieldPs.setString(3, entry.getKey());
                            fieldPs.setInt(4, 0);
                            fieldPs.setString(5, entry.getValue());
                            fieldPs.setNull(6, Types.INTEGER);
                            fieldPs.addBatch();
                        }

                        for (Map.Entry<String, Long> entry : obj.getReferenceFields().entrySet()) {
                            fieldPs.setLong(1, heapObjRowId);
                            fieldPs.setInt(2, fieldOrder++);
                            fieldPs.setString(3, entry.getKey());
                            fieldPs.setInt(4, 1);
                            fieldPs.setNull(5, Types.VARCHAR);
                            fieldPs.setLong(6, entry.getValue());
                            fieldPs.addBatch();
                        }
                    }

                    // 3. Save reference edges
                    List<ReferenceEdge> edges = snap.getEdges();
                    for (int edgeOrder = 0; edgeOrder < edges.size(); edgeOrder++) {
                        ReferenceEdge edge = edges.get(edgeOrder);
                        edgePs.setLong(1, snapshotId);
                        edgePs.setInt(2, edgeOrder);
                        edgePs.setString(3, edge.getSourceKind());
                        edgePs.setLong(4, edge.getSourceId());
                        edgePs.setString(5, edge.getLabel());
                        edgePs.setLong(6, edge.getTargetId());
                        edgePs.addBatch();
                    }

                    // 4. Save runtime events
                    List<RuntimeEvent> events = snap.getEvents();
                    for (int eventOrder = 0; eventOrder < events.size(); eventOrder++) {
                        RuntimeEvent event = events.get(eventOrder);
                        eventPs.setLong(1, snapshotId);
                        eventPs.setInt(2, eventOrder);
                        eventPs.setInt(3, event.getStepNumber());
                        eventPs.setString(4, event.getType().name());
                        eventPs.setLong(5, event.getObjectId());
                        eventPs.setString(6, event.getDescription() == null ? "" : event.getDescription());
                        eventPs.addBatch();
                    }

                    // 5. Save static references
                    int refOrder = 0;
                    for (Map.Entry<String, Long> entry : snap.getStaticReferences().entrySet()) {
                        staticPs.setLong(1, snapshotId);
                        staticPs.setInt(2, refOrder++);
                        staticPs.setString(3, entry.getKey());
                        staticPs.setLong(4, entry.getValue());
                        staticPs.addBatch();
                    }
                }

                localPs.executeBatch();
                fieldPs.executeBatch();
                edgePs.executeBatch();
                eventPs.executeBatch();
                staticPs.executeBatch();

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save snapshots for session " + sessionId, e);
        }
    }

    @Override
    public int getStepCount(long sessionId) {
        String sql = "SELECT COUNT(*) FROM snapshots WHERE session_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count steps for session " + sessionId, e);
        }
    }

    @Override
    public Snapshot getSnapshot(long sessionId, int stepNumber) {
        List<Snapshot> results = loadSnapshotsInternal(sessionId, stepNumber);
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public List<Snapshot> getAllSnapshots(long sessionId) {
        return loadSnapshotsInternal(sessionId, null);
    }

    @Override
    public List<SessionInfo> listSessions() {
        String sql = """
            SELECT s.session_id, s.program_name, s.created_at, COUNT(sn.snapshot_id) AS step_count
            FROM sessions s
            LEFT JOIN snapshots sn ON s.session_id = sn.session_id
            GROUP BY s.session_id, s.program_name, s.created_at
            ORDER BY s.session_id ASC
        """;
        List<SessionInfo> sessions = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                sessions.add(new SessionInfo(
                        rs.getLong("session_id"),
                        rs.getString("program_name"),
                        rs.getString("created_at"),
                        rs.getInt("step_count")
                ));
            }
            return sessions;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list sessions", e);
        }
    }

    @Override
    public void deleteSession(long sessionId) {
        String sql = "DELETE FROM sessions WHERE session_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete session " + sessionId, e);
        }
    }

    /**
     * Reconstructs either a single snapshot (when targetStep != null) or all snapshots
     * for a session in exact step and insertion order using 8 set-based queries.
     */
    private List<Snapshot> loadSnapshotsInternal(long sessionId, Integer targetStep) {
        boolean singleStep = (targetStep != null);
        String stepFilter = singleStep ? " AND sn.step_number = ?" : "";

        String snapSql = """
            SELECT sn.snapshot_id, sn.step_number, sn.line_number, sn.console_output,
                   sn.live_objects, sn.unreachable_objects, sn.collected_so_far, sn.stack_depth
            FROM snapshots sn
            WHERE sn.session_id = ?
        """ + stepFilter + " ORDER BY sn.step_number ASC";

        String frameSql = """
            SELECT sf.frame_id, sf.snapshot_id, sf.class_name, sf.method_name, sf.line_number
            FROM stack_frames sf
            JOIN snapshots sn ON sf.snapshot_id = sn.snapshot_id
            WHERE sn.session_id = ?
        """ + stepFilter + " ORDER BY sf.snapshot_id ASC, sf.frame_index ASC";

        String localSql = """
            SELECT fl.frame_id, fl.var_name, fl.is_reference, fl.primitive_value, fl.target_object_id
            FROM frame_locals fl
            JOIN stack_frames sf ON fl.frame_id = sf.frame_id
            JOIN snapshots sn ON sf.snapshot_id = sn.snapshot_id
            WHERE sn.session_id = ?
        """ + stepFilter + " ORDER BY fl.frame_id ASC, fl.local_order ASC";

        String objSql = """
            SELECT ho.heap_obj_row_id, ho.snapshot_id, ho.object_id, ho.class_name, ho.status,
                   ho.created_at_step, ho.unreachable_at_step, ho.collected_at_step
            FROM heap_objects ho
            JOIN snapshots sn ON ho.snapshot_id = sn.snapshot_id
            WHERE sn.session_id = ?
        """ + stepFilter + " ORDER BY ho.snapshot_id ASC, ho.object_order ASC";

        String fieldSql = """
            SELECT ofd.heap_obj_row_id, ofd.field_name, ofd.is_reference, ofd.primitive_value, ofd.target_object_id
            FROM object_fields ofd
            JOIN heap_objects ho ON ofd.heap_obj_row_id = ho.heap_obj_row_id
            JOIN snapshots sn ON ho.snapshot_id = sn.snapshot_id
            WHERE sn.session_id = ?
        """ + stepFilter + " ORDER BY ofd.heap_obj_row_id ASC, ofd.field_order ASC";

        String edgeSql = """
            SELECT e.snapshot_id, e.source_kind, e.source_id, e.label, e.target_id
            FROM edges e
            JOIN snapshots sn ON e.snapshot_id = sn.snapshot_id
            WHERE sn.session_id = ?
        """ + stepFilter + " ORDER BY e.snapshot_id ASC, e.edge_order ASC";

        String eventSql = """
            SELECT ev.snapshot_id, ev.step_number, ev.event_type, ev.object_id, ev.description
            FROM events ev
            JOIN snapshots sn ON ev.snapshot_id = sn.snapshot_id
            WHERE sn.session_id = ?
        """ + stepFilter + " ORDER BY ev.snapshot_id ASC, ev.event_order ASC";

        String staticSql = """
            SELECT sr.snapshot_id, sr.ref_name, sr.target_object_id
            FROM static_refs sr
            JOIN snapshots sn ON sr.snapshot_id = sn.snapshot_id
            WHERE sn.session_id = ?
        """ + stepFilter + " ORDER BY sr.snapshot_id ASC, sr.ref_order ASC";

        List<Snapshot> orderedSnapshots = new ArrayList<>();
        Map<Long, Snapshot> snapshotById = new LinkedHashMap<>();
        Map<Long, StackFrameInfo> frameById = new HashMap<>();
        Map<Long, ObjectInfo> objectByRowId = new HashMap<>();

        try (Connection conn = dbManager.getConnection()) {
            // 1. Load snapshot rows
            try (PreparedStatement ps = prepareSessionQuery(conn, snapSql, sessionId, targetStep);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long snapshotId = rs.getLong("snapshot_id");
                    Map<String, Integer> stats = new LinkedHashMap<>();
                    stats.put("liveObjects", rs.getInt("live_objects"));
                    stats.put("unreachableObjects", rs.getInt("unreachable_objects"));
                    stats.put("collectedSoFar", rs.getInt("collected_so_far"));
                    stats.put("stackDepth", rs.getInt("stack_depth"));

                    Snapshot snap = new Snapshot(
                            rs.getInt("step_number"),
                            rs.getInt("line_number"),
                            new ArrayList<>(),
                            new ArrayList<>(),
                            new ArrayList<>(),
                            new LinkedHashMap<>(),
                            rs.getString("console_output"),
                            new ArrayList<>(),
                            stats
                    );
                    orderedSnapshots.add(snap);
                    snapshotById.put(snapshotId, snap);
                }
            }

            if (orderedSnapshots.isEmpty()) {
                return orderedSnapshots;
            }

            // 2. Load stack frames
            try (PreparedStatement ps = prepareSessionQuery(conn, frameSql, sessionId, targetStep);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long frameId = rs.getLong("frame_id");
                    long snapshotId = rs.getLong("snapshot_id");
                    StackFrameInfo frame = new StackFrameInfo(
                            rs.getString("class_name"),
                            rs.getString("method_name"),
                            rs.getInt("line_number"),
                            new LinkedHashMap<>(),
                            new LinkedHashMap<>()
                    );
                    frameById.put(frameId, frame);
                    Snapshot snap = snapshotById.get(snapshotId);
                    if (snap != null) {
                        snap.getStack().add(frame);
                    }
                }
            }

            // 3. Load frame locals
            try (PreparedStatement ps = prepareSessionQuery(conn, localSql, sessionId, targetStep);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long frameId = rs.getLong("frame_id");
                    StackFrameInfo frame = frameById.get(frameId);
                    if (frame != null) {
                        String varName = rs.getString("var_name");
                        if (rs.getInt("is_reference") == 1) {
                            frame.getReferenceLocals().put(varName, rs.getLong("target_object_id"));
                        } else {
                            frame.getPrimitiveLocals().put(varName, rs.getString("primitive_value"));
                        }
                    }
                }
            }

            // 4. Load heap objects
            try (PreparedStatement ps = prepareSessionQuery(conn, objSql, sessionId, targetStep);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long rowId = rs.getLong("heap_obj_row_id");
                    long snapshotId = rs.getLong("snapshot_id");
                    ObjectInfo obj = new ObjectInfo(
                            rs.getLong("object_id"),
                            rs.getString("class_name"),
                            new LinkedHashMap<>(),
                            new LinkedHashMap<>(),
                            ObjectStatus.valueOf(rs.getString("status")),
                            rs.getInt("created_at_step"),
                            rs.getInt("unreachable_at_step"),
                            rs.getInt("collected_at_step")
                    );
                    objectByRowId.put(rowId, obj);
                    Snapshot snap = snapshotById.get(snapshotId);
                    if (snap != null) {
                        snap.getHeap().add(obj);
                    }
                }
            }

            // 5. Load object fields
            try (PreparedStatement ps = prepareSessionQuery(conn, fieldSql, sessionId, targetStep);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long rowId = rs.getLong("heap_obj_row_id");
                    ObjectInfo obj = objectByRowId.get(rowId);
                    if (obj != null) {
                        String fieldName = rs.getString("field_name");
                        if (rs.getInt("is_reference") == 1) {
                            obj.getReferenceFields().put(fieldName, rs.getLong("target_object_id"));
                        } else {
                            obj.getPrimitiveFields().put(fieldName, rs.getString("primitive_value"));
                        }
                    }
                }
            }

            // 6. Load reference edges
            try (PreparedStatement ps = prepareSessionQuery(conn, edgeSql, sessionId, targetStep);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long snapshotId = rs.getLong("snapshot_id");
                    Snapshot snap = snapshotById.get(snapshotId);
                    if (snap != null) {
                        snap.getEdges().add(new ReferenceEdge(
                                rs.getString("source_kind"),
                                rs.getLong("source_id"),
                                rs.getString("label"),
                                rs.getLong("target_id")
                        ));
                    }
                }
            }

            // 7. Load runtime events
            try (PreparedStatement ps = prepareSessionQuery(conn, eventSql, sessionId, targetStep);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long snapshotId = rs.getLong("snapshot_id");
                    Snapshot snap = snapshotById.get(snapshotId);
                    if (snap != null) {
                        snap.getEvents().add(new RuntimeEvent(
                                rs.getInt("step_number"),
                                EventType.valueOf(rs.getString("event_type")),
                                rs.getLong("object_id"),
                                rs.getString("description")
                        ));
                    }
                }
            }

            // 8. Load static references
            try (PreparedStatement ps = prepareSessionQuery(conn, staticSql, sessionId, targetStep);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long snapshotId = rs.getLong("snapshot_id");
                    Snapshot snap = snapshotById.get(snapshotId);
                    if (snap != null) {
                        snap.getStaticReferences().put(
                                rs.getString("ref_name"),
                                rs.getLong("target_object_id")
                        );
                    }
                }
            }

            return orderedSnapshots;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load snapshots for session " + sessionId, e);
        }
    }

    private PreparedStatement prepareSessionQuery(Connection conn, String sql, long sessionId, Integer targetStep)
            throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setLong(1, sessionId);
        if (targetStep != null) {
            ps.setInt(2, targetStep);
        }
        return ps;
    }

    private long readGeneratedId(PreparedStatement ps, String tableName) throws SQLException {
        try (ResultSet keys = ps.getGeneratedKeys()) {
            if (keys.next()) {
                return keys.getLong(1);
            }
        }
        throw new SQLException("Failed to retrieve generated key from " + tableName);
    }
}