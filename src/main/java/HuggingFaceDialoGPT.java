import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class HuggingFaceDialoGPT {
    public HuggingFaceDialoGPT() {
    }

    private static final String API_TOKEN = "TOKEN";

    public static void main(String[] args) throws Exception {
        String inputText = "Привет! Сгенерируй поздравление с днем рождения.";

        String jsonRequest = "{\"inputs\":\"" + inputText + "\"}";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api-inference.huggingface.co/models/microsoft/DialoGPT-large"))
                .header("Authorization", "Bearer " + API_TOKEN)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Response:");
        System.out.println(response.body());
    }
}
