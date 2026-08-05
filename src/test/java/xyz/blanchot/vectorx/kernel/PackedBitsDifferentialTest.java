package xyz.blanchot.vectorx.kernel;

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorSpecies;
import net.minecraft.util.SimpleBitStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import xyz.blanchot.vectorx.kernel.scalar.ScalarPackedBitsKernels;
import xyz.blanchot.vectorx.kernel.simd.SimdPackedBitsKernels;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Differential tests for the standalone reproduction of Minecraft's
 * compact bit-packed storage format. Touches only the real, unmodified
 * {@code net.minecraft.util.SimpleBitStorage} class as ground truth -- it
 * does not inject into it.
 */
class PackedBitsDifferentialTest {

    private static final PackedBitsKernels SCALAR = ScalarPackedBitsKernels.INSTANCE;
    private static final PackedBitsKernels VECTOR = SimdPackedBitsKernels.INSTANCE;

    private static final int[] SIZES = {0, 1, 2, 3, 7, 8, 9, 15, 16, 17, 63, 64, 65, 100, 256, 257, 4096, 4097};

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 16, 17, 20, 24, 31, 32})
    void everyBackendMatchesRealMinecraftSimpleBitStorageAcrossAllSizes(int bits) {
        Random random = new Random(bits * 1_000_003L + 7);
        for (int size : SIZES) {
            long mask = (1L << bits) - 1L;
            int[] source = new int[size];
            for (int i = 0; i < size; i++) {
                // Bias towards small values (typical palette indices) plus the
                // occasional max-magnitude value for this bit width.
                source[i] = random.nextInt(10) == 0 ? (int) mask : random.nextInt((int) Math.min(mask + 1, Integer.MAX_VALUE));
            }

            SimpleBitStorage reference = new SimpleBitStorage(bits, size, source);
            long[] rawData = reference.getRaw();

            int[] scalarOut = new int[size];
            int[] vectorOut = new int[size];
            SCALAR.unpack(rawData, bits, size, scalarOut);
            VECTOR.unpack(rawData, bits, size, vectorOut);

            int[] expected = new int[size];
            for (int i = 0; i < size; i++) {
                expected[i] = reference.get(i);
            }

            assertArrayEquals(expected, scalarOut, () -> "scalar mismatch vs real SimpleBitStorage, bits=" + bits + " size=" + size);
            assertArrayEquals(expected, vectorOut, () -> "vector mismatch vs real SimpleBitStorage, bits=" + bits + " size=" + size);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 16, 17, 20, 24, 31, 32})
    void everyBackendPacksIdenticallyToRealMinecraftSimpleBitStorage(int bits) {
        Random random = new Random(bits * 2_000_003L + 11);
        for (int size : SIZES) {
            long mask = (1L << bits) - 1L;
            int[] source = new int[size];
            for (int i = 0; i < size; i++) {
                source[i] = random.nextInt(10) == 0 ? (int) mask : random.nextInt((int) Math.min(mask + 1, Integer.MAX_VALUE));
            }

            SimpleBitStorage reference = new SimpleBitStorage(bits, size, source);
            long[] expected = reference.getRaw();

            int valuesPerLong = 64 / bits;
            int requiredLongs = (size + valuesPerLong - 1) / valuesPerLong;
            long[] scalarOut = new long[requiredLongs];
            long[] vectorOut = new long[requiredLongs];
            SCALAR.pack(source, bits, size, scalarOut);
            VECTOR.pack(source, bits, size, vectorOut);

            assertArrayEquals(expected, scalarOut, () -> "scalar pack mismatch vs real SimpleBitStorage, bits=" + bits + " size=" + size);
            assertArrayEquals(expected, vectorOut, () -> "vector pack mismatch vs real SimpleBitStorage, bits=" + bits + " size=" + size);
        }
    }

    @Test
    void scalarAndVectorAgreeOnEveryValidBitWidthWithHandRolledData() {
        for (int bits = 1; bits <= 32; bits++) {
            for (int size : SIZES) {
                long valuesPerLongCalc = 64L / bits;
                int requiredLongs = (int) ((size + valuesPerLongCalc - 1) / valuesPerLongCalc);
                long[] data = new long[Math.max(requiredLongs, 0)];
                Random random = new Random(bits * 31L + size);
                for (int i = 0; i < data.length; i++) {
                    data[i] = random.nextLong();
                }
                // Mask off any garbage in the unused tail bits of the final word,
                // mirroring what a real packer would leave there (zeros), so the
                // only thing under test is the extraction logic itself.
                if (data.length > 0) {
                    int written = (data.length - 1) * (int) valuesPerLongCalc;
                    int remaining = size - written;
                    if (remaining > 0 && remaining < valuesPerLongCalc) {
                        long validBits = remaining * (long) bits;
                        long tailMask = validBits >= 64 ? -1L : (1L << validBits) - 1L;
                        data[data.length - 1] &= tailMask;
                    }
                }

                int[] scalarOut = new int[size];
                int[] vectorOut = new int[size];
                SCALAR.unpack(data, bits, size, scalarOut);
                VECTOR.unpack(data, bits, size, vectorOut);

                assertArrayEquals(scalarOut, vectorOut, "scalar/vector mismatch bits=" + bits + " size=" + size);
            }
        }
    }

    /**
     * {@link #SIZES} is a fixed list, so whether it actually exercises
     * {@code SimdPackedBitsKernels}'s vectorized loop body (as opposed to
     * only ever hitting its scalar tail) depends on this machine's vector
     * lane width -- a size that lands exactly on a chunk boundary on one
     * platform may fall entirely within the tail on a platform with wider
     * lanes. This test computes the actual chunk size
     * {@code SimdPackedBitsKernels} uses for bits 8/16/32 from the runtime
     * species directly (the same construction the kernel itself uses) and
     * targets sizes immediately below, at, and above one and two chunk
     * boundaries, so the vectorized loop body is provably exercised
     * regardless of the platform this test happens to run on.
     */
    @Test
    void vectorPathBoundariesAreExercisedRegardlessOfPlatformLaneWidth() {
        VectorSpecies<Integer> intSpecies = IntVector.SPECIES_PREFERRED;
        int byteChunk = VectorSpecies.of(byte.class, intSpecies.vectorShape()).length();
        int shortChunk = VectorSpecies.of(short.class, intSpecies.vectorShape()).length();
        int intChunk = intSpecies.length();

        checkAroundChunkBoundaries(4, 16); // one full long always yields exactly 16 4-bit values
        checkAroundChunkBoundaries(5, 64 / 5); // 12 values/long, straddles the LANES_4=8 sub-chunk boundary
        checkAroundChunkBoundaries(6, 64 / 6); // 10 values/long
        checkAroundChunkBoundaries(7, 64 / 7); // 9 values/long
        checkAroundChunkBoundaries(15, 64 / 15); // 4 values/long, the global palette width
        checkAroundChunkBoundaries(8, byteChunk);
        checkAroundChunkBoundaries(16, shortChunk);
        checkAroundChunkBoundaries(32, intChunk);
    }

    private void checkAroundChunkBoundaries(int bits, int chunkSize) {
        Set<Integer> sizes = new LinkedHashSet<>();
        sizes.add(0);
        sizes.add(1);
        for (int multiple = 1; multiple <= 2; multiple++) {
            int boundary = multiple * chunkSize;
            sizes.add(boundary - 1);
            sizes.add(boundary);
            sizes.add(boundary + 1);
        }
        sizes.add(5 * chunkSize + 3); // several full chunks plus a non-trivial tail

        Random random = new Random(bits * 7_919L);
        long mask = (1L << bits) - 1L;
        for (int size : sizes) {
            int[] source = new int[size];
            for (int i = 0; i < size; i++) {
                source[i] = random.nextInt((int) Math.min(mask + 1, Integer.MAX_VALUE));
            }
            SimpleBitStorage reference = new SimpleBitStorage(bits, size, source);
            long[] rawData = reference.getRaw();

            int[] scalarOut = new int[size];
            int[] vectorOut = new int[size];
            SCALAR.unpack(rawData, bits, size, scalarOut);
            VECTOR.unpack(rawData, bits, size, vectorOut);

            int[] expected = new int[size];
            for (int i = 0; i < size; i++) {
                expected[i] = reference.get(i);
            }

            assertArrayEquals(expected, vectorOut,
                    () -> "vector mismatch at chunk boundary, bits=" + bits + " size=" + size + " chunkSize=" + chunkSize);
            assertArrayEquals(expected, scalarOut,
                    () -> "scalar mismatch at chunk boundary, bits=" + bits + " size=" + size);
        }
    }

    /**
     * {@code SimpleBitStorageMixin.vectorx$getAll} reuses the {@code unpack}
     * kernel and then dispatches to the consumer from that array in index
     * order, on the assumption that real {@code SimpleBitStorage.getAll}
     * calls its consumer in exactly that order. This locks that assumption
     * down against the real, unmodified class, independent of the Mixin
     * (which JUnit's plain classpath never applies).
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 4, 5, 8, 15, 16, 32})
    void getAllCallOrderMatchesUnpackArrayOrder(int bits) {
        Random random = new Random(bits * 3_000_017L + 13);
        for (int size : SIZES) {
            long mask = (1L << bits) - 1L;
            int[] source = new int[size];
            for (int i = 0; i < size; i++) {
                source[i] = random.nextInt((int) Math.min(mask + 1, Integer.MAX_VALUE));
            }

            SimpleBitStorage reference = new SimpleBitStorage(bits, size, source);

            int[] unpacked = new int[size];
            VECTOR.unpack(reference.getRaw(), bits, size, unpacked);

            List<Integer> viaGetAll = new ArrayList<>(size);
            reference.getAll(viaGetAll::add);

            assertArrayEquals(unpacked, viaGetAll.stream().mapToInt(Integer::intValue).toArray(),
                    () -> "getAll() call order diverges from unpack() array order, bits=" + bits + " size=" + size);
        }
    }

    @Test
    void invalidArgumentsThrowIdenticallyOnBothBackends() {
        long[] data = new long[10];

        assertThrows(IllegalArgumentException.class, () -> SCALAR.unpack(data, 0, 5, new int[5]));
        assertThrows(IllegalArgumentException.class, () -> VECTOR.unpack(data, 0, 5, new int[5]));

        assertThrows(IllegalArgumentException.class, () -> SCALAR.unpack(data, 33, 5, new int[5]));
        assertThrows(IllegalArgumentException.class, () -> VECTOR.unpack(data, 33, 5, new int[5]));

        assertThrows(IllegalArgumentException.class, () -> SCALAR.unpack(data, 8, -1, new int[5]));
        assertThrows(IllegalArgumentException.class, () -> VECTOR.unpack(data, 8, -1, new int[5]));

        assertThrows(IndexOutOfBoundsException.class, () -> SCALAR.unpack(data, 8, 100, new int[5]));
        assertThrows(IndexOutOfBoundsException.class, () -> VECTOR.unpack(data, 8, 100, new int[5]));

        assertThrows(IllegalArgumentException.class, () -> SCALAR.unpack(new long[1], 8, 100, new int[100]));
        assertThrows(IllegalArgumentException.class, () -> VECTOR.unpack(new long[1], 8, 100, new int[100]));

        assertThrows(NullPointerException.class, () -> SCALAR.unpack(null, 8, 0, new int[0]));
        assertThrows(NullPointerException.class, () -> VECTOR.unpack(null, 8, 0, new int[0]));

        int[] values = new int[10];

        assertThrows(IllegalArgumentException.class, () -> SCALAR.pack(values, 0, 5, new long[5]));
        assertThrows(IllegalArgumentException.class, () -> VECTOR.pack(values, 0, 5, new long[5]));

        assertThrows(IllegalArgumentException.class, () -> SCALAR.pack(values, 33, 5, new long[5]));
        assertThrows(IllegalArgumentException.class, () -> VECTOR.pack(values, 33, 5, new long[5]));

        assertThrows(IllegalArgumentException.class, () -> SCALAR.pack(values, 8, -1, new long[5]));
        assertThrows(IllegalArgumentException.class, () -> VECTOR.pack(values, 8, -1, new long[5]));

        assertThrows(IndexOutOfBoundsException.class, () -> SCALAR.pack(values, 8, 100, new long[5]));
        assertThrows(IndexOutOfBoundsException.class, () -> VECTOR.pack(values, 8, 100, new long[5]));

        assertThrows(IllegalArgumentException.class, () -> SCALAR.pack(new int[100], 8, 100, new long[1]));
        assertThrows(IllegalArgumentException.class, () -> VECTOR.pack(new int[100], 8, 100, new long[1]));

        assertThrows(NullPointerException.class, () -> SCALAR.pack(null, 8, 0, new long[0]));
        assertThrows(NullPointerException.class, () -> VECTOR.pack(null, 8, 0, new long[0]));
    }
}
