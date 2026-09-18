package xyz.blanchot.vectorx.carve;

import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import xyz.blanchot.vectorx.kernel.CarverSkipKernels;

/**
 * Pure, Mixin-free reimplementation of {@code WorldCarver.carveEllipsoid}'s
 * bounds computation and X/Z/Y traversal for {@code CanyonWorldCarver},
 * factored out so it can run under a plain JUnit test with no Fabric/Mixin
 * bootstrap.
 *
 * <p>{@link #sweepVectorized} is the exact logic
 * {@code CanyonWorldCarverMixin} runs in production (it delegates here).
 * {@link #sweepReference} is a separate, hand-transliterated copy of real
 * vanilla's algorithm (per-position {@code shouldSkip}, descending loop, no
 * precomputed mask array) -- copied from the decompiled 26.3 source, not
 * re-derived from memory, so it can serve as an independent oracle in
 * {@code CanyonCarveGeometryTest} rather than just testing this class
 * against itself.
 *
 * <p>Both methods report positions that pass the skip test via
 * {@link CarveSink} instead of calling the real
 * {@code CarverOutput.carve} -- what that sink does with a position is
 * out of scope here, and in production it is always a thin forward to the
 * real, untouched {@code CarverOutput} vanilla handed the carver, so it can
 * never diverge between the two paths.
 *
 * <h2>What 26.3 changed relative to 26.2</h2>
 * <ul>
 *   <li>{@code carveEllipsoid} is now a {@code public static void} method on
 *       the (newly non-generic, now-interface) {@code WorldCarver}, and no
 *       longer reports whether anything was carved. Both methods here
 *       therefore return {@code void} as well: the old {@code boolean} had
 *       exactly one meaning -- "did at least one position pass the gate" --
 *       and with the gate itself gone (see below) there is no vanilla
 *       consumer left to mirror. Tests observe carved positions through the
 *       sink, which is strictly more informative than the flag ever was.</li>
 *   <li>{@code carveBlock} and the per-position {@code CarvingMask}
 *       read/write gate are gone. Carvers now only push coordinates into a
 *       {@code net.minecraft.world.level.chunk.CarverOutput}
 *       ({@code void carve(int,int,int)}, {@code int minY()},
 *       {@code int maxY()}). In vanilla the concrete output is
 *       {@code CarvingMask}, whose {@code carve} is a plain
 *       {@code BitSet.set} -- idempotent and order-independent; block
 *       placement (including the grass/dirt surface preservation that used
 *       to live in {@code carveBlock}) happens afterwards in
 *       {@code NoiseBasedChunkGenerator.applyCarvingMask}, driven by
 *       {@code CarvingMask.visit}, which walks the {@code BitSet} in index
 *       order and so cannot observe the order bits were set in. The
 *       {@code hasGrass}/per-column bookkeeping the 26.2 Mixin had to
 *       replicate is therefore obsolete and has been dropped. The
 *       descending {@code worldY} dispatch is nonetheless kept verbatim
 *       (see {@link #sweepVectorized}), because {@code CarverOutput} is a
 *       public interface a third party may implement with order-sensitive
 *       behaviour, and matching vanilla's observable call sequence costs
 *       nothing.</li>
 *   <li>The {@code isUpgrading}/7-protected-blocks guard and the
 *       {@code minGenY + 1} floor did not vanish, they moved one frame out:
 *       {@code NoiseBasedChunkGenerator.generateCarvers} now builds
 *       {@code new CarvingMask(context.getMinGenY() + 1,
 *       context.getMinGenY() + context.getGenDepth() - 1 -
 *       (chunk.isUpgrading() ? 0 : 7))} (verified in the 26.3 bytecode), and
 *       {@code carveEllipsoid} just clamps to {@code output.minY()} /
 *       {@code output.maxY()}. These bounds are passed in here as
 *       {@code outputMinY} / {@code outputMaxY}, so this class needs neither
 *       {@code isUpgrading} nor {@code genDepth} any more.</li>
 *   <li>{@code CarverDebugSettings} / {@code SharedConstants.DEBUG_CARVERS}
 *       no longer exist, so the "re-carve already-masked positions when
 *       debugging" branch is gone too.</li>
 *   <li>{@code CanyonWorldCarver.shouldSkip} itself is byte-for-byte the
 *       same formula, still in {@code double}, still indexing
 *       {@code widthFactorPerHeight[worldY - minGenY - 1]}. That index stays
 *       in range for the same reason as in 26.2: the loop's lowest visited
 *       {@code worldY} is {@code minY + 1 >= outputMinY + 1 = minGenY + 2},
 *       giving a minimum index of 1.</li>
 * </ul>
 */
