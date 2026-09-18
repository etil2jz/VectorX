package xyz.blanchot.vectorx.mixin;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.blanchot.vectorx.VectorX;

@Mixin(targets = "net.minecraft.world.level.levelgen.densityfunction.op.ClampFunction$Sampler")
public abstract class ClampFunctionSamplerMixin {

    @Shadow
    @Final
    private DensitySampler input;

    @Shadow
    @Final
    private float min;

    @Shadow
    @Final
    private float max;

    @Inject(method = "sampleVolume", at = @At("HEAD"), cancellable = true)
    private void vectorx$sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume, CallbackInfo ci) {
        try {
            this.input.sampleVolume(context, outputBuffer, volume);
            float[] values = ((DensityBufferAccessor) outputBuffer).vectorx$values();
            VectorX.clamp().clampInPlace(values, outputBuffer.size(), this.min, this.max);
            ci.cancel();
        } catch (Throwable _) {
        }
    }
}
