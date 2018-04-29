import com.astedt.robin.util.concurrency.AsynchronousReference;
import com.astedt.robin.util.concurrency.nursery.Nursery;

public class NurseryTest {
    public static void main(String[] args) {

        Nursery.with((Nursery nursery) -> {
            nursery.startSoon(() -> System.out.println("Inline test 1"));
            nursery.startSoon(() -> System.out.println("Inline test 2"));
        });

        Nursery.with((Nursery nursery) -> {
            nursery.startSoon(() -> {
                nursery.startSoon(() -> System.out.println("Nested scopes test 1"));
                nursery.startSoon(() -> System.out.println("Nested scopes test 2"));
            });
            nursery.startSoon(() -> System.out.println("Nested scopes test 3"));
        });

        Nursery.with((Nursery nursery) -> {
            AsynchronousReference<Integer> result1 = nursery.startSoon(NurseryTest::intSupplier);
            AsynchronousReference<Integer> result2 = nursery.startSoon(NurseryTest::intSupplier);

            // The result of the children can not be accessed until it's finished.
            // The get function of the AsynchronousReference blocks until the result becomes available
            System.out.println(result1.get() + result2.get()); // Returns 999000
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
}
