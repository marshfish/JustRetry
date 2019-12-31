package com.mcode.retry;

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
        Retry<String, Retry.Void> success = Retry.of(() -> test(true))
                .retryTimes(4)
                .interval(TimeStrategy.random(4), TimeUnit.SECONDS)
                .supply(executor)
                .forceSync()
                .infinite()
                .maybeInterrupted()
                .retryTimeWindow(5, TimeUnit.SECONDS)
                .retryOn(RuntimeException.class, IllegalArgumentException.class)
                .successHook((e, c) -> log.warn("success"))
                .recoverWith(c -> "do recorver")
                .throwableHook(e -> log.warn("merge sample, invoke LIMS Exception:{}", e.getMessage()))
                .build();

        String result = success.getResult();
        System.out.println(result);
//        CompletableFuture<String> future = retry.getFuture();
//        String s = retry.getResult();
//        String s1 = retry.getResult(100, TimeUnit.SECONDS);
//        Callable<String> stringCallable = retry.toCallable();
//        Runnable runnable = retry.toRunnable();


//        Assert.assertEquals(retry.getResult(), "retry result");
    }

}
