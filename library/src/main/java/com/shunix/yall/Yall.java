package com.shunix.yall;

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author shunix
 * @since 2016/04/08
 */
public class Yall {
    private static ThreadLocal<StringBuilder> mLocalBuilder = new ThreadLocal<>();
    private static ConcurrentLinkedQueue<String> mLogQueue = new ConcurrentLinkedQueue<>();
    private static ScheduledExecutorService mExecutor = new ScheduledThreadPoolExecutor(1);

    private static Runnable scheduledTask = new Runnable() {
        @Override
        public void run() {
            int size = mLogQueue.size();
            Iterator<String> iterator = mLogQueue.iterator();
            int writtenCount = 0;
            while(iterator.hasNext()) {
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

    private static String getMethodInfo(int methodDepth) {
        StringBuilder builder = getLocalStringBuilder();
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        if (stackTrace.length > methodDepth + 1) {
            StackTraceElement element = stackTrace[methodDepth];
        }
        return builder.toString();
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
        mExecutor.scheduleWithFixedDelay(scheduledTask, YallConfig.SYNC_INTERVAL, YallConfig.SYNC_INTERVAL, TimeUnit.SECONDS);
    }

    public static void destroy() {
        mExecutor.shutdown();
    }
}
