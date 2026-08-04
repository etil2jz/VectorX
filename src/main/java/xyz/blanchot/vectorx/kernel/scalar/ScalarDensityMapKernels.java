package xyz.blanchot.vectorx.kernel.scalar;

import xyz.blanchot.vectorx.kernel.DensityMapKernels;
import xyz.blanchot.vectorx.kernel.DensityMapOp;
import xyz.blanchot.vectorx.kernel.SelfDescribing;

import java.util.Objects;

/**
 * Reference scalar implementation of {@link DensityMapKernels}. Each formula
 * below was copied from real Minecraft 26.2's
 * {@code DensityFunctions.Mapped#transform(Type, double)} and
 * {@code net.minecraft.util.Mth#clamp(double, double, double)} (the latter
 * for the SQUEEZE case), not re-derived from memory.
 */
public final class ScalarDensityMapKernels implements DensityMapKernels, SelfDescribing {

    public static final ScalarDensityMapKernels INSTANCE = new ScalarDensityMapKernels();

    private ScalarDensityMapKernels() {
    }

    public static double transform(DensityMapOp op, double x) {
        return switch (op) {
            case ABS -> Math.abs(x);
            case SQUARE -> x * x;
            case CUBE -> x * x * x;
            case HALF_NEGATIVE -> x > 0.0 ? x : x * 0.5;
            case QUARTER_NEGATIVE -> x > 0.0 ? x : x * 0.25;
            case INVERT -> 1.0 / x;
            case SQUEEZE -> {
                double c = x < -1.0 ? -1.0 : Math.min(x, 1.0);
                yield c / 2.0 - c * c * c / 24.0;
            }
        };
    }

    @Override
    public void apply(double[] values, DensityMapOp op) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(op, "op");
        for (int i = 0; i < values.length; i++) {
            values[i] = transform(op, values[i]);
        }
    }

    @Override
    public String describe() {
        return "scalar reference backend for density-function element-wise map";
    }
}
