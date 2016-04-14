package com.shunix.yall;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author shunix
 * @since 2016/04/08
 */
public class Yall implements YallConfig {
    private static ThreadLocal<StringBuilder> mLogItemBuilder = new ThreadLocal<>();
    private static ThreadLocal<StringBuilder> mMethodInfoBuilder = new ThreadLocal<>();
    private static ConcurrentLinkedQueue<String> mLogQueue = new ConcurrentLinkedQueue<>();
    private static ScheduledExecutorService mExecutor = new ScheduledThreadPoolExecutor(1);
    private static SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private static Context mContext;
    private static String mProcessName; // In case there's more than one process

    private static Runnable scheduledTask = new Runnable() {
        @Override
        public void run() {
            File logFile = getLogFile();
            if (logFile == null) {
                return;
            }
            FileWriter fileWriter = null;
            BufferedWriter writer = null;
            try {
                fileWriter = new FileWriter(logFile, true);
                writer = new BufferedWriter(fileWriter);
                int size = mLogQueue.size();
                Iterator<String> iterator = mLogQueue.iterator();
                int writtenCount = 0;
                while (iterator.hasNext()) {
                    String logItem = iterator.next();
                    writer.write(logItem);
                    writer.newLine();
                    iterator.remove();
                    writtenCount++;
                    if (writtenCount == size) {
                        break;
                    }
                }
            } catch (Exception e) {

            } finally {
                try {
                    if (fileWriter != null) {
                        fileWriter.close();
                    }
                    if (writer != null) {
                        writer.close();
                    }
                } catch (Exception e) {

                }
            }
        }
    };

