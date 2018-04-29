# JavaNursery
A Java implementation of a new concurrent control flow primitive as described at: https://vorpus.org/blog/notes-on-structured-concurrency-or-go-statement-considered-harmful/

The basic idea is that control flow enters a nursery and continues under it after all threads started inside the nursery scope have returned. The construct also deals with unchecked exceptions being thrown inside a child thread by interrupting the other children, waits for them to terminate and then propagating the exception up the stack.

As you can see below in the example code, child threads can even arbitrarily spawn new threads themselves without breaking the control flow, by passing down the nursery object to the called functions. This is safe as long as the Nursery object itself doesn't leak outside the nursery scope, which should not be possible as long as it's only passed along to children within the nursery, and no other concurrency primitive is used within the nursery scope.

To be able to use any results of the child threads non-statically a child thread can be started with a Supplier\<T\> instead of a Runnable, and the result becomes available in a blocking asynchronous data structure which can be accessed withing the nursery scope. The scope itself can also return any type of value as a result of the computations taking place inside which becomes available as soon as the scope collapses.

## Example usage
```java
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
```
