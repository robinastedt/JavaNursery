package com.astedt.robin.util.nursery;

public interface NurseryScope {
    void runBlock(Nursery nursery) throws InterruptedException;
}