package com.vikingsen.bus;

import java.util.List;

public interface Registrar {
    List<EventSubscription<?>> getSubscriptions();
}
