import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Logger;

public class Birthdays {
    private static final Logger LOGGER = Logger.getLogger(Birthdays.class.getName());
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Pattern BIRTHDAY_PATTERN = Pattern.compile("🎂 День рождения: (\\d{2}\\.\\d{2}\\.\\d{4})");

    public static List<LocalDate> extractBirthdaysWithRegex(String text) {
        List<LocalDate> birthdays = new ArrayList<>();
        Matcher matcher = BIRTHDAY_PATTERN.matcher(text);

        while (matcher.find()) {
            try {
                String dateStr = matcher.group(1);
                LocalDate date = convertToLocalDate(dateStr);
                birthdays.add(date);
            } catch (Exception e) {
                // скип
                LOGGER.warning("Failed to parse date: " + e.getMessage());
            }
        }

        return birthdays;
    }

    public static LocalDate convertToLocalDate(String dateString) {
        return LocalDate.parse(dateString, DATE_FORMATTER);
    }

    public static boolean isValidDateString(String dateString) {
        try {
            convertToLocalDate(dateString);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String convertToString(LocalDate date) {
        return date.format(DATE_FORMATTER);
    }
}
