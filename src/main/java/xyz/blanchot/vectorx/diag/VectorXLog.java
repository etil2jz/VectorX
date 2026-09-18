package xyz.blanchot.vectorx.diag;

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
