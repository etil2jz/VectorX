package xyz.blanchot.vectorx.kernel;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityBufferPool;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.op.ClampFunction;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import xyz.blanchot.vectorx.kernel.scalar.ScalarClampKernels;
import xyz.blanchot.vectorx.kernel.simd.SimdClampKernels;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClampDifferentialTest {

    private static final ClampKernels SCALAR = ScalarClampKernels.INSTANCE;
    private static final ClampKernels VECTOR = SimdClampKernels.INSTANCE;
    private static final float[][] BOUNDS = {{-1.0F, 1.0F}, {-100.0F, 80.0F}, {0.0F, 1.0F}, {-5.5F, 5.5F}};
    private static final float TAIL_CANARY = 1234.5F;

    private static float[] interestingValues(long seed, int extra) {
        float[] fixed = {
                0.0F, -0.0F, 1.0F, -1.0F, 0.5F, -0.5F, 2.0F, -2.0F, 0.999999F, -0.999999F, 1.000001F, -1.000001F,
                Float.MIN_VALUE, -Float.MIN_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN
        };
        float[] values = new float[fixed.length + extra];
        System.arraycopy(fixed, 0, values, 0, fixed.length);
        Random random = new Random(seed);
        for (int i = fixed.length; i < values.length; i++) {
            values[i] = (float) ((random.nextDouble() - 0.5) * Math.pow(10, random.nextInt(12) - 6));
        }
        return values;
    }

    private static float[] vanilla(float[] values, float min, float max) {
        int size = values.length;
        DensityVolume volume = new DensityVolume(size, 1, 1, 0, 0, 0);
        DensityBuffer buffer = DensityBuffer.createUnpooled(size);
        DensitySampler sampler = new ClampFunction.Sampler(new ArraySampler(values), min, max);
        sampler.sampleVolume(SamplerContext.EMPTY_UNCACHED, buffer, volume);

        float[] out = new float[size];
        for (int i = 0; i < size; i++) {
            out[i] = buffer.get(i);
        }
        return out;
    }

    @Test
    void scalarAndVectorMatchRealMinecraftClampSampler() {
        for (float[] bound : BOUNDS) {
            float min = bound[0];
            float max = bound[1];
            for (int size : new int[]{1, 3, 8, 16, 17, 64, 257}) {
                float[] values = Arrays.copyOf(interestingValues(size + 1L, size), size);
                float[] expected = vanilla(values, min, max);

                float[] scalarOut = values.clone();
                float[] vectorOut = values.clone();
                SCALAR.clampInPlace(scalarOut, size, min, max);
                VECTOR.clampInPlace(vectorOut, size, min, max);

                assertArrayEquals(expected, scalarOut, () -> "scalar mismatch vs real ClampFunction$Sampler, size=" + size + " bounds=" + min + ".." + max);
                assertArrayEquals(expected, vectorOut, () -> "vector mismatch vs real ClampFunction$Sampler, size=" + size + " bounds=" + min + ".." + max);
            }
        }
    }

    @Test
    void scalarMatchesRealMinecraftClampSampleValue() {
        float[] values = interestingValues(99L, 200);
        DensitySampler sampler = new ClampFunction.Sampler(new ArraySampler(values), -1.0F, 1.0F);

        float[] expected = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            expected[i] = sampler.sampleValue(SamplerContext.EMPTY_UNCACHED, i, 0, 0);
        }

        float[] scalarOut = values.clone();
        SCALAR.clampInPlace(scalarOut, values.length, -1.0F, 1.0F);
        assertArrayEquals(expected, scalarOut, "scalar mismatch vs real ClampFunction$Sampler.sampleValue");
    }

    @Test
    void vectorPathBoundariesAreExercisedRegardlessOfPlatformLaneWidth() {
        VectorSpecies<Float> species = FloatVector.SPECIES_PREFERRED;
        int lanes = species.length();

        for (int size : new int[]{1, lanes - 1, lanes, lanes + 1, 2 * lanes - 1, 2 * lanes, 2 * lanes + 1, 5 * lanes + 3}) {
            if (size <= 0) {
                continue;
            }
            Random random = new Random(7_919L + size);
            float[] values = new float[size];
            for (int i = 0; i < size; i++) {
                values[i] = (float) ((random.nextDouble() - 0.5) * 20.0);
            }

            float[] expected = vanilla(values, -1.0F, 1.0F);
            float[] vectorOut = values.clone();
            VECTOR.clampInPlace(vectorOut, size, -1.0F, 1.0F);

            assertArrayEquals(expected, vectorOut, () -> "vector mismatch at lane boundary, size=" + size + " lanes=" + lanes);
        }
    }

    @Test
    void poolBackedBufferHasSpareCapacityAndKernelsRespectIt() {
        int size = 97;
        DensityVolume volume = new DensityVolume(size, 1, 1, 0, 0, 0);
        SamplerContext context = SamplerContext.builder().useBufferArena(new DensityBufferPool(4)).build();
        DensityBuffer buffer = context.acquireBuffer(volume);

        assertTrue(buffer.capacity() > buffer.size(), "expected the pooled buffer capacity (" + buffer.capacity() + ") to exceed its size (" + buffer.size() + ")");
        int capacity = buffer.capacity();
        for (int i = size; i < capacity; i++) {
            buffer.set(i, TAIL_CANARY);
        }

        float[] values = Arrays.copyOf(interestingValues(4_242L, size), size);
        new ClampFunction.Sampler(new ArraySampler(values), -1.0F, 1.0F).sampleVolume(context, buffer, volume);

        float[] expected = new float[capacity];
        for (int i = 0; i < capacity; i++) {
            expected[i] = buffer.get(i);
        }

        float[] scalarOut = Arrays.copyOf(values, capacity);
        float[] vectorOut = Arrays.copyOf(values, capacity);
        for (int i = size; i < capacity; i++) {
            scalarOut[i] = TAIL_CANARY;
            vectorOut[i] = TAIL_CANARY;
        }
        SCALAR.clampInPlace(scalarOut, size, -1.0F, 1.0F);
        VECTOR.clampInPlace(vectorOut, size, -1.0F, 1.0F);

        assertArrayEquals(expected, scalarOut, "scalar wrote past size() or disagreed with vanilla");
        assertArrayEquals(expected, vectorOut, "vector wrote past size() or disagreed with vanilla");
    }

    @Test
    void zeroLengthIsANoOpOnBothBackends() {
        float[] scalarOut = {TAIL_CANARY, TAIL_CANARY};
        float[] vectorOut = {TAIL_CANARY, TAIL_CANARY};
        SCALAR.clampInPlace(scalarOut, 0, -1.0F, 1.0F);
        VECTOR.clampInPlace(vectorOut, 0, -1.0F, 1.0F);
        assertArrayEquals(new float[]{TAIL_CANARY, TAIL_CANARY}, scalarOut);
        assertArrayEquals(new float[]{TAIL_CANARY, TAIL_CANARY}, vectorOut);
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
