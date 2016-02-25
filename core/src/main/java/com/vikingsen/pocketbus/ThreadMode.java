package com.vikingsen.pocketbus;

public enum ThreadMode {
    CURRENT, // Schedulers.trampoline()
    MAIN, // AndroidSchedulers.mainThread()
    BACKGROUND // Schedulers.newThread()
}
