package xyz.blanchot.vectorx.compat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class CompatibilityRegistry {

    private final Map<String, String> knownConflicts = new HashMap<>();

    public CompatibilityRegistry() {
        knownConflicts.put("lithium", "packedStorageUnpack");
    }

    public Optional<String> conflictingKernel(String modId) {
        return Optional.ofNullable(knownConflicts.get(modId));
    }

    public Map<String, String> knownConflicts() {
        return Collections.unmodifiableMap(knownConflicts);
    }
}
