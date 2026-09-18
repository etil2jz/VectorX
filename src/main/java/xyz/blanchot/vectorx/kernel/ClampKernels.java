package xyz.blanchot.vectorx.kernel;

/**
 * In-place element-wise clamp of the first {@code length} elements of a
 * {@code float[]}, matching {@code net.minecraft.util.Mth.clamp(float, float, float)}
 * as used by real Minecraft 26.3's
 * {@code net.minecraft.world.level.levelgen.densityfunction.op.ClampFunction$Sampler}.
 *
 * <p>The explicit {@code length} is not redundant with {@code values.length}:
 * the backing array handed to this kernel is a real
 * {@code DensityBuffer}'s {@code float[] values}, and a
 * {@code ScopedDensityBuffer} comes from a pooled arena whose capacity is
 * generally <em>larger</em> than the live {@code size()} (verified in
 * 26.3's {@code ScopedDensityBuffer}, which sets {@code size} independently
 * of {@code super(capacity)}). Elements at or past {@code length} belong to
 * the pool, not to this sample, and must be left untouched.
 *
 * <p>Must not import {@code jdk.incubator.vector} -- only {@code kernel.simd} may.
 */
public interface ClampKernels {
    void clampInPlace(float[] values, int length, float min, float max);
}
