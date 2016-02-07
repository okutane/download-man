package localdomain.localhost.downloader.core;

/**
 * The primary entity in Downloader.
 *
 * The basic lifecycle for it is following:
 * - New {@link Download} is added to {@link Downloader} is {@link State#New} state.
 * - HEAD request is performed for {@link #url} so file name and size will be determined.
 * -
 *
 * @author <a href="mailto:dmitriy.matveev@odnoklassniki.ru">Dmitriy Matveev</a>
 */
public class Download {
    private final String url;
    private State state = State.New;
    private long progress;
    private long size;
    private String filename;
    private String message;

    public Download(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public long getProgress() {
        return progress;
    }

    public void setProgress(long progress) {
        this.progress = progress;
    }

    public float getCompletion() {
        return size <= 0 ? 0 : (float)progress / size;
    }

    public long getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public enum State {
        New,
        Ready,
        Stopped,
        Waiting,
        Running,
        Finished,
        Error
    }
}
