
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

public class Nursery {

    private final Thread[] threads;
    private final Task[] tasks;
    private final Thread currentThread;
    private final AtomicReference<Task> exceptionRaisedByTask;

    public Nursery(Runnable... children) {
        currentThread = Thread.currentThread();
        exceptionRaisedByTask = new AtomicReference<>(null);
        threads = new Thread[children.length];
        tasks = new Task[children.length];
        for (int i = 0; i < children.length; i += 1) {
            tasks[i] = new Task(children[i]);
            threads[i] = new Thread(tasks[i]);
        }
    }

    public void start() {
        for (Thread thread : threads) {
            thread.start();
        }

        Iterator<Thread> threadIterator = Arrays.asList(threads).iterator();
        while (threadIterator.hasNext()) {
            Thread thread = threadIterator.next();
            try {
                thread.join();
            } catch (InterruptedException e) {
                while (threadIterator.hasNext()) {
                    Thread nextThread = threadIterator.next();
                    nextThread.interrupt();
                }
            }
        }

        if (exceptionRaisedByTask.get() != null) {
            throw new NurseryPropagatedException(exceptionRaisedByTask.get().exception);
        }
    }

    private class Task implements Runnable {

        private final Runnable child;

        private Exception exception = null;

        public Task(Runnable child) {
            this.child = child;
        }

        @Override
        public void run() {
            try {
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
