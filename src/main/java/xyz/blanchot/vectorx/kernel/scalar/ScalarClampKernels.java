package xyz.blanchot.vectorx.kernel.scalar;

import xyz.blanchot.vectorx.kernel.ClampKernels;
import xyz.blanchot.vectorx.kernel.SelfDescribing;

import java.util.Objects;

public final class ScalarClampKernels implements ClampKernels, SelfDescribing {

    public static final ScalarClampKernels INSTANCE = new ScalarClampKernels();

    private ScalarClampKernels() {
    }

    public static float transform(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    @Override
    public void clampInPlace(float[] values, int length, float min, float max) {
        Objects.requireNonNull(values, "values");
        Objects.checkFromIndexSize(0, length, values.length);
        for (int i = 0; i < length; i++) {
            values[i] = transform(values[i], min, max);
        }
    }

    @Override
    public String describe() {
        return "scalar reference backend for element-wise float[] clamp";
    }
}
