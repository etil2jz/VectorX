package xyz.blanchot.vectorx.selftest;

import xyz.blanchot.vectorx.kernel.DensityMapKernels;
import xyz.blanchot.vectorx.kernel.DensityMapOp;

import java.util.Random;

public final class DensityMapSelfTest {

    private static final long SEED = 0x56454354_4F52_58L;
    private static final float[] EXTRA_LEAKY_FACTORS = {0.5F, 0.25F, 0.0F, -0.0F, 1.0F, -3.25F, Float.NaN};
    private static final float TAIL_CANARY = 1234.5F;
    private static final int[] SIZES = {0, 1, 3, 8, 16, 17, 64, 257};

    private DensityMapSelfTest() {
    }

    public static Result run(DensityMapKernels scalar, DensityMapKernels vector) {
        try {
            for (DensityMapOp op : DensityMapOp.values()) {
                check(op, scalar, vector);
            }
            for (float factor : EXTRA_LEAKY_FACTORS) {
                checkLeakyReLU(factor, scalar, vector);
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

    private static float[] source(int size, long seed) {
        int capacity = size + 5;
        float[] base = interestingValues(Math.max(0, capacity - 19), seed);
        float[] source = new float[capacity];
        System.arraycopy(base, 0, source, 0, Math.min(capacity, base.length));
        for (int i = size; i < capacity; i++) {
            source[i] = TAIL_CANARY;
        }
        return source;
    }

    private static void check(DensityMapOp op, DensityMapKernels scalar, DensityMapKernels vector) {
        for (int size : SIZES) {
            float[] src = source(size, SEED + op.ordinal());
            float[] scalarOut = src.clone();
            float[] vectorOut = src.clone();
            scalar.apply(scalarOut, size, op);
            vector.apply(vectorOut, size, op);
            compare("op=" + op, size, scalarOut, vectorOut);
        }
    }

    private static void checkLeakyReLU(float negativeFactor, DensityMapKernels scalar, DensityMapKernels vector) {
        for (int size : SIZES) {
            float[] src = source(size, SEED + Float.floatToIntBits(negativeFactor));
            float[] scalarOut = src.clone();
            float[] vectorOut = src.clone();
            scalar.leakyReLU(scalarOut, size, negativeFactor);
            vector.leakyReLU(vectorOut, size, negativeFactor);
            compare("leakyReLU factor=" + negativeFactor, size, scalarOut, vectorOut);
        }
    }

    private static void compare(String what, int size, float[] scalarOut, float[] vectorOut) {
        for (int i = 0; i < scalarOut.length; i++) {
            if (!bitwiseEquals(scalarOut[i], vectorOut[i])) {
                throw new SelfTestFailure(what + " size=" + size + " index=" + i + ": scalar=" + scalarOut[i] + " vector=" + vectorOut[i]);
            }
            if (i >= size && !bitwiseEquals(vectorOut[i], TAIL_CANARY)) {
                throw new SelfTestFailure(what + " size=" + size + ": wrote past length at index " + i + " (" + vectorOut[i] + ")");
            }
        }
    }

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
