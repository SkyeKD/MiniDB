package top.kexin.minidb.backend.tm;

/**
 * test
 *
 * @author KexinDai
 * @date 2025/8/21
 */

public class MockTransactionManager implements TransactionManager {

    @Override
    public long begin() {
        return 0;
    }

    @Override
    public void commit(long xid) {}

    @Override
    public void abort(long xid) {}

    @Override
    public boolean isActive(long xid) {
        return false;
    }

    @Override
    public boolean isCommitted(long xid) {
        return false;
    }

    @Override
    public boolean isAborted(long xid) {
        return false;
    }

    @Override
    public void close() {}

}

