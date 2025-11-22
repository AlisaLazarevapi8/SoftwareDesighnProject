import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.logging.Logger;
import java.util.logging.Level;



public class DatabaseManager {
    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());
    private HikariDataSource dataSource;

    //Инициализация
    public void initialize(String url, String username, String password) {
        try{
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(url);
            config.setUsername(username);
            config.setPassword(password);

            //настройка пула *няяяяя!!!!!!!!!))!)!)!)!)*
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30_000);
            config.setIdleTimeout(600_000);
            config.setMaxLifetime(1_800_000);

            config.setConnectionTestQuery("SELECT 1");

            dataSource = new HikariDataSource(config);

            createUsersTable();

            LOGGER.info("Пул успешно инициализирован!!!");
        } catch (Exception e){
            LOGGER.log(Level.SEVERE, "пул взорвала ядерная бомба", e);
        }
    }


    public void createUsersTable() {
        String createTableSql = """
                CREATE TABLE IF NOT EXISTS users (
                telegram_id BIGINT PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                birthday DATE NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(createTableSql);
            LOGGER.info("Таблица пользователей успешно создана или уже существовала");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "При создании таблицы пользователей произошла ошибка", e);
            throw  new RuntimeException("Ошибка при создании таблицы", e); //больше не осуждаю
        }
    }


    public boolean addUser(Long telegramId, String name, LocalDate birthday) {

        String sql = "INSERT INTO users (chat_id, username, birthdate) VALUES (?, ?, ?)" +
                "ON CONFLICT (telegram_id) DO NOTHING";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, telegramId);
            pstmt.setString(2, name);
            pstmt.setDate(3, Date.valueOf(birthday));

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                LOGGER.info(String.format("User added: telegram_id=%d, name=%s, birthday=%s",
                        telegramId, name, birthday));
                return true;
            }
            return false;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Ошибка при добавлении пользователя: " + telegramId, e);
            return false;
        }
    }

    public User getUserByTelegramId(long telegramId) {
        String sql = "SELECT telegram_id, name, birthday FROM users WHERE telegram_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, telegramId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getLong("telegram_id"),
                            rs.getString("name"),
                            rs.getDate("birthday").toLocalDate()
                    );
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to get user by telegram_id: " + telegramId, e);
        }

        return null;
    }

    public boolean deleteUserByTelegramId(long telegramId) {
        String sql = "DELETE FROM users WHERE telegram_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, telegramId);
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                LOGGER.info("User deleted with telegram_id: " + telegramId);
                return true;
            } else {
                LOGGER.info("User not found for deletion: telegram_id=" + telegramId);
                return false;
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to delete user with telegram_id: " + telegramId, e);
            return false;
        }
    }

    public List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT telegram_id, name, birthday FROM users ORDER BY name";

        try (Connection conn = dataSource.getConnection();
             Statement pstmt = conn.createStatement();
             ResultSet rs = pstmt.executeQuery(sql)) {

            while (rs.next()) {
                users.add(new User(
                        rs.getLong("telegram_id"),
                        rs.getString("name"),
                        rs.getDate("birthday").toLocalDate()
                ));
            }

            LOGGER.info("Retrieved " + users.size() + " users from database");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to get all users", e);
        }

        return users;
    }

    public List<User> getTodayBirthdays() {
        List<User> birthdays = new ArrayList<>();
        String sql = """
            SELECT telegram_id, name, birthday 
            FROM users 
            WHERE EXTRACT(MONTH FROM birthday) = ? 
            AND EXTRACT(DAY FROM birthday) = ?
            """;

        LocalDate today = LocalDate.now();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, today.getMonthValue());
            pstmt.setInt(2, today.getDayOfMonth());

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    birthdays.add(new User(
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

    /**
     * Получение статистики пула соединений
     */
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
}