package fm.francoisefm;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

public class AudioServer {

    public void start() throws Exception {

        int maxThreads = 100;
        int minThreads = 10;
        int idleTimeout = 120;

        QueuedThreadPool threadPool = new QueuedThreadPool(maxThreads, minThreads, idleTimeout);

        final Server server = new Server(threadPool);
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(7625);
        server.setConnectors(new Connector[] {connector});

        ServletHandler servletHandler = new ServletHandler();

        servletHandler.addServletWithMapping(AudioServlet.class, "/*");
        server.setHandler(servletHandler);

        server.start();
        server.join();
    }

    public static void main(String[] args) throws Exception {
        AudioServer server = new AudioServer();
        server.start();
    }
}