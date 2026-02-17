package org.example;

public class RequestSummary {
    private final int id;
    private final String number;
    private final String status;
    private final String createdAt;
    private final int itemCount;
    private final int totalQuantity;

    public RequestSummary(int id, String number, String status, String createdAt, int itemCount, int totalQuantity) {
        this.id = id;
        this.number = number;
        this.status = status;
        this.createdAt = createdAt;
        this.itemCount = itemCount;
        this.totalQuantity = totalQuantity;
    }

    public int getId() { return id; }
    public String getNumber() { return number; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }
    public int getItemCount() { return itemCount; }
    public int getTotalQuantity() { return totalQuantity; }
}