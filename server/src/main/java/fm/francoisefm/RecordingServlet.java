package fm.francoisefm;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RecordingServlet extends HttpServlet {

    private static final Pattern AUDIO_FILE_PATTERN = Pattern.compile("^/(" + ServletHelper.UUID_PATTERN + ")/([^/]+)$");

    private static final Logger LOG = Logger.getLogger("AudioServlet");

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        LOG.info("OPTIONS: " + ServletHelper.getRequestURL(request));

        ServletHelper.setAllowHeaders(request, response);

        response.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        long startTime = System.currentTimeMillis();
        LOG.info("GET " + ServletHelper.getRequestURL(request));

        try {
            handleGet(request, response);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error handling GET", e);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
        long timeTaken = System.currentTimeMillis() - startTime;
        LOG.info("GET (" + timeTaken + "ms): " + response.getStatus());
    }

    private void handleGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        validatePath(request);
        ServletHelper.validateQueryString(request);
        ServletHelper.setAllowHeaders(request, response);

        // Don't validate Auth header on GET

        Recording recording = getRecording(request);
        validateRecording(recording);
        writeRecording(response, recording);
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        LOG.info("DELETE " + ServletHelper.getRequestURL(request));

        try {
            handleDelete(request, response);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error handling GET", e);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
        long timeTaken = System.currentTimeMillis() - startTime;
        LOG.info("GET (" + timeTaken + "ms): " + response.getStatus());
    }

    private void handleDelete(HttpServletRequest request, HttpServletResponse response) {

        validatePath(request);
        ServletHelper.validateQueryString(request);
        ServletHelper.setAllowHeaders(request, response);

        UserId userId = ServletHelper.getUserId(request);
        LOG.info("User id: " + userId);

        Recording recording = getRecording(request);
        if (!recording.token.equals(userId.token)) {
            throw new AudioServerException("Recording token does not match user token.");
        }
        deleteRecording(recording);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void deleteRecording(Recording recording) {
        if (!recording.file.delete()) {
            throw new AudioServerException("Failed to delete recording file " + recording.file.getAbsolutePath());
        }
    }

    private void writeRecording(HttpServletResponse response, Recording recording) throws IOException {
        try (FileInputStream fis = new FileInputStream(recording.file)) {
            fis.transferTo(response.getOutputStream());
        }
    }

    private void validateRecording(Recording recording) {
        if (!recording.file.exists()) {
            throw new AudioServerException("Recording file does not exist:" + recording.file.getAbsolutePath());
        }
    }

    private Recording getRecording(HttpServletRequest request) {
        String path = request.getPathInfo();
        Matcher matcher = AUDIO_FILE_PATTERN.matcher(path);
        if (matcher.matches()) {
            return new Recording(matcher.group(1), matcher.group(2));
        }
        throw new AudioServerException("Invalid path");
    }

    private void validatePath(HttpServletRequest request) {
        String path = request.getPathInfo();
        if (!AUDIO_FILE_PATTERN.matcher(path).matches()) {
            throw new AudioServerException("Invalid path");
        }
    }
}
