package com.vikingsen.pocketbus;

import java.util.List;

public interface Registrar {
    List<Subscription<?>> getSubscriptions();
}
