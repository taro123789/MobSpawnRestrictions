package net.eleias.mobchecker.world;

import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Mob;

public class MonitoringWorld implements IMonitoringWorld {
    private World _world;
    private boolean _isIgnored;
    
    private boolean _isDoMobSpawningCheckTarget;

    public MonitoringWorld(World world, boolean isIgnored, boolean isDoMobSpawningCheckTarget) {
        _world = world;
        _isIgnored = isIgnored;

        _isDoMobSpawningCheckTarget = isDoMobSpawningCheckTarget;
    }

    @Override
    public World getWorld() {
        return _world;
    }

    @Override
    public WorldEntitiesInfo getEntitiesInfo() {
        return new WorldEntitiesInfo(_world);
    }

    @Override
    public void removeMobs(int removes) {
        if (_isIgnored) return;
        _world.getEntitiesByClass(Mob.class).stream().limit(removes).forEach(Mob::remove);
    }

    @Override
    public void removeAllMobs() {
        if (_isIgnored) return;
        _world.getEntitiesByClass(Mob.class).forEach(Mob::remove);
    }

    @Override
    public void startMobSpawning() {
        if (_isIgnored) return;
        _world.setGameRule(GameRule.DO_MOB_SPAWNING, true);
    }

    @Override
    public void stopMobSpawning() {
        if (_isIgnored) return;
        _world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
    }

    @Override
    public boolean isIgnored() {
        return _isIgnored;
    }

    @Override
    public <T> boolean isGameRuleCheckTarget(GameRule<T> gameRule) {
        if (gameRule == GameRule.DO_MOB_SPAWNING) {
            return _isDoMobSpawningCheckTarget;
        }
        return false;
    }
}
