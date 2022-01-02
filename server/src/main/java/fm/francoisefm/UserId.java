package fm.francoisefm;

public class UserId {

    public final String name;
    public final String token;

    public UserId(String name, String token) {
        this.name = name;
        this.token = token;
    }

    @Override
    public String toString() {
        return name + "(" + token + ")";
    }
}
