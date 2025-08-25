package top.kexin.minidb.backend.utils;

/**
 * util class to get uid based on pgno and offset
 *
 * @author KexinDai
 * @date 2025/8/24
 */
public class Types {
    public static long addressToUid(int pgno, short offset) {
        long u0 = (long)pgno;
        long u1 = (long)offset;
        return u0 << 32 | u1;
    }
}
