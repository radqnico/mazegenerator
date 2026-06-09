package it.nicoloscialpi.mazegenerator;

import org.bukkit.ChatColor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class MessageFileReader {
    private static volatile YamlConfiguration yamlConfiguration;

    public static void read(JavaPlugin plugin, String configName) {
        File file = new File(plugin.getDataFolder(), configName);
        if (!file.exists()) {
            // Ensure directory exists then copy default resource
            //noinspection ResultOfMethodCallIgnored
            file.getParentFile().mkdirs();
            plugin.saveResource(configName, false);
        }
        YamlConfiguration newConfig = new YamlConfiguration();
        try {
            newConfig.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().severe(e.getMessage());
            return;
        }
        yamlConfiguration = newConfig;
    }

    public static String getMessage(String key) {
        YamlConfiguration config = yamlConfiguration;
        if (config == null) {
            return "";
        }
        String prefix = config.getString("plugin-prefix", "&7[MazeGenerator] &r");
        String message = config.getString(key, "");
        return ChatColor.translateAlternateColorCodes('&', prefix + message);
    }

    public static void reload(JavaPlugin plugin, String configName) {
        read(plugin, configName);
    }
}

