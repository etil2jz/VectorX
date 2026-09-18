package xyz.blanchot.vectorx.dispatch;

/**
 * The part of a dispatcher {@code diag.Diagnostics} needs, so the report can
 * iterate over kernels instead of taking one positional parameter per kernel.
 *
 * <p>Every dispatcher resolves its backend once at construction and then only
 * answers questions about that decision, which is all of this interface. The
 * backend type itself is deliberately {@link Object}: each kernel family has
 * its own unrelated interface, and diagnostics only ever asks whether it
 * happens to implement {@code kernel.SelfDescribing}.
 */
public interface KernelDispatcher {

    /** The config key this kernel is toggled by, e.g. {@code densityFunctionClamp}. */
    String configKey();

    /** Whether the vector backend was selected. */
    boolean isVector();

    /** Non-null only when currently on the scalar path. */
    String disableReason();

    /** The resolved backend instance. */
    Object backend();
}
