package com.astedt.robin.util.nursery;

import java.util.Stack;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class Nursery {

    private final Stack<Thread> childThreads;
    private final Semaphore childAccessSemaphore;
    private final Thread nurseryThread;
    private final AtomicReference<Task> exceptionRaisedByTask;
    private final AtomicBoolean alive;
    private final AtomicInteger unnamedTasks;
    private final String nurseryId;

    private final static AtomicInteger unnamedNurseries = new AtomicInteger(0);

    public static void with(NurseryScope scope) {
        final String nurseryId = "<unnamed nursery id="+unnamedNurseries.getAndIncrement()+">";
        internalWith(nurseryId, scope);
    }

    public static void with(String nurseryId, NurseryScope scope) {
        internalWith(nurseryId, scope);
    }

    private static void internalWith(String nurseryId, NurseryScope scope) {
        final Nursery nursery = new Nursery(nurseryId);
        try {
            scope.runBlock(nursery);
            while (!nursery.childThreads.empty()) {
                nursery.childAccessSemaphore.acquire();
                Thread thread = nursery.childThreads.pop();
                nursery.childAccessSemaphore.release();
                thread.join();
            }
        } catch (InterruptedException e) {
            nursery.alive.set(false);
            while (!nursery.childThreads.empty()) {
                try {
                    nursery.childAccessSemaphore.acquire();
                } catch (InterruptedException e1) {
                    continue;
                }
                Thread thread = nursery.childThreads.pop();
                nursery.childAccessSemaphore.release();
                thread.interrupt();
            }
        } finally {
            nursery.alive.set(false);
        }

        if (nursery.exceptionRaisedByTask.get() != null) {
            throw new NurseryPropagatedException(nursery.exceptionRaisedByTask.get());
        }
    }

    private Nursery(String nurseryId) {
        this.nurseryId = nurseryId;
        alive = new AtomicBoolean(true);
        childThreads = new Stack<>();
        childAccessSemaphore = new Semaphore(1);
        nurseryThread = Thread.currentThread();
        exceptionRaisedByTask = new AtomicReference<>(null);
        unnamedTasks = new AtomicInteger(0);
    }

    public void startSoon(Runnable child) {
        final String childId = "<unnamed task id="+unnamedTasks.getAndIncrement()+">";
        startSoon(child, childId);
    }

    public void startSoon(Runnable child, String childId) {
        Task task = new Task(child, childId);
        Thread thread = new Thread(task);
        try {
            childAccessSemaphore.acquire();
        } catch (InterruptedException e) {
            return;
        }
        if (!alive.get()) {
            throw new NurseryInvokedOutOfScopeException();
        }
        childThreads.push(thread);
        thread.start();
        childAccessSemaphore.release();
    }

    class Task implements Runnable {

        private final Runnable child;
        private final String childId;
        private Exception exception = null;
        private Thread thread = null;

        public Task(Runnable child, String childId) {
            this.child = child;
            this.childId = childId;
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
                final String threadName = "Nursery[nursery="+nurseryId+", child="+childId+"]";
                thread = Thread.currentThread();
                thread.setName(threadName);
                child.run();
            } catch (Exception e) {
                exception = e;
                if (exceptionRaisedByTask.compareAndSet(null, this)) {
                    nurseryThread.interrupt();
                }
            }
        }
    }
}
