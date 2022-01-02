package fm.francoisefm;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.ErrorHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Logger;

public class CustomErrorHandler extends ErrorHandler {

    private static final Logger LOG = Logger.getLogger("CustomErrorHandler");

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
        LOG.warning("Handling request error: " + target + ", " + baseRequest);

        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

}
