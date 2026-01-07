import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatabaseManagerTest {

    @Mock
    private HikariDataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private Statement statement;

    @Mock
    private ResultSet resultSet;

    @InjectMocks
    private DatabaseManager databaseManager;

    @AfterEach
    void tearDown() {
        reset(dataSource, connection, preparedStatement, statement, resultSet);
    }

    @Test
    void testInitialize_Success() throws SQLException {
        // Arrange
        String url = "jdbc:postgresql://localhost:5432/test";
        String username = "user";
        String password = "pass";

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);

        // Act
        databaseManager.initialize(url, username, password);

        // Assert
        assertNotNull(databaseManager);
        // Можно добавить дополнительные проверки состояния
    }

    @Test
    void testCreateUsersTable_Success() throws SQLException {
        // Arrange
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute(anyString())).thenReturn(true);

        // Act & Assert
        assertDoesNotThrow(() -> databaseManager.createUsersTable());

        // Verify
        verify(statement, times(1)).execute(anyString());
    }

    @Test
    void testCreateUsersTable_SQLException() throws SQLException {
        // Arrange
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute(anyString())).thenThrow(new SQLException("Test exception"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> databaseManager.createUsersTable());
    }

    @Test
    void testAddUser_Success() throws SQLException {
        // Arrange
        int id = 1;
        Long telegramId = 12345L;
        String name = "John Doe";
        LocalDate birthday = LocalDate.of(1990, 1, 1);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);

        // Act
        boolean result = databaseManager.addUser(id, telegramId, name, birthday);

        // Assert
        assertTrue(result);
        verify(preparedStatement, times(1)).setInt(1, id);
        verify(preparedStatement, times(1)).setLong(2, telegramId);
        verify(preparedStatement, times(1)).setString(3, name);
        verify(preparedStatement, times(1)).setDate(4, Date.valueOf(birthday));
    }

    @Test
    void testAddUser_DuplicateKey() throws SQLException {
        // Arrange
        int id = 1;
        Long telegramId = 12345L;
        String name = "John Doe";
        LocalDate birthday = LocalDate.of(1990, 1, 1);

        SQLException duplicateException = new SQLException("Duplicate key", "23505");

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenThrow(duplicateException);

        // Act
        boolean result = databaseManager.addUser(id, telegramId, name, birthday);

        // Assert
        assertFalse(result);
    }

    @Test
    void testAddUser_OtherSQLException() throws SQLException {
        // Arrange
        int id = 1;
        Long telegramId = 12345L;
        String name = "John Doe";
        LocalDate birthday = LocalDate.of(1990, 1, 1);

        SQLException otherException = new SQLException("Other error", "12345");

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenThrow(otherException);

        // Act & Assert
        assertThrows(SQLException.class, () ->
                databaseManager.addUser(id, telegramId, name, birthday));
    }

    @Test
    void testGetUsersNum_Success() throws SQLException {
        // Arrange
        long telegramId = 12345L;
        int expectedCount = 5;

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt("user_count")).thenReturn(expectedCount);

        // Act
        int result = databaseManager.getUsersNum(telegramId);

        // Assert
        assertEquals(expectedCount, result);
        verify(preparedStatement, times(1)).setLong(1, telegramId);
    }

    @Test
    void testGetUsersNum_NoResults() throws SQLException {
        // Arrange
        long telegramId = 12345L;

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        // Act
        int result = databaseManager.getUsersNum(telegramId);

        // Assert
        assertEquals(0, result);
    }

    @Test
    void testGetUsersNum_SQLException() throws SQLException {
        // Arrange
        long telegramId = 12345L;

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenThrow(new SQLException());

        // Act
        int result = databaseManager.getUsersNum(telegramId);

        // Assert
        assertEquals(0, result);
    }

    @Test
    void testDeleteUserById_Success() throws SQLException {
        // Arrange
        long telegramId = 12345L;

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);

        // Act
        boolean result = databaseManager.deleteUserById(telegramId);

        // Assert
        assertTrue(result);
        verify(preparedStatement, times(1)).setLong(1, telegramId);
    }

    @Test
    void testDeleteUserById_NotFound() throws SQLException {
        // Arrange
        long telegramId = 12345L;

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(0);

        // Act
        boolean result = databaseManager.deleteUserById(telegramId);

        // Assert
        assertFalse(result);
    }

    @Test
    void testGetAllUsers_Success() throws SQLException {
        // Arrange
        long telegramId = 12345L;
        BirthdayUser expectedUser = new BirthdayUser(1, telegramId, "John Doe", LocalDate.of(1990, 1, 1));

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        when(resultSet.next()).thenReturn(true).thenReturn(false);
        when(resultSet.getLong("telegram_id")).thenReturn(expectedUser.getTelegramId());
        when(resultSet.getString("name")).thenReturn(expectedUser.getName());
        when(resultSet.getDate("birthday")).thenReturn(Date.valueOf(expectedUser.getBirthday()));

        // Act
        List<BirthdayUser> result = databaseManager.getAllUsers(telegramId);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(expectedUser.getName(), result.get(0).getName());
    }

    @Test
    void testGetAllUsers_Empty() throws SQLException {
        // Arrange
        long telegramId = 12345L;

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        // Act
        List<BirthdayUser> result = databaseManager.getAllUsers(telegramId);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetTodayBirthdays_Success() throws SQLException {
        // Arrange
        BirthdayUser expectedUser = new BirthdayUser(1, 12345L, "John Doe", LocalDate.now());

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        when(resultSet.next()).thenReturn(true).thenReturn(false);
        when(resultSet.getLong("telegram_id")).thenReturn(expectedUser.getTelegramId());
        when(resultSet.getString("name")).thenReturn(expectedUser.getName());
        when(resultSet.getDate("birthday")).thenReturn(Date.valueOf(expectedUser.getBirthday()));

        // Act
        List<BirthdayUser> result = databaseManager.getTodayBirthdays();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(expectedUser.getName(), result.get(0).getName());
    }

    @Test
    void testUserExists_True() throws SQLException {
        // Arrange
        long telegramId = 12345L;

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(1);

        // Act
        boolean result = databaseManager.userExists(telegramId);

        // Assert
        assertTrue(result);
    }

    @Test
    void testUserExists_False() throws SQLException {
        // Arrange
        long telegramId = 12345L;

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(0);

        // Act
        boolean result = databaseManager.userExists(telegramId);

        // Assert
        assertFalse(result);
    }

    @Test
    void testUpdateBirthday_Success() throws SQLException {
        // Arrange
        long telegramId = 12345L;
        LocalDate newBirthday = LocalDate.of(1995, 5, 15);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);

        // Act
        boolean result = databaseManager.updateBirthday(telegramId, newBirthday);

        // Assert
        assertTrue(result);
        verify(preparedStatement, times(1)).setDate(1, Date.valueOf(newBirthday));
        verify(preparedStatement, times(1)).setLong(2, telegramId);
    }

    @Test
    void testUpdateName_Success() throws SQLException {
        // Arrange
        long telegramId = 12345L;
        String newName = "Jane Doe";

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);

        // Act
        boolean result = databaseManager.updateName(telegramId, newName);

        // Assert
        assertTrue(result);
        verify(preparedStatement, times(1)).setString(1, newName);
        verify(preparedStatement, times(1)).setLong(2, telegramId);
    }

    @Test
    void testGetTodayNotifications_Success() throws SQLException {
        // Arrange
        List<BirthdayUser> todayBirthdays = new ArrayList<>();
        todayBirthdays.add(new BirthdayUser(1, 12345L, "John Doe", LocalDate.now()));
        todayBirthdays.add(new BirthdayUser(2, 67890L, "Jane Smith", LocalDate.now()));

        // Mock the getTodayBirthdays() method
        DatabaseManager spyManager = spy(databaseManager);
        doReturn(todayBirthdays).when(spyManager).getTodayBirthdays();

        // Act
        List<BirthdayNotification> result = spyManager.getTodayNotifications();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    void testGetTodayNotifications_Empty() throws SQLException {
        // Arrange
        // Mock the getTodayBirthdays() method to return empty list
        DatabaseManager spyManager = spy(databaseManager);
        doReturn(new ArrayList<BirthdayUser>()).when(spyManager).getTodayBirthdays();

        // Act
        List<BirthdayNotification> result = spyManager.getTodayNotifications();

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testShutdown() {
        // Arrange
        when(dataSource.isClosed()).thenReturn(false);

        // Act
        databaseManager.shutdown();

        // Assert
        verify(dataSource, times(1)).close();
    }

    @Test
    void testShutdown_AlreadyClosed() {
        // Arrange
        when(dataSource.isClosed()).thenReturn(true);

        // Act
        databaseManager.shutdown();

        // Assert
        verify(dataSource, never()).close();
    }
}
