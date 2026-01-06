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

    // Инициализация
    public void initialize(String url, String username, String password) {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(url);
            config.setUsername(username);
            config.setPassword(password);

            // настройка пула
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30_000);
            config.setIdleTimeout(600_000);
            config.setMaxLifetime(1_800_000);

            config.setConnectionTestQuery("SELECT 1");

            dataSource = new HikariDataSource(config);

            createUsersTable();

            LOGGER.info("Пул успешно инициализирован!!!");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "пул взорвала ядерная бомба", e);
        }
    }

    public void createUsersTable() {
        String createTableSql =
                "CREATE TABLE IF NOT EXISTS users (\n" +
                        "    id SERIAL PRIMARY KEY,\n" +
                        "    telegram_id BIGINT,\n" +
                        "    name VARCHAR(255) NOT NULL,\n" +
                        "    birthday DATE NOT NULL,\n" +
                        "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n" +
                        "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n" +
                        ");";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(createTableSql);
            LOGGER.info("Таблица пользователей успешно создана или уже существовала");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "При создании таблицы пользователей произошла ошибка", e);
            throw new RuntimeException("Ошибка при создании таблицы", e);
        }
    }

    public boolean addUser(int id, Long telegramId, String name, LocalDate birthday) throws SQLException {
        String sql = "INSERT INTO users (id, telegram_id, name, birthday) VALUES (?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.setLong(2, telegramId);
            pstmt.setString(3, name);
            pstmt.setDate(4, Date.valueOf(birthday));

            pstmt.executeUpdate();
            LOGGER.info(String.format("User added: id=%d, telegram_id=%d, name=%s, birthday=%s",
                    id, telegramId, name, birthday));
            return true;

        } catch (SQLException e) {
            if (e.getSQLState().equals("23505")) {
                LOGGER.info(String.format("User already exists: id=%d", id));
                return false;
            }
            LOGGER.log(Level.SEVERE, "Ошибка при добавлении пользователя: " + telegramId, e);
            throw e;
        }
    }

    public int getUsersNum(long telegramId) {
        String sql = "SELECT COUNT(*) as user_count FROM users WHERE telegram_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, telegramId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("user_count");
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to get user count by telegram_id: " + telegramId, e);
        }

        return 0;
    }

    public boolean deleteUserById(long telegramId) {
        String sql = "DELETE FROM users WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, telegramId);
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                LOGGER.info("User deleted with id: " + telegramId);
                return true;
            } else {
                LOGGER.info("User not found for deletion: id=" + telegramId);
                return false;
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to delete user with id: " + telegramId, e);
            return false;
        }
    }

    public List<BirthdayUser> getAllUsers(long telegramId) {
        List<BirthdayUser> users = new ArrayList<>();
        String sql = "SELECT id, telegram_id, name, birthday FROM users WHERE telegram_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, telegramId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    users.add(new BirthdayUser(
                            rs.getInt("id"),
                            rs.getLong("telegram_id"),
                            rs.getString("name"),
                            rs.getDate("birthday").toLocalDate()
                    ));
                }
            }

            LOGGER.info("Retrieved " + users.size() + " users from database");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to get all users", e);
        }

        return users;
    }

    public List<BirthdayUser> getAllRecUsers(long telegramId) {
        List<BirthdayUser> users = new ArrayList<>();

        LocalDate now = LocalDate.now();
        LocalDate sameDayLastMonth = now.minusMonths(1);

        String sql = "SELECT id, telegram_id, name, birthday FROM users " +
                "WHERE telegram_id = ? " +
                "AND (" +
                "   (EXTRACT(MONTH FROM birthday) = ? AND EXTRACT(DAY FROM birthday) BETWEEN ? AND ?) " +
                "   OR " +
                "   (EXTRACT(MONTH FROM birthday) = ? AND EXTRACT(DAY FROM birthday) BETWEEN 1 AND ?)" +
                ") " +
                "ORDER BY EXTRACT(MONTH FROM birthday), EXTRACT(DAY FROM birthday)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, telegramId);
            pstmt.setInt(2, sameDayLastMonth.getMonthValue());
            pstmt.setInt(3, sameDayLastMonth.getDayOfMonth());
            pstmt.setInt(4, sameDayLastMonth.lengthOfMonth());
            pstmt.setInt(5, now.getMonthValue());
            pstmt.setInt(6, now.getDayOfMonth());

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    users.add(new BirthdayUser(
                            rs.getInt("id"),
                            rs.getLong("telegram_id"),
                            rs.getString("name"),
                            rs.getDate("birthday").toLocalDate()
                    ));
                }
            }

            LOGGER.info("Retrieved " + users.size() + " users with birthdays from " +
                    sameDayLastMonth + " to " + now);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to get users with birthdays for the period", e);
        }

        return users;
    }

    public List<BirthdayUser> getAllFutUsers(long telegramId) {
        List<BirthdayUser> users = new ArrayList<>();

        LocalDate now = LocalDate.now();
        LocalDate sameDayNextMonth = now.plusMonths(1);

        String sql = "SELECT id, telegram_id, name, birthday FROM users " +
                "WHERE telegram_id = ? " +
                "AND (" +
                "   (EXTRACT(MONTH FROM birthday) = ? AND EXTRACT(DAY FROM birthday) BETWEEN ? AND ?) " +
                "   OR " +
                "   (EXTRACT(MONTH FROM birthday) = ? AND EXTRACT(DAY FROM birthday) BETWEEN 1 AND ?)" +
                ") " +
                "ORDER BY EXTRACT(MONTH FROM birthday), EXTRACT(DAY FROM birthday)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, telegramId);
            pstmt.setInt(2, now.getMonthValue());
            pstmt.setInt(3, now.getDayOfMonth());
            pstmt.setInt(4, now.lengthOfMonth());
            pstmt.setInt(5, sameDayNextMonth.getMonthValue());
            pstmt.setInt(6, sameDayNextMonth.getDayOfMonth());

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    users.add(new BirthdayUser(
                            rs.getInt("id"),
                            rs.getLong("telegram_id"),
                            rs.getString("name"),
                            rs.getDate("birthday").toLocalDate()
                    ));
                }
            }

            LOGGER.info("Retrieved " + users.size() + " users with birthdays from " +
                    now + " to " + sameDayNextMonth);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to get users with birthdays for the next month period", e);
        }

        return users;
    }

    public List<BirthdayUser> getAllUsersOnMonth(int month, long chatID) {
        List<BirthdayUser> users = new ArrayList<>();
        String sql = "SELECT id, telegram_id, name, birthday " +
                "FROM users " +
                "WHERE EXTRACT(MONTH FROM birthday) = ? " +
                "AND telegram_id = ? " +
                "ORDER BY EXTRACT(DAY FROM birthday), name";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, month);
            pstmt.setLong(2, chatID);

            LOGGER.info("Executing query for month=" + month + ", chatID=" + chatID);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    users.add(new BirthdayUser(
                            rs.getInt("id"),
                            rs.getLong("telegram_id"),
                            rs.getString("name"),
                            rs.getDate("birthday").toLocalDate()
                    ));
                }
            }

            LOGGER.info("Retrieved " + users.size() + " users for month: " + month + ", chatID: " + chatID);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to get users for month: " + month + ", chatID: " + chatID, e);
        }

        return users;
    }

    public List<BirthdayUser> getTodayBirthdays() {
        List<BirthdayUser> birthdays = new ArrayList<>();
        String sql = "SELECT id, telegram_id, name, birthday " +
                "FROM users " +
                "WHERE EXTRACT(MONTH FROM birthday) = ? " +
                "AND EXTRACT(DAY FROM birthday) = ?";

        LocalDate today = LocalDate.now();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, today.getMonthValue());
            pstmt.setInt(2, today.getDayOfMonth());

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    birthdays.add(new BirthdayUser(
                            rs.getInt("id"),
                            rs.getLong("telegram_id"),
                            rs.getString("name"),
                            rs.getDate("birthday").toLocalDate()
                    ));
                }
            }

            LOGGER.info("Found " + birthdays.size() + " birthdays today");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to get today's birthdays", e);
        }

        return birthdays;
    }

    public boolean userExists(long telegramId) {
        String sql = "SELECT COUNT(*) FROM users WHERE telegram_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, telegramId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to check user existence: " + telegramId, e);
        }

        return false;
    }

    public boolean updateBirthday(long telegramId, LocalDate birthday) {
        String sql = "UPDATE users SET birthday = ?, updated_at = CURRENT_TIMESTAMP WHERE telegram_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDate(1, Date.valueOf(birthday));
            pstmt.setLong(2, telegramId);

            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected > 0) {
                LOGGER.info(String.format("Birthday updated for telegram_id=%d: %s", telegramId, birthday));
                return true;
            }

            LOGGER.info("User not found for birthday update: telegram_id=" + telegramId);
            return false;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to update birthday for telegram_id: " + telegramId, e);
            return false;
        }
    }

    public boolean updateName(long telegramId, String name) {
        String sql = "UPDATE users SET name = ?, updated_at = CURRENT_TIMESTAMP WHERE telegram_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, name);
            pstmt.setLong(2, telegramId);

            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected > 0) {
                LOGGER.info(String.format("Name updated for telegram_id=%d: %s", telegramId, name));
                return true;
            }

            LOGGER.info("User not found for name update: telegram_id=" + telegramId);
            return false;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to update name for telegram_id: " + telegramId, e);
            return false;
        }
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LOGGER.info("Database connection pool closed");
        }
    }

    public String getPoolStats() {
        if (dataSource != null) {
            return String.format(
                    "Active connections: %d, Idle connections: %d, Total connections: %d",
                    dataSource.getHikariPoolMXBean().getActiveConnections(),
                    dataSource.getHikariPoolMXBean().getIdleConnections(),
                    dataSource.getHikariPoolMXBean().getTotalConnections()
            );
        }
        return "Connection pool not initialized";
    }

    // Метод для BirthdayScheduler
    public List<BirthdayNotification> getTodayNotifications() {
        List<BirthdayNotification> notifications = new ArrayList<>();
        List<BirthdayUser> todayBirthdays = getTodayBirthdays();

        for (BirthdayUser user : todayBirthdays) {
            notifications.add(new BirthdayNotification(
                    user.getTelegramId(),
                    user.getName()
            ));
        }

        return notifications;
    }
}