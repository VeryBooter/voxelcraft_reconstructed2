package dev.voxelcraft.core.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemPropertiesTest {
    private static final String KEY = "vc.test.systemProperties";
    private static final String LEGACY_KEY = "voxelcraft.test.systemProperties";
    private String originalValue;
    private String originalLegacyValue;

    @BeforeEach
    void saveProperties() {
        originalValue = System.clearProperty(KEY);
        originalLegacyValue = System.clearProperty(LEGACY_KEY);
    }

    @AfterEach
    void restoreProperties() {
        restore(KEY, originalValue);
        restore(LEGACY_KEY, originalLegacyValue);
    }

    @Test
    void missingPropertiesUseCallerDefaults() {
        assertTrue(SystemProperties.getBoolean(KEY, LEGACY_KEY, true));
        assertFalse(SystemProperties.getBoolean(KEY, LEGACY_KEY, false));
        assertEquals(7, SystemProperties.getInt(KEY, LEGACY_KEY, 7));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "true", "yes", "on", " TRUE ", " YeS ", " ON "})
    void acceptsTrueValuesFromEitherKey(String raw) {
        System.setProperty(LEGACY_KEY, raw);
        assertTrue(SystemProperties.getBoolean(KEY, LEGACY_KEY, false));

        System.setProperty(KEY, raw);
        System.setProperty(LEGACY_KEY, "false");
        assertTrue(SystemProperties.getBoolean(KEY, LEGACY_KEY, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "false", "no", "off", " FALSE ", " No ", " OFF "})
    void acceptsFalseValuesFromEitherKey(String raw) {
        System.setProperty(LEGACY_KEY, raw);
        assertFalse(SystemProperties.getBoolean(KEY, LEGACY_KEY, true));

        System.setProperty(KEY, raw);
        System.setProperty(LEGACY_KEY, "true");
        assertFalse(SystemProperties.getBoolean(KEY, LEGACY_KEY, true));
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = {" ", "invalid", "2"})
    void invalidBooleanUsesDefaultWithoutFallingBackToLegacyKey(String raw) {
        System.setProperty(LEGACY_KEY, raw);
        assertTrue(SystemProperties.getBoolean(KEY, LEGACY_KEY, true));
        assertFalse(SystemProperties.getBoolean(KEY, LEGACY_KEY, false));

        System.setProperty(KEY, raw);
        System.setProperty(LEGACY_KEY, "true");
        assertFalse(SystemProperties.getBoolean(KEY, LEGACY_KEY, false));
        System.setProperty(LEGACY_KEY, "false");
        assertTrue(SystemProperties.getBoolean(KEY, LEGACY_KEY, true));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 42, -7, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void acceptsTrimmedIntegersFromEitherKey(int value) {
        String raw = " " + value + " ";
        System.setProperty(LEGACY_KEY, raw);
        assertEquals(value, SystemProperties.getInt(KEY, LEGACY_KEY, 99));

        System.setProperty(KEY, raw);
        System.setProperty(LEGACY_KEY, "100");
        assertEquals(value, SystemProperties.getInt(KEY, LEGACY_KEY, 99));
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = {" ", "invalid", "1.5", "2147483648", "-2147483649"})
    void invalidIntegerUsesDefaultWithoutFallingBackToLegacyKey(String raw) {
        System.setProperty(LEGACY_KEY, raw);
        assertEquals(7, SystemProperties.getInt(KEY, LEGACY_KEY, 7));

        System.setProperty(KEY, raw);
        System.setProperty(LEGACY_KEY, "42");
        assertEquals(7, SystemProperties.getInt(KEY, LEGACY_KEY, 7));
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
