package localdomain.localhost.downloader.core;


import org.apache.commons.io.FileUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.BasicHttpContext;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:dmitriy.matveev@odnoklassniki.ru">Dmitriy Matveev</a>
 */
public class Downloader {
    public static final int UNKNOWN_SIZE = -1;
    private final File downloadDirectory;
    private final HttpClient client;

    private final List<Download> downloads = new ArrayList<>();

    private int bufferSize = 4096 * 10;
    private long minPartSize = 10 * 1024 * 1024;
    private int threadsNumber = Runtime.getRuntime().availableProcessors();

    private DownloaderEventHandler handler = new DownloaderEventHandler() {
    };

    ForkJoinPool pool = new ForkJoinPool(threadsNumber);

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
        for (Download download : downloads) {
            switch (download.getState()) {
                case New:
                    pool.execute(new DownloadJob(download));
            }
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
            int contentLength = contentLengthHeader != null ? Integer.parseInt(contentLengthHeader.getValue()) : UNKNOWN_SIZE;

            synchronized (download) {
                String absolute = new File(downloadDirectory, filename).getAbsolutePath();
                download.setSize(contentLength);
                download.setFilename(absolute);

                if (contentLength > 0) {
                    preallocateFile(absolute, contentLength);
                }

                setDownloadState(download, Download.State.Ready);
            }
        } catch (IOException e) {
            download.setMessage(e.getLocalizedMessage());
            setDownloadState(download, Download.State.Error);
        }
    }

    private void download(Download download) throws IOException {

        HttpGet request = new HttpGet(download.getUrl());

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
    }

    private void downloadPart(Download download, long from, Long to) throws IOException {
        HttpGet request = new HttpGet(download.getUrl());
        request.addHeader("Range", "bytes=" + from + '-' + (to == null ? "" : to.toString())); // expect 206

        CloseableHttpResponse response = (CloseableHttpResponse) client.execute(request, new BasicHttpContext());
        try {
            HttpEntity entity = response.getEntity();
            // todo long contentLength = entity.getContentLength();
            InputStream content = entity.getContent();

            byte[] buffer = new byte[bufferSize];
            long offset = from;
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
        } finally {
            response.close();
        }
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
        pool.shutdown();
        while (!pool.awaitTermination(500, TimeUnit.MILLISECONDS)) {
        }
    }

    private class DownloadJob extends RecursiveAction {
        private final Download download;

        private DownloadJob(Download download) {
            this.download = download;
        }

        @Override
        protected void compute() {
            prepare(download);

            if (download.getState() == Download.State.Error) {
                return;
            }

            if (download.getSize() == UNKNOWN_SIZE) {
                // size is unknown, download sequentially.
                try {
                    download(download);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return;
            }

            invokeAll(new DownloadPartJob(0, download.getSize()));
        }

        private class DownloadPartJob extends RecursiveAction {
            private final long from;
            private final long to;

            public DownloadPartJob(long from, long to) {
                this.from = from;
                this.to = to;
            }

            @Override
            protected void compute() {
                if (to - from <= minPartSize) {
                    try {
                        downloadPart(download, from, to);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return;
                }

                long mid = (from + to) >>> 1;
                invokeAll(new DownloadPartJob(from, mid), new DownloadPartJob(mid, to));
            }
        }
    }
}
