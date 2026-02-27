package dev.voxelcraft.core.block;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record GrowthRuleSchema(String id, Map<String, String> fieldTypes) {
    public GrowthRuleSchema {
        fieldTypes = Collections.unmodifiableMap(new LinkedHashMap<>(fieldTypes));
    }

    public Set<String> requiredFields() {
        return fieldTypes.keySet();
    }
}
