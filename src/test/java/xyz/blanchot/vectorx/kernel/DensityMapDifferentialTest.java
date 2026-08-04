package xyz.blanchot.vectorx.kernel;

import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.VectorSpecies;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import xyz.blanchot.vectorx.kernel.scalar.ScalarDensityMapKernels;
import xyz.blanchot.vectorx.kernel.simd.SimdDensityMapKernels;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Differential tests for {@link DensityMapKernels} against real Minecraft
 * 26.2's {@code net.minecraft.world.level.levelgen.DensityFunction}. Ground
 * truth is obtained exclusively through the real, public
 * {@code DensityFunction} default methods ({@code abs()}, {@code square()},
 * {@code cube()}, {@code halfNegative()}, {@code quarterNegative()},
 * {@code invert()}, {@code squeeze()}) wrapping an unmodified real
 * {@code DensityFunction} implementation -- never a reimplemented copy of
 * Mojang's private {@code Mapped.transform} switch.
 */
class DensityMapDifferentialTest {

    private static final DensityMapKernels SCALAR = ScalarDensityMapKernels.INSTANCE;
    private static final DensityMapKernels VECTOR = SimdDensityMapKernels.INSTANCE;

    /**
     * Real Minecraft's {@code DensityFunction.CODEC} static initializer pulls
     * in {@code Registries.DENSITY_FUNCTION}, which asserts the vanilla
     * registry bootstrap has already run (see {@code Bootstrap.checkBootstrapCalled}).
     * Unlike {@code SimpleBitStorage} in the packed-bits candidate, this class
     * cannot be touched in a bare JUnit JVM without first replicating this
     * part of Minecraft's real startup sequence -- a genuine extra cost/risk
     * this candidate carries that the previous one did not.
     */
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static DensityFunction realMapped(DensityMapOp op, DensityFunction input) {
        return switch (op) {
            case ABS -> input.abs();
            case SQUARE -> input.square();
            case CUBE -> input.cube();
            case HALF_NEGATIVE -> input.halfNegative();
            case QUARTER_NEGATIVE -> input.quarterNegative();
            case INVERT -> input.invert();
            case SQUEEZE -> input.squeeze();
        };
    }

    private static double[] interestingValues() {
        Random random = new Random(42);
        double[] fixed = {
                0.0, -0.0, 1.0, -1.0, 0.5, -0.5, 2.0, -2.0, 0.999999, -0.999999, 1.000001, -1.000001,
                Double.MIN_VALUE, -Double.MIN_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN,
                1.0E-300, -1.0E-300, 1.0E300, -1.0E300
        };
        double[] values = new double[fixed.length + 500];
        System.arraycopy(fixed, 0, values, 0, fixed.length);
        for (int i = fixed.length; i < values.length; i++) {
            values[i] = (random.nextDouble() - 0.5) * Math.pow(10, random.nextInt(20) - 10);
        }
        return values;
    }

    @ParameterizedTest
    @EnumSource(DensityMapOp.class)
    void scalarAndVectorMatchRealMinecraftDensityFunctionComputePerPoint(DensityMapOp op) {
        double[] values = interestingValues();
        ArraySource source = new ArraySource(values);
        DensityFunction mapped = realMapped(op, source);

        double[] expected = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            expected[i] = mapped.compute(new DensityFunction.SinglePointContext(i, 0, 0));
        }

        double[] scalarOut = values.clone();
        double[] vectorOut = values.clone();
        SCALAR.apply(scalarOut, op);
        VECTOR.apply(vectorOut, op);

