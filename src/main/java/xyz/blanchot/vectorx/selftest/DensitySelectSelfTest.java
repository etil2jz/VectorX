package xyz.blanchot.vectorx.selftest;

import xyz.blanchot.vectorx.kernel.DensitySelectKernels;

import java.util.Random;

/**
 * Fast differential self-test, run once at startup (if {@code selfTest} is
 * enabled) to compare a candidate vector {@link DensitySelectKernels} backend
 * against the scalar reference before trusting it.
 *
 * <p>Covers all three entry points at empty, size 1, below/at/not-a-multiple-of
 * the SIMD width, and with {@code length < values.length} (the normal shape of
 * a pooled 26.3 {@code ScopedDensityBuffer}).
 *
 * <p>The alpha values fed to {@code lerp} deliberately include exact
 * {@code 0.0f}, {@code -0.0f} and {@code 1.0f}: those are the two branches
 * vanilla special-cases, and they are semantic rather than an optimization,
 * since {@code first + 1.0F * (second - first)} is not {@code second} for
 * every pair of floats. NaN is included on every comparison so a lane that
 * falls through every branch is exercised too.
 */
public final class DensitySelectSelfTest {

    private static final long SEED = 0x56454354_4F52_5AL;
    private static final float TAIL_CANARY = 8765.5F;
    private static final int[] SIZES = {0, 1, 3, 8, 16, 17, 64, 257};
    private static final float[][] RANGES = {
            {0.2F, 0.8F}, {-1.0F, 1.0F}, {0.0F, 0.0F}, {-0.0F, 0.0F}, {Float.NaN, 1.0F}
    };

    private DensitySelectSelfTest() {
    }

    public static Result run(DensitySelectKernels scalar, DensitySelectKernels vector) {
        try {
            checkLerp(scalar, vector);
            for (float[] range : RANGES) {
                checkRangeChoiceConst(range[0], range[1], scalar, vector);
                checkRangeChoice(range[0], range[1], scalar, vector);
            }
            return Result.ok();
        } catch (SelfTestFailure e) {
            return Result.fail(e.getMessage());
        } catch (RuntimeException e) {
            return Result.fail("unexpected exception: " + e);
        }
    }

    /** Alphas weighted towards the two special-cased constants. */
    private static float[] alphas(int count, long seed) {
        float[] fixed = {
                0.0F, -0.0F, 1.0F, 0.5F, -1.0F, 2.0F, Float.NaN,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.MIN_VALUE
        };
        float[] values = new float[count];
        Random random = new Random(seed);
        for (int i = 0; i < count; i++) {
            if (i < fixed.length) {
                values[i] = fixed[i];
            } else {
                int bucket = random.nextInt(6);
                values[i] = bucket == 0 ? 0.0F : bucket == 1 ? 1.0F : (float) random.nextDouble();
            }
        }
        return values;
    }

    private static float[] operands(int count, long seed) {
        float[] fixed = {
                0.0F, -0.0F, 1.0F, -1.0F, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                Float.MAX_VALUE, -Float.MAX_VALUE
        };
        float[] values = new float[count];
        Random random = new Random(seed);
        for (int i = 0; i < count; i++) {
            values[i] = i < fixed.length
                    ? fixed[i]
                    : (float) ((random.nextDouble() - 0.5) * Math.pow(10, random.nextInt(10) - 5));
        }
        return values;
    }

    private static float[] withCanaryTail(float[] values, int size) {
        for (int i = size; i < values.length; i++) {
            values[i] = TAIL_CANARY;
        }
        return values;
    }

    private static void checkLerp(DensitySelectKernels scalar, DensitySelectKernels vector) {
        for (int size : SIZES) {
            float[] src = withCanaryTail(alphas(size + 5, SEED), size);
            float[] first = operands(size + 5, SEED + 17);
            float[] second = operands(size + 5, SEED + 31);
            float[] scalarOut = src.clone();
            float[] vectorOut = src.clone();
            scalar.lerp(scalarOut, first, second, size);
            vector.lerp(vectorOut, first, second, size);
            compare("lerp", size, scalarOut, vectorOut);
        }
    }

    private static void checkRangeChoiceConst(float min, float max,
                                              DensitySelectKernels scalar, DensitySelectKernels vector) {
        for (int size : SIZES) {
            float[] src = withCanaryTail(alphas(size + 5, SEED + 3), size);
            float[] scalarOut = src.clone();
            float[] vectorOut = src.clone();
            scalar.rangeChoiceConst(scalarOut, size, min, max, 7.5F, -7.5F);
            vector.rangeChoiceConst(vectorOut, size, min, max, 7.5F, -7.5F);
            compare("rangeChoiceConst [" + min + ", " + max + ")", size, scalarOut, vectorOut);
        }
    }

    private static void checkRangeChoice(float min, float max,
                                         DensitySelectKernels scalar, DensitySelectKernels vector) {
        for (int size : SIZES) {
            float[] src = withCanaryTail(operands(size + 5, SEED + 5), size);
            float[] input = alphas(size + 5, SEED + 7);
            float[] outOfRange = operands(size + 5, SEED + 11);
            float[] scalarOut = src.clone();
            float[] vectorOut = src.clone();
            scalar.rangeChoice(scalarOut, input, outOfRange, size, min, max);
            vector.rangeChoice(vectorOut, input, outOfRange, size, min, max);
            compare("rangeChoice [" + min + ", " + max + ")", size, scalarOut, vectorOut);
        }
    }

    private static void compare(String what, int size, float[] scalarOut, float[] vectorOut) {
        for (int i = 0; i < scalarOut.length; i++) {
            if (!bitwiseEquals(scalarOut[i], vectorOut[i])) {
                throw new SelfTestFailure(what + " size=" + size + " index=" + i
                        + ": scalar=" + scalarOut[i] + " vector=" + vectorOut[i]);
            }
            if (i >= size && !bitwiseEquals(vectorOut[i], TAIL_CANARY)) {
                throw new SelfTestFailure(what + " size=" + size
                        + ": wrote past length at index " + i + " (" + vectorOut[i] + ")");
            }
        }
    }

    /**
     * Bitwise, so NaN compares equal to NaN and {@code +0.0f} does not compare
     * equal to {@code -0.0f}.
     */
    private static boolean bitwiseEquals(float a, float b) {
        return Float.floatToIntBits(a) == Float.floatToIntBits(b);
    }

    public record Result(boolean passed, String failureDescription) {
        static Result ok() {
            return new Result(true, null);
        }

        static Result fail(String description) {
            return new Result(false, description);
        }
    }

    private static final class SelfTestFailure extends RuntimeException {
        SelfTestFailure(String message) {
            super(message);
        }
    }
}
