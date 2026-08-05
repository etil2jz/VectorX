package xyz.blanchot.vectorx.kernel.simd;

import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorShape;
import jdk.incubator.vector.VectorSpecies;
import xyz.blanchot.vectorx.kernel.CarverSkipKernels;
import xyz.blanchot.vectorx.kernel.SelfDescribing;

import java.util.Objects;

/**
 * Vector API backend for {@link CarverSkipKernels}.
 *
 * <p>{@code worldY} is walked ascending in lane-sized chunks (independent
 * per lane -- unlike vanilla's descending iteration, order doesn't affect
 * which positions are flagged, only who reads the flags afterward, which
 * stays the caller's responsibility). Uses true {@code div}, not a
 * precomputed-reciprocal multiply, to stay bit-identical with
 * {@link xyz.blanchot.vectorx.kernel.scalar.ScalarCarverSkipKernels} --
 * multiply-by-reciprocal rounds differently in the last bit, which matters
 * here because the result feeds a {@code >= 1.0} threshold comparison.
 */
public final class SimdCarverSkipKernels implements CarverSkipKernels, SelfDescribing {

    public static final SimdCarverSkipKernels INSTANCE = new SimdCarverSkipKernels();

    private static final VectorSpecies<Double> DSPECIES = DoubleVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Float> FSPECIES =
            VectorSpecies.of(float.class, VectorShape.forBitSize(DSPECIES.length() * Float.SIZE));
    private static final DoubleVector LANE_OFFSETS = laneOffsets();

    private SimdCarverSkipKernels() {
    }

    private static DoubleVector laneOffsets() {
        double[] offsets = new double[DSPECIES.length()];
        for (int lane = 0; lane < offsets.length; lane++) {
            offsets[lane] = lane;
        }
        return DoubleVector.fromArray(DSPECIES, offsets, 0);
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

        int lanes = DSPECIES.length();
        int bound = DSPECIES.loopBound(n);
        // widthFactorPerHeight index for i=0, i.e. worldY = minY + 1:
        // (minY + 1) - minGenY - 1
        int arrayBase = minY - minGenY;

        int i = 0;
        for (; i < bound; i += lanes) {
            int worldYBase = minY + 1 + i;
            DoubleVector worldYVec = LANE_OFFSETS.add((double) worldYBase);
            // Must match the scalar reference's (worldY - 0.5 - y) step order
            // exactly -- collapsing to a precomputed (0.5 + y) offset rounds
            // differently for many y values, silently flipping the >= 1.0
            // threshold decision for positions near the ellipsoid boundary.
            DoubleVector ydVec = worldYVec.sub(0.5).sub(y).div(verticalRadius);

            FloatVector wfpVec = FloatVector.fromArray(FSPECIES, widthFactorPerHeight, arrayBase + i);
            DoubleVector wfpVecD = (DoubleVector) wfpVec.convertShape(VectorOperators.F2D, DSPECIES, 0);

            DoubleVector lhs = ydVec.mul(ydVec).div(6.0).add(wfpVecD.mul(horizSum));
            VectorMask<Double> mask = lhs.compare(VectorOperators.GE, 1.0);
            for (int lane = 0; lane < lanes; lane++) {
                output[i + lane] = mask.laneIsSet(lane);
            }
        }
        for (; i < n; i++) {
            int worldY = minY + 1 + i;
            double yd = (worldY - 0.5 - y) / verticalRadius;
            output[i] = horizSum * widthFactorPerHeight[worldY - minGenY - 1] + yd * yd / 6.0 >= 1.0;
        }
    }

    @Override
    public String describe() {
        return "species=" + DSPECIES;
    }
}
