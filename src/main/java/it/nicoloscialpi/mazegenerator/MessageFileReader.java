package it.nicoloscialpi.mazegenerator;

import net.kyori.adventure.text.TextComponent;
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
            boolean mkdirs = file.getParentFile().mkdirs();
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
        String prefix = yamlConfiguration.getString("plugin-prefix");
        if(prefix == null){
            prefix = "[MazeGenerator]";
        }
        String message = yamlConfiguration.getString(key);
        if(message == null){
            message = "";
        }
        prefix = prefix.replaceAll("&", "§");
        message = message.replaceAll("&", "§");
        return prefix + message;
    }
}
