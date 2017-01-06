package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

public class RetryAttempts implements Callable<Boolean> {
    private static final Logger LOG = LoggerFactory.getLogger(RetryAttempts.class);

    private final int maxNumberOfAttempts;
    private final Callable<Boolean> attempt;
    private final long retryDelayInMilliseconds;

    public RetryAttempts(int maxNumberOfAttempts, Callable<Boolean> attempt, long retryDelayInMilliseconds) {
        this.maxNumberOfAttempts = maxNumberOfAttempts;
        this.attempt = attempt;
        this.retryDelayInMilliseconds = retryDelayInMilliseconds;
    }

    @Override
    public Boolean call() throws Exception {
        int attemptsLeft = maxNumberOfAttempts;
        while (attemptsLeft-- > 0) {
            if (attempt.call()) {
                LOG.info("Connection attempt succeeded");
                return true;
            }
            LOG.info("Connection attempt failed ({} attempts left)", attemptsLeft);
            Thread.sleep(retryDelayInMilliseconds);
        }
        return false;
    }
}
