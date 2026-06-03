package net.blueva.arcade.modules.guess_the_build.listener;

import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GamePhase;
import net.blueva.arcade.modules.guess_the_build.game.GuessTheBuildGame;
import net.blueva.arcade.modules.guess_the_build.state.GuessTheBuildArenaState;
import net.blueva.arcade.modules.guess_the_build.state.GuessTheBuildPhase;
import net.blueva.arcade.modules.guess_the_build.state.GuessTheBuildPlot;
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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

public class GuessTheBuildListener implements Listener {

    private final GuessTheBuildGame game;

    public GuessTheBuildListener(GuessTheBuildGame game) {
        this.game = game;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = game.getContext(player);
        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }
        if (event.getTo() == null) {
            return;
        }
        if (context.getPhase() != GamePhase.PLAYING) {
            return;
        }

        GuessTheBuildArenaState state = game.getArenaState(context);
        if (!context.isInsideBounds(event.getTo())) {
            Location spawn = state != null ? state.getPlayerPlotSpawn(player.getUniqueId()) : null;
            if (spawn != null) {
                player.teleport(spawn);
            } else {
                context.respawnPlayer(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = game.getContext(player);
        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }

        GuessTheBuildArenaState state = game.getArenaState(context);
        if (state == null || state.getPhase() != GuessTheBuildPhase.BUILDING) {
            event.setCancelled(true);
            return;
        }

        if (!player.equals(state.getCurrentBuilder())) {
            event.setCancelled(true);
            return;
        }

        GuessTheBuildPlot plot = state.getCurrentBuildPlot();
        if (plot != null) {
            if (!plot.isInside(event.getBlock().getLocation())) {
                event.setCancelled(true);
            }
        } else if (!context.isInsideBounds(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = game.getContext(player);
        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }

        GuessTheBuildArenaState state = game.getArenaState(context);
        if (state == null || state.getPhase() != GuessTheBuildPhase.BUILDING) {
            event.setCancelled(true);
            return;
        }

        if (!player.equals(state.getCurrentBuilder())) {
            event.setCancelled(true);
            return;
        }

        if (state.isInFloorChangeMode(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        GuessTheBuildPlot plot = state.getCurrentBuildPlot();
        if (plot != null) {
            if (!plot.isInside(event.getBlock().getLocation())) {
                event.setCancelled(true);
            }
        } else if (!context.isInsideBounds(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }

        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = game.getContext(target);
        if (context == null || !context.isPlayerPlaying(target)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = game.getContext(player);
        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = game.getContext(player);
        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }

        GuessTheBuildArenaState state = game.getArenaState(context);
        if (state == null) {
            return;
        }

    }
}
