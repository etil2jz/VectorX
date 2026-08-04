# Real-server benchmark

JMH numbers measure an isolated kernel on synthetic data. This is a
separate, real measurement: an actual dedicated server, real Mixins, real
world generation.

## Setup

- CPU: AMD Ryzen 7 9800X3D (Zen 5)
- JDK: Temurin 25.0.4
- `./gradlew runServer`, fresh world, fixed seed, RCON `forceload add` to
  force generation of 1024 real chunks (4 tiles of 256, chunk-aligned),
  same coordinates for both runs
- Metric: CPU-seconds consumed by the server process (Windows
  `Process.CPU`) from the moment the chunks were requested until it
  plateaus (generation done, confirmed by querying loaded blocks in the
  area via RCON)

## Vector width actually selected

```
kernel densityFunctionMap:   vector [species=Species[double, 8, S_512_BIT]]
kernel densityFunctionClamp: vector [species=Species[double, 8, S_512_BIT]]
kernel packedStorageUnpack:  vector [int=Species[int, 16, S_512_BIT], ...]
```

`S_512_BIT` everywhere → **AVX-512**, not AVX2, on this CPU/JDK combination.

## Result: 1024 real chunks, vector vs. forced scalar

| Backend                        | CPU-seconds |
|--------------------------------|-------------|
| Vector (AVX-512)               | 4.20        |
| Scalar (`"backend": "scalar"`) | 5.59        |

**~1.33x faster, ~25% less CPU time**, for identical real world generation.

Smaller-scale test (spawn-area prep only, ~121 chunks, 3 runs each side)
showed no clear difference — too short and too dominated by JVM
startup/JIT to isolate the gain. The 1024-chunk test above is the one
that's large enough to see it.

## Why 25%, not the 5-12x from JMH

Real chunk generation spends most of its CPU time on noise sampling,
feature/structure placement, and biome computation -- none of which this
mod vectorizes. The 25% is the vectorized kernels' actual share of that
total cost, measured in place, not extrapolated from the isolated numbers.
