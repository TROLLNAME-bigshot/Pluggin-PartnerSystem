package grinwin.promodawn.model;

import java.util.UUID;

public class PromoUsage {
    private final long id;
    private final UUID playerUuid;
    private final String playerName;
    private final String code;
    private final long usedAt;
    private final long firstJoinAt;

    public PromoUsage(long id, UUID playerUuid, String playerName, String code, long usedAt, long firstJoinAt) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.code = code;
        this.usedAt = usedAt;
        this.firstJoinAt = firstJoinAt;
    }

    public long getId() {
        return id;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getCode() {
        return code;
    }

    public long getUsedAt() {
        return usedAt;
    }

    public long getFirstJoinAt() {
        return firstJoinAt;
    }
}




