package xyz.blanchot.vectorx.carve;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.CarvingMask;
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

/**
 * End-to-end differential test for {@link CanyonCarveGeometry}: does
 * {@link CanyonCarveGeometry#sweepVectorized}, the exact code
 * {@code CanyonWorldCarverMixin} runs in production, agree with
 * {@link CanyonCarveGeometry#sweepReference} -- a separate transliteration
 * of real vanilla's {@code WorldCarver.carveEllipsoid} /
 * {@code CanyonWorldCarver.shouldSkip} -- on which exact positions get
 * carved, in what order, for realistic canyon-carve scenarios.
 *
 * <p>This is what {@code CarverSkipDifferentialTest} deliberately does NOT
 * cover: that test only checks the skip-mask math in isolation (scalar vs.
 * vector kernel, called directly with synthetic inputs). This test drives
 * the full bounds computation, X/Z/Y traversal, and mask read/write
 * interaction -- the part that was hand-copied into the Mixin -- against an
 * independent oracle, in-process and deterministic (no server session
 * involved, unlike the live NBT diffing this replaces).
 */
class CanyonCarveGeometryTest {

    private static final CarverSkipKernels SCALAR = ScalarCarverSkipKernels.INSTANCE;
    private static final CarverSkipKernels VECTOR = SimdCarverSkipKernels.INSTANCE;

    private static Scenario randomScenario(Random random) {
        int minGenY = -64;
        int genDepth = 384;
        int chunkX = random.nextInt(21) - 10;
        int chunkZ = random.nextInt(21) - 10;
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);

        // Canyon centers are frequently outside the target chunk -- carveEllipsoid
        // is called once per step along the whole tunnel, most of whose ellipsoids
        // land in neighboring chunks. Cover both near-center and near-cutoff cases.
        double x = chunkPos.getMiddleBlockX() + (random.nextDouble() * 80.0 - 40.0);
        double z = chunkPos.getMiddleBlockZ() + (random.nextDouble() * 80.0 - 40.0);
        double y = minGenY + 20 + random.nextDouble() * (genDepth - 40);

        double horizontalRadius = 0.5 + random.nextDouble() * 6.0;
        double verticalRadius = horizontalRadius * (1.0 + random.nextDouble() * 4.0);

        boolean isUpgrading = random.nextBoolean();
        boolean debugEnabled = random.nextInt(10) == 0;

        float[] widthFactorPerHeight = new float[genDepth];
        float widthFactor = 1.0f;
        for (int i = 0; i < genDepth; i++) {
            if (i == 0 || random.nextInt(4) == 0) {
                widthFactor = 1.0f + random.nextFloat() * random.nextFloat();
            }
            widthFactorPerHeight[i] = widthFactor * widthFactor;
        }

        List<int[]> preExisting = new ArrayList<>();
        int preExistingCount = random.nextInt(40);
        for (int i = 0; i < preExistingCount; i++) {
            int xi = random.nextInt(16);
            int zi = random.nextInt(16);
            int worldY = minGenY + random.nextInt(genDepth);
            preExisting.add(new int[]{xi, worldY, zi});
        }

