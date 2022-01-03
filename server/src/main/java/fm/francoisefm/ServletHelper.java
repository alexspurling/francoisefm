package fm.francoisefm;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServletHelper {

    public static final String UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    private static final Pattern USER_ID = Pattern.compile("^Bearer (.+)(" + UUID_PATTERN + ")$");
    private static final String RECORDINGS = "recordings";

    public static void validateQueryString(HttpServletRequest request) {
        if (request.getQueryString() != null) {
            throw new AudioServerException("Invalid query string: " + request.getPathInfo() + request.getQueryString());
        }
    }

    public static void setAllowHeaders(HttpServletRequest request, HttpServletResponse response) {
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

    public static UserId getUserId(HttpServletRequest request) {
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

    public static String getRequestURL(HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return request.getRequestURL() + "&" + request.getQueryString();
        } else {
            return request.getRequestURL().toString();
        }
    }

    public static File getUserDir(UserId userId) {
        File userDir = new File(RECORDINGS, userId.token);
        if (!userDir.exists()) {
            if (!userDir.mkdir()) {
                throw new AudioServerException("Could not create user dir: " + userDir.getAbsolutePath());
            }
        }
        return userDir;
    }

    public static File getRecordingFile(Recording recording) {
        return new File(new File(RECORDINGS, recording.token), recording.fileName);
    }
}
