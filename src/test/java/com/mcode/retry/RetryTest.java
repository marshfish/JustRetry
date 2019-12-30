package com.mcode.retry;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class RetryTest {
    private static Logger log = LoggerFactory.getLogger(RetryTest.class);

    public String test(boolean throwIt) {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (throwIt) {
            throw new RuntimeException("123123");
        }
        return "retry result";
    }

    @Test
    public void test1() {
        ExecutorService executor = Executors.newFixedThreadPool(1);
        Retry.RetryBuilder<String> builder = Retry.of(() -> test(false))
                .retryTimes(4)
                .interval(Retry.TimeSupplier.random(4), TimeUnit.SECONDS)
                .async(executor)
                .maybeInterrupted()
                .retryTimeWindow(10, TimeUnit.SECONDS)
                .retryWhen(RuntimeException.class, IllegalArgumentException.class)
                .successHook(e -> log.warn("success"))
                .recoverResult(() -> "do recorver")
                .throwableHook(e -> log.warn("merge sample, invoke LIMS Exception:{}", e.getMessage()));
        Retry<String> retry = builder.build();
        Assert.assertEquals(retry.resultBlocking(), "retry result");
    }

    @Test
    public void test2() {
        ExecutorService executor = Executors.newFixedThreadPool(1);
        Retry.RetryBuilder<String> builder = Retry.of(() -> test(true))
                .retryTimes(4)
                .interval(Retry.TimeSupplier.random(4), TimeUnit.SECONDS)
                .async(executor)
                .maybeInterrupted()
                .retryTimeWindow(10, TimeUnit.SECONDS)
                .retryWhen(RuntimeException.class, IllegalArgumentException.class)
                .successHook(e -> log.warn("success"))
                .throwableHook(e -> log.warn("merge sample, invoke LIMS Exception:{}", e.getMessage()));
        Retry<String> retry = builder.build();
        Assert.assertEquals(retry.resultBlocking(), null);
    }

    @Test
    public void test3() {
        ExecutorService executor = Executors.newFixedThreadPool(1);
        Retry.RetryBuilder<String> builder = Retry.of(() -> test(true))
                .retryTimes(4)
                .interval(Retry.TimeSupplier.random(4), TimeUnit.SECONDS)
                .async(executor)
                .maybeInterrupted()
                .retryTimeWindow(10, TimeUnit.SECONDS)
                .retryWhen(RuntimeException.class, IllegalArgumentException.class)
                .successHook(e -> log.warn("success"))
                .recoverResult(() -> "do recorver")
                .throwableHook(e -> log.warn("merge sample, invoke LIMS Exception:{}", e.getMessage()));
        Retry<String> retry = builder.build();
        Assert.assertEquals(retry.resultBlocking(), "do recorver");
    }

    @Test
    public void test4() {
        ExecutorService executor = Executors.newFixedThreadPool(1);
        Retry.RetryBuilder<String> builder = Retry.of(() -> test(true))
                .retryTimes(4)
                .interval(Retry.TimeSupplier.random(10), TimeUnit.SECONDS)
                .async(executor)
                .maybeInterrupted()
                .retryTimeWindow(10, TimeUnit.SECONDS)
                .retryWhen(RuntimeException.class, IllegalArgumentException.class)
                .recoverResult(() -> "do recorver")
                //no effect
                .successHook(e -> log.warn("success"))
                .throwableHook(e -> log.warn("merge sample, invoke LIMS Exception:{}", e.getMessage()));
        Retry<String> retry = builder.build();
        Assert.assertEquals(retry.resultBlocking(), "do recorver");
    }

    @Test
    public void test5() {
        ExecutorService executor = Executors.newFixedThreadPool(1);
        Retry.RetryBuilder<String> builder = Retry.of(() -> test(true))
                .retryTimes(4)
                .interval(Retry.TimeSupplier.random(4), TimeUnit.SECONDS)
                .async(executor)
                .maybeInterrupted()
                .retryTimeWindow(10, TimeUnit.SECONDS)
                .retryWhen(RuntimeException.class, IllegalArgumentException.class)
                .successHook(e -> log.warn("success"))
                .recoverResult(() -> "do recorver")
                .throwableHook(e -> log.warn("merge sample, invoke LIMS Exception:{}", e.getMessage()));
        Retry<String> retry = builder.build();
        Assert.assertEquals(retry.resultBlocking(), "do recorver");
    }

    @Test
    public void test6() {
        ExecutorService executor = Executors.newFixedThreadPool(1);
        Retry.RetryBuilder<String> builder = Retry.of(() -> test(true))
                .retryTimes(4)
                .interval(Retry.TimeSupplier.random(4), TimeUnit.SECONDS)
                .async(executor)
                .maybeInterrupted()
                .retryTimeWindow(10, TimeUnit.SECONDS)
                .retryWhen(RuntimeException.class, IllegalArgumentException.class)
                .successHook(e -> log.warn("success"))
                .recoverResult(() -> "do recorver")
                .throwableHook(e -> log.warn("merge sample, invoke LIMS Exception:{}", e.getMessage()));
        Retry<String> retry = builder.build();
        Assert.assertEquals(retry.resultBlocking(), "do recorver");
    }
}
