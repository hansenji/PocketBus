package com.vikingsen.pocketbus;

import java.util.List;

public interface Registrar {
    List<EventSubscription<?>> getSubscriptions();
}
