package xyz.blanchot.vectorx.kernel.simd;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import xyz.blanchot.vectorx.kernel.DensityMapKernels;
import xyz.blanchot.vectorx.kernel.DensityMapOp;
import xyz.blanchot.vectorx.kernel.SelfDescribing;
import xyz.blanchot.vectorx.kernel.scalar.ScalarDensityMapKernels;

import java.util.Objects;

public final class SimdDensityMapKernels implements DensityMapKernels, SelfDescribing {

    public static final SimdDensityMapKernels INSTANCE = new SimdDensityMapKernels();

    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    private SimdDensityMapKernels() {
    }

    private static FloatVector leakyReLU(FloatVector v, float negativeFactor) {
        return v.mul(negativeFactor).blend(v, v.compare(VectorOperators.GT, 0.0F));
    }

    private static FloatVector squeeze(FloatVector v) {
        FloatVector clamped = v.min(1.0F).blend(-1.0F, v.compare(VectorOperators.LT, -1.0F));
        FloatVector cube = clamped.mul(clamped).mul(clamped);
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