public final class CanyonCarveGeometry {

    private static final boolean[] EMPTY_MASK = new boolean[0];

    private CanyonCarveGeometry() {
    }

    /**
     * Vectorized equivalent of 26.3's
     * {@code WorldCarver.carveEllipsoid(ChunkPos, double, double, double,
     * double, double, CarverOutput, CarveSkipChecker)} specialized to
     * {@code CanyonWorldCarver}'s skip checker.
     *
     * <p>For each {@code (xIndex, zIndex)} column that survives the
     * horizontal ellipse test, the whole {@code worldY} skip decision is
     * computed in one bulk kernel call (ascending, which is the layout the
     * kernel's output array uses), and only then dispatched to
     * {@code sink} walking {@code worldY} descending -- exactly vanilla's
     * {@code for (int worldY = maxY; worldY > minY; worldY--)}.
     *
     * @param outputMinY vanilla's {@code output.minY()}
     * @param outputMaxY vanilla's {@code output.maxY()}
     * @param minGenY    vanilla's {@code context.getMinGenY()}, the base for
     *                   the {@code widthFactorPerHeight} index
     */
    public static void sweepVectorized(
            ChunkPos chunkPos,
            double x,
            double y,
            double z,
            double horizontalRadius,
            double verticalRadius,
            int outputMinY,
            int outputMaxY,
            int minGenY,
            float[] widthFactorPerHeight,
            CarverSkipKernels skipKernel,
            CarveSink sink
    ) {
        double centerX = chunkPos.getMiddleBlockX();
        double centerZ = chunkPos.getMiddleBlockZ();
        double maxDelta = 16.0 + horizontalRadius * 2.0;
        if (Math.abs(x - centerX) > maxDelta || Math.abs(z - centerZ) > maxDelta) {
            return;
        }

        int chunkMinX = chunkPos.getMinBlockX();
        int chunkMinZ = chunkPos.getMinBlockZ();
        int minXIndex = Math.max(Mth.floor(x - horizontalRadius) - chunkMinX - 1, 0);
        int maxXIndex = Math.min(Mth.floor(x + horizontalRadius) - chunkMinX, 15);
        int minY = Math.max(Mth.floor(y - verticalRadius) - 1, outputMinY);
        int maxY = Math.min(Mth.floor(y + verticalRadius) + 1, outputMaxY);
        int minZIndex = Math.max(Mth.floor(z - horizontalRadius) - chunkMinZ - 1, 0);
        int maxZIndex = Math.min(Mth.floor(z + horizontalRadius) - chunkMinZ, 15);

        int yRange = maxY - minY;
        boolean[] skipMask = yRange > 0 ? new boolean[yRange] : EMPTY_MASK;

        for (int xIndex = minXIndex; xIndex <= maxXIndex; xIndex++) {
            int worldX = chunkPos.getBlockX(xIndex);
            double xd = (worldX + 0.5 - x) / horizontalRadius;

            for (int zIndex = minZIndex; zIndex <= maxZIndex; zIndex++) {
                int worldZ = chunkPos.getBlockZ(zIndex);
                double zd = (worldZ + 0.5 - z) / horizontalRadius;
                double horizSum = xd * xd + zd * zd;
                if (horizSum >= 1.0 || yRange <= 0) {
                    continue;
                }

                skipKernel.canyonSkipMask(horizSum, y, verticalRadius, widthFactorPerHeight,
                        minGenY, minY, maxY, skipMask);

                for (int worldY = maxY; worldY > minY; worldY--) {
                    if (!skipMask[worldY - minY - 1]) {
                        sink.carve(xIndex, worldY, zIndex);
                    }
                }
            }
        }
    }

