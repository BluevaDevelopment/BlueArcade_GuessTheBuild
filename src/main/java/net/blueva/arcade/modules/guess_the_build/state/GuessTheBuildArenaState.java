package net.blueva.arcade.modules.guess_the_build.state;

import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.modules.guess_the_build.game.GuessTheBuildThemeService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GuessTheBuildArenaState {

    private final GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context;

    private final List<GuessTheBuildPlot> plots = new ArrayList<>();
    private final List<Location> plotSpawns = new ArrayList<>();
    private final Map<UUID, Location> playerPlotSpawns = new HashMap<>();
    private final Map<UUID, GuessTheBuildPlot> playerPlots = new HashMap<>();
    private final Map<UUID, Integer> playerPoints = new HashMap<>();

    private GuessTheBuildPhase phase = GuessTheBuildPhase.THEME_SELECTION;
    private int round = 1;
    private int buildTimeLeft = 0;
    private int themeSelectionTimeLeft = 0;
    private int roundDelayTimeLeft = 0;

    private GuessTheBuildPlot currentBuildPlot = null;
    private Player currentBuilder = null;
    private String currentTheme = null;
    private List<String> currentThemeSynonyms = new ArrayList<>();
    private final List<Player> whoGuessed = new ArrayList<>();
    private final List<Integer> revealedLetterIndices = new ArrayList<>();
    private final List<GuessTheBuildPlot> playedPlots = new ArrayList<>();
    private final List<String> playedThemes = new ArrayList<>();
    private GuessTheBuildThemeService.Difficulty currentThemeDifficulty = null;
    private boolean themeSelected = false;
    private boolean ended = false;

    private final Set<UUID> floorChangeMode = new HashSet<>();
    private final Map<UUID, Long> plotTimeOverrides = new HashMap<>();
    private final Map<UUID, org.bukkit.WeatherType> plotWeatherOverrides = new HashMap<>();

    public GuessTheBuildArenaState(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        this.context = context;
    }

    public GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> getContext() {
        return context;
    }

    public List<GuessTheBuildPlot> getPlots() {
        return plots;
    }

    public void addPlot(GuessTheBuildPlot plot) {
        plots.add(plot);
    }

    public void addPlotSpawn(Location spawn) {
        plotSpawns.add(spawn);
    }

    public void setPlayerPlotSpawn(UUID playerId, Location spawn) {
        playerPlotSpawns.put(playerId, spawn);
    }

    public Location getPlayerPlotSpawn(UUID playerId) {
        return playerPlotSpawns.get(playerId);
    }

    public void setPlayerPlot(UUID playerId, GuessTheBuildPlot plot) {
        playerPlots.put(playerId, plot);
    }

    public GuessTheBuildPlot getPlayerPlot(UUID playerId) {
        return playerPlots.get(playerId);
    }

    public GuessTheBuildPhase getPhase() {
        return phase;
    }

    public void setPhase(GuessTheBuildPhase phase) {
        this.phase = phase;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public int getBuildTimeLeft() {
        return buildTimeLeft;
    }

    public void setBuildTimeLeft(int buildTimeLeft) {
        this.buildTimeLeft = buildTimeLeft;
    }

    public void decrementBuildTime() {
        this.buildTimeLeft--;
    }

    public int getThemeSelectionTimeLeft() {
        return themeSelectionTimeLeft;
    }

    public void setThemeSelectionTimeLeft(int themeSelectionTimeLeft) {
        this.themeSelectionTimeLeft = themeSelectionTimeLeft;
    }

    public void decrementThemeSelectionTime() {
        this.themeSelectionTimeLeft--;
    }

    public int getRoundDelayTimeLeft() {
        return roundDelayTimeLeft;
    }

    public void setRoundDelayTimeLeft(int roundDelayTimeLeft) {
        this.roundDelayTimeLeft = roundDelayTimeLeft;
    }

    public void decrementRoundDelayTime() {
        this.roundDelayTimeLeft--;
    }

    public GuessTheBuildPlot getCurrentBuildPlot() {
        return currentBuildPlot;
    }

    public void setCurrentBuildPlot(GuessTheBuildPlot currentBuildPlot) {
        this.currentBuildPlot = currentBuildPlot;
    }

    public Player getCurrentBuilder() {
        return currentBuilder;
    }

    public void setCurrentBuilder(Player currentBuilder) {
        this.currentBuilder = currentBuilder;
    }

    public String getCurrentTheme() {
        return currentTheme;
    }

    public void setCurrentTheme(String currentTheme) {
        this.currentTheme = currentTheme;
    }

    public List<String> getCurrentThemeSynonyms() {
        return currentThemeSynonyms;
    }

    public void setCurrentThemeSynonyms(List<String> currentThemeSynonyms) {
        this.currentThemeSynonyms = currentThemeSynonyms != null ? currentThemeSynonyms : new ArrayList<>();
    }

    public List<Player> getWhoGuessed() {
        return whoGuessed;
    }

    public void addWhoGuessed(Player player) {
        whoGuessed.add(player);
    }

    public boolean hasGuessed(Player player) {
        return whoGuessed.contains(player);
    }

    public List<Integer> getRevealedLetterIndices() {
        return revealedLetterIndices;
    }

    public void clearRevealedLetters() {
        revealedLetterIndices.clear();
    }

    public List<GuessTheBuildPlot> getPlayedPlots() {
        return playedPlots;
    }

    public void addPlayedPlot(GuessTheBuildPlot plot) {
        playedPlots.add(plot);
    }

    public void clearPlayedPlots() {
        playedPlots.clear();
    }

    public List<String> getPlayedThemes() {
        return playedThemes;
    }

    public void addPlayedTheme(String theme) {
        playedThemes.add(theme);
    }

    public GuessTheBuildThemeService.Difficulty getCurrentThemeDifficulty() {
        return currentThemeDifficulty;
    }

    public void setCurrentThemeDifficulty(GuessTheBuildThemeService.Difficulty difficulty) {
        this.currentThemeDifficulty = difficulty;
    }

    public boolean isThemeSelected() {
        return themeSelected;
    }

    public void setThemeSelected(boolean themeSelected) {
        this.themeSelected = themeSelected;
    }

    public boolean isEnded() {
        return ended;
    }

    public void markEnded() {
        this.ended = true;
    }

    public void addPoints(UUID playerId, int points) {
        playerPoints.merge(playerId, points, Integer::sum);
    }

    public int getPoints(UUID playerId) {
        return playerPoints.getOrDefault(playerId, 0);
    }

    public Map<UUID, Integer> getPlayerPoints() {
        return playerPoints;
    }

    public void addFloorChangeMode(UUID playerId) {
        floorChangeMode.add(playerId);
    }

    public void removeFloorChangeMode(UUID playerId) {
        floorChangeMode.remove(playerId);
    }

    public boolean isInFloorChangeMode(UUID playerId) {
        return floorChangeMode.contains(playerId);
    }

    public void setPlotTimeOverride(UUID playerId, long ticks) {
        plotTimeOverrides.put(playerId, ticks);
    }

    public void setPlotWeatherOverride(UUID playerId, org.bukkit.WeatherType weather) {
        plotWeatherOverrides.put(playerId, weather);
    }

    public void clearPlayerState(UUID playerId) {
        floorChangeMode.remove(playerId);
        plotTimeOverrides.remove(playerId);
        plotWeatherOverrides.remove(playerId);
    }

    public void resetRoundState() {
        whoGuessed.clear();
        revealedLetterIndices.clear();
        currentTheme = null;
        currentThemeSynonyms.clear();
        currentThemeDifficulty = null;
        currentBuildPlot = null;
        currentBuilder = null;
        themeSelected = false;
    }
}
