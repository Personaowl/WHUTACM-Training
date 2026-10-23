package com.whut.training.domain.dto;

import java.util.List;

public class HomeOverview {
    private int totalUsers;
    private List<LeaderboardItem> topUsers;
    private Object todayProblem;
    private DailySubmissionSummary dailySubmissionSummary;
    private boolean problemPoolInitializing;

    public int getTotalUsers() {
        return totalUsers;
    }

    public void setTotalUsers(int totalUsers) {
        this.totalUsers = totalUsers;
    }

    public List<LeaderboardItem> getTopUsers() {
        return topUsers;
    }

    public void setTopUsers(List<LeaderboardItem> topUsers) {
        this.topUsers = topUsers;
    }

    public Object getTodayProblem() {
        return todayProblem;
    }

    public void setTodayProblem(Object todayProblem) {
        this.todayProblem = todayProblem;
    }

    public DailySubmissionSummary getDailySubmissionSummary() {
        return dailySubmissionSummary;
    }

    public void setDailySubmissionSummary(DailySubmissionSummary dailySubmissionSummary) {
        this.dailySubmissionSummary = dailySubmissionSummary;
    }

    public boolean isProblemPoolInitializing() {
        return problemPoolInitializing;
    }

    public void setProblemPoolInitializing(boolean problemPoolInitializing) {
        this.problemPoolInitializing = problemPoolInitializing;
    }

    public record DailySubmissionSummary(int todaySubmissions, int todayCheckedInUsers) {
    }
}
