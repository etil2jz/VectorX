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

/**
 * Vectorizes {@code SimpleBitStorage.unpack(int[])} and
 * {@code SimpleBitStorage.getAll(IntConsumer)} -- both bulk-extract the same
 * bit-packed palette-index array, just through a different sink.
 * {@code unpack} is reached from both {@code PalettedContainer.pack()}
 * (chunk save) and the static {@code PalettedContainer.unpack(Strategy,
 * PackedData)} factory (chunk load); {@code getAll} is reached from
 * {@code PalettedContainer.count()} (block/fluid count recompute, done for
 * every chunk section on chunk load via {@code LevelChunkSection}'s
 * states-and-biomes constructor) and from {@code PalettedContainer.getAll(
 * Consumer)} (e.g. the possible-biomes scan in
 * {@code ChunkGenerator.applyBiomeDecoration}).
 *
 * <p>{@code getAll} unpacks into a scratch {@code int[]} with the same
 * kernel {@code unpack} uses, then dispatches to the consumer from that
 * array in order -- same values, same call order as Mojang's inline
 * mask/shift loop, so behaviour is unchanged for whatever the consumer
 * does with them. No separate self-test is needed: correctness rides on
 * the same differential test that already covers {@code unpack}.
 *
 * <p>Both injections are cancellable {@code @Inject}s at the method head:
 * on success they cancel so Mojang's body never runs; on any failure they
 * do not cancel, so Mojang's unmodified body runs exactly as vanilla
 * would.
 *
 * <p>Conflict surface (see {@link xyz.blanchot.vectorx.compat.CompatibilityRegistry}):
 * Lithium's own {@code PalettedContainerMixin} fully {@code @Overwrite}s
 * {@code PalettedContainer.pack()} without calling
 * {@code SimpleBitStorage.unpack()}, so the {@code unpack} injection is
 * inert (not broken) on the chunk-save path when Lithium is installed; the
 * chunk-load path is untouched by Lithium and always benefits. Lithium's
 * block-counting feature ({@code mixin.util.block_tracking}, on by
 * default) was checked against its {@code develop} branch source
 * (2026-08): {@code LevelChunkSectionMixin} only {@code @ModifyArg}s the
 * {@code CountConsumer} passed into {@code PalettedContainer.count()}, and
 * {@code LevelChunkSection$BlockCounterMixin} only {@code @Inject}s into
 * that consumer's own {@code accept()} -- neither touches
 * {@code SimpleBitStorage} or changes how {@code count()} calls
 * {@code getAll()}, so the {@code getAll} injection here has no conflict
 * with Lithium either.
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

    @Inject(method = "getAll", at = @At("HEAD"), cancellable = true)
    private void vectorx$getAll(IntConsumer output, CallbackInfo ci) {
        try {
            int[] scratch = new int[this.size];
            VectorX.packedBits().unpack(this.data, this.bits, this.size, scratch);
            for (int value : scratch) {
                output.accept(value);
            }
            ci.cancel();
        } catch (Throwable t) {
            // Fall through to Mojang's own unmodified method body below;
            // never let a dispatch failure propagate into chunk save/load.
        }
    }
}
