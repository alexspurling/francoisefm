package fm.francoisefm;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServletHelper {

    private static final Logger LOG = Logger.getLogger("ServletHelper");

    public static final String UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    private static final Pattern AUTHORIZATION_HEADER = Pattern.compile("^Bearer ([A-Za-z0-9+/=]+)$");
    private static final Pattern USER_ID = Pattern.compile("^(.+)(" + UUID_PATTERN + ")$");
    public static final Path RECORDINGS = Path.of("recordings");
    public static final Path CONVERTED = Path.of("converted");
    public static final Properties PROPERTIES = readProperties();

    public static final AudioConverter AUDIO_CONVERTER = new AudioConverter();

    private static Properties readProperties() {
        Properties prop = new Properties();
        File propertiesFile = new File("server.properties");
        if (!propertiesFile.exists()) {
            propertiesFile = new File("server/server.properties");
        }
        try {
            prop.load(new FileInputStream(propertiesFile));
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Could not load properties file", e);
        }
        return prop;
    }

    public static void validateQueryString(HttpServletRequest request) {
        if (request.getQueryString() != null) {
            throw new AudioServerException("Invalid query string: " + request.getPathInfo() + request.getQueryString());
        }
    }

    public static void setAllowHeaders(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        if (origin != null && (origin.startsWith("http://localhost") || origin.startsWith("https://francoise.fm"))) {
            response.addHeader("Access-Control-Allow-Origin", origin);
        }
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, DELETE");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.addHeader("Access-Control-Allow-Credentials", "true");
        response.addHeader("Vary", "Origin");
        response.addHeader("Access-Control-Expose-Headers", "Location");
    }

    public static UserId getUserId(HttpServletRequest request) {
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader == null) {
            throw new AudioServerException("No Authorization header");
        }
        Matcher authHeaderMatcher = AUTHORIZATION_HEADER.matcher(authorizationHeader);
        if (!authHeaderMatcher.matches()) {
            throw new AudioServerException("Invalid userId: " + authorizationHeader);
        }
        String decodedUserId;
        try {
            decodedUserId = new String(Base64.getDecoder().decode(authHeaderMatcher.group(1)), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new AudioServerException("Bearer token is not base64 encoded: " + authorizationHeader);
        }
        Matcher userIdMatcher = USER_ID.matcher(decodedUserId);
        if (!userIdMatcher.matches()) {
            throw new AudioServerException("Invalid userId: " + decodedUserId);
        }
        return new UserId(userIdMatcher.group(1), userIdMatcher.group(2));
    }

    public static String getRequestURL(HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return request.getRequestURL() + "&" + request.getQueryString();
        } else {
            return request.getRequestURL().toString();
        }
    }

    public static File getUserDir(UserId userId) {
        File userDir = RECORDINGS.resolve(userId.token).toFile();
        if (!userDir.exists()) {
            if (!userDir.mkdir()) {
                throw new AudioServerException("Could not create user dir: " + userDir.getAbsolutePath());
            }
        }
        return userDir;
    }
}
