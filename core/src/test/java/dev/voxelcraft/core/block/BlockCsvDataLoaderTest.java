package dev.voxelcraft.core.block;

import dev.voxelcraft.core.block.data.BlockCsvDataLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class BlockCsvDataLoaderTest {
    @Test
    void loadsExternalLibraryWhenCsvIsPresent() throws IOException {
        Path blocks = Path.of("/Users/kevinli/Downloads/DownloadFromInternet/AtlasDownloadSection/blocks_mesh_library_v2_ecohardcore.csv");
        Path meshProfiles = Path.of("/Users/kevinli/Downloads/DownloadFromInternet/AtlasDownloadSection/mesh_profiles.csv");
        Path growthRules = Path.of("/Users/kevinli/Downloads/DownloadFromInternet/AtlasDownloadSection/growth_rules.csv");
        Assumptions.assumeTrue(Files.isRegularFile(blocks));
        Assumptions.assumeTrue(Files.isRegularFile(meshProfiles));
        Assumptions.assumeTrue(Files.isRegularFile(growthRules));

        BlockCsvDataLoader.LoadResult result = BlockCsvDataLoader.load(blocks, meshProfiles, growthRules, 7);
        Assertions.assertTrue(result.blockDefs().size() >= 3000);
        Assertions.assertTrue(result.growthSchemas().containsKey("NONE"));
    }

    @Test
    void invalidGrowthParamsFallBackToNone() throws IOException {
        Path dir = Files.createTempDirectory("vc-block-csv-test");
        Path meshProfiles = dir.resolve("mesh_profiles.csv");
        Path growthRules = dir.resolve("growth_rules.csv");
        Path blocks = dir.resolve("blocks.csv");

        Files.write(meshProfiles, List.of(
            "mesh_profile,default_alpha_mode,default_occlusion_mode,collision_kind,aabb_min,aabb_max,attach_faces,requires_support,double_sided,notes",
            "CUBE,OPAQUE,full,full,0_0_0,1_1_1,,false,false,"
        ));
        Files.write(growthRules, List.of(
            "growth_rule_id,description,params_schema_json",
            "SPREAD_MOSS_HUMID,desc,{}",
            "NONE,none,{}"
        ));
        Files.write(blocks, List.of(
            "block_id,display_name,category,material,variant,shape,render_bucket,alpha_mode,needs_sorting,occludes,occlusion_mode,mesh_profile,collision_kind,aabb_min,aabb_max,attach_faces,requires_support,double_sided,tex_layers,state_params,hardness_class,sound_class,flammable,requires_water,tint_mode,growth_rule_id,growth_params_json,mesh_notes,tags",
            "test_block,test,cat,stone,v,block,OPAQUE,OPAQUE,false,True,full,CUBE,full,0_0_0,1_1_1,,false,false,,,hard,stone,false,false,,SPREAD_MOSS_HUMID,{bad-json},,"
        ));

        BlockCsvDataLoader.LoadResult result = BlockCsvDataLoader.load(blocks, meshProfiles, growthRules, 0);
        Assertions.assertEquals(1, result.blockDefs().size());
        BlockDef def = result.blockDefs().get(0);
        Assertions.assertEquals("NONE", def.growthRuleId());
    }
}
