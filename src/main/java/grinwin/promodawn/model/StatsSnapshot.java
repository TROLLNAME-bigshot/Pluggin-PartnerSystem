package grinwin.promodawn.model;

import java.util.List;

public class StatsSnapshot {
    private final int dayCount;
    private final int weekCount;
    private final int totalCount;
    private final Integer monthCount; // nullable for backward compat
    private final List<PromoUsage> usagesPage;

    public StatsSnapshot(int dayCount, int weekCount, int totalCount, List<PromoUsage> usagesPage) {
        this(dayCount, weekCount, totalCount, null, usagesPage);
    }

    public StatsSnapshot(int dayCount, int weekCount, int totalCount, Integer monthCount, List<PromoUsage> usagesPage) {
        this.dayCount = dayCount;
        this.weekCount = weekCount;
        this.totalCount = totalCount;
        this.monthCount = monthCount;
        this.usagesPage = usagesPage;
    }

    public int getDayCount() { return dayCount; }
    public int getWeekCount() { return weekCount; }
    public int getTotalCount() { return totalCount; }
    public List<PromoUsage> getUsagesPage() { return usagesPage; }
    public Integer getMonthCount() { return monthCount; }
}


