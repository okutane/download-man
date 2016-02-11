package localdomain.localhost.downloader.ui;

import localdomain.localhost.downloader.core.Download;
import localdomain.localhost.downloader.core.DownloadCreationException;
import localdomain.localhost.downloader.core.Downloader;
import localdomain.localhost.downloader.core.DownloaderEventHandler;

import javax.swing.*;
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
    private static Downloader downloader = new Downloader(new File(System.getProperty("user.home"), "Downloads"));

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

        downloader.startAll(); // download queue is empty, but it's more convenient.

        JFrame frame = new JFrame("Download Manager");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        JTable table = new JTable(tableModel);
        table.getColumn("Progress").setCellRenderer(new ProgressCellRender());

        frame.add(new JScrollPane(table));

        JMenuBar menubar = createMenu(downloader, tableModel, frame);

        frame.setJMenuBar(menubar);

        frame.pack();
        frame.setVisible(true);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                downloader.stopAll();
            }
        });
    }

    private static JMenuBar createMenu(final Downloader downloader, final AbstractTableModel tableModel, final JFrame frame) {
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
                try {
                    downloader.createDownload(url.trim());
                    tableModel.fireTableDataChanged();
                } catch (DownloadCreationException e1) {
                    JOptionPane.showMessageDialog(frame, "Bad URL");
                }
            }
        });

        JMenuItem startAllMenuItem = new JMenuItem("Start all");
        startAllMenuItem.addActionListener(e -> downloader.startAll());

        JMenuItem stopAllMenuItem = new JMenuItem("Stop all");
        stopAllMenuItem.addActionListener(e -> downloader.stopAll());

        JMenuItem exitMenuItem = new JMenuItem("Exit");
        exitMenuItem.setMnemonic(KeyEvent.VK_E);
        exitMenuItem.setToolTipText("Exit application");
        exitMenuItem.addActionListener(e -> System.exit(0));

        file.add(addMenuItem);
        file.addSeparator();
        file.add(startAllMenuItem);
        file.add(stopAllMenuItem);
        file.addSeparator();
        file.add(exitMenuItem);

        menubar.add(file);

        JMenu performance = new JMenu("Performance");

        JMenu threads = new JMenu("Threads");
        ButtonGroup group = new ButtonGroup();
        int defaultThreads = Runtime.getRuntime().availableProcessors();
        int maxThreads = Runtime.getRuntime().availableProcessors() * 2;
        for (int i = 1; i <= maxThreads; i++) {
            final int threadsNumber = i;
            JRadioButtonMenuItem threadsOption = new JRadioButtonMenuItem(Integer.toString(i), i == defaultThreads);
            threadsOption.addActionListener(e -> downloader.setThreadsNumber(threadsNumber));
            group.add(threadsOption);
            threads.add(threadsOption);
        }
        performance.add(threads);

        menubar.add(performance);

        return menubar;
    }

}
