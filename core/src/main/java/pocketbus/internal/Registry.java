package pocketbus.internal;

import pocketbus.SubscriptionRegistration;

public interface Registry {
    <T> SubscriptionRegistration getRegistrar(T target);
}
