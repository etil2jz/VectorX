package xyz.blanchot.vectorx.carve;

import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import xyz.blanchot.vectorx.kernel.CarverSkipKernels;

public final class CanyonCarveGeometry {

    private static final boolean[] EMPTY_MASK = new boolean[0];

    private CanyonCarveGeometry() {
    }

    public static void sweepVectorized(ChunkPos chunkPos, double x, double y, double z, double horizontalRadius, double verticalRadius, int outputMinY, int outputMaxY, int minGenY, float[] widthFactorPerHeight, CarverSkipKernels skipKernel, CarveSink sink) {
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

                skipKernel.canyonSkipMask(horizSum, y, verticalRadius, widthFactorPerHeight, minGenY, minY, maxY, skipMask);

                for (int worldY = maxY; worldY > minY; worldY--) {
                    if (!skipMask[worldY - minY - 1]) {
                        sink.carve(xIndex, worldY, zIndex);
                    }
                }
            }
        }
    }

    public static void sweepReference(ChunkPos chunkPos, double x, double y, double z, double horizontalRadius, double verticalRadius, int outputMinY, int outputMaxY, int minGenY, float[] widthFactorPerHeight, CarveSink sink) {
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

    private static boolean referenceShouldSkip(float[] widthFactorPerHeight, int minGenY, double xd, double yd, double zd, int worldY) {
        int yIndex = worldY - minGenY;
        return (xd * xd + zd * zd) * widthFactorPerHeight[yIndex - 1] + yd * yd / 6.0 >= 1.0;
    }

    @FunctionalInterface
    public interface CarveSink {
        void carve(int xIndex, int worldY, int zIndex);
    }
}
