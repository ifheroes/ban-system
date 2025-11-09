package net.ifheroes.ban.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.ifheroes.ban.BanPlugin;
import net.kyori.adventure.text.Component;

import java.util.Arrays;
import java.util.UUID;

public class BanCommand implements SimpleCommand {

    private final BanPlugin plugin;

    public BanCommand(BanPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!source.hasPermission("ifheroes.ban")) {
            source.sendMessage(Component.text("No permission."));
            return;
        }
        if (args.length < 2) {
            source.sendMessage(Component.text("Usage: /ban <player> <reason>"));
            return;
        }

        String argPlayerName = args[0];
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        UUID modUuid;
        String modName;
        if (invocation.source() instanceof Player p) {
            modUuid = p.getUniqueId();
            modName = p.getUsername();
        } else {
            modUuid = null;
            modName = "Console";
        }

        // If player is online: ban directly
        plugin.getServer().getPlayer(argPlayerName).ifPresentOrElse(target -> {
            UUID uuid = target.getUniqueId();
            String targetNameActual = target.getUsername();

            // Direct ban (we are already on proxy thread)
            plugin.banPlayer(uuid, targetNameActual, reason, modUuid, modName);

            source.sendMessage(Component.text("Player " + targetNameActual + " has been banned."));
        }, () -> {
            // Offline case: async name resolution + ban
            plugin.banByNameAsync(argPlayerName, reason, modUuid, modName)
                .thenAccept(success -> {
                    // Feedback on proxy thread
                    plugin.getServer().getScheduler().buildTask(plugin, () -> {
                        if (success) {
                            source.sendMessage(Component.text("Player " + argPlayerName + " has been banned (offline)."));
                        } else {
                            source.sendMessage(Component.text("Ban failed for " + argPlayerName + "."));
                        }
                    }).schedule();
                });
        });
    }

    // Keep TRUE so Velocity won't deny the command automatically
    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }
}