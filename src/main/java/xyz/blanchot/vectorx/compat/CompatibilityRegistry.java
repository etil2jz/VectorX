package xyz.blanchot.vectorx.compat;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CompatibilityRegistry {

    private final Map<String, List<String>> knownConflicts = new HashMap<>();

    public CompatibilityRegistry() {
        knownConflicts.put("lithium", List.of("packedStorageUnpack"));
        knownConflicts.put("c2me-opts-dfc", List.of(
                "densityFunctionMap",
                "densityFunctionClamp",
                "densityFunctionMinMax",
                "densityFunctionSelect"));
    }

    public Optional<List<String>> conflictingKernels(String modId) {
        return Optional.ofNullable(knownConflicts.get(modId));
    }

    public Map<String, List<String>> knownConflicts() {
        return Collections.unmodifiableMap(knownConflicts);
    }
}