    /**
     * Independent oracle: a hand transliteration of real 26.3
     * {@code WorldCarver.carveEllipsoid} with
     * {@code CanyonWorldCarver.shouldSkip} inlined as
     * {@link #referenceShouldSkip}. Deliberately keeps the per-position
     * {@code yd}/{@code shouldSkip} evaluation and the descending loop, and
     * shares no code with {@link #sweepVectorized}.
     */
    public static void sweepReference(
            ChunkPos chunkPos,
            double x,
            double y,
            double z,
            double horizontalRadius,
            double verticalRadius,
            int outputMinY,
            int outputMaxY,
            int minGenY,
            float[] widthFactorPerHeight,
            CarveSink sink
    ) {
        double centerX = chunkPos.getMiddleBlockX();
        double centerZ = chunkPos.getMiddleBlockZ();
        double maxDelta = 16.0 + horizontalRadius * 2.0;
        if (Math.abs(x - centerX) > maxDelta || Math.abs(z - centerZ) > maxDelta) {
            return;
        }

        int chunkMinX = chunkPos.getMinBlockX();
        int chunkMinZ = chunkPos.getMinBlockZ();
        int minXIndex = Math.max(Mth.floor(x - horizontalRadius) - chunkMinX - 1, 0);
        int maxXIndex = Math.min(Mth.floor(x + horizontalRadius) - chunkMinX, 15);
        int minY = Math.max(Mth.floor(y - verticalRadius) - 1, outputMinY);
        int maxY = Math.min(Mth.floor(y + verticalRadius) + 1, outputMaxY);
        int minZIndex = Math.max(Mth.floor(z - horizontalRadius) - chunkMinZ - 1, 0);
        int maxZIndex = Math.min(Mth.floor(z + horizontalRadius) - chunkMinZ, 15);

        for (int xIndex = minXIndex; xIndex <= maxXIndex; xIndex++) {
            int worldX = chunkPos.getBlockX(xIndex);
            double xd = (worldX + 0.5 - x) / horizontalRadius;

            for (int zIndex = minZIndex; zIndex <= maxZIndex; zIndex++) {
                int worldZ = chunkPos.getBlockZ(zIndex);
                double zd = (worldZ + 0.5 - z) / horizontalRadius;
                if (xd * xd + zd * zd >= 1.0) {
                    continue;
                }

                for (int worldY = maxY; worldY > minY; worldY--) {
                    double yd = (worldY - 0.5 - y) / verticalRadius;
                    if (!referenceShouldSkip(widthFactorPerHeight, minGenY, xd, yd, zd, worldY)) {
                        sink.carve(xIndex, worldY, zIndex);
                    }
                }
            }
        }
    }

    /**
     * Copied from real Minecraft 26.3's
     * {@code CanyonWorldCarver.shouldSkip(WorldGenerationContext, float[], double, double, double, int)}.
     * Unchanged from 26.2 apart from the context type.
     */
    private static boolean referenceShouldSkip(float[] widthFactorPerHeight, int minGenY,
                                               double xd, double yd, double zd, int worldY) {
        int yIndex = worldY - minGenY;
        return (xd * xd + zd * zd) * widthFactorPerHeight[yIndex - 1] + yd * yd / 6.0 >= 1.0;
    }

    /**
     * Stands in for {@code CarverOutput.carve(int, int, int)}: the two
     * coordinates are chunk-local X/Z indices in {@code [0, 15]} and an
     * absolute world Y, exactly as vanilla passes them.
     */
    @FunctionalInterface
    public interface CarveSink {
        void carve(int xIndex, int worldY, int zIndex);
    }
}
