package xyz.blanchot.vectorx.kernel;

public interface DensityMapKernels {

    void apply(float[] values, int length, DensityMapOp op);

    void leakyReLU(float[] values, int length, float negativeFactor);
}
