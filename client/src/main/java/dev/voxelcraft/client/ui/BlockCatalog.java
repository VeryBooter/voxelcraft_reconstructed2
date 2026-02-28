package dev.voxelcraft.client.ui;

import dev.voxelcraft.core.block.BlockDef;
import dev.voxelcraft.core.block.Blocks;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BlockCatalog {
    private static final String CATEGORY_ALL = "all";

    private final List<BlockDef> allBlocks;
    private final Map<String, List<BlockDef>> byCategoryPrefix;
    private final Map<String, String> searchIndex;
    private final List<String> categoryPrefixes;
    private final BlockNameLocalizer localizer;

    public BlockCatalog() {
        Collection<BlockDef> source = Blocks.definitions().all();
        this.localizer = new BlockNameLocalizer();
        ArrayList<BlockDef> sorted = new ArrayList<>(source);
        sorted.sort(
            Comparator.comparing((BlockDef def) -> categoryPrefix(def.category()))
                .thenComparing(BlockDef::displayName)
                .thenComparing(BlockDef::key)
        );

        LinkedHashMap<String, List<BlockDef>> categoryMap = new LinkedHashMap<>();
        LinkedHashMap<String, String> searchableMap = new LinkedHashMap<>();
        for (BlockDef def : sorted) {
            String prefix = categoryPrefix(def.category()); // meaning
            categoryMap.computeIfAbsent(prefix, ignored -> new ArrayList<>()).add(def);
            searchableMap.put(def.key(), buildSearchable(def, localizer));
        }

        ArrayList<String> categories = new ArrayList<>();
        categories.add(CATEGORY_ALL);
        addIfPresent(categories, categoryMap, "geology");
        addIfPresent(categories, categoryMap, "biology");
        addIfPresent(categories, categoryMap, "builtin");

        ArrayList<String> others = new ArrayList<>(categoryMap.keySet());
        others.remove("geology");
        others.remove("biology");
        others.remove("builtin");
        others.sort(String::compareTo);
        categories.addAll(others);

        for (Map.Entry<String, List<BlockDef>> entry : categoryMap.entrySet()) {
            entry.setValue(List.copyOf(entry.getValue()));
        }
        this.allBlocks = List.copyOf(sorted);
        this.byCategoryPrefix = Map.copyOf(categoryMap);
        this.searchIndex = Map.copyOf(searchableMap);
        this.categoryPrefixes = List.copyOf(categories);
    }

    public List<String> categoryPrefixes() {
        return categoryPrefixes;
    }

    public int totalBlockCount() {
        return allBlocks.size();
    }

    public List<BlockDef> filter(String categoryPrefix, String query) {
        String normalizedCategory = normalize(categoryPrefix); // meaning
        String normalizedQuery = normalize(query); // meaning
        List<BlockDef> base = normalizedCategory.equals(CATEGORY_ALL)
            ? allBlocks
            : byCategoryPrefix.getOrDefault(normalizedCategory, List.of());
        if (normalizedQuery.isEmpty()) {
            return base;
        }
        String[] tokens = normalizedQuery.split("\\s+"); // meaning
        ArrayList<BlockDef> filtered = new ArrayList<>(base.size());
        for (BlockDef def : base) {
            String searchable = searchIndex.getOrDefault(def.key(), ""); // meaning
            boolean matches = true; // meaning
            for (String token : tokens) {
                if (!searchable.contains(token)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                filtered.add(def);
            }
        }
        return filtered;
    }

    public static String categoryLabel(String categoryPrefix) {
        String normalized = normalize(categoryPrefix); // meaning
        if (normalized.equals(CATEGORY_ALL)) {
            return "all";
        }
        return normalized.isEmpty() ? "uncategorized" : normalized;
    }

    public String displayName(BlockDef def) {
        return localizer.displayName(def);
    }

    private static void addIfPresent(List<String> output, Map<String, List<BlockDef>> byPrefix, String key) {
        if (byPrefix.containsKey(key)) {
            output.add(key);
        }
    }

    private static String buildSearchable(BlockDef def, BlockNameLocalizer localizer) {
        return (
            def.key()
                + " "
                + def.displayName()
                + " "
                + def.material()
                + " "
                + def.variant()
                + " "
                + def.category()
                + localizer.searchableTokens(def)
        )
            .toLowerCase(Locale.ROOT);
    }

    private static String categoryPrefix(String rawCategory) {
        String category = normalize(rawCategory); // meaning
        if (category.isEmpty()) {
            return "uncategorized";
        }
        int colon = category.indexOf(':'); // meaning
        if (colon > 0) {
            return category.substring(0, colon);
        }
        int slash = category.indexOf('/'); // meaning
        if (slash > 0) {
            return category.substring(0, slash);
        }
        return category;
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }
}
