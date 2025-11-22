import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BirthdayScheduler {
    private static final Logger LOGGER = Logger.getLogger(BirthdayScheduler.class.getName());

    private final ScheduledExecutorService scheduler;
    private final TelegramBot bot;
    private final DatabaseManager database;

    private  static final int CHECK_HOUR = 9;
    private static final int CHECK_MINUTE = 0;

    public BirthdayScheduler(TelegramBot myBot, DatabaseManager myDatabase) {
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.bot = myBot;
        this.database = myDatabase;
    }

    public void start() {
        LOGGER.info("Начало работы планировщика");
        scheduleDailyCheck();
    }

    private void scheduleDailyCheck() {
        LocalTime targetTime = LocalTime.of(CHECK_HOUR, CHECK_MINUTE);
        LocalTime now = LocalTime.now();

        long initialDelay;
        if (now.isBefore(targetTime)) {
            initialDelay = Duration.between(now, targetTime).toMinutes();
        } else {
            initialDelay = Duration.between(now, targetTime.plusHours(24)).toMinutes();
        }
//
        scheduler.scheduleAtFixedRate(
                this::checkBirthdays,
                initialDelay,
                24 * 60,
                TimeUnit.MINUTES
        );
    }

    private void checkBirthdays() {
        try {
            LOGGER.info("проверка дней рождений");

            List<User> todayBirthdays = database.getTodayBirthdays();

            if (todayBirthdays.isEmpty()) {
                LOGGER.info("Сегодня нет дней рождений");
                return;
            }

            LOGGER.info("Сегодня " + todayBirthdays.size() + " дней рождений");

            for (User user : todayBirthdays) {
                sendCongratulation(user);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Ошибка при проверке дней рождений", e);
        }
    }

    private void sendCongratulation(User user) {
        try {
            String message = String.format(" Сегодня день рождения у %s! Поздравляю! 🎂", user.getName());
            SendMessage request = new SendMessage(user.getTelegramId(), message);

            bot.execute(request);
            LOGGER.info("Congratulation sent to " + user.getName() + " (ID: " + user.getTelegramId() + ")");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to send congratulation to user: " + user.getTelegramId(), e);
        }
    }

    public void stop() {
        LOGGER.info("Stopping birthday scheduler");
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
