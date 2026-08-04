package xyz.blanchot.vectorx.mixin;

import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xyz.blanchot.vectorx.VectorX;

/**
 * Vectorizes the {@code int[]} -&gt; bit-packed {@code long[]} direction, the
 * inverse of what {@code mixin.SimpleBitStorageMixin} vectorizes.
 *
 * <p>The packing loop lives inline in {@code SimpleBitStorage}'s
 * {@code (int, int, int[])} constructor, and Mixin refuses a cancellable
 * {@code @Inject} on any constructor. So instead this redirects the
 * object-creation expression itself, at its two call sites in
 * {@code PalettedContainer}: {@code pack(Strategy)} and the static
 * {@code unpack(Strategy, PackedData)} factory -- both ordinary methods, so
 * the constructor restriction doesn't apply.
 *
 * <p>The redirect handler either builds the packed {@code long[]} via
 * {@code PackedBitsKernels.pack} and hands it to {@code SimpleBitStorage}'s
 * other, non-looping {@code (int, int, long[])} constructor, or on any
 * failure falls back to the real {@code (int, int, int[])} constructor
 * unmodified.
 *
 * <p>Conflict surface: Lithium's own {@code PalettedContainerMixin} fully
 * {@code @Overwrite}s {@code pack(Strategy)} without the {@code new
 * SimpleBitStorage(int, int, int[])} expression this redirect targets, so
 * the redirect silently doesn't apply there (see
 * {@code compat.CompatibilityRegistry}); {@code unpack(Strategy, PackedData)}
 * is untouched by Lithium and always benefits.
 */
@Mixin(PalettedContainer.class)
public abstract class PalettedContainerMixin {

    @Redirect(method = "unpack", at = @At(value = "NEW", target = "net/minecraft/util/SimpleBitStorage"))
    private static SimpleBitStorage vectorx$redirectUnpack(int bits, int size, int[] values) {
        return vectorx$vectorizedConstruct(bits, size, values);
    }

    @Unique
    private static SimpleBitStorage vectorx$vectorizedConstruct(int bits, int size, int[] values) {
        try {
            int valuesPerLong = 64 / bits;
            int requiredLongs = (size + valuesPerLong - 1) / valuesPerLong;
            long[] packed = new long[requiredLongs];
            VectorX.packedBits().pack(values, bits, size, packed);
            return new SimpleBitStorage(bits, size, packed);
        } catch (Throwable t) {
            // Fall through to the exact real constructor, unmodified; never
            // let a dispatch failure propagate into chunk save/load.
            return new SimpleBitStorage(bits, size, values);
        }
    }

    @Redirect(method = "pack", at = @At(value = "NEW", target = "net/minecraft/util/SimpleBitStorage"))
    private SimpleBitStorage vectorx$redirectPack(int bits, int size, int[] values) {
        return vectorx$vectorizedConstruct(bits, size, values);
    }
}
