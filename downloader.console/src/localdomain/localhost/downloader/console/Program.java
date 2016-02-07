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
    public static void main(String... args) throws InterruptedException, IOException {
        File downloads = Files.createTempDirectory("downloads").toFile();
        downloads.deleteOnExit();

        Downloader downloader = new Downloader(downloads);
        downloader.createDownload("http://ok.ru/ad");
        downloader.createDownload("http://uld9.mycdn.me/image?t=3&bid=812548898691&id=812548879235&plc=WEB&tkn=*edFRwGNDLmQj3R_tWOCvYCa1jXQ");
        downloader.createDownload("https://download.jetbrains.com/idea/ideaIU-15.0.3-custom-jdk-bundled.dmg");
        downloader.setHandler(new DownloaderEventHandler() {
            @Override
            public void downloadStateChanged(Download download) {
                System.out.println(download.getState() + ": " + download.getUrl());
                if (download.getState() == Download.State.Error) {
                    System.out.println(download.getMessage());
                }
            }

            @Override
            public void progressChanged(Download download) {
                System.out.println(download.getCompletion() + ": " + download.getUrl());
            }
        });
        downloader.startAll();
        downloader.waitAll();
    }
}
