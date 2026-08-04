package xyz.blanchot.vectorx.mixin;

import net.minecraft.util.SimpleBitStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.blanchot.vectorx.VectorX;

/**
 * Vectorizes {@code SimpleBitStorage.unpack(int[])} -- bulk-extracting a
 * bit-packed palette-index array, reached from both
 * {@code PalettedContainer.pack()} (chunk save) and the static
 * {@code PalettedContainer.unpack(Strategy, PackedData)} factory (chunk
 * load).
 *
 * <p>Uses a cancellable {@code @Inject} at the method's head: on success it
 * fills {@code output} itself and cancels the injection so Mojang's body
 * never runs; on any failure it does not cancel, so Mojang's unmodified
 * body runs exactly as vanilla would.
 *
 * <p>Conflict surface (see {@link xyz.blanchot.vectorx.compat.CompatibilityRegistry}):
 * Lithium's own {@code PalettedContainerMixin} fully {@code @Overwrite}s
 * {@code PalettedContainer.pack()} without calling
 * {@code SimpleBitStorage.unpack()}, so this injection is inert (not
 * broken) on the chunk-save path when Lithium is installed; the chunk-load
 * path is untouched by Lithium and always benefits.
 */
@Mixin(SimpleBitStorage.class)
public abstract class SimpleBitStorageMixin {

    @Shadow
    @Final
    private long[] data;

    @Shadow
    @Final
    private int bits;

    @Shadow
    @Final
    private int size;

    @Inject(method = "unpack", at = @At("HEAD"), cancellable = true)
    private void vectorx$unpack(int[] output, CallbackInfo ci) {
        try {
            VectorX.packedBits().unpack(this.data, this.bits, this.size, output);
            ci.cancel();
        } catch (Throwable t) {
            // Fall through to Mojang's own unmodified method body below;
            // never let a dispatch failure propagate into chunk save/load.
        }
    }
}
