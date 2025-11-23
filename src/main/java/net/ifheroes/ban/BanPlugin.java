// BanPlugin.java
package net.ifheroes.ban;

import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import net.ifheroes.ban.commands.BanCommand;
import net.ifheroes.ban.commands.KickCommand;
import net.ifheroes.ban.commands.UnbanCommand;
import net.ifheroes.ban.store.JsonFileBanStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import javax.inject.Inject;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Plugin(
    id = "ifheroes-ban",
    name = "Ifheroes Ban",
    version = "1.0.1",
    authors = {"Christopher"},
    description = "Bann-System mit Name & Moderator gespeichert"
)
public class BanPlugin {

    private final ProxyServer server;
    private final Set<UUID> bannedPlayers = new HashSet<>();
    private final Map<UUID, String> banReasons = new HashMap<>();
    private final Map<UUID, String> banPlayerNames = new HashMap<>();
    private JsonFileBanStore banStore;

    @Inject
    public BanPlugin(ProxyServer server) {
        this.server = server;
        this.banStore = new JsonFileBanStore();
        // lade bestehende Banns
        for (JsonFileBanStore.BanEntry e : banStore.loadAll()) {
            bannedPlayers.add(e.uuid());
            banReasons.put(e.uuid(), e.reason());
            banPlayerNames.put(e.uuid(), e.playerName());
        }
        registerCommands();
    }

    // Registrierung der Commands
    private void registerCommands() {
        CommandMeta metaKick = server.getCommandManager()
            .metaBuilder("kick").build();
        server.getCommandManager().register(metaKick, new KickCommand(this));

        CommandMeta metaBan = server.getCommandManager()
            .metaBuilder("ban").build();
        server.getCommandManager().register(metaBan, new BanCommand(this));

        CommandMeta metaUnban = server.getCommandManager()
            .metaBuilder("unban").build();
        server.getCommandManager().register(metaUnban, new UnbanCommand(this));
    }

    public ProxyServer getServer() {
        return server;
    }

    public Component mm(String msg) {
        return LegacyComponentSerializer
            .legacyAmpersand().deserialize(msg);
    }

    public void banPlayer(UUID uuid, String playerName, String reason, UUID modUuid, String modName) {
        bannedPlayers.add(uuid);
        banReasons.put(uuid, reason);
        banPlayerNames.put(uuid, playerName);
        JsonFileBanStore.BanEntry entry = new JsonFileBanStore.BanEntry(
            uuid, playerName, reason, modUuid, modName
        );
        banStore.create(entry);

        // If the player is currently online, disconnect them with the ban message
        server.getPlayer(uuid).ifPresent(player -> {
            String msg = "&e&lIFHEROES.NET SERVER" +
                         "\n&fYou are banned: &7" + reason +
                         "\n\n&fDuration: &7Permanent" +
                         "\n\n&fTo appeal, visit: &7https://ifheroes.net/discord" +
                         "\n\n&fHow to appeal: &7https://docs.ifheroes.net/appeal";
            player.disconnect(mm(msg));
        });
    }

    public boolean isBanned(UUID uuid) {
        return bannedPlayers.contains(uuid);
    }

    // Prüft, ob ein (offline) Bann per Spielername existiert (case-insensitive)
    public boolean isBannedByName(String playerName) {
        if (playerName == null) return false;
        String low = playerName.toLowerCase(Locale.ROOT);
        for (String n : banPlayerNames.values()) {
            if (n != null && n.toLowerCase(Locale.ROOT).equals(low)) {
                return true;
            }
        }
        return false;
    }

    public String getBanReason(UUID uuid) {
        return banReasons.getOrDefault(uuid, "No reason specified");
    }

    // Neue Methode: versucht Mojang-API, fallback auf Offline-UUID falls nicht gefunden / Fehler
public UUID resolveNameToUuidOrOffline(String playerName) {
        try {
            String enc = URLEncoder.encode(playerName, StandardCharsets.UTF_8);
            String uri = "https://api.mojang.com/users/profiles/minecraft/" + enc;
            HttpRequest req = HttpRequest.newBuilder()
                .uri(java.net.URI.create(uri))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
            HttpClient client = HttpClient.newBuilder().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                String body = resp.body();
                int idIdx = body.indexOf("\"id\":\"");
                if (idIdx >= 0) {
                    int start = idIdx + 6;
                    int end = body.indexOf("\"", start);
                    String raw = body.substring(start, end); // hex ohne Bindestriche
                    String dashed = raw.replaceFirst(
                        "([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]+)",
                        "$1-$2-$3-$4-$5");
                    return UUID.fromString(dashed);
                }
            }
        } catch (Exception ignored) {
            // Fehler bei API-Aufruf -> Fallback unten
        }
        // Offline-UUID Fallback (wie Offline-Mode UUID)
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
    }

    // Neue Methode: asynchron auflösen und anschließend den Bann auf dem Proxy-Thread ausführen
    public CompletableFuture<Boolean> banByNameAsync(String playerName, String reason, UUID modUuid, String modName) {
        return CompletableFuture.supplyAsync(() -> resolveNameToUuidOrOffline(playerName))
            .thenCompose(uuid -> {
                CompletableFuture<Boolean> result = new CompletableFuture<>();
                // Bann auf dem Proxy/Main-Thread ausführen, um Thread-Safety zu wahren
                server.getScheduler().buildTask(this, () -> {
                    banPlayer(uuid, playerName, reason, modUuid, modName);
                    result.complete(true);
                }).schedule();
                return result;
            });
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        UUID u = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getUsername();

        // Zuerst per UUID prüfen, ansonsten per Name (Offline-Banns)
        if (isBanned(u) || isBannedByName(name)) {
            // Versuche passende Reason per UUID, sonst generische Meldung
            String reason;
            if (isBanned(u)) {
                reason = getBanReason(u);
            } else {
                // Suche in bans.json nach dem Eintrag per Spielername (case-insensitive) und nutze dessen Reason
                String defaultReason = "Banned (by name)";
                String foundReason = defaultReason;
                for (JsonFileBanStore.BanEntry e : banStore.loadAll()) {
                    if (e.playerName() != null && e.playerName().equalsIgnoreCase(name)) {
                        if (e.reason() != null && !e.reason().isEmpty()) {
                            foundReason = e.reason();
                        }
                        break;
                    }
                }
                reason = foundReason;
            }

            // Mehrzeilige, englische Bannmeldung mit Duration + Appeal-Hinweis und Docs-Link
            String msg = "&e&lIFHEROES.NET SERVER" +
                         "\n&fYou are banned: &7" + reason +
                         "\n\n&fDuration: &7Permanent" +
                         "\n\n&fTo appeal, visit: &7https://ifheroes.net/discord" +
                         "\n\n&fHow to appeal: &7https://docs.ifheroes.net/appeal";

            event.setResult(LoginEvent.ComponentResult.denied(
                mm(msg)
            ));
        }
    }

    public boolean unbanByName(String playerName) {
        List<JsonFileBanStore.BanEntry> all = banStore.loadAll();
        List<JsonFileBanStore.BanEntry> keep = new ArrayList<>();
        boolean removed = false;
        for (JsonFileBanStore.BanEntry e : all) {
            if (!removed && e.playerName().equalsIgnoreCase(playerName)) {
                bannedPlayers.remove(e.uuid());
                banReasons.remove(e.uuid());
                banPlayerNames.remove(e.uuid());
                removed = true;
            } else {
                keep.add(e);
            }
        }
        if (removed) {
            banStore.saveAll(keep);
        }
        return removed;
    }
}