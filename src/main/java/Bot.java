import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class Bot {
    private static final Map<Long, String> userStates = new HashMap<>();
    private static final Map<Long, String> tempNames = new HashMap<>();

    public static void start(String botToken, String url, String username, String password) {
        TelegramBot bot = new TelegramBot(botToken);
        DatabaseManager dbManager = new DatabaseManager();
        dbManager.createUsersTable(url, username, password);

        String res = dbManager.getAllUsers();
        Birthdays bd = new Birthdays();
        bd.initiate(res, bot, dbManager);

        bot.setUpdatesListener(updates -> {
            for (Update update: updates) {
                if (update.message() != null && update.message().text() != null) {
                    Long chatId = update.message().chat().id();
                    String messageText = update.message().text();
                    String userName = update.message().chat().firstName();

                    if (messageText.equals("/start")) {
                        sendMessage(bot, chatId, "Привет, " + userName + "!\n"
                                + "Я ваш бот и я умею поздравлять с днем рождения.\n"
                                + "Как мной пользоваться:\n"
                                + "/newBirthday - добавить день рождения в базу\n"
                                + "/allBirthdays - посмотреть все дни рождения в базе\n"
                                + "/deleteBirthday - удалить день рождения из базы\n");
                    } else {
                        handleCommand(bot, chatId, messageText, dbManager);
                    }
                }
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }

    private static void handleCommand(TelegramBot bot, Long chatId, String command, DatabaseManager dbManager) {
        String userState = userStates.get(chatId);

        if (userState != null) {
            switch (userState) {
                case "WAITING_FOR_NAME":
                    tempNames.put(chatId, command);
                    userStates.put(chatId, "WAITING_FOR_DATE");
                    sendMessage(bot, chatId, "Когда поздравляем? (дата рождения вида DD.MM.YYYY)");
                    return;

                case "WAITING_FOR_DATE":
                    String name = tempNames.get(chatId);
                    String dateStr = command;

                    if (isValidDate(dateStr)) {
                        try{
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
                            LocalDate birthdate = LocalDate.parse(dateStr, formatter);

                            dbManager.addUser(chatId, name, birthdate);
                            sendMessage(bot, chatId, "ура работает)");
                        } catch (Exception e){
                            sendMessage(bot, chatId, "что то сломалось");
                        }
                    } else {
                        sendMessage(bot, chatId, "Неверный формат даты. Используйте DD.MM.YYYY");
                    }

                    userStates.remove(chatId);
                    tempNames.remove(chatId);
                    return;

                case "WAITING_FOR_ID_TO_DELETE":
                    Integer id = Integer.parseInt(command);

                    dbManager.deleteUser(id);
                    sendMessage(bot, chatId, "Пользователь " + id + "удален из базы.");

                    userStates.remove(chatId);
                    tempNames.remove(chatId);
                    return;
            }
        }

        switch (command) {
            case "/newBirthday":
                userStates.put(chatId, "WAITING_FOR_NAME");
                sendMessage(bot, chatId, "Кого поздравляем? (введите имя)");
                break;

            case "/allBirthdays":
                String res = dbManager.getAllUsers();
                sendMessage(bot, chatId, res);
                break;

            case "/deleteBirthday":
                userStates.put(chatId, "WAITING_FOR_ID_TO_DELETE");
                String res2del = dbManager.getAllUsers();
                sendMessage(bot, chatId, res2del);
                sendMessage(bot, chatId, "Напишите id пользователя, которого хотите удалить");
                break;

            default:
                if (command.startsWith("/")) {
                    sendMessage(bot, chatId, "Неизвестная команда: " + command);
                }
                break;
        }
    }

    private static boolean isValidDate(String date) {
        return date.matches("\\d{2}\\.\\d{2}\\.\\d{4}");
    }

    private static void sendMessage(TelegramBot bot, Long chatId, String text) {
        SendMessage request = new SendMessage(chatId, text);
        SendResponse response = bot.execute(request);
    }
}
