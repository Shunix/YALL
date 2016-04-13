package com.shunix.yall;

import android.util.Log;

import java.text.SimpleDateFormat;
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

    private static Runnable scheduledTask = new Runnable() {
        @Override
        public void run() {
            int size = mLogQueue.size();
            Iterator<String> iterator = mLogQueue.iterator();
            int writtenCount = 0;
            while (iterator.hasNext()) {
                String logItem = iterator.next();
                // TODO: 2016/4/12 write to file
                mLogQueue.remove(logItem);
                writtenCount++;
                if (writtenCount == size) {
                    break;
                }
            }
        }
    };

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

    public static void init() {
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
                .append(msg);
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
