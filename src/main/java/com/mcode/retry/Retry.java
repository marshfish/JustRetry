package com.mcode.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Retry<R> {
    private static final Logger log = LoggerFactory.getLogger(Retry.class);
    private final Supplier<R> action;
    private final Consumer<R> onSuccess;
    private final Consumer<RetryRunTimeException> onThrowable;
    private final Supplier<R> recover;
    private final RetryContext context;
    private final CompletableFuture<R> future;

    private Retry(Supplier<R> action,
                  Consumer<R> onSuccess,
                  Consumer<RetryRunTimeException> onThrowable,
                  Supplier<R> recover,
                  RetryContext context) {
        this.action = action;
        this.onSuccess = onSuccess;
        this.onThrowable = onThrowable;
        this.recover = recover;
        this.context = context;
        this.future = new CompletableFuture<>();
    }

    private static class RetryContext {
        private final Executor executor;
        private final List<Class<? extends Throwable>> throwableList;
        private final TimeSupplier interval;
        private final boolean maybeInterrupted;
        private final int retryTime;
        private final long timeWindow;

        private RetryContext(Executor executor,
                             List<Class<? extends Throwable>> throwableList,
                             boolean maybeInterrupted,
                             TimeSupplier interval,
                             int retryTime,
                             long timeWindow) {
            this.maybeInterrupted = maybeInterrupted;
            this.executor = executor;
            this.throwableList = throwableList;
            this.interval = interval;
            this.retryTime = retryTime;
            this.timeWindow = timeWindow;
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

        private int getRetryTime() {
            return retryTime;
        }

        public Executor getExecutor() {
            return executor;
        }

        public TimeSupplier getInterval() {
            return interval;
        }

        private boolean isMaybeInterrupted() {
            return maybeInterrupted;
        }

        private boolean needRetry(Throwable e) {
            return throwableList.stream().anyMatch(throwable -> throwable.isAssignableFrom(e.getClass()));
        }

        private long getTimeWindow() {
            return timeWindow;
        }
    }

    public static class RetryBuilder<R> {
        private final Supplier<R> action;
        private Consumer<R> onSuccess;
        private Consumer<RetryRunTimeException> onThrowable;
        private Supplier<R> recover;
        private Executor executor;
        private List<Class<? extends Throwable>> throwableList;
        private boolean maybeInterrupted;
        private TimeSupplier interval;
        private int retryTime;
        private long timeWindow;


        public Retry<R> build() {
            if (throwableList.size() == 0) {
                List<Class<? extends Throwable>> list = new ArrayList<>();
                list.add(Exception.class);
                throwableList = list;
            }
            RetryContext context = new RetryContext(executor != null ? executor : (Executor) Runnable::run,
                    throwableList,
                    maybeInterrupted,
                    interval != null ? interval : () -> 0L,
                    Math.max(retryTime, 0),
                    Math.max(timeWindow, 1000));
            return new Retry<>(action,
                    onSuccess != null ? onSuccess : e -> {
                    },
                    onThrowable != null ? onThrowable : e -> log.warn("Retry Exception :", e),
                    recover != null ? recover : () -> null,
                    context);
        }

        private RetryBuilder(Supplier<R> action) {
            this.action = action;
        }

        /**
         * 当操作抛出某异常时才会重试，默认为Exception。class
         *
         * @param throwableList 异常LIST
         */
        @SafeVarargs
        public final RetryBuilder<R> retryWhen(Class<? extends Throwable>... throwableList) {
            this.throwableList = Arrays.asList(throwableList);
            return this;
        }

        /**
         * 重试次数
         *
         * @param times 次数
         */
        public RetryBuilder<R> retryTimes(int times) {
            this.retryTime = Math.max(times, 0);
            return this;
        }

        /**
         * 设置线程等待间隔时间时响应中断
         */
        public RetryBuilder<R> maybeInterrupted() {
            this.maybeInterrupted = true;
            return this;
        }

        /**
         * 设置异步模式
         *
         * @param executor 执行线程池
         */
        public RetryBuilder<R> async(Executor executor) {
            Objects.requireNonNull(executor);
            this.executor = executor;
            return this;
        }

        /**
         * 设置异步模式并使用CommonForkJoinPool
         */
        public RetryBuilder<R> async() {
            this.executor = ForkJoinPool.commonPool();
            return this;
        }

        /**
         * 重试间隔时间
         *
         * @param interval 间隔时间wrapper，提供不同策略的间隔时间
         */
        public RetryBuilder<R> interval(TimeSupplier interval) {
            this.interval = interval;
            return this;
        }

        /**
         * 重试间隔时间
         *
         * @param interval 间隔时间wrapper，提供不同策略的间隔时间
         * @param timeUnit 时间单位
         */
        public RetryBuilder<R> interval(TimeSupplier interval, TimeUnit timeUnit) {
            Objects.requireNonNull(interval);
            Objects.requireNonNull(timeUnit);
            this.interval = interval.format(timeUnit);
            return this;
        }

        /**
         * 执行操作成功时调用一次
         *
         * @param onSuccess 钩子
         */
        public RetryBuilder<R> successHook(Consumer<R> onSuccess) {
            Objects.requireNonNull(onSuccess);
            this.onSuccess = onSuccess;
            return this;
        }

        /**
         * 执行操作失败时调用（由于会重试，可能调用该钩子多次）
         *
         * @param onThrowable 钩子
         */
        public RetryBuilder<R> throwableHook(Consumer<RetryRunTimeException> onThrowable) {
            Objects.requireNonNull(onThrowable);
            this.onThrowable = onThrowable;
            return this;
        }

        /**
         * 失败恢复，重试多次仍然失败，则返回预设值/降级数据
         *
         * @param recover 预设值
         */
        public RetryBuilder<R> recoverResult(Supplier<R> recover) {
            Objects.requireNonNull(recover);
            this.recover = recover;
            return this;
        }

        /**
         * 重试时间窗口，在首次重试->N次重试过程中如果发现超过该时间窗口，则放弃重试，返回 {@link #recoverResult(Supplier)}
         *
         * @param timeWindow 重试时间窗口
         */
        public RetryBuilder<R> retryTimeWindow(long timeWindow) {
            this.timeWindow = timeWindow;
            return this;
        }

        /**
         * 重试时间窗口，在首次重试->N次重试过程中如果发现超过该时间窗口，则放弃重试，返回 {@link #recoverResult(Supplier)}
         * 注意，异步模式下。当调用{@link #resultFuture()} 返回CompletableFuture<R>的时，
         * 由于返回的Future<R>提供了@{@link CompletableFuture#get(long, TimeUnit)}等超时方法，若其超时时间小于
         * 该方法设置的超时时间，可能导致该设置失效
         *
         * @param timeWindow 重试时间窗口
         * @param timeUnit   时间单位
         */
        public RetryBuilder<R> retryTimeWindow(long timeWindow, TimeUnit timeUnit) {
            this.timeWindow = timeUnit.toMillis(timeWindow);
            return this;
        }
    }

    public interface TimeSupplier extends Supplier<Long> {
        default TimeSupplier format(TimeUnit time) {
            return () -> time.toMillis(get());
        }

        static TimeSupplier random(int bound) {
            Random random = new Random();
            return () -> (long) random.nextInt(bound);
        }

        static TimeSupplier fixed(long interval) {
            return () -> (long) interval;
        }
    }

    public CompletableFuture<R> resultFuture() {
        context.getExecutor().execute(this::runRetry);
        return future;
    }

    public R resultBlocking(long time, TimeUnit timeUnit) {
        try {
            context.getExecutor().execute(this::runRetry);
            if (timeUnit != null) {
                return future.get(time, timeUnit);
            }
            return future.get();
        } catch (InterruptedException e) {
            log.warn("thread is interrupted when blocking for async result,context: {}", context);
        } catch (ExecutionException e) {
            log.warn("ExecutionException: ", e);
        } catch (TimeoutException e) {
            log.warn("get blocking result timeout: ", e);
        }
        return null;
    }

    public R resultBlocking() {
        return resultBlocking(0, null);
    }

    private void runRetry() {
        int i = 0;
        long start = System.currentTimeMillis();
        while (!future.isDone() && i < context.getRetryTime()) {
            TimeSupplier interval = context.getInterval();
            if (i > 0) {
                try {
                    Thread.sleep(Math.abs(interval.get()));
                } catch (InterruptedException e) {
                    if (context.isMaybeInterrupted()) {
                        log.warn("thread is interrupted when waiting for sleep interval,context: {}", context);
                        return;
                    }
                }
            }
            i++;
            R result;
            try {
                if (start + context.getTimeWindow() < System.currentTimeMillis()) {
                    break;
                }
                result = action.get();
                future.complete(result);
            } catch (Throwable e) {
                onThrowable.accept(RetryRunTimeException.create(context, e));
                if (context.needRetry(e)) {
                    continue;
                }
                break;
            }
            onSuccess.accept(result);
            return;
        }
        if (!future.isDone()) {
            future.complete(recover.get());
        }
    }


    public static <R> RetryBuilder<R> of(Supplier<R> action) {
        return new RetryBuilder<>(action);
    }

    public static class RetryRunTimeException extends RuntimeException {
        private RetryContext context;

        private RetryRunTimeException(RetryContext context, Throwable e) {
            super(e.getMessage());
            super.initCause(e);
            super.setStackTrace(e.getStackTrace());
            this.context = context;
        }

        public static RetryRunTimeException create(RetryContext context, Throwable e) {
            return new RetryRunTimeException(context, e);
        }

        public RetryContext getContext() {
            return context;
        }

        @Override
        public String toString() {
            return super.toString() + "\n retry context:" + context;
        }
    }
}
