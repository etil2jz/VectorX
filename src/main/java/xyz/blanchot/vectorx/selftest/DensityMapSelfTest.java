package xyz.blanchot.vectorx.selftest;

import xyz.blanchot.vectorx.kernel.DensityMapKernels;
import xyz.blanchot.vectorx.kernel.DensityMapOp;

import java.util.Random;

/**
 * Fast differential self-test, run once at startup (if {@code selfTest} is
 * enabled) to compare a candidate vector {@link DensityMapKernels} backend
 * against the scalar reference before trusting it.
 *
 * <p>Coverage per op: empty array, size 1, below/at/not-a-multiple-of the
 * SIMD width, {@code +-0.0}, {@code +-Infinity}, {@code NaN}, values at the
 * {@code +-1} clamp boundary (relevant to {@code SQUEEZE}), and deterministic
 * random magnitudes across a wide exponent range.
 */
public final class DensityMapSelfTest {

    private static final long SEED = 0x56454354_4F52_58L;

    private DensityMapSelfTest() {
    }

    public static Result run(DensityMapKernels scalar, DensityMapKernels vector) {
        try {
            for (DensityMapOp op : DensityMapOp.values()) {
                check(op, scalar, vector);
            }
            return Result.ok();
        } catch (SelfTestFailure e) {
            return Result.fail(e.getMessage());
        } catch (RuntimeException e) {
            return Result.fail("unexpected exception: " + e);
        }
    }

    private static double[] interestingValues(int extraRandomCount, long seed) {
        double[] fixed = {
                0.0, -0.0, 1.0, -1.0, 0.5, -0.5, 2.0, -2.0, 0.999999, -0.999999, 1.000001, -1.000001,
                Double.MIN_VALUE, -Double.MIN_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN
        };
        double[] values = new double[fixed.length + extraRandomCount];
        System.arraycopy(fixed, 0, values, 0, fixed.length);
        Random random = new Random(seed);
        for (int i = fixed.length; i < values.length; i++) {
            values[i] = (random.nextDouble() - 0.5) * Math.pow(10, random.nextInt(20) - 10);
        }
        return values;
    }

    private static void check(DensityMapOp op, DensityMapKernels scalar, DensityMapKernels vector) {
        int[] sizes = {0, 1, 3, 8, 17, 64, 257};
        for (int size : sizes) {
            double[] base = interestingValues(Math.max(0, size - 19), SEED + op.ordinal());
            double[] source = new double[size];
            System.arraycopy(base, 0, source, 0, Math.min(size, base.length));

            double[] scalarOut = source.clone();
            double[] vectorOut = source.clone();
            scalar.apply(scalarOut, op);
            vector.apply(vectorOut, op);

            for (int i = 0; i < size; i++) {
                if (!bitwiseEquals(scalarOut[i], vectorOut[i])) {
                    throw new SelfTestFailure("op=" + op + " size=" + size + " index=" + i
                            + ": scalar=" + scalarOut[i] + " vector=" + vectorOut[i]);
                }
            }
        }
    }

    /**
     * NaN must compare equal to NaN here (both backends must produce NaN for the same inputs), unlike {@code ==}.
     */
    private static boolean bitwiseEquals(double a, double b) {
        return Double.doubleToLongBits(a) == Double.doubleToLongBits(b);
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
