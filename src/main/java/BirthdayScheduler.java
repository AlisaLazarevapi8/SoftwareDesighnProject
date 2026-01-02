import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
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

    private static final int CHECK_HOUR = 9;
    private static final int CHECK_MINUTE = 0;

    public BirthdayScheduler(TelegramBot bot, DatabaseManager database) {
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.bot = bot;
        this.database = database;
    }

    public void start() {
        LOGGER.info("BirthdayScheduler started");
        scheduleDailyCheck();
    }

    private void scheduleDailyCheck() {
        LocalTime target = LocalTime.of(CHECK_HOUR, CHECK_MINUTE);
        LocalTime now = LocalTime.now();

        long initialDelayMinutes = now.isBefore(target)
                ? Duration.between(now, target).toMinutes()
                : Duration.between(now, target.plusHours(24)).toMinutes();

        scheduler.scheduleAtFixedRate(
                this::checkBirthdays,
                initialDelayMinutes,
                24 * 60L,
                TimeUnit.MINUTES
        );
    }

    private void checkBirthdays() {
        try {
            List<BirthdayNotification> notifications = database.getTodayNotifications();
            if (notifications.isEmpty()) {
                LOGGER.info("No birthdays today");
                return;
            }

            for (BirthdayNotification n : notifications) {
                String msg = "Сегодня день рождения у " + n.getPersonName() + "! Поздравляю! 🎂";
                bot.execute(new SendMessage(n.getNotifyChatId(), msg));
            }

            LOGGER.info("Sent " + notifications.size() + " birthday notifications");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "checkBirthdays failed", e);
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
