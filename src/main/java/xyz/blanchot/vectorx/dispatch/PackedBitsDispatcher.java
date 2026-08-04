package xyz.blanchot.vectorx.dispatch;

import xyz.blanchot.vectorx.VectorXConfig;
import xyz.blanchot.vectorx.VectorXConfig.KernelMode;
import xyz.blanchot.vectorx.diag.VectorXLog;
import xyz.blanchot.vectorx.kernel.PackedBitsKernels;
import xyz.blanchot.vectorx.kernel.scalar.ScalarPackedBitsKernels;
import xyz.blanchot.vectorx.selftest.PackedBitsSelfTest;

import java.util.Objects;

/**
 * Fail-open resolver for the {@link PackedBitsKernels} backend.
 *
 * <p>Decision order (first match wins), evaluated once at construction time:
 * <ol>
 *   <li>system property {@code vectorized.forceScalar=true} -&gt; scalar;</li>
 *   <li>config {@code backendForcedScalar=true} -&gt; scalar;</li>
 *   <li>{@code jdk.incubator.vector} absent from the boot module layer -&gt; scalar;</li>
 *   <li>{@code SimdPackedBitsKernels} fails to load/link -&gt; scalar;</li>
 *   <li>config {@code packedStorageUnpack} is {@code "scalar"} or {@code "off"} -&gt; scalar;</li>
 *   <li>the differential self-test fails -&gt; scalar;</li>
 *   <li>otherwise -&gt; vector.</li>
 * </ol>
 */
public final class PackedBitsDispatcher {

    public static final String CONFIG_KEY = "packedStorageUnpack";
    private static final String SIMD_CLASS_NAME = "xyz.blanchot.vectorx.kernel.simd.SimdPackedBitsKernels";
    private static final String SIMD_INSTANCE_FIELD = "INSTANCE";

    private final PackedBitsKernels backend;
    private final boolean vector;
    private final String disableReason;

    public PackedBitsDispatcher(VectorXConfig config, VectorXLog log) {
        this(config, log, PackedBitsDispatcher.class.getClassLoader());
    }

    PackedBitsDispatcher(VectorXConfig config, VectorXLog log, ClassLoader loader) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(log, "log");

        String reason = globalDisableReason(config);
        if (reason == null) {
            PackedBitsKernels candidate = tryLoadVectorBackend(loader, log);
            if (candidate == null) {
                reason = "failed to load " + SIMD_CLASS_NAME;
            } else {
                KernelMode mode = config.modeFor(CONFIG_KEY);
                if (mode == KernelMode.SCALAR || mode == KernelMode.OFF) {
                    reason = "config " + CONFIG_KEY + "=\"" + mode.configValue() + "\"";
                } else if (config.selfTestEnabled()) {
                    PackedBitsSelfTest.Result result = PackedBitsSelfTest.run(ScalarPackedBitsKernels.INSTANCE, candidate);
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

        this.backend = ScalarPackedBitsKernels.INSTANCE;
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

    private static PackedBitsKernels tryLoadVectorBackend(ClassLoader loader, VectorXLog log) {
        try {
            Class<?> simdClass = Class.forName(SIMD_CLASS_NAME, true, loader);
            Object instance = simdClass.getField(SIMD_INSTANCE_FIELD).get(null);
            return (PackedBitsKernels) instance;
        } catch (LinkageError | ReflectiveOperationException | ClassCastException e) {
            log.warn(CONFIG_KEY + ": failed to load " + SIMD_CLASS_NAME + ": " + e);
            return null;
        }
    }

    public PackedBitsKernels backend() {
        return backend;
    }

    public boolean isVector() {
        return vector;
    }

    /**
     * Non-null only when currently on the scalar path.
     */
    public String disableReason() {
        return disableReason;
    }
}
