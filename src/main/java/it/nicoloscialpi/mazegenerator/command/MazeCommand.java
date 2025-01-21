package it.nicoloscialpi.mazegenerator.command;

import com.google.common.util.concurrent.AtomicDouble;
import it.nicoloscialpi.mazegenerator.MessageFileReader;
import it.nicoloscialpi.mazegenerator.loadbalancer.LoadBalancer;
import it.nicoloscialpi.mazegenerator.maze.MazeGenerator;
import it.nicoloscialpi.mazegenerator.maze.MazePlacer;
import it.nicoloscialpi.mazegenerator.themes.Theme;
import it.nicoloscialpi.mazegenerator.themes.Themes;
import org.bukkit.Location;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class MazeCommand implements CommandExecutor, TabCompleter {
    private static final List<String> acceptableArguments = Arrays.asList(
            "x",
            "y",
            "z",
            "world",
            "mazeSizeX",
            "mazeSizeZ",
            "cellSize",
            "wallHeight",
            "hasExits",
            "additionalExits",
            "hasRoom",
            "roomSizeX",
            "roomSizeZ",
            "maxMemoryGigabytes",
            "erosion",
            "closed",
            "hollow",
            "themeName"
    );
    private final JavaPlugin plugin;

    public MazeCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {

        if (!commandSender.hasPermission("mazegenerator.maze")) {
            return false;
        }

        AtomicInteger x = new AtomicInteger();
        AtomicInteger y = new AtomicInteger();
        AtomicInteger z = new AtomicInteger();
        AtomicInteger mazeSizeX = new AtomicInteger(5);
        AtomicInteger mazeSizeZ = new AtomicInteger(5);
        AtomicInteger cellSize = new AtomicInteger(1);
        AtomicInteger wallHeight = new AtomicInteger(3);
        AtomicBoolean hasExits = new AtomicBoolean(false);
        AtomicInteger additionalExits = new AtomicInteger(0);
        AtomicBoolean hasRoom = new AtomicBoolean(false);
        AtomicInteger roomSizeX = new AtomicInteger(3);
        AtomicInteger roomSizeZ = new AtomicInteger(3);
        AtomicDouble maxMemory = new AtomicDouble(0);

        AtomicDouble erosion = new AtomicDouble(0);
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicBoolean hollow = new AtomicBoolean(false);
        AtomicReference<String> world = new AtomicReference<>("world");
        AtomicReference<String> themeName = new AtomicReference<>("themeName");


        if (commandSender instanceof Player) {
            Player player = (Player) commandSender;
            x.set(player.getLocation().getBlockX());
            y.set(player.getLocation().getBlockY());
            z.set(player.getLocation().getBlockZ());
            world.set(player.getWorld().getName());
        }
        ArrayList<String> arguments = new ArrayList<>(Arrays.asList(strings));

        CommandArgumentsParser commandArgumentsParser = new CommandArgumentsParser(arguments);

        commandArgumentsParser.getInt("x").ifPresent(x::set);
        commandArgumentsParser.getInt("y").ifPresent(y::set);
        commandArgumentsParser.getInt("z").ifPresent(z::set);
        commandArgumentsParser.getInt("mazeSizeX").ifPresent(mazeSizeX::set);
        commandArgumentsParser.getInt("mazeSizeZ").ifPresent(mazeSizeZ::set);
        commandArgumentsParser.getInt("cellSize").ifPresent(cellSize::set);
        commandArgumentsParser.getInt("wallHeight").ifPresent(wallHeight::set);
        commandArgumentsParser.getBool("hasExits").ifPresent(hasExits::set);
        commandArgumentsParser.getInt("additionalExits").ifPresent(additionalExits::set);
        commandArgumentsParser.getBool("hasRoom").ifPresent(hasRoom::set);
        commandArgumentsParser.getInt("roomSizeX").ifPresent(roomSizeX::set);
        commandArgumentsParser.getInt("roomSizeZ").ifPresent(roomSizeZ::set);
        commandArgumentsParser.getDouble("maxMemoryGigabytes").ifPresent(maxMemory::set);

        commandArgumentsParser.getDouble("erosion").ifPresent(erosion::set);
        commandArgumentsParser.getString("world").ifPresent(world::set);
        commandArgumentsParser.getString("themeName").ifPresent(themeName::set);
        commandArgumentsParser.getBool("closed").ifPresent(closed::set);
        commandArgumentsParser.getBool("hollow").ifPresent(hollow::set);

        plugin.getLogger().info(commandArgumentsParser.toString());

        try {
            MazeGenerator generator = new MazeGenerator(mazeSizeX.get(), mazeSizeZ.get());
            byte[][] generateMaze = generator.generateMaze(
                    additionalExits.get(),
                    erosion.get(),
                    hasRoom.get(),
                    roomSizeX.get(),
                    roomSizeZ.get(),
                    hasExits.get()
            );
            commandSender.sendMessage(MessageFileReader.getMessage("generation-done").replaceAll("%time%", String.valueOf(MazeGenerator.getLastGenerationMillis() / 1000.0)));
            Theme theme = Themes.getTheme(themeName.get());
            MazePlacer mazePlacer = new MazePlacer(theme,
                    generateMaze,
                    new Location(commandSender.getServer().getWorld(world.get()), x.get(), y.get(), z.get()),
                    wallHeight.get(),
                    cellSize.get(),
                    closed.get(),
                    hollow.get(),
                    maxMemory.get()
            );

            LoadBalancer loadBalancer = new LoadBalancer(plugin, commandSender, mazePlacer);
            loadBalancer.start();

        } catch (Exception e) {
            commandSender.sendMessage(MessageFileReader.getMessage("command-error"));
            commandSender.getServer().getLogger().severe(e.toString());
        }

        return true;
    }


    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {

        final List<String> suggestions = new ArrayList<>(acceptableArguments);

        if (strings.length > 0 && commandSender instanceof Player player) {
            Block targetBlockExact = player.getTargetBlockExact(10);
            if (targetBlockExact != null) {
                String last = strings[strings.length - 1];
                switch (last.toLowerCase()) {
                    case "x:" -> {
                        suggestions.clear();
                        suggestions.add(last + targetBlockExact.getX());
                        return suggestions;
                    }
                    case "y:" -> {
                        suggestions.clear();
                        suggestions.add(last + targetBlockExact.getY());
                        return suggestions;
                    }
                    case "z:" -> {
                        suggestions.clear();
                        suggestions.add(last + targetBlockExact.getZ());
                        return suggestions;
                    }
                }
            }
        }

        for (String argument : strings) {
            for (String acceptableArgument : acceptableArguments) {
                String[] split = argument.split(":");
                if (split.length == 0) {
                    continue;
                }
                if (split[0].equalsIgnoreCase(acceptableArgument)) {
                    suggestions.remove(acceptableArgument);
                }
            }
        }

        return suggestions.stream().map(arg -> arg + ":").toList();
    }
}
