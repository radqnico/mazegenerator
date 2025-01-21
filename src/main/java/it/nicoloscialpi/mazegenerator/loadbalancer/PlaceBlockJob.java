package it.nicoloscialpi.mazegenerator.loadbalancer;

import com.jeff_media.customblockdata.CustomBlockData;
import it.nicoloscialpi.mazegenerator.MazeGeneratorPlugin;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataType;

import java.io.Console;
import java.util.Objects;


public record PlaceBlockJob(int blockX, int blockY, int blockZ, Material material,
                            World world) implements LoadBalancerJob {

    @Override
    public void compute() {
        Block blockAt = world.getBlockAt(blockX, blockY, blockZ);
        blockAt.setType(material);
        CustomBlockData data = new CustomBlockData(blockAt, MazeGeneratorPlugin.plugin);
        data.set(new NamespacedKey("maze", "block"), PersistentDataType.STRING, "BLOCK");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlaceBlockJob that = (PlaceBlockJob) o;
        return blockX == that.blockX && blockY == that.blockY && blockZ == that.blockZ && material == that.material && Objects.equals(world, that.world);
    }

}
