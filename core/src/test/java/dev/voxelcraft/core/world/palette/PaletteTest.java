package dev.voxelcraft.core.world.palette;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PaletteTest {
    @Test
    void paletteReusesIdsForSameValue() {
        Palette<String> palette = new Palette<>();

        int first = palette.idFor("stone");
        int second = palette.idFor("stone");

        Assertions.assertEquals(first, second);
        Assertions.assertEquals(1, palette.size());
    }

    @Test
    void paletteResolvesValuesById() {
        Palette<String> palette = new Palette<>();
        palette.idFor("stone");
        int dirtId = palette.idFor("dirt");

        Assertions.assertEquals("dirt", palette.valueFor(dirtId));
    }

    @Test
    void invalidIdThrows() {
        Palette<String> palette = new Palette<>();
        palette.idFor("stone");

        Assertions.assertThrows(IllegalArgumentException.class, () -> palette.valueFor(10));
    }
}
