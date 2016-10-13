package pocketbus;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import pocketbus.internal.Registry;
import rx.Observable;
import rx.Scheduler;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

public class Bus {
    private static final String TAG = "PocketBus";
    private static final Object DEFAULT_LOCK = new Object();

    private static Bus defaultBus;
    private static boolean debug = false;

    @NonNull
    private final Scheduler mainScheduler;
    @NonNull
    private final Scheduler currentScheduler;
    @NonNull
    private final Scheduler backgroundScheduler;

    protected final int eventCleanupCount;
    @NonNull
    private final AtomicInteger eventCounter = new AtomicInteger();

    @NonNull
    private final Map<Class, List<Subscription>> mainThreadListeners = new LinkedHashMap<>();
    @NonNull
    private final Map<Class, List<Subscription>> backgroundThreadListeners = new LinkedHashMap<>();
    @NonNull
    private final Map<Class, List<Subscription>> currentThreadListeners = new LinkedHashMap<>();

    @NonNull
    private final Map<Class<?>, ? super Object> stickyEvents = new LinkedHashMap<>();
    @NonNull
    private final Object listenerLock = new Object();
    @NonNull
    private final Object stickyLock = new Object();
    @Nullable
    private Registry registry = null;

    private Bus(@NonNull Scheduler mainScheduler, @NonNull Scheduler currentScheduler, @NonNull Scheduler backgroundScheduler, int eventCleanupCount) {
        this.mainScheduler = mainScheduler;
        this.currentScheduler = currentScheduler;
        this.backgroundScheduler = backgroundScheduler;
        this.eventCleanupCount = eventCleanupCount;
    }

    /**
     * @return the default bus instance
     */
    @NonNull
    public static Bus getDefault() {
        synchronized (DEFAULT_LOCK) {
            if (defaultBus == null) {
                defaultBus = new Builder().build();
            }
            return defaultBus;
        }
    }

    /**
     * Sets the default bus instance
     *
     * @param bus
     */
    public synchronized static void setDefault(@NonNull Bus bus) {
        synchronized (DEFAULT_LOCK) {
            defaultBus = bus;
        }
    }

    /**
     * Enable or disable debug logging
     *
     * @param enable enable/disable debug logging
     */
    public static void setDebug(boolean enable) {
        debug = enable;
    }

    /**
     * Set the registry for registering subscriptions
     *
     * @param registry the registry of subscriptions
     */
    public void setRegistry(@Nullable Registry registry) {
        this.registry = registry;
    }

    /**
     * Register a target that has subscribe annotations.
     *
     * @param target the target that has subscribe annotations
     * @throws IllegalArgumentException if the target has no entry in the registry
     */
    public <T> void register(@NonNull T target) {
        SubscriptionRegistration subscriptionRegistration = null;
        if (registry != null) {
            subscriptionRegistration = registry.getRegistration(target);
        }
        if (subscriptionRegistration != null) {
            register(subscriptionRegistration);
        } else {
            throw new IllegalArgumentException("Register failed to find subscriptionRegistration for " + target.getClass() + " please check your registry");
        }
    }

    /**
     * Register a single subscription with the bus
     *
     * @param subscription the subscription to register
     * @throws NullPointerException if one of the subscription.getEventClass() returns null
     */
    public <T> void register(@NonNull Subscription<? super T> subscription) {
        register(subscription, true);
    }

    /**
     * Registers a list of subscriptions from the subscriptionRegistration
     *
     * @param subscriptionRegistration the registration to register
     * @throws NullPointerException if subscription.getEventClass() returns null
     */
    protected void register(@NonNull SubscriptionRegistration subscriptionRegistration) {
        List<Subscription<?>> subscriptions = subscriptionRegistration.getSubscriptions();
        for (Subscription subscription : subscriptions) {
            register(subscription, false);
        }
        for (Subscription subscription : subscriptions) {
            postStickyOnRegistration(subscription);
        }
    }

