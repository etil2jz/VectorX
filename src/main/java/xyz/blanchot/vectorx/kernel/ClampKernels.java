package xyz.blanchot.vectorx.kernel;

/**
 * In-place element-wise clamp of a {@code double[]}, matching
 * {@code net.minecraft.util.Mth.clamp(double, double, double)} as used by
 * real Minecraft's {@code DensityFunctions.Clamp}. Must not import
 * {@code jdk.incubator.vector} -- only {@code kernel.simd} may.
 */
public interface ClampKernels {
    void clampInPlace(double[] values, double min, double max);
}
