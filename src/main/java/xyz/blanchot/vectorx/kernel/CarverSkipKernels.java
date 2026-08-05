package xyz.blanchot.vectorx.kernel;

/**
 * Computes {@code CanyonWorldCarver.shouldSkip} for a whole {@code worldY}
 * sweep at once, for a fixed {@code (xd, zd)} carve-ellipsoid column.
 *
 * <p>Real Minecraft 26.2's formula, reproduced exactly (including using
 * true division, not a precomputed-reciprocal multiply, so results are
 * bit-identical to the scalar reference and to vanilla -- this matters
 * because the result feeds a {@code >= 1.0} threshold comparison, where
 * even a 1-ULP rounding difference could flip a boundary position):
 * <pre>
 *   yd = (worldY - 0.5 - y) / verticalRadius
 *   skip = horizSum * widthFactorPerHeight[worldY - minGenY - 1] + yd * yd / 6.0 &gt;= 1.0
 * </pre>
 * where {@code horizSum = xd*xd + zd*zd} is loop-invariant across the whole
 * {@code worldY} sweep (computed once by the caller).
 *
 * <p>Must not import {@code jdk.incubator.vector} -- only
 * {@link xyz.blanchot.vectorx.kernel.simd.SimdCarverSkipKernels} may.
 */
public interface CarverSkipKernels {

    /**
     * For each {@code worldY} in {@code (minY, maxY]} (vanilla's exact
     * range, walked ascending), writes {@code true} into
     * {@code output[worldY - minY - 1]} iff {@code shouldSkip} would return
     * {@code true} for that {@code worldY}.
     *
     * @throws NullPointerException      if {@code widthFactorPerHeight} or {@code output} is null
     * @throws IndexOutOfBoundsException if {@code output} is shorter than {@code maxY - minY},
     *                                   or if {@code widthFactorPerHeight} doesn't cover every
     *                                   {@code worldY - minGenY - 1} index touched
     */
    void canyonSkipMask(double horizSum, double y, double verticalRadius,
                         float[] widthFactorPerHeight, int minGenY,
                         int minY, int maxY, boolean[] output);
}
