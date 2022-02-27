package fm.francoisefm;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
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

    private static File convertPathTo(File recording, String newExtension) {
        Path filePath = ServletHelper.RECORDINGS.relativize(recording.toPath());
        Path convertedFilePath = ServletHelper.CONVERTED.resolve(filePath);

        int extensionIndex = recording.getName().lastIndexOf(".");
        String fileNoExt = recording.getName().substring(0, extensionIndex);

        String newFileName = fileNoExt + newExtension;
        return convertedFilePath.getParent().resolve(newFileName).toFile();
    }

    public static File convertPathToOgg(File recording) {
        return convertPathTo(recording, ".ogg");
    }

    public static File convertPathToOggLowpass(File recording) {
        return convertPathTo(recording, "-lowpass.ogg");
    }

    public void convertToOgg(File recording) {
        service.submit(() -> {
            try {
                convertToOgg(recording.getAbsoluteFile(), convertPathToOgg(recording), false);
                convertToOgg(recording.getAbsoluteFile(), convertPathToOggLowpass(recording), true);
            } catch(Exception e) {
                LOG.log(Level.SEVERE, "Error calling ffmpeg", e);
            }
        });
    }

    private void convertToOgg(File fileIn, File fileOut, boolean lowpass) throws IOException {
        String[] cmd;
        // pygame on the raspberry pi can only use one fixed sample rate rather than adapting
        // to the audio source. This means we have to encode all our audio with the same
        // sample rate. Later versions of pygame can adapt to the input but it's difficult
        // to update pygame on the raspberry pi
        String inputFile = windowsToWSLPath(fileIn);
        String outputFile = windowsToWSLPath(fileOut);

        if (System.getProperty("os.name").startsWith("Windows")) {
            if (lowpass) {
                cmd = new String[]{"wsl", "ffmpeg", "-y", "-i", inputFile, "-ar", "44100", "-af", "lowpass=f=400", outputFile};
            } else {
                cmd = new String[]{"wsl", "ffmpeg", "-y", "-i", inputFile, "-ar", "44100", outputFile};
            }
        } else {
            if (lowpass) {
                cmd = new String[]{"ffmpeg", "-y", "-i", inputFile, "-ar", "44100", "-af", "lowpass=f=400", outputFile};
            } else {
                cmd = new String[]{"ffmpeg", "-y", "-i", inputFile, "-ar", "44100", outputFile};
            }
        }

        // Make sure the converted directory exists
        if (!fileOut.getParentFile().exists()) {
            fileOut.getParentFile().mkdirs();
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

    private String windowsToWSLPath(File file) {
        return file.getAbsolutePath().replaceAll("\\\\", "/").replaceAll("^C:", "/mnt/c");
    }
}
