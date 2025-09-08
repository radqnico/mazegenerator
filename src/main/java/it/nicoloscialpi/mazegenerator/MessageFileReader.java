package it.nicoloscialpi.mazegenerator;

import org.bukkit.ChatColor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class MessageFileReader {
    private static YamlConfiguration yamlConfiguration;

    public static void read(JavaPlugin plugin, String configName) {
        File file = new File(plugin.getDataFolder(), configName);
        if (!file.exists()) {
            // Ensure directory exists then copy default resource
            //noinspection ResultOfMethodCallIgnored
            file.getParentFile().mkdirs();
            plugin.saveResource(configName, false);
        }
        yamlConfiguration = new YamlConfiguration();
        try {
            yamlConfiguration.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().severe(e.getMessage());
        }
    }

    public static String getMessage(String key) {
        String prefix = yamlConfiguration.getString("plugin-prefix", "&7[MazeGenerator] &r");
        String message = yamlConfiguration.getString(key, "");
        String colored = ChatColor.translateAlternateColorCodes('&', prefix + message);
        return colored;
    }
}

