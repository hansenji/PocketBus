package com.vikingsen.bus;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EventBusTest {

    private EventBus eventBus;

    @Before
    public void setup() {
        eventBus = EventBus.getDefault();
    }

    @Test
    public void testPost() {
        eventBus.register(new EventSubscription<Foo>() {
            @Override
            public void handle(Foo foo) {
                assertEquals("Foo", foo.getClass().getSimpleName());
            }

            @Override
            public Class<Foo> getEventClass() {
                return Foo.class;
            }

            @Override
            public ThreadMode getThreadMode() {
                return ThreadMode.CURRENT;
            }
        });
    }

}