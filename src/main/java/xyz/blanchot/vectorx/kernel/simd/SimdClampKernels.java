package xyz.blanchot.vectorx.kernel.simd;

import jdk.incubator.vector.FloatVector;
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
 *
 * <p>Since 26.3 the whole density-function pipeline is {@code float} rather
 * than {@code double}, so this uses {@code FloatVector.SPECIES_PREFERRED}:
 * twice the lanes per vector for the same register width.
 *
 * <p>Bit-exactness with {@link ScalarClampKernels}: {@code VectorOperators.MIN}
 * is specified in terms of {@code Math.min}, so it agrees with the scalar
 * reference on NaN and on signed zeros; and {@code compare(LT, min)} is an
 * IEEE-754 comparison, false for NaN exactly like Java's {@code <}. The
 * dispatcher's self-test re-checks both on the actual host at startup and
 * falls back to scalar if they ever disagree.
 */
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
