package it.nicoloscialpi.mazegenerator.loadbalancer;

import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

class TickEventListener implements Listener {

    public TickEventListener(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void unregister() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onTick(ServerTickStartEvent event) {
        LoadBalancer.LAST_TICK_START_NANOS = System.nanoTime();
        ChunkLoadLimiter.resetBudget();
    }
}
