import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class BotTest {

    @Test
    void testIsValidDate_ValidDate() {
        // Arrange
        String validDate = "15.12.2023";

        // Act
        boolean result = Bot.isValidDate(validDate);

        // Assert
        assertTrue(result);
    }

    @Test
    void testIsValidDate_InvalidDate_WrongFormat() {
        // Arrange
        String invalidDate1 = "15-12-2023";
        String invalidDate2 = "2023.12.15";
        String invalidDate3 = "15.12.23";
        String invalidDate4 = "1.12.2023";
        String invalidDate5 = "15.1.2023";

        // Act & Assert
        assertFalse(Bot.isValidDate(invalidDate1));
        assertFalse(Bot.isValidDate(invalidDate2));
        assertFalse(Bot.isValidDate(invalidDate3));
        assertFalse(Bot.isValidDate(invalidDate4));
        assertFalse(Bot.isValidDate(invalidDate5));
    }

    @Test
    void testIsValidDate_InvalidDate_EmptyString() {
        // Arrange
        String emptyDate = "";

        // Act
        boolean result = Bot.isValidDate(emptyDate);

        // Assert
        assertFalse(result);
    }

    @Test
    void testIsValidDate_ValidDate_WithLeadingZeros() {
        // Arrange
        String dateWithZeros = "01.01.2023";

        // Act
        boolean result = Bot.isValidDate(dateWithZeros);

        // Assert
        assertTrue(result);
    }

    @Test
    void testIsValidDate_ValidDate_BoundaryValues() {
        // Arrange
        String date1 = "31.12.2023";
        String date2 = "01.01.2023";
        String date3 = "29.02.2024"; // Високосный год

        // Act & Assert
        assertTrue(Bot.isValidDate(date1));
        assertTrue(Bot.isValidDate(date2));
        assertTrue(Bot.isValidDate(date3));
    }
}
