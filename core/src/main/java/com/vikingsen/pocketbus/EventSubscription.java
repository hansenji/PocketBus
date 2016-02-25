package com.vikingsen.pocketbus;

public interface EventSubscription<T> {
    void handle(T t);
    Class<T> getEventClass();
    ThreadMode getThreadMode();
}
