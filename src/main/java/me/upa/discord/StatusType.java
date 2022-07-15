package me.upa.discord;

public enum StatusType {
    ONLINE("<application> is now online."),
    ONLINE_RESTARTED("<application> has been restarted."),
    ONLINE_BETA("<application> is now online in beta mode. Expect minor downtime."),
    OFFLINE_ERROR("<application> is now offline due to an error."),
    OFFLINE_RESTARTING("<application> is now restarting."),
    OFFLINE("<application> is now offline.");

    private final String description;

    StatusType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
