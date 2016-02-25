package com.vikingsen.pocketbus;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BusTest {

    private Bus bus;

    @Before
    public void setup() {
        bus = Bus.getDefault();
    }

    @Test
    public void testPost() {
        bus.register(new EventSubscription<Foo>() {
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