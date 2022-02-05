package fm.francoisefm;

import java.nio.file.Path;

public class RadioServlet extends RecordingServlet {

    @Override
    public Path getRecordingLocation() {
        return ServletHelper.CONVERTED;
    }
}
