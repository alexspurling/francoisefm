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

    public String sanitisedName() {
        // Only these characters are allowed in our filenames
        return name.replaceAll("[^0-9a-zA-ZÀàÂâÆæÇçÉéÈèÊêËëÎîÏïÔôŒœÙùÛûÜüŸÿØøÅå]", "_");
    }

    public static void main(String[] args) {
        int printedLetters = 0;
        for (int i = 0; i < 0x20000; i++) {
            if (Character.isDefined(i) && !Character.isISOControl(i)) {
                System.out.print(new String(Character.toChars(i)));
                printedLetters++;
                if (printedLetters % 100 == 0) {
                    System.out.println();
                }
            }
        }
    }
}
