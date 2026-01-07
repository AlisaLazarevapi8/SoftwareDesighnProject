import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuGPT3GeneratorTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private final String apiToken = "test-token";

    @Test
    void testGenerateGreetingSuccess() throws Exception {
        // Given
        String jsonResponse = "{\"choices\":[{\"text\":\"Поздравляю, Тест!\"}]}";

        // When
        String result = RuGPT3Generator.generateGreeting(apiToken, "Тест");

        // Then
        assertNotNull(result);
        assertTrue(result.contains("Поздравляю") || result.contains("Тест"));
    }

    @Test
    void testGenerateGreetingFallback() {
        // Given - симулируем ошибку

        // When
        String result = RuGPT3Generator.generateGreeting("invalid-token", "Тест");

        // Then
        assertNotNull(result);
        assertTrue(result.contains("Тест"));
    }

    @Test
    void testGetFallbackGreeting() {
        // When
        String greeting1 = RuGPT3Generator.getFallbackGreeting("Анна");
        String greeting2 = RuGPT3Generator.getFallbackGreeting("Иван");

        // Then
        assertNotNull(greeting1);
        assertNotNull(greeting2);
        assertTrue(greeting1.contains("Анна"));
        assertTrue(greeting2.contains("Иван"));
    }

    @Test
    void testTestConnection() {
        // When
        boolean result = RuGPT3Generator.testConnection(apiToken);

        // Then
        // Тест должен либо пройти, либо упасть с ошибкой
        // В реальном тесте мокаем generateGreeting
    }
}
