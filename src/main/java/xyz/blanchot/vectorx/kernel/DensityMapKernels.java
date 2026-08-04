package xyz.blanchot.vectorx.kernel;

/**
 * In-place element-wise transform of a {@code double[]}, matching the
 * semantics of real Minecraft's {@code DensityFunctions.Mapped} /
 * {@code PureTransformer.fillArray}. Must not import
 * {@code jdk.incubator.vector} -- only {@code kernel.simd} may.
 */
public interface DensityMapKernels {
    void apply(double[] values, DensityMapOp op);
}
