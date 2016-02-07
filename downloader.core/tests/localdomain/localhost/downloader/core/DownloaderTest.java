package localdomain.localhost.downloader.core;

import org.apache.commons.io.FileUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.message.BasicHeader;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author <a href="mailto:dmitriy.matveev@odnoklassniki.ru">Dmitriy Matveev</a>
 */
public class DownloaderTest {
    File tmpDirectory;

    @Before
    public  void setUp() throws Exception {
        tmpDirectory = Files.createTempDirectory("downloads").toFile();
        tmpDirectory.deleteOnExit();
    }

    @Test
    public void test404() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse response = mock(HttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);

        when(client.execute(Matchers.any())).thenReturn(response);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(404);

        Downloader downloader = new Downloader(tmpDirectory, client);
        Download download = downloader.createDownload("https://intellij-support.jetbrains.com/requests/21262");

        downloader.startAll();
        downloader.waitAll();

        assertEquals(Download.State.Error, download.getState());
    }

    @Test
    public void testRefused() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.execute(Matchers.any())).thenThrow(HttpHostConnectException.class);

        Downloader downloader = new Downloader(tmpDirectory, client);
        Download download = downloader.createDownload("http://127.0.0.1");

        downloader.startAll();
        downloader.waitAll();

        assertEquals(Download.State.Error, download.getState());
    }

    @Test
    public void testOk() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse headResponse = mock(HttpResponse.class);
        HttpResponse getResponse = mock(HttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        HttpEntity entity = mock(HttpEntity.class);

        String content = "Hello, Mr. Matveev. We're fixed this bug";

        when(client.execute(Matchers.any())).thenReturn(headResponse, getResponse);
        when(headResponse.getStatusLine()).thenReturn(statusLine);
        when(headResponse.getFirstHeader("Content-Length"))
                .thenReturn(new BasicHeader("Content-Length", String.valueOf(content.getBytes().length)));
        when(getResponse.getEntity()).thenReturn(entity);
        when(entity.getContent()).thenReturn(new ByteArrayInputStream(content.getBytes()));
        when(statusLine.getStatusCode()).thenReturn(200);

        Downloader downloader = new Downloader(tmpDirectory, client);
        Download download = downloader.createDownload("https://intellij-support.jetbrains.com/requests/21262");

        downloader.startAll();
        downloader.waitAll();

        assertEquals(Download.State.Finished, download.getState());
        assertArrayEquals(content.getBytes(), FileUtils.readFileToByteArray(new File(tmpDirectory, "21262")));
    }

    @Test
    public void testMultipart() throws Exception {
        byte[] data = new byte[15 * 1024 * 1024];
        ThreadLocalRandom.current().nextBytes(data);

        HttpResponse headResponse = mock(HttpResponse.class);
        HttpResponse getResponse = mock(HttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        HttpEntity entity = mock(HttpEntity.class);

        when(headResponse.getStatusLine()).thenReturn(statusLine);
        when(headResponse.getFirstHeader("Content-Length"))
                .thenReturn(new BasicHeader("Content-Length", String.valueOf(data.length)));
        when(getResponse.getEntity()).thenReturn(entity);
        when(entity.getContent()).thenReturn(new ByteArrayInputStream(data));
        when(statusLine.getStatusCode()).thenReturn(200);

        CyclicBarrier cb = new CyclicBarrier(2);
        HttpClient client = new TestHttpClient() {
            @Override
            public HttpResponse execute(HttpUriRequest request) throws IOException {
                if (request.getMethod().equals("HEAD")) {
                    return headResponse;
                }
                if (request.getMethod().equals("GET")) {
                    try {
                        cb.await(2000, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (BrokenBarrierException | TimeoutException e) {
                        fail(e.getMessage());
                    }

                    Header range = request.getFirstHeader("Range");
                    if (range != null) {
                        HttpResponse getPartResponse = mock(HttpResponse.class);
                        return getPartResponse;
                    }

                    return getResponse;
                }
                return super.execute(request);
            }
        };

        Downloader downloader = new Downloader(tmpDirectory, client);
        Download download = downloader.createDownload("http://random.org/bytes.dat");

        downloader.startAll();
        downloader.waitAll();

        assertEquals(Download.State.Finished, download.getState());
        assertArrayEquals(data, FileUtils.readFileToByteArray(new File(tmpDirectory, "bytes.dat")));
    }

}