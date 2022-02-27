package fm.francoisefm;

import java.io.File;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FrequencyCalculator {

    private static final Logger LOG = Logger.getLogger("FrequencyCalculator");

    private static final Pattern filenamePattern = Pattern.compile("(.*)_([1-9][0-9][0-9][0-9]?)_([0-9][0-9])\\.\\w+$");

    private static final ConcurrentHashMap<String, Integer> userFrequencies = new ConcurrentHashMap<>();

    // Our frequency range is 87.0 to 107.0 which is equal to 200 different frequencies
    private static final int MAX_FREQUENCIES = 200;

    static {
        // Try to find all existing frequencies
        Path recordings = ServletHelper.RECORDINGS;

        LOG.info("Gathering initial frequencies");
        for (File userDir : recordings.toFile().listFiles()) {
            for (File recording : userDir.listFiles()) {
                Matcher matcher = filenamePattern.matcher(recording.getName());
                if (matcher.find()) {
                    String token = userDir.getName();
                    String name = matcher.group(1);
                    String frequencyStr = matcher.group(2);
                    LOG.info("Gathered " + token + " " + name + ": " + frequencyStr);
                    userFrequencies.put(token + "_" + name, Integer.parseInt(frequencyStr));
                }
            }
        }
        LOG.info("Finished gathering frequencies");
    }

    public static int calculate(UserId userId) {
        // Either find an existing user token (directory with files), or randomly generate a new one
        String frequencyKey = userId.token + "_" + userId.sanitisedName();
        Integer existingFrequency = userFrequencies.get(frequencyKey);
        if (existingFrequency != null) {
            return existingFrequency;
        }
        Random random = new Random();
        int randomFrequency = 990;
        for (int i = 0; i < MAX_FREQUENCIES; i++) {
            randomFrequency = random.nextInt(870, 1070);
            if (!userFrequencies.containsValue(randomFrequency)) {
                break;
            }
        }
        userFrequencies.put(frequencyKey, randomFrequency);
        return randomFrequency;
    }
}
