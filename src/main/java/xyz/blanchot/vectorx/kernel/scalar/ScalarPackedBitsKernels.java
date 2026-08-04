package xyz.blanchot.vectorx.kernel.scalar;

import xyz.blanchot.vectorx.kernel.PackedBitsKernels;
import xyz.blanchot.vectorx.kernel.SelfDescribing;

import java.util.Objects;

/**
 * Reference scalar implementation of {@link PackedBitsKernels}. Correct for
 * every valid bit width (1..32); the vector backend delegates to this one
 * for widths it does not vectorize.
 */
public final class ScalarPackedBitsKernels implements PackedBitsKernels, SelfDescribing {

    public static final ScalarPackedBitsKernels INSTANCE = new ScalarPackedBitsKernels();

    private ScalarPackedBitsKernels() {
    }

    @Override
    public void unpack(long[] data, int bits, int size, int[] output) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(output, "output");
        if (bits < 1 || bits > 32) {
            throw new IllegalArgumentException("bits must be in [1, 32], got " + bits);
        }
        if (size < 0) {
            throw new IllegalArgumentException("size must be >= 0, got " + size);
        }
        Objects.checkFromIndexSize(0, size, output.length);

        int valuesPerLong = 64 / bits;
        long mask = (1L << bits) - 1L;
        int requiredLongs = (size + valuesPerLong - 1) / valuesPerLong;
        if (data.length < requiredLongs) {
            throw new IllegalArgumentException("data too short: need at least " + requiredLongs
                    + " longs for size=" + size + " bits=" + bits + ", got " + data.length);
        }

        int written = 0;
        int wordIndex = 0;
        while (written < size) {
            long word = data[wordIndex++];
            int valuesInThisWord = Math.min(valuesPerLong, size - written);
            for (int slot = 0; slot < valuesInThisWord; slot++) {
                output[written + slot] = (int) (word & mask);
                word >>>= bits;
            }
            written += valuesInThisWord;
        }
    }

    @Override
    public void pack(int[] values, int bits, int size, long[] output) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(output, "output");
        if (bits < 1 || bits > 32) {
            throw new IllegalArgumentException("bits must be in [1, 32], got " + bits);
        }
        if (size < 0) {
            throw new IllegalArgumentException("size must be >= 0, got " + size);
        }
        Objects.checkFromIndexSize(0, size, values.length);

        int valuesPerLong = 64 / bits;
        long mask = (1L << bits) - 1L;
        int requiredLongs = (size + valuesPerLong - 1) / valuesPerLong;
        if (output.length < requiredLongs) {
            throw new IllegalArgumentException("output too short: need at least " + requiredLongs
                    + " longs for size=" + size + " bits=" + bits + ", got " + output.length);
        }

        int written = 0;
        int wordIndex = 0;
        while (written < size) {
            int valuesInThisWord = Math.min(valuesPerLong, size - written);
            long word = 0L;
            for (int slot = 0; slot < valuesInThisWord; slot++) {
                word |= ((long) values[written + slot] & mask) << (slot * bits);
            }
            output[wordIndex++] = word;
            written += valuesInThisWord;
        }
    }

    @Override
    public String describe() {
        return "scalar reference backend for packed-bits packing/unpacking (no vector species)";
    }
}
