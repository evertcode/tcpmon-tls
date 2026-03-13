package com.cafeina.tcpmon;

public enum InterceptMode {
    NONE,
    REQUEST,
    RESPONSE,
    BOTH;

    public boolean intercepts(Direction direction) {
        return switch (this) {
            case NONE -> false;
            case REQUEST -> direction == Direction.CLIENT_TO_TARGET;
            case RESPONSE -> direction == Direction.TARGET_TO_CLIENT;
            case BOTH -> true;
        };
    }
}
