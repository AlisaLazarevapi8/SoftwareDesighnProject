import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class Bot {
    private static final Map<Long, String> userStates = new HashMap<>();
    private static final Map<Long, String> tempNames = new HashMap<>();

    public static void start(String botToken, String url, String username, String password, String apiToken) {
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
                                + "/deleteBirthday - удалить день рождения из базы\n"
                                + "/поздравь - получить сгенерированное поздравление\n");
                    } else {
                        handleCommand(bot, chatId, messageText, dbManager, apiToken);
                    }
                }
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }

    private static void handleCommand(TelegramBot bot, Long chatId, String command, DatabaseManager dbManager, String apiToken) {
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
                            sendMessage(bot, chatId, "Ура, день рождения добавлен!");
                        } catch (Exception e){
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
                        Integer id = Integer.parseInt(command);
                        dbManager.deleteUser(id);
                        sendMessage(bot, chatId, "Пользователь " + id + " удалён из базы.");
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
                String res = dbManager.getAllUsers();
                sendMessage(bot, chatId, res);
                break;

            case "/deletebirthday":
                userStates.put(chatId, "WAITING_FOR_ID_TO_DELETE");
                String res2del = dbManager.getAllUsers();
                sendMessage(bot, chatId, res2del);
                sendMessage(bot, chatId, "Напишите id пользователя, которого хотите удалить");
                break;

            case "/поздравь":
                sendMessage(bot, chatId, "Генерируем поздравление... Пожалуйста, подождите.");
                String prompt = "Поздравление с днем рождения";
                String jsonResponse = generateGreeting(prompt, apiToken);
                String greeting = parseGeneratedText(jsonResponse);
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

    private static String generateGreeting(String prompt, String apiToken) {
        try {
            String jsonRequest = "{\"inputs\":\"" + prompt + "\"}";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api-inference.huggingface.co/models/microsoft/DialoGPT-large"))
                    .header("Authorization", "Bearer " + apiToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            return response.body();
        } catch (Exception e) {
            e.printStackTrace();
            return "Ошибка при генерации поздравления";
        }
    }

    private static String parseGeneratedText(String jsonResponse) {
        try {
            JsonElement element = JsonParser.parseString(jsonResponse);
            JsonArray array = element.getAsJsonArray();
            if (array.size() > 0) {
                return array.get(0).getAsJsonObject().get("generated_text").getAsString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Не удалось получить сгенерированный текст";
    }
}
