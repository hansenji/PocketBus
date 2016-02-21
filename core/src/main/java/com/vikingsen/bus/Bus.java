package com.vikingsen.bus;

public interface Bus {
    <T> void register(Class<T> clazz, EventSubscription<? super T> subscription, ThreadMode threadMode);
    void register(Registrar registrar);
    <T> void unregister(Class<T> clazz, EventSubscription<? super T> subscription);
    void unregister(Registrar registrar);
    <T> void post(T event);
    <T> void postSticky(T event);
    <T> void removeSticky(Class<T> clazz);
}
