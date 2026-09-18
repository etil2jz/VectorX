package xyz.blanchot.vectorx.mixin;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes real Minecraft 26.3's {@code DensityBuffer.values} -- a
 * {@code protected final float[]} -- so a vector kernel can be handed the
 * backing array directly.
 *
 * <p>Without this, the only way through the public API is
 * {@code get(int)}/{@code set(int, float)} one element at a time, which is
 * exactly the scalar loop the kernel is meant to replace; copying in and out
 * of a scratch array instead would cost two extra passes over the data and
 * an allocation per call, wiping out the gain on the small buffers chunk
 * generation actually uses.
 *
 * <p>This targets {@code DensityBuffer} itself rather than
 * {@code ScopedDensityBuffer}: {@code ScopedDensityBuffer} extends it (as may
 * any future subclass), and a Mixin on the base class applies to the whole
 * hierarchy, so the accessor is available whichever concrete buffer the
 * sampler is handed.
 *
 * <p>Callers must respect {@code DensityBuffer.size()} rather than
 * {@code values.length}: for a pooled {@code ScopedDensityBuffer} the array
 * is the arena slot's capacity and is generally longer than the live sample.
 */
@Mixin(DensityBuffer.class)
public interface DensityBufferAccessor {

    @Accessor("values")
    float[] vectorx$values();
}