    protected <T> void register(@NonNull Subscription<? super T> subscription, boolean postStickyEvents) {
        Map<Class, List<Subscription>> listenerMap;
        ThreadMode threadMode = subscription.getThreadMode();
        switch (threadMode) {
            case MAIN:
                listenerMap = mainThreadListeners;
                break;
            case BACKGROUND:
                listenerMap = backgroundThreadListeners;
                break;
            case CURRENT:
                listenerMap = currentThreadListeners;
                break;
            default:
                throw new IllegalArgumentException("Invalid thread mode " + threadMode);
        }

        Class<? super T> eventClass = subscription.getEventClass();
        if (eventClass == null) {
            throw new NullPointerException("Subscription.getEventClass() cannot be null");
        }
        synchronized (listenerLock) {
            if (!listenerMap.containsKey(eventClass)) {
                listenerMap.put(eventClass, new LinkedList<Subscription>());
            }

            listenerMap.get(eventClass).add(subscription);
            if (postStickyEvents) {
                postStickyOnRegistration(subscription);
            }
        }
        log("Registered subscription for " + eventClass + " on ThreadMode." + threadMode);
    }

    /**
     * Unregister a single subscription with the bus
     *
     * @param subscription the subscription to unregister
     * @throws NullPointerException if subscription.getEventClass() returns null
     */
    protected <T> void unregister(@NonNull Subscription<? super T> subscription) {
        Map<Class, List<Subscription>> listenerMap;

        ThreadMode threadMode = subscription.getThreadMode();
        switch (threadMode) {
            case MAIN:
                listenerMap = mainThreadListeners;
                break;
            case BACKGROUND:
                listenerMap = backgroundThreadListeners;
                break;
            case CURRENT:
                listenerMap = currentThreadListeners;
                break;
            default:
                throw new IllegalArgumentException("Invalid thread mode " + threadMode);
        }

        Class<? super T> eventClass = subscription.getEventClass();
        if (eventClass == null) {
            throw new NullPointerException("Subscription.getEventClass() cannot be null");
        }
        synchronized (listenerLock) {
            List<Subscription> subscriptions = listenerMap.get(eventClass);
            if (subscriptions != null) {
                unregister(subscription, subscriptions);
            }
        }
        log("Unregistered subscription for " + eventClass + " on ThreadMode." + threadMode);
    }

    /**
     * Unregister a target that has subscribe annotations.
     *
     * @param target the target that has subscribe annotations
     * @throws IllegalArgumentException if the target has no entry in the registry
     */
    public <T> void unregister(@NonNull T target) {
        SubscriptionRegistration subscriptionRegistration = null;
        if (registry != null) {
            subscriptionRegistration = registry.getRegistration(target);
        }
        if (subscriptionRegistration != null) {
            unregister(subscriptionRegistration);
        } else {
            throw new IllegalArgumentException("Unregister failed to find subscriptionRegistration for " + target.getClass() + " please check your registry");
        }
    }

    /**
     * Unregisters a list of subscriptions from the subscriptionRegistration
     *
     * @param subscriptionRegistration the registration to unregister
     * @throws NullPointerException if subscription.getEventClass() returns null
     */
    protected void unregister(@NonNull SubscriptionRegistration subscriptionRegistration) {
        for (Subscription subscription : subscriptionRegistration.getSubscriptions()) {
            unregister(subscription);
        }
    }

