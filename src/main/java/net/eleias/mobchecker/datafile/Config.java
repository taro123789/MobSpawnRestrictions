package net.eleias.mobchecker.datafile;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class Config {
    public static final String PATH_IGNORE_WORLDS = "ignore-worlds";

    private FileConfiguration _fc;

    private Supplier<FileConfiguration> _loadSupplier;
    private Runnable _saveRunnable;

    private SpawnLimitByMobsPerWorld _spawnLimitByMobsPerWorld;
    private SpawnLimitByTPS _spawnLimitByTPS;
    private AnnounceMessages _announceMessages;
    private GameruleChecker _gamemodeChecker;

    public Config(Supplier<FileConfiguration> loadSupplier, Runnable saveRunnable) {
        _loadSupplier = loadSupplier;
        _saveRunnable = saveRunnable;
    }

    public void load() {
        _fc = _loadSupplier.get();
        setIfNotContains(_fc, PATH_IGNORE_WORLDS);
        _spawnLimitByMobsPerWorld = new SpawnLimitByMobsPerWorld();
        _spawnLimitByTPS = new SpawnLimitByTPS();
        _announceMessages = new AnnounceMessages();
        _gamemodeChecker = new GameruleChecker();
    }

    public void save() {
        _saveRunnable.run();
    }

    public List<String> ignoreWorlds() {
        return _fc.getStringList(PATH_IGNORE_WORLDS);
    }

    public void ignoreWorlds(List<String> list) {
        _fc.set(PATH_IGNORE_WORLDS, list);
    }

    public SpawnLimitByMobsPerWorld spawnLimitByMobsPerWorld() {
        return _spawnLimitByMobsPerWorld;
    }

    public SpawnLimitByTPS spawnLimitByTPS() {
        return _spawnLimitByTPS;
    }

    public AnnounceMessages announceMessages() {
        return _announceMessages;
    }

    public GameruleChecker gamemodeChecker() {
        return _gamemodeChecker;
    }

    private void setIfNotContains(ConfigurationSection cs, String path) {
        if (!cs.getKeys(false).contains(path)) {
            cs.set(path, cs.get(path));
        }
    }

    public class SpawnLimitByMobsPerWorld {
        public static final String PATH_ENABLE = "enable";
        public static final String PATH_MOBS_UPPER_LIMIT = "mobs-upper-limit";
        public static final String PATH_CHECK_EXCEEDED_INTERVAL_TICK = "check-exceeded-interval-tick";

        private ConfigurationSection _cs = _fc.getConfigurationSection("spawn-limit-by-mobs-per-world");

        private SpawnLimitByMobsPerWorld() {
            setIfNotContains(_cs, PATH_ENABLE);
            setIfNotContains(_cs, PATH_MOBS_UPPER_LIMIT);
            setIfNotContains(_cs, PATH_CHECK_EXCEEDED_INTERVAL_TICK);
        }

        public boolean enable() {
            return _cs.getBoolean(PATH_ENABLE);
        }

        public void enable(boolean value) {
            _cs.set(PATH_ENABLE, value);
        }

        public int mobsUpperLimit() {
            return _cs.getInt(PATH_MOBS_UPPER_LIMIT);
        }

        public void mobsUpperLimit(int value) {
            _cs.set(PATH_MOBS_UPPER_LIMIT, value);
        }

        public int checkExceededIntervalTick() {
            return _cs.getInt(PATH_CHECK_EXCEEDED_INTERVAL_TICK);
        }

        public void checkExceededIntervalTick(int value) {
            _cs.set(PATH_CHECK_EXCEEDED_INTERVAL_TICK, value);
        }

        public Set<String> getKeys() {
            return _cs.getKeys(false);
        }
    }

    public class SpawnLimitByTPS {
        public static final String PATH_ENABLE = "enable";
        public static final String PATH_TPS_CRITERION = "tps-criterion";
        public static final String PATH_CHECK_BELOW_INTERVAL_TICK = "check-below-interval-tick";
        public static final String PATH_CHECK_RECOVERY_INTERVAL_TICK = "check-recovery-interval-tick";
        public static final String PATH_INVALID_CHECK_TICK_AFTER_RESTART = "invalid-check-tick-after-restart";
        public static final String PATH_REPEAT_COUNTER_LIMIT = "repeat-counter-limit";
        public static final String PATH_REPEAT_COUNTER_RESET_MINUTE = "repeat-counter-reset-minute";

        private ConfigurationSection _cs = _fc.getConfigurationSection("spawn-limit-by-tps");

        private SpawnLimitByTPS() {
            setIfNotContains(_cs, PATH_ENABLE);
            setIfNotContains(_cs, PATH_TPS_CRITERION);
            setIfNotContains(_cs, PATH_CHECK_BELOW_INTERVAL_TICK);
            setIfNotContains(_cs, PATH_CHECK_RECOVERY_INTERVAL_TICK);
            setIfNotContains(_cs, PATH_INVALID_CHECK_TICK_AFTER_RESTART);
            setIfNotContains(_cs, PATH_REPEAT_COUNTER_LIMIT);
            setIfNotContains(_cs, PATH_REPEAT_COUNTER_RESET_MINUTE);
        }

        public boolean enable() {
            return _cs.getBoolean(PATH_ENABLE);
        }

        public void enable(boolean value) {
            _cs.set(PATH_ENABLE, value);
        }

        public double tpsCriterion() {
            return _cs.getDouble(PATH_TPS_CRITERION);
        }

        public void tpsCriterion(double value) {
            _cs.set(PATH_TPS_CRITERION, value);
        }

        public int checkBelowIntervalTick() {
            return _cs.getInt(PATH_CHECK_BELOW_INTERVAL_TICK);
        }

        public void checkBelowIntervalTick(int value) {
            _cs.set(PATH_CHECK_BELOW_INTERVAL_TICK, value);
        }

        public int checkRecoveryIntervalTick() {
            return _cs.getInt(PATH_CHECK_RECOVERY_INTERVAL_TICK);
        }

        public void checkRecoveryIntervalTick(int value) {
            _cs.set(PATH_CHECK_RECOVERY_INTERVAL_TICK, value);
        }

        public int invalidCheckTickAfterRestart() {
            return _cs.getInt(PATH_INVALID_CHECK_TICK_AFTER_RESTART);
        }

        public void invalidCheckTickAfterRestart(int value) {
            _cs.set(PATH_INVALID_CHECK_TICK_AFTER_RESTART, value);
        }

        public int repeatCounterLimit() {
            return _cs.getInt(PATH_REPEAT_COUNTER_LIMIT);
        }

        public void repeatCounterLimit(int value) {
            _cs.set(PATH_REPEAT_COUNTER_LIMIT, value);
        }

        public int repeatCounterResetMinute() {
            return _cs.getInt(PATH_REPEAT_COUNTER_RESET_MINUTE);
        }

        public void repeatCounterResetMinute(int value) {
            _cs.set(PATH_REPEAT_COUNTER_RESET_MINUTE, value);
        }

        public Set<String> getKeys() {
            return _cs.getKeys(false);
        }
    }

    public class AnnounceMessages {
        public static final String PATH_SPAWN_TEMP_STOP = "spawn-temp-stop";
        public static final String PATH_SPAWN_PERM_STOP = "spawn-perm-stop";
        public static final String PATH_SPAWN_RESUME = "spawn-resume";

        private ConfigurationSection _cs = _fc.getConfigurationSection("announce-messages");

        private AnnounceMessages() {
            setIfNotContains(_cs, PATH_SPAWN_TEMP_STOP);
            setIfNotContains(_cs, PATH_SPAWN_PERM_STOP);
            setIfNotContains(_cs, PATH_SPAWN_RESUME);
        }

        public String spawnTempStop() {
            return _cs.getString(PATH_SPAWN_TEMP_STOP);
        }

        public void spawnTempStop(String value) {
            _cs.set(PATH_SPAWN_TEMP_STOP, value);
        }

        public String spawnPermStop() {
            return _cs.getString(PATH_SPAWN_PERM_STOP);
        }

        public void spawnPermStop(String value) {
            _cs.set(PATH_SPAWN_PERM_STOP, value);
        }

        public String spawnResume() {
            return _cs.getString(PATH_SPAWN_RESUME);
        }

        public void spawnResume(String value) {
            _cs.set(PATH_SPAWN_RESUME, value);
        }

        public Set<String> getKeys() {
            return _cs.getKeys(false);
        }
    }

    public class GameruleChecker {
        public static final String PATH_DO_MOB_SPAWNING = "do-mob-spawning";

        private ConfigurationSection _cs = _fc.getConfigurationSection("gamemode-checker");

        private DoMobSpawning _doMobSpawning;

        private GameruleChecker() {
            _doMobSpawning = new DoMobSpawning();
        }

        public DoMobSpawning doMobSpawning() {
            return _doMobSpawning;
        }

        public Set<String> getKeys() {
            return _cs.getKeys(false);
        }

        public class DoMobSpawning {
            public static final String PATH_ENABLE = "enable";
            public static final String PATH_VALUE = "value";
            public static final String PATH_CHECK_INTERVAL_TICK = "check-interval-tick";
            public static final String PATH_TARGET_WORLDS = "target-worlds";

            private ConfigurationSection _cs = GameruleChecker.this._cs.getConfigurationSection("do-mob-spawning");

            private DoMobSpawning() {
                setIfNotContains(_cs, PATH_ENABLE);
                setIfNotContains(_cs, PATH_VALUE);
                setIfNotContains(_cs, PATH_CHECK_INTERVAL_TICK);
                setIfNotContains(_cs, PATH_TARGET_WORLDS);
            }

            public boolean enable() {
                return _cs.getBoolean(PATH_ENABLE);
            }

            public void enable(boolean value) {
                _cs.set(PATH_ENABLE, value);
            }

            public boolean value() {
                return _cs.getBoolean(PATH_VALUE);
            }

            public void value(boolean value) {
                _cs.set(PATH_VALUE, value);
            }

            public int checkIntervalTick() {
                return _cs.getInt(PATH_CHECK_INTERVAL_TICK);
            }

            public void checkIntervalTick(int value) {
                _cs.set(PATH_CHECK_INTERVAL_TICK, value);
            }

            public List<String> targetWorlds() {
                return _cs.getStringList(PATH_TARGET_WORLDS);
            }

            public void targetWorlds(List<String> list) {
                _cs.set(PATH_TARGET_WORLDS, list);
            }

            public Set<String> getKeys() {
                return _cs.getKeys(false);
            }
        }
    }
}