package grinwin.promodawn.model;

public class PartnerTransaction {
    private final long id;
    private final String partnerName;
    private final java.util.UUID playerUuid;
    private final String playerName;
    private final String promoCode;
    private final double amount;
    private final double partnerPercent;
    private final double partnerEarned;
    private final long createdAt;

    public PartnerTransaction(long id, String partnerName, java.util.UUID playerUuid, String playerName, String promoCode,
            double amount, double partnerPercent, double partnerEarned, long createdAt) {
        this.id = id;
        this.partnerName = partnerName;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.promoCode = promoCode;
        this.amount = amount;
        this.partnerPercent = partnerPercent;
        this.partnerEarned = partnerEarned;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public String getPartnerName() {
        return partnerName;
    }

    public java.util.UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getPromoCode() {
        return promoCode;
    }

    public double getAmount() {
        return amount;
    }

    public double getPartnerPercent() {
        return partnerPercent;
    }

    public double getPartnerEarned() {
        return partnerEarned;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