        assertArrayEquals(expected, scalarOut, () -> "scalar mismatch vs real DensityFunction.compute, op=" + op);
        assertArrayEquals(expected, vectorOut, () -> "vector mismatch vs real DensityFunction.compute, op=" + op);
    }

    @ParameterizedTest
    @EnumSource(DensityMapOp.class)
    void scalarAndVectorMatchRealMinecraftDensityFunctionFillArray(DensityMapOp op) {
        double[] values = interestingValues();
        ArraySource source = new ArraySource(values);
        DensityFunction mapped = realMapped(op, source);

        double[] expected = new double[values.length];
        DensityFunction.ContextProvider contextProvider = new DensityFunction.ContextProvider() {
            @Override
            public DensityFunction.@NonNull FunctionContext forIndex(int index) {
                return new DensityFunction.SinglePointContext(index, 0, 0);
            }

            @Override
            public void fillAllDirectly(double[] output, @NonNull DensityFunction function) {
                for (int i = 0; i < output.length; i++) {
                    output[i] = function.compute(new DensityFunction.SinglePointContext(i, 0, 0));
                }
            }
        };
        mapped.fillArray(expected, contextProvider);

        double[] scalarOut = values.clone();
        double[] vectorOut = values.clone();
        SCALAR.apply(scalarOut, op);
        VECTOR.apply(vectorOut, op);

        assertArrayEquals(expected, scalarOut, () -> "scalar mismatch vs real DensityFunction.fillArray, op=" + op);
        assertArrayEquals(expected, vectorOut, () -> "vector mismatch vs real DensityFunction.fillArray, op=" + op);
    }

    /**
     * {@link #interestingValues()} has a fixed length, so whether it actually
     * exercises {@code SimdDensityMapKernels}'s vectorized loop body depends
     * on this machine's vector lane width. This test computes the actual
     * lane count from the runtime species directly (the same construction
     * the kernel itself uses) and targets array lengths immediately below,
     * at, and above one and two lane-count boundaries, so the vectorized
     * loop body is provably exercised regardless of the platform this test
     * happens to run on.
     */
    @ParameterizedTest
    @EnumSource(DensityMapOp.class)
    void vectorPathBoundariesAreExercisedRegardlessOfPlatformLaneWidth(DensityMapOp op) {
        VectorSpecies<Double> species = DoubleVector.SPECIES_PREFERRED;
        int lanes = species.length();

        for (int size : new int[]{0, 1, lanes - 1, lanes, lanes + 1, 2 * lanes - 1, 2 * lanes, 2 * lanes + 1, 5 * lanes + 3}) {
            if (size < 0) {
                continue;
            }
            Random random = new Random(op.ordinal() * 7_919L + size);
            double[] values = new double[size];
            for (int i = 0; i < size; i++) {
                values[i] = (random.nextDouble() - 0.5) * 20.0;
            }

            ArraySource source = new ArraySource(values);
            DensityFunction mapped = realMapped(op, source);
            double[] expected = new double[size];
            for (int i = 0; i < size; i++) {
                expected[i] = mapped.compute(new DensityFunction.SinglePointContext(i, 0, 0));
            }

            double[] vectorOut = values.clone();
            VECTOR.apply(vectorOut, op);

            assertArrayEquals(expected, vectorOut,
                    () -> "vector mismatch at lane boundary, op=" + op + " size=" + size + " lanes=" + lanes);
        }
    }

    @Test
    void emptyArrayIsANoOpOnBothBackends() {
        for (DensityMapOp op : DensityMapOp.values()) {
            double[] scalarOut = new double[0];
            double[] vectorOut = new double[0];
            SCALAR.apply(scalarOut, op);
            VECTOR.apply(vectorOut, op);
            assertEquals(0, scalarOut.length);
            assertEquals(0, vectorOut.length);
        }
    }

    /**
     * Wraps a plain {@code double[]} as a real {@code DensityFunction}, indexed by {@code blockX()}.
     */
    private record ArraySource(double[] values) implements DensityFunction.SimpleFunction {
        @Override
        public double compute(DensityFunction.FunctionContext context) {
            return this.values[context.blockX()];
        }

        @Override
        public double minValue() {
            return Double.NEGATIVE_INFINITY;
        }

        @Override
        public double maxValue() {
            return Double.POSITIVE_INFINITY;
        }

        @Override
        public @NonNull KeyDispatchDataCodec<? extends DensityFunction> codec() {
            throw new UnsupportedOperationException("not needed for this test: never serialized");
        }
    }
}
