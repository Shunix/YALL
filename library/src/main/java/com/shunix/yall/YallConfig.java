package com.shunix.yall;

/**
 * @author shunix
 * @since 2016/04/08
 */
public interface YallConfig {
    enum LOG_LEVEL {
        DEBUG,
        ERROR,
        INFO,
        VERBOSE,
        WARN,
        WTF
    }
    boolean IS_WRITE_TO_LOGCAT = true; // Whether or not the log will be write to logcat
    int SYNC_INTERVAL = 30; // Interval for flush logs to file, measured in seconds
    int CALL_DEPTH = 2;
    String LOG_COLUMN_SEPARATOR = "|";
    String FILE_NAME_SEPARATOR = "-";
    String LOG_DIR_NAME = "logs";
}
