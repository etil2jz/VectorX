package xyz.blanchot.vectorx.mixin;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.op.UnaryFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.blanchot.vectorx.VectorX;
import xyz.blanchot.vectorx.kernel.DensityMapOp;

@Mixin(targets = {
        "net.minecraft.world.level.levelgen.densityfunction.op.UnaryFunction$SqueezeSampler",
        "net.minecraft.world.level.levelgen.densityfunction.op.UnaryFunction$LeakyReLUSampler"
})
public abstract class UnaryFunctionSamplerMixin {

    @Unique
    private static float[] vectorx$values(DensityBuffer buffer) {
        return ((DensityBufferAccessor) buffer).vectorx$values();
    }

    @Inject(method = "sampleVolume", at = @At("HEAD"), cancellable = true)
    private void vectorx$sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume, CallbackInfo ci) {
        try {
            Object self = this;
            if (self instanceof UnaryFunction.SqueezeSampler squeeze) {
                squeeze.input().sampleVolume(context, outputBuffer, volume);
                VectorX.densityMap().apply(vectorx$values(outputBuffer), outputBuffer.size(), DensityMapOp.SQUEEZE);
            } else if (self instanceof UnaryFunction.LeakyReLUSampler leakyReLU) {
                leakyReLU.input().sampleVolume(context, outputBuffer, volume);
                VectorX.densityMap().leakyReLU(vectorx$values(outputBuffer), outputBuffer.size(), leakyReLU.negativeFactor());
            } else {
                return;
            }
            ci.cancel();
        } catch (Throwable _) {
        }
    }
}
