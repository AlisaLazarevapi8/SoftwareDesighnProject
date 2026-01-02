public class BirthdayNotification {
    private final long notifyChatId;
    private final String personName;

    public BirthdayNotification(long notifyChatId, String personName) {
        this.notifyChatId = notifyChatId;
        this.personName = personName;
    }

    public long getNotifyChatId() {
        return notifyChatId;
    }

    public String getPersonName() {
        return personName;
    }
}
