package tests.java.lang;

import org.junit.Test;
import tests.base.BaseTest;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ѧϰ����String���Դ�밸��
 *
 * @see tests.base.BaseTest ���Ի��� ��Ҫ����һЩ���Թ��õķ��������������������
 */
public class StringTest extends BaseTest {

    @Test
    public void testHashcode() {
        String str = new String("123");
        log.info(""+str.hashCode());
    }

    @Test
    public void testAA() throws InterruptedException {
       Lock lock = new ReentrantLock();
        Condition condition = lock.newCondition();
        condition.await();
        System.out.println(123);
    }
    
    
    
}
