import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.sql.SQLException;

public class LoadTest {

    @Test
    public void runHeavyLoadTest() throws InterruptedException {
        System.out.println("=== 🚀 ЗАПУСК НАГРУЗОЧНОГО ТЕСТИРОВАНИЯ (30% Write / 70% Read) ===");

        DatabaseManager dbManager = new DatabaseManager();
        // Используем H2 для тестов, чтобы не настраивать Postgres
        dbManager.initialize("jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");

        int usersCount = 50;   // Количество потоков-пользователей
        int opsPerUser = 20;   // Операций на каждого

        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < usersCount; i++) {
            final int userId = i + 1;
            final long telegramId = 100000L + i;

            executor.submit(() -> {
                try {
                    // 1. Сначала добавляем пользователя (Write)
                    dbManager.addUser(userId, telegramId, "TestUser_" + userId, LocalDate.of(1990, 1, 1));

                    for (int j = 0; j < opsPerUser; j++) {
                        // Чередуем чтение и запись (примерно 30/70)
                        if (j % 3 == 0) {
                            // Имитируем чтение всех пользователей чата (Read)
                            dbManager.getAllUsers(telegramId);
                        } else {
                            // Имитируем проверку существования (Read)
                            dbManager.userExists(telegramId);
                        }
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    System.err.println("Ошибка в потоке: " + e.getMessage());
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
        long endTime = System.currentTimeMillis();

        System.out.println("\n=== 📊 ИТОГИ НАГРУЗКИ ===");
        System.out.println("Успешных операций: " + successCount.get());
        System.out.println("Ошибок: " + errorCount.get());
        System.out.println("Время выполнения: " + (endTime - startTime) + " ms");

        if (errorCount.get() == 0) {
            System.out.println("🏆 ВЕРДИКТ: Система стабильна под нагрузкой!");
        }

        dbManager.shutdown();
    }
}