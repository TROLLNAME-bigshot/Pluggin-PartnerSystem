package grinwin.promodawn.model;

public class PromoCode {
    private final String code;
    private final String owner;
    private final String rewardKey;
    private final long createdAt;
    private final double partnerPercent;

    private final int maxUses;
    private final boolean ignoreGlobalLimit;
    private final java.util.List<String> successMessages;
    private final java.util.List<String> failureMessages;

    public PromoCode(String code, String owner, String rewardKey, long createdAt) {
        this(code, owner, rewardKey, createdAt, 0.0D, -1, false, null, null);
    }

    public PromoCode(String code, String owner, String rewardKey, long createdAt, int maxUses,
            java.util.List<String> successMessages, java.util.List<String> failureMessages) {
        this(code, owner, rewardKey, createdAt, 0.0D, maxUses, true, successMessages, failureMessages);
    }

    public PromoCode(String code, String owner, String rewardKey, long createdAt, int maxUses,
            boolean ignoreGlobalLimit, java.util.List<String> successMessages, java.util.List<String> failureMessages) {
        this(code, owner, rewardKey, createdAt, 0.0D, maxUses, ignoreGlobalLimit, successMessages, failureMessages);
    }

    public PromoCode(String code, String owner, String rewardKey, long createdAt, double partnerPercent, int maxUses,
            boolean ignoreGlobalLimit, java.util.List<String> successMessages, java.util.List<String> failureMessages) {
        this.code = code;
        this.owner = owner;
        this.rewardKey = rewardKey;
        this.createdAt = createdAt;
        this.partnerPercent = partnerPercent;
        this.maxUses = maxUses;
        this.ignoreGlobalLimit = ignoreGlobalLimit;
        this.successMessages = successMessages;
        this.failureMessages = failureMessages;
    }

    public String getCode() {
        return code;
    }

    public String getOwner() {
        return owner;
    }

    public String getRewardKey() {
        return rewardKey;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public double getPartnerPercent() {
        return partnerPercent;
    }

    public int getMaxUses() {
        return maxUses;
    }

    public boolean isIgnoreGlobalLimit() {
        return ignoreGlobalLimit;
    }

    public java.util.List<String> getSuccessMessages() {
        return successMessages;
    }

    public java.util.List<String> getFailureMessages() {
        return failureMessages;
    }
}
