package xyz.blanchot.vectorx.selftest;

import xyz.blanchot.vectorx.kernel.CarverSkipKernels;

import java.util.Random;

/**
 * Fast differential self-test, run once at startup (if {@code selfTest} is
 * enabled) to compare a candidate vector {@link CarverSkipKernels} backend
 * against the scalar reference before trusting it. Mirrors
 * {@link PackedBitsSelfTest}'s shape and coverage philosophy, adapted for
 * this kernel's ellipsoid-sweep shape.
 *
 * <p>Includes a boundary-stress pass in addition to random inputs: the
 * kernel's result feeds a {@code >= 1.0} threshold comparison, and vector
 * vs. scalar rounding could in principle diverge by a single ULP right at
 * that boundary even when both use true division (see
 * {@code SimdCarverSkipKernels}'s javadoc) -- this specifically hunts for
 * that by constructing inputs that land very close to the threshold.
 */
public final class CarverSkipSelfTest {

    private static final long SEED = 0x43414E594F4EL; // "CANYON"
    private static final int[] Y_LENGTHS = {0, 1, 2, 3, 7, 8, 9, 15, 16, 17, 25, 31, 32, 40};

    private CarverSkipSelfTest() {
    }

    public static Result run(CarverSkipKernels scalar, CarverSkipKernels vector) {
        try {
            Random random = new Random(SEED);
            for (int n : Y_LENGTHS) {
                checkRandom(n, random, scalar, vector);
            }
            checkBoundary(scalar, vector);
            return Result.ok();
        } catch (SelfTestFailure e) {
            return Result.fail(e.getMessage());
        } catch (RuntimeException e) {
            return Result.fail("unexpected exception: " + e);
        }
    }

    private static void checkRandom(int n, Random random, CarverSkipKernels scalar, CarverSkipKernels vector) {
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

        compare(n, minGenY, minY, maxY, horizSum, y, verticalRadius, widthFactorPerHeight, scalar, vector,
                "random n=" + n);
    }

    /**
     * Constructs a whole worldY sweep where every position's raw value
     * (before the >= 1.0 comparison) is deliberately placed within a hair
     * of 1.0, in both directions, to stress the comparison boundary rather
     * than rely on random inputs happening to land there.
     */
    private static void checkBoundary(CarverSkipKernels scalar, CarverSkipKernels vector) {
        int minGenY = 0;
        int minY = 0;
        int n = 40;
        int maxY = n;
        double horizSum = 0.5;
        double verticalRadius = 10.0;
        // Deliberately non-round: y=0.0 would make (worldY - 0.5 - y) and
        // worldY - (0.5 + y) round identically no matter how those two
        // subtractions are grouped, hiding exactly the class of bug this
        // test exists to catch (see SimdCarverSkipKernels's yd computation).
        double y = 64.37;

        float[] widthFactorPerHeight = new float[maxY - minGenY + 1];
        for (int worldY = minY + 1; worldY <= maxY; worldY++) {
            double yd = (worldY - 0.5 - y) / verticalRadius;
            double target = 1.0 - yd * yd / 6.0;
            // Choose widthFactorPerHeight so horizSum*wfp + yd*yd/6.0 lands
            // within a few ULPs of 1.0 either side, alternating direction.
            double nudge = (worldY % 2 == 0 ? 1 : -1) * 4.0 * Math.ulp(1.0);
            widthFactorPerHeight[worldY - minGenY - 1] = (float) ((target + nudge) / horizSum);
        }

        compare(n, minGenY, minY, maxY, horizSum, y, verticalRadius, widthFactorPerHeight, scalar, vector,
                "boundary");
    }

    private static void compare(int n, int minGenY, int minY, int maxY, double horizSum, double y,
                                 double verticalRadius, float[] widthFactorPerHeight,
                                 CarverSkipKernels scalar, CarverSkipKernels vector, String label) {
        boolean[] scalarOut = new boolean[Math.max(n, 1)];
        boolean[] vectorOut = new boolean[Math.max(n, 1)];
        scalar.canyonSkipMask(horizSum, y, verticalRadius, widthFactorPerHeight, minGenY, minY, maxY, scalarOut);
        vector.canyonSkipMask(horizSum, y, verticalRadius, widthFactorPerHeight, minGenY, minY, maxY, vectorOut);

        for (int i = 0; i < n; i++) {
            if (scalarOut[i] != vectorOut[i]) {
                throw new SelfTestFailure("(" + label + ") index=" + i
                        + ": scalar=" + scalarOut[i] + " vector=" + vectorOut[i]);
            }
        }
    }

    public record Result(boolean passed, String failureDescription) {
        static Result ok() {
            return new Result(true, null);
        }

        static Result fail(String description) {
            return new Result(false, description);
        }
    }

    private static final class SelfTestFailure extends RuntimeException {
        SelfTestFailure(String message) {
            super(message);
        }
    }
}
