package top.kexin.minidb.backend.dm.page;

/**
 * Page interface represents a single unit of storage in the database (a data page).
 *
 * Each page contains a block of raw binary data and is identified by a unique page number.
 * The interface provides methods for concurrency control (lock/unlock), dirty tracking,
 * and memory management (release). Implementations of this interface are expected to
 * handle synchronization, buffer cache lifecycle, and persistence logic.
 *
 * Responsibilities:
 * - Provide exclusive lock/unlock operations for thread-safe access.
 * - Mark and check whether a page has been modified (dirty flag).
 * - Expose the raw byte data of the page for read/write operations.
 * - Support releasing resources when the page is no longer in use.
 *
 * Typical usage:
 * - Page objects are managed by the Buffer Manager and used in storage engines.
 * - Clients fetch a page, acquire a lock, read/write data, mark it dirty if modified,
 *   and finally release it back to the cache.
 *
 *
 * @author KexinDai
 * @date 2025/8/21
 */
public interface Page {
    void lock();
    void unlock();
    void release();
    void setDirty(boolean dirty);
    boolean isDirty();
    int getPageNumber();
    byte[] getData();
}
