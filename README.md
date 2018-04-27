# JavaNursery
A Java implementation of a new concurrent control flow primitive as described at: https://vorpus.org/blog/notes-on-structured-concurrency-or-go-statement-considered-harmful/

The basic idea is that control flow enters a nursery and continues under it after all threads started inside the nursery scope have returned. The construct also deals with unchecked exceptions being thrown inside a child thread by interrupting the other children, waits for them to terminate and then propagating the exception up the stack.

As you can see below in the example code, child threads can even arbitrarily spawn new threads themselves without breaking the control flow, by passing down the nursery object to the called functions. This is safe as long as the Nursery object itself doesn't leak outside the nursery scope, which should not be possible as long as it's only passed along to children within the nursery, and no other concurrency primitive is used within the nursery scope.

## Example usage
```java
import com.astedt.robin.util.nursery.Nursery;

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
            nursery.startSoon(NurseryTest::test1, "test 1");
            nursery.startSoon(NurseryTest::test2, "test 2");
        });
    }

    private static void test1() {
        for (int i = 0; i < 10; i++) {
            System.out.println("Exception test 1: " + i);
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
    private static void test2() {
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                throw new NullPointerException();
            }
            System.out.println("Exception test 2: " + i);
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}
```
