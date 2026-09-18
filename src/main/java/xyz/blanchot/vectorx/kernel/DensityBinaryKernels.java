package xyz.blanchot.vectorx.kernel;

/**
 * In-place element-wise binary combination of the first {@code length}
 * elements of a {@code float[]}, matching the sampler records nested in real
 * Minecraft 26.3's
 * {@code net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction}.
 *
 * <p>See {@link ClampKernels} for why {@code length} is passed explicitly
 * rather than read from {@code values.length}: the array is a real
 * {@code DensityBuffer}'s backing storage, and a pooled
 * {@code ScopedDensityBuffer}'s capacity is generally larger than its live
 * {@code size()}. This applies to the right-hand buffer too -- both come from
 * the same arena, so neither may be read or written past {@code length}.
 *
 * <p><b>{@code MIN} and {@code MAX} are conditional writes, not
 * {@code Math.min}/{@code Math.max}.</b> Vanilla spells them
 * {@code if (candidate < current) current = candidate}, and that is
 * observably different from {@code Math.min} on signed zeros: with
 * {@code current = +0.0F} and {@code candidate = -0.0F}, the comparison
 * {@code -0.0F < +0.0F} is false (IEEE-754 treats the two zeros as equal), so
 * vanilla keeps {@code +0.0F}, whereas {@code Math.min} is specified to
 * return {@code -0.0F}. Implementations must reproduce the comparison, not
 * substitute the library function -- the same class of bit-exactness trap as
 * the precomputed-reciprocal bug in {@link DensityMapKernels}'s {@code SQUEEZE}.
 *
 * <p>Must not import {@code jdk.incubator.vector} -- only {@code kernel.simd} may.
 */
public interface DensityBinaryKernels {

    /**
     * Combines {@code values[i]} with {@code right[i]} for
     * {@code i in [0, length)}, writing the result back into {@code values}.
     * {@code values} is vanilla's left-hand operand (the output buffer the
     * left child already filled), {@code right} the scratch buffer the right
     * child filled.
     */
    void applyBuffer(float[] values, float[] right, int length, DensityBinaryOp op);

    /**
     * Combines {@code values[i]} with the compile-time constant
     * {@code operand} for {@code i in [0, length)}.
     *
     * <p>Operand placement follows vanilla and is <em>not</em> symmetric for
     * {@link DensityBinaryOp#SUB} and {@link DensityBinaryOp#DIV}: those
     * compute {@code operand - values[i]} and {@code operand / values[i]}.
     * See {@link DensityBinaryOp} for why there is no other order.
     */
    void applyConst(float[] values, int length, DensityBinaryOp op, float operand);
}
