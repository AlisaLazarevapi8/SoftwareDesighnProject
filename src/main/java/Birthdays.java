import com.pengrad.telegrambot.TelegramBot;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Birthdays {
    private BirthdayScheduler scheduler;
    private TelegramBot bot;
    private DatabaseManager database;


    public void initiate(String args, TelegramBot myBot, DatabaseManager myDatabase) {
        String text = args;
        this.bot = myBot;
        this.database = myDatabase;
        this.scheduler = new BirthdayScheduler(myBot, myDatabase);

        List<String> birthdays1 = extractBirthdaysWithRegex(text);
        for (String bd: birthdays1){
            LocalDate date = convertToLocalDate(bd);
            scheduler.start(date);
        }
    }
    public static List<String> extractBirthdaysWithRegex(String text) {
        List<String> birthdays = new ArrayList<>();
        Pattern pattern = Pattern.compile("🎂 День рождения: (\\d{2}\\.\\d{2}\\.\\d{4})");
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            birthdays.add(matcher.group(1));
        }
        return birthdays;
    }

    public static LocalDate convertToLocalDate(String dateString) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        return LocalDate.parse(dateString, formatter);
    }
}
