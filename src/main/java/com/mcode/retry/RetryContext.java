package com.mcode.retry;

import java.util.List;
import java.util.concurrent.Executor;

public class RetryContext<C> {
    private final Executor executor;
    private final List<Class<? extends Throwable>> throwableList;
    private final TimeStrategy interval;
    private final boolean maybeInterrupted;
    private final int retryTime;
    private final long timeWindow;
    private final C businessContext;

    RetryContext(Executor executor,
                 List<Class<? extends Throwable>> throwableList,
                 boolean maybeInterrupted,
                 TimeStrategy interval,
                 int retryTime,
                 long timeWindow, C businessContext) {
        this.maybeInterrupted = maybeInterrupted;
        this.executor = executor;
        this.throwableList = throwableList;
        this.interval = interval;
        this.retryTime = retryTime;
        this.timeWindow = timeWindow;
        this.businessContext = businessContext;
    }

    @Override
    public String toString() {
        return "RetryContext{" +
                "executor=" + executor +
                ", throwableList=" + throwableList +
                ", maybeInterrupted=" + maybeInterrupted +
                ", retryTime=" + retryTime +
                '}';
    }

    public int getRetryTime() {
        return retryTime;
    }

    public boolean canRetry(int current){
        return retryTime == Integer.MIN_VALUE || current < retryTime;
    }

    public Executor getExecutor() {
        return executor;
    }

    public TimeStrategy getInterval() {
        return interval;
    }

    public boolean isMaybeInterrupted() {
        return maybeInterrupted;
    }

    public boolean needRetry(Throwable e) {
        return throwableList.stream().anyMatch(throwable -> throwable.isAssignableFrom(e.getClass()));
    }

    public long getTimeWindow() {
        return timeWindow;
    }

    public C getBusinessContext() {
        return businessContext;
    }
}
