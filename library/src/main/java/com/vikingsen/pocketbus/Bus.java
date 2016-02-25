package com.vikingsen.pocketbus;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import rx.Observable;
import rx.Scheduler;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

public class Bus {
    private static final String TAG = "PocketBus";

    @NonNull
    private static Bus defaultBus = new Builder().build();
    private static boolean debug = false;

    @NonNull
    private final Scheduler mainScheduler;
    @NonNull
    private final Scheduler currentScheduler;
    @NonNull
    private final Scheduler backgroundScheduler;

    private final int eventCleanupCount;
    @NonNull
    private final AtomicInteger eventCounter = new AtomicInteger();

    @NonNull
    private final Map<Class, List<WeakReference<Subscription>>> mainThreadListeners = new LinkedHashMap<>();
    @NonNull
    private final Map<Class, List<WeakReference<Subscription>>> backgroundThreadListeners = new LinkedHashMap<>();
    @NonNull
    private final Map<Class, List<WeakReference<Subscription>>> currentThreadListeners = new LinkedHashMap<>();

    @NonNull
    private final Map<Class<?>, ? super Object> stickyEvents = new LinkedHashMap<>();
    @NonNull
    private final Object listenerLock = new Object();
    @NonNull
    private final Object stickyLock = new Object();


    private Bus(@NonNull Scheduler mainScheduler, @NonNull Scheduler currentScheduler, @NonNull Scheduler backgroundScheduler, int eventCleanupCount) {
        this.mainScheduler = mainScheduler;
        this.currentScheduler = currentScheduler;
        this.backgroundScheduler = backgroundScheduler;
        this.eventCleanupCount = eventCleanupCount;
    }

    @NonNull
    public static Bus getDefault() {
        return defaultBus;
    }

    public static void setDefault(@NonNull Bus bus) {
        defaultBus = bus;
    }

    public static void setDebug(boolean enable) {
        debug = enable;
    }

    public <T> void register(@NonNull Subscription<? super T> subscription) {
        register(subscription, true);
    }

    public void register(@NonNull Registrar registrar) {
        List<Subscription<?>> subscriptions = registrar.getSubscriptions();
        for (Subscription subscription : subscriptions) {
            register(subscription, false);
        }
        for (Subscription subscription : subscriptions) {
            postStickyOnRegistration(subscription);
        }
    }

    private <T> void register(@NonNull Subscription<? super T> subscription, boolean postStickyEvents) {
        Map<Class, List<WeakReference<Subscription>>> listenerMap;
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
        synchronized (listenerLock) {
            if (!listenerMap.containsKey(eventClass)) {
                listenerMap.put(eventClass, new LinkedList<WeakReference<Subscription>>());
            }

            listenerMap.get(eventClass).add(new WeakReference<Subscription>(subscription));
            if (postStickyEvents) {
                postStickyOnRegistration(subscription);
            }
        }
        log("Registered subscription for " + eventClass + " on ThreadMode." + threadMode);
    }

    public <T> void unregister(@NonNull Subscription<? super T> subscription) {
        Map<Class, List<WeakReference<Subscription>>> listenerMap;

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
        synchronized (listenerLock) {
            List<WeakReference<Subscription>> subscriptions = listenerMap.get(eventClass);
            if (subscriptions != null) {
                unregister(subscription, subscriptions);
            }
        }
        log("Unregistered subscription for " + eventClass + " on ThreadMode." + threadMode);
    }

    public void unregister(@NonNull Registrar registrar) {
        for (Subscription subscription : registrar.getSubscriptions()) {
            unregister(subscription);
        }
    }

