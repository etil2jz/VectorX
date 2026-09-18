package xyz.blanchot.vectorx.kernel.simd;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import xyz.blanchot.vectorx.kernel.DensityMapKernels;
import xyz.blanchot.vectorx.kernel.DensityMapOp;
import xyz.blanchot.vectorx.kernel.SelfDescribing;
import xyz.blanchot.vectorx.kernel.scalar.ScalarDensityMapKernels;

import java.util.Objects;

/**
 * Vector API backend for {@link DensityMapKernels}. Every {@link DensityMapOp}
 * vectorizes: each transform is a pure per-lane function of one
 * {@code float}, no cross-lane dependency.
 *
 * <p>Since 26.3 the whole density-function pipeline is {@code float} rather
 * than {@code double}, so this uses {@code FloatVector.SPECIES_PREFERRED}:
 * twice the lanes per vector for the same register width.
 *
 * <p>The leaky-ReLU ops and {@code SQUEEZE} use {@code blend(float, VectorMask)}
 * to reproduce the scalar branch lane-by-lane, including NaN: the mask is
 * built with the <em>same</em> comparison the scalar reference writes
 * ({@code v > 0} for leaky ReLU, {@code v < -1} for {@code SQUEEZE}'s clamp),
 * and IEEE-754 comparisons are false for NaN on both backends, so a NaN lane
 * provably takes the same branch.
 */
public final class SimdDensityMapKernels implements DensityMapKernels, SelfDescribing {

    public static final SimdDensityMapKernels INSTANCE = new SimdDensityMapKernels();

    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    private SimdDensityMapKernels() {
    }

    /**
     * Mirrors {@code UnaryFunction$LeakyReLUSampler#apply}: lanes with
     * {@code v > 0} keep {@code v}, all others (including NaN, for which
     * {@code >} is false) take {@code v * negativeFactor}.
     */
    private static FloatVector leakyReLU(FloatVector v, float negativeFactor) {
        return v.mul(negativeFactor).blend(v, v.compare(VectorOperators.GT, 0.0F));
    }

    /**
     * Mirrors {@code UnaryFunction$SqueezeSampler#apply}.
     */
    private static FloatVector squeeze(FloatVector v) {
        FloatVector clamped = v.min(1.0F).blend(-1.0F, v.compare(VectorOperators.LT, -1.0F));
        FloatVector cube = clamped.mul(clamped).mul(clamped);
        // True div, not a precomputed 1.0F/24.0F reciprocal multiply: the two
        // round differently for a small but non-negligible fraction of inputs
        // (verified empirically on the double version of this kernel), which
        // silently broke bit-exactness with the scalar reference -- the same
        // class of bug as SimdCarverSkipKernels's yd computation. The
        // association of the cube is (c * c) * c, matching Mth.cube's bytecode.
        return clamped.div(2.0F).sub(cube.div(24.0F));
    }

    private static FloatVector transform(DensityMapOp op, FloatVector v) {
        return switch (op) {
            case ABS -> v.abs();
            case SQUARE -> v.mul(v);
            case CUBE -> v.mul(v).mul(v);
            case HALF_NEGATIVE -> leakyReLU(v, 0.5F);
            case QUARTER_NEGATIVE -> leakyReLU(v, 0.25F);
            case RECIPROCAL -> FloatVector.broadcast(SPECIES, 1.0F).div(v);
            case SQUEEZE -> squeeze(v);
        };
    }

    @Override
    public void apply(float[] values, int length, DensityMapOp op) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(op, "op");
        Objects.checkFromIndexSize(0, length, values.length);

        int bound = SPECIES.loopBound(length);
        int i = 0;
        for (; i < bound; i += SPECIES.length()) {
            FloatVector v = FloatVector.fromArray(SPECIES, values, i);
            transform(op, v).intoArray(values, i);
        }
        for (; i < length; i++) {
            values[i] = ScalarDensityMapKernels.transform(op, values[i]);
        }
    }

    @Override
    public void leakyReLU(float[] values, int length, float negativeFactor) {
        Objects.requireNonNull(values, "values");
        Objects.checkFromIndexSize(0, length, values.length);

        int bound = SPECIES.loopBound(length);
        int i = 0;
        for (; i < bound; i += SPECIES.length()) {
            FloatVector v = FloatVector.fromArray(SPECIES, values, i);
            leakyReLU(v, negativeFactor).intoArray(values, i);
        }
        for (; i < length; i++) {
            values[i] = ScalarDensityMapKernels.leakyReLU(values[i], negativeFactor);
        }
    }

    @Override
    public String describe() {
        return "species=" + SPECIES + "; vectorizes all DensityMapOp values";
    }
}
