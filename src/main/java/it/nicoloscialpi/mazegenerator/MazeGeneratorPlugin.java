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
        Objects.requireNonNull(getCommand("maze")).setExecutor(new MazeCommand(this));
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
        // Plugin shutdown logic
    }

    @Override
    public void onLoad() {
    }
}
