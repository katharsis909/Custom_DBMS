package disk_persistence;

import STRUCTURE.DBMSException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class PageManager {
    private final File tableDir;

    private Page currentPage;
    private int currentPageId;

    public PageManager(String tableName) throws DBMSException {
        this.tableDir = new File("data", tableName);
        if (!tableDir.exists() && !tableDir.mkdirs()) {
            throw new DBMSException("Could not create table storage directory for '" + tableName + "'.");
        }

        this.currentPageId = getLastPageId();
        if (currentPageId == -1) {
            currentPageId = 0;
            currentPage = new Page(currentPageId);
        } else {
            currentPage = loadPageFromDisk(currentPageId);
        }
    }

    /**
     * Insert path: select the writable page, insert the row, flush it, and
     * return the physical page/offset reference for future index structures.
     */
    public synchronized RowPointer insertRow(byte[] row) throws DBMSException {
        if (!currentPage.hasSpace(row.length)) {
            flushCurrentPage();
            currentPageId++;
            currentPage = new Page(currentPageId);
        }

        int rowOffset = currentPage.insertRow(row);
        flushCurrentPage();
        return new RowPointer(currentPageId, rowOffset);
    }

    public synchronized Page loadPage(int pageId) throws DBMSException {
        if (currentPage != null && pageId == currentPageId) {
            return currentPage;
        }
        return loadPageFromDisk(pageId);
    }

    public synchronized int getCurrentPageId() {
        return currentPageId;
    }

    public synchronized Page getCurrentPage() {
        return currentPage;
    }

    public synchronized void flushCurrentPage() throws DBMSException {
        File file = pageFile(currentPageId);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(currentPage.getData());
        } catch (IOException e) {
            throw new DBMSException("Error writing page to disk: " + file.getPath(), e);
        }
    }

    private Page loadPageFromDisk(int pageId) throws DBMSException {
        File file = pageFile(pageId);
        if (!file.exists()) {
            throw new DBMSException("Page file not found: " + file.getPath());
        }

        byte[] data = new byte[Page.PAGE_SIZE];
        try (FileInputStream fis = new FileInputStream(file)) {
            int totalRead = 0;
            while (totalRead < Page.PAGE_SIZE) {
                int read = fis.read(data, totalRead, Page.PAGE_SIZE - totalRead);
                if (read == -1) {
                    break;
                }
                totalRead += read;
            }
            if (totalRead != Page.PAGE_SIZE) {
                throw new DBMSException("Corrupt page file: " + file.getPath());
            }
        } catch (IOException e) {
            throw new DBMSException("Error loading page from disk: " + file.getPath(), e);
        }

        Page page = new Page(pageId);
        page.loadData(data);
        return page;
    }

    private int getLastPageId() {
        File[] files = tableDir.listFiles((dir, name) -> name.startsWith("page_") && name.endsWith(".dat"));
        if (files == null || files.length == 0) {
            return -1;
        }

        int maxPageId = -1;
        for (File file : files) {
            String name = file.getName();
            try {
                int pageId = Integer.parseInt(name.substring(5, name.length() - 4));
                maxPageId = Math.max(maxPageId, pageId);
            } catch (NumberFormatException ignored) {
                // Ignore unrelated files inside the table directory.
            }
        }
        return maxPageId;
    }

    private File pageFile(int pageId) {
        return new File(tableDir, "page_" + pageId + ".dat");
    }
}
