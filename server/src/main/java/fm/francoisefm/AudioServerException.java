package fm.francoisefm;

public class AudioServerException extends RuntimeException {
    public AudioServerException(String msg) {
        super(msg);
    }

    public AudioServerException(String msg, Exception cause) {
        super(msg, cause);
    }
}
