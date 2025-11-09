package net.ifheroes.ban.commands;

import com.velocitypowered.api.command.SimpleCommand;
import net.ifheroes.ban.BanPlugin;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

public class KickCommand implements SimpleCommand {

    private final BanPlugin plugin;

    public KickCommand(BanPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length < 1) {
            source.sendMessage(Component.text("Usage: /kick <player> [reason]"));
            return;
        }

        String playerName = args[0];
        String reason = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "No reason provided";

        Player player = plugin.getServer().getPlayer(playerName).orElse(null);
        if (player == null) {
            source.sendMessage(Component.text("Player not found"));
            return;
        }

        player.disconnect(Component.text("§fYou have been kicked: §7" + reason));
        source.sendMessage(Component.text("Player " + playerName + " has been kicked for: " + reason));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("ifheroes.kick");
    }
}
