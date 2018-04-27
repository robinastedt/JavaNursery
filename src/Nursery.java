
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;

public class Nursery {

    private final Stack<Thread> childThreads;
    private final Thread currentThread;
    private final AtomicReference<Task> exceptionRaisedByTask;

    public static void with(Scope scope) {
        final Nursery nursery = new Nursery();
        try {
            scope.runBlock(nursery);
            while (!nursery.childThreads.empty()) {
                Thread thread = nursery.childThreads.pop();
                thread.join();
            }
        } catch (InterruptedException e) {
            while (!nursery.childThreads.empty()) {
                Thread thread = nursery.childThreads.pop();
                thread.interrupt();
            }
        }

        if (nursery.exceptionRaisedByTask.get() != null) {

            throw new PropagatedException(nursery.exceptionRaisedByTask.get());
        }
    }

    private Nursery() {
        childThreads = new Stack<>();
        currentThread = Thread.currentThread();
        exceptionRaisedByTask = new AtomicReference<>(null);
    }

    public synchronized void startSoon(Runnable child, String id) {
        Task task = new Task(child, id);
        Thread thread = new Thread(task);
        childThreads.push(thread);
        thread.start();
    }

    public interface Scope {
        void runBlock(Nursery nursery) throws InterruptedException;
    }

    public static class PropagatedException extends IllegalThreadStateException {
        private static final int META_DATA_TRACE_LINES = 1;
        private static final int ORIGINAL_TRACE_SUPPRESS_COUNT = 2;
        private static final int CURRENT_TRACE_SUPPRESS_COUNT = 1;


        public PropagatedException(Task task) {
            StackTraceElement[] currentStack = getStackTrace();
            StackTraceElement[] oldStack = task.exception.getStackTrace();

            final int traceLength =
                    currentStack.length - CURRENT_TRACE_SUPPRESS_COUNT
                    + oldStack.length - ORIGINAL_TRACE_SUPPRESS_COUNT
                    + META_DATA_TRACE_LINES;

            StackTraceElement[] newStack = new StackTraceElement[traceLength];

            // Original thrown exception
            newStack[0] = new StackTraceElement(
                    "Caused by exception in thread \"" + task.thread.getName() + "\" " + task.exception,
                    "", "", -1);

            // Original stack, suppress, trace of starting the child thread for readability
            System.arraycopy(
                    oldStack,
                    0,
                    newStack,
                    META_DATA_TRACE_LINES,
                    oldStack.length - ORIGINAL_TRACE_SUPPRESS_COUNT);

            // Suppress everything but the call to Nursery.with(), for readability
            System.arraycopy(
                    currentStack,
                    CURRENT_TRACE_SUPPRESS_COUNT,
                    newStack,
                    oldStack.length + META_DATA_TRACE_LINES - ORIGINAL_TRACE_SUPPRESS_COUNT,
                    currentStack.length - CURRENT_TRACE_SUPPRESS_COUNT);
            setStackTrace(newStack);
        }
    }


    private class Task implements Runnable {

        private final Runnable child;
        private final String id;
        private Exception exception = null;
        private Thread thread = null;

        public Task(Runnable child, String id) {
            this.child = child;
            this.id = id;
        }

        @Override
        public void run() {
            try {
                thread = Thread.currentThread();
                thread.setName("Nursery-child(" + id + ")");
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
