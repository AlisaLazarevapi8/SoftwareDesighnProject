import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class BirthdayEntry {
    private final long id;
    private final long ownerUserId;
    private final String personName;
    private final LocalDate birthday;

    public BirthdayEntry(long id, long ownerUserId, String personName, LocalDate birthday) {
        this.id = id;
        this.ownerUserId = ownerUserId;
        this.personName = personName;
        this.birthday = birthday;
    }

    public long getId() {
        return id;
    }

    public long getOwnerUserId() {
        return ownerUserId;
    }

    public String getPersonName() {
        return personName;
    }

    public LocalDate getBirthday() {
        return birthday;
    }

    public String getBirthdayFormatted() {
        return birthday.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    }
}
