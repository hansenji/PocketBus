package pocketbus;

public enum ThreadMode {
    /**
     * Subscription run on same thread as caller
     */
    CURRENT, // Schedulers.trampoline()
    /**
     * Subscription run on the main thread
     */
    MAIN, // AndroidSchedulers.mainThread()
    /**
     * Subscription run on a background thread
     */
    BACKGROUND // Schedulers.newThread()
}
