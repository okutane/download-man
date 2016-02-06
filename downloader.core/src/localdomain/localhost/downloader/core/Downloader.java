package localdomain.localhost.downloader.core;


import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:dmitriy.matveev@odnoklassniki.ru">Dmitriy Matveev</a>
 */
public class Downloader {
    private final File downloadDirectory;
    private final List<Download> downloads = new ArrayList<Download>();

    Thread scheduler = new Thread(new Runnable() {
        @Override
        public void run() {
            for (Download download : downloads) {
                HttpClient
                URL url = download.getUrl();
                try {
                    URLConnection connection = openConnection(url);
                    connection.connect();
                } catch (IOException e) {
                    download.setState(Download.State.Error);
                }
            }
        }
    });

    public Downloader(File downloadDirectory) {
        this.downloadDirectory = downloadDirectory;
    }

    protected URLConnection openConnection(URL url) throws IOException {
        return url.openConnection();
    }

    public Download createDownload(URL url) {
        Download download = new Download(url);

        downloads.add(download);

        return download;
    }

    public void startAll() {
        if (scheduler.getState() == Thread.State.NEW) {
            scheduler.run();
        }
    }

    public void waitAll() throws InterruptedException {
        scheduler.join();
    }
}
