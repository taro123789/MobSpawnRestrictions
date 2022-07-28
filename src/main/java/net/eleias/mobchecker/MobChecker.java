package net.eleias.mobchecker;

import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.eleias.mobchecker.datafile.Config;
import net.eleias.mobchecker.world.IMonitoringWorld;
import net.eleias.mobchecker.world.MonitoringWorld;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class MobChecker extends JavaPlugin {
    private final TextComponent LOG_PREFIX = Component.text()
                                     .append(Component.text("[", getTextColor(ChatColor.YELLOW)))
                                     .append(Component.text("モブチェッカー", getTextColor(ChatColor.GREEN)))
                                     .append(Component.text("] ", getTextColor(ChatColor.YELLOW)))
                                     .build();

    private final int HELP_SHOW_COMMANDS = 3;
    private final int INFO_WORLDS_LINES = 5;
    private final int INFO_MOBS_LINES = 10;

    private Config _config;
    private Map<String, IMonitoringWorld> _monitoringWorlds = new LinkedHashMap<>();
    
    private BukkitTask _mobCountCheckTask;
    private BukkitTask _tpsCheckTask = null;
    private LocalDateTime _latestStopByTPSsTime;
    private int _stopByTPSsCounter;

    private List<RegisteredCommand> _registeredCommands = List.of(
        new RegisteredCommand("/mobchecker help [page]", 
            Component.text("ヘルプを表示します。")
        ),
        new RegisteredCommand("/mobchecker reload",
            Component.text("設定をリロードし反映します。")
        ),
        new RegisteredCommand("/mobchecker info [page <page>]", 
            Component.text("現在の湧き量を表示します。")
        ),
        new RegisteredCommand("/mobchecker info world <worldname> [page]", 
            Component.text("指定したワールドにおける、現在の湧き量を表示します。")
        ),
        new RegisteredCommand("/mobchecker set world enable [value]", 
            Component.text("ワールドごとのMOB数上限の有効状態を表示・設定します。")
        ),
        new RegisteredCommand("/mobchecker set world mobs-upper-limit [value]", 
            Component.text("ワールドごとのMOB数上限の上限値を表示・設定します。")
        ),
        new RegisteredCommand("/mobchecker set world check-exceeded-interval-tick [value]", List.of(
            new TextComponent[]{Component.text("ワールドごとのMOB数が上限値を超えているかどうかをチェックするタスクの実行間隔を表示・設定します。")},
            new TextComponent[]{Component.text("設定時に数字のみを指定すると tick として指定しますが、数字の後に s / m / h をつけると、秒・分・時単位で設定することができます。", getTextColor(ChatColor.AQUA))}
        )),
        new RegisteredCommand("/mobchecker set tps enable [value]", 
            Component.text("TPSチェックの有効状態を表示・設定します。")
        ),
        new RegisteredCommand("/mobchecker set tps tps-criterion [value]", List.of(
            new TextComponent[]{Component.text("TPSをチェックする際の基準値を表示・設定します。")},
            new TextComponent[]{Component.text("（TPSがこの値を下回ったら制限モード、上回ったら通常モードになります。）")}
        )),
        new RegisteredCommand("/mobchecker set tps check-below-interval-tick [value]", List.of(
            new TextComponent[]{Component.text("TPSが基準値を下回ったかどうかをチェックするタスクの実行間隔を表示・設定します。")},
            new TextComponent[]{Component.text("設定時に数字のみを指定すると tick として指定しますが、数字の後に s / m / h をつけると、秒・分・時単位で設定することができます。", getTextColor(ChatColor.AQUA))}
        )),
        new RegisteredCommand("/mobchecker set tps check-recovery-interval-tick [value]", List.of(
            new TextComponent[]{Component.text("TPSが基準値を上回ったかどうかをチェックするタスクの実行間隔を表示・設定します。")},
            new TextComponent[]{Component.text("設定時に数字のみを指定すると tick として指定しますが、数字の後に s / m / h をつけると、秒・分・時単位で設定することができます。", getTextColor(ChatColor.AQUA))}
        )),
        new RegisteredCommand("/mobchecker set tps invalid-check-tick-after-restart [value]", List.of(
            new TextComponent[]{Component.text("TPSが基準値を上回った後に、再度下回った場合でも無視する時間を表示・設定します。")},
            new TextComponent[]{Component.text("設定時に数字のみを指定すると tick として指定しますが、数字の後に s / m / h をつけると、秒・分・時単位で設定することができます。", getTextColor(ChatColor.AQUA))}
        )),
        new RegisteredCommand("/mobchecker set tps repeat-counter-limit [value]", List.of(
            new TextComponent[]{Component.text("TPSが基準値を下回ったらカウントされるカウンターの上限を表示・設定します。")},
            new TextComponent[]{Component.text("カウンターがこの値を超えると、TPSが基準値を上回った場合でもMOBの再開を行わなくなります。", getTextColor(ChatColor.AQUA))}
        )),
        new RegisteredCommand("/mobchecker set tps repeat-counter-reset-minute [value]",
            Component.text("TPSが基準値を下回ったらカウントされるカウンターを 0 にリセットする分数を表示・設定します。")
        ),
        new RegisteredCommand("/mobchecker set announce-messages spawn-temp-stop [value]",
            Component.text("TPS低下による一時的なMOB湧き制限が行われた時のメッセージを表示・設定します。")
        ),
        new RegisteredCommand("/mobchecker set announce-messages spawn-perm-stop [value]",
            Component.text("慢性的なTPS低下による恒久的なMOB湧き制限が行われた時のメッセージを表示・設定します。")
        ),
        new RegisteredCommand("/mobchecker set announce-messages spawn-resume [value]",
            Component.text("TPS回復によるMOB湧き制限が解除された時のメッセージを表示・設定します。")
        ),
        new RegisteredCommand("/mobchecker ignore-worlds add <worldname>", 
            Component.text("指定したワールドを監視除外対象に追加します。")
        ),
        new RegisteredCommand("/mobchecker ignore-worlds remove <worldname>", 
            Component.text("指定したワールドを監視除外対象から削除します。")
        )
    );

    @Override
    public void onEnable() {
        saveDefaultConfig();
        _config = new Config(this::getConfig, this::saveConfig);
        _config.load();
        _config.save();
        init();
        getCommand("mobchecker").setExecutor(this);
        getCommand("mobchecker").setTabCompleter(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            // [ ]
            showHelp(sender, 1);
            return true;
        }

        switch (args[0]) {
            case "help":
                if (args.length == 1) {
                    // help
                    showHelp(sender, 1);
                    return true;
                } else if (args.length == 2) {
                    // help [page]
                    try {
                        var page = Integer.parseInt(args[1]);
                        if (page < 1) {
                            throw new NumberFormatException();
                        }
                        showHelp(sender, page);
                    } catch (NumberFormatException e) {
                        log(sender, Component.text("<page> には 1 以上の整数を指定してください。", TextColor.color(255, 0, 0)));
                    }
                    return true;
                }

            case "reload":
                // 再反映
                _config.load();
                init();
                log(sender, Component.text("設定を再反映しました。"));
                return true;

            case "info":
                // 新しく追加されたワールドがあればそのワールドの監視対象フラグは設定する
                // （reload ではないため、既存のワールドの監視対象フラグは書き換えない）
                checkNewLoadWorld();

                if (args.length == 1) {
                    // info
                    showInfo(sender, 1);
                    return true;
                } else if (args.length == 3 && args[1].equals("page")) {
                    // info [page <page>][]
                    try {
                        var page = Integer.parseInt(args[2]);
                        if (page < 1) {
                            throw new NumberFormatException();
                        }
                        showInfo(sender, page);
                    } catch (NumberFormatException e) {
                        log(sender, Component.text("<page> には 1 以上の整数を指定してください。", TextColor.color(255, 0, 0)));
                    }
                    return true;
                } else if (args.length == 3 && args[1].equals("world")) {
                    // info world <worldname>
                    showDetailInfo(sender, args[2], 1);
                    return true;
                } else if (args.length == 4 && args[1].equals("world")) {
                    // info world <worldname> <page>
                    try {
                        var page = Integer.parseInt(args[3]);
                        if (page < 1) {
                            throw new NumberFormatException();
                        }
                        showDetailInfo(sender, args[2], page);
                    } catch (NumberFormatException e) {
                        log(sender, Component.text("<page> には 1 以上の整数を指定してください。", TextColor.color(255, 0, 0)));
                    }
                    return true;
                }
                break;
            
            case "set":
                // 新しく追加されたワールドがあればそのワールドの監視対象フラグは設定する
                // （reload ではないため、既存のワールドの監視対象フラグは書き換えない）
                checkNewLoadWorld();

                if (args.length < 3 || 4 < args.length) break;
                if (args[1].equals("world")) {
                    switch (args[2]) {
                        case Config.SpawnLimitByMobsPerWorld.PATH_ENABLE:
                            // set world enable [value]
                            if (args.length == 3) {
                                log(sender, Component.text(MessageFormat.format(
                                    "ワールドごとのMOB数上限は {0} に設定されています。",
                                    _config.spawnLimitByMobsPerWorld().enable()
                                )));
                            } else {
                                var value = args[3].toLowerCase();
                                if (value.equals("true") || value.equals("false")) {
                                    _config.spawnLimitByMobsPerWorld().enable(Boolean.parseBoolean(value));
                                    _config.save();
                                    log(sender, Component.text(MessageFormat.format(
                                        "ワールドごとのMOB数上限を {0} に設定しました。", 
                                        value
                                    )));
                                } else {
                                    log(sender, Component.text("[value] には true または false を指定してください。", TextColor.color(255, 0, 0)));
                                }
                            }
                            return true;

                        case Config.SpawnLimitByMobsPerWorld.PATH_MOBS_UPPER_LIMIT:
                            // set world mobs-upper-limit [value]
                            if (args.length == 3) {
                                log(sender, Component.text(MessageFormat.format(
                                    "ワールドごとのMOB数上限の上限値は {0} に設定されています。",
                                    _config.spawnLimitByMobsPerWorld().mobsUpperLimit()
                                )));
                            } else {
                                try {
                                    var value = Integer.parseInt(args[3]);
                                    if (value < 0) {
                                        throw new NumberFormatException();
                                    }
                                    _config.spawnLimitByMobsPerWorld().mobsUpperLimit(value);
                                    _config.save();
                                    log(sender, Component.text(MessageFormat.format(
                                        "ワールドごとのMOB数上限の上限値を {0} に設定しました。", 
                                        value
                                    )));
                                } catch (NumberFormatException e) {
                                    log(sender, Component.text("[value] には 0 以上の数字を指定してください。", TextColor.color(255, 0, 0)));
                                }
                            }
                            return true;
                        
                        case Config.SpawnLimitByMobsPerWorld.PATH_CHECK_EXCEEDED_INTERVAL_TICK:
                            // set world check-exceeded-interval-tick [value]
                            if (args.length == 3) {
                                log(sender, Component.text(MessageFormat.format(
                                    "ワールドごとのMOB数が上限値を超えているかどうかのタスクは {0}tick ({1}) ごとに実行されています。",
                                    _config.spawnLimitByMobsPerWorld().checkExceededIntervalTick(),
                                    convertTickToTimeString(_config.spawnLimitByMobsPerWorld().checkExceededIntervalTick())
                                )));
                            } else {
                                try {
                                    var i = switch (args[3].charAt(args[3].length() - 1)) {
                                        case 's' -> Integer.parseInt(args[3].substring(0, args[3].length() - 1)) * 20;
                                        case 'm' -> Integer.parseInt(args[3].substring(0, args[3].length() - 1)) * 20 * 60;
                                        case 'h' -> Integer.parseInt(args[3].substring(0, args[3].length() - 1)) * 20 * 3600;
                                        case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> Integer.parseInt(args[3]);
                                        default -> throw new NumberFormatException();
                                    };
                                    if (i < 1) {
                                        throw new NumberFormatException();
                                    }
                                    _config.spawnLimitByMobsPerWorld().checkExceededIntervalTick(i);
                                    _config.save();
                                    log(sender, Component.text(MessageFormat.format(
                                        "ワールドごとのMOB数が上限値を超えているかどうかのタスクの実行間隔を {0}tick ({1}) ごとに設定しました。", 
                                        i, convertTickToTimeString(i)
                                    )));
                                } catch (NumberFormatException e) {
                                    log(sender, Component.text("[value] には 1 以上の整数（＋時間単位）を指定してください。", TextColor.color(255, 0, 0)));
                                }
                            }
                            return true;  
                    }
                } else if (args[1].equals("tps")) {
                    switch (args[2]) {
                        case Config.SpawnLimitByTPS.PATH_ENABLE -> {
                            // set tps enable [value]
                            if (args.length == 3) {
                                log(sender, Component.text(MessageFormat.format(
                                    "TPSチェックは {0} に設定されています。",
                                    _config.spawnLimitByTPS().enable()
                                )));
                            } else {
                                var value = args[3].toLowerCase();
                                if (value.equals("true") || value.equals("false")) {
                                    _config.spawnLimitByTPS().enable(Boolean.parseBoolean(value));
                                    _config.save();
                                    log(sender, Component.text(MessageFormat.format(
                                        "TPSチェックを {0} に設定しました。",
                                        value
                                    )));
                                } else {
                                    log(sender, Component.text("[value] には true または false を指定してください。", TextColor.color(255, 0, 0)));
                                }
                            }
                            return true;
                        }
                        
                        case Config.SpawnLimitByTPS.PATH_TPS_CRITERION -> {
                            // set tps tps-criterion [value]
                            if (args.length == 3) {
                                log(sender, Component.text(MessageFormat.format(
                                    "TPSチェックの基準値は {0} に設定されています。",
                                    _config.spawnLimitByTPS().tpsCriterion()
                                )));
                            } else {
                                try {
                                    var value = Double.parseDouble(args[3]);
                                    if (value < 0.1) {
                                        throw new NumberFormatException();
                                    }
                                    _config.spawnLimitByTPS().tpsCriterion(value);
                                    _config.save();
                                    log(sender, Component.text(MessageFormat.format(
                                        "TPSチェックの基準値を {0} に設定しました。",
                                        value
                                    )));
                                } catch (NumberFormatException e) {
                                    log(sender, Component.text("[value] には 0.1 以上の小数を指定してください。", TextColor.color(255, 0, 0)));
                                }
                            }
                            return true;
                        }
                        
                        case Config.SpawnLimitByTPS.PATH_CHECK_BELOW_INTERVAL_TICK -> {
                            // set tps check-below-interval [value]
                            if (args.length == 3) {
                                log(sender, Component.text(MessageFormat.format(
                                    "TPSが基準値を下回ったかどうかをチェックするタスクは {0}Tick ({1}) ごとに実行されています。",
                                    _config.spawnLimitByTPS().checkBelowIntervalTick(),
                                    convertTickToTimeString(_config.spawnLimitByTPS().checkBelowIntervalTick())
                                )));
                            } else {
                                try {
                                    var i = switch (args[3].charAt(args[3].length() - 1)) {
                                        case 's' -> Integer.parseInt(args[3].substring(0, args[3].length() - 1)) * 20;
                                        case 'm' -> Integer.parseInt(args[3].substring(0, args[3].length() - 1)) * 20 * 60;
                                        case 'h' -> Integer.parseInt(args[3].substring(0, args[3].length() - 1)) * 20 * 3600;
                                        case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> Integer.parseInt(args[3]);
                                        default -> throw new NumberFormatException();
                                    };
                                    if (i < 1) {
                                        throw new NumberFormatException();
                                    }
                                    _config.spawnLimitByTPS().checkBelowIntervalTick(i);
                                    _config.save();
                                    log(sender, Component.text(MessageFormat.format(
                                        "TPSが基準値を下回ったかどうかをチェックするタスクの実行間隔を {0}Tick ({1}) に設定しました。", 
                                        i, convertTickToTimeString(i)
                                    )));
                                } catch (NumberFormatException e) {
                                    log(sender, Component.text("[value] には 1 以上の整数（＋時間単位）を指定してください。", TextColor.color(255, 0, 0)));
                                }
                            }
                            return true;
                        }

                        case Config.SpawnLimitByTPS.PATH_CHECK_RECOVERY_INTERVAL_TICK -> {
                            // set tps check-recovery-interval [value]
                            if (args.length == 3) {
                                log(sender, Component.text(MessageFormat.format(
                                    "TPSが基準値を上回ったかどうかをチェックするタスクは {0}Tick ({1}) ごとに実行されています。",
                                    _config.spawnLimitByTPS().checkRecoveryIntervalTick(),
                                    convertTickToTimeString(_config.spawnLimitByTPS().checkRecoveryIntervalTick())
                                )));
                            } else {
                                try {
                                    var i = switch (args[3].charAt(args[3].length() - 1)) {
                                        case 's' -> Integer.parseInt(args[3].substring(0, args[3].length() - 1)) * 20;
                                        case 'm' -> Integer.parseInt(args[3].substring(0, args[3].length() - 1)) * 20 * 60;
                                        case 'h' -> Integer.parseInt(args[3].substring(0, args[3].length() - 1)) * 20 * 3600;
                                        case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> Integer.parseInt(args[3]);
                                        default -> throw new NumberFormatException();
                                    };
                                    if (i < 1) {
                                        throw new NumberFormatException();
                                    }
                                    _config.spawnLimitByTPS().checkRecoveryIntervalTick(i);
                                    _config.save();
                                    log(sender, Component.text(MessageFormat.format(
                                        "TPSが基準値を上回ったかどうかをチェックするタスクの実行間隔を {0}Tick ({1}) に設定しました。", 
                                        i, convertTickToTimeString(i)
                                    )));
                                } catch (NumberFormatException e) {
                                    log(sender, Component.text("[value] には 1 以上の整数（＋時間単位）を指定してください。", TextColor.color(255, 0, 0)));
                                }
                            }
                            return true;
                        }
                        
                        case Config.SpawnLimitByTPS.PATH_INVALID_CHECK_TICK_AFTER_RESTART -> {
                            // set tps invalid-check-after-restart [value]
                            if (args.length == 3) {
                                log(sender, Component.text(MessageFormat.format(
                                    "TPSが基準値を上回った後に、再度下回った場合でも無視する時間は {0}Tick ({1}) に設定されています。",
                                    _config.spawnLimitByTPS().invalidCheckTickAfterRestart(),
                                    convertTickToTimeString(_config.spawnLimitByTPS().invalidCheckTickAfterRestart())
                                )));
                            } else {
                                try {
                                    var i = switch (args[3].charAt(args[3].length() - 1)) {
                                        case 's' -> Integer.parseInt(args[3].substring(0, args[3].length() - 1)) * 20;
                                        case 'm' -> Integer.parseInt(args[3].substring(0, args[3].length() - 1)) * 20 * 60;
                                        case 'h' -> Integer.parseInt(args[3].substring(0, args[3].length() - 1)) * 20 * 3600;
                                        case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> Integer.parseInt(args[3]);
                                        default -> throw new NumberFormatException();
                                    };
                                    if (i < 1) {
                                        throw new NumberFormatException();
                                    }
                                    _config.spawnLimitByTPS().invalidCheckTickAfterRestart(i);
                                    _config.save();
                                    log(sender, Component.text(MessageFormat.format(
                                        "TPSが基準値を上回った後に、再度下回った場合でも無視する時間を {0}Tick ({1}) に設定しました。", 
                                        i, convertTickToTimeString(i)
                                    )));
                                } catch (NumberFormatException e) {
                                    log(sender, Component.text("[value] には 1 以上の整数（＋時間単位）を指定してください。", TextColor.color(255, 0, 0)));
                                }
                            }
                            return true;
                        }
                        
                        case Config.SpawnLimitByTPS.PATH_REPEAT_COUNTER_LIMIT -> {
                            // set tps repeat-counter-limit
                            if (args.length == 3) {
                                log(sender, Component.text(MessageFormat.format(
                                    "TPSが基準値を下回ったらカウントされるカウンターの上限値は {0} に設定されています。",
                                    _config.spawnLimitByTPS().repeatCounterLimit()
                                )));
                            } else {
                                try {
                                    var value = Integer.parseInt(args[3]);
                                    if (value < 0) {
                                        throw new NumberFormatException();
                                    }
                                    _config.spawnLimitByTPS().repeatCounterLimit(value);
                                    _config.save();
                                    log(sender, Component.text(MessageFormat.format(
                                        "TPSが基準値を下回ったらカウントされるカウンターの上限値を {0} に設定しました。", 
                                        value
                                    )));
                                } catch (NumberFormatException e) {
                                    log(sender, Component.text("[value] には 0 以上の整数を指定してください。", TextColor.color(255, 0, 0)));
                                }
                            }
                            return true;
                        }

                        case Config.SpawnLimitByTPS.PATH_REPEAT_COUNTER_RESET_MINUTE -> {
                            // set tps repeat-counter-reset-minute [value]
                            if (args.length == 3) {
                                log(sender, Component.text(MessageFormat.format(
                                    "TPSが基準値を下回ったらカウントされるカウンターがリセットされる時間は {0}分 に設定されています。",
                                    _config.spawnLimitByTPS().repeatCounterResetMinute()
                                )));
                            } else {
                                try {
                                    var value = Integer.parseInt(args[3]);
                                    if (value < 1) {
                                        throw new NumberFormatException();
                                    }
                                    _config.spawnLimitByTPS().repeatCounterLimit(value);
                                    _config.save();
                                    log(sender, Component.text(MessageFormat.format(
                                        "TPSが基準値を下回ったらカウントされるカウンターがリセットされる時間を {0}分 に設定しました。", 
                                        value
                                    )));
                                } catch (NumberFormatException e) {
                                    log(sender, Component.text("[value] には 1 以上の整数を指定してください。", TextColor.color(255, 0, 0)));
                                }
                            }
                            return true;
                        }
                    }
                } else if (args[1].equals("announce-messages")) {
                    switch (args[2]) {
                        case Config.AnnounceMessages.PATH_SPAWN_TEMP_STOP -> {
                            // set announce-messages spawn-temp-stop [value]
                            if (args.length == 3) {
                                log(sender, Component.text(MessageFormat.format(
                                    "TPS低下による一時的なMOB湧き制限が行われた時のメッセージ：{0}", 
                                    _config.announceMessages().spawnTempStop()
                                )));
                            } else {
                                _config.announceMessages().spawnTempStop(args[3]);
                                _config.save();
                                log(sender, Component.text("TPS低下による一時的なMOB湧き制限が行われた時のメッセージを更新しました。"));
                            }
                            return true;
                        }

                        case Config.AnnounceMessages.PATH_SPAWN_PERM_STOP -> {
                            // set announce-messages spawn-perm-stop [value]
                            if (args.length == 3) {
                                log(sender, Component.text(MessageFormat.format(
                                    "慢性的なTPS低下による恒久的なMOB湧き制限が行われた時のメッセージ：{0}", 
                                    _config.announceMessages().spawnPermStop()
                                )));
                            } else {
                                _config.announceMessages().spawnPermStop(args[3]);
                                _config.save();
                                log(sender, Component.text("慢性的なTPS低下による恒久的なMOB湧き制限が行われた時のメッセージを更新しました。"));
                            }
                            return true;
                        }

                        case Config.AnnounceMessages.PATH_SPAWN_RESUME -> {
                            // set announce-messages spawn-resume [value]
                            if (args.length == 3) {
                                log(sender, Component.text(MessageFormat.format(
                                    "TPS回復によるMOB湧き制限が解除された時のメッセージ：{0}", 
                                    _config.announceMessages().spawnResume()
                                )));
                            } else {
                                _config.announceMessages().spawnResume(args[3]);
                                _config.save();
                                log(sender, Component.text("TPS回復によるMOB湧き制限が解除された時のメッセージを更新しました。"));
                            }
                            return true;
                        }
                    }
                }
                break;
            
            case "ignore-worlds":
                if (args.length == 1) {
                    showIgnoreWorldNames(sender);
                    return true;
                } else if (args.length == 3) {
                    if (args[1].equals("add")) {
                        var ignoreWorlds = _config.ignoreWorlds();
                        if (ignoreWorlds.contains(args[2])) {
                            log(sender, Component.text(MessageFormat.format(
                                "すでにワールド {0} は監視除外対象に追加されています。",
                                args[2]
                            ), TextColor.color(255, 255, 0)));
                        } else {
                            ignoreWorlds.add(args[2]);
                            _config.ignoreWorlds(ignoreWorlds);
                            _config.save();
                            log(sender, Component.text(MessageFormat.format(
                                "ワールド {0} を監視除外対象に追加しました。", 
                                args[2]
                            )));
                        }
                    } else if (args[1].equals("remove")) {
                        var ignoreWorlds = _config.ignoreWorlds();
                        if (ignoreWorlds.contains(args[2])) {
                            ignoreWorlds.remove(args[2]);
                            _config.ignoreWorlds(ignoreWorlds);
                            _config.save();
                            log(sender, Component.text(MessageFormat.format(
                                "ワールド {0} を監視除外対象から削除しました。",
                                args[2]
                            )));
                        } else {
                            log(sender, Component.text(MessageFormat.format(
                                "ワールド {0} が監視除外対象の中に見つかりませんでした。", 
                                args[2]
                            ), TextColor.color(255, 255, 0)));
                        }
                    } else {
                        log(sender, Component.text("引数2には add または remove を指定してください。", TextColor.color(255, 0, 0)));
                    }
                    return true;
                }
                break;
        }
        log(sender, Component.text("コマンドに誤りがあります。"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Stream.of("help", "reload", "info", "set", "ignore-worlds").filter(s -> s.startsWith(args[0])).toList();
        }
        return switch (args[0]) {
            case "help" -> List.of("[page]");
            case "info" -> {
                yield switch (args.length) {
                    case 2 -> Stream.of("page", "world").filter((s) -> s.startsWith(args[1])).toList();
                    case 3 -> switch (args[1]) {
                        case "page" -> List.of("[page]");
                        case "world" -> Bukkit.getWorlds().stream().map((e) -> e.getName()).filter((s) -> s.startsWith(args[2])).toList();
                        default -> null;
                    };
                    case 4 -> (args[1].equals("world")) ? List.of("[page]") : null;
                    default -> null;
                };
            }
            case "set" -> {
                yield switch (args.length) {
                    case 2 -> Stream.of("world", "tps", "announce-messages").filter((s) -> s.startsWith(args[1])).toList();
                    case 3 -> switch (args[1]) {
                        case "world" -> _config.spawnLimitByMobsPerWorld().getKeys().stream().filter((s) -> s.startsWith(args[2])).toList();
                        case "tps" -> _config.spawnLimitByTPS().getKeys().stream().filter((s) -> s.startsWith(args[2])).toList();
                        case "announce-messages" -> _config.announceMessages().getKeys().stream().filter((s) -> s.startsWith(args[2])).toList();
                        default -> null;
                    };
                    case 4 -> List.of("[value]");
                    default -> null;
                };
            }
            case "ignore-worlds" -> {
                yield switch (args.length) {
                    case 2 -> Stream.of("add", "remove").filter((s) -> s.startsWith(args[1])).toList();
                    case 3 -> switch (args[1]) {
                        case "add" -> Bukkit.getWorlds().stream().map((e) -> e.getName()).filter((s) -> (!_config.ignoreWorlds().contains(s) && s.startsWith(args[2]))).toList();
                        case "remove" -> _config.ignoreWorlds().stream().filter((s) -> s.startsWith(args[2])).toList();
                        default -> null;
                    };
                    default -> null;
                };
            }
            default -> null;
        };
    }

    private void init() {
        // 初期化処理
        _latestStopByTPSsTime = null;
        _stopByTPSsCounter = 0;
        runMobCountCheckTask(
            _config.spawnLimitByMobsPerWorld().enable(),
            _config.spawnLimitByMobsPerWorld().mobsUpperLimit(),
            _config.spawnLimitByMobsPerWorld().checkExceededIntervalTick()
        );
        runTPSCheckTask(
            _config.spawnLimitByTPS().enable(),
            _config.spawnLimitByTPS().tpsCriterion(),
            _config.spawnLimitByTPS().checkBelowIntervalTick(),
            _config.spawnLimitByTPS().checkRecoveryIntervalTick(),
            _config.spawnLimitByTPS().invalidCheckTickAfterRestart(),
            _config.spawnLimitByTPS().repeatCounterLimit(),
            _config.spawnLimitByTPS().repeatCounterResetMinute(),
            _config.announceMessages().spawnTempStop(),
            _config.announceMessages().spawnPermStop(),
            _config.announceMessages().spawnResume()
        );

        // 現在読み込まれているワールドの監視対象フラグなどを再設定
        _monitoringWorlds.clear();
        Bukkit.getWorlds().forEach((e) -> {
            _monitoringWorlds.put(e.getName(), new MonitoringWorld(e, _config.ignoreWorlds().contains(e.getName())));
        });
    }

    private void checkNewLoadWorld() {
        // 登録されていない（おそらく追加された）ワールドを新しく一覧に追加し、監視対象フラグなどを設定
        Bukkit.getWorlds().stream().filter((e) -> !_monitoringWorlds.containsKey(e.getName())).forEach((e) -> {
            _monitoringWorlds.put(e.getName(), new MonitoringWorld(e, _config.ignoreWorlds().contains(e.getName())));
        });
    }

    //==============================
    // ログ
    //==============================
    private void log(TextComponent... messages) {
        log(true, messages);
    }

    private void log(boolean showPrefix, TextComponent... messages) {
        var msg = showPrefix ? LOG_PREFIX : Component.text("");
        for (var e : messages) {
            msg = msg.append(e);
        }
        Bukkit.getConsoleSender().sendMessage(msg);
    }

    private void log(CommandSender sender, TextComponent... messages) {
        log(sender, true, messages);
    }

    private void log(CommandSender sender, boolean showPrefix, TextComponent... messages) {
        var msg = showPrefix ? LOG_PREFIX : Component.text("");
        for (var e : messages) {
            msg = msg.append(e);
        }
        sender.sendMessage(msg);
    }

    private void logAllPlayer(TextComponent... messages) {
        logAllPlayer(true, messages);
    }

    private void logAllPlayer(boolean showPrefix, TextComponent... messages) {
        var msg = showPrefix ? LOG_PREFIX : Component.text("");
        for (var e : messages) {
            msg = msg.append(e);
        }
        final TextComponent finalMsg = msg;
        Bukkit.getOnlinePlayers().forEach((e) -> e.sendMessage(finalMsg));
    }

    private TextColor getTextColor(ChatColor color) {
        return switch (color) {
            case BLACK -> TextColor.color(0, 0, 0);
            case DARK_BLUE -> TextColor.color(0, 0, 170);
            case DARK_GREEN -> TextColor.color(0, 170, 0);
            case DARK_AQUA -> TextColor.color(0, 170, 170);
            case DARK_RED -> TextColor.color(170, 0, 0);
            case DARK_PURPLE -> TextColor.color(170, 0, 170);
            case GOLD -> TextColor.color(255, 170, 0);
            case GRAY -> TextColor.color(170, 170, 170);
            case DARK_GRAY -> TextColor.color(85, 85, 85);
            case BLUE -> TextColor.color(85, 85, 255);
            case GREEN -> TextColor.color(85, 255, 85);
            case AQUA -> TextColor.color(85, 255, 255);
            case RED -> TextColor.color(255, 85, 85);
            case LIGHT_PURPLE -> TextColor.color(255, 85, 255);
            case YELLOW -> TextColor.color(255, 255, 85);
            case WHITE -> TextColor.color(255, 255, 255);
            default -> null;
        };
    }

    //==============================
    // タスク
    //==============================
    /**
     * 一定時間ごとに各ワールドのMOB数をチェックし、一定数以上であればMOBを減らすタスクを実行します。
     */
    private @NotNull void runMobCountCheckTask(boolean enable, int mobsUpperLimit, int checkExceededIntervalTick) {
        // 既に実行されているタスクがあればキャンセル
        if (_mobCountCheckTask != null) {
            _mobCountCheckTask.cancel();
        }

        // 無効化されていれば実行しない
        if (!enable) return;

        _mobCountCheckTask = Bukkit.getScheduler().runTaskTimer(
            this, 
            () -> {
                for (var e : _monitoringWorlds.values()) {
                    // エンティティが上限を超えていた場合は超えた分だけ削除
                    var entityCount = e.getEntitiesInfo().entityCount();
                    if (entityCount - mobsUpperLimit > 0) {
                        e.removeMobs(entityCount - mobsUpperLimit);
                    }
                }
            }, 
            checkExceededIntervalTick, 
            checkExceededIntervalTick
        );
    }

    /**
     * 一定時間ごとにサーバーのTPSをチェックし、一定数未満であればMOBを削除するタスクを実行します。
     */
    private void runTPSCheckTask(
        boolean enable, double tpsCriterion, int checkBelowIntervalTick, int checkRecoveryIntervalTick, int invalidCheckTickAfterRestart,
        int repeatCounterLimit, int repeatCounterResetMinute, String announceSpawnTempStop, String announceSpawnPermStop, String announceSpawnResume
    ) {
        // 既に実行されているタスクがあればキャンセル
        if (_tpsCheckTask != null) {
            _tpsCheckTask.cancel();
        }

        // 無効化されているなら実行しない
        if (!enable) return;

        _tpsCheckTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            this, 
            () -> {
                // 直近1分のTPS平均値が基準値を下回ったら実行
                if (Bukkit.getTPS()[0] >= tpsCriterion) return;

                Bukkit.getScheduler().runTaskLater(this, () -> {
                    log(Component.text(MessageFormat.format(
                        "直近 1 分のTPS平均値が基準値を下回ったため、MOBを削除し、湧きを制限します。（平均値: {0} / 基準値: {1}）",
                        Bukkit.getTPS()[0], tpsCriterion
                    )));

                    // 前回の実行から一定時間以内ならカウンターを増加し、規定値に達したら復旧なしの停止
                    var now = LocalDateTime.now();
                    if (_latestStopByTPSsTime != null) {
                        if (ChronoUnit.MINUTES.between(_latestStopByTPSsTime, now) >= repeatCounterResetMinute) {
                            _stopByTPSsCounter = 0;
                        }
                    }
                    _latestStopByTPSsTime = now;
                    _stopByTPSsCounter++;
        
                    // タスクの実行を停止
                    _tpsCheckTask.cancel();
        
                    // モブを全削除して湧きを停止
                    _monitoringWorlds.values().forEach((e) -> {
                        e.removeAllMobs();
                        e.stopMobSpawning();
                    });
        
                    // 一定時間後、TPSが基準値を超えていれば戻す
                    if (!(_stopByTPSsCounter > repeatCounterLimit)) {
                        logAllPlayer(Component.text(announceSpawnTempStop, getTextColor(ChatColor.YELLOW)));
                        log(Component.text(MessageFormat.format(
                            "直近 {0} 分間におけるTPS低下検知回数は {1} 回です。TPSが回復すると、MOBの沸きが再開されます。",
                            repeatCounterResetMinute, _stopByTPSsCounter
                        )));
                        runTPSRecoverCheckTask(
                            enable, tpsCriterion, checkBelowIntervalTick, checkRecoveryIntervalTick, invalidCheckTickAfterRestart, 
                            repeatCounterLimit, repeatCounterResetMinute, announceSpawnTempStop, announceSpawnPermStop, announceSpawnResume
                        );
                    } else {
                        logAllPlayer(Component.text(announceSpawnPermStop, getTextColor(ChatColor.RED), TextDecoration.BOLD));
                        log(Component.text(MessageFormat.format(
                            "直近 {0} 分間におけるTPS低下検知回数が {1} 回を超えたため、MOBの沸き再開を無効化します。",
                            repeatCounterResetMinute, repeatCounterLimit
                        )));
                    }
                }, 0L);
            }, 
            checkBelowIntervalTick, 
            checkBelowIntervalTick
        );
    }

    private void runTPSRecoverCheckTask(
        boolean enable, double tpsCriterion, int checkBelowIntervalTick, int checkRecoveryIntervalTick, int invalidCheckTickAfterRestart,
        int repeatCounterLimit, int repeatCounterResetMinute, String announceSpawnTempStop, String announceSpawnPermStop, String announceSpawnResume
    ) {
        assert _tpsCheckTask != null;
        _tpsCheckTask.cancel();

        _tpsCheckTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            this,
            () -> {
                // 直近n分のTPS平均値が基準値を上回ったら実行
                int n;
                if (checkRecoveryIntervalTick < 6000) {
                    n = 1;
                } else if (checkRecoveryIntervalTick < 18000) {
                    n = 5;
                } else {
                    n = 15;
                }

                double tps = switch (n) {
                    case 1 -> Bukkit.getTPS()[0];
                    case 5 -> Bukkit.getTPS()[1];
                    case 15 -> Bukkit.getTPS()[2];
                    default -> throw new InternalError();
                };
                if (tps < tpsCriterion) return;

                Bukkit.getScheduler().runTaskLater(this, () -> {
                    // タスクの実行を停止
                    _tpsCheckTask.cancel();

                    // モブの湧きを再開
                    _monitoringWorlds.values().forEach(IMonitoringWorld::startMobSpawning);

                    log(Component.text(MessageFormat.format(
                        "直近 {0} 分のTPS平均値が基準値を上回ったため、MOBの湧き制限を解除します。TPSチェックは {1} ({2}Tick) 後に再開します。（平均値: {3} / 基準値: {4}）",
                        n, convertTickToTimeString(invalidCheckTickAfterRestart), invalidCheckTickAfterRestart, tps, tpsCriterion
                    )));
                    logAllPlayer(Component.text(announceSpawnResume, getTextColor(ChatColor.AQUA)));

                    // 一定時間後、元のTPSチェックを再実行
                    runRestartTPSCheckTask(
                        enable, tpsCriterion, checkBelowIntervalTick, checkRecoveryIntervalTick, invalidCheckTickAfterRestart, 
                        repeatCounterLimit, repeatCounterResetMinute, announceSpawnTempStop, announceSpawnPermStop, announceSpawnResume
                    );
                }, 0L);
            },
            checkRecoveryIntervalTick,
            checkRecoveryIntervalTick
        );
    }

    private void runRestartTPSCheckTask(
        boolean enable, double tpsCriterion, int checkBelowIntervalTick, int checkRecoveryIntervalTick, int invalidCheckTickAfterRestart,
        int repeatCounterLimit, int repeatCounterResetMinute, String announceSpawnTempStop, String announceSpawnPermStop, String announceSpawnResume
    ) {
        assert _tpsCheckTask != null;
        _tpsCheckTask.cancel();

        _tpsCheckTask = Bukkit.getScheduler().runTaskLaterAsynchronously(
            this,
            () -> {
                log(Component.text("TPSチェックを再開します。"));
                runTPSCheckTask(
                    enable, tpsCriterion, checkBelowIntervalTick, checkRecoveryIntervalTick, invalidCheckTickAfterRestart, 
                    repeatCounterLimit, repeatCounterResetMinute, announceSpawnTempStop, announceSpawnPermStop, announceSpawnResume
                );
            },
            invalidCheckTickAfterRestart
        );
    }

    //==============================
    // コマンド
    //==============================
    private void showHelp(CommandSender sender, int page) {
        var maxPage = ((_registeredCommands.size() - 1) / HELP_SHOW_COMMANDS) + 1;
        page = Math.min(page, maxPage);

        log(sender, false, Component.text(" "));
        log(sender, false, Component.text(MessageFormat.format("----------コマンド一覧 ({0}/{1})----------", page, maxPage)));
        var registeredCommands = _registeredCommands.stream().skip(HELP_SHOW_COMMANDS * (page - 1)).limit(HELP_SHOW_COMMANDS).toList();
        for (var i = 0; i < registeredCommands.size(); i++) {
            var registeredCommand = registeredCommands.get(i);
            log(sender, false, Component.text(registeredCommand._command, getTextColor(ChatColor.YELLOW), TextDecoration.BOLD).clickEvent(ClickEvent.suggestCommand(registeredCommand._command)));
            registeredCommand._descriptions.forEach((e) -> {
                log(sender, false, e);
            });
            if (i < registeredCommands.size() - 1) {
                log(sender, false, Component.text(" "));
            }
        }
        log(sender, false, Component.text("------------------------------------------------"));
        log(sender, false, Component.text(" "));
    }

    private void showInfo(CommandSender sender, int page) {
        var maxPage = ((Bukkit.getWorlds().size() - 1) / INFO_WORLDS_LINES) + 1;
        page = Math.min(page, maxPage);

        log(sender, Component.text("現在のモブの沸き量を表示します。"));
        log(sender, Component.text("計測中..."));
        
        log(sender, false, Component.text(" "));
        log(sender, false, Component.text(MessageFormat.format("------------現在の湧き量 ({0}/{1})---------------", page, maxPage)));

        var totalEntityCount      = 0;
        var totalMobCount         = 0;
        var totalFriendlyMobCount = 0;
        var totalMonsterMobCount  = 0;

        var monitoringWorlds = _monitoringWorlds.entrySet().stream().map((e) -> e.getValue()).toList();
        for (var i = 0; i < monitoringWorlds.size(); i++) {
            var monitoringWorld = monitoringWorlds.get(i);
            var worldEntitiesInfo = monitoringWorld.getEntitiesInfo();

            totalEntityCount += worldEntitiesInfo.entityCount();
            totalMobCount += worldEntitiesInfo.mobCount();
            totalFriendlyMobCount += worldEntitiesInfo.friendlyMobCount();
            totalMonsterMobCount += worldEntitiesInfo.monsterMobCount();
            
            // ワールドが表示対象ならリストに追加
            if (i / INFO_WORLDS_LINES + 1 == page) {
                log(sender, false, Component.text(MessageFormat.format("{0}：{1}体", monitoringWorld.getWorld().getName(), worldEntitiesInfo.mobCount())));
            }
        }

        log(sender, false, Component.text(" "));
        log(sender, false, Component.text(MessageFormat.format("現在の総湧き数 {0}体", totalEntityCount)));
        log(sender, false, Component.text(MessageFormat.format("現在の湧き比率 {0}％ {1}/{2}（mob/entity）",
            ((double) Math.round(((double) totalMobCount / totalEntityCount) * 1000)) / 10,
            totalMobCount,
            totalEntityCount
        )));
        log(sender, false, Component.text(MessageFormat.format("現在のmob比率 {0}/{1}％ {2}/{3}体（mob/enemy）", 
            ((double) Math.round(((double) totalFriendlyMobCount / totalMobCount) * 1000)) / 10,
            ((double) Math.round(((double) totalMonsterMobCount / totalMobCount) * 1000)) / 10,
            totalFriendlyMobCount, 
            totalMonsterMobCount
        )));
        log(sender, false, Component.text("------------------------------------------------"));
        log(sender, false, Component.text(" "));
    }

    private void showDetailInfo(CommandSender sender, String worldName, int page) {
        IMonitoringWorld monitoringWorld = _monitoringWorlds.get(worldName);
        if (monitoringWorld == null) {
            log(sender, Component.text("ワールドが見つかりませんでした。"));
            return;
        }

        var worldEntitiesInfo = monitoringWorld.getEntitiesInfo();

        var maxPage = ((worldEntitiesInfo.mobs().size() - 1) / INFO_MOBS_LINES) + 1;
        page = Math.min(page, maxPage);

        log(sender, false, Component.text(" "));
        log(sender, false, Component.text(MessageFormat.format("-------{0}の湧き量詳細 ({1}/{2})---------", worldName, page, maxPage)));

        worldEntitiesInfo.mobs().entrySet().stream().skip(INFO_MOBS_LINES * (page - 1)).limit(INFO_MOBS_LINES).forEach((e) -> {
            log(sender, false, Component.text(MessageFormat.format("{0} {1}体 {2}％",
                e.getKey(), 
                e.getValue(), 
                ((double) Math.round(((double) e.getValue() / worldEntitiesInfo.mobCount()) * 1000)) / 10
            )));
        });

        log(sender, false, Component.text(" "));
        log(sender, false, Component.text(MessageFormat.format("現在の総湧き数 {0}体", worldEntitiesInfo.entityCount())));
        log(sender, false, Component.text(MessageFormat.format("現在の湧き比率 {0}％ {1}/{2}（mob/entity）",
            ((double) Math.round(((double) worldEntitiesInfo.mobCount() / worldEntitiesInfo.entityCount()) * 1000)) / 10,
            worldEntitiesInfo.mobCount(),
            worldEntitiesInfo.entityCount()
        )));
        log(sender, false, Component.text(MessageFormat.format("現在のmob比率 {0}/{1}％ {2}/{3}体（mob/enemy）", 
            ((double) Math.round(((double) worldEntitiesInfo.friendlyMobCount() / worldEntitiesInfo.mobCount()) * 1000)) / 10,
            ((double) Math.round(((double) worldEntitiesInfo.monsterMobCount() / worldEntitiesInfo.mobCount()) * 1000)) / 10,
            worldEntitiesInfo.friendlyMobCount(), 
            worldEntitiesInfo.monsterMobCount()
        )));
        log(sender, false, Component.text("------------------------------------------------"));
        log(sender, false, Component.text(" "));
    }

    private void showIgnoreWorldNames(CommandSender sender) {
        log(sender, false, Component.text(" "));
        log(sender, false, Component.text("-------監視除外対象に追加されているワールド---------"));
        log(sender, false, Component.text("緑：監視除外中", getTextColor(ChatColor.GREEN)));
        log(sender, false, Component.text("黄：追加未反映（/mobchecker reload 実行後に反映）", getTextColor(ChatColor.YELLOW)));
        log(sender, false, Component.text("赤：削除未反映（/mobchecker reload 実行後に反映）", getTextColor(ChatColor.RED)));
        log(sender, false, Component.text("灰：監視除外対象に追加されているがワールドが見つからない", getTextColor(ChatColor.GRAY)));
        log(sender, false, Component.text(" "));
        var ignoreWorlds = _config.ignoreWorlds();
        ignoreWorlds.forEach((s) -> {
            var monitoringWorld = _monitoringWorlds.get(s);
            
            TextColor textColor;
            if (monitoringWorld != null) {
                if (monitoringWorld.isIgnored()) {
                    textColor = getTextColor(ChatColor.GREEN);
                } else {
                    textColor = getTextColor(ChatColor.YELLOW);
                }
            } else {
                textColor = getTextColor(ChatColor.GRAY);
            }

            log(sender, false, Component.text(s, textColor));
        });
        _monitoringWorlds.values().stream().filter((e) -> e.isIgnored() && !ignoreWorlds.contains(e.getWorld().getName())).forEach((e) -> {
            log(sender, false, Component.text(e.getWorld().getName(), getTextColor(ChatColor.RED)));
        });
        log(sender, false, Component.text("------------------------------------------------"));
        log(sender, false, Component.text(" "));
    }

    private int[] convertTickToTime(int tick) {
        assert tick >= 0;
        return new int[]{tick / 72000, (tick / 1200) % 60, (tick / 20) % 60, (tick % 20) * 5};
    }

    private String convertTickToTimeString(int tick) {
        var time = convertTickToTime(tick);
        StringBuilder sb = new StringBuilder();
        if (time[0] != 0) {
            sb = sb.append(time[0]).append("時間");
        }
        if (time[1] != 0) {
            sb = sb.append(time[1]).append("分");
        }
        if (time[2] != 0 && time[3] != 0) {
            sb = sb.append(time[2]).append(".").append(time[3]).append("秒");
        } else if (time[2] != 0) {
            sb = sb.append(time[2]).append("秒");
        } else if (time[3] != 0) {
            sb = sb.append(((double) time[3]) / 100).append("秒");
        }
        return sb.toString();
    }

    private class RegisteredCommand {
        private String _command;
        private List<TextComponent> _descriptions;

        RegisteredCommand(String command, TextComponent... components) {
            _command = command;
            var component = components[0];
            for (var i = 1; i < components.length; i++) {
                component.append(components[i]);
            }
            _descriptions = List.of(component);
        }

        RegisteredCommand(String command, List<TextComponent[]> componentsList) {
            _command = command;
            var descriptions = new ArrayList<TextComponent>();
            componentsList.forEach((e) -> {
                var component = e[0];
                for (var i = 1; i < e.length; i++) {
                    component.append(e[i]);
                }
                descriptions.add(component);
            });
            _descriptions = Collections.unmodifiableList(descriptions);
        }
    }
}