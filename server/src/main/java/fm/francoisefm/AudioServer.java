package fm.francoisefm;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import static fm.francoisefm.ServletHelper.CONVERTED;
import static fm.francoisefm.ServletHelper.RECORDINGS;

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
        servletHandler.addServletWithMapping(AllRecordingsServlet.class, "/audio");
        servletHandler.addServletWithMapping(RecordingServlet.class, "/audio/*");
        servletHandler.addServletWithMapping(AllStationsServlet.class, "/audio/radio");
        servletHandler.addServletWithMapping(RadioServlet.class, "/audio/radio/*");
        server.setHandler(servletHandler);
        server.setErrorHandler(new CustomErrorHandler());

        server.start();

        long timeTaken = System.currentTimeMillis() - startTime;
        LOG.info("Server started in " + timeTaken + "ms");

        server.join();
    }

    private static void configureLogger() {
        InputStream stream = AudioServer.class.getClassLoader().getResourceAsStream("logging.properties");
        if (stream == null) {
            LOG.warning("No configuration found for logging. Using default config.");
        } else {
            try {
                LogManager.getLogManager().readConfiguration(stream);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void ensureDirectoryExists(File dir) {
        if (dir.exists() && dir.isFile()) {
            LOG.severe("Not a directory: " + dir.getAbsolutePath());
            System.exit(1);
        }
        dir.mkdir();
        if (!dir.exists()) {
            LOG.severe("Failed to create directory: " + dir.getAbsolutePath());
            System.exit(1);
        }
    }

    public static void main(String[] args) throws Exception {
        configureLogger();
        ensureDirectoryExists(new File("logs"));
        ensureDirectoryExists(RECORDINGS.toFile());
        ensureDirectoryExists(CONVERTED.toFile());

        AudioServer server = new AudioServer();
        server.start();
    }
}