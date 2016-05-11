package pocketbus.internal;

import pocketbus.SubscriptionRegistration;

public interface Registry {
    /**
     * @param target the target that has subscribe annotations
     * @return the SubscriptionRegistration for the given target
     */
    <T> SubscriptionRegistration getRegistration(T target);
}
