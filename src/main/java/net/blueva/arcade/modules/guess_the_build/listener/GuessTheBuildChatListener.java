package net.blueva.arcade.modules.guess_the_build.listener;

import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GamePhase;
import net.blueva.arcade.modules.guess_the_build.game.GuessTheBuildGame;
import net.blueva.arcade.modules.guess_the_build.state.GuessTheBuildArenaState;
import net.blueva.arcade.modules.guess_the_build.state.GuessTheBuildPhase;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;

public class GuessTheBuildChatListener implements Listener {

    private final GuessTheBuildGame game;

    public GuessTheBuildChatListener(GuessTheBuildGame game) {
        this.game = game;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = game.getContext(player);
        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }
        if (context.getPhase() != GamePhase.PLAYING) {
            return;
        }

        GuessTheBuildArenaState state = game.getArenaState(context);
        if (state == null || state.getPhase() != GuessTheBuildPhase.BUILDING) {
            return;
        }

        if (player.equals(state.getCurrentBuilder())) {
            event.setCancelled(true);
            String msg = game.getModuleConfig().getStringFrom("language.yml", "game.cant_talk_builder");
            if (msg != null) {
                context.getMessagesAPI().sendRaw(player, msg);
            }
            return;
        }

        if (state.hasGuessed(player)) {
            event.setCancelled(true);
            String msg = game.getModuleConfig().getStringFrom("language.yml", "game.cant_talk_guessed");
            if (msg != null) {
                context.getMessagesAPI().sendRaw(player, msg);
            }
            return;
        }

        String message = event.getMessage().trim();
        if (message.isEmpty()) {
            return;
        }

        if (state.getCurrentThemeSynonyms().stream().anyMatch(theme -> theme.equalsIgnoreCase(message))) {
            event.setCancelled(true);
            context.getSchedulerAPI().runLater("gtb_chat_guess_" + player.getUniqueId(), () -> {
                game.handleCorrectGuess(context, state, player);
            }, 0L);
        }
    }
}
