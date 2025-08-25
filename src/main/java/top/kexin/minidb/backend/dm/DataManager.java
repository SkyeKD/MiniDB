package top.kexin.minidb.backend.dm;

import top.kexin.minidb.backend.dm.dataItem.DataItem;
import top.kexin.minidb.backend.dm.logger.Logger;
import top.kexin.minidb.backend.dm.page.PageOne;
import top.kexin.minidb.backend.dm.pageCache.PageCache;
import top.kexin.minidb.backend.tm.TransactionManager;

/**
 * DataManager is the storage-engine facade for reading and writing data items.
 * Upper layers (e.g., record/table modules) use this interface instead of
 * directly dealing with pages, logs, and recovery.
 *
 * Responsibilities:
 * - Read a DataItem by its UID (page number + offset encoding).
 * - Insert a new DataItem under a transaction (WAL logging and page allocation).
 * - Manage lifecycle (flush and close underlying resources).
 *
 * Collaborators:
 * - PageCache: caches pages in memory, handles dirty tracking and flushing.
 * - Logger: write-ahead logging (redo/undo) for crash recovery.
 * - TransactionManager: tracks transaction states used in recovery.
 * - PageOne: special metadata/check page for clean/unclean shutdown detection.
 * - Recover: runs startup recovery if the previous shutdown was unclean.
 *
 * Typical usage:
 * - For a brand-new database, call DataManager.create(...).
 * - For an existing database, call DataManager.open(...).
 *
 * Thread-safety and concurrency control are handled inside the implementation
 * (DataManagerImpl), which coordinates page locks, item-level locks, and logging.
 *
 *
 * @author KexinDai
 * @date 2025/8/24
 */

public interface DataManager {
    DataItem read(long uid) throws Exception;
    long insert(long xid, byte[] data) throws Exception;
    void close();

    public static DataManager create(String path, long mem, TransactionManager tm) {
        PageCache pc = PageCache.create(path, mem);
        Logger lg = Logger.create(path);

        DataManagerImpl dm = new DataManagerImpl(pc, lg, tm);
        dm.initPageOne();
        return dm;
    }

    public static DataManager open(String path, long mem, TransactionManager tm) {
        PageCache pc = PageCache.open(path, mem);
        Logger lg = Logger.open(path);
        DataManagerImpl dm = new DataManagerImpl(pc, lg, tm);
        if(!dm.loadCheckPageOne()) {
            Recover.recover(tm, lg, pc);
        }
        dm.fillPageIndex();
        PageOne.setVcOpen(dm.pageOne);
        dm.pc.flushPage(dm.pageOne);

        return dm;
    }
}
