package localdomain.localhost.downloader.ui;

import localdomain.localhost.downloader.core.Download;
import localdomain.localhost.downloader.core.Downloader;
import localdomain.localhost.downloader.core.DownloaderEventHandler;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;

/**
 * @author <a href="mailto:dmitriy.matveev@odnoklassniki.ru">Dmitriy Matveev</a>
 */
public class DownloadManager {

    public static class ProgressCellRender extends JProgressBar implements TableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            int progress = 0;
            if (value instanceof Double) {
                progress = (int)Math.round((Double) value * 100);
            } else if (value instanceof Float) {
                progress = Math.round(((Float) value) * 100f);
            } else if (value instanceof Integer) {
                progress = (int) value;
            }
            setValue(progress);
            return this;
        }
    }

    public static void main(String... args) {
        Downloader downloader = new Downloader(new File(System.getProperty("user.home"), "Downloads"));

        AbstractTableModel tableModel = new DownloadsTableModel(downloader);

        downloader.setHandler(new DownloaderEventHandler() {
            @Override
            public void downloadStateChanged(Download download) {
                SwingUtilities.invokeLater(tableModel::fireTableDataChanged);
            }

            @Override
            public void progressChanged(Download download) {
                // todo lots of invocations here, maybe add throttling?
                SwingUtilities.invokeLater(tableModel::fireTableDataChanged);
            }
        });

        JFrame frame = new JFrame("Download Manager");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        JTable table = new JTable(tableModel);
        table.getColumn("Progress").setCellRenderer(new ProgressCellRender());

        frame.add(new JScrollPane(table));

        JMenuBar menubar = new JMenuBar();

        JMenu file = new JMenu("File");
        file.setMnemonic(KeyEvent.VK_F);

        JMenuItem addMenuItem = new JMenuItem("Add download");
        addMenuItem.setMnemonic(KeyEvent.VK_A);
        addMenuItem.setToolTipText("Add download");
        addMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String url = JOptionPane.showInputDialog(frame, "URL to download from");
                downloader.createDownload(url);
                tableModel.fireTableDataChanged();
            }
        });

        JMenuItem startAllMenuItem = new JMenuItem("Start all");
        startAllMenuItem.addActionListener(e -> downloader.startAll());

        JMenuItem stopAllMenuItem = new JMenuItem("Stop all");
        stopAllMenuItem.addActionListener(e -> downloader.stopAll());

        JMenuItem exitMenuItem = new JMenuItem("Exit");
        exitMenuItem.setMnemonic(KeyEvent.VK_E);
        exitMenuItem.setToolTipText("Exit application");
        exitMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                downloader.stopAll();
                try {
                    downloader.waitAll();
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
                System.exit(0);
            }
        });

        file.add(addMenuItem);
        file.addSeparator();
        file.add(startAllMenuItem);
        file.add(stopAllMenuItem);
        file.addSeparator();
        file.add(exitMenuItem);

        menubar.add(file);

        frame.setJMenuBar(menubar);

        frame.pack();
        frame.setVisible(true);
    }

}
