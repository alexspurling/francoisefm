package fm.francoisefm;

import java.io.File;
import java.nio.file.Path;

public class Recording {

    public final String token;
    public final String fileName;
    public final File file;

    public Recording(Path recordingLocation, String token, String fileName) {
        this.token = token;
        this.fileName = fileName;
        this.file = recordingLocation.resolve(token).resolve(fileName).toFile();
    }
}
