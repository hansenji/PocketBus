package com.vikingsen.bus;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import rx.Observable;
import rx.Scheduler;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

/**
 * TODO: scheduled listeners (EventSubscription) cleanup
 */
public class EventBus implements Bus{
    private static final String TAG = "EventBus";

    private static EventBus defaultBus = new Builder().build();
    private static boolean debug = false;

    private final Scheduler mainScheduler;
    private final Scheduler currentScheduler;
    private final Scheduler backgroundScheduler;

    private final Map<Class, List<WeakReference<EventSubscription>>> mainThreadListeners = new LinkedHashMap<>();
    private final Map<Class, List<WeakReference<EventSubscription>>> backgroundThreadListeners = new LinkedHashMap<>();
    private final Map<Class, List<WeakReference<EventSubscription>>> currentThreadListeners = new LinkedHashMap<>();

    private final Map<Class<?>, ? super Object> stickyEvents = new LinkedHashMap<>();
    private final Object listenerLock = new Object();


    private EventBus(Scheduler mainScheduler, Scheduler currentScheduler, Scheduler backgroundScheduler) {
        this.mainScheduler = mainScheduler;
        this.currentScheduler = currentScheduler;
        this.backgroundScheduler = backgroundScheduler;
    }

    public static EventBus getDefault() {
        return defaultBus;
    }

    public static void setDefault(EventBus bus) {
        defaultBus = bus;
    }

    public static void enableDebug(boolean enable) {
        debug = enable;
    }

    public <T> void register(@NonNull Class<T> clazz, @NonNull EventSubscription<? super T> subscription) {
        register(clazz, subscription, ThreadMode.CURRENT);
    }

    public <T> void register(@NonNull Class<T> clazz, @NonNull EventSubscription<? super T> subscription, ThreadMode threadMode) {
        Map<Class, List<WeakReference<EventSubscription>>> listenerMap;

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

        synchronized (listenerLock) {
            if (!listenerMap.containsKey(clazz)) {
                listenerMap.put(clazz, new LinkedList<WeakReference<EventSubscription>>());
            }

            listenerMap.get(clazz).add(new WeakReference<EventSubscription>(subscription));
            if (debug) {
                countSubscriptions();
            }
        }
        postStickyOnRegistration(clazz, subscription, threadMode);
    }

    private void countSubscriptions() {
        int count = 0;
        count += countSubscriptions(mainThreadListeners);
        count += countSubscriptions(currentThreadListeners);
        count += countSubscriptions(backgroundThreadListeners);

        Log.i(TAG, "Registration Count: " + count);
    }

    private int countSubscriptions(Map<Class, List<WeakReference<EventSubscription>>> listMap) {
        int count = 0;
        for (List<WeakReference<EventSubscription>> subscriptions : listMap.values()) {
            count += subscriptions.size();
        }
        return count;
    }

    public void register(@NonNull Registrar registrar) {
        registrar.register(this);
    }

    public <T> void unregister(@NonNull Class<T> clazz, @NonNull EventSubscription<? super T> subscription) {
        //Unregisters listeners that match both the clazz and subscription
        synchronized (listenerLock) {
            if (mainThreadListeners.containsKey(clazz)) {
                unregister(subscription, mainThreadListeners.get(clazz));
            }

            if (backgroundThreadListeners.containsKey(clazz)) {
                unregister(subscription, backgroundThreadListeners.get(clazz));
            }

            if (currentThreadListeners.containsKey(clazz)) {
                unregister(subscription, currentThreadListeners.get(clazz));
            }
            if (debug) {
                countSubscriptions();
            }
        }
    }

    public void unregister(@NonNull Registrar registrar) {
        registrar.unregister(this);
    }

    public <T> void post(@NonNull T event) {
        synchronized (listenerLock) {
            for (Map.Entry<Class, List<WeakReference<EventSubscription>>> entry : currentThreadListeners.entrySet()) {
                checkAndPost(entry.getKey(), event, ThreadMode.CURRENT, entry.getValue());
            }

            for (Map.Entry<Class, List<WeakReference<EventSubscription>>> entry : mainThreadListeners.entrySet()) {
                checkAndPost(entry.getKey(), event, ThreadMode.MAIN, entry.getValue());
            }

            for (Map.Entry<Class, List<WeakReference<EventSubscription>>> entry : backgroundThreadListeners.entrySet()) {
                checkAndPost(entry.getKey(), event, ThreadMode.BACKGROUND, entry.getValue());
            }
        }
    }

