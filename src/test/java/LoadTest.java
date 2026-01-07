import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class LoadTest {

    @Test
    public void runMegaLoadTest() throws InterruptedException {
        System.out.println("запуск нагрузочного теста...");

        DatabaseManager dbManager = new DatabaseManager();
        // инициализация пула соединений
        dbManager.initialize("jdbc:h2:mem:megatest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");

        int threads = 15; // кол-во потоков
        int totalRequests = 300; // кол-во операций

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        Random rng = new Random();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalRequests; i++) {
            final int requestId = i;
            executor.submit(() -> {
                long start = System.currentTimeMillis();
                try {
                    // генерация случайных данных
                    long telegramId = 1000 + rng.nextInt(100);
                    int action = rng.nextInt(100);

                    // распределение типов нагрузки
                    if (action < 20) {
                        dbManager.addUser(requestId, telegramId, "User_" + requestId, LocalDate.now());
                    } else if (action < 50) {
                        dbManager.updateName(telegramId, "Name_" + requestId);
                    } else if (action < 90) {
                        dbManager.getAllUsers(telegramId);
                    } else {
                        dbManager.deleteUserById(telegramId);
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    // замер задержки
                    totalLatency.addAndGet(System.currentTimeMillis() - start);
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.MINUTES);
        long endTime = System.currentTimeMillis();

        // итоговые метрики
        System.out.println("\nРезультаты тестирования:");
        System.out.println("Всего запросов: " + totalRequests);
        System.out.println("Успешно: " + successCount.get());
        System.out.println("Ошибок: " + errorCount.get());
        if (successCount.get() > 0) {
            System.out.println("Средняя задержка: " + (totalLatency.get() / successCount.get()) + " мс");
        }
        System.out.println("Общее время выполнения: " + (endTime - startTime) + " мс");

        dbManager.shutdown();
    }
}