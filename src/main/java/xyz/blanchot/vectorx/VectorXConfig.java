package xyz.blanchot.vectorx;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class VectorXConfig {

    private static final KernelMode DEFAULT_MODE = KernelMode.AUTO;

    private final boolean backendForcedScalar;
    private final boolean selfTestEnabled;
    private final boolean diagnosticsEnabled;
    private final Map<String, KernelMode> kernelModes;

    private VectorXConfig(boolean backendForcedScalar, boolean selfTestEnabled, boolean diagnosticsEnabled, Map<String, KernelMode> kernelModes) {
        this.backendForcedScalar = backendForcedScalar;
        this.selfTestEnabled = selfTestEnabled;
        this.diagnosticsEnabled = diagnosticsEnabled;
        this.kernelModes = kernelModes;
    }

    public static VectorXConfig defaults() {
        Map<String, KernelMode> modes = new LinkedHashMap<>();
        modes.put("densityFunctionMap", KernelMode.AUTO);
        modes.put("densityFunctionClamp", KernelMode.AUTO);
        modes.put("packedStorageUnpack", KernelMode.AUTO);
        modes.put("densityFunctionMinMax", KernelMode.AUTO);
        modes.put("densityFunctionSelect", KernelMode.AUTO);
        modes.put("canyonCarverSkip", KernelMode.AUTO);
        return new VectorXConfig(false, true, false, modes);
    }

    public static VectorXConfig of(boolean backendForcedScalar, boolean selfTestEnabled, boolean diagnosticsEnabled, Map<String, KernelMode> kernelModes) {
        return new VectorXConfig(backendForcedScalar, selfTestEnabled, diagnosticsEnabled, new LinkedHashMap<>(kernelModes));
    }

    public VectorXConfig withBackendForcedScalar(boolean forced) {
        return new VectorXConfig(forced, selfTestEnabled, diagnosticsEnabled, kernelModes);
    }

    public VectorXConfig withKernelMode(String key, KernelMode mode) {
        Map<String, KernelMode> copy = new LinkedHashMap<>(kernelModes);
        copy.put(key, mode);
        return new VectorXConfig(backendForcedScalar, selfTestEnabled, diagnosticsEnabled, copy);
    }

    public boolean backendForcedScalar() {
        return backendForcedScalar;
    }

    public boolean selfTestEnabled() {
        return selfTestEnabled;
    }

    public boolean diagnosticsEnabled() {
        return diagnosticsEnabled;
    }

    public KernelMode modeFor(String key) {
        return kernelModes.getOrDefault(key, DEFAULT_MODE);
    }

    public enum KernelMode {
        AUTO, SCALAR, OFF;

        public String configValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
