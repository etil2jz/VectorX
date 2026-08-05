package xyz.blanchot.vectorx.diag;

import xyz.blanchot.vectorx.VectorXConfig;
import xyz.blanchot.vectorx.compat.CompatibilityRegistry;
import xyz.blanchot.vectorx.dispatch.CarverSkipDispatcher;
import xyz.blanchot.vectorx.dispatch.ClampDispatcher;
import xyz.blanchot.vectorx.dispatch.DensityMapDispatcher;
import xyz.blanchot.vectorx.dispatch.PackedBitsDispatcher;
import xyz.blanchot.vectorx.dispatch.VectorModuleProbe;
import xyz.blanchot.vectorx.kernel.SelfDescribing;

import java.util.List;

/**
 * Optional, human-readable startup report. {@link #oneLineSummary} is always
 * logged; {@link #fullReport} only when {@code diagnostics: true} in config.
 */
public final class Diagnostics {

    private Diagnostics() {
    }

    public static String oneLineSummary(DensityMapDispatcher densityMapDispatcher, ClampDispatcher clampDispatcher,
                                        PackedBitsDispatcher packedBitsDispatcher, CarverSkipDispatcher carverSkipDispatcher) {
        int vectorCount = 0;
        if (densityMapDispatcher.isVector()) vectorCount++;
        if (clampDispatcher.isVector()) vectorCount++;
        if (packedBitsDispatcher.isVector()) vectorCount++;
        if (carverSkipDispatcher.isVector()) vectorCount++;
        return "VectorX ready: " + vectorCount + "/4 kernels on the vector backend";
    }

    public static String fullReport(VectorXConfig config, DensityMapDispatcher densityMapDispatcher,
                                    ClampDispatcher clampDispatcher, PackedBitsDispatcher packedBitsDispatcher,
                                    CarverSkipDispatcher carverSkipDispatcher, ClassLoader loader,
                                    CompatibilityRegistry registry, List<String> loadedModIds) {
        StringBuilder sb = new StringBuilder();
        sb.append("VectorX diagnostics:\n");
        sb.append("  module in boot layer: ").append(VectorModuleProbe.isModuleInBootLayer()).append('\n');
        sb.append("  probe class resolvable via ").append(loader).append(": ")
                .append(VectorModuleProbe.canResolveProbeClass(loader)).append('\n');
        sb.append("  architecture: ").append(System.getProperty("os.arch")).append('\n');
        sb.append("  selfTest enabled: ").append(config.selfTestEnabled()).append('\n');

        appendKernelLine(sb, DensityMapDispatcher.CONFIG_KEY, densityMapDispatcher.isVector(),
                densityMapDispatcher.disableReason(), densityMapDispatcher.backend());
        appendKernelLine(sb, ClampDispatcher.CONFIG_KEY, clampDispatcher.isVector(),
                clampDispatcher.disableReason(), clampDispatcher.backend());
        appendKernelLine(sb, PackedBitsDispatcher.CONFIG_KEY, packedBitsDispatcher.isVector(),
                packedBitsDispatcher.disableReason(), packedBitsDispatcher.backend());
        appendKernelLine(sb, CarverSkipDispatcher.CONFIG_KEY, carverSkipDispatcher.isVector(),
                carverSkipDispatcher.disableReason(), carverSkipDispatcher.backend());

        sb.append("  known compatibility entries: ").append(registry.knownConflicts().size()).append('\n');
        for (String modId : loadedModIds) {
            registry.conflictingKernel(modId).ifPresent(kernel -> sb.append("  potential conflict: mod ")
                    .append(modId).append(" is known to transform the ").append(kernel).append(" hook\n"));
        }

        return sb.toString();
    }

    private static void appendKernelLine(StringBuilder sb, String configKey, boolean vector, String disableReason,
                                         Object backend) {
        sb.append("  kernel ").append(configKey).append(": ").append(vector ? "vector" : "scalar");
        if (!vector) {
            sb.append(" (reason: ").append(disableReason).append(')');
        } else if (backend instanceof SelfDescribing sd) {
            sb.append(" [").append(sd.describe()).append(']');
        }
        sb.append('\n');
    }
}
