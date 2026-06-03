package net.blueva.arcade.modules.guess_the_build.state;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public class GuessTheBuildPlot {

    private final Location min;
    private final Location max;
    private final Material floorMaterial;
    private final Location spawn;
    private int points = 0;

    public GuessTheBuildPlot(Location min, Location max, Material floorMaterial, Location spawn) {
        this.min = min;
        this.max = max;
        this.floorMaterial = floorMaterial;
        this.spawn = spawn;
    }

    public Location getMin() {
        return min;
    }

    public Location getMax() {
        return max;
    }

    public Material getFloorMaterial() {
        return floorMaterial;
    }

    public Location getSpawn() {
        return spawn;
    }

    public int getPoints() {
        return points;
    }

    public void addPoints(int points) {
        this.points += points;
    }

    public boolean isInside(Location location) {
        if (location == null || min == null || max == null) {
            return false;
        }
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        return x >= Math.min(min.getX(), max.getX()) && x <= Math.max(min.getX(), max.getX())
                && y >= Math.min(min.getY(), max.getY()) && y <= Math.max(min.getY(), max.getY())
                && z >= Math.min(min.getZ(), max.getZ()) && z <= Math.max(min.getZ(), max.getZ());
    }

    public Location findSafeTeleport() {
        if (min == null || max == null || min.getWorld() == null) {
            return spawn;
        }
        World world = min.getWorld();
        int centerX = (min.getBlockX() + max.getBlockX()) / 2;
        int centerZ = (min.getBlockZ() + max.getBlockZ()) / 2;
        int minY = Math.min(min.getBlockY(), max.getBlockY());
        int maxY = Math.max(min.getBlockY(), max.getBlockY());

        for (int y = minY + 1; y < maxY; y++) {
            Block current = world.getBlockAt(centerX, y, centerZ);
            Block above = world.getBlockAt(centerX, y + 1, centerZ);
            if (isAir(current) && isAir(above)) {
                return new Location(world, centerX + 0.5, y, centerZ + 0.5);
            }
        }

        return new Location(world, centerX + 0.5, minY + 1.0, centerZ + 0.5);
    }

    private boolean isAir(Block block) {
        if (block == null) {
            return false;
        }
        Material type = block.getType();
        return type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR;
    }
}
