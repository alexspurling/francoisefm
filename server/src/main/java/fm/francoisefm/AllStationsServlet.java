package fm.francoisefm;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class AllStationsServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger("AllStationsServlet");
    private static final String USERNAME = "Melville";
    private static final Set<String> AUDIO_FILE_EXTENSIONS = Set.of("ogg");

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

        // Important to set the content type before getting the PrintWriter
        // so that it correctly infers the string encoding as utf-8
        response.setContentType("application/json");
        PrintWriter writer = response.getWriter();

        writer.println("[");

        List<Station> allStations = StationsDb.getStations();

        for (int j = 0; j < allStations.size(); j++) {
            var station = allStations.get(j);
            writer.println("  {");
            writer.println("    \"name\": \"" + station.name() + "\",");
            writer.println("    \"token\": \"" + station.token() + "\",");
            writer.println("    \"frequency\": " + station.frequency() + ",");
            writer.println("    \"files\": [");

            String sanitisedFileName = new UserId(station.name(), station.token()).sanitisedName();

            Path convertedStationFolder = ServletHelper.CONVERTED.resolve(station.token());

            if (Files.exists(convertedStationFolder)) {
                List<Path> allFiles = Files.walk(convertedStationFolder, 1)
                        .filter(Files::isRegularFile)
                        .filter(p -> AUDIO_FILE_EXTENSIONS.contains(extension(p)))
                        .filter((p) -> p.getFileName().toString().matches("^" + Pattern.quote(sanitisedFileName) + "_([0-9][0-9])(-lowpass)?\\.ogg"))
                        .sorted(Comparator.comparingLong(p -> p.toFile().lastModified()))
                        .toList();

                if (!allFiles.isEmpty()) {
                    for (int i = 0; i < allFiles.size() - 1; i++) {
                        printPath(writer, allFiles.get(i), true);
                    }
                    printPath(writer, allFiles.get(allFiles.size() - 1), false);
                }
            }

            writer.println("    ]");
            if (j < allStations.size() - 1) {
                writer.println("  },");
            } else {
                writer.println("  }");
            }
        }

        writer.println("]");

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
        String hash = calcualteHash(path);
        Path relativePath = ServletHelper.CONVERTED.relativize(path);
        writer.print("      {\"path\": \"");
        writer.print(relativePath);
        writer.print("\", \"hash\": \"");
        writer.print(hash);
        if (printComma) {
            writer.println("\"},");
        } else {
            writer.println("\"}");
        }
    }

    private String calcualteHash(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");

            try (FileInputStream fis = new FileInputStream(path.toFile())) {
                byte[] byteArray = new byte[1024];
                int bytesCount;
                while ((bytesCount = fis.read(byteArray)) != -1) {
                    digest.update(byteArray, 0, bytesCount);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e);
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