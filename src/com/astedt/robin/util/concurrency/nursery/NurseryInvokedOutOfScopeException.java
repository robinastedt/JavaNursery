package com.astedt.robin.util.concurrency.nursery;

class NurseryInvokedOutOfScopeException extends IllegalThreadStateException {
    public NurseryInvokedOutOfScopeException() {
        super();
    }
}