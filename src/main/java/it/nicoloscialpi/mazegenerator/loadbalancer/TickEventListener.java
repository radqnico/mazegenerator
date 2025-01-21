package it.nicoloscialpi.mazegenerator.loadbalancer;

import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import com.jeff_media.customblockdata.CustomBlockData;
import it.nicoloscialpi.mazegenerator.MazeGeneratorPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

class TickEventListener implements Listener {

    private JavaPlugin plugin;

    public TickEventListener(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void unregister() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onTick(ServerTickStartEvent event) {
        LoadBalancer.LAST_TICK_START_TIME = System.currentTimeMillis();
    }

}