    private static File getLogFile() {
        if (mContext == null) {
            return null;
        }
        File logFile = null;
        try {
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                File logsDir = new File(mContext.getExternalFilesDir(null), LOG_DIR_NAME);
                if (logsDir.exists() || logsDir.mkdir()) {
                    logFile = new File(logsDir, getLogFileName());
                }
            }
            if (logFile == null) {
                File logsDir = new File(mContext.getFilesDir(), LOG_DIR_NAME);
                if (logsDir.exists() || logsDir.mkdir()) {
                    logFile = new File(logsDir, getLogFileName());
                }
            }
        } catch (Exception e) {

        }
        return logFile;
    }

    private static String getLogFileName() {
        Date date = new Date(System.currentTimeMillis());
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        StringBuilder builder = new StringBuilder();
        builder.append(mProcessName).append(FILE_NAME_SEPARATOR);
        builder.append(year).append(FILE_NAME_SEPARATOR);
        builder.append(month).append(FILE_NAME_SEPARATOR);
        builder.append(day).append(FILE_NAME_SEPARATOR);
        builder.append(hour);
        builder.append(LOG_FILE_EXTENTION);
        return builder.toString();
    }

    /**
     * Format: Classname | Linenumber | Methodname
     */
    static class MethodInfo {
        public String className;
        public String methodName;
        public int lineNumber;

        @Override
        public String toString() {
            StringBuilder builder = getMethodInfoBuilder();
            builder.append(className)
                    .append(LOG_COLUMN_SEPARATOR)
                    .append(lineNumber)
                    .append(LOG_COLUMN_SEPARATOR)
                    .append(methodName);
            return builder.toString();
        }
    }

    /**
     * Get the caller method information
     *
     * @param callDepth
     * @return
     */
    private static MethodInfo getMethodInfo(int callDepth) {
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        if (stackTrace.length > callDepth + 1) {
            StackTraceElement element = stackTrace[callDepth];
            if (element != null) {
                MethodInfo info = new MethodInfo();
                info.className = element.getClassName();
                info.methodName = element.getMethodName();
                info.lineNumber = element.getLineNumber();
                return info;
            }
        }
        return null;
    }

    private static String getCurrentTime() {
        long timestamp = System.currentTimeMillis();
        return mDateFormat.format(timestamp);
    }

    private static StringBuilder getLogItemBuilder() {
        StringBuilder builder = mLogItemBuilder.get();
        if (builder != null) {
            builder.delete(0, builder.length());
        } else {
            builder = new StringBuilder();
            mLogItemBuilder.set(builder);
        }
        return builder;
    }

    private static StringBuilder getMethodInfoBuilder() {
        StringBuilder builder = mMethodInfoBuilder.get();
        if (builder != null) {
            builder.delete(0, builder.length());
        } else {
            builder = new StringBuilder();
            mMethodInfoBuilder.set(builder);
        }
        return builder;
    }

    private static void initProcessName() {
        if (mContext == null) {
            return;
        }
        int pid = android.os.Process.myPid();
        ActivityManager activityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningAppProcessInfo info : activityManager.getRunningAppProcesses()) {
            if (info != null && info.pid == pid) {
                mProcessName = info.processName;
                break;
            }
        }
    }

    public static void init(Context context) {
        mContext = context;
        initProcessName();
        mExecutor.scheduleWithFixedDelay(scheduledTask, SYNC_INTERVAL, SYNC_INTERVAL, TimeUnit.SECONDS);
    }

    public static void destroy() {
        mExecutor.shutdown();
    }

    /**
     * Add logInner message to queue
     * Format : Time | Classname | Linenumber | Methodname | Level | Tag | Message
     *
     * @param level
     * @param tag
     * @param msg
     */
    private static void logInner(LOG_LEVEL level, String tag, String msg, int callDepth) {
        StringBuilder builder = getLogItemBuilder();
        String currentTime = getCurrentTime();
        MethodInfo info = getMethodInfo(callDepth);
        builder.append(currentTime).append(LOG_COLUMN_SEPARATOR);
        if (info != null) {
            builder.append(info.toString()).append(LOG_COLUMN_SEPARATOR);
        } else {
            // placeholder
            builder.append(LOG_COLUMN_SEPARATOR)
                    .append(LOG_COLUMN_SEPARATOR)
                    .append(LOG_COLUMN_SEPARATOR);
        }
        switch (level) {
            case DEBUG:
                builder.append(LOG_LEVEL.DEBUG.name());
                if (IS_WRITE_TO_LOGCAT) {
                    Log.d(tag, msg);
                }
                break;
            case ERROR:
                builder.append(LOG_LEVEL.ERROR.name());
                if (IS_WRITE_TO_LOGCAT) {
                    Log.e(tag, msg);
                }
                break;
            case INFO:
                builder.append(LOG_LEVEL.INFO.name());
                if (IS_WRITE_TO_LOGCAT) {
                    Log.i(tag, msg);
                }
                break;
            case VERBOSE:
                builder.append(LOG_LEVEL.VERBOSE.name());
                if (IS_WRITE_TO_LOGCAT) {
                    Log.v(tag, msg);
                }
                break;
            case WARN:
                builder.append(LOG_LEVEL.WARN.name());
                if (IS_WRITE_TO_LOGCAT) {
                    Log.w(tag, msg);
                }
                break;
            case WTF:
                builder.append(LOG_LEVEL.WTF.name());
                if (IS_WRITE_TO_LOGCAT) {
                    Log.wtf(tag, msg);
                }
                break;
        }
        builder
                .append(LOG_COLUMN_SEPARATOR)
                .append(tag)
                .append(LOG_COLUMN_SEPARATOR)
                .append(msg);
        mLogQueue.add(builder.toString());
    }

    public static void log(LOG_LEVEL level, String tag, String msg) {
        logInner(level, tag, msg, CALL_DEPTH + 1);
    }

    /**
     * Shortcut method, use class name as logInner tag.
     * If proguard is enabled and caller class is obfuscated,
     * the tag is meaningless.
     *
     * @param level
     * @param msg
     */
    public static void log(LOG_LEVEL level, String msg) {
        MethodInfo info = getMethodInfo(CALL_DEPTH);
        if (info != null) {
            logInner(level, info.className, msg, CALL_DEPTH + 1);
        }
    }

    public static void log(LOG_LEVEL level, String tag, String msg, Throwable tr) {
        String logMsg;
        if (!TextUtils.isEmpty(msg)) {
            logMsg = msg + "\t" + Log.getStackTraceString(tr);
        } else {
            logMsg = Log.getStackTraceString(tr);
        }
        logInner(level, tag, logMsg, CALL_DEPTH + 1);
    }

    public static void log(LOG_LEVEL level, String tag, Throwable tr) {
        if (tr != null) {
            logInner(level, tag, Log.getStackTraceString(tr), CALL_DEPTH + 1);
        }
    }

    public static void log(LOG_LEVEL level, Throwable tr) {
        MethodInfo info = getMethodInfo(CALL_DEPTH);
        if (tr != null && info != null) {
            logInner(level, info.className, Log.getStackTraceString(tr), CALL_DEPTH + 1);
        }
    }
}
