package net.blueva.arcade.modules.guess_the_build.game;

import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.modules.guess_the_build.state.GuessTheBuildArenaState;
import net.blueva.arcade.modules.guess_the_build.state.GuessTheBuildPlot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

class GuessTheBuildPlotService {

    void loadPlot(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                  GuessTheBuildArenaState state) {
        if (context.getDataAccess() == null) {
            return;
        }

        World arenaWorld = context.getArenaAPI() != null ? context.getArenaAPI().getWorld() : null;

        Location min = context.getDataAccess().getGameLocation("game.plot.bounds.min");
        Location max = context.getDataAccess().getGameLocation("game.plot.bounds.max");

        if (min == null || max == null) {
            min = readLocationFallback(context, "game.plot.bounds.min", arenaWorld);
            max = readLocationFallback(context, "game.plot.bounds.max", arenaWorld);
        }

        if (min == null || max == null) {
            org.bukkit.Bukkit.getLogger().warning("[GuessTheBuild] Plot bounds not set for arena " + context.getArenaId());
            return;
        }

        if (min.getWorld() == null && arenaWorld != null) {
            min = new Location(arenaWorld, min.getX(), min.getY(), min.getZ());
        }
        if (max.getWorld() == null && arenaWorld != null) {
            max = new Location(arenaWorld, max.getX(), max.getY(), max.getZ());
        }

        String floorName = context.getDataAccess().getGameData("game.plot.floor", String.class);
        Material floorMaterial = Material.GRASS_BLOCK;
        if (floorName != null) {
            try {
                floorMaterial = Material.valueOf(floorName.toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        Location spawn = context.getDataAccess().getGameLocation("game.plot.spawn");
        if (spawn == null) {
            spawn = readLocationFallback(context, "game.plot.spawn", arenaWorld);
        }
        if (spawn == null) {
            spawn = calculatePlotCenter(min, max);
        }
        if (spawn.getWorld() == null && arenaWorld != null) {
            spawn = new Location(arenaWorld, spawn.getX(), spawn.getY(), spawn.getZ(), spawn.getYaw(), spawn.getPitch());
        }

        GuessTheBuildPlot plot = new GuessTheBuildPlot(min, max, floorMaterial, spawn);
        state.addPlot(plot);
        state.addPlotSpawn(spawn);
    }

    void assignPlayersToPlot(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                             GuessTheBuildArenaState state) {
        List<GuessTheBuildPlot> plots = state.getPlots();
        if (plots.isEmpty()) {
            return;
        }
        GuessTheBuildPlot plot = plots.get(0);
        for (Player player : context.getPlayers()) {
            state.setPlayerPlotSpawn(player.getUniqueId(), plot.getSpawn());
            state.setPlayerPlot(player.getUniqueId(), plot);
        }
    }

    void regeneratePlotFloor(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                             GuessTheBuildArenaState state) {
        if (context.getBlocksAPI() == null) {
            return;
        }
        List<GuessTheBuildPlot> plots = state.getPlots();
        if (plots.isEmpty()) {
            return;
        }
        regeneratePlotFloor(context, plots.get(0));
    }

    void clearPlot(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                   GuessTheBuildArenaState state) {
        if (context.getBlocksAPI() == null) {
            return;
        }
        List<GuessTheBuildPlot> plots = state.getPlots();
        if (plots.isEmpty()) {
            return;
        }
        GuessTheBuildPlot plot = plots.get(0);
        if (plot.getMin() == null || plot.getMax() == null) {
            return;
        }
        context.getBlocksAPI().setRegion(plot.getMin(), plot.getMax(), Material.AIR);
    }

    void teleportToPlot(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                        GuessTheBuildArenaState state,
                        Player player) {
        Location plot = state.getPlayerPlotSpawn(player.getUniqueId());
        if (plot == null) {
            org.bukkit.Bukkit.getLogger().warning("[GuessTheBuild] No plot spawn for player " + player.getName() + " in arena " + context.getArenaId());
            return;
        }
        if (plot.getWorld() == null) {
            org.bukkit.Bukkit.getLogger().warning("[GuessTheBuild] Plot spawn has no world for player " + player.getName() + " in arena " + context.getArenaId());
            return;
        }
        context.getSchedulerAPI().runAtEntity(player, () -> player.teleport(centerLocation(plot)));
    }

    void regeneratePlotFloor(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                             GuessTheBuildPlot plot) {
        if (context.getBlocksAPI() == null || plot.getFloorMaterial() == null || plot.getMin() == null || plot.getMax() == null) {
            return;
        }
        int minX = Math.min(plot.getMin().getBlockX(), plot.getMax().getBlockX());
        int minY = Math.min(plot.getMin().getBlockY(), plot.getMax().getBlockY());
        int minZ = Math.min(plot.getMin().getBlockZ(), plot.getMax().getBlockZ());
        int maxX = Math.max(plot.getMin().getBlockX(), plot.getMax().getBlockX());
        int maxZ = Math.max(plot.getMin().getBlockZ(), plot.getMax().getBlockZ());

        Location floorMin = new Location(plot.getMin().getWorld(), minX, minY, minZ);
        Location floorMax = new Location(plot.getMax().getWorld(), maxX, minY, maxZ);
        context.getBlocksAPI().setRegion(floorMin, floorMax, plot.getFloorMaterial());
    }

    static Location centerLocation(Location loc) {
        double centeredX = Math.floor(loc.getX()) + 0.5;
        double centeredZ = Math.floor(loc.getZ()) + 0.5;
        return new Location(loc.getWorld(), centeredX, loc.getY(), centeredZ, loc.getYaw(), loc.getPitch());
    }

    private Location readLocationFallback(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                          String path, World fallbackWorld) {
        Double x = context.getDataAccess().getGameData(path + ".x", Double.class);
        Double y = context.getDataAccess().getGameData(path + ".y", Double.class);
        Double z = context.getDataAccess().getGameData(path + ".z", Double.class);
        if (x == null || y == null || z == null) {
            return null;
        }
        Float yaw = context.getDataAccess().getGameData(path + ".yaw", Float.class);
        Float pitch = context.getDataAccess().getGameData(path + ".pitch", Float.class);
        if (yaw == null) yaw = 0.0f;
        if (pitch == null) pitch = 0.0f;
        return new Location(fallbackWorld, x, y, z, yaw, pitch);
    }

    private Location calculatePlotCenter(Location min, Location max) {
        double centerX = (min.getX() + max.getX()) / 2.0;
        double centerZ = (min.getZ() + max.getZ()) / 2.0;
        double centerY = Math.min(min.getY(), max.getY()) + 1.0;
        World world = min.getWorld() != null ? min.getWorld() : max.getWorld();
        return new Location(world, centerX, centerY, centerZ);
    }
}
