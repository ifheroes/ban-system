// UnbanCommand.java
package net.ifheroes.ban.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.command.CommandSource;
import net.ifheroes.ban.BanPlugin;
import net.kyori.adventure.text.Component;

public class UnbanCommand implements SimpleCommand {

    private final BanPlugin plugin;

    public UnbanCommand(BanPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();
        String[] args = invocation.arguments();

        if (!src.hasPermission("ifheroes.unban")) {
            src.sendMessage(Component.text("No permission"));
            return;
        }
        if (args.length != 1) {
            src.sendMessage(Component.text("Use: /unban <player>"));
            return;
        }
        String targetName = args[0];
        boolean ok = plugin.unbanByName(targetName);
        if (ok) {
            src.sendMessage(Component.text("Unban successfully executed."));
        } else {
            src.sendMessage(Component.text("This player is currently not online."));
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true; // wir regeln die Permission in execute()
    }
}
