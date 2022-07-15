package me.upa.game;

import java.time.Instant;
import java.util.List;

public final class PropertyVisitor {

    public static boolean hasNewVisitors(List<PropertyVisitor> visitors, Instant lastChecked) {
        if(visitors.isEmpty())
            return false;
        PropertyVisitor first = visitors.get(0);
        if (first.getVisitedAt().isAfter(lastChecked)) {
            return true;
        }
        return false;
    }

    private final int price;
    private final Instant visitedAt;
    private final boolean pending;
    private final String username;

    public PropertyVisitor(int price, Instant visitedAt, String username, boolean pending) {
        this.price = price;
        this.visitedAt = visitedAt;
        this.username = username;
        this.pending = pending;
    }

    public int getPrice() {
        return price;
    }

    public Instant getVisitedAt() {
        return visitedAt;
    }

    public boolean isPending() {
        return pending;
    }

    public String getUsername() {
        return username;
    }
}
