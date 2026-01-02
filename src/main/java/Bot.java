import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Bot {

    private enum State { WAITING_FOR_NAME, WAITING_FOR_DATE, WAITING_FOR_ID_TO_DELETE }

    private static final Map<Long, State> userStates = new HashMap<>();
    private static final Map<Long, String> tempNames = new HashMap<>();

    public static void start(String botToken, String url, String username, String password, String apiToken) {
        TelegramBot bot = new TelegramBot(botToken);

        DatabaseManager dbManager = new DatabaseManager();
        dbManager.initialize(url, username, password);

        BirthdayScheduler scheduler = new BirthdayScheduler(bot, dbManager);
        scheduler.start();

        bot.setUpdatesListener(updates -> {
            for (Update update : updates) {
                if (update.message() == null || update.message().text() == null) continue;

                long chatId = update.message().chat().id();
                if (update.message().from() == null || update.message().from().id() == null) continue;
                long ownerUserId = update.message().from().id().longValue();

                String text = update.message().text().trim();
                String ownerName = update.message().from().firstName();

                // каждый раз обновляем: "чат владельца" = чат, где он сейчас пишет
                dbManager.upsertOwner(ownerUserId, chatId);

                if (text.equals("/start")) {
                    sendMessage(bot, chatId,
                            "Привет, " + ownerName + "!\n" +
                                    "Я бот, который помогает помнить дни рождения.\n\n" +
                                    "Команды:\n" +
                                    "/newBirthday — добавить день рождения\n" +
                                    "/allBirthdays — показать мой список\n" +
                                    "/deleteBirthday — удалить по id\n" +
                                    "/поздравь — сгенерировать поздравление (нейронка)\n");
                    continue;
                }

                handleCommand(bot, chatId, ownerUserId, text, dbManager, apiToken, ownerName);
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }

    private static void handleCommand(
            TelegramBot bot,
            long chatId,
            long ownerUserId,
            String command,
            DatabaseManager dbManager,
            String apiToken,
            String ownerName
    ) {
        State state = userStates.get(ownerUserId);

        if (state != null) {
            switch (state) {
                case WAITING_FOR_NAME : {
                    tempNames.put(ownerUserId, command);
                    userStates.put(ownerUserId, State.WAITING_FOR_DATE);
                    sendMessage(bot, chatId, "Когда поздравляем? (дата рождения вида DD.MM.YYYY)");
                    return;
                }
                case WAITING_FOR_DATE : {
                    String personName = tempNames.get(ownerUserId);
                    String dateStr = command;

                    if (!isValidDate(dateStr)) {
                        sendMessage(bot, chatId, "Неверный формат даты. Используй DD.MM.YYYY");
                        userStates.remove(ownerUserId);
                        tempNames.remove(ownerUserId);
                        return;
                    }

                    try {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
                        LocalDate birthdate = LocalDate.parse(dateStr, formatter);

                        long id = dbManager.addBirthday(ownerUserId, personName, birthdate);
                        sendMessage(bot, chatId, "Добавлено! id=" + id + " — " + personName + " (" + dateStr + ")");
                    } catch (Exception e) {
                        sendMessage(bot, chatId, "Что-то сломалось при добавлении :(");
                    } finally {
                        userStates.remove(ownerUserId);
                        tempNames.remove(ownerUserId);
                    }
                    return;
                }
                case WAITING_FOR_ID_TO_DELETE : {
                    try {
                        long id = Long.parseLong(command);
                        boolean deleted = dbManager.deleteBirthday(ownerUserId, id);
                        sendMessage(bot, chatId, deleted ? "Удалено." : "Не нашла такую запись (или она не твоя).");
                    } catch (NumberFormatException e) {
                        sendMessage(bot, chatId, "Нужно число (id записи).");
                    } finally {
                        userStates.remove(ownerUserId);
                        tempNames.remove(ownerUserId);
                    }
                    return;
                }
            }
        }

        switch (command.toLowerCase()) {
            case "/newbirthday" : {
                userStates.put(ownerUserId, State.WAITING_FOR_NAME);
                sendMessage(bot, chatId, "Кого поздравляем? (введи имя)");
            }
            case "/allbirthdays" : {
                List<BirthdayEntry> list = dbManager.getBirthdaysByOwner(ownerUserId);
                if (list.isEmpty()) {
                    sendMessage(bot, chatId, "У тебя пока пусто. Добавь через /newBirthday");
                    return;
                }

                StringBuilder sb = new StringBuilder("Твой список ДР:\n");
                for (BirthdayEntry e : list) {
                    sb.append("id=").append(e.getId())
                            .append(" — ").append(e.getPersonName())
                            .append(" — ").append(e.getBirthdayFormatted())
                            .append("\n");
                }
                sendMessage(bot, chatId, sb.toString());
            }
            case "/deletebirthday" : {
                List<BirthdayEntry> list = dbManager.getBirthdaysByOwner(ownerUserId);
                if (list.isEmpty()) {
                    sendMessage(bot, chatId, "Удалять нечего, список пуст.");
                    return;
                }

                StringBuilder sb = new StringBuilder("Что удалить? Напиши id:\n");
                for (BirthdayEntry e : list) {
                    sb.append("id=").append(e.getId())
                            .append(" — ").append(e.getPersonName())
                            .append(" — ").append(e.getBirthdayFormatted())
                            .append("\n");
                }
                sendMessage(bot, chatId, sb.toString());
                userStates.put(ownerUserId, State.WAITING_FOR_ID_TO_DELETE);
            }
            case "/поздравь" : {
                sendMessage(bot, chatId, "Генерируем поздравление... подожди чуть-чуть.");
                String greeting = RuGPT3Generator.generateGreeting(apiToken, ownerName);
                sendMessage(bot, chatId, greeting);
            }
            default : {
                if (command.startsWith("/")) {
                    sendMessage(bot, chatId, "Неизвестная команда: " + command);
                }
            }
        }
    }

    private static boolean isValidDate(String date) {
        return date.matches("\\d{2}\\.\\d{2}\\.\\d{4}");
    }

    private static void sendMessage(TelegramBot bot, long chatId, String text) {
        bot.execute(new SendMessage(chatId, text));
    }
}
