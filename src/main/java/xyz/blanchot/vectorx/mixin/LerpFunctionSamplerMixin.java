package xyz.blanchot.vectorx.mixin;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.op.LerpFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.blanchot.vectorx.VectorX;

@Mixin(targets = "net.minecraft.world.level.levelgen.densityfunction.op.LerpFunction$Sampler")
public abstract class LerpFunctionSamplerMixin {

    @Unique
    private static float[] vectorx$values(DensityBuffer buffer) {
        return ((DensityBufferAccessor) buffer).vectorx$values();
    }

    @Inject(method = "sampleVolume", at = @At("HEAD"), cancellable = true)
    private void vectorx$sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume, CallbackInfo ci) {
        try {
            LerpFunction.Sampler self = (LerpFunction.Sampler) (Object) this;
            self.alpha().sampleVolume(context, outputBuffer, volume);
            try (ScopedDensityBuffer firstBuffer = context.acquireBuffer(volume)) {
                self.first().sampleVolume(context, firstBuffer, volume);
                try (ScopedDensityBuffer secondBuffer = context.acquireBuffer(volume)) {
                    self.second().sampleVolume(context, secondBuffer, volume);
                    VectorX.select().lerp(vectorx$values(outputBuffer), vectorx$values(firstBuffer), vectorx$values(secondBuffer), outputBuffer.size());
                }
            }
            ci.cancel();
        } catch (Throwable _) {
        }
    }
}
