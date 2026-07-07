package net.blueva.arcade.modules.guess_the_build.game;

import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GamePhase;
import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.stats.StatsAPI;
import net.blueva.arcade.api.ui.MenuAPI;
import net.blueva.arcade.modules.guess_the_build.state.GuessTheBuildArenaState;
import net.blueva.arcade.modules.guess_the_build.state.GuessTheBuildPhase;
import net.blueva.arcade.modules.guess_the_build.state.GuessTheBuildPlot;
import net.blueva.arcade.modules.guess_the_build.support.cleanup.GuessTheBuildCleanupService;
import net.blueva.arcade.modules.guess_the_build.support.outcome.GuessTheBuildOutcomeService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GuessTheBuildGame {

    private final ModuleInfo moduleInfo;
    private final ModuleConfigAPI moduleConfig;
    private final CoreConfigAPI coreConfig;
    private final StatsAPI statsAPI;

    private final Map<Integer, GuessTheBuildArenaState> arenas = new ConcurrentHashMap<>();
    private final Map<Player, Integer> playerArena = new ConcurrentHashMap<>();

    private final GuessTheBuildOutcomeService outcomeService;
    private final GuessTheBuildPlotService plotService;
    private final GuessTheBuildRoundService roundService;
    private final GuessTheBuildThemeService themeService;
    private final GuessTheBuildHintService hintService;
    private final GuessTheBuildCleanupService cleanupService;
    private final Set<Integer> notificationSeconds;

    public GuessTheBuildGame(ModuleInfo moduleInfo,
                             ModuleConfigAPI moduleConfig,
                             CoreConfigAPI coreConfig,
                             StatsAPI statsAPI,
                             MenuAPI<Player, Material> menuAPI) {
        this.moduleInfo = moduleInfo;
        this.moduleConfig = moduleConfig;
        this.coreConfig = coreConfig;
        this.statsAPI = statsAPI;
        this.outcomeService = new GuessTheBuildOutcomeService(moduleInfo, statsAPI, this);
        this.plotService = new GuessTheBuildPlotService();
        this.themeService = new GuessTheBuildThemeService(moduleConfig, menuAPI, moduleInfo.getId());
        this.hintService = new GuessTheBuildHintService(moduleConfig);
        this.cleanupService = new GuessTheBuildCleanupService();
        this.roundService = new GuessTheBuildRoundService(moduleConfig, themeService, plotService);
        this.notificationSeconds = loadNotificationSeconds(coreConfig);
    }

    private static Set<Integer> loadNotificationSeconds(CoreConfigAPI coreConfig) {
        Set<Integer> seconds = new HashSet<>();
        List<String> raw = coreConfig.getSettingsStringList("game.global.countdown_notifications");
        if (raw != null) {
            for (String s : raw) {
                try {
                    seconds.add(Integer.parseInt(s.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return seconds;
    }

    public void startGame(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();

        context.getSummarySettings().setGameSummaryEnabled(false);
        context.getSummarySettings().setFinalSummaryEnabled(false);

        context.getSchedulerAPI().cancelArenaTasks(arenaId);
        GuessTheBuildArenaState state = new GuessTheBuildArenaState(context);
        arenas.put(arenaId, state);

        plotService.loadPlot(context, state);
        plotService.assignPlayersToPlot(context, state);
        plotService.clearPlot(context, state);
        plotService.regeneratePlotFloor(context, state);

        for (Player player : context.getPlayers()) {
            playerArena.put(player, arenaId);
            if (player.isOnline()) {
                plotService.teleportToPlot(context, state, player);
            }
        }
    }

    public void handleCountdownTick(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                    int secondsLeft) {
        for (Player player : context.getPlayers()) {
            if (!player.isOnline()) {
                continue;
            }
            context.getSoundsAPI().play(player, coreConfig.getSound("sounds.starting_game.countdown"));
            String title = coreConfig.getLanguage(player, "titles.starting_game.title")
                    .replace("{game_display_name}", moduleInfo.getName())
                    .replace("{time}", String.valueOf(secondsLeft));
            String subtitle = coreConfig.getLanguage(player, "titles.starting_game.subtitle")
                    .replace("{game_display_name}", moduleInfo.getName())
                    .replace("{time}", String.valueOf(secondsLeft));
            context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 20, 5);
        }
    }

    public void handleCountdownFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        for (Player player : context.getPlayers()) {
            if (!player.isOnline()) {
                continue;
            }
            context.getSoundsAPI().play(player, coreConfig.getSound("sounds.starting_game.start"));
        }
    }

    public void beginPlaying(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        GuessTheBuildArenaState state = getArenaState(context);
        if (state == null) {
            return;
        }

        startRound(context, state);
    }

    private void startRound(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                            GuessTheBuildArenaState state) {
        hintService.reset();
        roundService.startRound(context, state, theme -> onThemeSelected(context, state, theme));
        showScoreboard(context);
        updateScoreboard(context, state);
        startThemeSelectionTimer(context, state);
    }

    private void startThemeSelectionTimer(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                          GuessTheBuildArenaState state) {
        int arenaId = context.getArenaId();
        String taskId = "arena_" + arenaId + "_guess_the_build_theme_timer";

        context.getSchedulerAPI().cancelTask(taskId);
        context.getSchedulerAPI().runTimer(taskId, () -> {
            if (state.isEnded() || state.getPhase() != GuessTheBuildPhase.THEME_SELECTION) {
                context.getSchedulerAPI().cancelTask(taskId);
                return;
            }

            state.decrementThemeSelectionTime();
            int timeLeft = state.getThemeSelectionTimeLeft();
            updateScoreboard(context, state);

            if (timeLeft <= 0) {
                context.getSchedulerAPI().cancelTask(taskId);
                roundService.forceThemeSelection(context, state);
                startBuildTimer(context, state);
                return;
            }

            if (state.isThemeSelected()) {
                context.getSchedulerAPI().cancelTask(taskId);
                startBuildTimer(context, state);
            }
        }, 20L, 20L);
    }

    public void onThemeSelected(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                GuessTheBuildArenaState state,
                                GuessTheBuildThemeService.GuessTheme theme) {
        if (state.getPhase() != GuessTheBuildPhase.THEME_SELECTION) {
            return;
        }
        String taskId = "arena_" + context.getArenaId() + "_guess_the_build_theme_timer";
        context.getSchedulerAPI().cancelTask(taskId);
        roundService.applyTheme(context, state, theme);
        updateScoreboard(context, state);
        startBuildTimer(context, state);
    }

    private void startBuildTimer(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                 GuessTheBuildArenaState state) {
        int arenaId = context.getArenaId();
        String taskId = "arena_" + arenaId + "_guess_the_build_build_timer";

        context.getSchedulerAPI().cancelTask(taskId);
        updateScoreboard(context, state);
        context.getSchedulerAPI().runTimer(taskId, () -> {
            if (state.isEnded() || state.getPhase() != GuessTheBuildPhase.BUILDING) {
                context.getSchedulerAPI().cancelTask(taskId);
                return;
            }

            state.decrementBuildTime();
            int timeLeft = state.getBuildTimeLeft();

            hintService.tick(context, state);
            updateScoreboard(context, state);

            if (timeLeft <= 0) {
                context.getSchedulerAPI().cancelTask(taskId);
                endBuildPhase(context, state, false);
                return;
            }

            if (notificationSeconds.contains(timeLeft)) {
                for (Player player : context.getPlayers()) {
                    if (player.isOnline()) {
                        context.getSoundsAPI().play(player, coreConfig.getSound("sounds.in_game.countdown"));
                    }
                }
                broadcastTimeMessage(context, "build.time_remaining", timeLeft);
            }
        }, 20L, 20L);
    }

    public void handleCorrectGuess(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                   GuessTheBuildArenaState state,
                                   Player guesser) {
        if (state.getPhase() != GuessTheBuildPhase.BUILDING || state.hasGuessed(guesser)) {
            return;
        }

        int guesserBonus = moduleConfig.getIntFrom("settings.yml",
                "points.guesser_speed_bonus." + (state.getWhoGuessed().size() + 1), 0);

        int basePoints = 1;
        if (state.getCurrentThemeDifficulty() != null) {
            basePoints = moduleConfig.getIntFrom("settings.yml",
                    "points.difficulty_base." + state.getCurrentThemeDifficulty().name().toLowerCase(), 1);
        }
        int totalPoints = basePoints + guesserBonus;

        state.addPoints(guesser.getUniqueId(), totalPoints);
        GuessTheBuildPlot guesserPlot = state.getPlayerPlot(guesser.getUniqueId());
        if (guesserPlot != null) {
            guesserPlot.addPoints(totalPoints);
        }
        state.addWhoGuessed(guesser);

        String broadcast = moduleConfig.getTranslation(guesser, "game.correct_guess_broadcast");
        if (broadcast != null) {
            broadcast = broadcast.replace("{player}", guesser.getName());
            for (Player player : context.getPlayers()) {
                if (player.isOnline()) {
                    context.getMessagesAPI().sendRaw(player, broadcast);
                }
            }
        }

        String pointsMsg = moduleConfig.getTranslation(guesser, "game.correct_guess_points");
        if (pointsMsg != null) {
            context.getMessagesAPI().sendRaw(guesser, pointsMsg.replace("{points}", String.valueOf(totalPoints)));
        }

        Player builder = state.getCurrentBuilder();
        if (builder != null) {
            int builderPoints = moduleConfig.getIntFrom("settings.yml", "points.builder_per_guess", 1);
            state.addPoints(builder.getUniqueId(), builderPoints);
            GuessTheBuildPlot builderPlot = state.getCurrentBuildPlot();
            if (builderPlot != null) {
                builderPlot.addPoints(builderPoints);
            }
            String builderMsg = moduleConfig.getTranslation(guesser, "game.builder_got_points");
            if (builderMsg != null) {
                context.getMessagesAPI().sendRaw(builder, builderMsg.replace("{points}", String.valueOf(builderPoints)));
            }
        }

        int reduction = moduleConfig.getIntFrom("settings.yml", "timers.guess_time_reduction_seconds", 10);
        int minRemaining = moduleConfig.getIntFrom("settings.yml", "timers.min_remaining_time_after_guess", 15);
        int newTime = Math.max(minRemaining, state.getBuildTimeLeft() - reduction);
        state.setBuildTimeLeft(newTime);

        int totalPlayers = context.getPlayers().size();
        if (state.getWhoGuessed().size() >= totalPlayers - 1) {
            endBuildPhase(context, state, true);
        }
    }

    private void endBuildPhase(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                               GuessTheBuildArenaState state,
                               boolean allGuessed) {
        context.getSchedulerAPI().cancelTask("arena_" + context.getArenaId() + "_guess_the_build_build_timer");

        if (!allGuessed) {
            String theme = state.getCurrentTheme() != null ? state.getCurrentTheme() : "???";
            for (Player player : context.getPlayers()) {
                if (player.isOnline()) {
                    String msg = moduleConfig.getTranslation(player, "game.theme_reveal_timeout");
                    if (msg != null) {
                        context.getMessagesAPI().sendRaw(player, msg.replace("{theme}", theme));
                    }
                    String title = moduleConfig.getTranslation(player, "game.theme_reveal_timeout_title");
                    String subtitle = moduleConfig.getTranslation(player, "game.theme_reveal_timeout_subtitle");
                    if (title != null && subtitle != null) {
                        context.getTitlesAPI().sendRaw(player, title, subtitle.replace("{theme}", theme), 0, 40, 20);
                    }
                }
            }
        } else {
            String theme = state.getCurrentTheme() != null ? state.getCurrentTheme() : "???";
            for (Player player : context.getPlayers()) {
                if (player.isOnline()) {
                    String msg = moduleConfig.getTranslation(player, "game.all_guessed");
                    if (msg != null) {
                        context.getMessagesAPI().sendRaw(player, msg.replace("{theme}", theme));
                    }
                }
            }
        }

        roundService.endRound(context, state);
        startRoundDelayTimer(context, state);
    }

    private void startRoundDelayTimer(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                      GuessTheBuildArenaState state) {
        int arenaId = context.getArenaId();
        String taskId = "arena_" + arenaId + "_guess_the_build_round_delay";

        context.getSchedulerAPI().runTimer(taskId, () -> {
            if (state.isEnded() || state.getPhase() != GuessTheBuildPhase.ROUND_END) {
                context.getSchedulerAPI().cancelTask(taskId);
                return;
            }

            state.decrementRoundDelayTime();
            if (state.getRoundDelayTimeLeft() <= 0) {
                context.getSchedulerAPI().cancelTask(taskId);

                roundService.advanceRound(state);

                if (roundService.shouldEndGame(state)) {
                    outcomeService.endGame(context, state);
                } else {
                    startRound(context, state);
                }
            }
        }, 20L, 20L);
    }

    private void updateScoreboard(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                  GuessTheBuildArenaState state) {
        Map<String, String> placeholders = buildScoreboardPlaceholders(context, state);

        for (Player player : context.getPlayers()) {
            if (player.isOnline()) {
                context.getScoreboardAPI().update(player, "scoreboard.default", placeholders);
            }
        }
    }

    private void showScoreboard(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        for (Player player : context.getPlayers()) {
            if (player.isOnline()) {
                context.getScoreboardAPI().showScoreboard(player, "scoreboard.default");
            }
        }
    }

    private Map<String, String> buildScoreboardPlaceholders(
            GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
            GuessTheBuildArenaState state) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("theme", "???");
        placeholders.put("time", formatTime(getDisplayedTimeLeft(state)));
        placeholders.put("round", String.valueOf(state.getRound()));
        placeholders.put("arena", String.valueOf(context.getArenaId()));
        placeholders.put("phase", formatPhase(state.getPhase()));
        Player builder = state.getCurrentBuilder();
        placeholders.put("builder", builder != null ? builder.getName() : "-");
        return placeholders;
    }

    private int getDisplayedTimeLeft(GuessTheBuildArenaState state) {
        return switch (state.getPhase()) {
            case THEME_SELECTION -> state.getThemeSelectionTimeLeft();
            case BUILDING -> state.getBuildTimeLeft();
            case ROUND_END -> state.getRoundDelayTimeLeft();
            case ENDED -> 0;
        };
    }

    private String formatPhase(GuessTheBuildPhase phase) {
        if (phase == null) {
            return "-";
        }
        return switch (phase) {
            case THEME_SELECTION -> "Theme Selection";
            case BUILDING -> "Building";
            case ROUND_END -> "Round End";
            case ENDED -> "Ended";
        };
    }

    private static String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void broadcastTimeMessage(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                      String path, int seconds) {
        String template = moduleConfig.getTranslation(null, path);
        if (template == null || template.isBlank()) {
            return;
        }
        String message = template.replace("{time}", String.valueOf(seconds));
        for (Player player : context.getPlayers()) {
            if (player.isOnline()) {
                context.getMessagesAPI().sendRaw(player, message);
            }
        }
    }

    public void regeneratePlotFloor(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                    GuessTheBuildArenaState state,
                                    GuessTheBuildPlot plot) {
        plotService.regeneratePlotFloor(context, plot);
    }

    public void finishGame(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();
        context.getSchedulerAPI().cancelArenaTasks(arenaId);

        GuessTheBuildArenaState state = arenas.get(arenaId);
        if (state != null) {
            plotService.clearPlot(context, state);
            plotService.regeneratePlotFloor(context, state);
        }
        arenas.remove(arenaId);
        cleanupService.resetWorldDefaults(context);
        cleanupService.resetPlayerStates(context.getPlayers());
        cleanupService.clearPlayerInventories(context.getPlayers());
        removePlayersFromArena(arenaId, context.getPlayers());
    }

    public void shutdown() {
        Set<GuessTheBuildArenaState> states = Set.copyOf(arenas.values());
        for (GuessTheBuildArenaState state : states) {
            state.getContext().getSchedulerAPI().cancelModuleTasks("guess_the_build");
            cleanupService.resetWorldDefaults(state.getContext());
            cleanupService.resetPlayerStates(state.getContext().getPlayers());
            cleanupService.clearPlayerInventories(state.getContext().getPlayers());
        }

        arenas.clear();
        playerArena.clear();
        themeService.clearPendingSelection(null);
    }

    public GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> getContext(Player player) {
        Integer arenaId = playerArena.get(player);
        if (arenaId == null) {
            for (GuessTheBuildArenaState state : arenas.values()) {
                if (state.getContext() != null && state.getContext().getPlayers().contains(player)) {
                    arenaId = state.getContext().getArenaId();
                    playerArena.put(player, arenaId);
                    break;
                }
            }
        }
        if (arenaId == null) {
            return null;
        }
        GuessTheBuildArenaState state = arenas.get(arenaId);
        return state != null ? state.getContext() : null;
    }

    public GuessTheBuildArenaState getArenaState(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        if (context == null) {
            return null;
        }
        return arenas.get(context.getArenaId());
    }

    public void endGame(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        GuessTheBuildArenaState state = getArenaState(context);
        if (state == null || state.isEnded()) {
            return;
        }
        state.markEnded();
        outcomeService.endGame(context, state);
    }

    public Map<String, String> getCustomPlaceholders(Player player) {
        Map<String, String> placeholders = new HashMap<>();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = getContext(player);
        if (context == null) {
            return placeholders;
        }
        GuessTheBuildArenaState state = getArenaState(context);
        if (state == null) {
            return placeholders;
        }
        placeholders.putAll(buildScoreboardPlaceholders(context, state));
        return placeholders;
    }

    public Map<Player, Integer> getPlayerArena() {
        return playerArena;
    }

    public void removePlayersFromArena(int arenaId, List<Player> players) {
        for (Player player : players) {
            playerArena.remove(player);
        }
    }

    public ModuleConfigAPI getModuleConfig() {
        return moduleConfig;
    }

    public CoreConfigAPI getCoreConfig() {
        return coreConfig;
    }

    public ModuleInfo getModuleInfo() {
        return moduleInfo;
    }

    public StatsAPI getStatsAPI() {
        return statsAPI;
    }

    public GuessTheBuildThemeService getThemeService() {
        return themeService;
    }

    private static String formatCountdownTime(int seconds) {
        int safeSeconds = Math.max(0, seconds);
        return String.format("%02d:%02d", safeSeconds / 60, safeSeconds % 60);
    }

}
