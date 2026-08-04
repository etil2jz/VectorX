package xyz.blanchot.vectorx.kernel.simd;

import jdk.incubator.vector.DoubleVector;
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
 * {@code double}, no cross-lane dependency.
 *
 * <p>{@code HALF_NEGATIVE}, {@code QUARTER_NEGATIVE} and {@code SQUEEZE} use
 * {@code blend(double, VectorMask)} to reproduce the scalar branch
 * lane-by-lane, including NaN: IEEE-754 comparisons are false for NaN, so a
 * NaN lane takes the same branch on both backends by construction.
 */
public final class SimdDensityMapKernels implements DensityMapKernels, SelfDescribing {

    public static final SimdDensityMapKernels INSTANCE = new SimdDensityMapKernels();

    private static final VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_PREFERRED;

    private SimdDensityMapKernels() {
    }

    private static DoubleVector transform(DensityMapOp op, DoubleVector v) {
        return switch (op) {
            case ABS -> v.abs();
            case SQUARE -> v.mul(v);
            case CUBE -> v.mul(v).mul(v);
            case HALF_NEGATIVE -> v.blend(v.mul(0.5), v.compare(VectorOperators.LE, 0.0));
            case QUARTER_NEGATIVE -> v.blend(v.mul(0.25), v.compare(VectorOperators.LE, 0.0));
            case INVERT -> DoubleVector.broadcast(SPECIES, 1.0).div(v);
            case SQUEEZE -> {
                DoubleVector clamped = v.min(1.0).blend(-1.0, v.compare(VectorOperators.LT, -1.0));
                DoubleVector cube = clamped.mul(clamped).mul(clamped);
                yield clamped.mul(0.5).sub(cube.mul(1.0 / 24.0));
            }
        };
    }

    @Override
    public void apply(double[] values, DensityMapOp op) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(op, "op");

        int length = values.length;
        int bound = SPECIES.loopBound(length);
        int i = 0;
        for (; i < bound; i += SPECIES.length()) {
            DoubleVector v = DoubleVector.fromArray(SPECIES, values, i);
            transform(op, v).intoArray(values, i);
        }
        for (; i < length; i++) {
            values[i] = ScalarDensityMapKernels.transform(op, values[i]);
        }
    }

    @Override
    public String describe() {
        return "species=" + SPECIES + "; vectorizes all DensityMapOp values";
    }
}
