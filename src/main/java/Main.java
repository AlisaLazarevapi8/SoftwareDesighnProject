public class Main {
    public static void main(String[] args) {
        String token = args[0];
        String url = args[1];
        String username = args[2];
        String password = args[3];
        String apiToken = args[4];
        Bot.start(token, url, username, password, apiToken);

        System.out.println("Бот запущен...");
    }
}