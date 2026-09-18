package xyz.blanchot.vectorx.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.CarverOutput;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.carver.CanyonWorldCarver;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xyz.blanchot.vectorx.VectorX;
import xyz.blanchot.vectorx.carve.CanyonCarveGeometry;

@Mixin(CanyonWorldCarver.class)
public abstract class CanyonWorldCarverMixin {

    @Unique
    private static void vectorx$vectorizedCarveEllipsoid(WorldGenerationContext context, ChunkPos chunkPos, double x, double y, double z, double horizontalRadius, double verticalRadius, CarverOutput output, float[] widthFactorPerHeight) {
        CanyonCarveGeometry.sweepVectorized(chunkPos, x, y, z, horizontalRadius, verticalRadius, output.minY(), output.maxY(), context.getMinGenY(), widthFactorPerHeight, VectorX.carverSkip(), output::carve);
    }

    @Redirect(
            method = "doCarve",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/carver/WorldCarver;carveEllipsoid("
                            + "Lnet/minecraft/world/level/ChunkPos;"
                            + "DDDDD"
                            + "Lnet/minecraft/world/level/chunk/CarverOutput;"
                            + "Lnet/minecraft/world/level/levelgen/carver/WorldCarver$CarveSkipChecker;"
                            + ")V"
            )
    )
    private void vectorx$carveEllipsoid(ChunkPos chunkPos, double x, double y, double z, double horizontalRadius, double verticalRadius, CarverOutput output, WorldCarver.CarveSkipChecker skipChecker, @Local(argsOnly = true, name = "context") WorldGenerationContext context, @Local(name = "widthFactorPerHeight") float[] widthFactorPerHeight) {
        try {
            vectorx$vectorizedCarveEllipsoid(context, chunkPos, x, y, z, horizontalRadius, verticalRadius, output, widthFactorPerHeight);
        } catch (Throwable t) {
            WorldCarver.carveEllipsoid(chunkPos, x, y, z, horizontalRadius, verticalRadius, output, skipChecker);
        }
    }
}
