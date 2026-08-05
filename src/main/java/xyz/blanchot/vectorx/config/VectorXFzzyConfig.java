package xyz.blanchot.vectorx.config;

import me.fzzyhmstrs.fzzy_config.annotations.Action;
import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.annotations.RequiresAction;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum;
import net.minecraft.resources.Identifier;
import xyz.blanchot.vectorx.VectorX;
import xyz.blanchot.vectorx.VectorXConfig;
import xyz.blanchot.vectorx.VectorXConfig.KernelMode;
import xyz.blanchot.vectorx.dispatch.CarverSkipDispatcher;
import xyz.blanchot.vectorx.dispatch.ClampDispatcher;
import xyz.blanchot.vectorx.dispatch.DensityMapDispatcher;
import xyz.blanchot.vectorx.dispatch.PackedBitsDispatcher;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fzzy Config adapter: the only class in the project that touches Fzzy Config
 * or TOML. Its sole purpose is producing an immutable {@link VectorXConfig}
 * snapshot via {@link #toSnapshot()} -- everything downstream (dispatchers,
 * their tests) stays free of any GUI/serialization dependency.
 *
 * <p>Registered with {@code RegisterType.CLIENT}: the file is read/created on
 * both sides (client and dedicated server), but the GUI/ModMenu screen only
 * registers where a client actually exists. The config is never synced
 * server-to-client -- this is a per-machine performance setting, not
 * gameplay data.
 *
 * <p>Every field but {@link #diagnostics} is marked
 * {@link Action#RESTART}: the dispatchers resolve their backend once in
 * {@code VectorX.onInitialize()} and the Mixins hold the result in static
 * fields, so a live edit has no effect until the game restarts.
 */
public class VectorXFzzyConfig extends Config {

    @Comment("Force every kernel to the scalar backend, regardless of the settings below. "
            + "Overridden at runtime by the vectorized.forceScalar system property.")
    @RequiresAction(action = Action.RESTART)
    public ValidatedBoolean backendForcedScalar = new ValidatedBoolean(false);
    @Comment("Backend for DensityFunctions.Mapped. auto = vector if it loads and passes its "
            + "self-test, scalar fallback otherwise; scalar = force scalar; off = disable the hook.")
    @RequiresAction(action = Action.RESTART)
    public ValidatedEnum<KernelMode> densityFunctionMap =
            new ValidatedEnum<>(KernelMode.AUTO, ValidatedEnum.WidgetType.POPUP);
    @Comment("Backend for DensityFunctions.Clamp. Same auto/scalar/off semantics as densityFunctionMap.")
    @RequiresAction(action = Action.RESTART)
    public ValidatedEnum<KernelMode> densityFunctionClamp =
            new ValidatedEnum<>(KernelMode.AUTO, ValidatedEnum.WidgetType.POPUP);
    @Comment("Backend for SimpleBitStorage/PalettedContainer unpacking. Same auto/scalar/off "
            + "semantics as densityFunctionMap.")
    @RequiresAction(action = Action.RESTART)
    public ValidatedEnum<KernelMode> packedStorageUnpack =
            new ValidatedEnum<>(KernelMode.AUTO, ValidatedEnum.WidgetType.POPUP);
    @Comment("Backend for CanyonWorldCarver's ellipsoid skip test during cave/canyon "
            + "generation. Same auto/scalar/off semantics as densityFunctionMap.")
    @RequiresAction(action = Action.RESTART)
    public ValidatedEnum<KernelMode> canyonCarverSkip =
            new ValidatedEnum<>(KernelMode.AUTO, ValidatedEnum.WidgetType.POPUP);
    @Comment("Run the scalar-vs-vector differential self-test at startup before trusting a "
            + "kernel's vector backend. Leave this on unless you have a specific reason not to.")
    @RequiresAction(action = Action.RESTART)
    public ValidatedBoolean selfTest = new ValidatedBoolean(true);
    @Comment("Log a full diagnostics block at startup (module resolution, per-kernel backend "
            + "and fallback reason, known mod conflicts). Takes effect immediately.")
    public ValidatedBoolean diagnostics = new ValidatedBoolean(false);

    public VectorXFzzyConfig() {
        super(Identifier.fromNamespaceAndPath(VectorX.MOD_ID, "config"));
    }

    public VectorXConfig toSnapshot() {
        Map<String, KernelMode> modes = new LinkedHashMap<>();
        modes.put(DensityMapDispatcher.CONFIG_KEY, densityFunctionMap.get());
        modes.put(ClampDispatcher.CONFIG_KEY, densityFunctionClamp.get());
        modes.put(PackedBitsDispatcher.CONFIG_KEY, packedStorageUnpack.get());
        modes.put(CarverSkipDispatcher.CONFIG_KEY, canyonCarverSkip.get());
        return VectorXConfig.of(backendForcedScalar.get(), selfTest.get(), diagnostics.get(), modes);
    }
}
