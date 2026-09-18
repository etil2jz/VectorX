package xyz.blanchot.vectorx.kernel;

/**
 * Element-wise <em>data-dependent selection</em> over density buffers,
 * matching the sampler records nested in real Minecraft 26.3's
 * {@code op.LerpFunction} and {@code op.RangeChoiceFunction}.
 *
 * <p>These are grouped together because they share the property that makes
 * them worth vectorizing at all: unlike the arithmetic in
 * {@link DensityBinaryKernels}' {@code ADD}/{@code MUL}/{@code DIV}, every
 * loop here carries a branch whose outcome depends on the data, which C2
 * cannot auto-vectorize. Replacing the branch with a lane mask and a
 * {@code blend} is where the measured gain comes from.
 *
 * <p>All buffers come from the same arena as the output, so none may be read
 * or written past {@code length}; see {@link ClampKernels}.
 *
 * <p>Must not import {@code jdk.incubator.vector} -- only {@code kernel.simd} may.
 */
public interface DensitySelectKernels {

    /**
     * Mirrors {@code LerpFunction$Sampler}: {@code values} arrives holding the
     * alpha the {@code alpha} child produced, and leaves holding the result.
     *
     * <p>The {@code alpha == 0} and {@code alpha == 1} special cases are
     * semantic, not an optimization: {@code first + 1.0F * (second - first)}
     * is not {@code second} for every pair of floats, so collapsing them to a
     * plain lerp would change generated terrain. They are reproduced exactly.
     */
    void lerp(float[] values, float[] first, float[] second, int length);

    /**
     * Mirrors {@code RangeChoiceFunction$ConstSampler}: replaces each element
     * with {@code whenInRange} if it lies in
     * {@code [minInclusive, maxExclusive)}, and with {@code whenOutOfRange}
     * otherwise. NaN is out of range on both backends, since every IEEE-754
     * comparison against it is false.
     */
    void rangeChoiceConst(float[] values, int length,
                          float minInclusive, float maxExclusive,
                          float whenInRange, float whenOutOfRange);

    /**
     * Mirrors {@code RangeChoiceFunction$Sampler}: {@code values} arrives
     * holding what the {@code whenInRange} child produced and is overwritten
     * from {@code whenOutOfRange} wherever the corresponding {@code input}
     * element falls outside {@code [minInclusive, maxExclusive)}.
     */
    void rangeChoice(float[] values, float[] input, float[] whenOutOfRange, int length,
                     float minInclusive, float maxExclusive);
}
