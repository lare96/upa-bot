package me.upa;

import com.google.common.collect.ImmutableSet;

public final class UpaBotConstants {

    public static final ImmutableSet<Long> ADMINS;
    public static final ImmutableSet<Long> STAFF;

    public static final long UNRULY_CJ_MEMBER_ID;
    public static final long HIGH_ROAD_MEMBER_ID;
    public static final long BAMTECH_MEMBER_ID;

    public static final long BUILD_REQUESTS_CHANNEL_ID = 1052734168230006815L;
public static final long GLOBAL_TRAIN_CHANNEL = 1026633109367685131L;
public static final long HOLLIS_TRAIN_CHANNEL = 963108957784772659L;

    static {
        UNRULY_CJ_MEMBER_ID = 220622659665264643L;
        HIGH_ROAD_MEMBER_ID = 373218455937089537L;
        BAMTECH_MEMBER_ID = 200653175127146501L;
        ADMINS = ImmutableSet.of(
                UNRULY_CJ_MEMBER_ID,
                HIGH_ROAD_MEMBER_ID
        );
        STAFF = ImmutableSet.of(
                UNRULY_CJ_MEMBER_ID,
                HIGH_ROAD_MEMBER_ID,
                BAMTECH_MEMBER_ID
        );
    }

    private UpaBotConstants() {
    }
}
