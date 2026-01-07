import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BirthdaySchedulerTest {

    @Mock
    private TelegramBot bot;

    @Mock
    private DatabaseManager database;

    @Mock
    private ScheduledExecutorService scheduler;

    private BirthdayScheduler birthdayScheduler;

    @BeforeEach
    void setUp() throws Exception {
        // Создаем реальный объект
        birthdayScheduler = new BirthdayScheduler(bot, database);

        // Подменяем scheduler на mock с помощью рефлексии
        Field schedulerField = BirthdayScheduler.class.getDeclaredField("scheduler");
        schedulerField.setAccessible(true);
        schedulerField.set(birthdayScheduler, scheduler);
    }

    @Test
    void testStart_ShouldLogAndSchedule() {
        // Act
        birthdayScheduler.start();

        // Assert
        verify(scheduler).scheduleAtFixedRate(
                any(Runnable.class),
                anyLong(),
                eq(24 * 60L),
                eq(TimeUnit.MINUTES)
        );
    }

    @Test
    void testCheckBirthdays_WithNotifications() throws Exception {
        // Arrange
        List<BirthdayNotification> notifications = Arrays.asList(
                new BirthdayNotification(123L, "Иван"),
                new BirthdayNotification(456L, "Мария")
        );

        when(database.getTodayNotifications()).thenReturn(notifications);

        // Получаем приватный метод через рефлексию
        Method checkBirthdaysMethod = BirthdayScheduler.class.getDeclaredMethod("checkBirthdays");
        checkBirthdaysMethod.setAccessible(true);

        // Act
        checkBirthdaysMethod.invoke(birthdayScheduler);

        // Assert
        verify(database, times(1)).getTodayNotifications();

        // Используем ArgumentCaptor для захвата SendMessage объектов
        ArgumentCaptor<SendMessage> sendMessageCaptor = ArgumentCaptor.forClass(SendMessage.class);
        verify(bot, times(2)).execute(sendMessageCaptor.capture());

        List<SendMessage> sentMessages = sendMessageCaptor.getAllValues();
        // Проверяем, что сообщения были отправлены правильным пользователям
        // (не проверяем точные объекты, только факт отправки)
        assert sentMessages.size() == 2;
    }

    @Test
    void testCheckBirthdays_NoNotifications() throws Exception {
        // Arrange
        when(database.getTodayNotifications()).thenReturn(Collections.emptyList());

        Method checkBirthdaysMethod = BirthdayScheduler.class.getDeclaredMethod("checkBirthdays");
        checkBirthdaysMethod.setAccessible(true);

        // Act
        checkBirthdaysMethod.invoke(birthdayScheduler);

        // Assert
        verify(database, times(1)).getTodayNotifications();
        verify(bot, never()).execute(any(SendMessage.class));
    }

    @Test
    void testCheckBirthdays_Exception() throws Exception {
        // Arrange
        when(database.getTodayNotifications()).thenThrow(new RuntimeException("DB error"));

        Method checkBirthdaysMethod = BirthdayScheduler.class.getDeclaredMethod("checkBirthdays");
        checkBirthdaysMethod.setAccessible(true);

        // Act
        checkBirthdaysMethod.invoke(birthdayScheduler);

        // Assert
        // Исключение должно быть поймано внутри метода
        verify(database, times(1)).getTodayNotifications();
        verify(bot, never()).execute(any(SendMessage.class));
    }

    @Test
    void testStop_ShouldShutdownScheduler() {
        // Act
        birthdayScheduler.stop();

        // Assert
        // Убираем when() для scheduler.isShutdown() - это лишний мок
        // Метод stop() вызывает shutdown() независимо от isShutdown()
        verify(scheduler).shutdown();
        // Метод awaitTermination также вызывается, можно проверить:
        try {
            verify(scheduler).awaitTermination(eq(5L), eq(TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
