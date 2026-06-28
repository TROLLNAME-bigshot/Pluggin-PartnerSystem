package grinwin.promodawn.model;

import java.util.List;

public class MyPromoMenuData {
    private final PromoCode code;
    private final StatsSnapshot usageSnapshot;
    private final PartnerFinanceSummary financeSummary;
    private final List<PartnerTransaction> transactions;

    public MyPromoMenuData(PromoCode code, StatsSnapshot usageSnapshot, PartnerFinanceSummary financeSummary,
            List<PartnerTransaction> transactions) {
        this.code = code;
        this.usageSnapshot = usageSnapshot;
        this.financeSummary = financeSummary;
        this.transactions = transactions;
    }

    public PromoCode getCode() {
        return code;
    }

    public StatsSnapshot getUsageSnapshot() {
        return usageSnapshot;
    }

    public PartnerFinanceSummary getFinanceSummary() {
        return financeSummary;
    }

    public List<PartnerTransaction> getTransactions() {
        return transactions;
    }
}
