package xyz.blanchot.vectorx.mixin;

import net.minecraft.util.SimpleBitStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.blanchot.vectorx.VectorX;

import java.util.function.IntConsumer;

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
        } catch (Throwable _) {
        }
    }

    @Inject(method = "getAll", at = @At("HEAD"), cancellable = true)
    private void vectorx$getAll(IntConsumer output, CallbackInfo ci) {
        try {
            int[] scratch = new int[this.size];
            VectorX.packedBits().unpack(this.data, this.bits, this.size, scratch);
            for (int value : scratch) {
                output.accept(value);
            }
            ci.cancel();
        } catch (Throwable _) {
        }
    }
}
