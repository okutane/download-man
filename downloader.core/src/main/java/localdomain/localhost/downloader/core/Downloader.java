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
import org.apache.http.impl.execchain.RequestAbortedException;
import org.apache.http.protocol.BasicHttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
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
    private static Logger logger = LoggerFactory.getLogger(Downloader.class);
    private final File downloadDirectory;
    private final HttpClient client;

    private final List<Download> downloads = new ArrayList<>();

    private boolean running = false;
    private int maxRetryCount = 5;
    private int bufferSize = 4096 * 10;
    private long minPartSize = 10 * 1024 * 1024;
    private int threadsNumber;

    private DownloaderEventHandler handler = new DownloaderEventHandler() {
    };

    ForkJoinPool pool;

    public Downloader(File downloadDirectory) {
        this(downloadDirectory, HttpClients.createDefault());
    }

    Downloader(File downloadDirectory, HttpClient httpClient) {
        this.downloadDirectory = downloadDirectory;
        this.client = httpClient;
        setThreadsNumber(Runtime.getRuntime().availableProcessors());
    }

    public void setThreadsNumber(int threadsNumber) {
        if (pool != null) {
            if (threadsNumber == this.threadsNumber) {
                return;
            }
            pool.shutdownNow();
        }
        pool = new ForkJoinPool(threadsNumber);
        restartAll();

        this.threadsNumber = threadsNumber;
    }

    private void setDownloadState(Download download, Download.State state) {
        if (logger.isInfoEnabled()) {
            logger.info(download.getUrl() + " -> " + state);
        }
        download.setState(state);
        handler.downloadStateChanged(download);
    }

    private void addProgress(Download download, long offset, int length) {
        download.addProgress(offset, length);
        handler.progressChanged(download);
    }

    public void setHandler(DownloaderEventHandler handler) {
        this.handler = handler;
    }

    public Download createDownload(String url) throws DownloadCreationException {
        // validation
        try {
            URI uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new DownloadCreationException(e);
        }

        Download download = new Download(url);

        downloads.add(download);

        if (running) {
            pool.execute(new DownloadJob(download));
        }

        return download;
    }

    public List<Download> getDownloads() {
        return downloads;
    }

    public void restartAll() {
        if (!running) {
            return;
        }
        downloads.stream().filter(d -> d.getState() == Download.State.Ready || d.getState() == Download.State.New).forEach(d -> pool.execute(new DownloadJob(d)));
    }

    public void startAll() {
        if (running) {
            downloads.stream().filter(d -> d.getState() == Download.State.Error).forEach(d -> pool.execute(new DownloadJob(d)));
        } else {
            downloads.stream().filter(d -> d.getState() != Download.State.Finished).forEach(d -> pool.execute(new DownloadJob(d)));
        }
        running = true;
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
            logger.warn(download.getUrl(), e);
            download.setMessage(e.getLocalizedMessage());
            setDownloadState(download, Download.State.Error);
        }
    }

    private void download(Download download) throws DownloadFailedException {
        int tryCount = 0;
        while (true) {
            try {
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
            } catch (RequestAbortedException ignored) {
                // probably pool resize
                return;
            } catch (IOException e) {
                if (tryCount++ < maxRetryCount) {
                    continue;
                }
                throw new DownloadFailedException(e);
            }
        }
    }

    private void downloadPart(Download download, long from, Long to) throws DownloadFailedException {
        int tryCount = 0;
        while (true) {
            try {
                HttpGet request = new HttpGet(download.getUrl());
                request.addHeader("Range", "bytes=" + from + '-' + (to == null ? "" : to.toString()));

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
                    return;
                } finally {
                    response.close();
                }
            } catch (RequestAbortedException ignored) {
                // probably pool resize
                return;
            } catch (IOException e) {
                if (tryCount++ < maxRetryCount) {
                    continue;
                }
                throw new DownloadFailedException(e);
            }
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
        pool.shutdownNow();
        pool = new ForkJoinPool(threadsNumber);
        running = false;
    }

    public void waitAll() throws InterruptedException {
        pool.shutdown();
        while (!pool.awaitTermination(500, TimeUnit.MILLISECONDS)) {
        }
        running = false;
    }

    private class DownloadJob extends RecursiveAction {
        private final Download download;

        private DownloadJob(Download download) {
            this.download = download;
        }

        @Override
        protected void compute() {
            try {
                if (download.getState() == Download.State.Ready && download.getSize() != UNKNOWN_SIZE) {
                    List<DownloadPartJob> jobs = new ArrayList<>();
                    for (MultipartProgress.ProgressPart missingPart : download.getMissingParts()) {
                        jobs.add(new DownloadPartJob(missingPart.getFrom(), missingPart.getTo()));
                    }
                    invokeAll(jobs.toArray(new DownloadPartJob[0]));
                    return;
                }

                prepare(download);

                if (download.getState() == Download.State.Error) {
                    return;
                }

                if (download.getSize() == UNKNOWN_SIZE) {
                    // size is unknown, download sequentially.
                    try {
                        download(download);
                    } catch (DownloadFailedException e) {
                        logger.warn(download.getUrl(), e);
                        setDownloadState(download, Download.State.Error);
                    }
                    return;
                }

                invokeAll(new DownloadPartJob(0, download.getSize()));
            } finally {
                if (download.isComplete()) {
                    setDownloadState(download, Download.State.Finished);
                }
            }
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
                        if (logger.isInfoEnabled()) {
                            logger.info(download.getUrl() + ": " + from + "-" + to + " downloaded.");
                        }
                    } catch (DownloadFailedException e) {
                        logger.warn(download.getUrl(), e);
                        setDownloadState(download, Download.State.Error);
                    }
                    return;
                }

                long mid = (from + to) >>> 1;
                invokeAll(new DownloadPartJob(from, mid), new DownloadPartJob(mid, to));
            }
        }
    }
}
