package localdomain.localhost.downloader.core;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:dmitriy.matveev@odnoklassniki.ru">Dmitriy Matveev</a>
 */
public class MultipartProgressTest {
    @Test
    public void testNew() {
        MultipartProgress progress = new MultipartProgress(200);
        assertEquals(0.0, progress.getProgress(), 0.01);
    }

    @Test
    public void testPartial() {
        MultipartProgress progress = new MultipartProgress(200);
        progress.addProgress(0, 50);
        assertEquals(0.25, progress.getProgress(), 0.01);
    }

    @Test
    public void testOverlapping() {
        MultipartProgress progress = new MultipartProgress(200);
        progress.addProgress(50, 100);
        progress.addProgress(0, 100);
        assertEquals(0.75, progress.getProgress(), 0.01);
    }

    @Test
    public void testComplete() {
        MultipartProgress progress = new MultipartProgress(200);
        progress.addProgress(100, 100);
        progress.addProgress(0, 100);
        assertEquals(1.0, progress.getProgress(), 0.01);
    }

    @Test
    public void testAlreadyRecordedProgress() {
        MultipartProgress progress = new MultipartProgress(200);
        progress.addProgress(50, 100);
        progress.addProgress(75, 50);
        assertEquals(0.5, progress.getProgress(), 0.01);
    }

    @Test
    public void testBiggerProgress() {
        MultipartProgress progress = new MultipartProgress(200);
        progress.addProgress(75, 50);
        progress.addProgress(50, 100);
        assertEquals(0.5, progress.getProgress(), 0.01);
    }

    @Test
    public void testPrecision() {
        MultipartProgress progress = new MultipartProgress(2);
        progress.addProgress(1, 1);
        assertEquals(0.5, progress.getProgress(), 0.01);
        progress.addProgress(0, 1);
        assertEquals(1.0, progress.getProgress(), 0.01);
    }
}
