package com.visitor.system.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class AdminDashboardResp {
    private final long todayTotal;
    private final long pending;
    private final long approvedToday;
    private final long rejectedToday;
    private final long activeVisitors;
    private final long integrationFailures;
    private final List<DailyTrend> trend;

    @Getter
    @Builder
    public static class DailyTrend {
        private final LocalDate date;
        private final long total;
        private final long approved;
        private final long rejected;
    }
}
