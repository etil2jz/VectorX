package xyz.blanchot.vectorx.kernel.scalar;

import xyz.blanchot.vectorx.kernel.DensityMapKernels;
import xyz.blanchot.vectorx.kernel.DensityMapOp;
import xyz.blanchot.vectorx.kernel.SelfDescribing;

import java.util.Objects;

/**
 * Reference scalar implementation of {@link DensityMapKernels}. Each formula
 * below was copied from the corresponding per-op sampler record nested in
 * real Minecraft 26.3's
 * {@code net.minecraft.world.level.levelgen.densityfunction.op.UnaryFunction}
 * (26.3 replaced 26.2's single {@code DensityFunctions.Mapped#transform(Type, double)}
 * switch with one sampler class per op), not re-derived from memory:
 *
 * <ul>
 *   <li>{@code AbsSampler}: {@code Math.abs(v)}</li>
 *   <li>{@code SquareSampler}: {@code Mth.square(v)}, whose bytecode is {@code v * v}</li>
 *   <li>{@code CubeSampler}: {@code Mth.cube(v)}, whose bytecode is {@code (v * v) * v}
 *       -- the association matters for bit-exactness, and was read off
 *       {@code Mth.cube(float)}'s {@code fload_0 fload_0 fmul fload_0 fmul}</li>
 *   <li>{@code LeakyReLUSampler.apply}: {@code input > 0.0F ? input : input * negativeFactor}</li>
 *   <li>{@code ReciprocalSampler}: {@code 1.0F / v}</li>
 *   <li>{@code SqueezeSampler.apply}: {@code c / 2.0F - Mth.cube(c) / 24.0F}
 *       with {@code c = Mth.clamp(v, -1.0F, 1.0F)}</li>
 * </ul>
 *
 * <p>Both divisions in {@code SQUEEZE} are kept as real divisions, exactly as
 * Mojang writes them. {@code / 2.0F} could safely become {@code * 0.5F} (a
 * power of two is exact either way) but {@code / 24.0F} could not, and
 * spelling both the same way removes the temptation.
 */
public final class ScalarDensityMapKernels implements DensityMapKernels, SelfDescribing {

    public static final ScalarDensityMapKernels INSTANCE = new ScalarDensityMapKernels();

    private ScalarDensityMapKernels() {
    }

    /**
     * Mirrors {@code UnaryFunction$LeakyReLUSampler#apply(float, float)}.
     */
    public static float leakyReLU(float x, float negativeFactor) {
        return x > 0.0F ? x : x * negativeFactor;
    }

    /**
     * Mirrors {@code net.minecraft.util.Mth#clamp(float, float, float)}.
     */
    private static float clamp(float x, float min, float max) {
        return x < min ? min : Math.min(x, max);
    }

    /**
     * Mirrors {@code UnaryFunction$SqueezeSampler#apply(float)}.
     */
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
