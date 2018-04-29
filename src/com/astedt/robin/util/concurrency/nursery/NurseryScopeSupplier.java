package com.astedt.robin.util.concurrency.nursery;

public interface NurseryScopeSupplier<T> {
    T runBlock(Nursery nursery) throws InterruptedException;
}
