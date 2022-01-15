package fm.francoisefm;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AudioConverter {

    private static final Logger LOG = Logger.getLogger("AudioConverter");

    private final ExecutorService service;

    public AudioConverter() {
        service = Executors.newSingleThreadExecutor();
        try {
            LOG.addHandler(new FileHandler("logs/ffmpeg.log"));
            LOG.setUseParentHandlers(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void convertToOgg(File recording) {
        service.submit(() -> {
            try {
                String fileIn = recording.getAbsolutePath();
                int extensionIndex = fileIn.lastIndexOf(".");
                String fileNoExt = fileIn.substring(0, extensionIndex);
                convertToOgg(fileIn, fileNoExt + ".ogg", false);
                convertToOgg(fileIn, fileNoExt + "-lowpass.ogg", true);
            } catch(Exception e) {
                LOG.log(Level.SEVERE, "Error calling ffmpeg", e);
            }
        });
    }

    private void convertToOgg(String fileIn, String fileOut, boolean lowpass) throws IOException {
        String[] cmd;
        // pygame on the raspberry pi can only use one fixed sample rate rather than adapting
        // to the audio source. This means we have to encode all our audio with the same
        // sample rate. Later versions of pygame can adapt to the input but it's difficult
        // to update pygame on the raspberry pi
        if (lowpass) {
            cmd = new String[] {"ffmpeg", "-y", "-i", fileIn, "-ar", "44100", "-af", "lowpass=f=400", fileOut};
        } else {
            cmd = new String[] {"ffmpeg", "-y", "-i", fileIn, "-ar", "44100", fileOut};
        }

        Process process = Runtime.getRuntime().exec(cmd);

        // Probably not wise to try to consume all the std in before consuming the std err
        BufferedReader output = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = output.readLine()) != null) {
            LOG.info(line);
        }
        BufferedReader error = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        while ((line = error.readLine()) != null) {
            LOG.info(line);
        }
        // Wait up to 1 second for the process to exit
        for (int i = 0; i < 10; i++) {
            try {
                if (process.isAlive()) {
                    Thread.sleep(100);
                } else {
                    break;
                }
            } catch (InterruptedException e) {
                // ignore
            }
        }
        if (!process.isAlive()) {
            LOG.info("Process exited with code " + process.exitValue());
        } else {
            LOG.warning("Process did not exit for some reason");
        }
    }
}
