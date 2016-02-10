package localdomain.localhost.downloader.ui;

import localdomain.localhost.downloader.core.Download;
import localdomain.localhost.downloader.core.Downloader;

import javax.swing.table.AbstractTableModel;

/**
 * @author <a href="mailto:dmitriy.matveev@odnoklassniki.ru">Dmitriy Matveev</a>
 */
class DownloadsTableModel extends AbstractTableModel {
    private final Downloader downloader;

    public DownloadsTableModel(Downloader downloader) {
        this.downloader = downloader;
    }

    @Override
    public int getRowCount() {
        return downloader.getDownloads().size();
    }

    @Override
    public int getColumnCount() {
        return 3;
    }

    @Override
    public String getColumnName(int column) {
        switch (column) {
            case 0:
                return "Url";
            case 1:
                return "State";
            case 2:
                return "Progress";
        }
        return null;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Download download = downloader.getDownloads().get(rowIndex);
        switch (columnIndex) {
            case 0:
                return download.getUrl();
            case 1:
                return download.getState();
            case 2:
                return download.getCompletion();
        }
        return null;
    }
}
