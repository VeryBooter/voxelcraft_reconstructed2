package dev.voxelcraft.core.world.palette;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
/**
 * 中文说明：测试用例：用于验证 PaletteTest 相关行为与回归约束。
 */

// 中文标注（类）：`PaletteTest`，职责：用于测试与回归验证。
class PaletteTest {
    // 中文标注（方法）：`paletteReusesIdsForSameValue`，参数：无；用途：执行palette、reuses、ids、for、same、值相关逻辑。
    @Test
    void paletteReusesIdsForSameValue() {
        // 中文标注（局部变量）：`palette`，含义：用于表示palette。
        Palette<String> palette = new Palette<>(); // meaning

        // 中文标注（局部变量）：`first`，含义：用于表示first。
        int first = palette.idFor("stone"); // meaning
        // 中文标注（局部变量）：`second`，含义：用于表示second。
        int second = palette.idFor("stone"); // meaning

        Assertions.assertEquals(first, second);
        Assertions.assertEquals(1, palette.size());
    }

    // 中文标注（方法）：`paletteResolvesValuesById`，参数：无；用途：执行palette、resolves、values、by、标识相关逻辑。
    @Test
    void paletteResolvesValuesById() {
        // 中文标注（局部变量）：`palette`，含义：用于表示palette。
        Palette<String> palette = new Palette<>(); // meaning
        palette.idFor("stone");
        // 中文标注（局部变量）：`dirtId`，含义：用于表示泥土、标识。
        int dirtId = palette.idFor("dirt"); // meaning

        Assertions.assertEquals("dirt", palette.valueFor(dirtId));
    }

    // 中文标注（方法）：`invalidIdThrows`，参数：无；用途：执行invalid、标识、throws相关逻辑。
    @Test
    void invalidIdThrows() {
        // 中文标注（局部变量）：`palette`，含义：用于表示palette。
        Palette<String> palette = new Palette<>(); // meaning
        palette.idFor("stone");

        Assertions.assertThrows(IllegalArgumentException.class, () -> palette.valueFor(10));
    }
}
