package xyz.blanchot.vectorx.selftest;

import xyz.blanchot.vectorx.kernel.PackedBitsKernels;

import java.util.Arrays;
import java.util.Random;

/**
 * Fast differential self-test, run once at startup (if {@code selfTest} is
 * enabled) to compare a candidate vector {@link PackedBitsKernels} backend
 * against the scalar reference before trusting it. Mirrors
 * {@link ClampSelfTest}/{@link DensityMapSelfTest}'s shape and coverage
 * philosophy, adapted for this kernel's {@code (data, bits, size, output)}
 * shape.
 *
 * <p>Unlike the JUnit differential test ({@code PackedBitsDifferentialTest}),
 * this self-test compares the two backends against each other directly, not
 * against the real {@code net.minecraft.util.SimpleBitStorage} -- agreement
 * with the real class is already established at compile time by that test;
 * this one only needs to catch a scalar/vector divergence on *this* JVM at
 * startup (e.g. a JIT/hardware quirk the compile-time test's machine
 * didn't hit).
 */
public final class PackedBitsSelfTest {

    private static final long SEED = 0x504B4249_5453L; // "PKBITS"
    private static final int[] BIT_WIDTHS = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 16, 17, 20, 24, 31, 32};
    private static final int[] SIZES = {0, 1, 3, 8, 15, 16, 17, 32, 63, 64, 65, 257};

    private PackedBitsSelfTest() {
    }

    public static Result run(PackedBitsKernels scalar, PackedBitsKernels vector) {
        try {
            for (int bits : BIT_WIDTHS) {
                check(bits, scalar, vector);
            }
            return Result.ok();
        } catch (SelfTestFailure e) {
            return Result.fail(e.getMessage());
        } catch (RuntimeException e) {
            return Result.fail("unexpected exception: " + e);
        }
    }

    private static void check(int bits, PackedBitsKernels scalar, PackedBitsKernels vector) {
        long mask = (1L << bits) - 1L;
        int valuesPerLong = 64 / bits;
        Random random = new Random(SEED + bits);

        for (int size : SIZES) {
            int requiredLongs = (size + valuesPerLong - 1) / valuesPerLong;
            long[] data = new long[requiredLongs];
            for (int i = 0; i < data.length; i++) {
                data[i] = random.nextLong();
            }
            maskTailBits(data, size, bits, valuesPerLong);
            compare(bits, size, data, scalar, vector, "random");
        }

        // All-zero and all-mask-bits fixed patterns, spanning several whole
        // longs plus a non-trivial tail, catch any off-by-one in the
        // shift/mask arithmetic that random data might not hit reliably.
        checkPattern(bits, valuesPerLong, 0L, scalar, vector);
        checkPattern(bits, valuesPerLong, mask, scalar, vector);

        checkPack(bits, mask, valuesPerLong, scalar, vector);
    }

    private static void checkPack(int bits, long mask, int valuesPerLong, PackedBitsKernels scalar,
                                  PackedBitsKernels vector) {
        Random random = new Random(SEED + bits + 1);
        for (int size : SIZES) {
            int[] values = new int[size];
            for (int i = 0; i < size; i++) {
                values[i] = (int) (random.nextLong() & mask);
            }
            int requiredLongs = (size + valuesPerLong - 1) / valuesPerLong;
            long[] scalarOut = new long[requiredLongs];
            long[] vectorOut = new long[requiredLongs];
            scalar.pack(values, bits, size, scalarOut);
            vector.pack(values, bits, size, vectorOut);
            for (int i = 0; i < requiredLongs; i++) {
                if (scalarOut[i] != vectorOut[i]) {
                    throw new SelfTestFailure("pack bits=" + bits + " size=" + size + " wordIndex=" + i
                            + ": scalar=" + Long.toHexString(scalarOut[i]) + " vector=" + Long.toHexString(vectorOut[i]));
                }
            }
        }
    }

    private static void checkPattern(int bits, int valuesPerLong, long fillValue,
                                     PackedBitsKernels scalar, PackedBitsKernels vector) {
        int size = valuesPerLong * 5 + 3;
        long packedWord = 0L;
        for (int slot = 0; slot < valuesPerLong; slot++) {
            packedWord |= fillValue << (slot * bits);
        }
        int requiredLongs = (size + valuesPerLong - 1) / valuesPerLong;
        long[] data = new long[requiredLongs];
        Arrays.fill(data, packedWord);
        maskTailBits(data, size, bits, valuesPerLong);
        compare(bits, size, data, scalar, vector, "pattern=0x" + Long.toHexString(fillValue));
    }

    private static void maskTailBits(long[] data, int size, int bits, int valuesPerLong) {
        if (data.length == 0) {
            return;
        }
        int written = (data.length - 1) * valuesPerLong;
        int remaining = size - written;
        if (remaining > 0 && remaining < valuesPerLong) {
            long validBits = (long) remaining * bits;
            long tailMask = validBits >= 64 ? -1L : (1L << validBits) - 1L;
            data[data.length - 1] &= tailMask;
        }
    }

    private static void compare(int bits, int size, long[] data, PackedBitsKernels scalar,
                                PackedBitsKernels vector, String label) {
        int[] scalarOut = new int[size];
        int[] vectorOut = new int[size];
        scalar.unpack(data, bits, size, scalarOut);
        vector.unpack(data, bits, size, vectorOut);
        for (int i = 0; i < size; i++) {
            if (scalarOut[i] != vectorOut[i]) {
                throw new SelfTestFailure("bits=" + bits + " size=" + size + " (" + label + ") index=" + i
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
