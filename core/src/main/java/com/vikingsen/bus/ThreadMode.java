package com.vikingsen.bus;

public enum ThreadMode {
    CURRENT, // Schedulers.trampoline()
    MAIN, // AndroidSchedulers.mainThread()
    BACKGROUND // Schedulers.newThread()
}
