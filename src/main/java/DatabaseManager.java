import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseManager {
    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());
    private HikariDataSource dataSource;

    public void initialize(String url, String username, String password) {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(url);
            config.setUsername(username);
            config.setPassword(password);

            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30_000);
            config.setIdleTimeout(600_000);
            config.setMaxLifetime(1_800_000);
            config.setConnectionTestQuery("SELECT 1");

            dataSource = new HikariDataSource(config);

            createOwnersTable();
            createBirthdaysTable();

            LOGGER.info("DB initialized, tables ready.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "DB init failed", e);
            throw new RuntimeException("DB init failed", e);
        }
    }

    private void createOwnersTable() {
        String sql =
                "CREATE TABLE IF NOT EXISTS owners (" +
                        " owner_user_id BIGINT PRIMARY KEY," +
                        " notify_chat_id BIGINT NOT NULL," +
                        " created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                        " updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ");";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to create owners table", e);
            throw new RuntimeException(e);
        }
    }

    private void createBirthdaysTable() {
        String sql =
                "CREATE TABLE IF NOT EXISTS birthdays (" +
                        " id BIGSERIAL PRIMARY KEY," +
                        " owner_user_id BIGINT NOT NULL REFERENCES owners(owner_user_id) ON DELETE CASCADE," +
                        " person_name VARCHAR(255) NOT NULL," +
                        " birthday DATE NOT NULL," +
                        " created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                        " updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ");";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to create birthdays table", e);
            throw new RuntimeException(e);
        }
    }

    /** Регистрируем/обновляем владельца и "чат владельца" (куда слать уведомления). */
    public void upsertOwner(long ownerUserId, long notifyChatId) {
        String sql =
                "INSERT INTO owners(owner_user_id, notify_chat_id) VALUES (?, ?) " +
                        "ON CONFLICT (owner_user_id) DO UPDATE SET " +
                        " notify_chat_id = EXCLUDED.notify_chat_id," +
                        " updated_at = CURRENT_TIMESTAMP;";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, ownerUserId);
            ps.setLong(2, notifyChatId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "upsertOwner failed: owner=" + ownerUserId, e);
            throw new RuntimeException(e);
        }
    }

    /** Добавить день рождения в список владельца. */
    public long addBirthday(long ownerUserId, String personName, LocalDate birthday) {
        String sql =
                "INSERT INTO birthdays(owner_user_id, person_name, birthday) VALUES (?, ?, ?) " +
                        "RETURNING id;";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, ownerUserId);
            ps.setString(2, personName);
            ps.setDate(3, Date.valueOf(birthday));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
            throw new RuntimeException("No id returned from INSERT");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "addBirthday failed: owner=" + ownerUserId, e);
            throw new RuntimeException(e);
        }
    }

    /** Получить список ДР конкретного владельца. */
    public List<BirthdayEntry> getBirthdaysByOwner(long ownerUserId) {
        List<BirthdayEntry> list = new ArrayList<>();
        String sql =
                "SELECT id, owner_user_id, person_name, birthday " +
                        "FROM birthdays " +
                        "WHERE owner_user_id = ? " +
                        "ORDER BY birthday, id;";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, ownerUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new BirthdayEntry(
                            rs.getLong("id"),
                            rs.getLong("owner_user_id"),
                            rs.getString("person_name"),
                            rs.getDate("birthday").toLocalDate()
                    ));
                }
            }
            return list;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "getBirthdaysByOwner failed: owner=" + ownerUserId, e);
            throw new RuntimeException(e);
        }
    }

    /** Удалить ДР по id, но только если он принадлежит этому владельцу. */
    public boolean deleteBirthday(long ownerUserId, long birthdayId) {
        String sql = "DELETE FROM birthdays WHERE owner_user_id = ? AND id = ?;";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, ownerUserId);
            ps.setLong(2, birthdayId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "deleteBirthday failed: owner=" + ownerUserId + ", id=" + birthdayId, e);
            throw new RuntimeException(e);
        }
    }

    /** Для планировщика: что сегодня празднуем и в какой чат это писать. */
    public List<BirthdayNotification> getTodayNotifications() {
        List<BirthdayNotification> result = new ArrayList<>();
        LocalDate today = LocalDate.now();

        String sql =
                "SELECT o.notify_chat_id, b.person_name " +
                        "FROM birthdays b " +
                        "JOIN owners o ON o.owner_user_id = b.owner_user_id " +
                        "WHERE EXTRACT(MONTH FROM b.birthday) = ? " +
                        "  AND EXTRACT(DAY FROM b.birthday) = ? " +
                        "ORDER BY o.notify_chat_id, b.id;";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, today.getMonthValue());
            ps.setInt(2, today.getDayOfMonth());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new BirthdayNotification(
                            rs.getLong("notify_chat_id"),
                            rs.getString("person_name")
                    ));
                }
            }
            return result;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "getTodayNotifications failed", e);
            throw new RuntimeException(e);
        }
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
