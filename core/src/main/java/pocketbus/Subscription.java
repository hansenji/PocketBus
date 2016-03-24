package pocketbus;

public interface Subscription<T> {
    void handle(T t);
    Class<T> getEventClass();
    ThreadMode getThreadMode();
    <E> E getTarget();
}
