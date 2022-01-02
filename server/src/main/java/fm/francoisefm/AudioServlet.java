package fm.francoisefm;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AudioServlet extends HttpServlet {

    private static final Pattern BEARER = Pattern.compile("^Bearer (\\w+)$");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        System.out.println("ok got");
        response.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        System.out.println("Got options request");
        response.addHeader("Access-Control-Allow-Origin", "http://localhost:63342");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.addHeader("Access-Control-Allow-Credentials", "true");
        response.addHeader("Vary", "Origin");


        response.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Make sure a user id was given

        System.out.println("Got post request");

        response.addHeader("Access-Control-Allow-Origin", "http://localhost:63342");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.addHeader("Access-Control-Allow-Credentials", "true");
        response.addHeader("Vary", "Origin");

        String userToken = getUserToken(request);
        if (userToken == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        System.out.println("Got request for user token" + userToken);
        System.out.println("Content type: " + request.getHeader("Content-Type"));
        System.out.println("Content length: " + request.getHeader("Content-Length"));

        File targetFile = new File("audio.ogg");
        Files.copy(request.getInputStream(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().println("{ \"status\": \"ok\"}");
    }

    private String getUserToken(HttpServletRequest request) {
        String tokenHeader = request.getHeader("Authorization");
        if (tokenHeader == null) {
            return null;
        }
        Matcher matcher = BEARER.matcher(tokenHeader);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }
}