package xyz.blanchot.vectorx.kernel.scalar;

import xyz.blanchot.vectorx.kernel.DensitySelectKernels;
import xyz.blanchot.vectorx.kernel.SelfDescribing;

import java.util.Objects;

/**
 * Reference scalar implementation of {@link DensitySelectKernels}. Every loop
 * body was copied from the matching sampler in real Minecraft 26.3, not
 * re-derived from memory:
 *
 * <ul>
 *   <li>{@code LerpFunction$Sampler}: {@code alpha == 0 ? first : alpha == 1 ?
 *       second : Mth.lerp(alpha, first, second)}, where {@code Mth.lerp}'s
 *       bytecode is {@code start + delta * (end - start)} -- the association
 *       matters and is reproduced below;</li>
 *   <li>{@code RangeChoiceFunction$ConstSampler}: {@code input >= minInclusive
 *       && input < maxExclusive ? whenInRange : whenOutOfRange};</li>
 *   <li>{@code RangeChoiceFunction$Sampler}: {@code if (!(input >=
 *       minInclusive) || !(input < maxExclusive)) out = whenOutOfRange} --
 *       vanilla's negated spelling is kept verbatim because it is what makes
 *       NaN take the out-of-range branch.</li>
 * </ul>
 */
public final class ScalarDensitySelectKernels implements DensitySelectKernels, SelfDescribing {

    public static final ScalarDensitySelectKernels INSTANCE = new ScalarDensitySelectKernels();

    private ScalarDensitySelectKernels() {
    }

    /** Mirrors {@code net.minecraft.util.Mth#lerp(float, float, float)}. */
    public static float mthLerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    /** Mirrors one iteration of {@code LerpFunction$Sampler}'s loop. */
    public static float lerpElement(float alpha, float first, float second) {
        if (alpha == 0.0F) {
            return first;
        } else if (alpha == 1.0F) {
            return second;
        } else {
            return mthLerp(alpha, first, second);
        }
    }

    /** Mirrors {@code RangeChoiceFunction$ConstSampler#choose}. */
    public static float chooseConst(float input, float minInclusive, float maxExclusive,
                                    float whenInRange, float whenOutOfRange) {
        return input >= minInclusive && input < maxExclusive ? whenInRange : whenOutOfRange;
    }

    @Override
    public void lerp(float[] values, float[] first, float[] second, int length) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.checkFromIndexSize(0, length, values.length);
        Objects.checkFromIndexSize(0, length, first.length);
        Objects.checkFromIndexSize(0, length, second.length);
        for (int i = 0; i < length; i++) {
            values[i] = lerpElement(values[i], first[i], second[i]);
        }
    }

    @Override
    public void rangeChoiceConst(float[] values, int length,
                                 float minInclusive, float maxExclusive,
                                 float whenInRange, float whenOutOfRange) {
        Objects.requireNonNull(values, "values");
        Objects.checkFromIndexSize(0, length, values.length);
        for (int i = 0; i < length; i++) {
            values[i] = chooseConst(values[i], minInclusive, maxExclusive, whenInRange, whenOutOfRange);
        }
    }

    @Override
    public void rangeChoice(float[] values, float[] input, float[] whenOutOfRange, int length,
                            float minInclusive, float maxExclusive) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(whenOutOfRange, "whenOutOfRange");
        Objects.checkFromIndexSize(0, length, values.length);
        Objects.checkFromIndexSize(0, length, input.length);
        Objects.checkFromIndexSize(0, length, whenOutOfRange.length);
        for (int i = 0; i < length; i++) {
            float in = input[i];
            if (!(in >= minInclusive) || !(in < maxExclusive)) {
                values[i] = whenOutOfRange[i];
            }
        }
    }

    @Override
    public String describe() {
        return "scalar reference backend for density-function data-dependent selection";
    }
}
