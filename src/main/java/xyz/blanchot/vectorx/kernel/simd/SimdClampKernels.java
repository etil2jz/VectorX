package xyz.blanchot.vectorx.kernel.simd;

import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import xyz.blanchot.vectorx.kernel.ClampKernels;
import xyz.blanchot.vectorx.kernel.SelfDescribing;
import xyz.blanchot.vectorx.kernel.scalar.ScalarClampKernels;

import java.util.Objects;

/**
 * Vector API backend for {@link ClampKernels}. Uses {@code blend} to turn
 * the data-dependent {@code value < min} branch into branch-free lane
 * selection.
 */
public final class SimdClampKernels implements ClampKernels, SelfDescribing {

    public static final SimdClampKernels INSTANCE = new SimdClampKernels();

    private static final VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_PREFERRED;

    private SimdClampKernels() {
    }

    @Override
    public void clampInPlace(double[] values, double min, double max) {
        Objects.requireNonNull(values, "values");

        int length = values.length;
        int bound = SPECIES.loopBound(length);
        int i = 0;
        for (; i < bound; i += SPECIES.length()) {
            DoubleVector v = DoubleVector.fromArray(SPECIES, values, i);
            DoubleVector clamped = v.min(max).blend(min, v.compare(VectorOperators.LT, min));
            clamped.intoArray(values, i);
        }
        for (; i < length; i++) {
            values[i] = ScalarClampKernels.transform(values[i], min, max);
        }
    }

    @Override
    public String describe() {
        return "species=" + SPECIES;
    }
}
