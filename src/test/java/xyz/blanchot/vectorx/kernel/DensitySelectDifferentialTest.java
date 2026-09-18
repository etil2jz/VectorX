package xyz.blanchot.vectorx.kernel;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.op.LerpFunction;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import xyz.blanchot.vectorx.kernel.scalar.ScalarDensitySelectKernels;
import xyz.blanchot.vectorx.kernel.simd.SimdDensitySelectKernels;

import java.lang.reflect.Constructor;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DensitySelectDifferentialTest {

    private static final DensitySelectKernels SCALAR = ScalarDensitySelectKernels.INSTANCE;
    private static final DensitySelectKernels VECTOR = SimdDensitySelectKernels.INSTANCE;

    private static final String RANGE_CHOICE = "net.minecraft.world.level.levelgen.densityfunction.op.RangeChoiceFunction";

    private static DensitySampler newRangeChoiceSampler(DensitySampler input, float min, float max, DensitySampler whenInRange, DensitySampler whenOutOfRange) {
        return construct(RANGE_CHOICE + "$Sampler", new Class<?>[]{DensitySampler.class, float.class, float.class, DensitySampler.class, DensitySampler.class}, input, min, max, whenInRange, whenOutOfRange);
    }

    private static DensitySampler newRangeChoiceConstSampler(DensitySampler input, float min, float max, float whenInRange, float whenOutOfRange) {
        return construct(RANGE_CHOICE + "$ConstSampler", new Class<?>[]{DensitySampler.class, float.class, float.class, float.class, float.class}, input, min, max, whenInRange, whenOutOfRange);
    }

    private static DensitySampler construct(String className, Class<?>[] signature, Object... args) {
        try {
            Class<?> type = Class.forName(className);
            Constructor<?> constructor = type.getDeclaredConstructor(signature);
            constructor.setAccessible(true);
            return (DensitySampler) constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not reach real Minecraft " + className + "; the production Mixin targets this class by the same name", e);
        }
    }

    private static float[] alphas(long seed, int count) {
        float[] fixed = {0.0F, -0.0F, 1.0F, 0.5F, -1.0F, 2.0F, Float.NaN,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
        float[] values = new float[count];
        Random random = new Random(seed);
        for (int i = 0; i < count; i++) {
            if (i < fixed.length) {
                values[i] = fixed[i];
            } else {
                int bucket = random.nextInt(6);
                values[i] = bucket == 0 ? 0.0F : bucket == 1 ? 1.0F : (float) random.nextDouble();
            }
        }
        return values;
    }

    private static float[] operands(long seed, int count) {
        float[] fixed = {0.0F, -0.0F, 1.0F, -1.0F, Float.NaN,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.MAX_VALUE, -Float.MAX_VALUE};
        float[] values = new float[count];
        Random random = new Random(seed);
        for (int i = 0; i < count; i++) {
            values[i] = i < fixed.length ? fixed[i] : (float) ((random.nextDouble() - 0.5) * Math.pow(10, random.nextInt(10) - 5));
        }
        return values;
    }

    private static float[] vanilla(DensitySampler sampler, int size) {
        DensityVolume volume = new DensityVolume(size, 1, 1, 0, 0, 0);
        DensityBuffer buffer = DensityBuffer.createUnpooled(size);
        sampler.sampleVolume(SamplerContext.EMPTY_UNCACHED, buffer, volume);
        float[] out = new float[size];
        for (int i = 0; i < size; i++) {
            out[i] = buffer.get(i);
        }
        return out;
    }

    @Test
    void lerpMatchesRealMinecraftSampleVolume() {
        float[] alpha = alphas(1L, 500);
        float[] first = operands(2L, 500);
        float[] second = operands(3L, 500);
        int size = alpha.length;

        float[] expected = vanilla(new LerpFunction.Sampler(new ArraySampler(alpha), new ArraySampler(first), new ArraySampler(second)), size);

        float[] scalarOut = alpha.clone();
        float[] vectorOut = alpha.clone();
        SCALAR.lerp(scalarOut, first, second, size);
        VECTOR.lerp(vectorOut, first, second, size);

        assertArrayEquals(expected, scalarOut, "scalar mismatch vs real LerpFunction$Sampler");
        assertArrayEquals(expected, vectorOut, "vector mismatch vs real LerpFunction$Sampler");
    }

    @Test
    void lerpMatchesRealMinecraftSampleValue() {
        float[] alpha = alphas(11L, 400);
        float[] first = operands(12L, 400);
        float[] second = operands(13L, 400);
        DensitySampler sampler = new LerpFunction.Sampler(new ArraySampler(alpha), new ArraySampler(first), new ArraySampler(second));

        float[] expected = new float[alpha.length];
        for (int i = 0; i < alpha.length; i++) {
            expected[i] = sampler.sampleValue(SamplerContext.EMPTY_UNCACHED, i, 0, 0);
        }

        float[] scalarOut = alpha.clone();
        float[] vectorOut = alpha.clone();
        SCALAR.lerp(scalarOut, first, second, alpha.length);
        VECTOR.lerp(vectorOut, first, second, alpha.length);

        assertArrayEquals(expected, scalarOut, "scalar mismatch vs real lerp sampleValue");
        assertArrayEquals(expected, vectorOut, "vector mismatch vs real lerp sampleValue");
    }

    @Test
    void rangeChoiceConstMatchesRealMinecraftSampleVolume() {
        float[] input = alphas(21L, 500);
        int size = input.length;

        for (float[] range : new float[][]{{0.2F, 0.8F}, {-1.0F, 1.0F}, {0.0F, 0.0F}, {Float.NaN, 1.0F}}) {
            float min = range[0];
            float max = range[1];
            float[] expected = vanilla(newRangeChoiceConstSampler(new ArraySampler(input), min, max, 7.5F, -7.5F), size);

            float[] scalarOut = input.clone();
            float[] vectorOut = input.clone();
            SCALAR.rangeChoiceConst(scalarOut, size, min, max, 7.5F, -7.5F);
            VECTOR.rangeChoiceConst(vectorOut, size, min, max, 7.5F, -7.5F);

            assertArrayEquals(expected, scalarOut, () -> "scalar mismatch, range [" + min + ", " + max + ")");
            assertArrayEquals(expected, vectorOut, () -> "vector mismatch, range [" + min + ", " + max + ")");
        }
    }

    @Test
    void rangeChoiceMatchesRealMinecraftSampleVolume() {
        float[] input = alphas(31L, 500);
        float[] whenInRange = operands(32L, 500);
        float[] whenOutOfRange = operands(33L, 500);
        int size = input.length;

        for (float[] range : new float[][]{{0.2F, 0.8F}, {-1.0F, 1.0F}, {Float.NaN, 1.0F}}) {
            float min = range[0];
            float max = range[1];
            float[] expected = vanilla(newRangeChoiceSampler(new ArraySampler(input), min, max, new ArraySampler(whenInRange), new ArraySampler(whenOutOfRange)), size);

            float[] scalarOut = whenInRange.clone();
            float[] vectorOut = whenInRange.clone();
            SCALAR.rangeChoice(scalarOut, input, whenOutOfRange, size, min, max);
            VECTOR.rangeChoice(vectorOut, input, whenOutOfRange, size, min, max);

            assertArrayEquals(expected, scalarOut, () -> "scalar mismatch, range [" + min + ", " + max + ")");
            assertArrayEquals(expected, vectorOut, () -> "vector mismatch, range [" + min + ", " + max + ")");
        }
    }

    @Test
    void negativeZeroAlphaTakesTheFirstBranch() {
        float[] alpha = {-0.0F};
        float[] first = {3.5F};
        float[] second = {-8.25F};

        float[] expected = vanilla(new LerpFunction.Sampler(new ArraySampler(alpha), new ArraySampler(first), new ArraySampler(second)), 1);
        assertEquals(3.5F, expected[0], "vanilla should pick first for a -0.0f alpha");

        float[] scalarOut = alpha.clone();
        float[] vectorOut = alpha.clone();
        SCALAR.lerp(scalarOut, first, second, 1);
        VECTOR.lerp(vectorOut, first, second, 1);

        assertEquals(3.5F, scalarOut[0], "scalar kernel diverged on -0.0f alpha");
        assertEquals(3.5F, vectorOut[0], "vector kernel diverged on -0.0f alpha");
    }

    @Test
    void kernelsRespectLengthAndLeaveTheTailAlone() {
        int size = 97;
        int capacity = size + 13;
        float canary = 1234.5F;

        float[] alpha = alphas(41L, capacity);
        float[] first = operands(42L, capacity);
        float[] second = operands(43L, capacity);
        for (int i = size; i < capacity; i++) {
            alpha[i] = canary;
        }

        float[] scalarOut = alpha.clone();
        float[] vectorOut = alpha.clone();
        SCALAR.lerp(scalarOut, first, second, size);
        VECTOR.lerp(vectorOut, first, second, size);
        assertArrayEquals(scalarOut, vectorOut, "backends diverged on lerp");

        float[] scalarRange = alpha.clone();
        float[] vectorRange = alpha.clone();
        SCALAR.rangeChoiceConst(scalarRange, size, 0.2F, 0.8F, 1.0F, -1.0F);
        VECTOR.rangeChoiceConst(vectorRange, size, 0.2F, 0.8F, 1.0F, -1.0F);
        assertArrayEquals(scalarRange, vectorRange, "backends diverged on rangeChoiceConst");

        for (int i = size; i < capacity; i++) {
            assertEquals(canary, vectorOut[i], "lerp wrote past length");
            assertEquals(canary, vectorRange[i], "rangeChoiceConst wrote past length");
        }
    }

    private record ArraySampler(float[] values) implements DensitySampler {
        @Override
        public void sampleVolume(@NonNull SamplerContext context, @NonNull DensityBuffer outputBuffer, @NonNull DensityVolume volume) {
            DensitySampler.sampleVolumeNaive(context, outputBuffer, volume, this);
        }

        @Override
        public float sampleValue(@NonNull SamplerContext context, int blockX, int blockY, int blockZ) {
            return this.values[blockX];
        }
    }
}
