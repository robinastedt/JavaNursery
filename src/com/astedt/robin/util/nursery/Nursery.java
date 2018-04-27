package com.astedt.robin.util.nursery;

import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class Nursery {

    private final Stack<Thread> childThreads;
    private final Thread currentThread;
    private final AtomicReference<Task> exceptionRaisedByTask;
    private final AtomicBoolean alive;

    public static void with(NurseryScope scope) {
        final Nursery nursery = new Nursery();
        try {
            scope.runBlock(nursery);
            while (!nursery.childThreads.empty()) {
                Thread thread = nursery.childThreads.pop();
                thread.join();
            }
        } catch (InterruptedException e) {
            nursery.alive.set(false);
            while (!nursery.childThreads.empty()) {
                Thread thread = nursery.childThreads.pop();
                thread.interrupt();
            }
        } finally {
            nursery.alive.set(false);
        }

        if (nursery.exceptionRaisedByTask.get() != null) {
            throw new NurseryPropagatedException(nursery.exceptionRaisedByTask.get());
        }
    }

    private Nursery() {
        alive = new AtomicBoolean(true);
        childThreads = new Stack<>();
        currentThread = Thread.currentThread();
        exceptionRaisedByTask = new AtomicReference<>(null);
    }

    public synchronized void startSoon(Runnable child, String id) {
        if (!alive.get()) {
            throw new NurseryInvokedOutOfScopeException();
        }
        Task task = new Task(child, id);
        Thread thread = new Thread(task);
        childThreads.push(thread);
        thread.start();
    }

    class Task implements Runnable {

        private final Runnable child;
        private final String id;
        private Exception exception = null;
        private Thread thread = null;

        public Task(Runnable child, String id) {
            this.child = child;
            this.id = id;
        }

        public Exception getException() {
            return exception;
        }

        public Thread getThread() {
            return thread;
        }

        @Override
        public void run() {
            try {
                thread = Thread.currentThread();
                thread.setName("com.astedt.robin.util.nursery.Nursery-child(" + id + ")");
                child.run();
            } catch (Exception e) {
                exception = e;
                if (exceptionRaisedByTask.compareAndSet(null, this)) {
                    currentThread.interrupt();
                }
            }
        }
    }
}
