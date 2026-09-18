package xyz.blanchot.vectorx.dispatch;

import xyz.blanchot.vectorx.VectorXConfig;
import xyz.blanchot.vectorx.VectorXConfig.KernelMode;
import xyz.blanchot.vectorx.diag.VectorXLog;
import xyz.blanchot.vectorx.kernel.DensityBinaryKernels;
import xyz.blanchot.vectorx.kernel.scalar.ScalarDensityBinaryKernels;
import xyz.blanchot.vectorx.selftest.DensityBinarySelfTest;

import java.util.Objects;

public final class MinMaxDispatcher implements KernelDispatcher {

    public static final String CONFIG_KEY = "densityFunctionMinMax";
    private static final String SIMD_CLASS_NAME = "xyz.blanchot.vectorx.kernel.simd.SimdDensityBinaryKernels";
    private static final String SIMD_INSTANCE_FIELD = "INSTANCE";

    private final DensityBinaryKernels backend;
    private final boolean vector;
    private final String disableReason;

    public MinMaxDispatcher(VectorXConfig config, VectorXLog log) {
        this(config, log, MinMaxDispatcher.class.getClassLoader());
    }

    MinMaxDispatcher(VectorXConfig config, VectorXLog log, ClassLoader loader) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(log, "log");

        String reason = globalDisableReason(config);
        if (reason == null) {
            DensityBinaryKernels candidate = tryLoadVectorBackend(loader, log);
            if (candidate == null) {
                reason = "failed to load " + SIMD_CLASS_NAME;
            } else {
                KernelMode mode = config.modeFor(CONFIG_KEY);
                if (mode == KernelMode.SCALAR || mode == KernelMode.OFF) {
                    reason = "config " + CONFIG_KEY + "=\"" + mode.configValue() + "\"";
                } else if (config.selfTestEnabled()) {
                    DensityBinarySelfTest.Result result = DensityBinarySelfTest.run(ScalarDensityBinaryKernels.INSTANCE, candidate);
                    if (!result.passed()) {
                        reason = "self-test failed: " + result.failureDescription();
                        log.warn("kernel " + CONFIG_KEY + " falling back to scalar (" + reason + ")");
                    }
                }
            }
            if (reason == null) {
                this.backend = candidate;
                this.vector = true;
                this.disableReason = null;
                log.info(CONFIG_KEY + " using vector backend (" + SIMD_CLASS_NAME + ")");
                return;
            }
        }

        this.backend = ScalarDensityBinaryKernels.INSTANCE;
        this.vector = false;
        this.disableReason = reason;
        log.info(CONFIG_KEY + " using scalar backend (" + reason + ")");
    }

    private static String globalDisableReason(VectorXConfig config) {
        if (Boolean.getBoolean(VectorModuleProbe.FORCE_SCALAR_PROPERTY)) {
            return "system property " + VectorModuleProbe.FORCE_SCALAR_PROPERTY + "=true";
        }
        if (config.backendForcedScalar()) {
            return "config backendForcedScalar=true";
        }
        if (!VectorModuleProbe.isModuleInBootLayer()) {
            return "jdk.incubator.vector is not present in the boot module layer";
        }
        return null;
    }

    private static DensityBinaryKernels tryLoadVectorBackend(ClassLoader loader, VectorXLog log) {
        try {
            Class<?> simdClass = Class.forName(SIMD_CLASS_NAME, true, loader);
            Object instance = simdClass.getField(SIMD_INSTANCE_FIELD).get(null);
            return (DensityBinaryKernels) instance;
        } catch (LinkageError | ReflectiveOperationException | ClassCastException e) {
            log.warn(CONFIG_KEY + ": failed to load " + SIMD_CLASS_NAME + ": " + e);
            return null;
        }
    }

    public DensityBinaryKernels backend() {
        return backend;
    }

    @Override
    public String configKey() {
        return CONFIG_KEY;
    }

    @Override
    public boolean isVector() {
        return vector;
    }

    @Override
    public String disableReason() {
        return disableReason;
    }
}
