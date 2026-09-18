package xyz.blanchot.vectorx.kernel;

/**
 * An independent, standalone reproduction of the element-wise binary
 * transforms real Minecraft 26.3 exposes through the sampler records nested in
 * {@code net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction}
 * -- {@code BinaryFunction.Type} itself is never referenced here, so this enum
 * stays loadable in a plain JVM with no Minecraft on the classpath.
 *
 * <p>Each member covers two vanilla sampler shapes, which
 * {@link DensityBinaryKernels} exposes as two separate entry points:
 * <ul>
 *   <li>a <em>two-buffer</em> form ({@code AddSampler}, {@code SubSampler},
 *       {@code MulSampler}, {@code DivSampler}, {@code MinSampler},
 *       {@code MaxSampler}), which samples its right-hand child into a
 *       scratch buffer borrowed from the arena and then combines the two
 *       element-wise;</li>
 *   <li>a <em>constant</em> form ({@code ConstAddSampler} and friends), where
 *       one operand was folded to a {@code float} at compile time.</li>
 * </ul>
 *
 * <p><b>Operand placement in the constant form is not symmetric, and follows
 * vanilla exactly.</b> {@code ConstAddSampler}, {@code ConstMulSampler},
 * {@code ConstMinSampler} and {@code ConstMaxSampler} hold the sampler on the
 * left and the constant on the right, but {@code ConstSubSampler} and
 * {@code ConstDivSampler} hold the <em>constant</em> on the left: they compute
 * {@code c - v} and {@code c / v}, never {@code v - c} or {@code v / c}.
 * Vanilla has no sampler for the other two orders (the compiler rewrites
 * {@code v - c} and {@code v / c} into {@code ConstAdd}/{@code ConstMul} with
 * a negated/reciprocal constant), so neither does this enum.
 */
public enum DensityBinaryOp {
    /** {@code AddSampler} / {@code ConstAddSampler}: {@code v + c}. */
    ADD,
    /**
     * {@code SubSampler} / {@code ConstSubSampler}. Two-buffer form is
     * {@code left - right}; constant form is {@code c - v}.
     */
    SUB,
    /** {@code MulSampler} / {@code ConstMulSampler}: {@code v * c}. */
    MUL,
    /**
     * {@code DivSampler} / {@code ConstDivSampler}. Two-buffer form is
     * {@code left / right}; constant form is {@code c / v}.
     */
    DIV,
    /**
     * {@code MinSampler} / {@code ConstMinSampler}. Vanilla writes
     * conditionally -- {@code if (candidate < current) current = candidate}
     * -- which is <em>not</em> {@code Math.min}: see
     * {@link DensityBinaryKernels} for why the difference is observable.
     */
    MIN,
    /**
     * {@code MaxSampler} / {@code ConstMaxSampler}. Same conditional-write
     * shape as {@link #MIN}, with the comparison reversed.
     */
    MAX
}
