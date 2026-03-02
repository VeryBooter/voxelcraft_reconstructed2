package dev.voxelcraft.core.block;

// 中文标注：本文件已标记。

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BlockDefinitionRegistry {
    private final List<BlockDef> byId = new ArrayList<>();
    private final Map<String, BlockDef> byKey = new HashMap<>();

    public BlockDef register(BlockDef def) {
        String key = BlockDef.normalizeKey(def.key());
        if (byKey.putIfAbsent(key, def) != null) {
            throw new IllegalStateException("Duplicate block key: " + key);
        }
        int id = def.id().asUnsignedInt();
        ensureCapacity(id + 1);
        if (byId.get(id) != null) {
            throw new IllegalStateException("Duplicate block numeric id: " + id);
        }
        byId.set(id, def);
        return def;
    }

    public BlockDef byKey(String key) {
        return byKey.get(BlockDef.normalizeKey(key));
    }

    public BlockDef byId(int id) {
        if (id < 0 || id >= byId.size()) {
            return null;
        }
        return byId.get(id);
    }

    public int size() {
        return byKey.size();
    }

    public Collection<BlockDef> all() {
        return Collections.unmodifiableCollection(byKey.values());
    }

    private void ensureCapacity(int targetSize) {
        while (byId.size() < targetSize) {
            byId.add(null);
        }
    }
}
