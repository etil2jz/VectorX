package xyz.blanchot.vectorx;

import org.slf4j.Logger;

import xyz.blanchot.vectorx.diag.VectorXLog;

/**
 * Adapts an SLF4J {@link Logger} to {@link VectorXLog}. This is the only
 * place besides {@link VectorX} itself that depends on SLF4J: every other
 * class in the mod logs through {@link VectorXLog} so it stays testable in a
 * plain JVM without Minecraft or fabric-loader on the classpath.
 */
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
