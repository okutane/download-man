package localdomain.localhost.downloader.core;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:dmitriy.matveev@odnoklassniki.ru">Dmitriy Matveev</a>
 */
public class DownloaderTest {
    Downloader downloader;

    @Before
    public  void setUp() throws Exception {
        File tmpDirectory = Files.createTempDirectory("downloads").toFile();
        tmpDirectory.deleteOnExit();
        downloader = new Downloader(tmpDirectory);
    }

    @Test
    public void test404() throws Exception {
        Download download = downloader.createDownload("https://v.ok.ru/contacts.html");

        downloader.startAll();
        downloader.waitAll();

        assertEquals(Download.State.Error, download.getState());
    }

    @Test
    public void testRefused() throws Exception {
        Download download = downloader.createDownload("http://127.0.0.1");

        downloader.startAll();
        downloader.waitAll();

        assertEquals(Download.State.Error, download.getState());
    }
}