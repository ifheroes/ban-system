// JsonFileBanStore.java
package net.ifheroes.ban.store;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class JsonFileBanStore {

    private final Path banFile = Paths.get("bans.json");

    public record BanEntry(
        UUID uuid,
        String playerName,
        String reason,
        UUID moderatorUuid,
        String moderatorName
    ) {}

    public JsonFileBanStore() {
        try {
            if (Files.notExists(banFile)) {
                Files.createFile(banFile);
                Files.writeString(banFile, "[]");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void create(BanEntry entry) {
        List<BanEntry> bans = loadAll();
        bans.add(entry);
        saveAll(bans);
    }

    public List<BanEntry> loadAll() {
        List<BanEntry> list = new ArrayList<>();
        try {
            String json = Files.readString(banFile).trim();
            if (json.isEmpty() || json.equals("[]")) {
                return list;
            }
            String body = json.substring(1, json.length() - 1).trim();
            for (String obj : body.split("\\},\\s*\\{")) {
                String clean = obj.replaceFirst("^\\{","").replaceFirst("\\}$","").trim();
                Map<String,String> map = Arrays.stream(clean.split(","))
                    .map(s -> s.split(":",2))
                    .filter(a -> a.length==2)
                    .collect(Collectors.toMap(
                        a->a[0].trim().replace("\"",""),
                        a->a[1].trim().replace("\"","")
                    ));

                String uuidStr         = map.get("uuid");
                if (uuidStr == null) continue;
                UUID uuid             = UUID.fromString(uuidStr);
                String playerName     = map.getOrDefault("playerName", "unbekannt");
                String reason         = map.getOrDefault("reason", "");
                String modUuidStr     = map.get("moderatorUuid");
                UUID modUuid          = null;
                if (modUuidStr != null && !modUuidStr.equals("null")) {
                    modUuid = UUID.fromString(modUuidStr);
                }
                String moderatorName  = map.getOrDefault("moderatorName", "Konsole");

                list.add(new BanEntry(uuid, playerName, reason, modUuid, moderatorName));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void saveAll(List<BanEntry> entries) {
        try (BufferedWriter w = Files.newBufferedWriter(banFile)) {
            w.write("[\n");
            String joined = entries.stream().map(e ->
                String.format(
                    "  {\"uuid\":\"%s\",\"playerName\":\"%s\",\"reason\":\"%s\",\"moderatorUuid\":\"%s\",\"moderatorName\":\"%s\"}",
                    e.uuid(),
                    e.playerName().replace("\"","\\\""),
                    e.reason().replace("\"","\\\""),
                    e.moderatorUuid() != null ? e.moderatorUuid() : "null",
                    e.moderatorName().replace("\"","\\\"")
                )
            ).collect(Collectors.joining(",\n"));
            w.write(joined);
            w.write("\n]");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
