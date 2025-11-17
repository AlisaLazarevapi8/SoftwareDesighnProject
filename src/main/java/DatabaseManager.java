import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class DatabaseManager {
    private static String URL;
    private static String USER;
    private static String PASSWORD;

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public void createUsersTable(String url, String user, String password) {
        URL = url;
        USER = user;
        PASSWORD = password;

        String createTableSql = "CREATE TABLE IF NOT EXISTS users (" +
                "id SERIAL PRIMARY KEY, " +
                "chat_id BIGINT NOT NULL, " +
                "username VARCHAR(255), " +
                //"birthdate VARCHAR(255), " +
                "birthdate DATE, " +
                "registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {

            stmt.execute(createTableSql);
            System.out.println("Таблица users создана или уже существует");

        } catch (SQLException e) {
            System.out.println("Ошибка при создании таблицы: " + e.getMessage());
            e.printStackTrace(); // осуждаю
        }
    }
//string birthday
    public void addUser(Long chatId, String username, LocalDate birthdate) {
        ensureTableExists();

        String sql = "INSERT INTO users (chat_id, username, birthdate) VALUES (?, ?, ?)";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, chatId);
            pstmt.setString(2, username);
            pstmt.setDate(3, Date.valueOf(birthdate)); //тяжело

            pstmt.executeUpdate();
            System.out.println("Пользователь добавлен: chatId=" + chatId + ", name=" + username + ", date=" + birthdate);
        } catch (SQLException e) {
            System.out.println("Ошибка при добавлении пользователя: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void deleteUser(Integer Id) {
        String sql = "DELETE FROM users WHERE id = ?";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, Id);
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                System.out.println("Пользователь с chat_id " + Id + " удален");
            } else {
                System.out.println("Пользователь с chat_id " + Id + " не найден");
            }

        } catch (SQLException e) {
            System.out.println("Ошибка при удалении пользователя: " + e.getMessage());
        }
    }

    public String getAllUsers() {
        ensureTableExists();

        StringBuilder result = new StringBuilder();
        String sql = "SELECT id, chat_id, username, birthdate, registered_at FROM users ORDER BY registered_at DESC";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            result.append("📊 Список всех дней рождения:\n\n");

            int count = 0;
            while (rs.next()) {
                count++;
                int id = rs.getInt("id");
                String username = rs.getString("username");
                String birthdate = rs.getString("birthdate");
                Timestamp registeredAt = rs.getTimestamp("registered_at");

                String regDate = new SimpleDateFormat("dd.MM.yyyy HH:mm").format(registeredAt);

                result.append(String.format(
                        "👤 Запись #%d\n" +
                                "🆔 ID записи: %d\n" +
                                "📛 Имя: %s\n" +
                                "🎂 День рождения: %s\n" +
                                "📅 Добавлено: %s\n\n",
                        count,
                        id,
                        username != null ? username : "не указан",
                        birthdate != null ? birthdate : "не указана",
                        regDate
                ));
            }

            if (count == 0) {
                result.append("❌ В базе данных пока нет записей о днях рождения");
            } else {
                result.append("Всего записей: ").append(count);
            }

        } catch (SQLException e) {
            result.setLength(0);
            result.append("Ошибка при получении пользователей: ").append(e.getMessage());
            e.printStackTrace(); //так кстате давно никто не пишет держу в курсеее
        }

        return result.toString();
    }


    public Map<Long, String> getUserByBirthday(String date) {
        ensureTableExists();

        Map<Long, String> users = new HashMap<>();
        //StringBuilder result = new StringBuilder();
        String sql = "SELECT chat_id, username FROM users WHERE birthdate = ?"; //не нравится мне все это


        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, date); // пронесло можно считать

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Long chatId = rs.getLong("chat_id");
                    String username = rs.getString("username");
                    users.put(chatId, username);
                }
            }

        } catch (SQLException e) {
            System.out.println("Ошибка при получении пользователей: " + e.getMessage());
            e.printStackTrace();
        }

        return users;
    }

    public String getUpcomingBirthdays() {
        ensureTableExists();

        StringBuilder result = new StringBuilder();

        String sql = "SELECT username, birthdate FROM users" +
                "WHERE (EXTRACT(MONTH FROM birthdate) > EXTRACT(MONTH FROM CURRENT_DATE" +
                "OR (EXTRACT(MONTH FROM birthdate) > EXTRACT(MONTH FROM CURRENT_DATE" +
                "AND (EXTRACT(MONTH FROM birthdate) >= EXTRACT(MONTH FROM CURRENT_DATE" +
                "ORDER BY EXTRACT(MONTH FROM birthdate), EXTRACT(DAY FROM birthdate)";

        try(Connection conn = connect();
        PreparedStatement pstmt = conn.prepareStatement(sql);
        ResultSet rs  = pstmt.executeQuery();){
            result.append("Ближайшие дни рождения: \n");
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

            int counter = 0;
            while (rs.next()){
                counter++;
                String username = rs.getString("username");
                java.util.Date birthdate = rs.getDate("birthdate");
                String formattedDate = LocalDate.ofEpochDay(birthdate.getTime() / (24 * 60 * 60 * 1000)).format(formatter);

                result.append(String.format("%s - %s\n", username, formattedDate));
            }
            if (counter == 0){
                result.append("В ближайший месяц нет дней рождений)");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return result.toString();
    }
    //чиним гит
    private void ensureTableExists() {
        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {

            String checkSql = "SELECT 1 FROM users LIMIT 1";
            stmt.executeQuery(checkSql);

        } catch (SQLException e) {
            System.out.println("Таблица users не существует, создаем...");
            createUsersTable(URL, USER, PASSWORD);
        }
    }
}