package xyz.blanchot.vectorx.dispatch;

public interface KernelDispatcher {

    String configKey();

    boolean isVector();

    String disableReason();

    Object backend();
}
