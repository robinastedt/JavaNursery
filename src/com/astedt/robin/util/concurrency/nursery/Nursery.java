package com.astedt.robin.util.concurrency.nursery;

import com.astedt.robin.util.concurrency.AsynchronousReference;

import java.util.Stack;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class Nursery {

    private final Stack<Thread> childThreads;
    private final Semaphore childAccessSemaphore;
    private final Thread nurseryThread;
    private final AtomicReference<Task> exceptionRaisedByTask;
    private final AtomicBoolean alive;
    private final AtomicInteger unnamedTasks;
    private final String nurseryId;

    private final static AtomicInteger unnamedNurseries = new AtomicInteger(0);

    public static void open(NurseryScope scope) {
        internalOpen(getUnnamedNurseryId(), convertToSupplier(scope));
    }

    public static void open(String nurseryId, NurseryScope scope) {
        internalOpen(nurseryId, convertToSupplier(scope));
    }

    public static <T> T open(NurseryScopeSupplier<T> scope) {
        return internalOpen(getUnnamedNurseryId(), scope);
    }

    public static <T> T open(String nurseryId, NurseryScopeSupplier<T> scope) {
        return internalOpen(nurseryId, scope);
    }

    private static <T> NurseryScopeSupplier<T> convertToSupplier(NurseryScope scope) {
        return (Nursery nursery) -> {scope.runBlock(nursery); return null;};
    }

    private static String getUnnamedNurseryId() {
        return "<unnamed nursery id="+unnamedNurseries.getAndIncrement()+">";
    }

    private static <T> T internalOpen(String nurseryId, NurseryScopeSupplier<T> scope) {
        T result = null;
        final Nursery nursery = new Nursery(nurseryId);
        try {
            result = scope.runBlock(nursery);
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
        return result;
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

    public void start(Runnable child) {
        internalStart(getVoidSupplier(child), getUnnamedTaskId());
    }

    public void start(Runnable child, String childId) {
        internalStart(getVoidSupplier(child), childId);
    }

    public <T> AsynchronousReference<T> start(Supplier<T> child) {
        return internalStart(child, getUnnamedTaskId());
    }

    public <T> AsynchronousReference<T> start(Supplier<T> child, String childId) {
        return internalStart(child, childId);
    }

    private static Supplier<Void> getVoidSupplier(Runnable runnable) {
        return () -> {runnable.run(); return null;};
    }

    private String getUnnamedTaskId() {
        return "<unnamed task id="+unnamedTasks.getAndIncrement()+">";
    }

    private <T> AsynchronousReference<T> internalStart(Supplier<T> child, String childId) {
        Task task = new Task(child, childId);
        Thread thread = new Thread(task);
        try {
            childAccessSemaphore.acquire();
        } catch (InterruptedException e) {
            return null;
        }
        if (!alive.get()) {
            throw new NurseryInvokedOutOfScopeException();
        }
        childThreads.push(thread);
        thread.start();
        childAccessSemaphore.release();
        return task.getResult();
    }



    class Task<T> implements Runnable {

        private final Supplier<T> child;
        private final String childId;
        private Exception exception = null;
        private Thread thread = null;
        private AsynchronousReference<T> result;

        public Task(Supplier<T> child, String childId) {
            this.child = child;
            this.childId = childId;
            result = new AsynchronousReference<>();
        }

        public Exception getException() {
            return exception;
        }

        public Thread getThread() {
            return thread;
        }

        public AsynchronousReference<T> getResult() {
            return result;
        }

        @Override
        public void run() {
            try {
                final String threadName = "Nursery[nursery="+nurseryId+", child="+childId+"]";
                thread = Thread.currentThread();
                thread.setName(threadName);
                result.set(child.get());
            } catch (Exception e) {
                exception = e;
                if (exceptionRaisedByTask.compareAndSet(null, this)) {
                    nurseryThread.interrupt();
                }
            }
        }
    }
}
