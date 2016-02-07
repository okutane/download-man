package localdomain.localhost.downloader.core;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.message.BasicHeader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;

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
    }
}