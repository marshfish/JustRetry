package com.mcode.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class Retry<R, C> {
    private static final Logger log = LoggerFactory.getLogger(Retry.class);
    private final Supplier<R> action;
    private final BiConsumer<R, C> onSuccess;
    private final Consumer<RetryRunTimeException> onThrowable;
    private final Function<C, R> recover;
    private final RetryContext<C> context;
    private final CompletableFuture<R> future;

    private Retry(Supplier<R> action,
                  BiConsumer<R, C> onSuccess,
                  Consumer<RetryRunTimeException> onThrowable,
                  Function<C, R> recover,
                  RetryContext<C> context) {
        this.action = action;
        this.onSuccess = onSuccess;
        this.onThrowable = onThrowable;
        this.recover = recover;
        this.context = context;
        this.future = new CompletableFuture<>();
    }

    public static class RetryBuilder<R, C> {
        private final Supplier<R> action;
        private BiConsumer<R, C> onSuccess;
        private Consumer<RetryRunTimeException> onThrowable;
        private Function<C, R> recover;
        private Executor executor;
        private List<Class<? extends Throwable>> throwableList;
        private boolean maybeInterrupted;
        private TimeStrategy interval;
        private int retryTime;
        private long timeWindow;
        private C businessCtx;

        public RetryBuilder(Supplier<R> action, C context) {
            this.action = action;
            this.businessCtx = context;
        }


        public Retry<R, C> build() {
            if (throwableList.size() == 0) {
                List<Class<? extends Throwable>> list = new ArrayList<>();
                list.add(Exception.class);
                throwableList = list;
            }
            RetryContext<C> context = new RetryContext<>(executor != null ? executor : ForkJoinPool.commonPool(),
                    throwableList,
                    maybeInterrupted,
                    interval != null ? interval : () -> 0L,
                    retryTime == Integer.MIN_VALUE ? retryTime : Math.max(retryTime, 0),
                    Math.max(timeWindow, 1000), businessCtx);
            return new Retry<>(action,
                    onSuccess != null ? onSuccess : (e, c) -> {
                    },
                    onThrowable != null ? onThrowable : e -> log.warn("Retry Exception :", e),
                    recover != null ? recover : c -> null,
                    context);
        }

        /**
         * 当操作抛出某异常时才会重试，默认为Exception。class
         *
         * @param throwableList 异常LIST
         */
        @SafeVarargs
        public final RetryBuilder<R, C> retryOn(Class<? extends Throwable>... throwableList) {
            this.throwableList = Arrays.asList(throwableList);
            return this;
        }

        /**
         * 重试次数
         *
         * @param times 次数
         */
        public RetryBuilder<R, C> retryTimes(int times) {
            this.retryTime = Math.max(times, 0);
            return this;
        }

        /**
         * 无限重试
         */
        public RetryBuilder<R, C> infinite() {
            this.retryTime = Integer.MIN_VALUE;
            return this;
        }

        /**
         * 设置线程等待间隔时间时响应中断
         */
        public RetryBuilder<R, C> maybeInterrupted() {
            this.maybeInterrupted = true;
            return this;
        }

        /**
         * 提供异步模式的执行器
         *
         * @param executor 执行线程池
         */
        public RetryBuilder<R, C> supply(ExecutorService executor) {
            Objects.requireNonNull(executor);
            this.executor = executor;
            return this;
        }

        /**
         * 强制使用同步模式，注意会使返回的Future、Callback等全部失效
         */
        public RetryBuilder<R, C> forceSync() {
            this.executor = Runnable::run;
            return this;
        }

        /**
         * 重试间隔时间
         *
         * @param interval 间隔时间wrapper，提供不同策略的间隔时间
         */
        public RetryBuilder<R, C> interval(TimeStrategy interval) {
            this.interval = interval;
            return this;
        }

        /**
         * 重试间隔时间
         *
         * @param interval 间隔时间wrapper，提供不同策略的间隔时间
         * @param timeUnit 时间单位
         */
        public RetryBuilder<R, C> interval(TimeStrategy interval, TimeUnit timeUnit) {
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
        public RetryBuilder<R, C> successHook(BiConsumer<R, C> onSuccess) {
            Objects.requireNonNull(onSuccess);
            this.onSuccess = onSuccess;
            return this;
        }

        /**
         * 执行操作失败时调用（由于会重试，可能调用该钩子多次）
         *
         * @param onThrowable 钩子
         */
        public RetryBuilder<R, C> throwableHook(Consumer<RetryRunTimeException> onThrowable) {
            Objects.requireNonNull(onThrowable);
            this.onThrowable = onThrowable;
            return this;
        }

        /**
         * 失败恢复，重试多次仍然失败，则返回预设值/降级数据
         *
         * @param recover 预设值
         */
        public RetryBuilder<R, C> recoverWith(Function<C, R> recover) {
            Objects.requireNonNull(recover);
            this.recover = recover;
            return this;
        }

        /**
         * 重试时间窗口，在首次重试->N次重试过程中如果发现超过该时间窗口，则放弃重试，返回 {@link #recoverWith(Function)}
         *
         * @param timeWindow 重试时间窗口
         */
        public RetryBuilder<R, C> retryTimeWindow(long timeWindow) {
            this.timeWindow = timeWindow;
            return this;
        }

        /**
         * 重试时间窗口，在首次重试->N次重试过程中如果发现超过该时间窗口，则放弃重试，返回 {@link #recoverWith(Function)}
         * 注意，异步模式下。当调用{@link #getFuture()} 返回CompletableFuture<R>的时，
         * 由于返回的Future<R>提供了@{@link CompletableFuture#get(long, TimeUnit)}等超时方法，若其超时时间小于
         * 该方法设置的超时时间，可能导致该设置失效
         *
         * @param timeWindow 重试时间窗口
         * @param timeUnit   时间单位
         */
        public RetryBuilder<R, C> retryTimeWindow(long timeWindow, TimeUnit timeUnit) {
            this.timeWindow = timeUnit.toMillis(timeWindow);
            return this;
        }
    }


    public void run() {
        context.getExecutor().execute(this::runRetry);
    }


    public CompletableFuture<R> getFuture() {
        context.getExecutor().execute(this::runRetry);
        return future;
    }


    public R getResult(long time, TimeUnit timeUnit) {
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

    public R getResult() {
        return getResult(0, null);
    }

    public Callable<R> toCallable() {
        return this::getResult;
    }

    public Runnable toRunnable() {
        return this::getResult;
    }

    private void runRetry() {
        int i = 0;
        long start = System.currentTimeMillis();
        while (!future.isDone() && context.canRetry(i)) {
            if (i++ == Integer.MAX_VALUE) {
                i = 0;
            }
            if (i > 1) {
                try {
                    Thread.sleep(Math.abs(context.getInterval().get()));
                } catch (InterruptedException e) {
                    if (context.isMaybeInterrupted()) {
                        log.warn("thread is interrupted when waiting for sleep interval,context: {}", context);
                        return;
                    }
                }
            }

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
            onSuccess.accept(result, context.getBusinessContext());
            return;
        }
        if (!future.isDone()) {
            future.complete(recover.apply(context.getBusinessContext()));
        }
    }


    public static <R> RetryBuilder<R, Void> of(Supplier<R> action) {
        return new RetryBuilder<>(action, Void.VOID);
    }

    public static <R> RetryBuilder<R, Void> of(Runnable action) {
        return new RetryBuilder<>(() -> {
            action.run();
            return null;
        }, Void.VOID);
    }

    public static <R, C> RetryBuilder<R, C> withContext(Supplier<R> action, C context) {
        return new RetryBuilder<>(action, context);
    }

    public static <R, C> RetryBuilder<R, C> withContext(Runnable action, C context) {
        return new RetryBuilder<>(() -> {
            action.run();
            return null;
        }, context);
    }

    public static class Void {
        private static final Void VOID = new Void();
    }
}
