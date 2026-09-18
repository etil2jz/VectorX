package xyz.blanchot.vectorx.kernel;

public interface DensityBinaryKernels {

    void applyBuffer(float[] values, float[] right, int length, DensityBinaryOp op);

    void applyConst(float[] values, int length, DensityBinaryOp op, float operand);
}
