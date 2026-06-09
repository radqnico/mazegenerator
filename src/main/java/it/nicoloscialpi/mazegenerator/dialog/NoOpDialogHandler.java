package it.nicoloscialpi.mazegenerator.dialog;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import java.util.function.Consumer;

public class NoOpDialogHandler implements DialogHandler {
    @Override
    public void sendDialog(Player player, MazeOptions options, Consumer<MazeOptions> onConfirm) {
        player.sendMessage(dialogNotAvailableMessage());
    }

    @Override
    public Component dialogNotAvailableMessage() {
        return net.kyori.adventure.text.Component.text("Dialogs are not available in this version. Use /maze help for command usage.");
    }
}