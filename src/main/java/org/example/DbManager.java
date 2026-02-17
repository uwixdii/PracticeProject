package org.example;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class DbManager {

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_PROCESSED = "processed";
    private static final String STATUS_PARTIAL = "partial_processed";

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // =========================
    // CONNECTION
    // =========================

    public static Connection connect(String path) throws SQLException {
        File f = new File(path);
        File parent = f.getParentFile();

        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Не удалось создать папку для БД");
        }

        Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
        init(c);
        return c;
    }

    private static void init(Connection c) throws SQLException {
        String[] tables = {
                """
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT UNIQUE NOT NULL,
                    password_hash TEXT NOT NULL,
                    role TEXT NOT NULL,
                    workshop_number INTEGER DEFAULT 0
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE,
                    quantity INTEGER NOT NULL DEFAULT 0
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS requests (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    number TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'pending',
                    created_by TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    last_edited_at TEXT,
                    archived INTEGER NOT NULL DEFAULT 0
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS request_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    request_id INTEGER NOT NULL,
                    item_id INTEGER NOT NULL,
                    quantity INTEGER NOT NULL,
                    status TEXT NOT NULL DEFAULT 'pending',
                    FOREIGN KEY (request_id) REFERENCES requests(id),
                    FOREIGN KEY (item_id) REFERENCES items(id)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS request_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    request_id INTEGER NOT NULL,
                    action TEXT NOT NULL,
                    details TEXT,
                    by_user TEXT,
                    timestamp TEXT,
                    FOREIGN KEY (request_id) REFERENCES requests(id)
                )
                """
        };

        try (Statement s = c.createStatement()) {
            for (String sql : tables) {
                s.execute(sql);
            }
        }

        if (countUsers(c) == 0) {
            insertTestUsers(c);
        }
    }

    // =========================
    // USERS
    // =========================

    private static int countUsers(Connection c) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM users")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void insertTestUsers(Connection c) throws SQLException {
        String[][] test = {
                {"admin", "admin123", "Админ", "0"},
                {"ceh549", "123", "Цех", "549"},
                {"ceh777", "123", "Цех", "777"},
                {"sklad1", "123", "Склад", "0"},
                {"supply1", "123", "Центр снабжения", "0"}
        };

        String sql = "INSERT INTO users (username, password_hash, role, workshop_number) VALUES (?,?,?,?)";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (String[] row : test) {
                ps.setString(1, row[0]);
                ps.setString(2, hash(row[1]));
                ps.setString(3, row[2]);
                ps.setInt(4, Integer.parseInt(row[3]));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static String hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static User authenticate(Connection c, String login, String pass) throws SQLException {
        String sql = "SELECT id, username, role, workshop_number FROM users WHERE username = ? AND password_hash = ?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, login);
            ps.setString(2, hash(pass));

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("role"),
                            rs.getInt("workshop_number")
                    );
                }
            }
        }
        return null;
    }

    public static List<User> getAllUsers(Connection c) throws SQLException {
        List<User> list = new ArrayList<>();

        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, username, role, workshop_number FROM users ORDER BY username")) {

            while (rs.next()) {
                list.add(new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("role"),
                        rs.getInt("workshop_number")
                ));
            }
        }

        return list;
    }

    public static void deleteUser(Connection c, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // =========================
    // ITEMS
    // =========================

    public static List<Item> getAllItems(Connection c) throws SQLException {
        List<Item> list = new ArrayList<>();

        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, name, quantity FROM items ORDER BY name")) {

            while (rs.next()) {
                list.add(new Item(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getInt("quantity")
                ));
            }
        }

        return list;
    }

    public static void addItem(Connection c, String name, int qty) throws SQLException {
        try (PreparedStatement ps =
                     c.prepareStatement("INSERT INTO items (name, quantity) VALUES (?, ?)")) {
            ps.setString(1, name);
            ps.setInt(2, qty);
            ps.executeUpdate();
        }
    }

    // =========================
    // REQUESTS
    // =========================

    public static int createRequest(Connection conn,
                                    int workshopNumber,
                                    String createdBy) throws SQLException {

        String pattern = (workshopNumber == 0)
                ? "A%"
                : workshopNumber + "%";

        String countSql = "SELECT COUNT(*) FROM requests WHERE number LIKE ?";

        int count;
        try (PreparedStatement ps = conn.prepareStatement(countSql)) {
            ps.setString(1, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                count = rs.getInt(1);
            }
        }

        String number = (workshopNumber == 0)
                ? "A" + String.format("%04d", count + 1)
                : workshopNumber + String.format("%03d", count + 1);

        String insert = "INSERT INTO requests (number, status, created_by, created_at) VALUES (?, ?, ?, ?)";

        try (PreparedStatement ps =
                     conn.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, number);
            ps.setString(2, STATUS_PENDING);
            ps.setString(3, createdBy);
            ps.setString(4, LocalDateTime.now().format(FORMATTER));

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public static void addRequestItem(Connection conn,
                                      int requestId,
                                      int itemId,
                                      int quantity) throws SQLException {

        String sql = "INSERT INTO request_items (request_id, item_id, quantity, status) VALUES (?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, requestId);
            ps.setInt(2, itemId);
            ps.setInt(3, quantity);
            ps.setString(4, STATUS_PENDING);
            ps.executeUpdate();
        }
    }

    public static void updateRequestStatus(Connection conn, int requestId) throws SQLException {
        int pendingCount = 0;
        int totalCount = 0;

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM request_items WHERE request_id = ? AND status = ?")) {
            ps.setInt(1, requestId);
            ps.setString(2, STATUS_PENDING);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                pendingCount = rs.getInt(1);
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM request_items WHERE request_id = ?")) {
            ps.setInt(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                totalCount = rs.getInt(1);
            }
        }

        String newStatus;
        if (pendingCount == totalCount) {
            newStatus = STATUS_PENDING;
        } else if (pendingCount == 0) {
            archiveRequest(conn, requestId);
            return;
        } else {
            newStatus = STATUS_PARTIAL;
        }

        updateRequestStatusOnly(conn, requestId, newStatus);
        updateRequestLastEdited(conn, requestId);
    }
    private static void updateRequestStatusOnly(Connection conn,
                                                int requestId,
                                                String status) throws SQLException {

        try (PreparedStatement ps =
                     conn.prepareStatement("UPDATE requests SET status = ? WHERE id = ?")) {

            ps.setString(1, status);
            ps.setInt(2, requestId);
            ps.executeUpdate();
        }
    }

    private static void archiveRequest(Connection conn, int requestId) throws SQLException {
        try (PreparedStatement ps =
                     conn.prepareStatement("UPDATE requests SET archived = 1, status = ? WHERE id = ?")) {

            ps.setString(1, STATUS_PROCESSED);
            ps.setInt(2, requestId);
            ps.executeUpdate();
        }
    }

    // =========================
    // HISTORY
    // =========================

    public static void addHistoryEntry(Connection conn,
                                       int requestId,
                                       String action,
                                       String details,
                                       String byUser) throws SQLException {

        String sql = "INSERT INTO request_history (request_id, action, details, by_user, timestamp) VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, requestId);
            ps.setString(2, action);
            ps.setString(3, details);
            ps.setString(4, byUser);
            ps.setString(5, LocalDateTime.now().format(FORMATTER));
            ps.executeUpdate();
        }
    }

    // =========================
    // GET REQUESTS
    // =========================

    private static List<RequestSummary> getRequestsByArchiveFlag(Connection conn,
                                                                 int archived) throws SQLException {

        List<RequestSummary> list = new ArrayList<>();

        String sql = """
                SELECT 
                    r.id, r.number, r.status, r.created_at,
                    COALESCE(COUNT(ri.id), 0) as item_count,
                    COALESCE(SUM(ri.quantity), 0) as total_qty
                FROM requests r
                LEFT JOIN request_items ri ON ri.request_id = r.id
                WHERE r.archived = ?
                GROUP BY r.id
                ORDER BY r.created_at DESC
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, archived);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new RequestSummary(
                            rs.getInt("id"),
                            rs.getString("number"),
                            rs.getString("status"),
                            rs.getString("created_at"),
                            rs.getInt("item_count"),
                            rs.getInt("total_qty")
                    ));
                }
            }
        }

        return list;
    }

    public static List<RequestSummary> getArchivedRequests(Connection conn) throws SQLException {
        return getRequestsByArchiveFlag(conn, 1);
    }

    public static List<RequestSummary> getPendingRequests(Connection conn) throws SQLException {
        return getRequestsByArchiveFlag(conn, 0);
    }
}
