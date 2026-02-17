package org.example;

public class User {
    private final int id;
    private final String username;
    private final String role;
    private final int workshopNumber;

    public User(int id, String username, String role, int workshopNumber) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.workshopNumber = workshopNumber;
    }

    public int    getId()             { return id; }
    public String getUsername()       { return username; }
    public String getRole()           { return role; }
    public int    getWorkshopNumber() { return workshopNumber; }

    @Override
    public String toString() {
        return username + " (" + role + (workshopNumber > 0 ? ", цех " + workshopNumber : "") + ")";
    }
}