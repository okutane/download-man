package localdomain.localhost.downloader.core;

import java.net.URL;

/**
 * @author <a href="mailto:dmitriy.matveev@odnoklassniki.ru">Dmitriy Matveev</a>
 */
public class Download {
    private final URL url;
    private State state = State.Stopped;
    private long progress;
    private long size;

    public Download(URL url) {
        this.url = url;
    }

    public URL getUrl() {
        return url;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public float getProgress() {
        return size == 0 ? 0 : (float)progress / size;
    }

    public enum State {
        Stopped,
        Waiting,
        Running,
        Finished,
        Error
    }
}