    private <T> void checkAndPost(Class subscriptionClazz, T event, ThreadMode threadMode, List<WeakReference<EventSubscription>> subscriptions) {
        if (subscriptionClazz.isInstance(event)) {
            post(event, threadMode, subscriptions);
        }
    }

    public <T> void postSticky(@NonNull T event) {
        stickyEvents.put(event.getClass(), event);
        post(event);
    }

    public <T> void removeSticky(@NonNull Class<T> eventClass) {
        stickyEvents.remove(eventClass);
    }

    /**
     * Used to post sticky events to a newly registered listener
     */
    private <T> void postStickyOnRegistration(@NonNull Class<T> clazz, @NonNull EventSubscription<? super T> subscription, ThreadMode threadMode) {
        for (Map.Entry<Class<?>, ? super Object> entry : stickyEvents.entrySet()) {
            if (clazz.isAssignableFrom(entry.getKey())) {
                post(entry.getValue(), threadMode, Collections.singletonList(new WeakReference<EventSubscription>(subscription)));
            }
        }
    }

    private <T> void post(@NonNull T event, ThreadMode threadMode, @NonNull List<WeakReference<EventSubscription>> subscriptions) {
        Observable.just(new SubscriptionStore<T>(event, subscriptions))
                .subscribeOn(getScheduler(threadMode))
                .subscribe(new Action1<SubscriptionStore<T>>() {
                    @Override
                    public void call(SubscriptionStore<T> store) {
                        performPost(store);
                    }
                });
    }

    private <T> void performPost(SubscriptionStore<T> store) {
        synchronized (listenerLock) {
            Iterator<WeakReference<EventSubscription>> iterator = store.subscriptions.iterator();

            while (iterator.hasNext()) {
                EventSubscription subscription = iterator.next().get();
                if (subscription != null) {
                    //noinspection unchecked
                    subscription.handle(store.event);
                } else {
                    iterator.remove();
                }
            }
        }
    }

    private <T> void unregister(@NonNull EventSubscription<T> registeredSubscription, @NonNull List<WeakReference<EventSubscription>> subscriptions) {
        Iterator<WeakReference<EventSubscription>> iterator = subscriptions.iterator();

        while (iterator.hasNext()) {
            EventSubscription subscription = iterator.next().get();
            if (subscription == null || subscription.equals(registeredSubscription)) {
                iterator.remove();
            }
        }
    }

    private Scheduler getScheduler(ThreadMode threadMode) {
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

    private static class SubscriptionStore<T> {
        @NonNull
        T event;
        @NonNull
        List<WeakReference<EventSubscription>> subscriptions;

        public SubscriptionStore(@NonNull T event, @NonNull List<WeakReference<EventSubscription>> subscriptions) {
            this.event = event;
            this.subscriptions = subscriptions;
        }
    }

    private static class Builder {
        private static final int DEFAULT_BACKGROUND_THREAD_POOL_SIZE = 2;

        @Nullable
        private Scheduler mainScheduler;
        @Nullable
        private Scheduler currentScheduler;
        @Nullable
        private Scheduler backgroundScheduler;
        private int backgroundThreadPoolSize = DEFAULT_BACKGROUND_THREAD_POOL_SIZE;

        public Builder setMainScheduler(Scheduler scheduler) {
            this.mainScheduler = scheduler;
            return this;
        }

        public Builder setCurrentScheduler(Scheduler scheduler) {
            this.currentScheduler = scheduler;
            return this;
        }

        public Builder setBackgroundScheduler(Scheduler scheduler) {
            this.backgroundScheduler = scheduler;
            return this;
        }

        public Builder setBackgroundThreadPoolSize(int threadPoolSize) {
            if (threadPoolSize < 1) {
                throw new IllegalArgumentException("Thread pool size must be >= 1");
            }
            this.backgroundThreadPoolSize = threadPoolSize;
            return this;
        }

        public EventBus build() {
            if (mainScheduler == null) {
                mainScheduler = AndroidSchedulers.mainThread();
            }
            if (currentScheduler == null) {
                currentScheduler = Schedulers.trampoline();
            }
            if (backgroundScheduler == null) {
                backgroundScheduler = Schedulers.from(Executors.newFixedThreadPool(backgroundThreadPoolSize));
            }
            return new EventBus(mainScheduler, currentScheduler, backgroundScheduler);
        }
    }
}