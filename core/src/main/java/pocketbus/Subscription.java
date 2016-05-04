package pocketbus;

public interface Subscription<T> {
    boolean handle(T t);
    Class<T> getEventClass();
    ThreadMode getThreadMode();
    <E> E getTarget();
}
