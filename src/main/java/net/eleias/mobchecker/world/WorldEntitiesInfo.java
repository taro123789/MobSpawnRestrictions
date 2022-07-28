package net.eleias.mobchecker.world;

import java.util.EnumMap;
import java.util.Map;

import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;

public class WorldEntitiesInfo {
    private int _entityCount;
    private int _mobCount         = 0;
    private int _friendlyMobCount = 0;
    private int _monsterMobCount  = 0;

    private Map<EntityType, Integer> _mobs = new EnumMap<>(EntityType.class);

    WorldEntitiesInfo(World world) {
        _entityCount = world.getEntityCount();
        for (var mob : world.getEntitiesByClass(Mob.class)) {
            _mobs.put(mob.getType(), _mobs.getOrDefault(mob.getType(), 0) + 1);

            _mobCount++;
            if (mob instanceof Monster) {
                _monsterMobCount++;
            } else {
                _friendlyMobCount++;
            }
        }
    }

    public int entityCount() {
        return _entityCount;
    }

    public int mobCount() {
        return _mobCount;
    }

    public int friendlyMobCount() {
        return _friendlyMobCount;
    }

    public int monsterMobCount() {
        return _monsterMobCount;
    }

    public Map<EntityType, Integer> mobs() {
        return _mobs;
    }
}
