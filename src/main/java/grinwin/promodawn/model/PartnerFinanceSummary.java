package grinwin.promodawn.model;

public class PartnerFinanceSummary {
    private final double dayEarned;
    private final double weekEarned;
    private final double monthEarned;
    private final double totalEarned;
    private final double unpaidAmount;
    private final double totalPaid;

    public PartnerFinanceSummary(double dayEarned, double weekEarned, double monthEarned, double totalEarned,
            double unpaidAmount, double totalPaid) {
        this.dayEarned = dayEarned;
        this.weekEarned = weekEarned;
        this.monthEarned = monthEarned;
        this.totalEarned = totalEarned;
        this.unpaidAmount = unpaidAmount;
        this.totalPaid = totalPaid;
    }

    public double getDayEarned() {
        return dayEarned;
    }

    public double getWeekEarned() {
        return weekEarned;
    }

    public double getMonthEarned() {
        return monthEarned;
    }

    public double getTotalEarned() {
        return totalEarned;
    }

    public double getUnpaidAmount() {
        return unpaidAmount;
    }

    public double getTotalPaid() {
        return totalPaid;
    }
}
