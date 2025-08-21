package top.kexin.minidb.backend.utils;

/**
 * deal with fatal error
 * exit and print error messages
 *
 * @author KexinDai
 * @date 2025/8/21
 */
public class Panic {
    public static void panic(Exception err) {
        err.printStackTrace();
        System.exit(1);
    }
}
