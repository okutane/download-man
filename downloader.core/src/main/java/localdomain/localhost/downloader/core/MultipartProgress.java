package localdomain.localhost.downloader.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

/**
 * @author <a href="mailto:dmitriy.matveev@odnoklassniki.ru">Dmitriy Matveev</a>
 */
public class MultipartProgress {
    private final TreeSet<ProgressPart> parts = new TreeSet<>();
    private final long size;

    public MultipartProgress(long size) {
        this.size = size;
    }

    public long getSize() {
        return size;
    }

    public synchronized long getAbsoluteProgress() {
        return parts.stream().mapToLong(p -> p.to - p.from).sum();
    }

    public synchronized double getProgress() {
        long absoluteProgress = getAbsoluteProgress();
        return absoluteProgress == 0 ? 0.0 : absoluteProgress / (double)size;
    }

    public synchronized boolean isComplete() {
        return parts.size() == 1 && parts.first().from == 0 && parts.first().to == size;
    }

    public synchronized void addProgress(long offset, long length) {
        long from = offset;
        long to = offset + length;

        Iterator<ProgressPart> iterator = parts.iterator();
        while (iterator.hasNext()) {
            ProgressPart part = iterator.next();

            if (from <= part.from && to >= part.to) {
                // overwriting existing progress
                iterator.remove();
            } else if (from >= part.from && to <= part.to) {
                // useless progress
                return;
            } else if (from <= part.from && to >= part.from) {
                // partial overwrite or merge
                to = part.to;
                iterator.remove();
            } else if (part.from <= from && part.to >= from) {
                // partial overwrite or merge
                from = part.from;
                iterator.remove();
            }
        }

        // adding new or merged part
        parts.add(new ProgressPart(from, to));
    }

    public synchronized List<ProgressPart> getMissingParts() {
        List<ProgressPart> result = new ArrayList<>();

        long from = 0;
        for (ProgressPart part : parts) {
            result.add(new ProgressPart(from, part.from));
            from = part.to;
        }

        if (from != size) {
            result.add(new ProgressPart(from, size));
        }

        return result;
    }

    public static class ProgressPart implements Comparable<ProgressPart> {
        private final long from;
        private final long to;

        public ProgressPart(long from, long to) {
            this.from = from;
            this.to = to;
        }

        public long getFrom() {
            return from;
        }

        public long getTo() {
            return to;
        }

        @Override
        public int compareTo(ProgressPart o) {
            return Long.compare(from, o.from);
        }

        @Override
        public String toString() {
            return "[" + from + ", " + to + ')';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ProgressPart that = (ProgressPart) o;

            if (from != that.from) return false;
            return to == that.to;

        }

        @Override
        public int hashCode() {
            int result = (int) (from ^ (from >>> 32));
            result = 31 * result + (int) (to ^ (to >>> 32));
            return result;
        }
    }
}
