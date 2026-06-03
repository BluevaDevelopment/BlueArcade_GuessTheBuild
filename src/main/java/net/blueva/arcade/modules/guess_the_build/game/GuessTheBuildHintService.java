package net.blueva.arcade.modules.guess_the_build.game;

import net.blueva.arcade.api.ModuleAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.ui.MessageAPI;
import net.blueva.arcade.modules.guess_the_build.state.GuessTheBuildArenaState;
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

public class GuessTheBuildHintService {

    private final ModuleConfigAPI moduleConfig;
    private boolean characterCountBroadcasted = false;

    public GuessTheBuildHintService(ModuleConfigAPI moduleConfig) {
        this.moduleConfig = moduleConfig;
    }

    public void reset() {
        characterCountBroadcasted = false;
    }

    public void tick(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                     GuessTheBuildArenaState state) {
        int timeLeft = state.getBuildTimeLeft();
        if (timeLeft <= 0) {
            return;
        }

        int charCountBroadcastAt = moduleConfig.getIntFrom("settings.yml", "timers.character_count_broadcast_seconds", 90);
        int hintRevealStart = moduleConfig.getIntFrom("settings.yml", "timers.hint_reveal_start_seconds", 70);
        int hintInterval = moduleConfig.getIntFrom("settings.yml", "timers.hint_reveal_interval_seconds", 10);

        String theme = state.getCurrentTheme();
        if (theme == null || theme.isEmpty()) {
            return;
        }

        if (!characterCountBroadcasted && timeLeft <= charCountBroadcastAt) {
            characterCountBroadcasted = true;
            String msg = moduleConfig.getStringFrom("language.yml", "game.character_count");
            if (msg != null) {
                msg = msg.replace("{count}", String.valueOf(countLetters(theme)));
                for (Player player : context.getPlayers()) {
                    if (player.isOnline()) {
                        context.getMessagesAPI().sendRaw(player, msg);
                    }
                }
            }
        }

        if (timeLeft <= hintRevealStart && timeLeft % hintInterval == 0) {
            revealRandomLetter(state, theme);
        }

        sendActionBars(context, state, theme);
    }

    private void revealRandomLetter(GuessTheBuildArenaState state, String theme) {
        int themeLength = theme.length();
        List<Integer> hidden = new ArrayList<>();
        for (int i = 0; i < themeLength; i++) {
            char c = theme.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (!state.getRevealedLetterIndices().contains(i)) {
                hidden.add(i);
            }
        }

        if (hidden.size() > 2) {
            int idx = hidden.get((int) (Math.random() * hidden.size()));
            state.getRevealedLetterIndices().add(idx);
        }
    }

    private void sendActionBars(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                GuessTheBuildArenaState state, String theme) {
        String timeStr = formatTime(state.getBuildTimeLeft());

        String builderMessage = moduleConfig.getStringFrom("language.yml", "game.action_bar.builder");
        if (builderMessage == null) {
            builderMessage = "<aqua>You are building:</aqua> <green>{theme}</green> <dark_gray>•</dark_gray> <aqua>Time:</aqua> <yellow>{time}</yellow>";
        }
        builderMessage = builderMessage.replace("{theme}", theme).replace("{time}", timeStr);

        String guessedMessage = moduleConfig.getStringFrom("language.yml", "game.action_bar.guessed");
        if (guessedMessage == null) {
            guessedMessage = "<aqua>Theme:</aqua> <green>{theme}</green> <dark_gray>•</dark_gray> <aqua>Status:</aqua> <green>Guessed!</green>";
        }
        guessedMessage = guessedMessage.replace("{theme}", theme);

        String guessingTemplate = moduleConfig.getStringFrom("language.yml", "game.action_bar.guessing");
        if (guessingTemplate == null) {
            guessingTemplate = "<aqua>Theme:</aqua> <white>{hint}</white> <dark_gray>•</dark_gray> <aqua>Time:</aqua> <yellow>{time}</yellow>";
        }
        String hintString = buildHintString(theme, state.getRevealedLetterIndices());
        String guessingMessage = guessingTemplate.replace("{hint}", hintString).replace("{time}", timeStr);

        @SuppressWarnings("unchecked")
        MessageAPI<Player> messagesAPI = (MessageAPI<Player>) ModuleAPI.getMessagesAPI();

        Player builder = state.getCurrentBuilder();
        for (Player player : context.getPlayers()) {
            if (!player.isOnline()) {
                continue;
            }
            if (player.equals(builder)) {
                if (messagesAPI != null) {
                    messagesAPI.sendActionBar(player, builderMessage);
                }
            } else if (state.hasGuessed(player)) {
                if (messagesAPI != null) {
                    messagesAPI.sendActionBar(player, guessedMessage);
                }
            } else {
                if (messagesAPI != null) {
                    messagesAPI.sendActionBar(player, guessingMessage);
                }
            }
        }
    }

    private static String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public static String buildHintString(String theme, List<Integer> revealedIndices) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < theme.length(); i++) {
            char c = theme.charAt(i);
            if (Character.isWhitespace(c)) {
                sb.append("  ");
            } else if (revealedIndices.contains(i)) {
                sb.append(c).append(' ');
            } else {
                sb.append("_ ");
            }
        }
        return sb.toString().trim();
    }

    private int countLetters(String theme) {
        int count = 0;
        for (char c : theme.toCharArray()) {
            if (!Character.isWhitespace(c)) {
                count++;
            }
        }
        return count;
    }
}
