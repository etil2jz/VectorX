package xyz.blanchot.vectorx.kernel;

/**
 * Optional self-description for diagnostics. Lets {@code diag.Diagnostics}
 * report backend-specific detail (e.g. the vector species and lane count)
 * without ever importing {@code jdk.incubator.vector} itself: the interface
 * only declares a plain {@link String}, so implementing/consuming it never
 * forces resolution of the incubator module.
 */
public interface SelfDescribing {
    String describe();
}
