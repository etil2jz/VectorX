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

@Mixin(targets = "net.minecraft.world.level.levelgen.densityfunction.op.RangeChoiceFunction$ConstSampler")
public abstract class RangeChoiceFunctionConstSamplerMixin {

    @Shadow
    @Final
    private DensitySampler input;

    @Shadow
    @Final
    private float minInclusive;

    @Shadow
    @Final
    private float maxExclusive;

    @Shadow
    @Final
    private float whenInRange;

    @Shadow
    @Final
    private float whenOutOfRange;

    @Inject(method = "sampleVolume", at = @At("HEAD"), cancellable = true)
    private void vectorx$sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume, CallbackInfo ci) {
        try {
            this.input.sampleVolume(context, outputBuffer, volume);
            VectorX.select().rangeChoiceConst(((DensityBufferAccessor) outputBuffer).vectorx$values(), outputBuffer.size(), this.minInclusive, this.maxExclusive, this.whenInRange, this.whenOutOfRange);
            ci.cancel();
        } catch (Throwable _) {
        }
    }
}
