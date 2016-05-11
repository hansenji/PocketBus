package pocketbus;

public interface Subscription<T> {
    /**
     * Handle an event posted to the bus
     *
     * @param t event posted to the bus
     * @return false if subscription should be unregistered
     */
    boolean handle(T t);

    /**
     * @return Class of event this subscription handles
     */
    Class<T> getEventClass();

    /**
     * @return ThreadMode for this subscription
     */
    ThreadMode getThreadMode();

    /**
     * @return the target with which this subscription interacts
     */
    <E> E getTarget();
}
