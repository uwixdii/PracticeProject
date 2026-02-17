package org.example;

public class RequestItemDetail {
    private final int id;
    private final int itemId;
    private final String itemName;
    private final int quantity;
    private final String status;

    public RequestItemDetail(int id, int itemId, String itemName, int quantity, String status) {
        this.id = id;
        this.itemId = itemId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.status = status;
    }

    public int getId() { return id; }
    public int getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public int getQuantity() { return quantity; }
    public String getStatus() { return status; }
}