package xyz.blanchot.vectorx.kernel.scalar;

import xyz.blanchot.vectorx.kernel.DensityBinaryKernels;
import xyz.blanchot.vectorx.kernel.DensityBinaryOp;
import xyz.blanchot.vectorx.kernel.SelfDescribing;

import java.util.Objects;

public final class ScalarDensityBinaryKernels implements DensityBinaryKernels, SelfDescribing {

    public static final ScalarDensityBinaryKernels INSTANCE = new ScalarDensityBinaryKernels();

    private ScalarDensityBinaryKernels() {
    }

    public static float combine(DensityBinaryOp op, float left, float right) {
        return switch (op) {
            case ADD -> left + right;
            case SUB -> left - right;
            case MUL -> left * right;
            case DIV -> left / right;
            case MIN -> right < left ? right : left;
            case MAX -> right > left ? right : left;
        };
    }

    public static float combineConst(DensityBinaryOp op, float value, float operand) {
        return switch (op) {
            case ADD -> value + operand;
            case SUB -> operand - value;
            case MUL -> value * operand;
            case DIV -> operand / value;
            case MIN -> operand < value ? operand : value;
            case MAX -> operand > value ? operand : value;
        };
    }

    @Override
    public void applyBuffer(float[] values, float[] right, int length, DensityBinaryOp op) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(op, "op");
        Objects.checkFromIndexSize(0, length, values.length);
        Objects.checkFromIndexSize(0, length, right.length);
        for (int i = 0; i < length; i++) {
            values[i] = combine(op, values[i], right[i]);
        }
    }

    @Override
    public void applyConst(float[] values, int length, DensityBinaryOp op, float operand) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(op, "op");
        Objects.checkFromIndexSize(0, length, values.length);
        for (int i = 0; i < length; i++) {
            values[i] = combineConst(op, values[i], operand);
        }
    }

    @Override
    public String describe() {
        return "scalar reference backend for density-function element-wise binary ops";
    }
}
