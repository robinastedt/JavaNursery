class NurseryPropagatedException extends IllegalThreadStateException {
    private static final int META_DATA_TRACE_LINES = 1;
    private static final int ORIGINAL_TRACE_SUPPRESS_COUNT = 2;
    private static final int CURRENT_TRACE_SUPPRESS_COUNT = 1;


    public NurseryPropagatedException(Nursery.Task task) {
        StackTraceElement[] currentStack = getStackTrace();
        StackTraceElement[] oldStack = task.getException().getStackTrace();

        final int traceLength =
                currentStack.length - CURRENT_TRACE_SUPPRESS_COUNT
                        + oldStack.length - ORIGINAL_TRACE_SUPPRESS_COUNT
                        + META_DATA_TRACE_LINES;

        StackTraceElement[] newStack = new StackTraceElement[traceLength];

        // Original thrown exception
        newStack[0] = new StackTraceElement(
                "Caused by exception in thread \"" + task.getThread().getName() + "\" " + task.getException(),
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