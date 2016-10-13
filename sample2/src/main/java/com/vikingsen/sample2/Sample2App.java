package com.vikingsen.sample2;

import android.app.Application;

import pocketbus.Bus;
import pocketbus.Registry;

/**
 * Copyright © 2016 IRI
 * All rights reserved
 */
@Registry
public class Sample2App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Bus.getDefault().setRegistry(new BusRegistry());
    }
}
