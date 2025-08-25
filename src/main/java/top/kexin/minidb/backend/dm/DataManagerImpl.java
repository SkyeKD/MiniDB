package top.kexin.minidb.backend.dm;

import top.kexin.minidb.backend.common.AbstractCache;
import top.kexin.minidb.backend.dm.dataItem.DataItem;
import top.kexin.minidb.backend.dm.dataItem.DataItemImpl;
import top.kexin.minidb.backend.dm.logger.Logger;
import top.kexin.minidb.backend.dm.page.Page;
import top.kexin.minidb.backend.dm.page.PageOne;
import top.kexin.minidb.backend.dm.page.PageX;
import top.kexin.minidb.backend.dm.pageCache.PageCache;
import top.kexin.minidb.backend.dm.pageIndex.PageIndex;
import top.kexin.minidb.backend.dm.pageIndex.PageInfo;
import top.kexin.minidb.backend.tm.TransactionManager;
import top.kexin.minidb.backend.utils.Panic;
import top.kexin.minidb.backend.utils.Types;
import top.kexin.minidb.common.Error;


/**
 * DataManagerImpl is the concrete storage-engine implementation behind the DataManager
 * interface. It coordinates the page cache, write-ahead logging, transactional
 * item updates, and an in-memory free-space index to support efficient reads and writes.
 *
 * Key responsibilities:
 * - Load and parse DataItems from pages on demand (with caching via AbstractCache).
 * - Insert new DataItems into pages, selecting a page with enough free space,
 *   logging the operation before applying changes (WAL), and returning a UID.
 * - Maintain and rebuild a PageIndex (free-space map) across pages to accelerate inserts.
 * - Manage the check/metadata page (PageOne) at startup/shutdown to detect clean
 *   vs. unclean shutdowns, enabling recovery runs when needed.
 *
 * Collaboration:
 * - PageCache (pc): fetches, pins, and flushes pages; can allocate new pages.
 * - Logger (logger): append-only WAL; used by Recover to redo/undo on crash.
 * - TransactionManager (tm): tracks transaction states used during recovery.
 * - PageX: per-page layout helpers, including free-space tracking and item inserts.
 * - PageIndex: in-memory free-space index to select a page for inserts.
 * - Recover: builds WAL records (insert/update) and drives recovery at open.
 * - AbstractCache<DataItem>: base class providing ref-counted caching for DataItems.
 *
 * UID layout (as used in getForCache):
 * - High 32 bits: page number
 * - Low 16 bits: offset within the page (byte offset of the DataItem header)
 *
 * Lifecycle:
 * - initPageOne(): called for brand-new DBs to create and flush the metadata page.
 * - loadCheckPageOne(): called when opening existing DBs; validates PageOne state.
 * - fillPageIndex(): rebuilds the in-memory free-space index from all data pages.
 * - close(): marks PageOne closed, releases caches/logs, and closes the page cache.
 *
 * @author KexinDai
 * @date 2025/8/24
 */


public class DataManagerImpl extends AbstractCache<DataItem> implements DataManager {

    TransactionManager tm;
    PageCache pc;
    Logger logger;
    PageIndex pIndex;
    Page pageOne;

    public DataManagerImpl(PageCache pc, Logger logger, TransactionManager tm) {
        super(0);
        this.pc = pc;
        this.logger = logger;
        this.tm = tm;
        this.pIndex = new PageIndex();
    }

    @Override
    public DataItem read(long uid) throws Exception {
        DataItemImpl di = (DataItemImpl)super.get(uid);
        if(!di.isValid()) {
            di.release();
            return null;
        }
        return di;
    }

    @Override
    public long insert(long xid, byte[] data) throws Exception {
        byte[] raw = DataItem.wrapDataItemRaw(data);
        if(raw.length > PageX.MAX_FREE_SPACE) {
            throw Error.DataTooLargeException;
        }

        PageInfo pi = null;
        for(int i = 0; i < 5; i ++) {
            pi = pIndex.select(raw.length);
            if (pi != null) {
                break;
            } else {
                int newPgno = pc.newPage(PageX.initRaw());
                pIndex.add(newPgno, PageX.MAX_FREE_SPACE);
            }
        }
        if(pi == null) {
            throw Error.DatabaseBusyException;
        }

        Page pg = null;
        int freeSpace = 0;
        try {
            pg = pc.getPage(pi.pgno);
            byte[] log = Recover.insertLog(xid, pg, raw);
            logger.log(log);

            short offset = PageX.insert(pg, raw);

            pg.release();
            return Types.addressToUid(pi.pgno, offset);

        } finally {
            // 将取出的pg重新插入pIndex
            if(pg != null) {
                pIndex.add(pi.pgno, PageX.getFreeSpace(pg));
            } else {
                pIndex.add(pi.pgno, freeSpace);
            }
        }
    }

    @Override
    public void close() {
        super.close();
        logger.close();

        PageOne.setVcClose(pageOne);
        pageOne.release();
        pc.close();
    }

    // 为xid生成update日志
    public void logDataItem(long xid, DataItem di) {
        byte[] log = Recover.updateLog(xid, di);
        logger.log(log);
    }

    public void releaseDataItem(DataItem di) {
        super.release(di.getUid());
    }

    @Override
    protected DataItem getForCache(long uid) throws Exception {
        short offset = (short)(uid & ((1L << 16) - 1));
        uid >>>= 32;
        int pgno = (int)(uid & ((1L << 32) - 1));
        Page pg = pc.getPage(pgno);
        return DataItem.parseDataItem(pg, offset, this);
    }

    @Override
    protected void releaseForCache(DataItem di) {
        di.page().release();
    }

    // 在创建文件时初始化PageOne
    void initPageOne() {
        int pgno = pc.newPage(PageOne.InitRaw());
        assert pgno == 1;
        try {
            pageOne = pc.getPage(pgno);
        } catch (Exception e) {
            Panic.panic(e);
        }
        pc.flushPage(pageOne);
    }

    // 在打开已有文件时时读入PageOne，并验证正确性
    boolean loadCheckPageOne() {
        try {
            pageOne = pc.getPage(1);
        } catch (Exception e) {
            Panic.panic(e);
        }
        return PageOne.checkVc(pageOne);
    }

    // 初始化pageIndex
    void fillPageIndex() {
        int pageNumber = pc.getPageNumber();
        for(int i = 2; i <= pageNumber; i ++) {
            Page pg = null;
            try {
                pg = pc.getPage(i);
            } catch (Exception e) {
                Panic.panic(e);
            }
            pIndex.add(pg.getPageNumber(), PageX.getFreeSpace(pg));
            pg.release();
        }
    }

}
