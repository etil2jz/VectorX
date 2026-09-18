package xyz.blanchot.vectorx.kernel;

public interface PackedBitsKernels {

    void unpack(long[] data, int bits, int size, int[] output);

    void pack(int[] values, int bits, int size, long[] output);
}