    /**
     * Deliver the event to all registered subscriptions for this event type including super classes
     * Note a subscription that takes Object will receive all events.
     *
     * @param event the event to deliver to subscriptions
     */
    public <T> void post(T event) {
        if (event == null) {
            throw new NullPointerException("Event cannot be null");
        }
        synchronized (listenerLock) {
            for (Map.Entry<Class, List<Subscription>> entry : currentThreadListeners.entrySet()) {
                checkAndPost(entry.getKey(), event, entry.getValue(), ThreadMode.CURRENT);
            }

            for (Map.Entry<Class, List<Subscription>> entry : mainThreadListeners.entrySet()) {
                checkAndPost(entry.getKey(), event, entry.getValue(), ThreadMode.MAIN);
            }

            for (Map.Entry<Class, List<Subscription>> entry : backgroundThreadListeners.entrySet()) {
                checkAndPost(entry.getKey(), event, entry.getValue(), ThreadMode.BACKGROUND);
            }
        }

        int counter = eventCounter.incrementAndGet();
        log("EventCounter: " + counter);
        if (counter >= eventCleanupCount) {
            eventCounter.set(0);
            Observable.just(0)
                    .subscribeOn(backgroundScheduler)
                    .subscribe(new Action1<Integer>() {
                        @Override
                        public void call(Integer integer) {
                            cleanupReferences();
                        }
                    });
        }
    }

    private <T> void checkAndPost(@NonNull Class subscriptionClazz, @NonNull T event, @NonNull List<Subscription> subscriptions,
                                  @NonNull ThreadMode threadMode) {
        if (subscriptionClazz.isInstance(event)) {
            post(event, subscriptions, threadMode);
            log("Event<" + event.getClass() + "> posted to Subscription<" + subscriptionClazz + "> on ThreadMode." + threadMode);
        }
    }

    /**
     * @deprecated This should never be used random acts of terror. Use a singleton class and check its state.
     *
     * Similar to {@code post} but the event is saved and delivered to subscriptions of matching types when they are registered.
     * Only the last event of each type is saved and delivered.
     *
     * @param event the event to post and save to be delivered on registration
     */
    @Deprecated
    public <T> void postSticky(T event) {
        if (event == null) {
            throw new NullPointerException("Event cannot be null");
        }
        synchronized (stickyLock) {
            stickyEvents.put(event.getClass(), event);
        }
        post(event);
    }

    /**
     * @deprecated  This should never by used see {@code postSticky}
     *
     * Remove the Sticky event of type eventClass
     *
     * @param eventClass the eventClass type to remove from the sticky store
     * @return true if a sticky event was removed
     */
    @Deprecated
    public <T> boolean removeSticky(@NonNull Class<T> eventClass) {
        synchronized (stickyLock) {
            return stickyEvents.remove(eventClass) != null;
        }
    }

    /**
     * @deprecated  This should never by used see {@code postSticky}

     * Return the sticky event of tyep eventClass if it exists in the sticky store otherwise return null
     *
     * @param eventClass
     * @return the sticky event of type eventClass if found in the sticky store null otherwise
     */
    @Nullable
    @Deprecated
    public <T> T getSticky(@NonNull Class<T> eventClass) {
        synchronized (stickyLock) {
            //noinspection unchecked
            return (T) stickyEvents.get(eventClass);
        }
    }

    /**
     * Used to post sticky events to a newly registered listener
     */
    @Deprecated
    private <T> void postStickyOnRegistration(@NonNull Subscription<? super T> subscription) {
        Class<? super T> eventClass = subscription.getEventClass(); // This check is handled by register.
        ThreadMode threadMode = subscription.getThreadMode();
        synchronized (stickyLock) {
            for (Map.Entry<Class<?>, ? super Object> entry : stickyEvents.entrySet()) {
                Class<?> stickyClass = entry.getKey();
                if (eventClass.isAssignableFrom(stickyClass)) {
                    post(entry.getValue(), Collections.singletonList((Subscription) subscription), threadMode);
                    log("Sticky Event<" + stickyClass + "> posted to Subscription<" + eventClass + "> on ThreadMode." + threadMode);
                }
            }
        }
    }

    private <T> void post(@NonNull T event, @NonNull List<Subscription> subscriptions, @NonNull ThreadMode threadMode) {
        Observable.just(new SubscriptionsStore<>(event, new ArrayList<>(subscriptions), threadMode))
                .subscribeOn(getScheduler(threadMode))
                .subscribe(new Action1<SubscriptionsStore<T>>() {
                    @Override
                    public void call(SubscriptionsStore<T> store) {
                        performPost(store);
                    }
                });
    }

