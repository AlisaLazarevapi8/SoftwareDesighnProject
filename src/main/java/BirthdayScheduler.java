import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BirthdayScheduler {
    private final ScheduledExecutorService scheduler;
    private final TelegramBot bot;
    private final DatabaseManager database;

    private final HuggingFaceDialoGPT huggingFaceDialoGPT = new HuggingFaceDialoGPT();

    public BirthdayScheduler(TelegramBot myBot, DatabaseManager myDatabase) {
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.bot = myBot;
        this.database = myDatabase;
    }

    public void start(LocalDate date) {
        scheduleDailyCheck(9, 0, date);
    }

    private void scheduleDailyCheck(int hour, int minute, LocalDate date) {
        LocalTime targetTime = LocalTime.of(hour, minute);
        LocalTime now = LocalTime.now();

        long initialDelay;
        if (now.isBefore(targetTime)) {
            initialDelay = Duration.between(now, targetTime).toMinutes();
        } else {
            initialDelay = Duration.between(now, targetTime.plusHours(24)).toMinutes();
        }
//
        scheduler.scheduleAtFixedRate(() -> {
                    LocalDate today = LocalDate.now();
                    checkBirthdays(date);
                }, initialDelay,
                24 * 60,
                TimeUnit.MINUTES);
    }

    private void checkBirthdays(LocalDate userBD) {
        try {
            LocalDate today = LocalDate.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            String dateString = userBD.format(formatter);

            Map<Long, String> users = this.database.getUserByBirthday(dateString);

            for (Map.Entry<Long, String> entry : users.entrySet()) {
                Long chatId = entry.getKey();
                String name = entry.getValue();
                String message = "🎉 Сегодня день рождения у " + name + "! Поздравляю!";

                //вроде работает
                SendMessage congratulation = new SendMessage(chatId, message);
                bot.execute(congratulation);
            }

        } catch (Exception e) {
            System.err.println("Error in birthday check: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
