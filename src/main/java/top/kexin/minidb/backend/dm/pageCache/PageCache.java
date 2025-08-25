package top.kexin.minidb.backend.dm.pageCache;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

import top.kexin.minidb.backend.dm.page.Page;
import top.kexin.minidb.backend.utils.Panic;
import top.kexin.minidb.common.Error;

/**
 * PageCache defines the abstraction for a page caching mechanism in the database.
 *
 * Each data page has a fixed size #PAGE_SIZE, and the cache manages the lifecycle
 * of pages between disk (RandomAccessFile/FileChannel) and memory.
 * It provides unified APIs to create, fetch, release, flush, and truncate pages.
 *
 * Responsibilities:
 *   - Manage in-memory pages with a fixed capacity.</li>
 *   - Provide access to existing pages by page number.</li>
 *   - Support creating new pages with initialized content.</li>
 *   - Mark pages dirty and flush them to disk when needed.</li>
 *   - Handle resource closing and file lifecycle management.</li>
 *
 *
 * @author KexinDai
 * @date 2025/8/21
 */

public interface PageCache {

    public static final int PAGE_SIZE = 1 << 13;

    int newPage(byte[] initData);
    Page getPage(int pgno) throws Exception;
    void close();
    void release(Page page);

    void truncateByBgno(int maxPgno);
    int getPageNumber();
    void flushPage(Page pg);

    // static factory methods
    public static PageCacheImpl create(String path, long memory) {
        File f = new File(path+PageCacheImpl.DB_SUFFIX);
        try {
            if(!f.createNewFile()) {
                Panic.panic(Error.FileExistsException);
            }
        } catch (Exception e) {
            Panic.panic(e);
        }
        if(!f.canRead() || !f.canWrite()) {
            Panic.panic(Error.FileCannotRWException);
        }

        FileChannel fc = null;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f, "rw");
            fc = raf.getChannel();
        } catch (FileNotFoundException e) {
            Panic.panic(e);
        }
        return new PageCacheImpl(raf, fc, (int)memory/PAGE_SIZE);
    }

    public static PageCacheImpl open(String path, long memory) {
        File f = new File(path+PageCacheImpl.DB_SUFFIX);
        if(!f.exists()) {
            Panic.panic(Error.FileNotExistsException);
        }
        if(!f.canRead() || !f.canWrite()) {
            Panic.panic(Error.FileCannotRWException);
        }

        FileChannel fc = null;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f, "rw");
            fc = raf.getChannel();
        } catch (FileNotFoundException e) {
            Panic.panic(e);
        }
        return new PageCacheImpl(raf, fc, (int)memory/PAGE_SIZE);
    }
}

