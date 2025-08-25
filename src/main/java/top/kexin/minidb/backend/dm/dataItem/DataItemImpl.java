package top.kexin.minidb.backend.dm.dataItem;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import top.kexin.minidb.backend.common.SubArray;
import top.kexin.minidb.backend.dm.DataManagerImpl;
import top.kexin.minidb.backend.dm.page.Page;


/**
 * DataItemImpl is the concrete implementation of the DataItem interface.
 * Each DataItemImpl holds:
 * - A reference to the raw byte array in the page (via SubArray)
 * - A backup copy (oldRaw) for rollback in case a transaction aborts
 * - Locks (read and write) to provide concurrency control
 * - The UID (unique identifier derived from page number and offset)
 * - A reference to the owning Page and DataManagerImpl
 * Transaction workflow with a DataItem:
 * 1. before(): acquire write lock, mark page dirty, back up original bytes
 * 2. unBefore(): restore backup bytes and release write lock (rollback)
 * 3. after(xid): log changes with DataManager and release write lock (commit)
 * This ensures atomicity and durability of updates at the DataItem level.
 *
 * @author KexinDai
 * @date 2025/8/24
 */
public class DataItemImpl implements DataItem {

    static final int OF_VALID = 0;
    static final int OF_SIZE = 1;
    static final int OF_DATA = 3;

    private SubArray raw;
    private byte[] oldRaw;
    private Lock rLock;
    private Lock wLock;
    private DataManagerImpl dm;
    private long uid;
    private Page pg;

    public DataItemImpl(SubArray raw, byte[] oldRaw, Page pg, long uid, DataManagerImpl dm) {
        this.raw = raw;
        this.oldRaw = oldRaw;
        ReadWriteLock lock = new ReentrantReadWriteLock();
        rLock = lock.readLock();
        wLock = lock.writeLock();
        this.dm = dm;
        this.uid = uid;
        this.pg = pg;
    }

    public boolean isValid() {
        return raw.raw[raw.start+OF_VALID] == (byte)0;
    }

    @Override
    public SubArray data() {
        return new SubArray(raw.raw, raw.start+OF_DATA, raw.end);
    }

    @Override
    public void before() {
        wLock.lock();
        pg.setDirty(true);
        System.arraycopy(raw.raw, raw.start, oldRaw, 0, oldRaw.length);
    }

    @Override
    public void unBefore() {
        System.arraycopy(oldRaw, 0, raw.raw, raw.start, oldRaw.length);
        wLock.unlock();
    }

    @Override
    public void after(long xid) {
        dm.logDataItem(xid, this);
        wLock.unlock();
    }

    @Override
    public void release() {
        dm.releaseDataItem(this);
    }

    @Override
    public void lock() {
        wLock.lock();
    }

    @Override
    public void unlock() {
        wLock.unlock();
    }

    @Override
    public void rLock() {
        rLock.lock();
    }

    @Override
    public void rUnLock() {
        rLock.unlock();
    }

    @Override
    public Page page() {
        return pg;
    }

    @Override
    public long getUid() {
        return uid;
    }

    @Override
    public byte[] getOldRaw() {
        return oldRaw;
    }

    @Override
    public SubArray getRaw() {
        return raw;
    }

}
