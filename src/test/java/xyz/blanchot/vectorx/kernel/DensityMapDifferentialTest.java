package xyz.blanchot.vectorx.kernel;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityBufferPool;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.op.UnaryFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import xyz.blanchot.vectorx.kernel.scalar.ScalarDensityMapKernels;
import xyz.blanchot.vectorx.kernel.simd.SimdDensityMapKernels;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Differential tests for {@link DensityMapKernels} against the real
 * per-op sampler records nested in Minecraft 26.3's
 * {@code net.minecraft.world.level.levelgen.densityfunction.op.UnaryFunction}.
 *
 * <p>26.3 replaced 26.2's single {@code DensityFunctions.Mapped} +
 * {@code Mapped.Type} switch with one {@code DensitySampler} record per op,
 * all {@code public} with public canonical constructors. Ground truth here is
 * therefore those unmodified records, constructed directly over a stub input
 * sampler -- never a reimplemented copy of Mojang's formulas, and never a
 * {@code CompileContext} (whose three methods are noise/random factories a
 * constant input never calls). {@code UnaryFunction.compileSampler} is what
 * maps a {@code Type} to one of these classes; {@link #vanillaSampler} mirrors
 * that mapping for the subset {@link DensityMapOp} models.
 *
 * <p>These tests deliberately do not bootstrap Minecraft's registries: each
 * {@code UnaryFunction$*Sampler} is a distinct class from {@code UnaryFunction},
 * so touching one never runs {@code UnaryFunction.Type}'s codec initializer or
 * its dependency on {@code Registries.DENSITY_FUNCTION}.
 */
class DensityMapDifferentialTest {

    private static final DensityMapKernels SCALAR = ScalarDensityMapKernels.INSTANCE;
    private static final DensityMapKernels VECTOR = SimdDensityMapKernels.INSTANCE;
    private static final float TAIL_CANARY = 1234.5F;

    /**
     * The same {@code Type -> sampler class} mapping real
     * {@code UnaryFunction.compileSampler} performs, restricted to the ops
     * {@link DensityMapOp} models.
     */
    private static DensitySampler vanillaSampler(DensityMapOp op, DensitySampler input) {
        return switch (op) {
            case ABS -> new UnaryFunction.AbsSampler(input);
            case SQUARE -> new UnaryFunction.SquareSampler(input);
            case CUBE -> new UnaryFunction.CubeSampler(input);
            case HALF_NEGATIVE -> new UnaryFunction.LeakyReLUSampler(input, 0.5F);
            case QUARTER_NEGATIVE -> new UnaryFunction.LeakyReLUSampler(input, 0.25F);
            case RECIPROCAL -> new UnaryFunction.ReciprocalSampler(input);
            case SQUEEZE -> new UnaryFunction.SqueezeSampler(input);
        };
    }

    private static float[] interestingValues(long seed, int extra) {
        float[] fixed = {
                0.0F, -0.0F, 1.0F, -1.0F, 0.5F, -0.5F, 2.0F, -2.0F, 0.999999F, -0.999999F, 1.000001F, -1.000001F,
                Float.MIN_VALUE, -Float.MIN_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN,
                1.0E-30F, -1.0E-30F, 1.0E30F, -1.0E30F
        };
        float[] values = new float[fixed.length + extra];
        System.arraycopy(fixed, 0, values, 0, fixed.length);
        Random random = new Random(seed);
        for (int i = fixed.length; i < values.length; i++) {
            values[i] = (float) ((random.nextDouble() - 0.5) * Math.pow(10, random.nextInt(12) - 6));
        }
        return values;
    }

    /**
     * Runs a real Mojang sampler over {@code values} and returns what it wrote.
     */
    private static float[] vanilla(DensitySampler mapped, int size) {
        DensityVolume volume = new DensityVolume(size, 1, 1, 0, 0, 0);
        DensityBuffer buffer = DensityBuffer.createUnpooled(size);
        mapped.sampleVolume(SamplerContext.EMPTY_UNCACHED, buffer, volume);

        float[] out = new float[size];
        for (int i = 0; i < size; i++) {
            out[i] = buffer.get(i);
        }
        return out;
    }

    @ParameterizedTest
    @EnumSource(DensityMapOp.class)
    void scalarAndVectorMatchRealMinecraftSampleVolume(DensityMapOp op) {
        float[] values = interestingValues(op.ordinal(), 500);
        int size = values.length;
        float[] expected = vanilla(vanillaSampler(op, new ArraySampler(values)), size);

        float[] scalarOut = values.clone();
        float[] vectorOut = values.clone();
        SCALAR.apply(scalarOut, size, op);
        VECTOR.apply(vectorOut, size, op);

        assertArrayEquals(expected, scalarOut, () -> "scalar mismatch vs real UnaryFunction sampler, op=" + op);
        assertArrayEquals(expected, vectorOut, () -> "vector mismatch vs real UnaryFunction sampler, op=" + op);
    }

    /**
     * {@code sampleValue} is a separate method body from {@code sampleVolume}
     * in every one of these records, and the Mixin only replaces the latter;
     * the kernel must agree with both.
     */
    @ParameterizedTest
    @EnumSource(DensityMapOp.class)
    void scalarAndVectorMatchRealMinecraftSampleValue(DensityMapOp op) {
        float[] values = interestingValues(op.ordinal() + 1_000L, 500);
        DensitySampler mapped = vanillaSampler(op, new ArraySampler(values));

        float[] expected = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            expected[i] = mapped.sampleValue(SamplerContext.EMPTY_UNCACHED, i, 0, 0);
        }

        float[] scalarOut = values.clone();
        float[] vectorOut = values.clone();
        SCALAR.apply(scalarOut, values.length, op);
        VECTOR.apply(vectorOut, values.length, op);

        assertArrayEquals(expected, scalarOut, () -> "scalar mismatch vs real sampleValue, op=" + op);
        assertArrayEquals(expected, vectorOut, () -> "vector mismatch vs real sampleValue, op=" + op);
    }

    /**
     * {@link #interestingValues} has a fixed length, so whether it actually
     * exercises {@code SimdDensityMapKernels}'s vectorized loop body depends on
     * this machine's vector lane width. This test computes the lane count from
     * the runtime species directly (the same construction the kernel uses --
     * {@code FloatVector} since 26.3, not {@code DoubleVector}) and targets
     * lengths immediately below, at and above one and two lane boundaries, so
     * the vectorized body is provably exercised on any platform.
     */
    @ParameterizedTest
    @EnumSource(DensityMapOp.class)
    void vectorPathBoundariesAreExercisedRegardlessOfPlatformLaneWidth(DensityMapOp op) {
        VectorSpecies<Float> species = FloatVector.SPECIES_PREFERRED;
        int lanes = species.length();

        for (int size : new int[]{1, lanes - 1, lanes, lanes + 1, 2 * lanes - 1, 2 * lanes, 2 * lanes + 1, 5 * lanes + 3}) {
            if (size <= 0) {
                continue;
            }
            Random random = new Random(op.ordinal() * 7_919L + size);
            float[] values = new float[size];
            for (int i = 0; i < size; i++) {
                values[i] = (float) ((random.nextDouble() - 0.5) * 20.0);
            }

            float[] expected = vanilla(vanillaSampler(op, new ArraySampler(values)), size);
            float[] vectorOut = values.clone();
            VECTOR.apply(vectorOut, size, op);

            assertArrayEquals(expected, vectorOut,
                    () -> "vector mismatch at lane boundary, op=" + op + " size=" + size + " lanes=" + lanes);
        }
    }

    /**
     * {@code SQUEEZE}'s reference divides by {@code 24.0F}; a vector backend
     * that instead multiplies by a precomputed {@code 1.0F / 24.0F} rounds
     * differently for a small fraction of interior (unclamped) inputs -- rare
     * enough that {@link #interestingValues}' random samples (mostly outside
     * {@code [-1, 1]}, where clamping saturates the value and hides the
     * discrepancy) can miss it by chance. This sweeps 20,000 evenly spaced
     * points densely covering the interior range specifically so that class of
     * regression cannot hide statistically. It has caught the bug twice in this
     * project's history, once here and once in {@code SimdCarverSkipKernels}.
     */
    @Test
    void squeezeAgreesWithScalarAcrossDenseInteriorSweep() {
        int n = 20_000;
        float[] values = new float[n];
        for (int i = 0; i < n; i++) {
            values[i] = (float) (-1.0 + 2.0 * i / (n - 1));
        }

        float[] scalarOut = values.clone();
        float[] vectorOut = values.clone();
        SCALAR.apply(scalarOut, n, DensityMapOp.SQUEEZE);
        VECTOR.apply(vectorOut, n, DensityMapOp.SQUEEZE);
        float[] expected = vanilla(new UnaryFunction.SqueezeSampler(new ArraySampler(values)), n);

        assertArrayEquals(expected, scalarOut, "SQUEEZE scalar mismatch across dense interior sweep");
        assertArrayEquals(expected, vectorOut, "SQUEEZE vector mismatch across dense interior sweep");
    }

    /**
     * {@code UnaryFunction.compileSampler} only ever builds a
     * {@code LeakyReLUSampler} with {@code 0.5F} or {@code 0.25F}, but the
     * record is public and its canonical constructor accepts anything, so
     * {@code UnaryFunctionSamplerMixin} forwards the sampler's actual
     * {@code negativeFactor()} to {@link DensityMapKernels#leakyReLU}. This
     * checks that path against the real record for factors vanilla never
     * produces.
     */
    @ParameterizedTest
    @ValueSource(floats = {0.5F, 0.25F, 0.0F, -0.0F, 1.0F, -3.25F, 1.0E20F, Float.NaN})
    void leakyReLUMatchesRealMinecraftForArbitraryFactors(float negativeFactor) {
        float[] values = interestingValues(Float.floatToIntBits(negativeFactor), 500);
        int size = values.length;
        float[] expected = vanilla(new UnaryFunction.LeakyReLUSampler(new ArraySampler(values), negativeFactor), size);

        float[] scalarOut = values.clone();
        float[] vectorOut = values.clone();
        SCALAR.leakyReLU(scalarOut, size, negativeFactor);
        VECTOR.leakyReLU(vectorOut, size, negativeFactor);

        assertArrayEquals(expected, scalarOut, () -> "scalar mismatch, negativeFactor=" + negativeFactor);
        assertArrayEquals(expected, vectorOut, () -> "vector mismatch, negativeFactor=" + negativeFactor);
    }

    /**
     * 26.3 hands samplers a {@code ScopedDensityBuffer} drawn from a
     * {@code DensityBufferPool}, whose capacity is rounded up to a multiple of
     * 16 while {@code size()} stays exact -- so the backing {@code float[]} the
     * Mixin passes to the kernel is routinely longer than the live sample and
     * the trailing slots still hold a previous user's data. This pins that down
     * against the real pool (the assertion below is empirical proof, not an
     * assumption) and checks that both backends stop exactly at {@code size()}.
     */
    @ParameterizedTest
    @EnumSource(DensityMapOp.class)
    void poolBackedBufferHasSpareCapacityAndKernelsRespectIt(DensityMapOp op) {
        int size = 97;
        DensityVolume volume = new DensityVolume(size, 1, 1, 0, 0, 0);
        SamplerContext context = SamplerContext.builder().useBufferArena(new DensityBufferPool(4)).build();
        DensityBuffer buffer = context.acquireBuffer(volume);

        assertTrue(buffer.capacity() > buffer.size(),
                "expected the pooled buffer capacity (" + buffer.capacity() + ") to exceed its size (" + buffer.size() + ")");
        int capacity = buffer.capacity();
        for (int i = size; i < capacity; i++) {
            buffer.set(i, TAIL_CANARY);
        }

        float[] values = Arrays.copyOf(interestingValues(op.ordinal() + 4_242L, size), size);
        vanillaSampler(op, new ArraySampler(values)).sampleVolume(context, buffer, volume);

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
        SCALAR.apply(scalarOut, size, op);
        VECTOR.apply(vectorOut, size, op);

        assertArrayEquals(expected, scalarOut, () -> "scalar wrote past size() or disagreed with vanilla, op=" + op);
        assertArrayEquals(expected, vectorOut, () -> "vector wrote past size() or disagreed with vanilla, op=" + op);
    }

    @Test
    void zeroLengthIsANoOpOnBothBackends() {
        for (DensityMapOp op : DensityMapOp.values()) {
            float[] scalarOut = {TAIL_CANARY, TAIL_CANARY};
            float[] vectorOut = {TAIL_CANARY, TAIL_CANARY};
            SCALAR.apply(scalarOut, 0, op);
            VECTOR.apply(vectorOut, 0, op);
            assertArrayEquals(new float[]{TAIL_CANARY, TAIL_CANARY}, scalarOut);
            assertArrayEquals(new float[]{TAIL_CANARY, TAIL_CANARY}, vectorOut);
        }
    }

    /**
     * Wraps a plain {@code float[]} as a real {@code DensitySampler}, indexed by
     * {@code blockX}. {@code DensitySampler.sampleVolumeNaive} traverses z, then
     * x, then y, so a {@code sizeX * 1 * 1} volume anchored at the origin maps
     * buffer index {@code i} to {@code blockX == i}.
     */
    private record ArraySampler(float[] values) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            DensitySampler.sampleVolumeNaive(context, outputBuffer, volume, this);
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return this.values[blockX];
        }
    }
}
