package xyz.blanchot.vectorx.mixin;

import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xyz.blanchot.vectorx.VectorX;

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
            return new SimpleBitStorage(bits, size, values);
        }
    }

    @Redirect(method = "pack", at = @At(value = "NEW", target = "net/minecraft/util/SimpleBitStorage"))
    private SimpleBitStorage vectorx$redirectPack(int bits, int size, int[] values) {
        return vectorx$vectorizedConstruct(bits, size, values);
    }
}
