package top.kexin.minidb.backend.tm;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import top.kexin.minidb.backend.utils.Panic;

import top.kexin.minidb.backend.utils.Parser;
import top.kexin.minidb.common.Error;
/**
 *
 * File-backed implementation that persists transaction metadata in a “.xid” file
 *
 * @author KexinDai
 * @date 2025/8/21
 */
public class TransactionManagerImpl {
    static final int LEN_XID_HEADER_LENGTH = 8;
    // transaction length
    private static final int XID_FIELD_SIZE = 1;

    //  3 states
    private static final byte FIELD_TRAN_ACTIVE   = 0;
    private static final byte FIELD_TRAN_COMMITTED = 1;
    private static final byte FIELD_TRAN_ABORTED  = 2;

    // super transaction, always committed
    public static final long SUPER_XID = 0;

    static final String XID_SUFFIX = ".xid";


    private RandomAccessFile file;
    private FileChannel fc;
    private long xidCounter;
    private Lock counterLock;

    TransactionManagerImpl(RandomAccessFile raf, FileChannel fc) {
        this.file = raf;
        this.fc = fc;
        counterLock = new ReentrantLock();
        checkXIDCounter();
    }

    /**
     * Validates the XID file.
     *
     * File layout:
     *   [0..7]   : 8-byte header storing xidCounter (the highest allocated XID)
     *   [8..]    : per-XID status bytes (size = XID_FIELD_SIZE, xid=1 at offset 8)
     *
     * Procedure:
     * 1) Read the physical file length L via RandomAccessFile.length().
     * 2) Read xidCounter from the 8-byte header at position 0.
     * 3) Compute the expected logical length:
     *        expected = LEN_XID_HEADER_LENGTH + xidCounter * XID_FIELD_SIZE
     *    (equivalently: expected = getXidPosition(xidCounter + 1)).
     * 4) If L < LEN_XID_HEADER_LENGTH or expected != L, the file is inconsistent
     *    (e.g., interrupted write or manual corruption) → panic with BadXIDFileException.
     */

    private void checkXIDCounter() {
        long fileLen = 0;
        try {
            fileLen = file.length();
        } catch (IOException e1) {
            Panic.panic(Error.BadXIDFileException);
        }
        if(fileLen < LEN_XID_HEADER_LENGTH) {
            Panic.panic(Error.BadXIDFileException);
        }

        ByteBuffer buf = ByteBuffer.allocate(LEN_XID_HEADER_LENGTH);
        try {
            fc.position(0);
            fc.read(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }
        this.xidCounter = Parser.parseLong(buf.array());
        long end = getXidPosition(this.xidCounter + 1);
        if(end != fileLen) {
            Panic.panic(Error.BadXIDFileException);
        }
    }

    private long getXidPosition(long xid) {
        return LEN_XID_HEADER_LENGTH + (xid-1)*XID_FIELD_SIZE;
    }

    // update xid transaction status
    private void updateXID(long xid, byte status) {
        long offset = getXidPosition(xid);
        byte[] tmp = new byte[XID_FIELD_SIZE];
        tmp[0] = status;
        ByteBuffer buf = ByteBuffer.wrap(tmp);
        try {
            fc.position(offset);
            fc.write(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }
        try {
            fc.force(false);
        } catch (IOException e) {
            Panic.panic(e);
        }
    }

    // add XID and update header
    private void incrXIDCounter() {
        xidCounter ++;
        ByteBuffer buf = ByteBuffer.wrap(Parser.long2Byte(xidCounter));
        try {
            fc.position(0);
            fc.write(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }
        try {
            fc.force(false);
        } catch (IOException e) {
            Panic.panic(e);
        }
    }

    // begin a transaction and return XID
    public long begin() {
        counterLock.lock();
        try {
            long xid = xidCounter + 1;
            updateXID(xid, FIELD_TRAN_ACTIVE);
            incrXIDCounter();
            return xid;
        } finally {
            counterLock.unlock();
        }
    }

    public void commit(long xid) {
        updateXID(xid, FIELD_TRAN_COMMITTED);
    }

    public void abort(long xid) {
        updateXID(xid, FIELD_TRAN_ABORTED);
    }

    private boolean checkXID(long xid, byte status) {
        long offset = getXidPosition(xid);
        ByteBuffer buf = ByteBuffer.wrap(new byte[XID_FIELD_SIZE]);
        try {
            fc.position(offset);
            fc.read(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }
        return buf.array()[0] == status;
    }

    public boolean isActive(long xid) {
        if(xid == SUPER_XID) return false;
        return checkXID(xid, FIELD_TRAN_ACTIVE);
    }

    public boolean isCommitted(long xid) {
        if(xid == SUPER_XID) return true;
        return checkXID(xid, FIELD_TRAN_COMMITTED);
    }

    public boolean isAborted(long xid) {
        if(xid == SUPER_XID) return false;
        return checkXID(xid, FIELD_TRAN_ABORTED);
    }

    public void close() {
        try {
            fc.close();
            file.close();
        } catch (IOException e) {
            Panic.panic(e);
        }
    }


}
