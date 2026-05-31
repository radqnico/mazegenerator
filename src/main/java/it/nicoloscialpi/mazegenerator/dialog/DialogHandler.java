package it.nicoloscialpi.mazegenerator.dialog;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.function.Consumer;

public interface DialogHandler {
    void sendDialog(Player player, MazeOptions options, Consumer<MazeOptions> onConfirm);
    Component dialogNotAvailableMessage();
}