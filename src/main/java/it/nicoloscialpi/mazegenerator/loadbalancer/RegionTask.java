package it.nicoloscialpi.mazegenerator.loadbalancer;

import org.bukkit.World;

public interface RegionTask {
    World getRegionWorld();
    int getRegionChunkX();
    int getRegionChunkZ();
}

