package fm.francoisefm;

import java.io.File;

import static fm.francoisefm.ServletHelper.RECORDINGS;

public class Recording {

    public final String token;
    public final String fileName;
    public final File file;

    public Recording(String token, String fileName) {
        this.token = token;
        this.fileName = fileName;
        this.file = new File(new File(RECORDINGS, token), fileName);
    }
}
