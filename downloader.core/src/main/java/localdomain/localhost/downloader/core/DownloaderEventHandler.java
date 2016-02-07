package localdomain.localhost.downloader.core;

/**
 * @author <a href="mailto:dmitriy.matveev@odnoklassniki.ru">Dmitriy Matveev</a>
 */
public interface DownloaderEventHandler {
    default void downloadStateChanged(Download download) {
    }

    default void progressChanged(Download download) {
    }
}
