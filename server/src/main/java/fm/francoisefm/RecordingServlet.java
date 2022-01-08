package fm.francoisefm;

import org.eclipse.jetty.io.EofException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RecordingServlet extends HttpServlet {

    private static final Pattern AUDIO_FILE_PATTERN = Pattern.compile("^/(" + ServletHelper.UUID_PATTERN + ")/([^/]+)$");
    private static final Pattern RANGE_HEADER_PATTERN = Pattern.compile("^bytes=([0-9]+)-([0-9]+)$");

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
        writeRecording(request, response, recording);
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

    private void writeRecording(HttpServletRequest request, HttpServletResponse response, Recording recording) throws IOException {
        // If the request has a Range header then return only the requested number of bytes
        String range = request.getHeader("Range");
        if (range != null) {
            Matcher matcher = RANGE_HEADER_PATTERN.matcher(range);
            if (matcher.matches()) {
                int byteFrom = Integer.parseInt(matcher.group(1));
                int byteTo = Integer.parseInt(matcher.group(2));
                writeRecording(response, recording, byteFrom, byteTo);
                return;
            }
        }
        writeRecording(response, recording);
    }

    private void writeRecording(HttpServletResponse response, Recording recording) throws IOException {
        int contentLength = getFileSize(recording);
        response.setHeader("Content-Length", String.valueOf(contentLength));
        try (FileInputStream fis = new FileInputStream(recording.file)) {
            fis.transferTo(response.getOutputStream());
        } catch (EOFException e) {
            // If the browser closes its connection, just return without error
            LOG.warning("Browser closed connection");
        }
    }

    private void writeRecording(HttpServletResponse response, Recording recording, int byteFrom, int byteTo) throws IOException {
        byte[] buffer = new byte[1024];

        // This may or may not match what was requested
        // I don't think it's possible to set the Content-Length header
        // and also to stream the data from disk
        int fileSize = getFileSize(recording);
        // Add 1 to byteTo because it's inclusive
        int contentLength = Math.min((byteTo + 1) - byteFrom, fileSize);
        response.setHeader("Content-Length", String.valueOf(contentLength));
        response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        response.setHeader("Content-Range", "bytes " + byteFrom + "-" + byteTo + "/" + fileSize);

        LOG.info("Getting range " + byteFrom + " to " + byteTo + " (" + contentLength + ")");

        OutputStream outputStream = response.getOutputStream();
        int totalBytesRead = 0;
        try (FileInputStream fis = new FileInputStream(recording.file)) {
            // Skip the initial bytes
            fis.skip(byteFrom);
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) > 0) {
                totalBytesRead += bytesRead;
                if (totalBytesRead > contentLength) {
                    outputStream.write(buffer, 0, totalBytesRead - contentLength);
                    break;
                }
                outputStream.write(buffer, 0, bytesRead);
            }
        } catch (EOFException e) {
            // If the browser closes its connection, just return without error
            LOG.warning("Browser closed connection after " + totalBytesRead +
                    " of " + contentLength + " bytes were transferred");
        }
    }

    private int getFileSize(Recording recording) throws IOException {
        long fileSize = Files.size(recording.file.toPath());
        if (fileSize > Integer.MAX_VALUE) {
            throw new AudioServerException("Audio file is too big!");
        }
        int fileSizeInt = (int) fileSize;
        return fileSizeInt;
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
