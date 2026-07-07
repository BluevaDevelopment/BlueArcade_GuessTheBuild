package net.blueva.arcade.modules.guess_the_build.setup;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.setup.GameSetupHandler;
import net.blueva.arcade.api.setup.SetupContext;
import net.blueva.arcade.api.setup.TabCompleteContext;
import net.blueva.arcade.api.setup.TabCompleteResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class GuessTheBuildSetup implements GameSetupHandler {

    private final ModuleConfigAPI moduleConfig;

    public GuessTheBuildSetup(ModuleConfigAPI moduleConfig) {
        this.moduleConfig = moduleConfig;
    }

    @Override
    public boolean handle(SetupContext context) {
        return handleInternal(castSetupContext(context));
    }

    private boolean handleInternal(SetupContext<Player, CommandSender, Location> context) {
        String subcommand = context.getArg(context.getStartIndex() - 1);
        if ("plot".equalsIgnoreCase(subcommand)) {
            return handlePlot(context);
        }
        return false;
    }

    @Override
    public TabCompleteResult tabComplete(TabCompleteContext context) {
        return tabCompleteInternal(castTabContext(context));
    }

    private TabCompleteResult tabCompleteInternal(TabCompleteContext<Player, CommandSender> context) {
        String subcommand = context.getArg(context.getStartIndex() - 1);

        if ("plot".equalsIgnoreCase(subcommand)) {
            if (context.getRelativeArgIndex() == 0) {
                return TabCompleteResult.of("set", "spawn");
            }
        }

        return TabCompleteResult.empty();
    }

    @Override
    public List<String> getSubcommands() {
        return List.of("plot");
    }

    private boolean handlePlot(SetupContext<Player, CommandSender, Location> context) {
        String action = context.getHandlerArg(0);
        if ("set".equalsIgnoreCase(action)) {
            return handlePlotSet(context);
        }
        if ("spawn".equalsIgnoreCase(action)) {
            return handlePlotSpawnSet(context);
        }
        context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage(context.getPlayer(), "plot.usage"));
        return true;
    }

    private boolean handlePlotSet(SetupContext<Player, CommandSender, Location> context) {
        Player player = context.getPlayer();
        if (player == null) {
            return true;
        }

        if (!context.getSelection().hasCompleteSelection(player)) {
            context.getMessagesAPI().sendRaw(player, getSetupMessage(context.getPlayer(), "plot.set.must_use_stick"));
            return true;
        }

        Location pos1 = context.getSelection().getPosition1(player);
        Location pos2 = context.getSelection().getPosition2(player);

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        int height = maxY - minY + 1;

        if (height < 3) {
            context.getMessagesAPI().sendRaw(player, getSetupMessage(context.getPlayer(), "plot.set.too_shallow"));
            return true;
        }

        World world = pos1.getWorld() != null ? pos1.getWorld() : pos2.getWorld();
        if (world == null) {
            return true;
        }

        int floorLayers = countFloorLayers(world, minX, maxX, minY, maxY, minZ, maxZ);
        if (floorLayers == 0) {
            context.getMessagesAPI().sendRaw(player, getSetupMessage(context.getPlayer(), "plot.set.no_floor"));
            return true;
        }
        if (floorLayers > 2) {
            context.getMessagesAPI().sendRaw(player, getSetupMessage(context.getPlayer(), "plot.set.too_many_floor_layers"));
            return true;
        }

        Material floorMaterial = detectFloorMaterial(world, minX, minY, minZ);

        context.getData().setRegionBounds("game.plot.bounds", pos1, pos2);
        context.getData().setString("game.plot.floor", floorMaterial.name());
        context.getData().setLocation("game.plot.spawn", player.getLocation());
        context.getData().setString("basic.world", player.getWorld().getName());
        context.getData().save();

        int x = (int) Math.abs(pos2.getX() - pos1.getX()) + 1;
        int y = height;
        int z = (int) Math.abs(pos2.getZ() - pos1.getZ()) + 1;
        int blocks = x * y * z;

        String msg = getSetupMessage(context.getPlayer(), "plot.set.success")
                .replace("{blocks}", String.valueOf(blocks))
                .replace("{x}", String.valueOf(x))
                .replace("{y}", String.valueOf(y))
                .replace("{z}", String.valueOf(z))
                .replace("{floor}", floorMaterial.name());
        context.getMessagesAPI().sendRaw(player, msg);

        String spawnMsg = getSetupMessage(context.getPlayer(), "plot.set.spawn_set");
        if (spawnMsg != null && !spawnMsg.isEmpty()) {
            context.getMessagesAPI().sendRaw(player, spawnMsg);
        }
        return true;
    }

    private boolean handlePlotSpawnSet(SetupContext<Player, CommandSender, Location> context) {
        Player player = context.getPlayer();
        if (player == null) {
            return true;
        }
        try {
            Location loc = player.getLocation();
            context.getData().setLocation("game.plot.spawn", loc);
            context.getData().setString("basic.world", loc.getWorld().getName());
            context.getData().save();

            String msg = getSetupMessage(context.getPlayer(), "plot.spawn.set");
            if (msg == null || msg.isEmpty()) {
                msg = "<green>Plot spawn updated to your current location.</green>";
            }
            context.getMessagesAPI().sendRaw(player, msg);
        } catch (Exception e) {
            org.bukkit.Bukkit.getLogger().severe("[GuessTheBuild] Error in plot spawn setup: " + e.getMessage());
            e.printStackTrace();
            context.getMessagesAPI().sendRaw(player, "<red>An error occurred while saving the plot spawn. Check console.</red>");
        }
        return true;
    }

    private int countFloorLayers(World world, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        int layers = 0;
        for (int y = minY; y <= maxY; y++) {
            boolean hasBlock = false;
            for (int x = minX; x <= maxX && !hasBlock; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (world.getBlockAt(x, y, z).getType() != Material.AIR) {
                        hasBlock = true;
                        break;
                    }
                }
            }
            if (hasBlock) {
                layers++;
            } else {
                break;
            }
        }
        return layers;
    }

    private Material detectFloorMaterial(World world, int minX, int minY, int minZ) {
        Block block = world.getBlockAt(minX, minY, minZ);
        Material material = block.getType();
        if (material != Material.AIR) {
            return material;
        }
        return Material.GRASS_BLOCK;
    }

    private String getSetupMessage(Player player, String key) {
        String message = moduleConfig.getTranslation(player, "setup_messages." + key);
        if (message != null && !message.isBlank()) {
            return message;
        }
        return switch (key) {
            case "plot.usage" -> "<red>Usage:</red> <yellow>/baa game <id> guess_the_build plot <set|spawn></yellow>";
            case "plot.set.must_use_stick" -> "<red>Select two corners with the setup tool first.</red>";
            case "plot.set.too_shallow" -> "<red>Plot is too shallow. The selection must be at least 3 blocks high (1 floor + 2 air).</red>";
            case "plot.set.no_floor" -> "<red>No floor detected. The lowest layer of the selection must contain blocks.</red>";
            case "plot.set.too_many_floor_layers" -> "<red>Too many floor layers detected. The floor must be 1-2 block layers thick. Clear excess blocks above the floor.</red>";
            case "plot.set.success" -> "<green>Plot set.</green> <gray>Blocks:</gray> <white>{blocks}</white> <gray>({x}x{y}x{z})</gray> <gray>Floor:</gray> <yellow>{floor}</yellow>";
            case "plot.set.spawn_set" -> "<gray>Plot spawn set to your current location. You can change it anytime with</gray> <yellow>/baa game <id> guess_the_build plot spawn</yellow>";
            case "plot.spawn.set" -> "<green>Plot spawn updated to your current location.</green>";
            default -> "";
        };
    }

    @SuppressWarnings("unchecked")
    private SetupContext<Player, CommandSender, Location> castSetupContext(SetupContext context) {
        return context;
    }

    @SuppressWarnings("unchecked")
    private TabCompleteContext<Player, CommandSender> castTabContext(TabCompleteContext context) {
        return context;
    }
}
