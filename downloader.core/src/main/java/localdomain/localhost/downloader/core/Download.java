package localdomain.localhost.downloader.core;

import java.util.concurrent.atomic.AtomicInteger;

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
    private MultipartProgress progress;
    private String filename;
    private String message;

    private AtomicInteger errorCount = new AtomicInteger(0);
    private AtomicInteger runnersCount = new AtomicInteger(0);

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

    public void addProgress(long offset, long length) {
        progress.addProgress(offset, length);
    }

    public long getAbsoluteCompletion() {
        return progress == null ? 0 : progress.getAbsoluteProgress();
    }

    public double getCompletion() {
        return progress == null ? 0.0 : progress.getProgress();
    }

    public Boolean isComplete() {
        return progress == null ? null : progress.isComplete();
    }

    public void setSize(int size) {
        this.progress = new MultipartProgress(size);
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

    public AtomicInteger getErrorCount() {
        return errorCount;
    }

    public AtomicInteger getRunnersCount() {
        return runnersCount;
    }

    public enum State {
        New,
        Preparing,
        Ready,
        Stopped,
        Waiting,
        Running,
        Finished,
        Error
    }
}
