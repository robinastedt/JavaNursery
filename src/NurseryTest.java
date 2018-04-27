public class NurseryTest {
    public static void main(String[] args) {
        Nursery.with((Nursery nursery) -> {
            nursery.startSoon(NurseryTest::test1, "test 1");
            nursery.startSoon(NurseryTest::test2, "test 2");
        });
    }

    private static void test1() {
        for (int i = 0; i < 100; i++) {
            System.out.println("Test 1: " + i);
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
    private static void test2() {
        for (int i = 0; i < 100; i++) {
            if (i == 50) {
                throw new NullPointerException();
            }
            System.out.println("Test 2: " + i);
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}
