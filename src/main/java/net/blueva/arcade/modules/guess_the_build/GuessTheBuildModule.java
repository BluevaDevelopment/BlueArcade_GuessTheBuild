package net.blueva.arcade.modules.guess_the_build;

import net.blueva.arcade.api.ModuleAPI;
import net.blueva.arcade.api.achievements.AchievementsAPI;
import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.events.CustomEventRegistry;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GameModule;
import net.blueva.arcade.api.game.GameResult;
import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.stats.StatDefinition;
import net.blueva.arcade.api.stats.StatScope;
import net.blueva.arcade.api.stats.StatsAPI;
import net.blueva.arcade.api.setup.SetupRequirement;
import net.blueva.arcade.api.ui.MenuAPI;
import net.blueva.arcade.modules.guess_the_build.game.GuessTheBuildGame;
import net.blueva.arcade.modules.guess_the_build.listener.GuessTheBuildChatListener;
import net.blueva.arcade.modules.guess_the_build.listener.GuessTheBuildListener;
import net.blueva.arcade.modules.guess_the_build.setup.GuessTheBuildSetup;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.blueva.arcade.api.setup.ModuleSetupCommand;
import net.blueva.arcade.api.setup.ModuleSetupMetadata;
import net.blueva.arcade.api.setup.ModuleSetupStep;
import net.blueva.arcade.api.setup.ModuleSetupStatusCheck;
import java.util.List;

public class GuessTheBuildModule implements GameModule<Player, Location, World, Material, ItemStack, Sound, Block, Entity, Listener, EventPriority> {

    private ModuleConfigAPI moduleConfig;
    private CoreConfigAPI coreConfig;
    private ModuleInfo moduleInfo;
    private StatsAPI statsAPI;
    private MenuAPI<Player, Material> menuAPI;

    private GuessTheBuildGame game;

    @Override
    public void onLoad() {
        moduleInfo = ModuleAPI.getModuleInfo("guess_the_build");
        if (moduleInfo == null) {
            throw new IllegalStateException("ModuleInfo not available for GuessTheBuild module");
        }

        moduleConfig = ModuleAPI.getModuleConfig(moduleInfo.getId());
        coreConfig = ModuleAPI.getCoreConfig();
        statsAPI = ModuleAPI.getStatsAPI();

        registerConfigs();
        registerStats();
        registerAchievements();

        MenuAPI<Player, Material> menuAPI = ModuleAPI.getMenuAPI();
        this.menuAPI = menuAPI;

        game = new GuessTheBuildGame(moduleInfo, moduleConfig, coreConfig, statsAPI, menuAPI);

        if (menuAPI != null) {
            menuAPI.registerModuleActionHandler(moduleInfo.getId(), (player, payload) -> {
                if (player == null || payload == null || payload.isBlank()) {
                    return false;
                }
                String[] parts = payload.trim().split("\\s+");
                if (parts.length >= 2 && "theme".equalsIgnoreCase(parts[0])) {
                    if (game.getThemeService().handleThemeAction(player, parts[1])) {
                        return true;
                    }
                }
                return false;
            });
        }

        ModuleAPI.getSetupAPI().registerHandler(moduleInfo.getId(), new GuessTheBuildSetup(moduleConfig));
    }

    @Override
    public void onStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        game.startGame(context);
    }

    @Override
    public void onCountdownTick(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                int secondsLeft) {
        game.handleCountdownTick(context, secondsLeft);
    }

    @Override
    public void onCountdownFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        game.handleCountdownFinish(context);
    }

    @Override
    public boolean freezePlayersOnCountdown() {
        return false;
    }

    @Override
    public Set<SetupRequirement> getDisabledRequirements() {
        return Set.of(SetupRequirement.SPAWNS);
    }

    @Override
    public void onGameStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        game.beginPlaying(context);
    }

    @Override
    public void onEnd(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                      GameResult<Player> result) {
        game.finishGame(context);
    }

    @Override
    public void onDisable() {
        if (game != null) {
            game.shutdown();
        }
        if (menuAPI != null && moduleInfo != null) {
            menuAPI.unregisterModuleActionHandler(moduleInfo.getId());
        }
    }

    @Override
    public void registerEvents(CustomEventRegistry<Listener, EventPriority> registry) {
        registry.register(new GuessTheBuildListener(game));
        registry.register(new GuessTheBuildChatListener(game));
    }

    @Override
    public Map<String, String> getCustomPlaceholders(Player player) {
        if (game == null || player == null) {
            return new HashMap<>();
        }
        return game.getCustomPlaceholders(player);
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

    private void registerConfigs() {
        moduleConfig.register("settings.yml");
        moduleConfig.register("achievements.yml");
        moduleConfig.register("store.yml");
    }

    private void registerStats() {
        if (statsAPI == null) {
            return;
        }

        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("wins", moduleConfig.getTranslation(null, "stats.labels.wins"),
                        moduleConfig.getTranslation(null, "stats.descriptions.wins"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("games_played", moduleConfig.getTranslation(null, "stats.labels.games_played"),
                        moduleConfig.getTranslation(null, "stats.descriptions.games_played"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("points_total", moduleConfig.getTranslation(null, "stats.labels.points_total"),
                        moduleConfig.getTranslation(null, "stats.descriptions.points_total"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("points_highest", moduleConfig.getTranslation(null, "stats.labels.points_highest"),
                        moduleConfig.getTranslation(null, "stats.descriptions.points_highest"), StatScope.MODULE));
    }

    private void registerAchievements() {
        AchievementsAPI achievementsAPI = ModuleAPI.getAchievementsAPI();
        if (achievementsAPI != null) {
            achievementsAPI.registerModuleAchievements(moduleInfo.getId(), "achievements.yml");
        }
    }


    @Override
    public boolean requiresSpawnCapacityValidation() {
        return false;
    }

    @Override
    public ModuleSetupMetadata getSetupMetadata() {
        return new ModuleSetupMetadata() {

            @Override
            public List<ModuleSetupStep> getSetupSteps() {
                return List.of(
                        new ModuleSetupStep("plot", true, "Configure Plot", "Configure the module-specific plot setup data.", List.of("/baa game <arena> guess_the_build plot"), "plot region and spawn")
                );
            }

            @Override
            public List<ModuleSetupCommand> getSetupCommands() {
                return List.of(
                        new ModuleSetupCommand("plot", "/baa game <arena> guess_the_build plot", "Configure plot setup data.", true)
                );
            }

            @Override
            public List<ModuleSetupStatusCheck<?, ?, ?>> getStatusChecks() {
                return List.of(
                        new ModuleSetupStatusCheck<>("plot", true, "Create at least one plot.", context -> context.getData().getInt("game.plots.total", 0) > 0 || context.getData().has("game.plot.bounds.min.x"))
                );
            }
        };
    }

}
