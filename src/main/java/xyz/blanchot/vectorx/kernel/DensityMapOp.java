package xyz.blanchot.vectorx.kernel;

/**
 * An independent, standalone reproduction of the element-wise transforms
 * real Minecraft 26.3 exposes through
 * {@code net.minecraft.world.level.levelgen.densityfunction.op.UnaryFunction.Type}
 * -- {@code UnaryFunction.Type} itself is never referenced here, so this enum
 * stays loadable in a plain JVM with no Minecraft on the classpath (the
 * dispatcher self-test relies on that).
 *
 * <p>26.3 renamed {@code DensityFunctions.Mapped.Type.INVERT} to
 * {@code UnaryFunction.Type.RECIPROCAL} and added four new members:
 * {@code SQRT}, {@code NEGATE}, {@code LOG} and {@code SIGN}. This enum
 * follows the rename but deliberately does <em>not</em> model the four new
 * members:
 * <ul>
 *   <li>VectorX only ever dispatches {@code HALF_NEGATIVE},
 *       {@code QUARTER_NEGATIVE} and {@code SQUEEZE} through a Mixin; the
 *       remaining members exist purely as differential-test and self-test
 *       coverage for the kernel contract, and adding members that are never
 *       dispatched buys nothing;</li>
 *   <li>{@code LOG} in particular cannot be modelled without breaking this
 *       project's hard bit-exactness invariant: Mojang's {@code LogSampler}
 *       computes {@code (float) Math.log(v)}, and {@code VectorOperators.LOG}
 *       is specified only to ~1 ulp accuracy, so a vector backend could not
 *       be proven bit-identical to the scalar reference. {@code SIGN}
 *       ({@code Math.signum}) is in the same family of fiddly special-case
 *       semantics for no measured gain.</li>
 * </ul>
 * Members omitted here are simply never vectorized: the Mixin does not target
 * their sampler classes at all, so real Mojang code runs unmodified.
 */
public enum DensityMapOp {
    /** {@code UnaryFunction$AbsSampler}: {@code Math.abs(v)}. */
    ABS,
    /** {@code UnaryFunction$SquareSampler}: {@code Mth.square(v)} == {@code v * v}. */
    SQUARE,
    /** {@code UnaryFunction$CubeSampler}: {@code Mth.cube(v)} == {@code (v * v) * v}. */
    CUBE,
    /** {@code UnaryFunction$LeakyReLUSampler} with {@code negativeFactor = 0.5F}. */
    HALF_NEGATIVE,
    /** {@code UnaryFunction$LeakyReLUSampler} with {@code negativeFactor = 0.25F}. */
    QUARTER_NEGATIVE,
    /** {@code UnaryFunction$ReciprocalSampler}: {@code 1.0F / v}. Was {@code INVERT} before 26.3. */
    RECIPROCAL,
    /** {@code UnaryFunction$SqueezeSampler}. */
    SQUEEZE
}
