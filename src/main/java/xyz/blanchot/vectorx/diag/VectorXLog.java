package xyz.blanchot.vectorx.diag;

/**
 * Minimal logging abstraction. Kernel, dispatch, config and self-test code
 * depend only on this interface, never on SLF4J directly, so they stay
 * testable without Minecraft or fabric-loader on the classpath.
 */
public interface VectorXLog {

    static VectorXLog noop() {
        return new VectorXLog() {
            @Override
            public void info(String message) {
            }

            @Override
            public void warn(String message) {
            }
        };
    }

    static VectorXLog console() {
        return new VectorXLog() {
            @Override
            public void info(String message) {
                System.out.println("[VectorX] " + message);
            }

            @Override
            public void warn(String message) {
                System.out.println("[VectorX] [WARN] " + message);
            }
        };
    }

    void info(String message);

    void warn(String message);
}
