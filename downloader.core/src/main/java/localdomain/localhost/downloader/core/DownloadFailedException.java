package localdomain.localhost.downloader.core;

import java.io.IOException;

/**
 * @author <a href="mailto:dmitriy.matveev@odnoklassniki.ru">Dmitriy Matveev</a>
 */
public class DownloadFailedException extends Exception {
    public DownloadFailedException(Exception e) {
        super(e);
    }
}
