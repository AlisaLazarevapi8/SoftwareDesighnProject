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
    private static final Map<Long, String> userStates = new HashMap<>();
    private static final Map<Long, String> tempNames = new HashMap<>();

    public static void start(String botToken, String url, String username, String password, String apiToken) {
        TelegramBot bot = new TelegramBot(botToken);
        DatabaseManager dbManager = new DatabaseManager();
        dbManager.initialize(url, username, password);

        BirthdayScheduler scheduler = new BirthdayScheduler(bot, dbManager);
        scheduler.start();

        bot.setUpdatesListener(updates -> {
            for (Update update : updates) {
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
                                + "/recentbirthdays - посмотреть все дни рождения на прошедший месяц\n"
                                + "/futurebirthdays - посмотреть все дни рождения на ближайший месяц\n"
                                + "/allbirthdaysonmonth - посмотреть все дни рождения на данный месяц\n"
                                + "/deleteBirthday - удалить день рождения из базы\n"
                                + "/getCongratulationByNeuro - получить сгенерированное поздравление\n");
                    } else {
                        handleCommand(bot, chatId, messageText, dbManager, apiToken, userName);
                    }
                }
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }

    private static void handleCommand(TelegramBot bot, Long chatId, String command,
                                      DatabaseManager dbManager, String apiToken, String userName) {
        String userState = userStates.get(chatId);

        if (userState != null) {
            switch (userState) {
                case "WAITING_FOR_MONTH":
                    try {
                        int month = Integer.parseInt(command.trim());

                        if (month < 1 || month > 12) {
                            sendMessage(bot, chatId, "Некорректный номер месяца. Введите число от 1 до 12.");
                            return;
                        }

                        List<BirthdayUser> users = dbManager.getAllUsersOnMonth(month, chatId);

                        userStates.remove(chatId);

                        if (users.isEmpty()) {
                            sendMessage(bot, chatId, String.format(
                                    "В %d месяце нет дней рождения.", month));
                        } else {
                            String[] monthNames = {
                                    "январе", "феврале", "марте", "апреле", "мае", "июне",
                                    "июле", "августе", "сентябре", "октябре", "ноябре", "декабре"
                            };

                            StringBuilder response = new StringBuilder(String.format(
                                    "🎂 Дни рождения в %s (%d месяц):\n\n",
                                    monthNames[month - 1], month));

                            for (int i = 0; i < users.size(); i++) {
                                BirthdayUser user = users.get(i);
                                response.append(i + 1).append(". ")
                                        .append(user.getName()).append(" - ")
                                        .append(user.getBirthdayFormatted()).append("\n");
                            }
                            sendMessage(bot, chatId, response.toString());
                        }

                    } catch (NumberFormatException e) {
                        sendMessage(bot, chatId, "Пожалуйста, введите число от 1 до 12.");
                    }
                    return;

                case "WAITING_FOR_NAME":
                    tempNames.put(chatId, command);
                    userStates.put(chatId, "WAITING_FOR_DATE");
                    sendMessage(bot, chatId, "Когда поздравляем? (дата рождения вида DD.MM.YYYY)");
                    return;

                case "WAITING_FOR_DATE":
                    Integer id = dbManager.getUsersNum(chatId) + 1;
                    String name = tempNames.get(chatId);
                    String dateStr = command;

                    if (isValidDate(dateStr)) {
                        try {
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
                            LocalDate birthdate = LocalDate.parse(dateStr, formatter);

                            dbManager.addUser(id, chatId, name, birthdate);
                            sendMessage(bot, chatId, "Ура, день рождения добавлен!");
                        } catch (Exception e) {
                            sendMessage(bot, chatId, "Что-то сломалось при добавлении.");
                        }
                    } else {
                        sendMessage(bot, chatId, "Неверный формат даты. Используйте DD.MM.YYYY");
                    }

                    userStates.remove(chatId);
                    tempNames.remove(chatId);
                    return;

                case "WAITING_FOR_ID_TO_DELETE":
                    try {
                        long userId = Long.parseLong(command);
                        if (dbManager.deleteUserById(userId)) {
                            sendMessage(bot, chatId, "Пользователь удалён из базы.");
                        } else {
                            sendMessage(bot, chatId, "Пользователь не найден.");
                        }
                    } catch (NumberFormatException e) {
                        sendMessage(bot, chatId, "Неверный id для удаления.");
                    }

                    userStates.remove(chatId);
                    tempNames.remove(chatId);
                    return;
            }
        }

        switch (command.toLowerCase()) {
            case "/newbirthday":
                userStates.put(chatId, "WAITING_FOR_NAME");
                sendMessage(bot, chatId, "Кого поздравляем? (введите имя)");
                break;

            case "/allbirthdays":
                List<BirthdayUser> users = dbManager.getAllUsers(chatId);
                if (users.isEmpty()) {
                    sendMessage(bot, chatId, "В базе нет пользователей.");
                } else {
                    StringBuilder response = new StringBuilder("Пользователи в базе:\n");
                    for (int i = 0; i < users.size(); i++) {
                        BirthdayUser user = users.get(i);
                        response.append(i + 1).append(". ")
                                .append(user.getTelegramId()).append(". ")
                                .append(user.getName()).append(" - ")
                                .append(user.getBirthdayFormatted()).append("\n");
                    }
                    sendMessage(bot, chatId, response.toString());
                }
                break;

            case "/recentbirthdays":
                List<BirthdayUser> usersRec = dbManager.getAllRecUsers(chatId);
                if (usersRec.isEmpty()) {
                    sendMessage(bot, chatId, "В базе нет пользователей.");
                } else {
                    StringBuilder response = new StringBuilder("Пользователи в базе:\n");
                    for (int i = 0; i < usersRec.size(); i++) {
                        BirthdayUser user = usersRec.get(i);
                        response.append(i + 1).append(". ")
                                .append(user.getTelegramId()).append(". ")
                                .append(user.getName()).append(" - ")
                                .append(user.getBirthdayFormatted()).append("\n");
                    }
                    sendMessage(bot, chatId, response.toString());
                }
                break;

            case "/futurebirthdays":
                List<BirthdayUser> usersFut = dbManager.getAllFutUsers(chatId);
                if (usersFut.isEmpty()) {
                    sendMessage(bot, chatId, "В базе нет пользователей.");
                } else {
                    StringBuilder response = new StringBuilder("Пользователи в базе:\n");
                    for (int i = 0; i < usersFut.size(); i++) {
                        BirthdayUser user = usersFut.get(i);
                        response.append(i + 1).append(". ")
                                .append(user.getTelegramId()).append(". ")
                                .append(user.getName()).append(" - ")
                                .append(user.getBirthdayFormatted()).append("\n");
                    }
                    sendMessage(bot, chatId, response.toString());
                }
                break;

            case "/allbirthdaysonmonth":
                userStates.put(chatId, "WAITING_FOR_MONTH");
                sendMessage(bot, chatId, "Введите номер месяца (от 1 до 12):");
                break;

            case "/deletebirthday":
                List<BirthdayUser> usersForDelete = dbManager.getAllUsers(chatId);
                if (usersForDelete.isEmpty()) {
                    sendMessage(bot, chatId, "В базе нет пользователей для удаления.");
                } else {
                    StringBuilder response = new StringBuilder("Пользователи в базе:\n");
                    for (int i = 0; i < usersForDelete.size(); i++) {
                        BirthdayUser user = usersForDelete.get(i);
                        response.append(i + 1).append(". ").append(user.getName())
                                .append(" - ").append(user.getBirthdayFormatted()).append("\n");
                    }
                    sendMessage(bot, chatId, response.toString());
                    userStates.put(chatId, "WAITING_FOR_ID_TO_DELETE");
                    sendMessage(bot, chatId, "Напишите telegram_id пользователя, которого хотите удалить");
                }
                break;

            case "/getCongratulationByNeuro":
                sendMessage(bot, chatId, " Генерируем поздравление... Пожалуйста, подождите...ня");
                String greeting = RuGPT3Generator.generateGreeting(apiToken, userName);
                sendMessage(bot, chatId, greeting);
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
        bot.execute(request);
    }
}
