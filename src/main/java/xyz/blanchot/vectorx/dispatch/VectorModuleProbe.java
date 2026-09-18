package xyz.blanchot.vectorx.dispatch;

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
