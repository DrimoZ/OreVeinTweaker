package dev.drimoz.oreveintweaker;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** /oreveintweaker reload - re-reads the vein files and the config, and lists every extra vein that is on. */
final class VeinCommand {
    private VeinCommand() {}

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("oreveintweaker")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("reload").executes(ctx -> {
                    VeinRules.rebuild();
                    var lines = VeinRules.report();
                    lines.forEach(l -> ctx.getSource().sendSuccess(() -> Component.literal(l), false));
                    return lines.size();
                })));
    }
}
