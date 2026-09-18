package xyz.blanchot.vectorx.kernel;

/**
 * In-place element-wise transform of the first {@code length} elements of a
 * {@code float[]}, matching the semantics of the per-op {@code DensitySampler}
 * classes nested in real Minecraft 26.3's
 * {@code net.minecraft.world.level.levelgen.densityfunction.op.UnaryFunction}
 * ({@code AbsSampler}, {@code SquareSampler}, {@code CubeSampler},
 * {@code LeakyReLUSampler}, {@code ReciprocalSampler}, {@code SqueezeSampler}).
 *
 * <p>See {@link ClampKernels} for why {@code length} is passed explicitly
 * rather than read from {@code values.length}.
 *
 * <p>Must not import {@code jdk.incubator.vector} -- only {@code kernel.simd} may.
 */
public interface DensityMapKernels {

    /**
     * Applies {@code op} to {@code values[0 .. length)}.
     */
    void apply(float[] values, int length, DensityMapOp op);

    /**
     * Applies real Minecraft 26.3's {@code UnaryFunction$LeakyReLUSampler}
     * transform -- {@code input > 0.0F ? input : input * negativeFactor} --
     * to {@code values[0 .. length)}, for an <em>arbitrary</em> factor.
     *
     * <p>{@code UnaryFunction.compileSampler} only ever builds this sampler
     * with {@code 0.5F} ({@code HALF_NEGATIVE}) or {@code 0.25F}
     * ({@code QUARTER_NEGATIVE}) -- verified in the decompiled 26.3 source --
     * and those two cases are equivalently reachable through
     * {@link #apply(float[], int, DensityMapOp)} with
     * {@link DensityMapOp#HALF_NEGATIVE} / {@link DensityMapOp#QUARTER_NEGATIVE}.
     * But {@code LeakyReLUSampler} is a {@code public record}, so its canonical
     * constructor is public and a third party can legally construct one with
     * any factor. This entry point exists so the Mixin never has to assume
     * the factor is one of the two vanilla values: it forwards whatever
     * {@code negativeFactor()} actually reports.
     */
    void leakyReLU(float[] values, int length, float negativeFactor);
}
