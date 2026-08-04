package xyz.blanchot.vectorx.kernel;

/**
 * Packs and unpacks a fixed-width bit-packed {@code long[]} to/from an
 * {@code int[]}.
 *
 * <p>Reproduces the packing scheme used by Minecraft's compact
 * block-state/biome storage, cross-checked against the real class in this
 * project's own differential tests: {@code size} values, each {@code bits}
 * wide (1..32), are packed LSB-first into consecutive {@code long}s,
 * {@code 64 / bits} values per {@code long}, and a value never spans two
 * {@code long}s -- unused high bits in the last, partially-filled
 * {@code long} are ignored on read and left as zero on write.
 *
 * <p>Must not import {@code jdk.incubator.vector} -- only
 * {@link xyz.blanchot.vectorx.kernel.simd.SimdPackedBitsKernels} may.
 */
public interface PackedBitsKernels {

    /**
     * Unpacks {@code size} values, {@code bits} wide, from {@code data} into
     * {@code output[0, size)}.
     *
     * @throws NullPointerException      if {@code data} or {@code output} is null
     * @throws IllegalArgumentException  if {@code bits} is not in {@code [1, 32]},
     *                                   if {@code size} is negative, or if {@code data} is too short for
     *                                   {@code size} values at that bit width
     * @throws IndexOutOfBoundsException if {@code output} is shorter than {@code size}
     */
    void unpack(long[] data, int bits, int size, int[] output);

    /**
     * Packs {@code size} values from {@code values[0, size)}, {@code bits}
     * wide, into {@code output} -- the inverse of {@link #unpack}.
     *
     * @throws NullPointerException      if {@code values} or {@code output} is null
     * @throws IllegalArgumentException  if {@code bits} is not in {@code [1, 32]},
     *                                   if {@code size} is negative, or if {@code output} is too short for
     *                                   {@code size} values at that bit width
     * @throws IndexOutOfBoundsException if {@code values} is shorter than {@code size}
     */
    void pack(int[] values, int bits, int size, long[] output);
}
