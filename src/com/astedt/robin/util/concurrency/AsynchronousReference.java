package com.astedt.robin.util.concurrency;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class AsynchronousReference<T> {
    private final AtomicReference<T> resource;
    private final Semaphore resourceBlocker;
    private final AtomicBoolean isSet;


    public AsynchronousReference() {
        resourceBlocker = new Semaphore(0);
        isSet = new AtomicBoolean(false);
        resource = new AtomicReference<>();
    }
    public void set(T resource) {
        this.resource.set(resource);
        if (isSet.compareAndSet(false, true)) {
            resourceBlocker.release();
        }
    }

    public T get() throws InterruptedException {
        if (!isSet.get()) {
            resourceBlocker.acquire();
            resourceBlocker.release();
        }
        return resource.get();
    }
}