    public <T> void post(@NonNull T event) {
        synchronized (listenerLock) {
            for (Map.Entry<Class, List<WeakReference<Subscription>>> entry : currentThreadListeners.entrySet()) {
                checkAndPost(entry.getKey(), event, entry.getValue(), ThreadMode.CURRENT);
            }

            for (Map.Entry<Class, List<WeakReference<Subscription>>> entry : mainThreadListeners.entrySet()) {
                checkAndPost(entry.getKey(), event, entry.getValue(), ThreadMode.MAIN);
            }

            for (Map.Entry<Class, List<WeakReference<Subscription>>> entry : backgroundThreadListeners.entrySet()) {
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
                            cleanupWeakReferences();
                        }
                    });
        }
    }

    private <T> void checkAndPost(@NonNull Class subscriptionClazz, @NonNull T event, @NonNull List<WeakReference<Subscription>> subscriptions,
                                  @NonNull ThreadMode threadMode) {
        if (subscriptionClazz.isInstance(event)) {
            post(event, subscriptions, threadMode);
            log("Event<" + event.getClass() + "> posted to Subscription<" + subscriptionClazz + "> on ThreadMode." + threadMode);
        }
    }

    public <T> void postSticky(@NonNull T event) {
        synchronized (stickyLock) {
            stickyEvents.put(event.getClass(), event);
        }
        post(event);
    }

    public <T> void removeSticky(@NonNull Class<T> eventClass) {
        synchronized (stickyLock) {
            stickyEvents.remove(eventClass);
        }
    }

    /**
     * Used to post sticky events to a newly registered listener
     */
    private <T> void postStickyOnRegistration(@NonNull Subscription<? super T> subscription) {
        Class<? super T> eventClass = subscription.getEventClass();
        ThreadMode threadMode = subscription.getThreadMode();
        synchronized (stickyLock) {
            for (Map.Entry<Class<?>, ? super Object> entry : stickyEvents.entrySet()) {
                Class<?> stickyClass = entry.getKey();
                if (eventClass.isAssignableFrom(stickyClass)) {
                    post(entry.getValue(), Collections.singletonList(new WeakReference<Subscription>(subscription)), threadMode);
                    log("Sticky Event<" + stickyClass + "> posted to Subscription<" + eventClass + "> on ThreadMode." + threadMode);
                }
            }
        }
    }

    private <T> void post(@NonNull T event, @NonNull List<WeakReference<Subscription>> subscriptions, @NonNull ThreadMode threadMode) {
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
        for (WeakReference<Subscription> subscriptionRef : store.subscriptions) {
            Subscription subscription = subscriptionRef.get();
            if (subscription != null) {
                //noinspection unchecked
                subscription.handle(store.event);
            } else {
                Observable.just(new SubscriptionStore<>(store.event, subscription, store.threadMode))
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
        Map<Class, List<WeakReference<Subscription>>> listenerMap;

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
            for (Map.Entry<Class, List<WeakReference<Subscription>>> entry : listenerMap.entrySet()) {
                if (entry.getKey().isInstance(store.event)) {
                    unregister(store.subscription, entry.getValue());
                }
            }
        }
    }

    private <T> void unregister(@NonNull Subscription<T> registeredSubscription, @NonNull List<WeakReference<Subscription>> subscriptions) {
        Iterator<WeakReference<Subscription>> iterator = subscriptions.iterator();

        while (iterator.hasNext()) {
            Subscription subscription = iterator.next().get();
            if (subscription == null || subscription.equals(registeredSubscription)) {
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

    private void cleanupWeakReferences() {
        synchronized (listenerLock) {
            cleanupWeakReferences(mainThreadListeners, ThreadMode.MAIN);
            cleanupWeakReferences(backgroundThreadListeners, ThreadMode.BACKGROUND);
            cleanupWeakReferences(currentThreadListeners, ThreadMode.CURRENT);
        }
    }

    private void cleanupWeakReferences(@NonNull Map<Class, List<WeakReference<Subscription>>> listeners, @NonNull ThreadMode threadMode) {
        int cleanupCount = 0;
        for (List<WeakReference<Subscription>> weakReferences : listeners.values()) {
            Iterator<WeakReference<Subscription>> iterator = weakReferences.iterator();
            while (iterator.hasNext()) {
                Subscription subscription = iterator.next().get();
                if (subscription == null) {
                    iterator.remove();
                    cleanupCount++;
                }
            }
        }
        log("cleanupWeakReferences: " + cleanupCount + " for ThreadMode." + threadMode);
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
        final List<WeakReference<Subscription>> subscriptions;

        public SubscriptionsStore(@NonNull T event, @NonNull List<WeakReference<Subscription>> subscriptions, @NonNull ThreadMode threadMode) {
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
        @NonNull
        final Subscription subscription;

        public SubscriptionStore(@NonNull T event, @NonNull Subscription subscription, @NonNull ThreadMode threadMode) {
            this.event = event;
            this.threadMode = threadMode;
            this.subscription = subscription;
        }
    }

    public static class Builder {
        private static final int DEFAULT_BACKGROUND_THREAD_POOL_SIZE = 2;
        private static final int DEFAULT_EVENT_CLEANUP_COUNT = 100;

        @Nullable
        private Scheduler mainScheduler;
        @Nullable
        private Scheduler currentScheduler;
        @Nullable
        private Scheduler backgroundScheduler;
        private int backgroundThreadPoolSize = DEFAULT_BACKGROUND_THREAD_POOL_SIZE;
        private int eventCleanupCount = DEFAULT_EVENT_CLEANUP_COUNT;

        @NonNull
        public Builder setMainScheduler(@NonNull Scheduler scheduler) {
            this.mainScheduler = scheduler;
            return this;
        }

        @NonNull
        public Builder setCurrentScheduler(@NonNull Scheduler scheduler) {
            this.currentScheduler = scheduler;
            return this;
        }

        @NonNull
        public Builder setBackgroundScheduler(@NonNull Scheduler scheduler) {
            this.backgroundScheduler = scheduler;
            return this;
        }

        @NonNull
        public Builder setBackgroundThreadPoolSize(int threadPoolSize) {
            if (threadPoolSize < 1) {
                throw new IllegalArgumentException("Thread pool size must be >= 1");
            }
            this.backgroundThreadPoolSize = threadPoolSize;
            return this;
        }

        @NonNull
        public Builder setEventCleanupCount(int eventCleanupCount) {
            this.eventCleanupCount = eventCleanupCount;
            return this;
        }

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