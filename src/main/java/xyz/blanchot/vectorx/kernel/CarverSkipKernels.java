package xyz.blanchot.vectorx.kernel;

public interface CarverSkipKernels {

    void canyonSkipMask(double horizSum, double y, double verticalRadius, float[] widthFactorPerHeight, int minGenY, int minY, int maxY, boolean[] output);
}
