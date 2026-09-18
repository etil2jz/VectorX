package xyz.blanchot.vectorx.kernel.scalar;

import xyz.blanchot.vectorx.kernel.DensityMapKernels;
import xyz.blanchot.vectorx.kernel.DensityMapOp;
import xyz.blanchot.vectorx.kernel.SelfDescribing;

import java.util.Objects;

public final class ScalarDensityMapKernels implements DensityMapKernels, SelfDescribing {

    public static final ScalarDensityMapKernels INSTANCE = new ScalarDensityMapKernels();

    private ScalarDensityMapKernels() {
    }

    public static float leakyReLU(float x, float negativeFactor) {
        return x > 0.0F ? x : x * negativeFactor;
    }

    private static float clamp(float x, float min, float max) {
        return x < min ? min : Math.min(x, max);
    }

    public static float squeeze(float x) {
        float c = clamp(x, -1.0F, 1.0F);
        return c / 2.0F - c * c * c / 24.0F;
    }

    public static float transform(DensityMapOp op, float x) {
        return switch (op) {
            case ABS -> Math.abs(x);
            case SQUARE -> x * x;
            case CUBE -> x * x * x;
            case HALF_NEGATIVE -> leakyReLU(x, 0.5F);
            case QUARTER_NEGATIVE -> leakyReLU(x, 0.25F);
            case RECIPROCAL -> 1.0F / x;
            case SQUEEZE -> squeeze(x);
        };
    }

    @Override
    public void apply(float[] values, int length, DensityMapOp op) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(op, "op");
        Objects.checkFromIndexSize(0, length, values.length);
        for (int i = 0; i < length; i++) {
            values[i] = transform(op, values[i]);
        }
    }

    @Override
    public void leakyReLU(float[] values, int length, float negativeFactor) {
        Objects.requireNonNull(values, "values");
        Objects.checkFromIndexSize(0, length, values.length);
        for (int i = 0; i < length; i++) {
            values[i] = leakyReLU(values[i], negativeFactor);
        }
    }

    @Override
    public String describe() {
        return "scalar reference backend for density-function element-wise map";
    }
}
