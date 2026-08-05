package xyz.blanchot.vectorx.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.carver.CarverConfiguration;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.function.Function;

/**
 * {@code carveBlock} is {@code protected} in {@code WorldCarver}, so
 * {@code CanyonWorldCarverMixin}'s reimplementation of {@code carveEllipsoid}
 * -- a plain {@code @Unique} static helper, not one of Mixin's own
 * specially-treated {@code @Inject}/{@code @Redirect} handlers -- can't call
 * it directly without a real Java subclass relationship. This accessor
 * mixin is Mixin's own documented mechanism for exactly that: it targets
 * {@code WorldCarver} (where {@code carveBlock} is actually declared) and
 * synthesizes a public-equivalent bridge to it, regardless of the original
 * method's visibility.
 */
@Mixin(WorldCarver.class)
public interface WorldCarverAccessor {

    @Invoker("carveBlock")
    boolean vectorx$invokeCarveBlock(
            CarvingContext context,
            CarverConfiguration configuration,
            ChunkAccess chunk,
            Function<BlockPos, Holder<Biome>> biomeGetter,
            CarvingMask mask,
            BlockPos.MutableBlockPos blockPos,
            BlockPos.MutableBlockPos helperPos,
            Aquifer aquifer,
            MutableBoolean hasGrass
    );

    /**
     * Same story as {@link #vectorx$invokeCarveBlock}: {@code carveEllipsoid}
     * is also {@code protected}, needed here purely for
     * {@code CanyonWorldCarverMixin}'s fail-open fallback (calling the real,
     * unmodified method on any reimplementation failure).
     */
    @Invoker("carveEllipsoid")
    boolean vectorx$invokeCarveEllipsoid(
            CarvingContext context,
            CarverConfiguration configuration,
            ChunkAccess chunk,
            Function<BlockPos, Holder<Biome>> biomeGetter,
            Aquifer aquifer,
            double x,
            double y,
            double z,
            double horizontalRadius,
            double verticalRadius,
            CarvingMask mask,
            WorldCarver.CarveSkipChecker skipChecker
    );
}
