<div align="center">

# VectorX

**Conservative optimizations using the Java Vector API to vectorize specific Minecraft hot paths, with a mandatory scalar fallback.**

[![Build](https://github.com/etil2jz/VectorX/actions/workflows/build.yml/badge.svg)](https://github.com/etil2jz/VectorX/actions/workflows/build.yml)
[![License: LGPL-3.0-only](https://img.shields.io/badge/license-LGPL--3.0--only-blue.svg)](LICENSE)
[![Modrinth](https://img.shields.io/badge/download-Modrinth-00AF5C)](https://modrinth.com/mod/vectorx)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-green)](https://fabricmc.net/)
[![Fabric](https://img.shields.io/badge/mod%20loader-Fabric-dbd0b4)](https://fabricmc.net/)

</div>

---

VectorX rewrites a small, carefully chosen set of Minecraft's hottest array loops using the
[Java Vector API](https://openjdk.org/jeps/508) (`jdk.incubator.vector`), so that a single CPU
instruction processes 4, 8 or 16 values at once instead of one — SSE, AVX2, AVX-512, or NEON,
whichever your CPU and JDK actually provide.

Unlike most SIMD experiments, **every vectorized kernel here ships with a scalar twin**, and the
mod will silently and permanently fall back to that twin the moment anything is off: no Vector API
module, an unexpected CPU, a failed startup correctness check, a conflicting mod, or an exception
at runtime. **Vanilla behaviour is the floor, never the risk.**

> VectorX is a **world generation and chunk I/O** optimization. It reduces CPU time on the server
> thread (and on singleplayer's integrated server); it is not an FPS/renderer mod, and it does not
> replace Lithium, Sodium or FerriteCore — it complements them.

## Features

VectorX ships **four kernels**, each independently toggleable and independently resolved at
startup:

| Kernel                 | Vanilla target                           | What it does                                                                                                                                                                                                                                                                                                |
|------------------------|------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `densityFunctionMap`   | `DensityFunctions$Mapped`                | Vectorizes the element-wise transforms used during noise/terrain generation. Only `half_negative`, `quarter_negative` and `squeeze` are routed to SIMD — the remaining ops (`abs`, `square`, `cube`, `invert`) are already branch-free and measured no real gain, so they are deliberately left on vanilla. |
| `densityFunctionClamp` | `DensityFunctions$Clamp`                 | Vectorizes the clamp applied over a whole density array, using lane-wise `min`/`max` instead of a per-element branch.                                                                                                                                                                                       |
| `packedStorageUnpack`  | `SimpleBitStorage` / `PalettedContainer` | Vectorizes both directions of palette bit-packing — `long[]` → `int[]` unpacking and `int[]` → `long[]` packing — hit on every chunk save and every chunk load. Also covers `SimpleBitStorage.getAll(IntConsumer)`, reached from `PalettedContainer.count()` (the block/fluid count recompute done for every chunk section on chunk load) and `PalettedContainer.getAll(Consumer)` (e.g. the biome-decoration possible-biomes scan), by unpacking through the same kernel into a scratch array and dispatching from it in order.                                                                                                                                             |
| `canyonCarverSkip`     | `CanyonWorldCarver` (via `WorldCarver`)  | Vectorizes the inner `worldY` sweep of `WorldCarver.carveEllipsoid`'s ellipsoid-membership test, for canyon carving only — bulk-computes the `>= 1.0` skip decision for a whole vertical column instead of one `shouldSkip` call per block. `CaveWorldCarver` is deliberately not covered (its skip test closes over `floorLevel`, an RNG-derived local from two call frames up that can't be reached without either fragile reflection or reimplementing the RNG-heavy tunnel loop, which risks silently desyncing generated terrain from vanilla for a given seed — a correctness bug the fail-open below can't catch, since it wouldn't throw). The surrounding bounds/traversal logic is a faithful reimplementation of `carveEllipsoid` itself, kept in a plain, Mixin-free class (`CanyonCarveGeometry`) so it can be differentially tested against an independent transliteration of the real method, in-process, without a running game.                                                                                                                                             |

Both `DensityFunctions$Mapped` and `DensityFunctions$Clamp` inherit `fillArray` from
`PureTransformer` without overriding it, so VectorX supplies the override rather than replacing
any existing Mojang method body.

## Safety model

This is the part that matters more than the speedup.

1. **Startup differential self-test.** Before a vector backend is trusted, it is run side by side
   with its scalar twin over generated inputs (including edge cases and non-multiple-of-lane-width
   lengths). A single mismatch demotes that kernel to scalar for the rest of the session.
2. **Per-call fail-open.** Every Mixin wraps its dispatch in `try`/`catch (Throwable)`. On any
   failure it falls through to the exact vanilla code path — Mojang's own method body, or a
   per-element loop calling Mojang's own `transform(double)`.
3. **No hard Mixin requirements.** `vectorx.mixins.json` uses `"required": false` and
   `defaultRequire: 0`, so a Minecraft update or a conflicting mod that moves the targeted code
   makes VectorX inert, not crash-prone.
4. **No static reference to the incubator module.** Module detection goes through
   `VectorModuleProbe`, which never names a `jdk.incubator.vector` class statically. The mod loads
   and runs correctly on a JVM where the module is entirely absent.
5. **Informational compatibility registry, never automatic.** Known interactions with other mods
   are listed for diagnostics only; VectorX never silently disables another mod's behaviour.

Resolution order per kernel, evaluated once at startup (first match wins):

```
vectorized.forceScalar=true  →  scalar
config backendForcedScalar   →  scalar
jdk.incubator.vector absent  →  scalar
SIMD class fails to link     →  scalar
config mode = scalar / off   →  scalar
self-test fails              →  scalar
otherwise                    →  vector
```

## Requirements

|              |                                                             |
|--------------|-------------------------------------------------------------|
| Minecraft    | 26.2                                                        |
| Mod loader   | Fabric Loader ≥ 0.19.3                                      |
| Java         | **25 or newer**                                             |
| Dependencies | [Fzzy Config](https://modrinth.com/mod/fzzy-config) ≥ 0.7.6 |
| Side         | Client and server (`environment: *`)                        |

Fabric API is **not** required.

### ⚠️ Required JVM flag

The Vector API is still an **incubating** module, so the JVM will not expose it unless you ask:

```
--add-modules=jdk.incubator.vector
```

Add it to your JVM arguments (launcher profile, `user_jvm_args.txt`, or your server start script).
**Without this flag VectorX still loads and works perfectly — it just runs every kernel on the
scalar backend and gives you no speedup at all.** The startup log tells you which one you got:

```
[vectorx] VectorX ready: 4/4 kernels on the vector backend
```

If that line says `0/4`, the flag is missing. Enable `diagnostics` in the config for the full
report, including the exact reason each kernel fell back and the vector width actually selected.

## Configuration

VectorX uses [Fzzy Config](https://modrinth.com/mod/fzzy-config): edit the generated TOML file
under `config/`, or open the settings screen in-game via
[Mod Menu](https://modrinth.com/mod/modmenu).

| Option                 | Default | Effect                                                                                                                                |
|------------------------|---------|---------------------------------------------------------------------------------------------------------------------------------------|
| `backendForcedScalar`  | `false` | Forces every kernel to scalar, regardless of the settings below.                                                                      |
| `densityFunctionMap`   | `auto`  | `auto` = vector if it loads and passes its self-test, scalar otherwise · `scalar` = force scalar · `off` = disable the hook entirely. |
| `densityFunctionClamp` | `auto`  | Same `auto` / `scalar` / `off` semantics.                                                                                             |
| `packedStorageUnpack`  | `auto`  | Same `auto` / `scalar` / `off` semantics.                                                                                             |
| `canyonCarverSkip`     | `auto`  | Same `auto` / `scalar` / `off` semantics.                                                                                             |
| `selfTest`             | `true`  | Runs the scalar-vs-vector differential check at startup before trusting a vector backend. Leave this on.                              |
| `diagnostics`          | `false` | Logs a full startup report: module resolution, per-kernel backend, fallback reason, vector species, and known mod interactions.       |

Every option except `diagnostics` requires a **restart**: backends are resolved once during
`onInitialize()` and cached in static fields.

The config is deliberately **not** synced from server to client — it is a per-machine performance
setting, not gameplay data.

### System property

```
-Dvectorized.forceScalar=true
```

Overrides everything and pins all kernels to scalar. Useful for A/B testing, or as a
zero-config kill switch when you can't reach the config file (e.g. on a hosted server panel).

## Compatibility

| Mod                                        | Status                                                                                                                                                                                                                                                              |
|--------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Lithium**                                | Compatible. Lithium `@Overwrite`s `PalettedContainer.pack()` with a fused implementation that never calls `SimpleBitStorage.unpack()`, so VectorX's hook is simply **inert** there — not broken. The chunk-*load* path is untouched by Lithium and always benefits. Lithium's `mixin.util.block_tracking` (on by default) only `@ModifyArg`/`@Inject`s the `CountConsumer` passed into `PalettedContainer.count()` and its own `accept()` callback — it never touches `SimpleBitStorage`, so VectorX's `getAll` hook has no conflict either. Lithium's mixin source tree also contains no path or class touching `WorldCarver`/`CanyonWorldCarver` — it doesn't modify world carving at all, so `canyonCarverSkip` has no conflict. |
| **Sodium / Iris / rendering mods**         | Unaffected — VectorX touches no rendering code.                                                                                                                                                                                                                     |
| **FerriteCore, Krypton, ModernFix, C2ME…** | No known interaction.                                                                                                                                                                                                                                               |

Absence from the registry means *"no known conflict"*, not *"verified compatible"*. No entry is
added without reading the other mod's actual source to confirm it transforms the exact
class/method a kernel hooks — never from documentation alone.

If you find a conflict, please [open an issue](https://github.com/etil2jz/VectorX/issues) with
your `diagnostics: true` startup block attached.

## Building from source

```bash
git clone https://github.com/etil2jz/VectorX.git
cd VectorX
./gradlew build
```

The jar lands in `build/libs/`. JDK 25 is required.

```bash
./gradlew test      # unit + differential + child-JVM scalar-path tests
./gradlew jmhRun    # JMH microbenchmarks (adds --add-modules automatically)
./gradlew runServer # dev server, Vector API module pre-enabled
```

### Project layout

```
kernel/          interfaces + scalar/ and simd/ implementations of each kernel
dispatch/        fail-open backend resolution and the jdk.incubator.vector probe
selftest/        scalar-vs-vector differential checks run at startup
mixin/           the four Minecraft hooks, each with a vanilla fallback
config/          Fzzy Config adapter (the only class aware of TOML/GUI)
diag/            one-line summary + optional full startup report
compat/          informational registry of known mod interactions
```

The architecture keeps Minecraft, Fabric and Fzzy Config out of the kernels entirely:
`VectorXConfig` is a plain immutable snapshot, so dispatchers, kernels and their tests all run in
a bare JVM.

## License

[LGPL-3.0-only](LICENSE).

## Credits

Created by [etil2jz](https://github.com/etil2jz). Built with
[Fabric Loom](https://github.com/FabricMC/fabric-loom), configured with
[Fzzy Config](https://github.com/fzzyhmstrs/fzzy_config) by fzzyhmstrs.
