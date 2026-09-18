package xyz.blanchot.vectorx.selftest;

import xyz.blanchot.vectorx.kernel.ClampKernels;

import java.util.Random;

/**
 * Fast differential self-test, run once at startup (if {@code selfTest} is
 * enabled) to compare a candidate vector {@link ClampKernels} backend
 * against the scalar reference before trusting it. Mirrors
 * {@link DensityMapSelfTest}'s shape and coverage philosophy.
 *
 * <p>Coverage: empty range, size 1, below/at/not-a-multiple-of the SIMD
 * width, {@code +-0.0f}, {@code +-Infinity}, {@code NaN}, values at and
 * around several real bound pairs (including the exact {@code [-1, 1]} and
 * {@code [-100, 80]} bounds used in the real vanilla noise router), and
 * deterministic random magnitudes across a wide exponent range.
 *
 * <p>It also covers {@code length < values.length}, which is the normal
 * case in 26.3: a {@code ScopedDensityBuffer} is handed out by a pooled
 * arena whose capacity exceeds the live {@code size()}, and the kernel must
 * leave the trailing pool slots untouched.
 */
public final class ClampSelfTest {

    private static final long SEED = 0x56454354_4F52_58L;
    private static final float[][] BOUNDS = {{-1.0F, 1.0F}, {-100.0F, 80.0F}, {0.0F, 1.0F}, {-5.5F, 5.5F}};
    private static final float TAIL_CANARY = 1234.5F;

    private ClampSelfTest() {
    }

    public static Result run(ClampKernels scalar, ClampKernels vector) {
        try {
            for (float[] bound : BOUNDS) {
                check(bound[0], bound[1], scalar, vector);
            }
            return Result.ok();
        } catch (SelfTestFailure e) {
            return Result.fail(e.getMessage());
        } catch (RuntimeException e) {
            return Result.fail("unexpected exception: " + e);
        }
    }

    private static float[] interestingValues(int extraRandomCount, long seed) {
        float[] fixed = {
                0.0F, -0.0F, 1.0F, -1.0F, 0.5F, -0.5F, 2.0F, -2.0F, 0.999999F, -0.999999F, 1.000001F, -1.000001F,
                Float.MIN_VALUE, -Float.MIN_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN
        };
        float[] values = new float[fixed.length + extraRandomCount];
        System.arraycopy(fixed, 0, values, 0, fixed.length);
        Random random = new Random(seed);
        for (int i = fixed.length; i < values.length; i++) {
            values[i] = (float) ((random.nextDouble() - 0.5) * Math.pow(10, random.nextInt(12) - 6));
        }
        return values;
    }

    private static void check(float min, float max, ClampKernels scalar, ClampKernels vector) {
        int[] sizes = {0, 1, 3, 8, 16, 17, 64, 257};
        for (int size : sizes) {
            // Over-allocate by a fixed margin and fill the tail with a canary, so
            // the "capacity > size" shape a pooled DensityBuffer really has is
            // exercised: both backends must stop exactly at `size`.
            int capacity = size + 5;
            float[] base = interestingValues(Math.max(0, capacity - 19), SEED + Float.floatToIntBits(min));
            float[] source = new float[capacity];
            System.arraycopy(base, 0, source, 0, Math.min(capacity, base.length));
            for (int i = size; i < capacity; i++) {
                source[i] = TAIL_CANARY;
            }

            float[] scalarOut = source.clone();
            float[] vectorOut = source.clone();
            scalar.clampInPlace(scalarOut, size, min, max);
            vector.clampInPlace(vectorOut, size, min, max);

            for (int i = 0; i < capacity; i++) {
                if (!bitwiseEquals(scalarOut[i], vectorOut[i])) {
                    throw new SelfTestFailure("bounds=" + min + ".." + max + " size=" + size + " index=" + i
                            + ": scalar=" + scalarOut[i] + " vector=" + vectorOut[i]);
                }
                if (i >= size && !bitwiseEquals(vectorOut[i], TAIL_CANARY)) {
                    throw new SelfTestFailure("bounds=" + min + ".." + max + " size=" + size
                            + ": wrote past length at index " + i + " (" + vectorOut[i] + ")");
                }
            }
        }
    }

    /**
     * NaN must compare equal to NaN here (both backends must produce NaN for the same inputs), unlike {@code ==}.
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
