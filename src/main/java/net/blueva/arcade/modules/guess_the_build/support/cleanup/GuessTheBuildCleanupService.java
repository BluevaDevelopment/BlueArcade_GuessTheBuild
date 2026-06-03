package net.blueva.arcade.modules.guess_the_build.support.cleanup;

import net.blueva.arcade.api.game.GameContext;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class GuessTheBuildCleanupService {

    public void resetWorldDefaults(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        if (context == null || context.getArenaAPI() == null) {
            return;
        }
        World world = context.getArenaAPI().getWorld();
        if (world == null) {
            return;
        }
        world.setTime(1000L);
        world.setStorm(false);
        world.setThundering(false);
    }

    public void resetPlayerStates(List<Player> players) {
        if (players == null) {
            return;
        }
        Attribute maxHealthAttribute = maxHealthAttribute();
        for (Player player : players) {
            if (player == null) {
                continue;
            }
            player.setGameMode(GameMode.SURVIVAL);
            player.resetPlayerTime();
            player.resetPlayerWeather();
            if (maxHealthAttribute != null && player.getAttribute(maxHealthAttribute) != null) {
                player.getAttribute(maxHealthAttribute).setBaseValue(20.0);
            }
            player.setHealth(Math.min(player.getHealth(), 20.0));
        }
    }

    public void clearPlayerInventories(List<Player> players) {
        if (players == null) {
            return;
        }
        for (Player player : players) {
            if (player == null) {
                continue;
            }
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setExtraContents(null);
            player.updateInventory();
        }
    }

    private Attribute maxHealthAttribute() {
        Attribute attribute = attributeConstant("MAX_HEALTH");
        return attribute != null ? attribute : attributeConstant("GENERIC_MAX_HEALTH");
    }

    private Attribute attributeConstant(String fieldName) {
        try {
            Object value = Attribute.class.getField(fieldName).get(null);
            return value instanceof Attribute attr ? attr : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
