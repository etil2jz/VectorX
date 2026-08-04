package xyz.blanchot.vectorx.kernel.simd;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorShape;
import jdk.incubator.vector.VectorSpecies;
import xyz.blanchot.vectorx.kernel.PackedBitsKernels;
import xyz.blanchot.vectorx.kernel.SelfDescribing;
import xyz.blanchot.vectorx.kernel.scalar.ScalarPackedBitsKernels;

import java.lang.foreign.MemorySegment;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * Vector API backend for packed-bits packing and unpacking.
 *
 * <p>Bit widths 8, 16 and 32 align to a whole number of bytes, so they're
 * vectorized by reinterpreting the packed {@code long[]} as a byte/short/int
 * view (zero-copy, via {@code MemorySegment}) with a zero-extending widen.
 * Bit widths 4, 5, 6, 7 and 15 are not byte-aligned, so they instead use a
 * broadcast-shift-mask technique (see {@link #unpack4}). Packing is
 * vectorized the same way in reverse for bit width 4 (see {@link #pack4}:
 * mask, shift left, OR-reduce each chunk's lanes into one scalar
 * {@code long}). Every other width/direction delegates to
 * {@link ScalarPackedBitsKernels}.
 *
 * <p>The byte/short species share the exact {@code VectorShape} of
 * {@code IntVector.SPECIES_PREFERRED}, so the byte-to-int and short-to-int
 * lane ratios used below are exactly 4 and 2 by construction on any
 * platform, never a hardcoded AVX2/AVX-512/NEON assumption.
 *
 * <p><b>Every species/shift/mask vector below MUST be a {@code static final}
 * field referenced directly by its own dedicated per-bit-width method --
 * never passed as a parameter, never looked up from a runtime {@code bits}-
 * keyed cache.</b> A prototype that fetched them from a {@code HashMap}
 * measured a 11x slowdown vs. scalar: routing a {@code VectorSpecies}/
 * {@code Vector} through any indirection stops the JIT treating it as a
 * compile-time constant, which this API's intrinsics require. The identical
 * arithmetic with {@code static final} fields measured ~12x faster than
 * scalar instead.
 */
public final class SimdPackedBitsKernels implements PackedBitsKernels, SelfDescribing {

    public static final SimdPackedBitsKernels INSTANCE = new SimdPackedBitsKernels();

    private static final VectorSpecies<Integer> INT_SPECIES = IntVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Byte> BYTE_SPECIES = VectorSpecies.of(byte.class, INT_SPECIES.vectorShape());
    private static final VectorSpecies<Short> SHORT_SPECIES = VectorSpecies.of(short.class, INT_SPECIES.vectorShape());
    private static final int BYTE_TO_INT_PARTS = BYTE_SPECIES.length() / INT_SPECIES.length();
    private static final int SHORT_TO_INT_PARTS = SHORT_SPECIES.length() / INT_SPECIES.length();

    private static final int VALUES_PER_LONG_4 = 16; // 64 bits / 4-bit width
    private static final VectorSpecies<Long> LONG_SPECIES_4 = VectorSpecies.of(long.class, INT_SPECIES.vectorShape());
    private static final int LANES_4 = LONG_SPECIES_4.length();
    // True on every vector shape the Vector API currently defines (up to
    // 512-bit native, giving at most 8 long lanes); guarded rather than
    // assumed, so an exotic future/emulated shape fails open to scalar
    // instead of computing a wrong pass count.
    private static final boolean UNPACK4_SUPPORTED = LANES_4 > 0 && LANES_4 <= VALUES_PER_LONG_4
            && VALUES_PER_LONG_4 % LANES_4 == 0;
    private static final int PASSES_PER_LONG_4 = UNPACK4_SUPPORTED ? VALUES_PER_LONG_4 / LANES_4 : 0;
    private static final VectorSpecies<Integer> INT_SPECIES_4 = UNPACK4_SUPPORTED
            ? VectorSpecies.of(int.class, VectorShape.forBitSize(LANES_4 * Integer.SIZE))
            : null;
    private static final LongVector MASK_VEC_4 = UNPACK4_SUPPORTED
            ? LongVector.broadcast(LONG_SPECIES_4, 0xFL)
            : null;
    private static final LongVector[] SHIFT_BASE_4 = buildShiftBases4();
    private static final int VALUES_PER_LONG_5 = 64 / 5;

    // Odd bit widths (5, 6, 7, 15): same broadcast-shift-mask technique as
    // unpack4/pack4, reusing the platform-derived LONG_SPECIES_4/LANES_4/
    // INT_SPECIES_4 fields (the "_4" suffix is historical, not bits=4
    // specific). Unlike unpack4, VALUES_PER_LONG doesn't divide evenly by
    // LANES_4 here -- e.g. bits=5 packs 12 values/long against 8 lanes, so a
    // long's second chunk only has 4 valid lanes -- handled with a masked
    // partial store (INT_SPECIES_4.indexInRange). Widths 9/10 were
    // prototyped too but aren't wired in: SimpleBitStorage.createForBlockStates
    // never produces 9- or 10-bit sections in real Minecraft.
    private static final int CHUNKS_PER_LONG_5 = ceilDiv(VALUES_PER_LONG_5);
    private static final LongVector MASK_VEC_5 = LongVector.broadcast(LONG_SPECIES_4, (1L << 5) - 1);
    private static final LongVector[] SHIFT_BASE_5 = buildOddShiftBases(5, CHUNKS_PER_LONG_5);
    private static final int VALUES_PER_LONG_6 = 64 / 6;
    private static final int CHUNKS_PER_LONG_6 = ceilDiv(VALUES_PER_LONG_6);
    private static final LongVector MASK_VEC_6 = LongVector.broadcast(LONG_SPECIES_4, (1L << 6) - 1);
    private static final LongVector[] SHIFT_BASE_6 = buildOddShiftBases(6, CHUNKS_PER_LONG_6);
    private static final int VALUES_PER_LONG_7 = 64 / 7;
    private static final int CHUNKS_PER_LONG_7 = ceilDiv(VALUES_PER_LONG_7);
    private static final LongVector MASK_VEC_7 = LongVector.broadcast(LONG_SPECIES_4, (1L << 7) - 1);
    private static final LongVector[] SHIFT_BASE_7 = buildOddShiftBases(7, CHUNKS_PER_LONG_7);
    private static final int VALUES_PER_LONG_15 = 64 / 15;
    private static final int CHUNKS_PER_LONG_15 = ceilDiv(VALUES_PER_LONG_15);
    private static final LongVector MASK_VEC_15 = LongVector.broadcast(LONG_SPECIES_4, (1L << 15) - 1);
    private static final LongVector[] SHIFT_BASE_15 = buildOddShiftBases(15, CHUNKS_PER_LONG_15);
    private SimdPackedBitsKernels() {
    }

    private static LongVector[] buildShiftBases4() {
        if (!UNPACK4_SUPPORTED) {
            return new LongVector[0];
        }
        LongVector laneIndex = LongVector.zero(LONG_SPECIES_4).addIndex(1);
        LongVector[] bases = new LongVector[PASSES_PER_LONG_4];
        for (int pass = 0; pass < PASSES_PER_LONG_4; pass++) {
            bases[pass] = laneIndex.add((long) pass * LANES_4).mul(4L);
        }
        return bases;
    }

    private static int ceilDiv(int a) {
        return (a + SimdPackedBitsKernels.LANES_4 - 1) / SimdPackedBitsKernels.LANES_4;
    }

    private static LongVector[] buildOddShiftBases(int bits, int chunks) {
        LongVector laneIndex = LongVector.zero(LONG_SPECIES_4).addIndex(1);
        LongVector[] bases = new LongVector[chunks];
        for (int chunk = 0; chunk < chunks; chunk++) {
            bases[chunk] = laneIndex.add((long) chunk * LANES_4).mul(bits);
        }
        return bases;
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
        int requiredLongs = (size + valuesPerLong - 1) / valuesPerLong;
        if (data.length < requiredLongs) {
            throw new IllegalArgumentException("data too short: need at least " + requiredLongs
                    + " longs for size=" + size + " bits=" + bits + ", got " + data.length);
        }

        switch (bits) {
            case 4 -> {
                if (UNPACK4_SUPPORTED) {
                    unpack4(data, size, output);
                } else {
                    ScalarPackedBitsKernels.INSTANCE.unpack(data, bits, size, output);
                }
            }
            case 5 -> unpack5(data, size, output);
            case 6 -> unpack6(data, size, output);
            case 7 -> unpack7(data, size, output);
            case 8 -> unpack8(data, size, output);
            case 15 -> unpack15(data, size, output);
            case 16 -> unpack16(data, size, output);
            case 32 -> unpack32(data, size, output);
            default -> ScalarPackedBitsKernels.INSTANCE.unpack(data, bits, size, output);
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
        int requiredLongs = (size + valuesPerLong - 1) / valuesPerLong;
        if (output.length < requiredLongs) {
            throw new IllegalArgumentException("output too short: need at least " + requiredLongs
                    + " longs for size=" + size + " bits=" + bits + ", got " + output.length);
        }

        if (bits == 4 && UNPACK4_SUPPORTED) {
            pack4(values, size, output);
        } else {
            ScalarPackedBitsKernels.INSTANCE.pack(values, bits, size, output);
        }
    }

    private void pack4(int[] values, int size, long[] output) {
        int fullLongs = size / VALUES_PER_LONG_4;
        int li = 0;
        for (; li < fullLongs; li++) {
            int base = li * VALUES_PER_LONG_4;
            long word = 0L;
            for (int pass = 0; pass < PASSES_PER_LONG_4; pass++) {
                IntVector iv = IntVector.fromArray(INT_SPECIES_4, values, base + pass * LANES_4);
                LongVector widened = (LongVector) iv.convertShape(VectorOperators.ZERO_EXTEND_I2L, LONG_SPECIES_4, 0);
                LongVector shifted = widened.lanewise(VectorOperators.AND, MASK_VEC_4)
                        .lanewise(VectorOperators.LSHL, SHIFT_BASE_4[pass]);
                word |= shifted.reduceLanes(VectorOperators.OR);
            }
            output[li] = word;
        }
        if (li * VALUES_PER_LONG_4 < size) {
            long word = 0L;
            int slot = 0;
            for (int i = li * VALUES_PER_LONG_4; i < size; i++, slot++) {
                word |= ((long) values[i] & 0xFL) << (slot * 4);
            }
            output[li] = word;
        }
    }

    private void unpack5(long[] data, int size, int[] output) {
        int fullLongs = size / VALUES_PER_LONG_5;
        int li = 0;
        for (; li < fullLongs; li++) {
            long word = data[li];
            LongVector broadcast = LongVector.broadcast(LONG_SPECIES_4, word);
            int base = li * VALUES_PER_LONG_5;
            for (int chunk = 0; chunk < CHUNKS_PER_LONG_5; chunk++) {
                LongVector masked = broadcast.lanewise(VectorOperators.LSHR, SHIFT_BASE_5[chunk])
                        .lanewise(VectorOperators.AND, MASK_VEC_5);
                IntVector iv = (IntVector) masked.convertShape(VectorOperators.L2I, INT_SPECIES_4, 0);
                int outBase = base + chunk * LANES_4;
                int chunkLen = Math.min(LANES_4, VALUES_PER_LONG_5 - chunk * LANES_4);
                if (chunkLen == LANES_4) {
                    iv.intoArray(output, outBase);
                } else {
                    iv.intoArray(output, outBase, INT_SPECIES_4.indexInRange(0, chunkLen));
                }
            }
        }
        for (int i = li * VALUES_PER_LONG_5; i < size; i++) {
            long word = data[i / VALUES_PER_LONG_5];
            int slot = i % VALUES_PER_LONG_5;
            output[i] = (int) ((word >>> (slot * 5)) & 0x1FL);
        }
    }

    private void unpack6(long[] data, int size, int[] output) {
        int fullLongs = size / VALUES_PER_LONG_6;
        int li = 0;
        for (; li < fullLongs; li++) {
            long word = data[li];
            LongVector broadcast = LongVector.broadcast(LONG_SPECIES_4, word);
            int base = li * VALUES_PER_LONG_6;
            for (int chunk = 0; chunk < CHUNKS_PER_LONG_6; chunk++) {
                LongVector masked = broadcast.lanewise(VectorOperators.LSHR, SHIFT_BASE_6[chunk])
                        .lanewise(VectorOperators.AND, MASK_VEC_6);
                IntVector iv = (IntVector) masked.convertShape(VectorOperators.L2I, INT_SPECIES_4, 0);
                int outBase = base + chunk * LANES_4;
                int chunkLen = Math.min(LANES_4, VALUES_PER_LONG_6 - chunk * LANES_4);
                if (chunkLen == LANES_4) {
                    iv.intoArray(output, outBase);
                } else {
                    iv.intoArray(output, outBase, INT_SPECIES_4.indexInRange(0, chunkLen));
                }
            }
        }
        for (int i = li * VALUES_PER_LONG_6; i < size; i++) {
            long word = data[i / VALUES_PER_LONG_6];
            int slot = i % VALUES_PER_LONG_6;
            output[i] = (int) ((word >>> (slot * 6)) & 0x3FL);
        }
    }

    private void unpack7(long[] data, int size, int[] output) {
        int fullLongs = size / VALUES_PER_LONG_7;
        int li = 0;
        for (; li < fullLongs; li++) {
            long word = data[li];
            LongVector broadcast = LongVector.broadcast(LONG_SPECIES_4, word);
            int base = li * VALUES_PER_LONG_7;
            for (int chunk = 0; chunk < CHUNKS_PER_LONG_7; chunk++) {
                LongVector masked = broadcast.lanewise(VectorOperators.LSHR, SHIFT_BASE_7[chunk])
                        .lanewise(VectorOperators.AND, MASK_VEC_7);
                IntVector iv = (IntVector) masked.convertShape(VectorOperators.L2I, INT_SPECIES_4, 0);
                int outBase = base + chunk * LANES_4;
                int chunkLen = Math.min(LANES_4, VALUES_PER_LONG_7 - chunk * LANES_4);
                if (chunkLen == LANES_4) {
                    iv.intoArray(output, outBase);
                } else {
                    iv.intoArray(output, outBase, INT_SPECIES_4.indexInRange(0, chunkLen));
                }
            }
        }
        for (int i = li * VALUES_PER_LONG_7; i < size; i++) {
            long word = data[i / VALUES_PER_LONG_7];
            int slot = i % VALUES_PER_LONG_7;
            output[i] = (int) ((word >>> (slot * 7)) & 0x7FL);
        }
    }

    private void unpack15(long[] data, int size, int[] output) {
        int fullLongs = size / VALUES_PER_LONG_15;
        int li = 0;
        for (; li < fullLongs; li++) {
            long word = data[li];
            LongVector broadcast = LongVector.broadcast(LONG_SPECIES_4, word);
            int base = li * VALUES_PER_LONG_15;
            for (int chunk = 0; chunk < CHUNKS_PER_LONG_15; chunk++) {
                LongVector masked = broadcast.lanewise(VectorOperators.LSHR, SHIFT_BASE_15[chunk])
                        .lanewise(VectorOperators.AND, MASK_VEC_15);
                IntVector iv = (IntVector) masked.convertShape(VectorOperators.L2I, INT_SPECIES_4, 0);
                int outBase = base + chunk * LANES_4;
                int chunkLen = Math.min(LANES_4, VALUES_PER_LONG_15 - chunk * LANES_4);
                if (chunkLen == LANES_4) {
                    iv.intoArray(output, outBase);
                } else {
                    iv.intoArray(output, outBase, INT_SPECIES_4.indexInRange(0, chunkLen));
                }
            }
        }
        for (int i = li * VALUES_PER_LONG_15; i < size; i++) {
            long word = data[i / VALUES_PER_LONG_15];
            int slot = i % VALUES_PER_LONG_15;
            output[i] = (int) ((word >>> (slot * 15)) & 0x7FFFL);
        }
    }

    private void unpack4(long[] data, int size, int[] output) {
        int fullLongs = size / VALUES_PER_LONG_4;
        int li = 0;
        for (; li < fullLongs; li++) {
            long word = data[li];
            LongVector broadcast = LongVector.broadcast(LONG_SPECIES_4, word);
            int base = li * VALUES_PER_LONG_4;
            for (int pass = 0; pass < PASSES_PER_LONG_4; pass++) {
                LongVector masked = broadcast.lanewise(VectorOperators.LSHR, SHIFT_BASE_4[pass])
                        .lanewise(VectorOperators.AND, MASK_VEC_4);
                IntVector iv = (IntVector) masked.convertShape(VectorOperators.L2I, INT_SPECIES_4, 0);
                iv.intoArray(output, base + pass * LANES_4);
            }
        }
        for (int i = li * VALUES_PER_LONG_4; i < size; i++) {
            long word = data[i / VALUES_PER_LONG_4];
            int slot = i % VALUES_PER_LONG_4;
            output[i] = (int) ((word >>> (slot * 4)) & 0xFL);
        }
    }

    private void unpack8(long[] data, int size, int[] output) {
        MemorySegment segment = MemorySegment.ofArray(data);
        int bytesPerChunk = BYTE_SPECIES.length();
        int i = 0;
        int chunkBound = (size / bytesPerChunk) * bytesPerChunk;
        for (; i < chunkBound; i += bytesPerChunk) {
            ByteVector bv = ByteVector.fromMemorySegment(BYTE_SPECIES, segment, i, ByteOrder.LITTLE_ENDIAN);
            for (int part = 0; part < BYTE_TO_INT_PARTS; part++) {
                IntVector iv = (IntVector) bv.convertShape(VectorOperators.ZERO_EXTEND_B2I, INT_SPECIES, part);
                iv.intoArray(output, i + part * INT_SPECIES.length());
            }
        }
        for (; i < size; i++) {
            long word = data[i / 8];
            int byteInWord = i % 8;
            output[i] = (int) (word >>> (byteInWord * 8) & 0xFFL);
        }
    }

    private void unpack16(long[] data, int size, int[] output) {
        MemorySegment segment = MemorySegment.ofArray(data);
        int shortsPerChunk = SHORT_SPECIES.length();
        int i = 0;
        int chunkBound = (size / shortsPerChunk) * shortsPerChunk;
        for (; i < chunkBound; i += shortsPerChunk) {
            ShortVector sv = ShortVector.fromMemorySegment(SHORT_SPECIES, segment, (long) i * 2, ByteOrder.LITTLE_ENDIAN);
            for (int part = 0; part < SHORT_TO_INT_PARTS; part++) {
                IntVector iv = (IntVector) sv.convertShape(VectorOperators.ZERO_EXTEND_S2I, INT_SPECIES, part);
                iv.intoArray(output, i + part * INT_SPECIES.length());
            }
        }
        for (; i < size; i++) {
            long word = data[i / 4];
            int shortInWord = i % 4;
            output[i] = (int) (word >>> (shortInWord * 16) & 0xFFFFL);
        }
    }

    private void unpack32(long[] data, int size, int[] output) {
        MemorySegment segment = MemorySegment.ofArray(data);
        int intsPerChunk = INT_SPECIES.length();
        int i = 0;
        int chunkBound = (size / intsPerChunk) * intsPerChunk;
        for (; i < chunkBound; i += intsPerChunk) {
            IntVector iv = IntVector.fromMemorySegment(INT_SPECIES, segment, (long) i * 4, ByteOrder.LITTLE_ENDIAN);
            iv.intoArray(output, i);
        }
        for (; i < size; i++) {
            long word = data[i / 2];
            int intInWord = i % 2;
            output[i] = (int) (word >>> (intInWord * 32));
        }
    }

    @Override
    public String describe() {
        return "int=" + INT_SPECIES + ", byte=" + BYTE_SPECIES + " (" + BYTE_TO_INT_PARTS
                + " parts/int), short=" + SHORT_SPECIES + " (" + SHORT_TO_INT_PARTS + " parts/int), long4="
                + LONG_SPECIES_4 + " (" + PASSES_PER_LONG_4 + " passes/long, supported=" + UNPACK4_SUPPORTED
                + "); unpack vectorizes bits in {4,5,6,7,8,15,16,32}, pack vectorizes bits in {4}";
    }
}
