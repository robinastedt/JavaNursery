import com.astedt.robin.util.concurrency.AsynchronousReference;
import com.astedt.robin.util.concurrency.nursery.Nursery;

public class NurseryTest {
    public static void main(String[] args) {

        Nursery.open((Nursery nursery) -> {
            nursery.start(() -> System.out.println("Inline test 1"));
            nursery.start(() -> System.out.println("Inline test 2"));
        });

        Nursery.open((Nursery nursery) -> {
            nursery.start(() -> {
                nursery.start(() -> System.out.println("Nested scopes test 1"));
                nursery.start(() -> System.out.println("Nested scopes test 2"));
            });
            nursery.start(() -> System.out.println("Nested scopes test 3"));
        });

        // A Nursery can also store results from children, and then work on them asynchronously
        // If there's some result to be returned within the scope, it is also passed on as a result of the scope itself.
        int result = Nursery.open((Nursery nursery) -> {
            AsynchronousReference<Integer> result1 = nursery.start(NurseryTest::intSupplier);
            AsynchronousReference<Integer> result2 = nursery.start(NurseryTest::intSupplier);

            // The result of the children can not be accessed until it's finished.
            // The get function of the AsynchronousReference blocks until the result becomes available
            return result1.get() + result2.get();
        });
        System.out.println(result);


        // An example of one or more threads failing
        Nursery.open((Nursery nursery) -> {
            nursery.start(NurseryTest::exceptionThrower);
            nursery.start(NurseryTest::exceptionThrower);
            nursery.start(NurseryTest::exceptionThrower);
            nursery.start(NurseryTest::exceptionThrower);
            nursery.start(NurseryTest::exceptionThrower);
        });
    }

    private static int intSupplier() {
        int result = 0;
        for (int i = 0; i < 1000; i++) {
            result += i;
            try {
                Thread.sleep(1); // The illusion of working hard
            } catch (InterruptedException e) {
                e.printStackTrace();
                return -1;
            }
        }
        return result; // Returns 499500
    }

    private static void exceptionThrower() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            return;
        }
        throw new NullPointerException();
    }
}
