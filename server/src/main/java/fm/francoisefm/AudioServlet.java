package fm.francoisefm;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AudioServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger("AudioServlet");

    private static final Pattern USER_ID = Pattern.compile("^Bearer (.+)([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$");

    private static final int MAX_FILES_PER_USER = 100;
    private static final int MAX_FILE_SIZE = 1000000; // 1mb max file size

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        long startTime = System.currentTimeMillis();
        LOG.info("GET " + getRequestURL(request));

        setAllowHeaders(request, response);

        response.setStatus(HttpServletResponse.SC_OK);

        long timeTaken = System.currentTimeMillis() - startTime;
        LOG.info("GET (" + timeTaken + "ms): " + response.getStatus());
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        LOG.info("OPTIONS: " + getRequestURL(request));

        setAllowHeaders(request, response);

        response.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        long startTime = System.currentTimeMillis();
        LOG.info("POST: " + getRequestURL(request));

        try {
            handlePost(request, response);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error handling POST", e);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
        long timeTaken = System.currentTimeMillis() - startTime;
        LOG.info("POST (" + timeTaken + "ms): " + response.getStatus());
    }

    private void handlePost(HttpServletRequest request, HttpServletResponse response) throws IOException {

        validatePath(request);
        validateQueryString(request);

        setAllowHeaders(request, response);

        UserId userId = getUserId(request);

        String contentType = request.getHeader("Content-Type");

        LOG.info("UserId: " + userId);
        LOG.info("Content type: " + contentType);
        LOG.info("Content length: " + request.getHeader("Content-Length"));

        String audioContainer = getAudioContainer(contentType);
        File audioFile = getNewAudioFile(userId, audioContainer);

        writeRequestStream(request.getInputStream(), audioFile);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setHeader("Location", "/audio/" + userId.token + "/" + audioFile.getName());
    }

    private String getAudioContainer(String contentType) {
        if (contentType.startsWith("audio/webm")) {
            return "webm";
        } else if (contentType.startsWith("audio/ogg")) {
            return "ogg";
        }
        return null;
    }

    private void writeRequestStream(ServletInputStream inputStream, File audioFile) throws IOException {
        byte[] buffer = new byte[10000];

        LOG.info("Writing to " + audioFile.getAbsolutePath());

        int bytesWritten = 0;
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(audioFile))) {
            while (!inputStream.isFinished()) {
                int bytesRead = inputStream.read(buffer);
                if (bytesRead < 0) {
                    return;
                }
                bos.write(buffer, 0, bytesRead);
                bytesWritten += bytesRead;
                if (bytesWritten >= MAX_FILE_SIZE) {
                    LOG.warning("Tried to write more than maximum allowed file size");
                    break;
                }
            }
        }
        LOG.info("Finished writing " + bytesWritten + " to " + audioFile.getAbsolutePath());
    }

    private String getRequestURL(HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return request.getRequestURL() + "&" + request.getQueryString();
        } else {
            return request.getRequestURL().toString();
        }
    }

    private void validateQueryString(HttpServletRequest request) {
        // We always expect POST requests to be made to /audio with no query string
        if (request.getQueryString() != null) {
            throw new AudioServerException("Invalid query string: " + request.getPathInfo() + request.getQueryString());
        }
    }

    private void validatePath(HttpServletRequest request) {
        // We always expect POST requests to be made to /audio and nothing else (no trailing / or any other params)
        String path = request.getPathInfo();
        if (request.getPathInfo() != null) {
            throw new AudioServerException("Invalid path for POST: " + path);
        }
    }

    private File getNewAudioFile(UserId userId, String audioContainer) {
        File audioDir = AudioServer.getAudioDir();
        File userDir = new File(audioDir, userId.token);
        if (!userDir.exists()) {
            if (!userDir.mkdir()) {
                throw new AudioServerException("Could not create user dir: " + userDir.getAbsolutePath());
            }
        }
        String fileExtension = audioContainer != null ? audioContainer : "ogg";
        for (int i = 0; i < MAX_FILES_PER_USER; i++) {
            File audioFile = new File(userDir, userId.name + i + "." + fileExtension);
            if (!audioFile.exists()) {
                return audioFile;
            }
        }
        throw new AudioServerException("User has run out of available files: " + userId);
    }

    private void setAllowHeaders(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        String allowedOrigin = null;
        if (origin != null && (origin.startsWith("http://localhost") || origin.startsWith("https://francoise.fm"))) {
            allowedOrigin = origin;
        }
        if (allowedOrigin != null) {
            response.addHeader("Access-Control-Allow-Origin", allowedOrigin);
        }
        response.addHeader("Access-Control-Allow-Methods", "GET, POST");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.addHeader("Access-Control-Allow-Credentials", "true");
        response.addHeader("Vary", "Origin");
        response.addHeader("Access-Control-Expose-Headers", "Location");
    }

    private UserId getUserId(HttpServletRequest request) {
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader == null) {
            throw new AudioServerException("No userId");
        }
        Matcher matcher = USER_ID.matcher(authorizationHeader);
        if (matcher.matches()) {
            return new UserId(matcher.group(1), matcher.group(2));
        }
        throw new AudioServerException("Invalid userId: " + authorizationHeader);
    }
}