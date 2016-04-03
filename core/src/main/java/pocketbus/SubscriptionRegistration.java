package pocketbus;

import java.util.List;

public interface SubscriptionRegistration {
    List<Subscription<?>> getSubscriptions();
}
