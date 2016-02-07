package localdomain.localhost.downloader.core;


import org.apache.commons.io.FileUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.HttpClients;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:dmitriy.matveev@odnoklassniki.ru">Dmitriy Matveev</a>
 */
public class Downloader {
    private final File downloadDirectory;
    private final HttpClient client;

    private final List<Download> downloads = new ArrayList<Download>();
    private final PriorityQueue<DownloadJob> priorityQueue = new PriorityQueue<>();

    private int bufferSize = 4096;
    private int threadsNumber = Runtime.getRuntime().availableProcessors();

    private DownloaderEventHandler handler = new DownloaderEventHandler() {
    };

    Thread scheduler = new Thread(new Runnable() {
        @Override
        public void run() {
            while (!priorityQueue.isEmpty()) {
                DownloadJob poll = priorityQueue.poll();
                poll.run();
            }
        }
    });

    private void setDownloadState(Download download, Download.State state) {
        download.setState(state);
        handler.downloadStateChanged(download);
    }

    private void addProgress(Download download, long offset, int length) {
        download.addProgress(offset, length);
        handler.progressChanged(download);
    }

    public Downloader(File downloadDirectory) {
        this(downloadDirectory, HttpClients.createDefault());
    }

    Downloader(File downloadDirectory, HttpClient httpClient) {
        this.downloadDirectory = downloadDirectory;
        this.client = httpClient;
    }

    public void setHandler(DownloaderEventHandler handler) {
        this.handler = handler;
    }

    public Download createDownload(String url) {
        Download download = new Download(url);

        downloads.add(download);

        return download;
    }

    public List<Download> getDownloads() {
        return downloads;
    }

    public void startAll() {
        if (scheduler.getState() == Thread.State.NEW) {
            for (Download download : downloads) {
                if (download.getState() == Download.State.New) {
                    priorityQueue.add(new DownloadJob(download, this::prepare));
                }
            }

            scheduler.start();
        }
    }

    private void prepare(Download download) {
        HttpHead request = new HttpHead(download.getUrl());
        try {
            HttpResponse response = client.execute(request);

            if (response.getStatusLine().getStatusCode() != 200) {
                setDownloadState(download, Download.State.Error);
                return;
            }

            String filename = evaluateFilename(request, response);

            Header contentLengthHeader = response.getFirstHeader("Content-Length");
            int contentLength = contentLengthHeader != null ? Integer.parseInt(contentLengthHeader.getValue()) : -1;

            synchronized (download) {
                String absolute = new File(downloadDirectory, filename).getAbsolutePath();
                download.setSize(contentLength);
                download.setFilename(absolute);

                if (contentLength > 0) {
                    preallocateFile(absolute, contentLength);
                }

                setDownloadState(download, Download.State.Ready);
                priorityQueue.add(new DownloadJob(download, this::download));
            }
        } catch (IOException e) {
            download.setMessage(e.getLocalizedMessage());
            setDownloadState(download, Download.State.Error);
        }
    }

    private void download(Download download) {
        HttpGet request = new HttpGet(download.getUrl());
        try {
            HttpResponse response = client.execute(request);

            HttpEntity entity = response.getEntity();
            // todo long contentLength = entity.getContentLength();
            InputStream content = entity.getContent();

            byte[] buffer = new byte[bufferSize];
            int offset = 0;
            int bc;
            do {
                bc = content.read(buffer);
                if (bc > 0) {
                    try (RandomAccessFile raf = new RandomAccessFile(download.getFilename(), "rw")) {
                        raf.seek(offset);
                        raf.write(buffer, 0, bc);
                        addProgress(download, offset, bc);
                        offset += bc;
                    }
                }
            } while (bc != -1);

            if (download.isComplete()) {
                setDownloadState(download, Download.State.Finished);
            }
        } catch (IOException e) {
            e.printStackTrace();
            download.setMessage(e.getMessage());
            setDownloadState(download, Download.State.Error);
        }
    }

    private void downloadPart(Download download, long from, Long to) {
        // stub
        HttpGet request = new HttpGet(download.getUrl());
        request.addHeader("Range", "bytes=" + from + '-' + (to == null ? "" : to.toString())); // expect 206
    }

    private void preallocateFile(String absolute, int contentLength) throws IOException {
        FileUtils.touch(new File(absolute));
        try (RandomAccessFile raf = new RandomAccessFile(absolute, "rw")) {
            raf.setLength(contentLength);
        }
    }

    private String evaluateFilename(HttpHead request, HttpResponse response) {
        File filename = new File(request.getURI().getPath());

        /** fixme Content-Disposition may offer a better name. */
        String name = filename.getName();

        /** fixme dirty hack, use apache tika */
        Header contentType = response.getFirstHeader("Content-Type");
        String[] expectedExtensions =
                (contentType != null && "image/jpeg".equals(contentType.getValue())) ? new String[] { "jpg", "jpeg" } : null;

        if (expectedExtensions != null && Arrays.stream(expectedExtensions).noneMatch(ext -> name.endsWith('.' + ext))) {
            return name + '.' + expectedExtensions[0];
        } else {
            return name;
        }
    }

    public void stopAll() {
    }

    public void waitAll() throws InterruptedException {
        scheduler.join();
    }

    private class DownloadJob implements Runnable, Comparable<DownloadJob> {
        private final Download download;
        private final Consumer<Download> job;

        private DownloadJob(Download download, Consumer<Download> job) {
            this.download = download;
            this.job = job;
        }

        @Override
        public int compareTo(DownloadJob o) {
            return Integer.compare(downloads.indexOf(download), downloads.indexOf(o.download));
        }

        @Override
        public void run() {
            job.accept(download);
        }
    }
}
