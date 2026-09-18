package xyz.blanchot.vectorx;

import org.slf4j.Logger;

import xyz.blanchot.vectorx.diag.VectorXLog;

final class Slf4jLog implements VectorXLog {

    private final Logger logger;

    Slf4jLog(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warn(String message) {
        logger.warn(message);
    }
}
