package top.kexin.minidb.backend.dm.dataItem;

import java.util.Arrays;

import com.google.common.primitives.Bytes;

import top.kexin.minidb.backend.common.SubArray;
import top.kexin.minidb.backend.dm.DataManagerImpl;
import top.kexin.minidb.backend.dm.page.Page;
import top.kexin.minidb.backend.utils.Parser;
import top.kexin.minidb.backend.utils.Types;

/**
 * DataItem represents the smallest unit of data stored in a page.
 *
 * A DataItem has the following physical layout:
 *   [ValidFlag][DataSize][Data]
 *   - ValidFlag (1 byte): 0 means valid, 1 means invalid (deleted or expired)
 *   - DataSize  (2 bytes): length of the Data field
 *   - Data      (N bytes): the actual user data
 *
 * Main responsibilities of a DataItem:
 * - Support transactional operations: before(), unBefore(), after(xid)
 * - Provide concurrency control with read/write locks
 * - Allow access to the raw data, old data copy, and UID (page number + offset)
 * - Allow release back to the DataManager
 *
 * Static helper methods are also provided:
 * - wrapDataItemRaw(byte[]): wrap raw user data into a DataItem format
 * - parseDataItem(Page, short, DataManagerImpl): parse a DataItem from a page at a given offset
 * - setDataItemRawInvalid(byte[]): mark a DataItem as invalid
 *
 * This interface is implemented by DataItemImpl.
 *
 * @author KexinDai
 * @date 2025/8/24
 */

public interface DataItem {
    SubArray data();

    void before();
    void unBefore();
    void after(long xid);
    void release();

    void lock();
    void unlock();
    void rLock();
    void rUnLock();

    Page page();
    long getUid();
    byte[] getOldRaw();
    SubArray getRaw();

    public static byte[] wrapDataItemRaw(byte[] raw) {
        byte[] valid = new byte[1];
        byte[] size = Parser.short2Byte((short)raw.length);
        return Bytes.concat(valid, size, raw);
    }

    // get dataitem based on offset and pg
    public static DataItem parseDataItem(Page pg, short offset, DataManagerImpl dm) {
        byte[] raw = pg.getData();
        short size = Parser.parseShort(Arrays.copyOfRange(raw, offset+DataItemImpl.OF_SIZE, offset+DataItemImpl.OF_DATA));
        short length = (short)(size + DataItemImpl.OF_DATA);
        long uid = Types.addressToUid(pg.getPageNumber(), offset);
        return new DataItemImpl(new SubArray(raw, offset, offset+length), new byte[length], pg, uid, dm);
    }

    public static void setDataItemRawInvalid(byte[] raw) {
        raw[DataItemImpl.OF_VALID] = (byte)1;
    }
}
