package it.nicoloscialpi.mazegenerator.dialog.impl;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import it.nicoloscialpi.mazegenerator.dialog.DialogHandler;
import it.nicoloscialpi.mazegenerator.dialog.MazeOptions;
import it.nicoloscialpi.mazegenerator.themes.Themes;

import java.util.List;
import java.util.function.Consumer;

public class PaperDialogHandler implements DialogHandler {

    @Override
    public void sendDialog(Player player, MazeOptions options, Consumer<MazeOptions> onConfirm) {
        List<SingleOptionDialogInput.OptionEntry> themeEntries = new java.util.ArrayList<>();
        if (Themes.getThemes() != null) {
            Themes.getThemes().keySet().forEach(name ->
                themeEntries.add(SingleOptionDialogInput.OptionEntry.create(name, Component.text(name), false)));
        }
        if (themeEntries.isEmpty()) {
            themeEntries.add(SingleOptionDialogInput.OptionEntry.create("desert", Component.text("desert"), true));
        }

        DialogActionCallback callback = (view, audience) -> {
            if (!(audience instanceof Player p)) return;

            MazeOptions filled = new MazeOptions(
                view.getFloat("mazeSizeX").intValue(),
                view.getFloat("mazeSizeZ").intValue(),
                view.getFloat("cellSize").intValue(),
                view.getFloat("wallHeight").intValue(),
                view.getFloat("layers").intValue(),
                view.getFloat("stairs").intValue(),
                view.getFloat("additionalExits").intValue(),
                view.getFloat("erosion") * 100f / 10000f,
                view.getBoolean("hasExits") != null && view.getBoolean("hasExits"),
                view.getBoolean("hasRoom") != null && view.getBoolean("hasRoom"),
                view.getBoolean("closed") != null && view.getBoolean("closed"),
                view.getBoolean("hollow") != null && view.getBoolean("hollow"),
                view.getBoolean("layDown") != null && view.getBoolean("layDown"),
                view.getText("themeName") != null ? view.getText("themeName") : "desert"
            );
            onConfirm.accept(filled);
        };

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Maze Configuration", NamedTextColor.GOLD))
                        .inputs(List.of(
                                DialogInput.numberRange("mazeSizeX", Component.text("Maze Width (cells)", NamedTextColor.GREEN), 1f, 500f)
                                        .step(1f).initial(5f).width(300).build(),
                                DialogInput.numberRange("mazeSizeZ", Component.text("Maze Depth (cells)", NamedTextColor.GREEN), 1f, 500f)
                                        .step(1f).initial(5f).width(300).build(),
                                DialogInput.numberRange("cellSize", Component.text("Cell Size", NamedTextColor.GREEN), 1f, 10f)
                                        .step(1f).initial(1f).width(300).build(),
                                DialogInput.numberRange("wallHeight", Component.text("Wall Height", NamedTextColor.GREEN), 1f, 20f)
                                        .step(1f).initial(3f).width(300).build(),
                                DialogInput.numberRange("layers", Component.text("Layers (multi-level)", NamedTextColor.GREEN), 1f, 10f)
                                        .step(1f).initial(1f).width(300).build(),
                                DialogInput.numberRange("stairs", Component.text("Stairs per layer pair", NamedTextColor.GREEN), 1f, 5f)
                                        .step(1f).initial(1f).width(300).build(),
                                DialogInput.numberRange("additionalExits", Component.text("Additional Exits", NamedTextColor.GREEN), 0f, 10f)
                                        .step(1f).initial(0f).width(300).build(),
                                DialogInput.numberRange("erosion", Component.text("Erosion %", NamedTextColor.GREEN), 0f, 20f)
                                        .step(1f).initial(0f).width(300).build(),
                                DialogInput.bool("hasExits", Component.text("Generate Exits?", NamedTextColor.GREEN)).build(),
                                DialogInput.bool("hasRoom", Component.text("Generate Room?", NamedTextColor.GREEN)).build(),
                                DialogInput.bool("closed", Component.text("Closed Maze?", NamedTextColor.GREEN)).build(),
                                DialogInput.bool("hollow", Component.text("Hollow Maze?", NamedTextColor.GREEN)).build(),
                                DialogInput.bool("layDown", Component.text("Lay Down on Terrain?", NamedTextColor.GREEN)).build(),
                                DialogInput.singleOption("themeName", Component.text("Theme", NamedTextColor.GREEN), themeEntries).build()
                        ))
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.create(
                                Component.text("Generate Maze", TextColor.color(0xAEFFC1)),
                                Component.text("Click to generate the maze"),
                                100,
                                DialogAction.customClick(callback, ClickCallback.Options.builder().uses(1).build())
                        ),
                        ActionButton.create(
                                Component.text("Cancel", TextColor.color(0xFFA0B1)),
                                Component.text("Close this dialog"),
                                100,
                                null
                        )
                ))
        );

        player.showDialog(dialog);
    }

    @Override
    public Component dialogNotAvailableMessage() {
        return Component.text("Dialogs are not available in this version. Use /maze help for command usage.");
    }
}