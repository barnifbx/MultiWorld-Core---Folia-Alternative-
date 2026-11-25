package net.flowsmp.multiworld.world;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/** Empty world with a small stone platform at 0 / 64 / 0. */
public final class VoidGenerator extends ChunkGenerator {
    @Override
    public void generateSurface(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        if (chunkX != 0 || chunkZ != 0) return;
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) data.setBlock(x, 63, z, Material.STONE);
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 1.5, 64, 1.5);
    }

    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }
}
