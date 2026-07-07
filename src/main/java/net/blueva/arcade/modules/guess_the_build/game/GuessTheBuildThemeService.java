package net.blueva.arcade.modules.guess_the_build.game;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.ui.MenuAPI;
import net.blueva.arcade.api.ui.menu.BedrockButtonDefinition;
import net.blueva.arcade.api.ui.menu.BedrockMenuDefinition;
import net.blueva.arcade.api.ui.menu.BedrockSimpleMenuDefinition;
import net.blueva.arcade.api.ui.menu.JavaItemDefinition;
import net.blueva.arcade.api.ui.menu.JavaMenuItem;
import net.blueva.arcade.api.ui.menu.MenuDefinition;
import net.blueva.arcade.modules.guess_the_build.state.GuessTheBuildArenaState;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class GuessTheBuildThemeService {

    private final ModuleConfigAPI moduleConfig;
    private final MenuAPI<Player, Material> menuAPI;
    private final String moduleId;
    private final Random random = new Random();
    private final Map<UUID, PendingSelection> pendingSelections = new ConcurrentHashMap<>();

    public GuessTheBuildThemeService(ModuleConfigAPI moduleConfig,
                                     MenuAPI<Player, Material> menuAPI,
                                     String moduleId) {
        this.moduleConfig = moduleConfig;
        this.menuAPI = menuAPI;
        this.moduleId = moduleId;
    }

    public void openThemeSelectionMenu(Player builder, GuessTheBuildArenaState state, Consumer<GuessTheme> onSelect) {
        if (builder == null || !builder.isOnline()) {
            return;
        }

        GuessTheme easy = getRandomTheme(Difficulty.EASY, state.getPlayedThemes());
        GuessTheme medium = getRandomTheme(Difficulty.MEDIUM, state.getPlayedThemes());
        GuessTheme hard = getRandomTheme(Difficulty.HARD, state.getPlayedThemes());

        pendingSelections.put(builder.getUniqueId(), new PendingSelection(easy, medium, hard, onSelect));

        MenuDefinition<Material> menu = buildMenu(builder, easy, medium, hard);
        if (menuAPI != null) {
            menuAPI.openMenu(builder, menu, Collections.emptyMap());
        }
    }

    public boolean handleThemeAction(Player player, String action) {
        if (player == null || action == null || action.isBlank()) {
            return false;
        }
        String diffStr = action.toLowerCase(Locale.ROOT).trim();
        PendingSelection pending = pendingSelections.remove(player.getUniqueId());
        if (pending == null) {
            return false;
        }

        GuessTheme selected = switch (diffStr) {
            case "easy" -> pending.easy;
            case "medium" -> pending.medium;
            case "hard" -> pending.hard;
            default -> null;
        };

        if (selected == null) {
            return false;
        }
        if (pending.onSelect != null) {
            pending.onSelect.accept(selected);
        }
        return true;
    }

    public void clearPendingSelection(UUID playerId) {
        if (playerId == null) {
            pendingSelections.clear();
            return;
        }
        pendingSelections.remove(playerId);
    }

    public GuessTheme forceRandomTheme(GuessTheBuildArenaState state) {
        Difficulty difficulty = Difficulty.values()[random.nextInt(Difficulty.values().length)];
        return getRandomTheme(difficulty, state.getPlayedThemes());
    }

    public GuessTheme getRandomTheme(Difficulty difficulty, List<String> playedThemes) {
        List<String> themes = moduleConfig.getStringListFrom("settings.yml", "themes." + difficulty.name().toLowerCase(Locale.ROOT));
        if (themes == null || themes.isEmpty()) {
            themes = List.of("House");
        }

        List<String> available = new ArrayList<>(themes);
        available.removeAll(playedThemes);
        if (available.isEmpty()) {
            available = new ArrayList<>(themes);
        }

        String selected = available.get(random.nextInt(available.size()));
        List<String> names = new ArrayList<>(Arrays.asList(selected.split("\\s*,\\s*")));
        return new GuessTheme(names, difficulty);
    }

    private MenuDefinition<Material> buildMenu(Player player, GuessTheme easy, GuessTheme medium, GuessTheme hard) {
        String title = moduleConfig.getTranslation(player, "theme_selection.menu.title");
        if (title == null) {
            title = "<bold><gradient:aqua:light_purple>Select a Theme</gradient></bold>";
        }

        List<JavaMenuItem<Material>> items = new ArrayList<>();

        items.add(JavaMenuItem.of(11, buildItemDefinition(player, easy, "theme_selection.easy")));
        items.add(JavaMenuItem.of(13, buildItemDefinition(player, medium, "theme_selection.medium")));
        items.add(JavaMenuItem.of(15, buildItemDefinition(player, hard, "theme_selection.hard")));

        for (int i = 0; i < 27; i++) {
            if (i != 11 && i != 13 && i != 15) {
                items.add(JavaMenuItem.of(i, JavaItemDefinition.of(
                        Material.GRAY_STAINED_GLASS_PANE,
                        1,
                        " ",
                        List.of(),
                        List.of()
                )));
            }
        }

        BedrockMenuDefinition bedrockMenu = buildBedrockMenu(player, easy, medium, hard);
        return new MenuDefinition<>(title, 27, items, bedrockMenu);
    }

    private JavaItemDefinition<Material> buildItemDefinition(Player player, GuessTheme theme, String labelKey) {
        String difficultyLabel = moduleConfig.getTranslation(player, labelKey);
        String name = (difficultyLabel != null ? difficultyLabel : theme.difficulty.name())
                .replace("{theme}", theme.names.get(0))
                .replace("{points}", String.valueOf(theme.difficulty.getPointsReward()));

        List<String> lore = new ArrayList<>();
        String themeStr = String.join(", ", theme.names);
        lore.add("<gray>" + themeStr + "</gray>");
        lore.add("");
        String pointsLore = moduleConfig.getTranslation(player, "theme_selection.points_lore");
        if (pointsLore != null) {
            lore.add(pointsLore.replace("{points}", String.valueOf(theme.difficulty.getPointsReward())));
        }

        return JavaItemDefinition.of(
                Material.PAPER,
                1,
                name,
                lore,
                List.of("MODULE;" + moduleId + ";theme " + theme.difficulty.name().toLowerCase(Locale.ROOT))
        );
    }

    private BedrockMenuDefinition buildBedrockMenu(Player player, GuessTheme easy, GuessTheme medium, GuessTheme hard) {
        String title = moduleConfig.getTranslation(player, "theme_selection.menu.title");
        if (title == null) {
            title = "<bold><gradient:aqua:light_purple>Select a Theme</gradient></bold>";
        }
        List<String> content = List.of("<gray>Select a difficulty to reveal your secret theme.</gray>");

        List<BedrockButtonDefinition> buttons = new ArrayList<>();
        buttons.add(buildBedrockButton(player, easy, "theme_selection.easy"));
        buttons.add(buildBedrockButton(player, medium, "theme_selection.medium"));
        buttons.add(buildBedrockButton(player, hard, "theme_selection.hard"));

        return new BedrockSimpleMenuDefinition(title, content, buttons);
    }

    private BedrockButtonDefinition buildBedrockButton(Player player, GuessTheme theme, String labelKey) {
        String difficultyLabel = moduleConfig.getTranslation(player, labelKey);
        String name = (difficultyLabel != null ? difficultyLabel : theme.difficulty.name())
                .replace("{theme}", theme.names.get(0))
                .replace("{points}", String.valueOf(theme.difficulty.getPointsReward()));

        return BedrockButtonDefinition.of(
                name,
                null,
                List.of("MODULE;" + moduleId + ";theme " + theme.difficulty.name().toLowerCase(Locale.ROOT))
        );
    }

    public record GuessTheme(List<String> names, Difficulty difficulty) {
        public String getPrimaryName() {
            return names.isEmpty() ? "" : names.get(0);
        }
    }

    public enum Difficulty {
        EASY(1), MEDIUM(2), HARD(3);

        private final int pointsReward;

        Difficulty(int pointsReward) {
            this.pointsReward = pointsReward;
        }

        public int getPointsReward() {
            return pointsReward;
        }
    }

    private record PendingSelection(GuessTheme easy, GuessTheme medium, GuessTheme hard,
                                    Consumer<GuessTheme> onSelect) {
    }
}
