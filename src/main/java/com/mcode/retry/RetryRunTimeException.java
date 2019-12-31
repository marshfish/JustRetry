package com.mcode.retry;

public class RetryRunTimeException extends RuntimeException {
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

    public RetryContext getRetryContext() {
        return context;
    }

    @Override
    public String toString() {
        return super.toString() + "\n retry context:" + context;
    }
}