    private <T> void performPost(@NonNull SubscriptionsStore<T> store) {
        for (Subscription subscription : store.subscriptions) {
            //noinspection unchecked
            if (!subscription.handle(store.event)) {
                Observable.just(new SubscriptionStore<>(store.event, null, store.threadMode))
                        .subscribeOn(backgroundScheduler)
                        .subscribe(new Action1<SubscriptionStore<T>>() {
                            @Override
                            public void call(SubscriptionStore<T> store) {
                                unregister(store);
                            }
                        });
            }
        }
    }

    private <T> void unregister(@NonNull SubscriptionStore<T> store) {
        Map<Class, List<Subscription>> listenerMap;

        switch (store.threadMode) {
            case MAIN:
                listenerMap = mainThreadListeners;
                break;
            case BACKGROUND:
                listenerMap = backgroundThreadListeners;
                break;
            case CURRENT:
                listenerMap = currentThreadListeners;
                break;
            default:
                throw new IllegalArgumentException("Invalid thread mode " + store.threadMode);
        }

        synchronized (listenerLock) {
            for (Map.Entry<Class, List<Subscription>> entry : listenerMap.entrySet()) {
                if (entry.getKey().isInstance(store.event)) {
                    unregister(store.subscription, entry.getValue());
                }
            }
        }
    }

    private <T> void unregister(@Nullable Subscription<T> registeredSubscription, @NonNull List<Subscription> subscriptions) {
        Iterator<Subscription> iterator = subscriptions.iterator();

        while (iterator.hasNext()) {
            Subscription subscription = iterator.next();
            if (subscription.getTarget() == null || subscription.equals(registeredSubscription)) {
                iterator.remove();
            }
        }
    }

    @NonNull
    private Scheduler getScheduler(@NonNull ThreadMode threadMode) {
        switch (threadMode) {
            case CURRENT:
                return currentScheduler;
            case MAIN:
                return mainScheduler;
            case BACKGROUND:
                return backgroundScheduler;
            default:
                throw new IllegalArgumentException("Invalid ThreadMode: " + threadMode);
        }
    }

    private void cleanupReferences() {
        synchronized (listenerLock) {
            cleanupReferences(mainThreadListeners, ThreadMode.MAIN);
            cleanupReferences(backgroundThreadListeners, ThreadMode.BACKGROUND);
            cleanupReferences(currentThreadListeners, ThreadMode.CURRENT);
        }
    }

    private void cleanupReferences(@NonNull Map<Class, List<Subscription>> listeners, @NonNull ThreadMode threadMode) {
        int cleanupCount = 0;
        for (List<Subscription> references : listeners.values()) {
            Iterator<Subscription> iterator = references.iterator();
            while (iterator.hasNext()) {
                Subscription subscription = iterator.next();
                if (subscription.getTarget() == null) {
                    iterator.remove();
                    cleanupCount++;
                }
            }
        }
        log("cleanupReferences: " + cleanupCount + " for ThreadMode." + threadMode);
    }

    private void log(@NonNull String msg) {
        if (debug) {
            Log.d(TAG, msg);
        }
    }

    private static class SubscriptionsStore<T> {
        @NonNull
        final T event;
        @NonNull
        final ThreadMode threadMode;
        @NonNull
        final List<Subscription> subscriptions;

        public SubscriptionsStore(@NonNull T event, @NonNull List<Subscription> subscriptions, @NonNull ThreadMode threadMode) {
            this.event = event;
            this.threadMode = threadMode;
            this.subscriptions = subscriptions;
        }
    }

    private static class SubscriptionStore<T> {
        @NonNull
        final T event;
        @NonNull
        final ThreadMode threadMode;
        @Nullable
        final Subscription subscription;

