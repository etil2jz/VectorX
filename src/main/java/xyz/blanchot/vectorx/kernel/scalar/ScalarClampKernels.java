package xyz.blanchot.vectorx.kernel.scalar;

import xyz.blanchot.vectorx.kernel.ClampKernels;
import xyz.blanchot.vectorx.kernel.SelfDescribing;

import java.util.Objects;

/**
 * Reference scalar implementation of {@link ClampKernels}. The formula below
 * was copied from real Minecraft 26.2's
 * {@code net.minecraft.util.Mth#clamp(double, double, double)}, not
 * re-derived from memory: {@code value < min ? min : Math.min(value, max)}.
 */
public final class ScalarClampKernels implements ClampKernels, SelfDescribing {

    public static final ScalarClampKernels INSTANCE = new ScalarClampKernels();

    private ScalarClampKernels() {
    }

    public static double transform(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    @Override
    public void clampInPlace(double[] values, double min, double max) {
        Objects.requireNonNull(values, "values");
        for (int i = 0; i < values.length; i++) {
            values[i] = transform(values[i], min, max);
        }
    }

    @Override
    public String describe() {
        return "scalar reference backend for element-wise double[] clamp";
    }
}
