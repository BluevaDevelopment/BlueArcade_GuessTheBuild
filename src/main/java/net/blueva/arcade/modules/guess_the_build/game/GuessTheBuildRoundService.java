package net.blueva.arcade.modules.guess_the_build.game;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.modules.guess_the_build.state.GuessTheBuildArenaState;
import net.blueva.arcade.modules.guess_the_build.state.GuessTheBuildPhase;
import net.blueva.arcade.modules.guess_the_build.state.GuessTheBuildPlot;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class GuessTheBuildRoundService {

    private final ModuleConfigAPI moduleConfig;
    private final GuessTheBuildThemeService themeService;
    private final GuessTheBuildPlotService plotService;

    public GuessTheBuildRoundService(ModuleConfigAPI moduleConfig,
                                     GuessTheBuildThemeService themeService,
                                     GuessTheBuildPlotService plotService) {
        this.moduleConfig = moduleConfig;
        this.themeService = themeService;
        this.plotService = plotService;
    }

    public void startRound(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                           GuessTheBuildArenaState state,
                           Consumer<GuessTheBuildThemeService.GuessTheme> onThemeSelected) {
        state.resetRoundState();
        state.setPhase(GuessTheBuildPhase.THEME_SELECTION);

        if (!state.getPlots().isEmpty()) {
            plotService.clearPlot(context, state);
            plotService.regeneratePlotFloor(context, state);
        }

        selectNextBuilder(state, context);
        if (state.getCurrentBuilder() == null) {
            return;
        }

        GuessTheBuildPlot buildPlot = state.getPlots().isEmpty() ? null : state.getPlots().get(0);
        state.setCurrentBuildPlot(buildPlot);
        if (buildPlot != null && buildPlot.getSpawn() != null) {
            for (Player player : context.getPlayers()) {
                if (player.isOnline()) {
                    plotService.teleportToPlot(context, state, player);
                }
            }
        }

        for (Player player : context.getPlayers()) {
            if (player.isOnline()) {
                String title = moduleConfig.getTranslation(player, "game.theme_being_selected.title");
                String subtitle = moduleConfig.getTranslation(player, "game.theme_being_selected.subtitle");
                if (title != null && subtitle != null) {
                    context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 40, 20);
                }
            }
        }

        hidePlayers(context, state);

        int themeSelectionSeconds = moduleConfig.getIntFrom("settings.yml", "timers.theme_selection_seconds", 15);
        state.setThemeSelectionTimeLeft(themeSelectionSeconds);

        Player builder = state.getCurrentBuilder();
        if (builder != null && builder.isOnline()) {
            context.getSchedulerAPI().runLater(
                    "arena_" + context.getArenaId() + "_guess_the_build_open_theme_menu",
                    () -> {
                        if (state.isEnded()
                                || state.getPhase() != GuessTheBuildPhase.THEME_SELECTION
                                || !builder.isOnline()) {
                            return;
                        }
                        context.getSchedulerAPI().runAtEntity(builder,
                                () -> themeService.openThemeSelectionMenu(builder, state, onThemeSelected));
                    },
                    3L
            );
        }
    }

    public void forceThemeSelection(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                    GuessTheBuildArenaState state) {
        if (state.isThemeSelected()) {
            return;
        }
        GuessTheBuildThemeService.GuessTheme theme = themeService.forceRandomTheme(state);
        applyTheme(context, state, theme);
    }

    public void applyTheme(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                           GuessTheBuildArenaState state,
                           GuessTheBuildThemeService.GuessTheme theme) {
        state.setCurrentTheme(theme.getPrimaryName());
        state.setCurrentThemeSynonyms(theme.names());
        state.setCurrentThemeDifficulty(theme.difficulty());
        state.setThemeSelected(true);
        state.addPlayedTheme(String.join(", ", theme.names()));

        Player builder = state.getCurrentBuilder();
        if (builder != null && builder.isOnline()) {
            builder.closeInventory();
        }

        state.setPhase(GuessTheBuildPhase.BUILDING);

        int buildTimeSeconds = readBuildTimeSeconds(context);
        state.setBuildTimeLeft(buildTimeSeconds);

        for (Player player : context.getPlayers()) {
            if (!player.isOnline()) {
                continue;
            }
            if (player.equals(builder)) {
                player.setGameMode(GameMode.CREATIVE);
                player.setAllowFlight(true);
                player.setFlying(true);
                player.getInventory().clear();
            } else {
                player.setGameMode(GameMode.ADVENTURE);
                player.setAllowFlight(true);
                player.setFlying(true);
                player.getInventory().clear();
            }
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setFireTicks(0);

            context.getScoreboardAPI().showScoreboard(player, "scoreboard.default");
        }

        for (Player player : context.getPlayers()) {
            if (player.isOnline()) {
                String broadcast = moduleConfig.getTranslation(player, "game.theme_selected_broadcast");
                if (broadcast != null) {
                    context.getMessagesAPI().sendRaw(player, broadcast);
                }
            }
        }

        hidePlayers(context, state);
    }

    public void endRound(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                         GuessTheBuildArenaState state) {
        state.setPhase(GuessTheBuildPhase.ROUND_END);
        showPlayers(context, state);

        int roundDelay = moduleConfig.getIntFrom("settings.yml", "timers.round_delay_seconds", 5);
        state.setRoundDelayTimeLeft(roundDelay);
    }

    public boolean shouldEndGame(GuessTheBuildArenaState state) {
        int alivePlayers = state.getContext().getAlivePlayers().size();
        int totalRounds = alivePlayers;
        return state.getRound() > totalRounds;
    }

    public void advanceRound(GuessTheBuildArenaState state) {
        state.setRound(state.getRound() + 1);
    }

    private void selectNextBuilder(GuessTheBuildArenaState state,
                                   GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        List<Player> players = new ArrayList<>(context.getAlivePlayers());
        List<Player> alreadyBuilt = new ArrayList<>();
        for (Player p : players) {
            if (state.getPlayedPlots().contains(state.getPlayerPlot(p.getUniqueId()))) {
                alreadyBuilt.add(p);
            }
        }

        List<Player> available = new ArrayList<>(players);
        available.removeAll(alreadyBuilt);

        if (available.isEmpty()) {
            state.clearPlayedPlots();
            available = new ArrayList<>(players);
        }

        if (available.isEmpty()) {
            return;
        }

        Player builder = available.get((int) (Math.random() * available.size()));
        state.setCurrentBuilder(builder);
        GuessTheBuildPlot plot = state.getPlayerPlot(builder.getUniqueId());
        if (plot != null) {
            state.addPlayedPlot(plot);
        }
    }

    private void hidePlayers(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                             GuessTheBuildArenaState state) {
        Player builder = state.getCurrentBuilder();
        if (builder == null) {
            return;
        }

        List<Player> participants = getVisibilityParticipants(context);

        for (Player viewer : participants) {
            if (!viewer.isOnline()) {
                continue;
            }

            for (Player target : participants) {
                if (target.equals(viewer) || !target.isOnline()) {
                    continue;
                }

                if (viewer.equals(builder)) {
                    viewer.hidePlayer(target);
                    continue;
                }

                if (target.equals(builder)) {
                    viewer.showPlayer(target);
                    continue;
                }

                viewer.hidePlayer(target);
            }
        }
    }

    private void showPlayers(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                             GuessTheBuildArenaState state) {
        List<Player> participants = getVisibilityParticipants(context);
        for (Player player : participants) {
            if (!player.isOnline()) {
                continue;
            }
            for (Player other : participants) {
                if (other.isOnline()) {
                    player.showPlayer(other);
                }
            }
        }
    }

    private List<Player> getVisibilityParticipants(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        List<Player> participants = new ArrayList<>(context.getPlayers());
        for (Player spectator : context.getSpectators()) {
            if (!participants.contains(spectator)) {
                participants.add(spectator);
            }
        }
        return participants;
    }

    private int readBuildTimeSeconds(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        try {
            Object raw = context.getDataAccess().getGameData("basic.time", Object.class);
            if (raw instanceof Number number) {
                return Math.max(10, number.intValue());
            }
        } catch (Exception ignored) {
        }
        return 300;
    }
}
