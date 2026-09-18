package xyz.blanchot.vectorx.selftest;

import xyz.blanchot.vectorx.kernel.DensityBinaryKernels;
import xyz.blanchot.vectorx.kernel.DensityBinaryOp;

import java.util.Random;

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
            values[i] = i < fixed.length ? fixed[i] : (float) ((random.nextDouble() - 0.5) * Math.pow(10, random.nextInt(12) - 6));
        }
        return values;
    }

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
            float[] right = source(size, SEED + 991 + op.ordinal());
            float[] scalarOut = src.clone();
            float[] vectorOut = src.clone();
            scalar.applyBuffer(scalarOut, right, size, op);
            vector.applyBuffer(vectorOut, right, size, op);
            compare("buffer op=" + op, size, scalarOut, vectorOut);
        }
    }

    private static void checkConst(DensityBinaryOp op, float operand, DensityBinaryKernels scalar, DensityBinaryKernels vector) {
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
