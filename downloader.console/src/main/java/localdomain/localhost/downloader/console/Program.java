package localdomain.localhost.downloader.console;

import localdomain.localhost.downloader.core.Download;
import localdomain.localhost.downloader.core.Downloader;
import localdomain.localhost.downloader.core.DownloaderEventHandler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author <a href="mailto:dmitriy.matveev@odnoklassniki.ru">Dmitriy Matveev</a>
 */
public class Program {
    public static void main(String... args) throws Exception {
        File downloads = Files.createTempDirectory("downloads").toFile();
        downloads.deleteOnExit();

        Downloader downloader = new Downloader(downloads);
        downloader.createDownload("http://cs631216.vk.me/v631216220/7687/rmjro0_sLR0.jpg");
        downloader.createDownload("http://ok.ru/ad");
        downloader.createDownload("http://uld9.mycdn.me/image?t=3&bid=812548898691&id=812548879235&plc=WEB&tkn=*edFRwGNDLmQj3R_tWOCvYCa1jXQ");
        downloader.createDownload("https://download.jetbrains.com/idea/ideaIU-15.0.3-custom-jdk-bundled.dmg");
        downloader.startAll();
        downloader.waitAll();
    }
}
