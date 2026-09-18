package xyz.blanchot.vectorx.kernel.simd;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import xyz.blanchot.vectorx.kernel.ClampKernels;
import xyz.blanchot.vectorx.kernel.SelfDescribing;
import xyz.blanchot.vectorx.kernel.scalar.ScalarClampKernels;

import java.util.Objects;

public final class SimdClampKernels implements ClampKernels, SelfDescribing {

    public static final SimdClampKernels INSTANCE = new SimdClampKernels();

    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    private SimdClampKernels() {
    }

    @Override
    public void clampInPlace(float[] values, int length, float min, float max) {
        Objects.requireNonNull(values, "values");
        Objects.checkFromIndexSize(0, length, values.length);

        int bound = SPECIES.loopBound(length);
        int i = 0;
        for (; i < bound; i += SPECIES.length()) {
            FloatVector v = FloatVector.fromArray(SPECIES, values, i);
            FloatVector clamped = v.min(max).blend(min, v.compare(VectorOperators.LT, min));
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
