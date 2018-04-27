package com.astedt.robin.util.nursery;

import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class Nursery {

    private final Stack<Thread> childThreads;
    private final Thread currentThread;
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

    private Nursery(String nurseryId) {
        this.nurseryId = nurseryId;
        alive = new AtomicBoolean(true);
        childThreads = new Stack<>();
        currentThread = Thread.currentThread();
        exceptionRaisedByTask = new AtomicReference<>(null);
        unnamedTasks = new AtomicInteger(0);
    }

    public synchronized void startSoon(Runnable child) {
        final String taskId = "<unnamed task id="+unnamedTasks.getAndIncrement()+">";
        startSoon(child, taskId);
    }

    public synchronized void startSoon(Runnable child, String taskId) {
        if (!alive.get()) {
            throw new NurseryInvokedOutOfScopeException();
        }
        Task task = new Task(child, taskId);
        Thread thread = new Thread(task);
        childThreads.push(thread);
        thread.start();
    }

    class Task implements Runnable {

        private final Runnable child;
        private final String taskId;
        private Exception exception = null;
        private Thread thread = null;

        public Task(Runnable child, String id) {
            this.child = child;
            this.taskId = id;
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
                final String threadName = "Nursery[nursery="+nurseryId+", task="+taskId+"]";
                thread = Thread.currentThread();
                thread.setName(threadName);
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
