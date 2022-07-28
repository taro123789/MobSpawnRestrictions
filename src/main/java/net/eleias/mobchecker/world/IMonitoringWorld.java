package net.eleias.mobchecker.world;

import org.bukkit.World;

public interface IMonitoringWorld {
    World getWorld();
    WorldEntitiesInfo getEntitiesInfo();
    void removeMobs(int removes);
    void removeAllMobs();
    void startMobSpawning();
    void stopMobSpawning();
    boolean isIgnored();
}
