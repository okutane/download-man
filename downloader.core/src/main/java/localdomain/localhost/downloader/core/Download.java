package localdomain.localhost.downloader.core;

import java.util.List;

/**
 * The primary entity in Downloader.
 *
 * The basic lifecycle for it is following:
 * - New {@link Download} is added to {@link Downloader} in {@link State#New} state.
 * - After HEAD request is performed for {@link #url} file name and size are determined and state is changed to {@link State#Ready}.
 * - Files in {@link State#Ready} are tried to be downloaded, so they then become {@link State#Finished} or {@link State#Error}
 *
 * @author <a href="mailto:dmitriy.matveev@odnoklassniki.ru">Dmitriy Matveev</a>
 */
public class Download {
    /**
     * Magic value for files with unknown size.
     */
    public static final int UNKNOWN_SIZE = -1;

    private final String url;
    private State state = State.New;
    private MultipartProgress progress;
    private String filename;

    Download(String url) {
        this.url = url;
    }

    /**
     * @return url to download file from.
     */
    public String getUrl() {
        return url;
    }

    /**
     * @return {@link State} in which current download is.
     */
    public State getState() {
        return state;
    }

    void setState(State state) {
        this.state = state;
    }

    void addProgress(long offset, long length) {
        progress.addProgress(offset, length);
    }

    /**
     * @return size of current download in bytes or {@link #UNKNOWN_SIZE} if unknown.
     */
    public long getSize() {
        return progress == null ? UNKNOWN_SIZE : progress.getSize();
    }

    /**
     * @return total number of bytes downloaded from remote address.
     */
    public long getAbsoluteCompletion() {
        return progress == null ? 0 : progress.getAbsoluteProgress();
    }

    /**
     * @return relative value of download progress on a scale from 0.0 to 1.0. 0.0 is also returned when size is unknown.
     */
    public double getCompletion() {
        return progress == null ? 0.0 : progress.getProgress();
    }

    /**
     * @return is current download completed.
     */
    public Boolean isComplete() {
        return progress == null ? null : progress.isComplete();
    }

    /**
     * @return list of parts which aren't downloaded yet.
     */
    public List<MultipartProgress.ProgressPart> getMissingParts() {
        return progress.getMissingParts();
    }

    void setSize(long size) {
        this.progress = new MultipartProgress(size);
    }

    /**
     * @return destination filename.
     */
    public String getFilename() {
        return filename;
    }

    void setFilename(String filename) {
        this.filename = filename;
    }

    public enum State {
        New,
        Ready,
        Finished,
        Error
    }
}
