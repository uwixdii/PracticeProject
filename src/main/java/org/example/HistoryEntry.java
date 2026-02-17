package org.example;

public class HistoryEntry {
    private final String action;
    private final String details;
    private final String byUser;
    private final String timestamp;

    public HistoryEntry(String action, String details, String byUser, String timestamp) {
        this.action = action;
        this.details = details;
        this.byUser = byUser;
        this.timestamp = timestamp;
    }

    public String getAction() { return action; }
    public String getDetails() { return details; }
    public String getByUser() { return byUser; }
    public String getTimestamp() { return timestamp; }
}