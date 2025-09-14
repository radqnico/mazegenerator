package it.nicoloscialpi.mazegenerator;

import it.nicoloscialpi.mazegenerator.command.MazeCommand;
import it.nicoloscialpi.mazegenerator.themes.ThemeConfigurationReader;

import it.nicoloscialpi.mazegenerator.themes.Themes;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class MazeGeneratorPlugin extends JavaPlugin {

    public static MazeGeneratorPlugin plugin;

    @Override
    public void onEnable() {
        plugin = this;
        saveDefaultConfig();
        // Register command executor and tab-completer
        MazeCommand mazeCommand = new MazeCommand(this);
        Objects.requireNonNull(getCommand("maze")).setExecutor(mazeCommand);
        Objects.requireNonNull(getCommand("maze")).setTabCompleter(mazeCommand);
        Themes.parseThemesFromReader(
                new ThemeConfigurationReader(this, "themes.yml")
        );
        Themes.getThemes().forEach((s, theme) -> {
            getLogger().info("Theme found: " + s);
        });
        MessageFileReader.read(this, "messages.yml");
    }

    @Override
    public void onDisable() {
        // Cancel tasks and unregister listeners
        getServer().getScheduler().cancelTasks(this);
        it.nicoloscialpi.mazegenerator.loadbalancer.LoadBalancer.shutdown();
    }

    @Override
    public void onLoad() {
    }
}
