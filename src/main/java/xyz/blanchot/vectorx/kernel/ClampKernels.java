package xyz.blanchot.vectorx.kernel;

public interface ClampKernels {

    void clampInPlace(float[] values, int length, float min, float max);
}
