package xyz.blanchot.vectorx.kernel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import xyz.blanchot.vectorx.kernel.scalar.ScalarCarverSkipKernels;
import xyz.blanchot.vectorx.kernel.simd.SimdCarverSkipKernels;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CarverSkipDifferentialTest {

    private static final CarverSkipKernels SCALAR = ScalarCarverSkipKernels.INSTANCE;
    private static final CarverSkipKernels VECTOR = SimdCarverSkipKernels.INSTANCE;

    private static final int[] Y_LENGTHS = {0, 1, 2, 3, 7, 8, 9, 15, 16, 17, 25, 31, 32, 40, 63, 64, 65};

    private static boolean[] trim(boolean[] array, int n) {
        boolean[] result = new boolean[n];
        System.arraycopy(array, 0, result, 0, n);
        return result;
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12})
    void scalarAndVectorAgreeAcrossRandomInputs(int seedOffset) {
        Random random = new Random(0x43414E594F4EL + seedOffset);

        for (int n : Y_LENGTHS) {
            int minGenY = -64;
            int minY = 60;
            int maxY = minY + n;
            double horizSum = random.nextDouble() * 1.5;
            double y = 64.0 + random.nextDouble() * 10.0 - 5.0;
            double verticalRadius = 0.5 + random.nextDouble() * 20.0;

            int depth = maxY - minGenY + 4;
            float[] widthFactorPerHeight = new float[depth];
            for (int i = 0; i < depth; i++) {
                widthFactorPerHeight[i] = random.nextFloat() * 3.0f;
            }

            boolean[] scalarOut = new boolean[Math.max(n, 1)];
            boolean[] vectorOut = new boolean[Math.max(n, 1)];
            SCALAR.canyonSkipMask(horizSum, y, verticalRadius, widthFactorPerHeight, minGenY, minY, maxY, scalarOut);
            VECTOR.canyonSkipMask(horizSum, y, verticalRadius, widthFactorPerHeight, minGenY, minY, maxY, vectorOut);

            boolean[] scalarTrimmed = trim(scalarOut, n);
            boolean[] vectorTrimmed = trim(vectorOut, n);
            assertArrayEquals(scalarTrimmed, vectorTrimmed, () -> "mismatch n=" + n + " horizSum=" + horizSum + " y=" + y + " verticalRadius=" + verticalRadius);
        }
    }

    @Test
    void everyRemainderAroundVectorWidthIsExercised() {
        Random random = new Random(0xB0DA71);
        int maxWidthGuess = 16;
        for (int n = 0; n <= maxWidthGuess * 2 + 3; n++) {
            int minGenY = 0;
            int minY = 100;
            int maxY = minY + n;
            double horizSum = 0.3 + random.nextDouble() * 0.6;
            double y = 100.0 + random.nextDouble() * 5.0;
            double verticalRadius = 1.0 + random.nextDouble() * 15.0;

            float[] widthFactorPerHeight = new float[maxY - minGenY + 1];
            for (int i = 0; i < widthFactorPerHeight.length; i++) {
                widthFactorPerHeight[i] = random.nextFloat() * 2.0f;
            }

            boolean[] scalarOut = new boolean[Math.max(n, 1)];
            boolean[] vectorOut = new boolean[Math.max(n, 1)];
            SCALAR.canyonSkipMask(horizSum, y, verticalRadius, widthFactorPerHeight, minGenY, minY, maxY, scalarOut);
            VECTOR.canyonSkipMask(horizSum, y, verticalRadius, widthFactorPerHeight, minGenY, minY, maxY, vectorOut);

            int finalN = n;
            assertArrayEquals(trim(scalarOut, n), trim(vectorOut, n), () -> "mismatch at n=" + finalN);
        }
    }

    @Test
    void boundaryValuesAgreeWithNonRoundY() {
        int minGenY = 0;
        int minY = 0;
        int n = 40;
        double horizSum = 0.5;
        double verticalRadius = 10.0;
        double y = 64.37;

        float[] widthFactorPerHeight = new float[n - minGenY + 1];
        for (int worldY = minY + 1; worldY <= n; worldY++) {
            double yd = (worldY - 0.5 - y) / verticalRadius;
            double target = 1.0 - yd * yd / 6.0;
            double nudge = (worldY % 2 == 0 ? 1 : -1) * 4.0 * Math.ulp(1.0);
            widthFactorPerHeight[worldY - minGenY - 1] = (float) ((target + nudge) / horizSum);
        }

        boolean[] scalarOut = new boolean[n];
        boolean[] vectorOut = new boolean[n];
        SCALAR.canyonSkipMask(horizSum, y, verticalRadius, widthFactorPerHeight, minGenY, minY, n, scalarOut);
        VECTOR.canyonSkipMask(horizSum, y, verticalRadius, widthFactorPerHeight, minGenY, minY, n, vectorOut);

        assertArrayEquals(scalarOut, vectorOut);
    }

    @Test
    void zeroLengthProducesNoOutput() {
        float[] widthFactorPerHeight = new float[10];
        boolean[] scalarOut = new boolean[0];
        boolean[] vectorOut = new boolean[0];
        SCALAR.canyonSkipMask(0.5, 64.0, 5.0, widthFactorPerHeight, 0, 60, 60, scalarOut);
        VECTOR.canyonSkipMask(0.5, 64.0, 5.0, widthFactorPerHeight, 0, 60, 60, vectorOut);
        assertEquals(0, scalarOut.length);
        assertEquals(0, vectorOut.length);
    }

    @Test
    void outputShorterThanRangeThrows() {
        float[] widthFactorPerHeight = new float[10];
        assertThrows(IndexOutOfBoundsException.class, () -> SCALAR.canyonSkipMask(0.5, 64.0, 5.0, widthFactorPerHeight, 0, 60, 65, new boolean[2]));
        assertThrows(IndexOutOfBoundsException.class, () -> VECTOR.canyonSkipMask(0.5, 64.0, 5.0, widthFactorPerHeight, 0, 60, 65, new boolean[2]));
    }

    @Test
    void nullArgumentsThrowIdenticallyOnBothBackends() {
        assertThrows(NullPointerException.class, () -> SCALAR.canyonSkipMask(0.5, 64.0, 5.0, null, 0, 60, 65, new boolean[5]));
        assertThrows(NullPointerException.class, () -> VECTOR.canyonSkipMask(0.5, 64.0, 5.0, null, 0, 60, 65, new boolean[5]));

        float[] widthFactorPerHeight = new float[10];
        assertThrows(NullPointerException.class, () -> SCALAR.canyonSkipMask(0.5, 64.0, 5.0, widthFactorPerHeight, 0, 60, 65, null));
        assertThrows(NullPointerException.class, () -> VECTOR.canyonSkipMask(0.5, 64.0, 5.0, widthFactorPerHeight, 0, 60, 65, null));
    }
}
