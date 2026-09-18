package xyz.blanchot.vectorx.selftest;

import xyz.blanchot.vectorx.kernel.DensityBinaryKernels;
import xyz.blanchot.vectorx.kernel.DensityBinaryOp;

import java.util.Random;

/**
 * Fast differential self-test, run once at startup (if {@code selfTest} is
 * enabled) to compare a candidate vector {@link DensityBinaryKernels} backend
 * against the scalar reference before trusting it.
 *
 * <p>Covers both entry points for every op -- including the four the Mixins
 * never dispatch, since the kernel contract promises them -- at empty, size 1,
 * below/at/not-a-multiple-of the SIMD width, and with
 * {@code length < values.length} (the normal shape of a pooled 26.3
 * {@code ScopedDensityBuffer}, whose capacity exceeds its live {@code size()}).
 *
 * <p>The value set deliberately pairs {@code +0.0f} with {@code -0.0f} and
 * includes NaN on both sides of every comparison. That is the specific trap
 * {@code MIN}/{@code MAX} carry: vanilla writes conditionally rather than
 * calling {@code Math.min}, and the two disagree on signed zeros, so a vector
 * backend that reached for {@code VectorOperators.MIN} would pass a
 * random-data test and still corrupt terrain.
 */
public final class DensityBinarySelfTest {

    private static final long SEED = 0x56454354_4F52_59L;
    private static final float TAIL_CANARY = 4321.5F;
    private static final int[] SIZES = {0, 1, 3, 8, 16, 17, 64, 257};
    private static final float[] CONST_OPERANDS = {0.0F, -0.0F, 1.0F, -1.0F, 0.5F, Float.NaN, Float.POSITIVE_INFINITY};

    private DensityBinarySelfTest() {
    }

    public static Result run(DensityBinaryKernels scalar, DensityBinaryKernels vector) {
        try {
            for (DensityBinaryOp op : DensityBinaryOp.values()) {
                checkBuffer(op, scalar, vector);
                for (float operand : CONST_OPERANDS) {
                    checkConst(op, operand, scalar, vector);
                }
            }
            return Result.ok();
        } catch (SelfTestFailure e) {
            return Result.fail(e.getMessage());
        } catch (RuntimeException e) {
            return Result.fail("unexpected exception: " + e);
        }
    }

    private static float[] interestingValues(int count, long seed) {
        float[] fixed = {
                0.0F, -0.0F, -0.0F, 0.0F, 1.0F, -1.0F, 0.5F, -0.5F,
                Float.MIN_VALUE, -Float.MIN_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN, 2.0F, -2.0F
        };
        float[] values = new float[count];
        Random random = new Random(seed);
        for (int i = 0; i < count; i++) {
            values[i] = i < fixed.length
                    ? fixed[i]
                    : (float) ((random.nextDouble() - 0.5) * Math.pow(10, random.nextInt(12) - 6));
        }
        return values;
    }

    /**
     * Builds a buffer of {@code size + 5} floats whose tail is a canary,
     * reproducing the "capacity larger than live size" shape of a pooled
     * density buffer.
     */
    private static float[] source(int size, long seed) {
        float[] source = interestingValues(size + 5, seed);
        for (int i = size; i < source.length; i++) {
            source[i] = TAIL_CANARY;
        }
        return source;
    }

    private static void checkBuffer(DensityBinaryOp op, DensityBinaryKernels scalar, DensityBinaryKernels vector) {
        for (int size : SIZES) {
            float[] src = source(size, SEED + op.ordinal());
            // A second, independently seeded operand, so the two sides disagree
            // lane by lane and the comparison actually decides something.
            float[] right = source(size, SEED + 991 + op.ordinal());
            float[] scalarOut = src.clone();
            float[] vectorOut = src.clone();
            scalar.applyBuffer(scalarOut, right, size, op);
            vector.applyBuffer(vectorOut, right, size, op);
            compare("buffer op=" + op, size, scalarOut, vectorOut);
        }
    }

    private static void checkConst(DensityBinaryOp op, float operand,
                                   DensityBinaryKernels scalar, DensityBinaryKernels vector) {
        for (int size : SIZES) {
            float[] src = source(size, SEED + op.ordinal() + Float.floatToIntBits(operand));
            float[] scalarOut = src.clone();
            float[] vectorOut = src.clone();
            scalar.applyConst(scalarOut, size, op, operand);
            vector.applyConst(vectorOut, size, op, operand);
            compare("const op=" + op + " operand=" + operand, size, scalarOut, vectorOut);
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
     * Bitwise, so NaN compares equal to NaN and {@code +0.0f} does <em>not</em>
     * compare equal to {@code -0.0f} -- the whole point of this test.
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
