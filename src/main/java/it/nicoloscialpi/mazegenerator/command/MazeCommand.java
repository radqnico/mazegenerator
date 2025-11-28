package it.nicoloscialpi.mazegenerator.command;

import it.nicoloscialpi.mazegenerator.MessageFileReader;
import it.nicoloscialpi.mazegenerator.loadbalancer.LoadBalancer;
import it.nicoloscialpi.mazegenerator.maze.MazeStreamPlacer;
import it.nicoloscialpi.mazegenerator.themes.Theme;
import it.nicoloscialpi.mazegenerator.themes.Themes;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MazeCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ACCEPTABLE_ARGS = Arrays.asList(
            "x","y","z","world",
            "mazeSizeX","mazeSizeZ",
            "cellSize","wallHeight",
            "hasExits","additionalExits",
            "hasRoom","roomSizeX","roomSizeZ",
            "erosion","closed","hollow","themeName"
    );

    private final JavaPlugin plugin;
    private static final Map<UUID, PendingBuild> PENDING = new HashMap<>();

    public MazeCommand(JavaPlugin plugin) { this.plugin = plugin; }

    private static class MazeOptions {
        int x, y, z;
        String world = "world";
        int mazeSizeX = 5, mazeSizeZ = 5;
        int cellSize = 1, wallHeight = 3;
        boolean hasExits = false; int additionalExits = 0;
        boolean hasRoom = false; int roomSizeX = 3, roomSizeZ = 3;
        double erosion = 0.0; boolean closed = false; boolean hollow = false;
        String themeName = "desert";
    }

    private MazeOptions parseOptions(CommandSender sender, String[] args) {
        MazeOptions opt = new MazeOptions();
        if (sender instanceof Player p) {
            Location l = p.getLocation();
            opt.x = l.getBlockX(); opt.y = l.getBlockY(); opt.z = l.getBlockZ();
            opt.world = p.getWorld().getName();
        }
        CommandArgumentsParser p = new CommandArgumentsParser(new ArrayList<>(Arrays.asList(args)));
        p.getInt("x").ifPresent(v -> opt.x = v);
        p.getInt("y").ifPresent(v -> opt.y = v);
        p.getInt("z").ifPresent(v -> opt.z = v);
        p.getInt("mazeSizeX").ifPresent(v -> opt.mazeSizeX = v);
        p.getInt("mazeSizeZ").ifPresent(v -> opt.mazeSizeZ = v);
        p.getInt("cellSize").ifPresent(v -> opt.cellSize = v);
        p.getInt("wallHeight").ifPresent(v -> opt.wallHeight = v);
        p.getBool("hasExits").ifPresent(v -> opt.hasExits = v);
        p.getInt("additionalExits").ifPresent(v -> opt.additionalExits = v);
        p.getBool("hasRoom").ifPresent(v -> opt.hasRoom = v);
        p.getInt("roomSizeX").ifPresent(v -> opt.roomSizeX = v);
        p.getInt("roomSizeZ").ifPresent(v -> opt.roomSizeZ = v);
        p.getDouble("erosion").ifPresent(v -> opt.erosion = v);
        p.getString("world").ifPresent(v -> opt.world = v);
        p.getString("themeName").ifPresent(v -> opt.themeName = v.toLowerCase(Locale.ROOT));
        p.getBool("closed").ifPresent(v -> opt.closed = v);
        p.getBool("hollow").ifPresent(v -> opt.hollow = v);
        return opt;
    }

    private record PendingBuild(MazeOptions options, Theme theme, Location origin) {}

    private Optional<String> validate(MazeOptions o, CommandSender sender) {
        if (o.mazeSizeX < 1 || o.mazeSizeZ < 1) return Optional.of("Invalid maze size");
        if (o.mazeSizeX > 501 || o.mazeSizeZ > 501) return Optional.of("Maze size too large (max 501 per axis)");
        if (o.cellSize < 1 || o.wallHeight < 1) return Optional.of("Invalid cellSize/wallHeight");
        if (o.cellSize > 32) return Optional.of("cellSize too large (max 32)");
        if (o.wallHeight > 32) return Optional.of("wallHeight too large (max 32)");
        if (o.erosion < 0.0 || o.erosion > 1.0) return Optional.of("Erosion must be in [0,1]");
        World w = sender.getServer().getWorld(o.world);
        if (w == null) return Optional.of("World not found: " + o.world);
        int minY = w.getMinHeight();
        int maxY = w.getMaxHeight() - 1;
        if (o.y < minY || (o.y + o.wallHeight) > maxY) {
            int maxBaseY = maxY - o.wallHeight;
            return Optional.of("Y is out of build range for this world (allowed " + minY + ".." + maxBaseY + ")");
        }
        if (Themes.getThemes() == null || !Themes.getThemes().containsKey(o.themeName)) {
            return Optional.of("Unknown theme: " + o.themeName);
        }
        return Optional.empty();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (!sender.hasPermission("mazegenerator.maze")) {
            sender.sendMessage(MessageFileReader.getMessage("no-permission"));
            return true;
        }

        // Subcommand: stop
        if (args.length > 0 && args[0].equalsIgnoreCase("stop")) {
            it.nicoloscialpi.mazegenerator.loadbalancer.LoadBalancer.stopAll();
            sender.sendMessage(MessageFileReader.getMessage("job-stopped"));
            return true;
        }

        // Subcommand: confirm
        if (args.length > 0 && args[0].equalsIgnoreCase("confirm")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Only players can confirm builds.");
                return true;
            }
            PendingBuild pending = PENDING.remove(p.getUniqueId());
            if (pending == null) {
                sender.sendMessage("No pending maze. Use /maze first to preview.");
                return true;
            }
            MazePreviewer.stopPreview(p);
            startBuild(sender, pending.options, pending.theme, pending.origin);
            return true;
        }

        // Subcommand: cancel
        if (args.length > 0 && args[0].equalsIgnoreCase("cancel")) {
            if (sender instanceof Player p) {
                MazePreviewer.stopPreview(p);
                if (PENDING.remove(p.getUniqueId()) != null) {
                    sender.sendMessage("Pending maze cancelled.");
                } else {
                    sender.sendMessage("No pending maze to cancel.");
                }
            } else {
                sender.sendMessage("Nothing to cancel.");
            }
            return true;
        }

        // Subcommand: status
        if (args.length > 0 && args[0].equalsIgnoreCase("status")) {
            it.nicoloscialpi.mazegenerator.loadbalancer.LoadBalancer lb = it.nicoloscialpi.mazegenerator.loadbalancer.LoadBalancer.getFor(sender);
            if (lb == null) {
                sender.sendMessage("No active maze for you right now.");
            } else {
                sender.sendMessage(String.format("Maze progress: %.2f%% (budget %dms)", lb.getProgressPercentage(), lb.getCurrentMillisPerTick()));
            }
            return true;
        }

        // Subcommand: help
        if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        // Subcommand: reload
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("mazegenerator.reload")) {
                sender.sendMessage(MessageFileReader.getMessage("no-permission"));
                return true;
            }
            plugin.reloadConfig();
            Themes.parseThemesFromReader(new it.nicoloscialpi.mazegenerator.themes.ThemeConfigurationReader(plugin, "themes.yml"));
            MessageFileReader.read(plugin, "messages.yml");
            sender.sendMessage(MessageFileReader.getMessage("config-reloaded"));
            return true;
        }

        // Normal generation
        MazeOptions opt = parseOptions(sender, args);
        Optional<String> err = validate(opt, sender);
        if (err.isPresent()) {
            sender.sendMessage(MessageFileReader.getMessage("command-error"));
            sender.sendMessage("Reason: " + err.get());
            return true;
        }

        try {
            Theme theme = Themes.getTheme(opt.themeName);
            Location origin = new Location(sender.getServer().getWorld(opt.world), opt.x, opt.y, opt.z);
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Only players can preview and confirm mazes. Use in-game.");
                return true;
            }
            // Store pending build and show preview particles
            PENDING.remove(p.getUniqueId());
            MazePreviewer.stopPreview(p);
            PendingBuild pending = new PendingBuild(opt, theme, origin);
            PENDING.put(p.getUniqueId(), pending);
            MazePreviewer.showPreview(plugin, p, origin, opt.mazeSizeX, opt.mazeSizeZ, opt.cellSize, opt.wallHeight);
            sender.sendMessage("Preview shown with particles (enable them). Use /maze confirm to start or /maze cancel to discard.");
        } catch (Exception e) {
            sender.sendMessage("An unexpected plugin error occurred. Please contact the developer on Modrinth with your command details.");
            sender.getServer().getLogger().severe(e.toString());
        }
        return true;
    }

    private void startBuild(CommandSender sender, MazeOptions opt, Theme theme, Location origin) {
        if (sender instanceof Player p) {
            MazePreviewer.stopPreview(p);
        }
        MazeStreamPlacer streamPlacer = new MazeStreamPlacer(
                theme,
                origin,
                opt.wallHeight,
                opt.cellSize,
                opt.closed,
                opt.hollow,
                opt.mazeSizeX,
                opt.mazeSizeZ,
                opt.additionalExits,
                opt.erosion,
                opt.hasRoom,
                opt.roomSizeX,
                opt.roomSizeZ,
                opt.hasExits
        );
        LoadBalancer lb = new LoadBalancer(plugin, sender, streamPlacer);
        lb.start();
    }

    private void sendHelp(CommandSender sender) {
        String[] lines = new String[]{
                "--- MazeGenerator Help ---",
                "Usage: /maze key:value [key:value ...]",
                "Subcommands: /maze help, /maze stop, /maze status, /maze confirm, /maze cancel, /maze reload",
                "",
                "Core keys:",
                "  x,y,z,world          -> placement origin",
                "  mazeSizeX,mazeSizeZ  -> maze size in cells (odd enforced)",
                "  cellSize,wallHeight  -> cell footprint and wall height (default 1/3)",
                "  hasExits,additionalExits,hasRoom,roomSizeX,roomSizeZ",
                "  erosion              -> 0..1 occasional holes",
                "  closed,hollow        -> roof over paths / shell walls",
                "  themeName            -> theme from themes.yml",
                "",
                "Examples:",
                "  /maze mazeSizeX:51 mazeSizeZ:51 cellSize:2 wallHeight:4 themeName:forest",
                "  /maze world:world_nether x:100 y:80 z:-200 mazeSizeX:41 mazeSizeZ:41 themeName:snowy",
                "",
                "Tips:",
                "  - First /maze shows a particle outline; /maze confirm to build, /maze cancel to discard",
                "  - Use hollow:true and larger cellSize to reduce blocks",
                "  - Tweak config.yml (millis-per-tick, jobs-batch-cells, max-blocks-per-job) to protect TPS",
                "  - /maze stop cancels active builds; /maze status shows progress",
                "  - /maze reload reloads config, messages, themes"
        };
        for (String line : lines) sender.sendMessage(line);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> suggestions = new ArrayList<>(ACCEPTABLE_ARGS);
        String last = args.length > 0 ? args[args.length - 1] : "";

        for (String a : args) {
            String[] kv = a.split(":", 2);
            if (kv.length > 0) suggestions.remove(kv[0]);
        }

        if (last.contains(":")) {
            String key = last.substring(0, last.indexOf(":"));
            suggestions.clear();
            switch (key.toLowerCase()) {
                case "x": case "y": case "z":
                    if (sender instanceof Player p) {
                        Block b = p.getTargetBlockExact(10);
                        if (b != null) {
                            suggestions.add("x:" + b.getX());
                            suggestions.add("y:" + b.getY());
                            suggestions.add("z:" + b.getZ());
                        }
                    }
                    break;
                case "world":
                    sender.getServer().getWorlds().forEach(w -> suggestions.add("world:" + w.getName()));
                    break;
                case "themename":
                    Themes.getThemes().keySet().forEach(t -> suggestions.add("themeName:" + t));
                    break;
                case "hasexits":
                case "hasroom":
                case "closed":
                case "hollow":
                    suggestions.add(key + ":true");
                    suggestions.add(key + ":false");
                    break;
                case "cellsize":
                    suggestions.add("cellSize:1");
                    suggestions.add("cellSize:2");
                    break;
                case "wallheight":
                    suggestions.add("wallHeight:3");
                    suggestions.add("wallHeight:4");
                    break;
                default:
                    break;
            }
            return suggestions;
        }

        // Build final list: key suggestions with ':' plus optional 'stop'/'help'/'reload' subcommands
        List<String> out = new ArrayList<>();
        for (String k : suggestions) {
            out.add(k + ":");
        }
        // Suggest 'stop/status/help/reload' only for the first token (no key:value yet)
        if (args.length == 0 || (args.length == 1 && !last.contains(":"))) {
            String lastLower = last.toLowerCase();
            if (last.isEmpty() || "stop".startsWith(lastLower)) {
                out.add("stop");
            }
            if (last.isEmpty() || "confirm".startsWith(lastLower)) {
                out.add("confirm");
            }
            if (last.isEmpty() || "cancel".startsWith(lastLower)) {
                out.add("cancel");
            }
            if (last.isEmpty() || "status".startsWith(lastLower)) {
                out.add("status");
            }
            if (last.isEmpty() || "help".startsWith(lastLower)) {
                out.add("help");
            }
            if (last.isEmpty() || "reload".startsWith(lastLower)) {
                out.add("reload");
            }
        }
        return out;
    }
}
