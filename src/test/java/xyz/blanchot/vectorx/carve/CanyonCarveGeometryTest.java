package xyz.blanchot.vectorx.carve;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import xyz.blanchot.vectorx.kernel.CarverSkipKernels;
import xyz.blanchot.vectorx.kernel.scalar.ScalarCarverSkipKernels;
import xyz.blanchot.vectorx.kernel.simd.SimdCarverSkipKernels;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanyonCarveGeometryTest {

    private static final CarverSkipKernels SCALAR = ScalarCarverSkipKernels.INSTANCE;
    private static final CarverSkipKernels VECTOR = SimdCarverSkipKernels.INSTANCE;

    private static Scenario randomScenario(Random random) {
        int minGenY = -64;
        int genDepth = 384;
        int chunkX = random.nextInt(21) - 10;
        int chunkZ = random.nextInt(21) - 10;
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);

        double x = chunkPos.getMiddleBlockX() + (random.nextDouble() * 80.0 - 40.0);
        double z = chunkPos.getMiddleBlockZ() + (random.nextDouble() * 80.0 - 40.0);
        double y = minGenY + 20 + random.nextDouble() * (genDepth - 40);

        double horizontalRadius = 0.5 + random.nextDouble() * 6.0;
        double verticalRadius = horizontalRadius * (1.0 + random.nextDouble() * 4.0);

        boolean isUpgrading = random.nextBoolean();

        float[] widthFactorPerHeight = new float[genDepth];
        float widthFactor = 1.0f;
        for (int i = 0; i < genDepth; i++) {
            if (i == 0 || random.nextInt(4) == 0) {
                widthFactor = 1.0f + random.nextFloat() * random.nextFloat();
            }
            widthFactorPerHeight[i] = widthFactor * widthFactor;
        }

        return Scenario.of(chunkPos, x, y, z, horizontalRadius, verticalRadius, minGenY, genDepth, isUpgrading, widthFactorPerHeight);
    }

    private static List<int[]> runReference(Scenario s) {
        List<int[]> carved = new ArrayList<>();
        CanyonCarveGeometry.sweepReference(s.chunkPos, s.x, s.y, s.z, s.horizontalRadius, s.verticalRadius, s.outputMinY, s.outputMaxY, s.minGenY, s.widthFactorPerHeight, (xIndex, worldY, zIndex) -> carved.add(new int[]{xIndex, worldY, zIndex}));
        return carved;
    }

    private static List<int[]> runVectorized(Scenario s, CarverSkipKernels kernel) {
        List<int[]> carved = new ArrayList<>();
        CanyonCarveGeometry.sweepVectorized(s.chunkPos, s.x, s.y, s.z, s.horizontalRadius, s.verticalRadius, s.outputMinY, s.outputMaxY, s.minGenY, s.widthFactorPerHeight, kernel, (xIndex, worldY, zIndex) -> carved.add(new int[]{xIndex, worldY, zIndex}));
        return carved;
    }

    private static void assertSamePositions(List<int[]> expected, List<int[]> actual, String label) {
        assertEquals(expected.size(), actual.size(), () -> label + ": carved position count differs");
        for (int i = 0; i < expected.size(); i++) {
            int[] e = expected.get(i);
            int[] a = actual.get(i);
            int index = i;
            assertTrue(e[0] == a[0] && e[1] == a[1] && e[2] == a[2], () -> label + ": position #" + index + " differs: expected " + java.util.Arrays.toString(e) + " actual " + java.util.Arrays.toString(a));
        }
    }

    private static int topCarvedY(List<int[]> carved) {
        assertFalse(carved.isEmpty(), "expected a non-empty carve");
        int top = Integer.MIN_VALUE;
        for (int[] pos : carved) {
            top = Math.max(top, pos[1]);
        }
        return top;
    }

    private static float[] uniformWidthFactors(int genDepth) {
        float[] widthFactors = new float[genDepth];
        java.util.Arrays.fill(widthFactors, 1.0f);
        return widthFactors;
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20})
    void vectorizedAgreesWithReferenceAcrossRealisticScenarios(int seedOffset) {
        Random random = new Random(0x43414E594F4EL + seedOffset * 31L);
        for (int trial = 0; trial < 25; trial++) {
            Scenario s = randomScenario(random);
            List<int[]> reference = runReference(s);
            List<int[]> vectorizedScalar = runVectorized(s, SCALAR);
            List<int[]> vectorizedVector = runVectorized(s, VECTOR);

            String label = "chunk=" + s.chunkPos + " x=" + s.x + " y=" + s.y + " z=" + s.z + " hr=" + s.horizontalRadius + " vr=" + s.verticalRadius + " outputY=[" + s.outputMinY + "," + s.outputMaxY + "]";
            assertSamePositions(reference, vectorizedScalar, label + " (scalar)");
            assertSamePositions(reference, vectorizedVector, label + " (vector)");
        }
    }

    @Test
    void centeredScenarioActuallyCarvesSomething() {
        ChunkPos chunkPos = new ChunkPos(0, 0);
        Scenario s = Scenario.of(chunkPos, chunkPos.getMiddleBlockX(), 64.0, chunkPos.getMiddleBlockZ(), 4.0, 10.0, -64, 384, false, uniformWidthFactors(384));

        List<int[]> reference = runReference(s);
        assertFalse(reference.isEmpty(), "expected this centered scenario to carve at least one position");
        assertSamePositions(reference, runVectorized(s, SCALAR), "centered (scalar)");
        assertSamePositions(reference, runVectorized(s, VECTOR), "centered (vector)");
    }

    @Test
    void chunkFarFromCarveCenterCarvesNothing() {
        ChunkPos chunkPos = new ChunkPos(0, 0);
        Scenario s = Scenario.of(chunkPos, 1000.0, 64.0, 1000.0, 3.0, 6.0, -64, 384, false, new float[384]);

        assertTrue(runReference(s).isEmpty());
        assertTrue(runVectorized(s, SCALAR).isEmpty());
        assertTrue(runVectorized(s, VECTOR).isEmpty());
    }

    @Test
    void collapsedVerticalRangeCarvesNothingWithoutThrowing() {
        ChunkPos chunkPos = new ChunkPos(0, 0);
        Scenario s = Scenario.of(chunkPos, 8.0, -64.0, 8.0, 3.0, 0.1, -64, 384, false, new float[384]);

        assertTrue(runReference(s).isEmpty());
        assertTrue(runVectorized(s, SCALAR).isEmpty());
        assertTrue(runVectorized(s, VECTOR).isEmpty());
    }

    @Test
    void upgradingChunkReachesSevenBlocksHigher() {
        ChunkPos chunkPos = new ChunkPos(0, 0);
        int minGenY = -64;
        int genDepth = 384;
        int ceilingY = minGenY + genDepth - 1;
        float[] widthFactors = uniformWidthFactors(genDepth);
        Scenario normal = Scenario.of(chunkPos, 8.0, ceilingY, 8.0, 4.0, 12.0, minGenY, genDepth, false, widthFactors);
        Scenario upgrading = Scenario.of(chunkPos, 8.0, ceilingY, 8.0, 4.0, 12.0, minGenY, genDepth, true, widthFactors);

        assertEquals(ceilingY - 7, normal.outputMaxY);
        assertEquals(ceilingY, upgrading.outputMaxY);

        int normalTop = topCarvedY(runReference(normal));
        int upgradingTop = topCarvedY(runReference(upgrading));
        assertEquals(normal.outputMaxY, normalTop);
        assertEquals(upgrading.outputMaxY, upgradingTop);

        assertSamePositions(runReference(normal), runVectorized(normal, VECTOR), "normal");
        assertSamePositions(runReference(upgrading), runVectorized(upgrading, VECTOR), "upgrading");
    }

    private record Scenario(ChunkPos chunkPos, double x, double y, double z, double horizontalRadius,
                            double verticalRadius, int outputMinY, int outputMaxY, int minGenY,
                            float[] widthFactorPerHeight) {
        static Scenario of(ChunkPos chunkPos, double x, double y, double z, double horizontalRadius, double verticalRadius, int minGenY, int genDepth, boolean isUpgrading, float[] widthFactorPerHeight) {
            int protectedBlocksOnTop = isUpgrading ? 0 : 7;
            return new Scenario(chunkPos, x, y, z, horizontalRadius, verticalRadius, minGenY + 1, minGenY + genDepth - 1 - protectedBlocksOnTop, minGenY, widthFactorPerHeight);
        }
    }
}
