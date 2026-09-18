package xyz.blanchot.vectorx.kernel;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import xyz.blanchot.vectorx.kernel.scalar.ScalarDensityBinaryKernels;
import xyz.blanchot.vectorx.kernel.simd.SimdDensityBinaryKernels;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Differential tests for {@link DensityBinaryKernels} against the real sampler
 * records nested in Minecraft 26.3's
 * {@code net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction}.
 * Ground truth is those unmodified records, constructed directly over stub
 * input samplers -- never a reimplemented copy of Mojang's formulas.
 *
 * <p><b>{@code sampleVolume} is the only oracle these kernels can be held to,
 * and that is a property of vanilla, not a shortcut taken here.</b> Several of
 * these records implement their two methods differently, so 26.3 is internally
 * inconsistent for some inputs:
 * <ul>
 *   <li>all four min/max records: {@code sampleVolume} writes conditionally
 *       ({@code if (candidate < current) current = candidate}) while
 *       {@code sampleValue} calls {@code Math.min}/{@code Math.max}, and the
 *       two disagree on signed zeros -- see
 *       {@link #vanillaDisagreesWithItselfOnSignedZero()};</li>
 *   <li>{@code MulSampler} and {@code DivSampler}: {@code sampleValue}
 *       short-circuits {@code left == 0.0F} to {@code 0.0F} while
 *       {@code sampleVolume} does not, so they disagree whenever the left
 *       operand is zero and the right is infinite or NaN -- see
 *       {@link #vanillaDisagreesWithItselfOnZeroTimesInfinity()}.</li>
 * </ul>
 * VectorX only ever replaces {@code sampleVolume}, so the kernels match
 * {@code sampleVolume}. The {@code sampleValue} cross-checks below are
 * restricted to the shapes where vanilla's two paths do agree.
 */
class DensityBinaryDifferentialTest {

    private static final DensityBinaryKernels SCALAR = ScalarDensityBinaryKernels.INSTANCE;
    private static final DensityBinaryKernels VECTOR = SimdDensityBinaryKernels.INSTANCE;

    private static DensitySampler twoBufferSampler(DensityBinaryOp op, DensitySampler left, DensitySampler right) {
        return switch (op) {
            case ADD -> new BinaryFunction.AddSampler(left, right);
            case SUB -> new BinaryFunction.SubSampler(left, right);
            case MUL -> new BinaryFunction.MulSampler(left, right);
            case DIV -> new BinaryFunction.DivSampler(left, right);
            // The third component only guards sampleValue's short circuit and is
            // unused by sampleVolume; an infinity disables it either way.
            case MIN -> new BinaryFunction.MinSampler(left, right, Float.NEGATIVE_INFINITY);
            case MAX -> new BinaryFunction.MaxSampler(left, right, Float.POSITIVE_INFINITY);
        };
    }

    private static DensitySampler constSampler(DensityBinaryOp op, DensitySampler left, float operand) {
        return switch (op) {
            case ADD -> new BinaryFunction.ConstAddSampler(left, operand);
            case SUB -> new BinaryFunction.ConstSubSampler(operand, left);
            case MUL -> new BinaryFunction.ConstMulSampler(left, operand);
            case DIV -> new BinaryFunction.ConstDivSampler(operand, left);
            case MIN -> new BinaryFunction.ConstMinSampler(left, operand);
            case MAX -> new BinaryFunction.ConstMaxSampler(left, operand);
        };
    }

    private static float[] interestingValues(long seed, int count) {
        float[] fixed = {
                0.0F, -0.0F, 1.0F, -1.0F, 0.5F, -0.5F, 2.0F, -2.0F,
                Float.MIN_VALUE, -Float.MIN_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN
        };
        float[] values = new float[count];
        Random random = new Random(seed);
        for (int i = 0; i < count; i++) {
            values[i] = i < fixed.length
                    ? fixed[i]
                    : (float) ((random.nextDouble() - 0.5) * Math.pow(10, random.nextInt(12) - 6));
        }
        return values;
    }

    /** Runs a real Mojang sampler over a volume and returns what it wrote. */
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

    @ParameterizedTest
    @EnumSource(DensityBinaryOp.class)
    void twoBufferMatchesRealMinecraftSampleVolume(DensityBinaryOp op) {
        float[] left = interestingValues(op.ordinal(), 500);
        float[] right = interestingValues(op.ordinal() + 7_000L, 500);
        int size = left.length;

        float[] expected = vanilla(twoBufferSampler(op, new ArraySampler(left), new ArraySampler(right)), size);

        float[] scalarOut = left.clone();
        float[] vectorOut = left.clone();
        SCALAR.applyBuffer(scalarOut, right, size, op);
        VECTOR.applyBuffer(vectorOut, right, size, op);

        assertArrayEquals(expected, scalarOut, () -> "scalar mismatch vs real two-buffer sampler, op=" + op);
        assertArrayEquals(expected, vectorOut, () -> "vector mismatch vs real two-buffer sampler, op=" + op);
    }

    @ParameterizedTest
    @EnumSource(DensityBinaryOp.class)
    void constMatchesRealMinecraftSampleVolume(DensityBinaryOp op) {
        float[] values = interestingValues(op.ordinal() + 21_000L, 500);
        int size = values.length;

        for (float operand : new float[]{0.0F, -0.0F, 1.0F, -1.0F, 0.37F, Float.NaN}) {
            float[] expected = vanilla(constSampler(op, new ArraySampler(values), operand), size);

            float[] scalarOut = values.clone();
            float[] vectorOut = values.clone();
            SCALAR.applyConst(scalarOut, size, op, operand);
            VECTOR.applyConst(vectorOut, size, op, operand);

            assertArrayEquals(expected, scalarOut,
                    () -> "scalar mismatch vs real const sampler, op=" + op + " operand=" + operand);
            assertArrayEquals(expected, vectorOut,
                    () -> "vector mismatch vs real const sampler, op=" + op + " operand=" + operand);
        }
    }

    /**
     * For the two-buffer ops whose vanilla code paths agree, the kernels must
     * agree with both. MIN, MAX, MUL and DIV are excluded on purpose: their
     * {@code sampleValue} takes a shortcut {@code sampleVolume} does not. See
     * the class javadoc.
     */
    @ParameterizedTest
    @EnumSource(value = DensityBinaryOp.class, names = {"ADD", "SUB"})
    void twoBufferMatchesRealMinecraftSampleValue(DensityBinaryOp op) {
        float[] left = interestingValues(op.ordinal() + 33_000L, 400);
        float[] right = interestingValues(op.ordinal() + 44_000L, 400);
        DensitySampler sampler = twoBufferSampler(op, new ArraySampler(left), new ArraySampler(right));

        float[] expected = new float[left.length];
        for (int i = 0; i < left.length; i++) {
            expected[i] = sampler.sampleValue(SamplerContext.EMPTY_UNCACHED, i, 0, 0);
        }

        float[] scalarOut = left.clone();
        float[] vectorOut = left.clone();
        SCALAR.applyBuffer(scalarOut, right, left.length, op);
        VECTOR.applyBuffer(vectorOut, right, left.length, op);

        assertArrayEquals(expected, scalarOut, () -> "scalar mismatch vs real sampleValue, op=" + op);
        assertArrayEquals(expected, vectorOut, () -> "vector mismatch vs real sampleValue, op=" + op);
    }

    /**
     * Unlike their two-buffer counterparts, none of the {@code Const*}
     * arithmetic samplers short-circuit, so for those four ops vanilla's two
     * paths do agree and the kernels must match both.
     */
    @ParameterizedTest
    @EnumSource(value = DensityBinaryOp.class, names = {"ADD", "SUB", "MUL", "DIV"})
    void constMatchesRealMinecraftSampleValue(DensityBinaryOp op) {
        float[] values = interestingValues(op.ordinal() + 55_000L, 400);
        float operand = 0.37F;
        DensitySampler sampler = constSampler(op, new ArraySampler(values), operand);

        float[] expected = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            expected[i] = sampler.sampleValue(SamplerContext.EMPTY_UNCACHED, i, 0, 0);
        }

        float[] scalarOut = values.clone();
        float[] vectorOut = values.clone();
        SCALAR.applyConst(scalarOut, values.length, op, operand);
        VECTOR.applyConst(vectorOut, values.length, op, operand);

        assertArrayEquals(expected, scalarOut, () -> "scalar mismatch vs real const sampleValue, op=" + op);
        assertArrayEquals(expected, vectorOut, () -> "vector mismatch vs real const sampleValue, op=" + op);
    }

    /**
     * The second vanilla self-inconsistency: {@code MulSampler} and
     * {@code DivSampler} return {@code 0.0F} from {@code sampleValue} whenever
     * the left operand is zero, without ever consulting the right one, while
     * {@code sampleVolume} multiplies or divides unconditionally. With a zero
     * left operand and an infinite right one the two paths therefore return
     * {@code 0.0F} and {@code NaN} respectively. The kernels follow
     * {@code sampleVolume}, which is the method the Mixins replace.
     */
    @Test
    void vanillaDisagreesWithItselfOnZeroTimesInfinity() {
        float[] left = {0.0F};
        float[] right = {Float.POSITIVE_INFINITY};

        BinaryFunction.MulSampler sampler =
                new BinaryFunction.MulSampler(new ArraySampler(left), new ArraySampler(right));

        float viaVolume = vanilla(sampler, 1)[0];
        float viaValue = sampler.sampleValue(SamplerContext.EMPTY_UNCACHED, 0, 0, 0);

        assertEquals(Float.NaN, viaVolume, "sampleVolume multiplies unconditionally: 0 * inf is NaN");
        assertEquals(0.0F, viaValue, "sampleValue short-circuits a zero left operand to 0.0f -- if this "
                + "ever fails, Mojang made the two paths consistent and the kernels should be revisited");

        float[] scalarOut = left.clone();
        float[] vectorOut = left.clone();
        SCALAR.applyBuffer(scalarOut, right, 1, DensityBinaryOp.MUL);
        VECTOR.applyBuffer(vectorOut, right, 1, DensityBinaryOp.MUL);

        assertEquals(Float.NaN, scalarOut[0], "scalar kernel must follow sampleVolume");
        assertEquals(Float.NaN, vectorOut[0], "vector kernel must follow sampleVolume");
    }

    /**
     * Pins down the vanilla inconsistency this project has to choose a side of.
     * With {@code current = +0.0F} and {@code candidate = -0.0F},
     * {@code ConstMinSampler.sampleVolume}'s {@code if (right < get(i))} is
     * false, so it keeps {@code +0.0F}; its own {@code sampleValue} calls
     * {@code Math.min}, which is specified to return {@code -0.0F}. VectorX
     * replaces {@code sampleVolume}, so both kernels must reproduce
     * {@code +0.0F} -- a backend that reached for
     * {@code VectorOperators.MIN} would silently produce the other one.
     */
    @Test
    void vanillaDisagreesWithItselfOnSignedZero() {
        float[] values = {0.0F, -0.0F};
        BinaryFunction.ConstMinSampler sampler =
                new BinaryFunction.ConstMinSampler(new ArraySampler(values), -0.0F);

        float[] viaVolume = vanilla(sampler, values.length);
        float viaValue = sampler.sampleValue(SamplerContext.EMPTY_UNCACHED, 0, 0, 0);

        assertEquals(Float.floatToIntBits(0.0F), Float.floatToIntBits(viaVolume[0]),
                "sampleVolume's conditional write should keep +0.0f");
        assertEquals(Float.floatToIntBits(-0.0F), Float.floatToIntBits(viaValue),
                "sampleValue's Math.min should return -0.0f -- if this ever fails, Mojang "
                        + "made the two paths consistent and the kernels should be revisited");

        float[] scalarOut = values.clone();
        float[] vectorOut = values.clone();
        SCALAR.applyConst(scalarOut, values.length, DensityBinaryOp.MIN, -0.0F);
        VECTOR.applyConst(vectorOut, values.length, DensityBinaryOp.MIN, -0.0F);

        assertEquals(Float.floatToIntBits(0.0F), Float.floatToIntBits(scalarOut[0]),
                "scalar kernel must follow sampleVolume, not Math.min");
        assertEquals(Float.floatToIntBits(0.0F), Float.floatToIntBits(vectorOut[0]),
                "vector kernel must follow sampleVolume, not Math.min");
    }

    /**
     * The kernels must leave the slots past {@code length} untouched: the array
     * they are handed is a pooled buffer's backing storage, whose capacity runs
     * past the live sample.
     */
    @Test
    void kernelsRespectLengthAndLeaveTheTailAlone() {
        int size = 97;
        int capacity = size + 13;
        float canary = 1234.5F;

        for (DensityBinaryOp op : DensityBinaryOp.values()) {
            float[] src = new float[capacity];
            float[] right = new float[capacity];
            Random random = new Random(op.ordinal());
            for (int i = 0; i < capacity; i++) {
                src[i] = i < size ? (float) (random.nextDouble() - 0.5) : canary;
                right[i] = i < size ? (float) (random.nextDouble() - 0.5) : canary;
            }

            float[] scalarOut = src.clone();
            float[] vectorOut = src.clone();
            SCALAR.applyBuffer(scalarOut, right, size, op);
            VECTOR.applyBuffer(vectorOut, right, size, op);

            assertArrayEquals(scalarOut, vectorOut, () -> "backends diverged, op=" + op);
            for (int i = size; i < capacity; i++) {
                assertEquals(canary, vectorOut[i], () -> "vector backend wrote past length, op=" + op);
                assertEquals(canary, scalarOut[i], () -> "scalar backend wrote past length, op=" + op);
            }
        }
    }

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
