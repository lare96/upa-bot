package me.upa.discord;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

public final class UpaStoreRequest implements Serializable {
    private static final long serialVersionUID = -7562578929566305404L;

    public enum RequestType {
        PAC,
        PROPERTY
    }

    private final long memberId;
    private final long commentId;
    private final long value;
    private final String reason;
    private final RequestType type;

    public UpaStoreRequest(long memberId, long commentId, long value, String reason, RequestType type) {
        this.memberId = memberId;
        this.commentId = commentId;
        this.value = value;
        this.reason = reason;
        this.type = type;
    }

    public long getMemberId() {
        return memberId;
    }

    public long getCommentId() {
        return commentId;
    }

    public long getValue() {
        return value;
    }

    public String getReason() {
        return reason;
    }

    public RequestType getType() {
        return type;
    }
}
