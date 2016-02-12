package localdomain.localhost.downloader.core;

/**
 * The listener interface for receiving action events.
 *
 * @author <a href="mailto:dmitriy.matveev@odnoklassniki.ru">Dmitriy Matveev</a>
 */
public interface DownloaderEventHandler {
    /**
     * Invoked then {@link Download} state is changed.
     * @param download
     */
    default void downloadStateChanged(Download download) {
    }

    /**
     * Invoked then {@link Download} progress is changed.
     * @param download
     */
    default void progressChanged(Download download) {
    }
}
