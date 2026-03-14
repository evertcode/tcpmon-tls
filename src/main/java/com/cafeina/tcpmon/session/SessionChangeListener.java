package com.cafeina.tcpmon.session;

@FunctionalInterface
public interface SessionChangeListener {
    void onSessionChange(SessionChangeEvent event);
}
