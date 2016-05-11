package pocketbus;

import java.util.List;

public interface SubscriptionRegistration {
    /**
     * @return a list of subscriptions
     */
    List<Subscription<?>> getSubscriptions();
}
