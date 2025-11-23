import java.time.LocalDate;
import java.time.temporal.ChronoUnit;


public class User {
    private long telegramId;
    private String name;
    private LocalDate birthday;

    public User(long telegramId, String name, LocalDate birthday) {
        this.telegramId = telegramId;
        this.name = name;
        this.birthday = birthday;
    }

    public long getTelegramId() {
        return telegramId;
    }

    public void setTelegramId(long telegramId) {
        this.telegramId = telegramId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getBirthday() {
        return birthday;
    }

    public void setBirthday(LocalDate birthday) {
        this.birthday = birthday;
    }

    public int getAge() {
        return (int) ChronoUnit.YEARS.between(birthday, LocalDate.now());

    }

    public String getBirthdayFormatted() {
        return birthday.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    }

    @Override
    public String toString() {
        return "User{" +
                "telegramId=" + telegramId +
                ", name='" + name + '\'' +
                ", birthday=" + birthday +
                '}';
    }
}
