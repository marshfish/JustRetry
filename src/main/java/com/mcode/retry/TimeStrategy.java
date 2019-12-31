package com.mcode.retry;

import org.apache.commons.lang3.RandomUtils;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public interface TimeStrategy extends Supplier<Long> {
    default TimeStrategy format(TimeUnit time) {
        return () -> time.toMillis(get());
    }

    static TimeStrategy random(long start, long end) {
        return () -> RandomUtils.nextLong(start, end);
    }

    static TimeStrategy random(int bound) {
        return () -> RandomUtils.nextLong(0, bound);
    }

    static TimeStrategy fixed(long interval) {
        return () -> (long) interval;
    }

    static TimeStrategy nothing() {
        return () -> 0L;
    }
}
