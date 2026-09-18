package xyz.blanchot.vectorx.kernel.simd;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import xyz.blanchot.vectorx.kernel.DensityBinaryKernels;
import xyz.blanchot.vectorx.kernel.DensityBinaryOp;
import xyz.blanchot.vectorx.kernel.SelfDescribing;
import xyz.blanchot.vectorx.kernel.scalar.ScalarDensityBinaryKernels;

import java.util.Objects;

/**
 * Vector API backend for {@link DensityBinaryKernels}. Every
 * {@link DensityBinaryOp} vectorizes: each is a pure per-lane function of one
 * or two {@code float}s with no cross-lane dependency.
 *
 * <p><b>{@code MIN} and {@code MAX} deliberately do not use
 * {@code VectorOperators.MIN}/{@code MAX}.</b> Those are specified in terms of
 * {@code Math.min}/{@code Math.max}, which disagree with vanilla's conditional
 * write on signed zeros: vanilla's {@code if (candidate < current)} keeps
 * {@code +0.0F} when the candidate is {@code -0.0F}, while {@code Math.min}
 * returns {@code -0.0F}. Reproducing the comparison with {@code compare} plus
 * {@code blend} keeps this bit-identical to
 * {@link ScalarDensityBinaryKernels}, including on NaN: an IEEE-754 comparison
 * is false for NaN on both backends, so a NaN lane provably takes the same
 * branch.
 *
 * <p>{@code SUB} is a real {@code sub}, and the constant forms of {@code SUB}
 * and {@code DIV} broadcast the constant into the left operand rather than
 * rewriting the expression -- {@code operand - v} is not {@code -(v - operand)}
 * for every input, and {@code operand / v} is not {@code v * (1 / operand)}
 * at all.
 */
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
            // right < left ? right : left -- see the class javadoc on why this
            // is not lanewise(MIN).
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
            // operand < v ? operand : v
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
