package fm.francoisefm;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AllRecordingsServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger("AllRecordingsServlet");

    private static final Pattern AUDIO_CONTAINER = Pattern.compile("^audio/(\\w+)", Pattern.CASE_INSENSITIVE);

    private static final int MAX_FILES_PER_USER = 100;
    private static final int MAX_FILE_SIZE = 2097152; // 2mb max file size gives about 5 minutes recording

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
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

        UserId userId = ServletHelper.getUserId(request);
        LOG.info("User id: " + userId);

        File userDir = ServletHelper.getUserDir(userId);
        if (!userDir.exists()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        File[] userFiles = userDir.listFiles();
        if (userFiles == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String sanitisedUserName = userId.name.replaceAll("[\\\\./]", "_");
        // Make sure we only look for files that match the logged-in user's name
        List<File> ownUserFiles = Arrays.stream(userFiles)
                .sorted(Comparator.comparingLong(File::lastModified))
                .filter((f) -> f.getName().matches("^" + Pattern.quote(sanitisedUserName) + "[0-9][0-9].\\w+$"))
                .collect(Collectors.toList());

        if (ownUserFiles.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // Important to set the content type before getting the PrintWriter
        // so that it correctly infers the string encoding as utf-8
        response.setContentType("application/json");
        PrintWriter writer = response.getWriter();

        writer.println("[");

        // Awkward iteration through all but the last file
        for (int i = 0; i < ownUserFiles.size() - 1; i++) {
            writer.println("  \"/audio/" + userId.token + "/" + ownUserFiles.get(i).getName() + "\",");
        }
        // So that the last file does not include a comma
        writer.println("  \"/audio/" + userId.token + "/" + ownUserFiles.get(ownUserFiles.size() - 1).getName() + "\"");

        writer.println("]");

        response.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        LOG.info("OPTIONS: " + ServletHelper.getRequestURL(request));

        ServletHelper.setAllowHeaders(request, response);

        response.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        long startTime = System.currentTimeMillis();
        LOG.info("POST: " + ServletHelper.getRequestURL(request));

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
        ServletHelper.validateQueryString(request);

        ServletHelper.setAllowHeaders(request, response);

        UserId userId = ServletHelper.getUserId(request);
        String contentType = request.getHeader("Content-Type");

        LOG.info("UserId: " + userId);
        LOG.info("Content type: " + contentType);
        LOG.info("Content length: " + request.getHeader("Content-Length"));

        String fileExtension = getFileExtension(contentType);
        File audioFile = getNewAudioFile(userId, fileExtension);

        writeRequestStream(request.getInputStream(), audioFile);

        LOG.info("Converting file to ogg: " + audioFile + " (file exists: " + audioFile.exists() + ")");
        ServletHelper.AUDIO_CONVERTER.convertToOgg(audioFile);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setHeader("Location", "/audio/" + userId.token + "/" + urlEncode(audioFile.getName()));
    }

    private String urlEncode(String name) {
        return URLEncoder.encode(name, StandardCharsets.UTF_8);
    }

    private String getFileExtension(String contentType) {
        if (contentType != null) {
            Matcher matcher = AUDIO_CONTAINER.matcher(contentType);
            if (matcher.find()) {
                return "." + matcher.group(1);
            }
        }
        LOG.warning("Unrecognised contentType. Defaulting to .ogg.");
        return ".ogg";
    }

    private void writeRequestStream(ServletInputStream inputStream, File audioFile) throws IOException {
        byte[] buffer = new byte[10000];

        LOG.info("Writing to " + audioFile.getAbsolutePath());

        int bytesWritten = 0;
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(audioFile))) {
            while (!inputStream.isFinished()) {
                int bytesRead = inputStream.read(buffer);
                if (bytesRead == -1) {
                    break;
                }
                bos.write(buffer, 0, bytesRead);
                bytesWritten += bytesRead;
                if (bytesWritten >= MAX_FILE_SIZE) {
                    LOG.warning("Tried to write more than maximum allowed file size");
                    break;
                }
            }
        }
        LOG.info("Finished writing " + bytesWritten + " bytes to " + audioFile.getAbsolutePath());
    }

    private void validatePath(HttpServletRequest request) {
        String path = request.getPathInfo();
        if (path != null) {
            throw new AudioServerException("Invalid path: " + path);
        }
    }

    private File getNewAudioFile(UserId userId, String fileExtension) {
        File userDir = ServletHelper.getUserDir(userId);
        String sanitisedUserName = userId.name.replaceAll("[\\\\./]", "_");
        for (int i = 1; i < MAX_FILES_PER_USER; i++) {
            File audioFile = new File(userDir, String.format(sanitisedUserName + "%02d", i) + fileExtension);
            if (!audioFile.exists()) {
                return audioFile;
            }
        }
        throw new AudioServerException("User has run out of available files: " + userId);
    }
}