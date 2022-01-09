package fm.francoisefm;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class AllRecordingsServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger("RecordingsServlet");
    private static final String USERNAME = "Melville";
    private static final Set<String> AUDIO_FILE_EXTENSIONS = Set.of("webm", "ogg", "mp4");

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
        basicAuth(request);
        PrintWriter writer = response.getWriter();

        writer.println("[");

        List<Path> allFiles = Files.walk(ServletHelper.RECORDINGS)
                .filter(Files::isRegularFile)
                .filter(p -> AUDIO_FILE_EXTENSIONS.contains(extension(p)))
                .collect(Collectors.toList());

        if (!allFiles.isEmpty()) {
            for (int i = 0; i < allFiles.size() - 1; i++) {
                printPath(writer, allFiles.get(i), true);
            }
            printPath(writer, allFiles.get(allFiles.size() - 1), false);
        }

        writer.println("]");

        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private String extension(Path p) {
        String fileName = p.toFile().getName();
        int dotPos = fileName.indexOf(".");
        if (dotPos == -1) {
            return "";
        }
        return fileName.substring(dotPos + 1);
    }

    private void printPath(PrintWriter writer, Path path, boolean printComma) {
        Path relativePath = ServletHelper.RECORDINGS.relativize(path);
        writer.print("\"");
        writer.print(relativePath);
        if (printComma) {
            writer.println("\",");
        } else {
            writer.println("\"");
        }
    }

    private void basicAuth(HttpServletRequest request) {
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader == null || !authorizationHeader.startsWith("Basic ")) {
            throw new AudioServerException("Unauthorised");
        }
        String basicAuthPass = ServletHelper.PROPERTIES.getProperty("BASIC_AUTH_PASS");
        byte[] expectedUsernameAndPassword = (USERNAME + ":" + basicAuthPass).getBytes(StandardCharsets.UTF_8);
        String encodedUsernameAndPassword = authorizationHeader.substring("Basic ".length());
        byte[] decodedUsernameAndPassword = Base64.getDecoder().decode(encodedUsernameAndPassword);

        if (!MessageDigest.isEqual(expectedUsernameAndPassword, decodedUsernameAndPassword)) {
            LOG.warning("Received incorrect basic auth password: " + new String(decodedUsernameAndPassword));
            throw new AudioServerException("Unauthorised basic auth attempt");
        }
    }

    private void validatePath(HttpServletRequest request) {
        String path = request.getPathInfo();
        if (path != null) {
            throw new AudioServerException("Invalid path: " + path);
        }
    }

}