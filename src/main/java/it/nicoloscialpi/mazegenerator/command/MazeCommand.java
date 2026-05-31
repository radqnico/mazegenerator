package it.nicoloscialpi.mazegenerator.command;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import it.nicoloscialpi.mazegenerator.MessageFileReader;
import it.nicoloscialpi.mazegenerator.loadbalancer.LoadBalancer;
import it.nicoloscialpi.mazegenerator.maze.MazeStreamPlacer;
import it.nicoloscialpi.mazegenerator.themes.Theme;
import it.nicoloscialpi.mazegenerator.themes.ThemeConfigurationReader;
import it.nicoloscialpi.mazegenerator.themes.Themes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
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

    private static final List<String> ORDERED_ARGS = Arrays.asList(
            "x","y","z","sizeX","sizeZ","mazeSizeX","mazeSizeZ",
            "world",
            "cellSize","wallHeight",
            "hasExits","additionalExits",
            "hasRoom","roomSizeX","roomSizeZ",
            "erosion","closed","hollow","themeName","layDown"
    );
    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "stop", "confirm", "cancel", "status", "help", "reload", "dialog"
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
        boolean layDown = false;
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
        p.getInt("sizeX").ifPresent(v -> opt.mazeSizeX = v);
        p.getInt("sizeZ").ifPresent(v -> opt.mazeSizeZ = v);
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
        p.getBool("layDown").ifPresent(v -> opt.layDown = v);
        return opt;
    }

    private record PendingBuild(MazeOptions options, Theme theme, Location origin) {}

    private Optional<String> validate(MazeOptions o, CommandSender sender) {
        if (o.mazeSizeX < 1 || o.mazeSizeZ < 1) return Optional.of("Invalid maze size");
        if (o.cellSize < 1 || o.wallHeight < 1) return Optional.of("Invalid cellSize/wallHeight");
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

        if (args.length > 0) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "stop":
                    return handleStop(sender);
                case "confirm":
                    return handleConfirm(sender);
                case "cancel":
                    return handleCancel(sender);
                case "status":
                    return handleStatus(sender);
                case "help":
                    return handleHelp(sender);
                case "reload":
                    return handleReload(sender);
                case "dialog":
                    return handleDialog(sender);
                default:
                    break;
            }
        }

        return handleGeneration(sender, args);
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
                opt.hasExits,
                opt.layDown
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
        LinkedHashSet<String> availableKeys = new LinkedHashSet<>(ORDERED_ARGS);
        String last = args.length > 0 ? args[args.length - 1] : "";
        List<String> suggestions = new ArrayList<>();

        for (String a : args) {
            if (a == null) continue;
            int idx = a.indexOf(":");
            String key = idx >= 0 ? a.substring(0, idx) : a;
            String canonical = canonicalKey(key);
            availableKeys.removeIf(k -> canonicalKey(k).equalsIgnoreCase(canonical));
        }

        if (last.contains(":")) {
            String key = last.substring(0, last.indexOf(":"));
            String valueQuery = last.substring(last.indexOf(":") + 1).toLowerCase(Locale.ROOT);
            String displayKey = displayKey(key);
            suggestions.clear();
            switch (key.toLowerCase()) {
                case "x": case "y": case "z":
                    if (sender instanceof Player p) {
                        Block b = p.getTargetBlockExact(10);
                        if (b != null) {
                            suggestions.add("x:" + b.getX());
                            suggestions.add("y:" + b.getY());
                            suggestions.add("z:" + b.getZ());
                        } else {
                            Location loc = p.getLocation();
                            suggestions.add("x:" + loc.getBlockX());
                            suggestions.add("y:" + loc.getBlockY());
                            suggestions.add("z:" + loc.getBlockZ());
                        }
                    }
                    break;
                case "world":
                    sender.getServer().getWorlds().forEach(w -> suggestions.add(displayKey + ":" + w.getName()));
                    break;
                case "themename":
                    if (Themes.getThemes() != null) {
                        Themes.getThemes().keySet().forEach(t -> suggestions.add(displayKey + ":" + t));
                    }
                    break;
                case "hasexits":
                case "hasroom":
                case "closed":
                case "hollow":
                    suggestions.add(displayKey + ":true");
                    suggestions.add(displayKey + ":false");
                    break;
                case "cellsize":
                    suggestions.add(displayKey + ":1");
                    suggestions.add(displayKey + ":2");
                    break;
                case "wallheight":
                    suggestions.add(displayKey + ":3");
                    suggestions.add(displayKey + ":4");
                    break;
                default:
                    break;
            }
            filterByQuery(suggestions, valueQuery);
            return suggestions;
        }

        // Build final list: key suggestions with ':' plus optional subcommands
        List<String> out = new ArrayList<>();
        List<String> filteredKeys = filterKeysByQuery(new ArrayList<>(availableKeys), last);
        for (String k : filteredKeys) {
            out.add(k + ":");
        }
        // Suggest subcommands only for the first token (no key:value yet)
        if (args.length == 0 || (args.length == 1 && !last.contains(":"))) {
            String lastLower = last.toLowerCase(Locale.ROOT);
            for (String sub : SUBCOMMANDS) {
                if (last.isEmpty() || sub.contains(lastLower)) {
                    out.add(sub);
                }
            }
        }
        return out;
    }

    private String canonicalKey(String key) {
        if ("sizex".equalsIgnoreCase(key)) return "mazeSizeX";
        if ("sizez".equalsIgnoreCase(key)) return "mazeSizeZ";
        for (String k : ORDERED_ARGS) {
            if (k.equalsIgnoreCase(key)) return k;
        }
        return key;
    }

    private String displayKey(String key) {
        if ("sizex".equalsIgnoreCase(key)) return "sizeX";
        if ("sizez".equalsIgnoreCase(key)) return "sizeZ";
        return canonicalKey(key);
    }

    private List<String> filterKeysByQuery(List<String> candidates, String query) {
        String q = query.toLowerCase(Locale.ROOT);
        List<String> filtered = new ArrayList<>();
        for (String candidate : candidates) {
            if (q.isEmpty() || candidate.toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(candidate);
            }
        }
        return filtered;
    }

    private void filterByQuery(List<String> suggestions, String query) {
        if (query == null || query.isEmpty()) return;
        String q = query.toLowerCase(Locale.ROOT);
        suggestions.removeIf(s -> !s.toLowerCase(Locale.ROOT).contains(q));
    }

    private boolean handleStop(CommandSender sender) {
        LoadBalancer.stopAll();
        sender.sendMessage(MessageFileReader.getMessage("job-stopped"));
        return true;
    }

    private boolean handleConfirm(CommandSender sender) {
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

    private boolean handleCancel(CommandSender sender) {
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

    private boolean handleStatus(CommandSender sender) {
        LoadBalancer lb = LoadBalancer.getFor(sender);
        if (lb == null) {
            sender.sendMessage("No active maze for you right now.");
        } else {
            sender.sendMessage(String.format("Maze progress: %.2f%% (budget %dms)", lb.getProgressPercentage(), lb.getCurrentMillisPerTick()));
        }
        return true;
    }

    private boolean handleHelp(CommandSender sender) {
        sendHelp(sender);
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("mazegenerator.reload")) {
            sender.sendMessage(MessageFileReader.getMessage("no-permission"));
            return true;
        }
        plugin.reloadConfig();
        Themes.parseThemesFromReader(new ThemeConfigurationReader(plugin, "themes.yml"));
        MessageFileReader.read(plugin, "messages.yml");
        sender.sendMessage(MessageFileReader.getMessage("config-reloaded"));
        return true;
    }

    private boolean handleDialog(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Dialogs are only available for players.");
            return true;
        }
        if (!sender.hasPermission("mazegenerator.maze")) {
            sender.sendMessage(MessageFileReader.getMessage("no-permission"));
            return true;
        }

        List<SingleOptionDialogInput.OptionEntry> themeEntries = new ArrayList<>();
        if (Themes.getThemes() != null) {
            Themes.getThemes().keySet().forEach(name ->
                themeEntries.add(SingleOptionDialogInput.OptionEntry.create(name, Component.text(name), false)));
        }
        if (themeEntries.isEmpty()) {
            themeEntries.add(SingleOptionDialogInput.OptionEntry.create("desert", Component.text("desert"), true));
        }

        Dialog dialog = Dialog.create(builder -> builder
                .base(DialogBase.builder(Component.text("Maze Configuration", NamedTextColor.GOLD))
                        .inputs(List.of(
                                DialogInput.numberRange("mazeSizeX", Component.text("Maze Width (cells)", NamedTextColor.GREEN), 1f, 100f)
                                        .step(1f).initial(5f).width(300).build(),
                                DialogInput.numberRange("mazeSizeZ", Component.text("Maze Depth (cells)", NamedTextColor.GREEN), 1f, 100f)
                                        .step(1f).initial(5f).width(300).build(),
                                DialogInput.numberRange("cellSize", Component.text("Cell Size", NamedTextColor.GREEN), 1f, 10f)
                                        .step(1f).initial(1f).width(300).build(),
                                DialogInput.numberRange("wallHeight", Component.text("Wall Height", NamedTextColor.GREEN), 1f, 20f)
                                        .step(1f).initial(3f).width(300).build(),
                                DialogInput.numberRange("erosion", Component.text("Erosion (0-100%)", NamedTextColor.GREEN), 0f, 100f)
                                        .step(5f).initial(0f).width(300)
                                        .labelFormat("%s%% holes in walls").build(),
                                DialogInput.bool("hasExits", Component.text("Generate Exits?", NamedTextColor.GREEN)).build(),
                                DialogInput.bool("hasRoom", Component.text("Generate Room?", NamedTextColor.GREEN)).build(),
                                DialogInput.bool("closed", Component.text("Closed Maze?", NamedTextColor.GREEN)).build(),
                                DialogInput.bool("hollow", Component.text("Hollow Maze?", NamedTextColor.GREEN)).build(),
                                DialogInput.singleOption("themeName", Component.text("Theme", NamedTextColor.GREEN), themeEntries).build()
                        ))
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.create(
                                Component.text("Generate Maze", TextColor.color(0xAEFFC1)),
                                Component.text("Click to generate the maze"),
                                100,
                                DialogAction.customClick(createMazeDialogCallback(), ClickCallback.Options.builder().uses(1).build())
                        ),
                        ActionButton.create(
                                Component.text("Cancel", TextColor.color(0xFFA0B1)),
                                Component.text("Close this dialog"),
                                100,
                                null
                        )
                ))
        );

        p.showDialog(dialog);
        return true;
    }

    private DialogActionCallback createMazeDialogCallback() {
        return (view, audience) -> {
            if (!(audience instanceof Player player)) return;

            int mazeSizeX = view.getFloat("mazeSizeX").intValue();
            int mazeSizeZ = view.getFloat("mazeSizeZ").intValue();
            int cellSize = view.getFloat("cellSize").intValue();
            int wallHeight = view.getFloat("wallHeight").intValue();
            int erosion = view.getFloat("erosion").intValue();
            Boolean hasExits = view.getBoolean("hasExits");
            Boolean hasRoom = view.getBoolean("hasRoom");
            Boolean closed = view.getBoolean("closed");
            Boolean hollow = view.getBoolean("hollow");
            String themeName = view.getText("themeName");
            if (themeName == null) themeName = "desert";

            MazeOptions opt = new MazeOptions();
            opt.mazeSizeX = mazeSizeX;
            opt.mazeSizeZ = mazeSizeZ;
            opt.cellSize = cellSize;
            opt.wallHeight = wallHeight;
            opt.erosion = erosion / 100.0;
            opt.hasExits = hasExits != null && hasExits;
            opt.hasRoom = hasRoom != null && hasRoom;
            opt.closed = closed != null && closed;
            opt.hollow = hollow != null && hollow;
            opt.themeName = themeName;

            Location l = player.getLocation();
            opt.x = l.getBlockX();
            opt.y = l.getBlockY();
            opt.z = l.getBlockZ();
            opt.world = player.getWorld().getName();

            Theme theme = Themes.getTheme(opt.themeName);
            Location origin = new Location(Bukkit.getWorld(opt.world), opt.x, opt.y, opt.z);

            if (opt.layDown && !TerrainValidation.canLayDown(origin, opt.mazeSizeX, opt.mazeSizeZ, opt.cellSize, opt.wallHeight)) {
                player.sendMessage(Component.text("Cannot lay down maze here (no valid ground).", NamedTextColor.RED));
                return;
            }

            PendingBuild pending = new PendingBuild(opt, theme, origin);
            PENDING.put(player.getUniqueId(), pending);
            MazePreviewer.showPreview(plugin, player, origin, opt.mazeSizeX, opt.mazeSizeZ, opt.cellSize, opt.wallHeight, opt.layDown);
            player.sendMessage(Component.text("Preview shown with particles. Use /maze confirm to start or /maze cancel to discard.", NamedTextColor.YELLOW));
        };
    }

    private boolean handleGeneration(CommandSender sender, String[] args) {
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
                boolean hasCoords = hasCoordArgs(args);
                if (!hasCoords) {
                    sender.sendMessage("Console usage requires x:, y:, z: coordinates and world: if needed.");
                    return true;
                }
                // Console: no preview/confirm, build immediately
                if (opt.layDown && !TerrainValidation.canLayDown(origin, opt.mazeSizeX, opt.mazeSizeZ, opt.cellSize, opt.wallHeight)) {
                    sender.sendMessage("Cannot lay down maze here (no valid ground).");
                    return true;
                }
                startBuild(sender, opt, theme, origin);
                return true;
            }

            boolean requestConfirm = plugin.getConfig().getBoolean("request-confirm", true);
            if (!requestConfirm) {
                MazePreviewer.stopPreview(p);
                if (opt.layDown && !TerrainValidation.canLayDown(origin, opt.mazeSizeX, opt.mazeSizeZ, opt.cellSize, opt.wallHeight)) {
                    sender.sendMessage("Cannot lay down maze here (no valid ground).");
                    return true;
                }
                startBuild(sender, opt, theme, origin);
                sender.sendMessage(MessageFileReader.getMessage("build-no-preview"));
                return true;
            }

            PENDING.remove(p.getUniqueId());
            MazePreviewer.stopPreview(p);
            if (opt.layDown && !TerrainValidation.canLayDown(origin, opt.mazeSizeX, opt.mazeSizeZ, opt.cellSize, opt.wallHeight)) {
                sender.sendMessage("Cannot lay down maze here (no valid ground).");
                return true;
            }
            PendingBuild pending = new PendingBuild(opt, theme, origin);
            PENDING.put(p.getUniqueId(), pending);
            MazePreviewer.showPreview(plugin, p, origin, opt.mazeSizeX, opt.mazeSizeZ, opt.cellSize, opt.wallHeight, opt.layDown);
            sender.sendMessage("Preview shown with particles (enable them). Use /maze confirm to start or /maze cancel to discard.");
        } catch (Exception e) {
            sender.sendMessage("An unexpected plugin error occurred. Please contact the developer on Modrinth with your command details.");
            sender.getServer().getLogger().severe(e.toString());
        }
        return true;
    }

    private boolean hasCoordArgs(String[] args) {
        boolean hasX = false, hasY = false, hasZ = false;
        for (String a : args) {
            if (a == null) continue;
            String lower = a.toLowerCase(Locale.ROOT);
            if (lower.startsWith("x:")) hasX = true;
            if (lower.startsWith("y:")) hasY = true;
            if (lower.startsWith("z:")) hasZ = true;
        }
        return hasX && hasY && hasZ;
    }
}
