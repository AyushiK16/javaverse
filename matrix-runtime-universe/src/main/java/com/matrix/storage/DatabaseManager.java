package com.matrix.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Manages SQLite database connections, enables foreign keys,
 * and initializes the normalized database schema.
 */
public class DatabaseManager {
    private final String dbPath;
    private final String jdbcUrl;
    private Connection memoryAnchorConnection;

    /**
     * Opens or creates the default "matrix.db" SQLite database in the project root.
     */
    public DatabaseManager() {
        this("matrix.db");
    }

    /**
     * Opens or creates a SQLite database at the given path (or ":memory:" for tests).
     */
    public DatabaseManager(String dbPath) {
        this.dbPath = (dbPath == null || dbPath.isBlank()) ? "matrix.db" : dbPath;

        if (":memory:".equals(this.dbPath)) {
            // Use a unique shared-cache in-memory URI so multiple connections
            // inside this DatabaseManager share the same temporary database.
            String uniqueName = "matrix_mem_" + UUID.randomUUID().toString().replace("-", "");
            this.jdbcUrl = "jdbc:sqlite:file:" + uniqueName + "?mode=memory&cache=shared";
            try {
                this.memoryAnchorConnection = DriverManager.getConnection(this.jdbcUrl);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to initialize in-memory SQLite database", e);
            }
        } else {
            this.jdbcUrl = "jdbc:sqlite:" + this.dbPath;
        }

        initializeSchema();
    }

    /**
     * Opens a new SQLite connection with foreign key constraints enabled.
     */
    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(jdbcUrl);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
        return conn;
    }

    /**
     * Creates all normalized tables and lookup indexes if they do not exist yet.
     */
    public void initializeSchema() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Sessions table (one row per program run)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sessions (
                    session_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    program_name TEXT NOT NULL,
                    source_code TEXT,
                    created_at TEXT NOT NULL
                )
            """);

            // 2. Snapshots table (one row per step in a session)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS snapshots (
                    snapshot_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id INTEGER NOT NULL,
                    step_number INTEGER NOT NULL,
                    line_number INTEGER NOT NULL,
                    console_output TEXT NOT NULL,
                    live_objects INTEGER NOT NULL,
                    unreachable_objects INTEGER NOT NULL,
                    collected_so_far INTEGER NOT NULL,
                    stack_depth INTEGER NOT NULL,
                    UNIQUE(session_id, step_number),
                    FOREIGN KEY (session_id) REFERENCES sessions(session_id) ON DELETE CASCADE
                )
            """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_snapshots_session_step
                ON snapshots(session_id, step_number)
            """);

            // 3. Stack frames table (method calls on the stack)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS stack_frames (
                    frame_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    snapshot_id INTEGER NOT NULL,
                    frame_index INTEGER NOT NULL,
                    class_name TEXT NOT NULL,
                    method_name TEXT NOT NULL,
                    line_number INTEGER NOT NULL,
                    FOREIGN KEY (snapshot_id) REFERENCES snapshots(snapshot_id) ON DELETE CASCADE
                )
            """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_stack_frames_snapshot
                ON stack_frames(snapshot_id)
            """);

            // 4. Frame locals table (primitive and reference local variables)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS frame_locals (
                    local_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    frame_id INTEGER NOT NULL,
                    local_order INTEGER NOT NULL,
                    var_name TEXT NOT NULL,
                    is_reference INTEGER NOT NULL,
                    primitive_value TEXT,
                    target_object_id INTEGER,
                    FOREIGN KEY (frame_id) REFERENCES stack_frames(frame_id) ON DELETE CASCADE
                )
            """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_frame_locals_frame
                ON frame_locals(frame_id)
            """);

            // 5. Heap objects table (objects present in memory at a step)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS heap_objects (
                    heap_obj_row_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    snapshot_id INTEGER NOT NULL,
                    object_order INTEGER NOT NULL,
                    object_id INTEGER NOT NULL,
                    class_name TEXT NOT NULL,
                    status TEXT NOT NULL,
                    created_at_step INTEGER NOT NULL,
                    unreachable_at_step INTEGER NOT NULL,
                    collected_at_step INTEGER NOT NULL,
                    FOREIGN KEY (snapshot_id) REFERENCES snapshots(snapshot_id) ON DELETE CASCADE
                )
            """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_heap_objects_snapshot
                ON heap_objects(snapshot_id)
            """);

            // 6. Object fields table (both primitive and reference fields)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS object_fields (
                    field_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    heap_obj_row_id INTEGER NOT NULL,
                    field_order INTEGER NOT NULL,
                    field_name TEXT NOT NULL,
                    is_reference INTEGER NOT NULL,
                    primitive_value TEXT,
                    target_object_id INTEGER,
                    FOREIGN KEY (heap_obj_row_id) REFERENCES heap_objects(heap_obj_row_id) ON DELETE CASCADE
                )
            """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_object_fields_obj
                ON object_fields(heap_obj_row_id)
            """);

            // 7. Edges table (STACK, STATIC, and OBJECT reference arrows)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS edges (
                    edge_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    snapshot_id INTEGER NOT NULL,
                    edge_order INTEGER NOT NULL,
                    source_kind TEXT NOT NULL,
                    source_id INTEGER NOT NULL,
                    label TEXT NOT NULL,
                    target_id INTEGER NOT NULL,
                    FOREIGN KEY (snapshot_id) REFERENCES snapshots(snapshot_id) ON DELETE CASCADE
                )
            """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_edges_snapshot
                ON edges(snapshot_id)
            """);

            // 8. Events table (runtime events that occurred at a step)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS events (
                    event_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    snapshot_id INTEGER NOT NULL,
                    event_order INTEGER NOT NULL,
                    step_number INTEGER NOT NULL,
                    event_type TEXT NOT NULL,
                    object_id INTEGER NOT NULL,
                    description TEXT NOT NULL,
                    FOREIGN KEY (snapshot_id) REFERENCES snapshots(snapshot_id) ON DELETE CASCADE
                )
            """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_events_snapshot
                ON events(snapshot_id)
            """);

            // 9. Static references table (static class fields pointing to objects)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS static_refs (
                    static_ref_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    snapshot_id INTEGER NOT NULL,
                    ref_order INTEGER NOT NULL,
                    ref_name TEXT NOT NULL,
                    target_object_id INTEGER NOT NULL,
                    FOREIGN KEY (snapshot_id) REFERENCES snapshots(snapshot_id) ON DELETE CASCADE
                )
            """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_static_refs_snapshot
                ON static_refs(snapshot_id)
            """);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database schema", e);
        }
    }

    public String getDbPath() {
        return dbPath;
    }

    /**
     * Closes the anchor connection when using an in-memory database.
     */
    public void close() {
        if (memoryAnchorConnection != null) {
            try {
                memoryAnchorConnection.close();
            } catch (SQLException ignored) {
            }
        }
    }
}