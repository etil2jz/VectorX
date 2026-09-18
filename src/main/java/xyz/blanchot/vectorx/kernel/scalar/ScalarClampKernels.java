package xyz.blanchot.vectorx.kernel.scalar;

import xyz.blanchot.vectorx.kernel.ClampKernels;
import xyz.blanchot.vectorx.kernel.SelfDescribing;

import java.util.Objects;

/**
 * Reference scalar implementation of {@link ClampKernels}. The formula below
 * was copied from real Minecraft 26.3's
 * {@code net.minecraft.util.Mth#clamp(float, float, float)} -- confirmed
 * against that method's bytecode ({@code fcmpg} / {@code ifge} /
 * {@code Math.min}), not re-derived from memory:
 * {@code value < min ? min : Math.min(value, max)}.
 *
 * <p>The {@code fcmpg} in that bytecode is what makes NaN fall through to
 * {@code Math.min(NaN, max) == NaN} rather than to {@code min}; the
 * expression below reproduces that exactly, since Java's {@code <} on floats
 * is likewise false for NaN.
 */
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
