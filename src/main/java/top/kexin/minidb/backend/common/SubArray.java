package top.kexin.minidb.backend.common;

/**
 * content
 *
 * @author KexinDai
 * @date 2025/8/21
 */
public class SubArray {
    public byte[] raw;
    public int start;
    public int end;

    public SubArray(byte[] raw, int start, int end) {
        this.raw = raw;
        this.start = start;
        this.end = end;
    }
}