        return new Scenario(chunkPos, x, y, z, horizontalRadius, verticalRadius,
                minGenY, genDepth, isUpgrading, debugEnabled, widthFactorPerHeight, preExisting);
    }

    private static CarvingMask freshMask(Scenario s) {
        CarvingMask mask = new CarvingMask(s.genDepth, s.minGenY);
        for (int[] bit : s.preExistingMaskBits) {
            mask.set(bit[0], bit[1], bit[2]);
        }
        return mask;
    }

    private static List<int[]> runReference(Scenario s) {
        List<int[]> carved = new ArrayList<>();
        boolean any = CanyonCarveGeometry.sweepReference(s.chunkPos, s.x, s.y, s.z,
                s.horizontalRadius, s.verticalRadius, s.minGenY, s.genDepth, s.isUpgrading, s.debugEnabled,
                s.widthFactorPerHeight, freshMask(s),
                (xIndex, worldY, zIndex) -> carved.add(new int[]{xIndex, worldY, zIndex}));
        assertEquals(!carved.isEmpty(), any, "carved-any flag should track whether any position was reported");
        return carved;
    }

    private static List<int[]> runVectorized(Scenario s, CarverSkipKernels kernel) {
        List<int[]> carved = new ArrayList<>();
        boolean any = CanyonCarveGeometry.sweepVectorized(s.chunkPos, s.x, s.y, s.z,
                s.horizontalRadius, s.verticalRadius, s.minGenY, s.genDepth, s.isUpgrading, s.debugEnabled,
                s.widthFactorPerHeight, kernel, freshMask(s),
                (xIndex, worldY, zIndex) -> carved.add(new int[]{xIndex, worldY, zIndex}));
        assertEquals(!carved.isEmpty(), any, "carved-any flag should track whether any position was reported");
        return carved;
    }

    private static void assertSamePositions(List<int[]> expected, List<int[]> actual, String label) {
        assertEquals(expected.size(), actual.size(), () -> label + ": carved position count differs");
        for (int i = 0; i < expected.size(); i++) {
            int[] e = expected.get(i);
            int[] a = actual.get(i);
            int index = i;
            assertTrue(e[0] == a[0] && e[1] == a[1] && e[2] == a[2],
                    () -> label + ": position #" + index + " differs: expected "
                            + java.util.Arrays.toString(e) + " actual " + java.util.Arrays.toString(a));
        }
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

            String label = "chunk=" + s.chunkPos + " x=" + s.x + " y=" + s.y + " z=" + s.z
                    + " hr=" + s.horizontalRadius + " vr=" + s.verticalRadius
                    + " upgrading=" + s.isUpgrading + " debug=" + s.debugEnabled;
            assertSamePositions(reference, vectorizedScalar, label + " (scalar)");
            assertSamePositions(reference, vectorizedVector, label + " (vector)");
        }
    }

    @Test
    void chunkFarFromCarveCenterCarvesNothing() {
        ChunkPos chunkPos = new ChunkPos(0, 0);
        Scenario s = new Scenario(chunkPos, 1000.0, 64.0, 1000.0, 3.0, 6.0,
                -64, 384, false, false, new float[384], List.of());

        assertTrue(runReference(s).isEmpty());
        assertTrue(runVectorized(s, SCALAR).isEmpty());
        assertTrue(runVectorized(s, VECTOR).isEmpty());
    }

    @Test
    void collapsedVerticalRangeCarvesNothingWithoutThrowing() {
        ChunkPos chunkPos = new ChunkPos(0, 0);
        // y placed right at the generation ceiling so maxY <= minY collapses the range.
        Scenario s = new Scenario(chunkPos, 8.0, -64.0, 8.0, 3.0, 0.1,
                -64, 384, false, false, new float[384], List.of());

        assertTrue(runReference(s).isEmpty());
        assertTrue(runVectorized(s, SCALAR).isEmpty());
        assertTrue(runVectorized(s, VECTOR).isEmpty());
    }

    @Test
    void debugModeReCarvesAlreadyMaskedPositions() {
        Random random = new Random(0xDEB46);
        ChunkPos chunkPos = new ChunkPos(0, 0);
        Scenario base = randomScenario(random);
        // Force a scenario centered on this chunk so it actually carves something.
        Scenario noDebug = new Scenario(chunkPos, chunkPos.getMiddleBlockX(), 64.0, chunkPos.getMiddleBlockZ(),
                4.0, 10.0, base.minGenY, base.genDepth, false, false, base.widthFactorPerHeight, List.of());
        List<int[]> firstPass = runReference(noDebug);
        assertFalse(firstPass.isEmpty(), "expected this centered scenario to carve at least one position");

        // Pre-mask exactly the positions the first pass carved, then re-run: without
        // debug they should all be gated out (already masked); with debug they should
        // all reappear, identically, on both the reference and vectorized paths.
        Scenario premaskedNoDebug = new Scenario(chunkPos, noDebug.x, noDebug.y, noDebug.z,
                noDebug.horizontalRadius, noDebug.verticalRadius, noDebug.minGenY, noDebug.genDepth,
                noDebug.isUpgrading, false, noDebug.widthFactorPerHeight, firstPass);
        assertTrue(runReference(premaskedNoDebug).isEmpty(), "already-masked positions should be skipped without debug");

        Scenario premaskedDebug = new Scenario(chunkPos, noDebug.x, noDebug.y, noDebug.z,
                noDebug.horizontalRadius, noDebug.verticalRadius, noDebug.minGenY, noDebug.genDepth,
                noDebug.isUpgrading, true, noDebug.widthFactorPerHeight, firstPass);
        List<int[]> reDebugReference = runReference(premaskedDebug);
        List<int[]> reDebugVectorScalar = runVectorized(premaskedDebug, SCALAR);
        List<int[]> reDebugVectorVector = runVectorized(premaskedDebug, VECTOR);
        assertSamePositions(firstPass, reDebugReference, "debug re-carve (reference)");
        assertSamePositions(firstPass, reDebugVectorScalar, "debug re-carve (vectorized scalar)");
        assertSamePositions(firstPass, reDebugVectorVector, "debug re-carve (vectorized vector)");
    }

    private record Scenario(
            ChunkPos chunkPos, double x, double y, double z,
            double horizontalRadius, double verticalRadius,
            int minGenY, int genDepth, boolean isUpgrading, boolean debugEnabled,
            float[] widthFactorPerHeight, List<int[]> preExistingMaskBits
    ) {
    }
}
