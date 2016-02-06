package localdomain.localhost.downloader.core;


import org.apache.http.client.HttpClient;

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
    private DownloaderEventHandler handler = new DownloaderEventHandler() {
    };

    Thread scheduler = new Thread(new Runnable() {
        @Override
        public void run() {
            for (Download download : downloads) {
                URL url = download.getUrl();
                try {
                    HttpClient client =
                    URLConnection connection = openConnection(url);
                    connection.connect();
                    setDownloadState(download, Download.State.Running);
                } catch (IOException e) {
                    setDownloadState(download, Download.State.Error);
                }
            }
        }
    });

    private void setDownloadState(Download download, Download.State state) {
        download.setState(state);
        handler.downloadStateChanged(download);
    }

    public Downloader(File downloadDirectory) {
        this.downloadDirectory = downloadDirectory;
    }

    protected URLConnection openConnection(URL url) throws IOException {
        return url.openConnection();
    }

    public void setHandler(DownloaderEventHandler handler) {
        this.handler = handler;
    }

    public Download createDownload(URL url) {
        Download download = new Download(url);

        downloads.add(download);

        return download;
    }

    public List<Download> getDownloads() {
        return downloads;
    }

    public void startAll() {
        if (scheduler.getState() == Thread.State.NEW) {
            scheduler.run();
        }
    }

    public void stopAll() {
    }

    public void waitAll() throws InterruptedException {
        scheduler.join();
    }
}
