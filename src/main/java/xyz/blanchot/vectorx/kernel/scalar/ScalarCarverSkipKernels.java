package xyz.blanchot.vectorx.kernel.scalar;

import xyz.blanchot.vectorx.kernel.CarverSkipKernels;
import xyz.blanchot.vectorx.kernel.SelfDescribing;

import java.util.Objects;

/**
 * Reference scalar implementation of {@link CarverSkipKernels}. The formula
 * was copied from real Minecraft 26.2's {@code CanyonWorldCarver.shouldSkip
 * (CarvingContext, float[], double, double, double, int)} and the {@code yd}
 * computation from the {@code worldY} loop in {@code WorldCarver.carveEllipsoid},
 * not re-derived from memory.
 */
public final class ScalarCarverSkipKernels implements CarverSkipKernels, SelfDescribing {

    public static final ScalarCarverSkipKernels INSTANCE = new ScalarCarverSkipKernels();

    private ScalarCarverSkipKernels() {
    }

    @Override
    public void canyonSkipMask(double horizSum, double y, double verticalRadius,
                               float[] widthFactorPerHeight, int minGenY,
                               int minY, int maxY, boolean[] output) {
        Objects.requireNonNull(widthFactorPerHeight, "widthFactorPerHeight");
        Objects.requireNonNull(output, "output");
        int n = maxY - minY;
        if (output.length < n) {
            throw new IndexOutOfBoundsException("output shorter than " + n);
        }

        for (int i = 0; i < n; i++) {
            int worldY = minY + 1 + i;
            double yd = (worldY - 0.5 - y) / verticalRadius;
            output[i] = horizSum * widthFactorPerHeight[worldY - minGenY - 1] + yd * yd / 6.0 >= 1.0;
        }
    }

    @Override
    public String describe() {
        return "scalar reference backend for canyon carver ellipsoid skip test";
    }
}
