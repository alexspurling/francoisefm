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

    public void convertToMp3(File recording) {
        service.submit(() -> {
            try {
                String fileIn = recording.getAbsolutePath();
                int extensionIndex = fileIn.lastIndexOf(".");
                String fileNoExt = fileIn.substring(0, extensionIndex);
                convertToMp3(fileIn, fileNoExt + ".mp3", false);
                convertToMp3(fileIn, fileNoExt + "-lowpass.mp3", true);
            } catch(Exception e) {
                LOG.log(Level.SEVERE, "Error calling ffmpeg", e);
            }
        });
    }

    private void convertToMp3(String fileIn, String fileOut, boolean lowpass) throws IOException {
        String cmd;
        if (lowpass) {
            cmd = "ffmpeg -y -i " + fileIn + " -b:a 128k -af lowpass=f=400 " + fileOut;
        } else {
            cmd = "ffmpeg -y -i " + fileIn + " -b:a 128k " + fileOut;
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
        LOG.info("Process exited with code " + process.exitValue());
    }
}
