package dev.voxelcraft.client.ui;

import dev.voxelcraft.core.block.BlockDef;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Optional block name localization for picker/hotbar display.
 */
public final class BlockNameLocalizer {
    private static final String DEFAULT_FILE = "blocks_mesh_library_v2_ecohardcore_zh_bi.csv";
    private static final String CLASSPATH_FILE = "/data/" + DEFAULT_FILE;

    private final Map<String, Names> namesByKey; // meaning
    private final Mode mode; // meaning

    public BlockNameLocalizer() {
        this.mode = parseMode(
            firstNonBlank(
                System.getProperty("vc.ui.blockNameMode"),
                System.getProperty("voxelcraft.ui.blockNameMode"),
                "bi"
            )
        );
        LoadResult loaded = loadNames();
        this.namesByKey = loaded.namesByKey();
        if (!namesByKey.isEmpty()) {
            System.out.printf(
                Locale.ROOT,
                "[block-i18n] loaded names=%d mode=%s source=%s%n",
                namesByKey.size(),
                mode.name().toLowerCase(Locale.ROOT),
                loaded.source()
            );
        }
    }

    public String displayName(BlockDef def) {
        if (def == null) {
            return "Block";
        }
        String fallback = nonBlankOrElse(def.displayName(), def.key()); // meaning
        Names names = namesByKey.get(normalizeKey(def.key())); // meaning
        if (names == null) {
            return fallback;
        }
        return switch (mode) {
            case EN -> fallback;
            case ZH -> nonBlankOrElse(names.zhName(), nonBlankOrElse(names.biName(), fallback));
            case BI -> nonBlankOrElse(names.biName(), nonBlankOrElse(names.zhName(), fallback));
        };
    }

    public String searchableTokens(BlockDef def) {
        if (def == null) {
            return "";
        }
        Names names = namesByKey.get(normalizeKey(def.key())); // meaning
        if (names == null) {
            return "";
        }
        return (" " + nullToEmpty(names.zhName()) + " " + nullToEmpty(names.biName())).toLowerCase(Locale.ROOT);
    }

    private LoadResult loadNames() {
        String configured = firstNonBlank(
            System.getProperty("vc.blocks.i18n.csv"),
            System.getProperty("voxelcraft.blocks.i18n.csv")
        );
        if (configured != null) {
            LoadResult configuredResult = loadConfigured(configured);
            if (configuredResult != null) {
                return configuredResult;
            }
        }

        LoadResult classpathResult = loadClasspath(CLASSPATH_FILE);
        if (classpathResult != null) {
            return classpathResult;
        }

        LoadResult fileResult = loadFile(Paths.get(DEFAULT_FILE));
        if (fileResult != null) {
            return fileResult;
        }
        fileResult = loadFile(Paths.get("data", DEFAULT_FILE));
        if (fileResult != null) {
            return fileResult;
        }
        return new LoadResult(Map.of(), "none");
    }

    private LoadResult loadConfigured(String configured) {
        String trimmed = configured.trim(); // meaning
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.regionMatches(true, 0, "classpath:", 0, "classpath:".length())) {
            String resource = trimmed.substring("classpath:".length()).trim(); // meaning
            if (resource.isEmpty()) {
                return null;
            }
            return loadClasspath(resource.startsWith("/") ? resource : "/" + resource);
        }
        return loadFile(Paths.get(trimmed));
    }

    private LoadResult loadClasspath(String resource) {
        String normalized = resource.startsWith("/") ? resource : "/" + resource; // meaning
        try (InputStream stream = BlockNameLocalizer.class.getResourceAsStream(normalized)) {
            if (stream == null) {
                return null;
            }
            return new LoadResult(parse(stream), "classpath:" + normalized);
        } catch (IOException exception) {
            return null;
        }
    }

    private LoadResult loadFile(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }
        try (InputStream stream = Files.newInputStream(path)) {
            return new LoadResult(parse(stream), path.toString());
        } catch (IOException exception) {
            return null;
        }
    }

    private static Map<String, Names> parse(InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                return Map.of();
            }

            List<String> headers = parseCsvLine(stripBom(headerLine)); // meaning
            int keyIndex = headers.indexOf("block_id"); // meaning
            int zhIndex = headers.indexOf("display_name_zh"); // meaning
            int biIndex = headers.indexOf("display_name_bi"); // meaning
            if (keyIndex < 0 || (zhIndex < 0 && biIndex < 0)) {
                return Map.of();
            }

            LinkedHashMap<String, Names> map = new LinkedHashMap<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> columns = parseCsvLine(line); // meaning
                if (keyIndex >= columns.size()) {
                    continue;
                }
                String key = normalizeKey(columns.get(keyIndex)); // meaning
                if (key.isEmpty()) {
                    continue;
                }
                String zhName = zhIndex >= 0 && zhIndex < columns.size() ? columns.get(zhIndex).trim() : ""; // meaning
                String biName = biIndex >= 0 && biIndex < columns.size() ? columns.get(biIndex).trim() : ""; // meaning
                if (zhName.isEmpty() && biName.isEmpty()) {
                    continue;
                }
                map.put(key, new Names(zhName, biName));
            }
            return Map.copyOf(map);
        }
    }

    private static List<String> parseCsvLine(String line) {
        ArrayList<String> values = new ArrayList<>(); // meaning
        StringBuilder current = new StringBuilder(); // meaning
        boolean inQuotes = false; // meaning
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }
            if (ch == ',' && !inQuotes) {
                values.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        values.add(current.toString().trim());
        return values;
    }

    private static String stripBom(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return value.charAt(0) == '\uFEFF' ? value.substring(1) : value;
    }

    private static Mode parseMode(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT); // meaning
        return switch (normalized) {
            case "zh", "cn", "zh_cn", "chinese" -> Mode.ZH;
            case "en", "english" -> Mode.EN;
            default -> Mode.BI;
        };
    }

    private static String normalizeKey(String key) {
        return BlockDef.normalizeKey(key);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String nonBlankOrElse(String value, String fallback) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        return fallback;
    }

    private record LoadResult(Map<String, Names> namesByKey, String source) {
    }

    private record Names(String zhName, String biName) {
    }

    private enum Mode {
        EN,
        ZH,
        BI
    }
}
