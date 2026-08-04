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
import xyz.blanchot.vectorx.kernel.scalar.ScalarClampKernels;
import xyz.blanchot.vectorx.kernel.simd.SimdClampKernels;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Differential tests for {@link ClampKernels} against real Minecraft 26.2's
 * {@code DensityFunctions.Clamp}, reached exclusively through the real,
 * public {@code DensityFunction.clamp(double, double)} default method.
 */
class ClampDifferentialTest {

    private static final ClampKernels SCALAR = ScalarClampKernels.INSTANCE;
    private static final ClampKernels VECTOR = SimdClampKernels.INSTANCE;
    private static final double[][] BOUNDS = {{-1.0, 1.0}, {-100.0, 80.0}, {0.0, 1.0}, {-5.5, 5.5}};

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static double[] interestingValues(long seed, int extra) {
        double[] fixed = {
                0.0, -0.0, 1.0, -1.0, 0.5, -0.5, 2.0, -2.0, 0.999999, -0.999999, 1.000001, -1.000001,
                Double.MIN_VALUE, -Double.MIN_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN
        };
        double[] values = new double[fixed.length + extra];
        System.arraycopy(fixed, 0, values, 0, fixed.length);
        Random random = new Random(seed);
        for (int i = fixed.length; i < values.length; i++) {
            values[i] = (random.nextDouble() - 0.5) * Math.pow(10, random.nextInt(20) - 10);
        }
        return values;
    }

    @Test
    void scalarAndVectorMatchRealMinecraftClamp() {
        for (double[] bound : BOUNDS) {
            double min = bound[0];
            double max = bound[1];
            for (int size : new int[]{0, 1, 3, 8, 17, 64, 257}) {
                double[] values = java.util.Arrays.copyOf(interestingValues(size + 1, size), size);

                DensityFunction source = new ArraySource(values);
                DensityFunction clamped = source.clamp(min, max);

                double[] expected = new double[size];
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
                clamped.fillArray(expected, contextProvider);

                double[] scalarOut = values.clone();
                double[] vectorOut = values.clone();
                SCALAR.clampInPlace(scalarOut, min, max);
                VECTOR.clampInPlace(vectorOut, min, max);

                assertArrayEquals(expected, scalarOut,
                        () -> "scalar mismatch vs real DensityFunction.clamp, size=" + size + " bounds=" + min + ".." + max);
                assertArrayEquals(expected, vectorOut,
                        () -> "vector mismatch vs real DensityFunction.clamp, size=" + size + " bounds=" + min + ".." + max);
            }
        }
    }

    @Test
    void vectorPathBoundariesAreExercisedRegardlessOfPlatformLaneWidth() {
        VectorSpecies<Double> species = DoubleVector.SPECIES_PREFERRED;
        int lanes = species.length();

        for (int size : new int[]{0, 1, lanes - 1, lanes, lanes + 1, 2 * lanes - 1, 2 * lanes, 2 * lanes + 1, 5 * lanes + 3}) {
            if (size < 0) {
                continue;
            }
            Random random = new Random(7_919L + size);
            double[] values = new double[size];
            for (int i = 0; i < size; i++) {
                values[i] = (random.nextDouble() - 0.5) * 20.0;
            }

            double[] expected = values.clone();
            for (int i = 0; i < size; i++) {
                expected[i] = ScalarClampKernels.transform(expected[i], -1.0, 1.0);
            }

            double[] vectorOut = values.clone();
            VECTOR.clampInPlace(vectorOut, -1.0, 1.0);

            assertArrayEquals(expected, vectorOut, () -> "vector mismatch at lane boundary, size=" + size + " lanes=" + lanes);
        }
    }

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
