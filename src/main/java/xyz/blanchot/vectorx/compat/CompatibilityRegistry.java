package xyz.blanchot.vectorx.compat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of known mod-id -&gt; conflicting-kernel mappings.
 *
 * <p>No entry may be added without first verifying, by reading that mod's
 * actual source, that it transforms the exact class/method a kernel's hook
 * touches -- never from documentation alone. This is purely informational
 * (feeds {@link xyz.blanchot.vectorx.diag.Diagnostics#fullReport}'s
 * "potential conflict" line) and never disables anything automatically: a
 * verified interaction here is not necessarily an unsafe one, and absence
 * from this map means "no known conflict", not "verified compatible".
 */
public final class CompatibilityRegistry {

    private final Map<String, String> knownConflicts = new HashMap<>();

    public CompatibilityRegistry() {
        // Lithium's PalettedContainerMixin fully @Overwrite's pack() with a
        // fused implementation that never calls SimpleBitStorage.unpack() or
        // its packing constructor, so this project's hooks there are simply
        // inert (not broken) when Lithium is loaded; the chunk-load path is
        // untouched by Lithium and always benefits either way.
        knownConflicts.put("lithium", "packedStorageUnpack");

        // Verified against Lithium's full mixin source tree (CaffeineMC/lithium,
        // develop branch; there was no 26.3 branch yet at the time of writing):
        // no mixin path or class touches WorldCarver, CanyonWorldCarver, or
        // carveEllipsoid/shouldSkip -- Lithium doesn't modify world carving at
        // all. Its chunk/serialization/SimpleBitStorageMixin only *adds* a
        // lithium$compact method and touches neither unpack nor getAll, so it
        // doesn't widen the packedStorageUnpack interaction above either. No
        // entry needed for canyonCarverSkip; kept here as a note, not a map
        // entry, since this registry only records verified conflicts/inertness,
        // not clean bills of health.
    }

    public Optional<String> conflictingKernel(String modId) {
        return Optional.ofNullable(knownConflicts.get(modId));
    }

    public Map<String, String> knownConflicts() {
        return Collections.unmodifiableMap(knownConflicts);
    }
}
