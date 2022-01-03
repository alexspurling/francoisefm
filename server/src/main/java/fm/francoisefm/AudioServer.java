package fm.francoisefm;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import java.io.File;
import java.util.logging.Logger;

public class AudioServer {

    private static final Logger LOG = Logger.getLogger("AudioServer");

    public void start() throws Exception {

        long startTime = System.currentTimeMillis();
        int maxThreads = 100;
        int minThreads = 10;
        int idleTimeout = 120;

        QueuedThreadPool threadPool = new QueuedThreadPool(maxThreads, minThreads, idleTimeout);

        final Server server = new Server(threadPool);
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(9090);
        connector.getConnectionFactories().stream()
                .filter(connFactory -> connFactory instanceof HttpConnectionFactory)
                .forEach(hcf -> ((HttpConnectionFactory)hcf).getHttpConfiguration().setSendServerVersion(false));

        server.setConnectors(new Connector[] {connector});

        ServletHandler servletHandler = new ServletHandler();
        servletHandler.addServletWithMapping(RecordingsServlet.class, "/audio");
        servletHandler.addServletWithMapping(RecordingServlet.class, "/audio/*");
        server.setHandler(servletHandler);
        server.setErrorHandler(new CustomErrorHandler());

        server.start();

        long timeTaken = System.currentTimeMillis() - startTime;
        LOG.info("Server started in " + timeTaken + "ms");

        server.join();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: AudioServer <audio-dir>");
            System.exit(1);
        }

        ensureDirectoryExists(new File("logs"));
        ensureDirectoryExists(new File("recordings"));

        AudioServer server = new AudioServer();
        server.start();
    }

    private static void ensureDirectoryExists(File audioDirParam) {
        if (!audioDirParam.exists()) {
            LOG.severe("Directory does not exist: " + audioDirParam.getAbsolutePath());
            System.exit(1);
        }
        if (audioDirParam.isFile()) {
            LOG.severe("Not a directory: " + audioDirParam.getAbsolutePath());
            System.exit(1);
        }
    }
}