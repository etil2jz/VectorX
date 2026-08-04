package xyz.blanchot.vectorx.dispatch;

/**
 * Detects whether {@code jdk.incubator.vector} is usable, WITHOUT ever
 * referencing any of its classes statically -- this class itself must always
 * load and run correctly even when the incubator module is entirely absent.
 *
 * <p>Two independent signals are exposed because they can legitimately
 * disagree under an unusual classloader topology:
 * <ul>
 *   <li>{@link #isModuleInBootLayer()} checks the JVM's module graph.</li>
 *   <li>{@link #canResolveProbeClass(ClassLoader)} checks whether a given
 *       class loader can actually resolve a class from the module -- a
 *       module can be present in the boot layer while a specific classloader
 *       still refuses to see it.</li>
 * </ul>
 */
public final class VectorModuleProbe {

    public static final String FORCE_SCALAR_PROPERTY = "vectorized.forceScalar";
    private static final String MODULE_NAME = "jdk.incubator.vector";
    private static final String PROBE_CLASS_NAME = "jdk.incubator.vector.IntVector";

    private VectorModuleProbe() {
    }

    public static boolean isModuleInBootLayer() {
        return ModuleLayer.boot().findModule(MODULE_NAME).isPresent();
    }

    public static boolean canResolveProbeClass(ClassLoader loader) {
        try {
            Class.forName(PROBE_CLASS_NAME, false, loader);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
