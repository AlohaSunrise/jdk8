package tests.java.lang;

import java.util.concurrent.locks.LockSupport;

public class ParkTest {

    public static void main(String[] args) {
        {
            Thread thread = Thread.currentThread();

            LockSupport.unpark(thread);

            System.out.println("a");
            LockSupport.park();
            System.out.println("b");
            LockSupport.park();
            System.out.println("c");

        }
    }
    
    
    
    
    
}
