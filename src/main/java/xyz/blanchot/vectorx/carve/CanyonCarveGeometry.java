package xyz.blanchot.vectorx.carve;

import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.CarvingMask;
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
 * precomputed mask array) -- copied from the decompiled 26.2 source, not
 * re-derived from memory, so it can serve as an independent oracle in
 * {@code CanyonCarveGeometryTest} rather than just testing this class
 * against itself.
 *
 * <p>Both methods report gate-passing positions (skip check false, mask
 * check true) via {@link CarveSink} instead of calling the real
 * {@code carveBlock} -- block-placement success depends on real chunk data
 * that's out of scope here, and in production that call is always the real,
 * untouched {@code carveBlock} (via {@code WorldCarverAccessor}), so it can
 * never diverge between the two paths.
 */
public final class CanyonCarveGeometry {

    private static final boolean[] EMPTY_MASK = new boolean[0];

    private CanyonCarveGeometry() {
    }

    public static boolean sweepVectorized(
            ChunkPos chunkPos,
            double x,
            double y,
            double z,
            double horizontalRadius,
            double verticalRadius,
            int minGenY,
            int genDepth,
            boolean isUpgrading,
            boolean debugEnabled,
            float[] widthFactorPerHeight,
            CarverSkipKernels skipKernel,
            CarvingMask mask,
            CarveSink sink
    ) {
        double centerX = chunkPos.getMiddleBlockX();
        double centerZ = chunkPos.getMiddleBlockZ();
        double maxDelta = 16.0 + horizontalRadius * 2.0;
        if (Math.abs(x - centerX) > maxDelta || Math.abs(z - centerZ) > maxDelta) {
            return false;
        }

        int chunkMinX = chunkPos.getMinBlockX();
        int chunkMinZ = chunkPos.getMinBlockZ();
        int minXIndex = Math.max(Mth.floor(x - horizontalRadius) - chunkMinX - 1, 0);
        int maxXIndex = Math.min(Mth.floor(x + horizontalRadius) - chunkMinX, 15);
        int minY = Math.max(Mth.floor(y - verticalRadius) - 1, minGenY + 1);
        int protectedBlocksOnTop = isUpgrading ? 0 : 7;
        int maxY = Math.min(Mth.floor(y + verticalRadius) + 1, minGenY + genDepth - 1 - protectedBlocksOnTop);
        int minZIndex = Math.max(Mth.floor(z - horizontalRadius) - chunkMinZ - 1, 0);
        int maxZIndex = Math.min(Mth.floor(z + horizontalRadius) - chunkMinZ, 15);
        boolean carved = false;

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
                    if (!skipMask[worldY - minY - 1] && (!mask.get(xIndex, worldY, zIndex) || debugEnabled)) {
                        mask.set(xIndex, worldY, zIndex);
                        carved = true;
                        sink.carve(xIndex, worldY, zIndex);
                    }
                }
            }
        }

        return carved;
    }

    public static boolean sweepReference(
            ChunkPos chunkPos,
            double x,
            double y,
            double z,
            double horizontalRadius,
            double verticalRadius,
            int minGenY,
            int genDepth,
            boolean isUpgrading,
            boolean debugEnabled,
            float[] widthFactorPerHeight,
            CarvingMask mask,
            CarveSink sink
    ) {
        double centerX = chunkPos.getMiddleBlockX();
        double centerZ = chunkPos.getMiddleBlockZ();
        double maxDelta = 16.0 + horizontalRadius * 2.0;
        if (Math.abs(x - centerX) > maxDelta || Math.abs(z - centerZ) > maxDelta) {
            return false;
        }

        int chunkMinX = chunkPos.getMinBlockX();
        int chunkMinZ = chunkPos.getMinBlockZ();
        int minXIndex = Math.max(Mth.floor(x - horizontalRadius) - chunkMinX - 1, 0);
        int maxXIndex = Math.min(Mth.floor(x + horizontalRadius) - chunkMinX, 15);
        int minY = Math.max(Mth.floor(y - verticalRadius) - 1, minGenY + 1);
        int protectedBlocksOnTop = isUpgrading ? 0 : 7;
        int maxY = Math.min(Mth.floor(y + verticalRadius) + 1, minGenY + genDepth - 1 - protectedBlocksOnTop);
        int minZIndex = Math.max(Mth.floor(z - horizontalRadius) - chunkMinZ - 1, 0);
        int maxZIndex = Math.min(Mth.floor(z + horizontalRadius) - chunkMinZ, 15);
        boolean carved = false;

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
                    boolean skip = referenceShouldSkip(widthFactorPerHeight, minGenY, xd, yd, zd, worldY);
                    if (!skip && (!mask.get(xIndex, worldY, zIndex) || debugEnabled)) {
                        mask.set(xIndex, worldY, zIndex);
                        carved = true;
                        sink.carve(xIndex, worldY, zIndex);
                    }
                }
            }
        }

        return carved;
    }

    /**
     * Copied from real Minecraft 26.2's
     * {@code CanyonWorldCarver.shouldSkip(CarvingContext, float[], double, double, double, int)}.
     */
    private static boolean referenceShouldSkip(float[] widthFactorPerHeight, int minGenY,
                                               double xd, double yd, double zd, int worldY) {
        int yIndex = worldY - minGenY;
        return (xd * xd + zd * zd) * widthFactorPerHeight[yIndex - 1] + yd * yd / 6.0 >= 1.0;
    }

    @FunctionalInterface
    public interface CarveSink {
        void carve(int xIndex, int worldY, int zIndex);
    }
}
