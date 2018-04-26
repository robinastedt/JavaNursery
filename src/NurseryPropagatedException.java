public class NurseryPropagatedException extends IllegalThreadStateException {
    public NurseryPropagatedException(Exception originalException) {
        this.setStackTrace(originalException.getStackTrace());
    }
}
