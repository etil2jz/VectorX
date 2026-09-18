package xyz.blanchot.vectorx.kernel.simd;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import xyz.blanchot.vectorx.kernel.DensitySelectKernels;
import xyz.blanchot.vectorx.kernel.SelfDescribing;
import xyz.blanchot.vectorx.kernel.scalar.ScalarDensitySelectKernels;

import java.util.Objects;

public final class SimdDensitySelectKernels implements DensitySelectKernels, SelfDescribing {

    public static final SimdDensitySelectKernels INSTANCE = new SimdDensitySelectKernels();

    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    private SimdDensitySelectKernels() {
    }

    @Override
    public void lerp(float[] values, float[] first, float[] second, int length) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.checkFromIndexSize(0, length, values.length);
        Objects.checkFromIndexSize(0, length, first.length);
        Objects.checkFromIndexSize(0, length, second.length);

        int bound = SPECIES.loopBound(length);
        int i = 0;
        for (; i < bound; i += SPECIES.length()) {
            FloatVector alpha = FloatVector.fromArray(SPECIES, values, i);
            FloatVector f = FloatVector.fromArray(SPECIES, first, i);
            FloatVector s = FloatVector.fromArray(SPECIES, second, i);
            FloatVector lerped = f.add(alpha.mul(s.sub(f)));
            lerped = lerped.blend(s, alpha.compare(VectorOperators.EQ, 1.0F));
            lerped = lerped.blend(f, alpha.compare(VectorOperators.EQ, 0.0F));
            lerped.intoArray(values, i);
        }
        for (; i < length; i++) {
            values[i] = ScalarDensitySelectKernels.lerpElement(values[i], first[i], second[i]);
        }
    }

    @Override
    public void rangeChoiceConst(float[] values, int length, float minInclusive, float maxExclusive, float whenInRange, float whenOutOfRange) {
        Objects.requireNonNull(values, "values");
        Objects.checkFromIndexSize(0, length, values.length);

        FloatVector inRangeVec = FloatVector.broadcast(SPECIES, whenInRange);
        FloatVector outOfRangeVec = FloatVector.broadcast(SPECIES, whenOutOfRange);

        int bound = SPECIES.loopBound(length);
        int i = 0;
        for (; i < bound; i += SPECIES.length()) {
            FloatVector v = FloatVector.fromArray(SPECIES, values, i);
            VectorMask<Float> in = v.compare(VectorOperators.GE, minInclusive).and(v.compare(VectorOperators.LT, maxExclusive));
            outOfRangeVec.blend(inRangeVec, in).intoArray(values, i);
        }
        for (; i < length; i++) {
            values[i] = ScalarDensitySelectKernels.chooseConst(values[i], minInclusive, maxExclusive, whenInRange, whenOutOfRange);
        }
    }

    @Override
    public void rangeChoice(float[] values, float[] input, float[] whenOutOfRange, int length, float minInclusive, float maxExclusive) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(whenOutOfRange, "whenOutOfRange");
        Objects.checkFromIndexSize(0, length, values.length);
        Objects.checkFromIndexSize(0, length, input.length);
        Objects.checkFromIndexSize(0, length, whenOutOfRange.length);

        int bound = SPECIES.loopBound(length);
        int i = 0;
        for (; i < bound; i += SPECIES.length()) {
            FloatVector v = FloatVector.fromArray(SPECIES, values, i);
            FloatVector in = FloatVector.fromArray(SPECIES, input, i);
            FloatVector oor = FloatVector.fromArray(SPECIES, whenOutOfRange, i);
            VectorMask<Float> outside = in.compare(VectorOperators.GE, minInclusive).and(in.compare(VectorOperators.LT, maxExclusive)).not();
            v.blend(oor, outside).intoArray(values, i);
        }
        for (; i < length; i++) {
            float x = input[i];
            if (!(x >= minInclusive) || !(x < maxExclusive)) {
                values[i] = whenOutOfRange[i];
            }
        }
    }

    @Override
    public String describe() {
        return "species=" + SPECIES;
    }
}
