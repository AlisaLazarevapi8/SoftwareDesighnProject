import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class HuggingFaceDialoGPT {
    public static void start(String arg) throws Exception {

        String inputText = "Привет! Сгенерируй поздравление с днем рождения.";

        String jsonRequest = "{\"inputs\":\"" + inputText + "\"}";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api-inference.huggingface.co/models/microsoft/DialoGPT-large"))
                .header("Authorization", "Bearer " + arg)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Response:");
        System.out.println(response.body());
    }
}
