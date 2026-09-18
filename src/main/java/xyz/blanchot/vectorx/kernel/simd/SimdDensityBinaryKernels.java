package xyz.blanchot.vectorx.kernel.simd;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import xyz.blanchot.vectorx.kernel.DensityBinaryKernels;
import xyz.blanchot.vectorx.kernel.DensityBinaryOp;
import xyz.blanchot.vectorx.kernel.SelfDescribing;
import xyz.blanchot.vectorx.kernel.scalar.ScalarDensityBinaryKernels;

import java.util.Objects;

public final class SimdDensityBinaryKernels implements DensityBinaryKernels, SelfDescribing {

    public static final SimdDensityBinaryKernels INSTANCE = new SimdDensityBinaryKernels();

    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    private SimdDensityBinaryKernels() {
    }

    private static FloatVector combine(DensityBinaryOp op, FloatVector left, FloatVector right) {
        return switch (op) {
            case ADD -> left.add(right);
            case SUB -> left.sub(right);
            case MUL -> left.mul(right);
            case DIV -> left.div(right);
            case MIN -> left.blend(right, right.compare(VectorOperators.LT, left));
            case MAX -> left.blend(right, right.compare(VectorOperators.GT, left));
        };
    }

    private static FloatVector combineConst(DensityBinaryOp op, FloatVector v, float operand) {
        return switch (op) {
            case ADD -> v.add(operand);
            case SUB -> FloatVector.broadcast(SPECIES, operand).sub(v);
            case MUL -> v.mul(operand);
            case DIV -> FloatVector.broadcast(SPECIES, operand).div(v);
            case MIN -> v.blend(operand, v.compare(VectorOperators.GT, operand));
            case MAX -> v.blend(operand, v.compare(VectorOperators.LT, operand));
        };
    }

    @Override
    public void applyBuffer(float[] values, float[] right, int length, DensityBinaryOp op) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(op, "op");
        Objects.checkFromIndexSize(0, length, values.length);
        Objects.checkFromIndexSize(0, length, right.length);

        int bound = SPECIES.loopBound(length);
        int i = 0;
        for (; i < bound; i += SPECIES.length()) {
            FloatVector l = FloatVector.fromArray(SPECIES, values, i);
            FloatVector r = FloatVector.fromArray(SPECIES, right, i);
            combine(op, l, r).intoArray(values, i);
        }
        for (; i < length; i++) {
            values[i] = ScalarDensityBinaryKernels.combine(op, values[i], right[i]);
        }
    }

    @Override
    public void applyConst(float[] values, int length, DensityBinaryOp op, float operand) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(op, "op");
        Objects.checkFromIndexSize(0, length, values.length);

        int bound = SPECIES.loopBound(length);
        int i = 0;
        for (; i < bound; i += SPECIES.length()) {
            FloatVector v = FloatVector.fromArray(SPECIES, values, i);
            combineConst(op, v, operand).intoArray(values, i);
        }
        for (; i < length; i++) {
            values[i] = ScalarDensityBinaryKernels.combineConst(op, values[i], operand);
        }
    }

    @Override
    public String describe() {
        return "species=" + SPECIES + "; vectorizes all DensityBinaryOp values";
    }
}