        public SubscriptionStore(@NonNull T event, @Nullable Subscription subscription, @NonNull ThreadMode threadMode) {
            this.event = event;
            this.threadMode = threadMode;
            this.subscription = subscription;
        }
    }

    /**
     * Builds an instance of the {@link Bus}
     */
    public static class Builder {
        private static final int DEFAULT_BACKGROUND_THREAD_POOL_SIZE = 2;
        private static final int DEFAULT_EVENT_CLEANUP_COUNT = 100;

        @Nullable
        private Scheduler mainScheduler;
        @Nullable
        private Scheduler currentScheduler;
        @Nullable
        private Scheduler backgroundScheduler;
        protected int backgroundThreadPoolSize = DEFAULT_BACKGROUND_THREAD_POOL_SIZE;
        protected int eventCleanupCount = DEFAULT_EVENT_CLEANUP_COUNT;

        /**
         * Set the RxScheduler to use for ThreadMode.MAIN
         * <p>
         * Default AndroidSchedulers.mainThread()
         *
         * @param scheduler the RxScheduler for the ThreadMode.MAIN
         * @return This builder to allow for chaining calls to set methods
         */
        @NonNull
        public Builder setMainScheduler(@NonNull Scheduler scheduler) {
            this.mainScheduler = scheduler;
            return this;
        }

        /**
         * Set the RxScheduler to use for ThreadMode.CURRENT
         * <p>
         * Default Schedulers.trampoline()
         *
         * @param scheduler the RxScheduler for the ThreadMode.CURRENT
         * @return This builder to allow for chaining calls to set methods
         */
        @NonNull
        public Builder setCurrentScheduler(@NonNull Scheduler scheduler) {
            this.currentScheduler = scheduler;
            return this;
        }

        /**
         * Set the RxScheduler to use for ThreadMode.BACKGROUND
         * <p>
         * Default Schedulers.from(Executors.newFixedThreadPool(backgroundThreadPoolSize))
         *
         * @param scheduler the RxScheduler for the ThreadMode.BACKGROUND
         * @return This builder to allow for chaining calls to set methods
         */
        @NonNull
        public Builder setBackgroundScheduler(@NonNull Scheduler scheduler) {
            this.backgroundScheduler = scheduler;
            return this;
        }

        /**
         * Set the number of threads for ThreadMode.BACKGROUND
         * Only used if the default BackgroundScheduler is used.
         * <p>
         * Default is {@literal 2}
         *
         * @param threadPoolSize The number of threads for the background ThreadMode
         * @return This builder to allow for chaining calls to set methods
         */
        @NonNull
        public Builder setBackgroundThreadPoolSize(int threadPoolSize) {
            if (threadPoolSize < 1) {
                throw new IllegalArgumentException("Thread pool size must be >= 1");
            }
            this.backgroundThreadPoolSize = threadPoolSize;
            return this;
        }

        /**
         * Set the number of events between cleanup of subscriptions
         *
         * Default is {@literal 100}
         *
         * @param eventCleanupCount number of events between cleanup of subscriptions
         * @return This builder to allow for chaining calls to set methods
         */
        @NonNull
        public Builder setEventCleanupCount(int eventCleanupCount) {
            this.eventCleanupCount = eventCleanupCount;
            return this;
        }

        /**
         * Builds a new instance of the bus with the given configuration
         *
         * @return the new instance of the bus
         */
        @NonNull
        public Bus build() {
            if (mainScheduler == null) {
                mainScheduler = AndroidSchedulers.mainThread();
            }
            if (currentScheduler == null) {
                currentScheduler = Schedulers.trampoline();
            }
            if (backgroundScheduler == null) {
                backgroundScheduler = Schedulers.from(Executors.newFixedThreadPool(backgroundThreadPoolSize));
            }
            return new Bus(mainScheduler, currentScheduler, backgroundScheduler, eventCleanupCount);
        }
    }
}