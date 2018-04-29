package com.astedt.robin.util.concurrency.nursery;

public interface NurseryScope {
    void runBlock(Nursery nursery) throws InterruptedException;
}