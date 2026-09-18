package xyz.blanchot.vectorx.kernel;

public interface DensitySelectKernels {

    void lerp(float[] values, float[] first, float[] second, int length);

    void rangeChoiceConst(float[] values, int length, float minInclusive, float maxExclusive, float whenInRange, float whenOutOfRange);

    void rangeChoice(float[] values, float[] input, float[] whenOutOfRange, int length, float minInclusive, float maxExclusive);
}
