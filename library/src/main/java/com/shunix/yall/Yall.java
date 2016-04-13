package com.shunix.yall;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
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
    private static ThreadLocal<StringBuilder> mLocalBuilder = new ThreadLocal<>();
    private static ConcurrentLinkedQueue<String> mLogQueue = new ConcurrentLinkedQueue<>();
    private static ScheduledExecutorService mExecutor = new ScheduledThreadPoolExecutor(1);
    private static SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private static Context mContext;

    private static Runnable scheduledTask = new Runnable() {
        @Override
        public void run() {
            try {
                File logFile = getLogFile();
                if (logFile == null) {
                    return;
                }
                FileOutputStream outputStream = new FileOutputStream(logFile);
                PrintWriter writer = new PrintWriter(outputStream);
                int size = mLogQueue.size();
                Iterator<String> iterator = mLogQueue.iterator();
                int writtenCount = 0;
                while (iterator.hasNext()) {
                    String logItem = iterator.next();
                    // FIXME may overwrite current content
                    writer.write(logItem);
                    iterator.remove();
                    writtenCount++;
                    if (writtenCount == size) {
                        break;
                    }
                }
            } catch (Exception e) {
                Yall.log(LOG_LEVEL.ERROR, "Yall.ScheduledTask", e.getMessage());
            }
        }
    };

    private static File getLogFile() {
        if (mContext == null) {
            return null;
        }
        File logFile = null;
        if (Environment.isExternalStorageEmulated() && Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
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
        if (mContext != null) {
            builder.append(mContext.getPackageName()).append(FILE_NAME_SEPARATOR);
        }
        builder.append(year).append(FILE_NAME_SEPARATOR);
        builder.append(month).append(FILE_NAME_SEPARATOR);
        builder.append(day).append(FILE_NAME_SEPARATOR);
        builder.append(hour);
        return builder.toString();
    }

    /**
     * Get the caller method information
     * Format: Classname | Linenumber | Methodname
     *
     * @param callDepth
     * @return
     */
    private static void appendMethodInfo(int callDepth, StringBuilder builder) {
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        if (stackTrace.length > callDepth + 1) {
            StackTraceElement element = stackTrace[callDepth];
            if (element != null) {
                builder.append(element.getClassName())
                        .append(LOG_COLUMN_SEPARATOR)
                        .append(element.getLineNumber())
                        .append(LOG_COLUMN_SEPARATOR)
                        .append(element.getMethodName())
                        .append(LOG_COLUMN_SEPARATOR);
            }
        }
    }

    private static void appendTime(StringBuilder builder) {
        long timestamp = System.currentTimeMillis();
        builder.append(mDateFormat.format(timestamp)).append(LOG_COLUMN_SEPARATOR);
    }

    private static StringBuilder getLocalStringBuilder() {
        StringBuilder builder = mLocalBuilder.get();
        if (builder != null) {
            builder.delete(0, builder.length());
        } else {
            builder = new StringBuilder();
            mLocalBuilder.set(builder);
        }
        return builder;
    }

    public static void init(Context context) {
        mContext = context;
        mExecutor.scheduleWithFixedDelay(scheduledTask, SYNC_INTERVAL, SYNC_INTERVAL, TimeUnit.SECONDS);
    }

    public static void destroy() {
        mExecutor.shutdown();
    }

    /**
     * Add log message to queue
     * Format : Time | Classname | Linenumber | Methodname | Level | Tag | Message
     *
     * @param level
     * @param tag
     * @param msg
     */
    public static void log(LOG_LEVEL level, String tag, String msg) {
        StringBuilder builder = getLocalStringBuilder();
        appendTime(builder);
        appendMethodInfo(CALL_DEPTH, builder);
        switch (level) {
            case DEBUG:
                builder.append(LOG_LEVEL.DEBUG.name());
                break;
            case ERROR:
                builder.append(LOG_LEVEL.ERROR.name());
                break;
            case INFO:
                builder.append(LOG_LEVEL.INFO.name());
                break;
            case VERBOSE:
                builder.append(LOG_LEVEL.VERBOSE.name());
                break;
            case WARN:
                builder.append(LOG_LEVEL.WARN.name());
                break;
            case WTF:
                builder.append(LOG_LEVEL.WTF.name());
                break;
        }
        builder
                .append(LOG_COLUMN_SEPARATOR)
                .append(tag)
                .append(LOG_COLUMN_SEPARATOR)
                .append(msg)
                .append("\n");
        String message = builder.toString();
        mLogQueue.add(message);
        if (IS_WRITE_TO_LOGCAT) {
            switch (level) {
                case DEBUG:
                    Log.d(tag, message);
                    break;
                case ERROR:
                    Log.e(tag, message);
                    break;
                case INFO:
                    Log.i(tag, message);
                    break;
                case VERBOSE:
                    Log.v(tag, message);
                    break;
                case WARN:
                    Log.w(tag, message);
                    break;
                case WTF:
                    Log.wtf(tag, message);
                    break;
            }
        }
    }
}
