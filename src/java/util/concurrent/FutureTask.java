/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

/*
 *
 *
 *
 *
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;
import java.util.concurrent.locks.LockSupport;

/**
 * A cancellable asynchronous computation.  This class provides a base
 * implementation of {@link Future}, with methods to start and cancel
 * a computation, query to see if the computation is complete, and
 * retrieve the result of the computation.  The result can only be
 * retrieved when the computation has completed; the {@code get}
 * methods will block if the computation has not yet completed.  Once
 * the computation has completed, the computation cannot be restarted
 * or cancelled (unless the computation is invoked using
 * {@link #runAndReset}).
 *
 * <p>A {@code FutureTask} can be used to wrap a {@link Callable} or
 * {@link Runnable} object.  Because {@code FutureTask} implements
 * {@code Runnable}, a {@code FutureTask} can be submitted to an
 * {@link Executor} for execution.
 *
 * <p>In addition to serving as a standalone class, this class provides
 * {@code protected} functionality that may be useful when creating
 * customized task classes.
 *
 * @since 1.5
 * @author Doug Lea
 * @param <V> The result type returned by this FutureTask's {@code get} methods
 */
public class FutureTask<V> implements RunnableFuture<V> {
    /*
     * Revision notes: This differs from previous versions of this
     * class that relied on AbstractQueuedSynchronizer, mainly to
     * avoid surprising users about retaining interrupt status during
     * cancellation races. Sync control in the current design relies
     * on a "state" field updated via CAS to track completion, along
     * with a simple Treiber stack to hold waiting threads.
     *
     * Style note: As usual, we bypass overhead of using
     * AtomicXFieldUpdaters and instead directly use Unsafe intrinsics.
     */

    /**
     * The run state of this task, initially NEW.  The run state
     * transitions to a terminal state only in methods set,
     * setException, and cancel.  During completion, state may take on
     * transient values of COMPLETING (while outcome is being set) or
     * INTERRUPTING (only while interrupting the runner to satisfy a
     * cancel(true)). Transitions from these intermediate to final
     * states use cheaper ordered/lazy writes because values are unique
     * and cannot be further modified.
     *
     * Possible state transitions:
     * NEW -> COMPLETING -> NORMAL
     * NEW -> COMPLETING -> EXCEPTIONAL
     * NEW -> CANCELLED
     * NEW -> INTERRUPTING -> INTERRUPTED
     */
    //��ʾ��ǰtask״̬
    private volatile int state;
    //��ǰ������δִ��
    private static final int NEW          = 0;
    //��ǰ�������ڽ�������΢��ȫ������һ���ٽ�״̬
    private static final int COMPLETING   = 1;
    //��ǰ������������
    private static final int NORMAL       = 2;
    //��ǰ����ִ�й����з������쳣�� �ڲ���װ�� callable.run() �����׳��쳣��
    private static final int EXCEPTIONAL  = 3;
    //��ǰ����ȡ��
    private static final int CANCELLED    = 4;
    //��ǰ�����ж���..  �̴߳��˸���ǣ���Ҫ����Ӧ�ж�
    private static final int INTERRUPTING = 5;
    //��ǰ�������ж�
    private static final int INTERRUPTED  = 6;

    /** The underlying callable; nulled out after running */
    //submit(runnable/callable)   runnable ʹ�� ������ģʽ αװ�� Callable�ˡ�
    private Callable<V> callable;
    /** The result to return or exception to throw from get() */
    //��������£���������ִ�н�����outcome����ִ�н���� callable ����ֵ��
    //�����������callable�����׳��쳣��outcome�����쳣
    private Object outcome; // non-volatile, protected by state reads/writes
    /** The thread running the callable; CASed during run() */
    //��ǰ�����߳�ִ���ڼ䣬���浱ǰִ��������̶߳������á�
    private volatile Thread runner;
    /** Treiber stack of waiting threads */
    //��Ϊ���кܶ��߳�ȥget��ǰ����Ľ�������� ����ʹ����һ�����ݽṹ stack ͷ�� ͷȡ ��һ�����С�
    private volatile WaitNode waiters;

    /**
     * Returns result or throws exception for completed task.
     *
     * @param s completed state value
     */
    @SuppressWarnings("unchecked")
    private V report(int s) throws ExecutionException {
        //��������£�outcome �������callable���н����Ľ��
        //��������������� callable �׳����쳣��
        Object x = outcome;
        //������������ǰ����״̬��������
        if (s == NORMAL)
            //ֱ�ӷ���callable������
            return (V)x;

        //��ȡ��״̬
        if (s >= CANCELLED)
            throw new CancellationException();

        //ִ�е��⣬˵��callable�ӿ�ʵ���У�����bug��...
        throw new ExecutionException((Throwable)x);
    }

    /**
     * Creates a {@code FutureTask} that will, upon running, execute the
     * given {@code Callable}.
     *
     * @param  callable the callable task
     * @throws NullPointerException if the callable is null
     */
    public FutureTask(Callable<V> callable) {
        if (callable == null)
            throw new NullPointerException();
        //callable���ǳ���Ա�Լ�ʵ�ֵ�ҵ����
        this.callable = callable;
        //���õ�ǰ����״̬Ϊ NEW
        this.state = NEW;       // ensure visibility of callable
    }

    /**
     * Creates a {@code FutureTask} that will, upon running, execute the
     * given {@code Runnable}, and arrange that {@code get} will return the
     * given result on successful completion.
     *
     * @param runnable the runnable task
     * @param result the result to return on successful completion. If
     * you don't need a particular result, consider using
     * constructions of the form:
     * {@code Future<?> f = new FutureTask<Void>(runnable, null)}
     * @throws NullPointerException if the runnable is null
     */
    public FutureTask(Runnable runnable, V result) {
        //ʹ��װ����ģʽ��runnableת��Ϊ�� callable�ӿڣ��ⲿ�߳� ͨ��get��ȡ
        //��ǰ����ִ�н��ʱ���������Ϊ null Ҳ����Ϊ ��������ֵ��
        this.callable = Executors.callable(runnable, result);
        this.state = NEW;       // ensure visibility of callable
    }

    public boolean isCancelled() {
        return state >= CANCELLED;
    }

    public boolean isDone() {
        return state != NEW;
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        //����һ��state == NEW ���� ��ʾ��ǰ������������ ���� �����̳߳� ���������..
        //��������UNSAFE.compareAndSwapInt(this, stateOffset, NEW, mayInterruptIfRunning ? INTERRUPTING : CANCELLED))
        //      ����������˵���޸�״̬�ɹ�������ȥִ�������߼��ˣ����� ����false ��ʾcancelʧ�ܡ�
        if (!(state == NEW &&
                UNSAFE.compareAndSwapInt(this, stateOffset, NEW,
                        mayInterruptIfRunning ? INTERRUPTING : CANCELLED)))
            return false;

        try {    // in case call to interrupt throws exception
            if (mayInterruptIfRunning) {
                try {
                    //ִ�е�ǰFutureTask ���̣߳��п���������null����null ������ǣ� ��ǰ������ �����У���û���̻߳�ȡ�����ء���
                    Thread t = runner;
                    //����������˵����ǰ�߳� runner ������ִ��task.
                    if (t != null)
                        //��runner�߳�һ���ж��ź�.. �����ĳ�������Ӧ�ж� �����ж��߼�..�������������Ӧ�жϵ�..ɶҲ���ᷢ����
                        t.interrupt();
                } finally { // final state
                    //��������״̬Ϊ �ж���ɡ�
                    UNSAFE.putOrderedInt(this, stateOffset, INTERRUPTED);
                }
            }

        } finally {
            //��������get() �������̡߳�
            finishCompletion();
        }

        return true;
    }

    /**
     * @throws CancellationException {@inheritDoc}
     */
    //����������̵߳ȴ���ǰ����ִ����ɺ�Ľ��...
    public V get() throws InterruptedException, ExecutionException {
        //��ȡ��ǰ����״̬
        int s = state;
        //����������δִ�С�����ִ�С�����ɡ� ����get���ⲿ�̻߳ᱻ������get�����ϡ�
        if (s <= COMPLETING)
            //����task��ǰ״̬�����ܵ�ǰ�߳��������Ѿ�˯��һ����..
            s = awaitDone(false, 0L);

        return report(s);
    }

    /**
     * @throws CancellationException {@inheritDoc}
     */
    public V get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        if (unit == null)
            throw new NullPointerException();
        int s = state;
        if (s <= COMPLETING &&
                (s = awaitDone(true, unit.toNanos(timeout))) <= COMPLETING)
            throw new TimeoutException();
        return report(s);
    }

    /**
     * Protected method invoked when this task transitions to state
     * {@code isDone} (whether normally or via cancellation). The
     * default implementation does nothing.  Subclasses may override
     * this method to invoke completion callbacks or perform
     * bookkeeping. Note that you can query status inside the
     * implementation of this method to determine whether this task
     * has been cancelled.
     */
    protected void done() { }

    /**
     * Sets the result of this future to the given value unless
     * this future has already been set or has been cancelled.
     *
     * <p>This method is invoked internally by the {@link #run} method
     * upon successful completion of the computation.
     *
     * @param v the value
     */
    protected void set(V v) {
        //ʹ��CAS��ʽ���õ�ǰ����״̬Ϊ �����..
        //��û�п���ʧ���أ� �ⲿ�̵߳Ȳ����ˣ�ֱ����setִ��CAS֮ǰ ��  taskȡ���ˡ�  ��С�����¼���
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {

            outcome = v;
            //�������ֵ�� outcome֮�����ϻὫ��ǰ����״̬�޸�Ϊ NORMAL ��������״̬��
            UNSAFE.putOrderedInt(this, stateOffset, NORMAL); // final state

            //��һ�£�
            //������ð�get() �ٴ��������߳� ����..
            finishCompletion();
        }
    }

    /**
     * Causes this future to report an {@link ExecutionException}
     * with the given throwable as its cause, unless this future has
     * already been set or has been cancelled.
     *
     * <p>This method is invoked internally by the {@link #run} method
     * upon failure of the computation.
     *
     * @param t the cause of failure
     */
    protected void setException(Throwable t) {
        //ʹ��CAS��ʽ���õ�ǰ����״̬Ϊ �����..
        //��û�п���ʧ���أ� �ⲿ�̵߳Ȳ����ˣ�ֱ����setִ��CAS֮ǰ ��  taskȡ���ˡ�  ��С�����¼���
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            //���õ��� callable ���ϲ��׳������쳣��
            outcome = t;
            //����ǰ�����״̬ �޸�Ϊ EXCEPTIONAL
            UNSAFE.putOrderedInt(this, stateOffset, EXCEPTIONAL); // final state
            //��ͷ���� get() ��
            finishCompletion();
        }
    }

    //submit(runnable/callable) -> newTaskFor(runnable) -> execute(task)   -> pool
    //����ִ�����
    public void run() {
        //����һ��state != NEW ����������˵����ǰtask�Ѿ���ִ�й��� ���� ��cancel �ˣ���֮��NEW״̬�������߳̾Ͳ������ˡ�
        //��������!UNSAFE.compareAndSwapObject(this, runnerOffset,null, Thread.currentThread())
        //       ����������casʧ�ܣ���ǰ���������߳���ռ��...
        if (state != NEW ||
                !UNSAFE.compareAndSwapObject(this, runnerOffset,
                        null, Thread.currentThread()))
            return;

        //ִ�е������ǰtaskһ���� NEW ״̬������ ��ǰ�߳�Ҳ��ռTASK�ɹ���

        try {
            //callable ���ǳ���Ա�Լ���װ�߼���callable ���� װ�κ��runnable
            Callable<V> c = callable;
            //����һ��c != null ��ֹ��ָ���쳣
            //��������state == NEW ��ֹ�ⲿ�߳� cancel����ǰ����
            if (c != null && state == NEW) {

                //�������
                V result;
                //true ��ʾcallable.run �����ִ�гɹ� δ�׳��쳣
                //false ��ʾcallable.run �����ִ��ʧ�� �׳��쳣
                boolean ran;

                try {
                    //���ó���Ա�Լ�ʵ�ֵ�callable ���� װ�κ��runnable
                    result = c.call();
                    //c.callδ�׳��κ��쳣��ran������Ϊtrue �����ִ�гɹ�
                    ran = true;
                } catch (Throwable ex) {
                    //˵������Ա�Լ�д���߼�����bug�ˡ�
                    result = null;
                    ran = false;
                    setException(ex);
                }



                if (ran)
                    //˵����ǰc.call����ִ�н����ˡ�
                    //set�������ý����outcome
                    set(result);
            }
        } finally {
            // runner must be non-null until state is settled to
            // prevent concurrent calls to run()
            runner = null;
            // state must be re-read after nulling runner to prevent
            // leaked interrupts
            int s = state;
            if (s >= INTERRUPTING)
                //��ͷ��˵..���� cancel() �������ˡ�
                handlePossibleCancellationInterrupt(s);
        }
    }

    /**
     * Executes the computation without setting its result, and then
     * resets this future to initial state, failing to do so if the
     * computation encounters an exception or is cancelled.  This is
     * designed for use with tasks that intrinsically execute more
     * than once.
     *
     * @return {@code true} if successfully run and reset
     */
    protected boolean runAndReset() {
        if (state != NEW ||
                !UNSAFE.compareAndSwapObject(this, runnerOffset,
                        null, Thread.currentThread()))
            return false;
        boolean ran = false;
        int s = state;
        try {
            Callable<V> c = callable;
            if (c != null && s == NEW) {
                try {
                    c.call(); // don't set result
                    ran = true;
                } catch (Throwable ex) {
                    setException(ex);
                }
            }
        } finally {
            // runner must be non-null until state is settled to
            // prevent concurrent calls to run()
            runner = null;
            // state must be re-read after nulling runner to prevent
            // leaked interrupts
            s = state;
            if (s >= INTERRUPTING)
                handlePossibleCancellationInterrupt(s);
        }
        return ran && s == NEW;
    }

    /**
     * Ensures that any interrupt from a possible cancel(true) is only
     * delivered to a task while in run or runAndReset.
     */
    private void handlePossibleCancellationInterrupt(int s) {
        // It is possible for our interrupter to stall before getting a
        // chance to interrupt us.  Let's spin-wait patiently.
        if (s == INTERRUPTING)
            while (state == INTERRUPTING)
                Thread.yield(); // wait out pending interrupt

        // assert state == INTERRUPTED;

        // We want to clear any interrupt we may have received from
        // cancel(true).  However, it is permissible to use interrupts
        // as an independent mechanism for a task to communicate with
        // its caller, and there is no way to clear only the
        // cancellation interrupt.
        //
        // Thread.interrupted();
    }

    /**
     * Simple linked list nodes to record waiting threads in a Treiber
     * stack.  See other classes such as Phaser and SynchronousQueue
     * for more detailed explanation.
     */
    static final class WaitNode {
        volatile Thread thread;
        volatile WaitNode next;
        WaitNode() { thread = Thread.currentThread(); }
    }

    /**
     * Removes and signals all waiting threads, invokes done(), and
     * nulls out callable.
     */
    private void finishCompletion() {
        // assert state > COMPLETING;
        //qָ��waiters �����ͷ��㡣
        for (WaitNode q; (q = waiters) != null;) {
            
            // �����б�� �߳� һֱ�������������get���������뵽waiters ջ�� ���ڲ�����������cas��
            if (UNSAFE.compareAndSwapObject(this, waitersOffset, q, null)) {
                for (;;) {
                    //��ȡ��ǰnode�ڵ��װ�� thread
                    Thread t = q.thread;
                    //����������˵����ǰ�̲߳�Ϊnull
                    if (t != null) {

                        q.thread = null;//help GC
                        //���ѵ�ǰ�ڵ��Ӧ���߳�
                        LockSupport.unpark(t);
                    }
                    //next ��ǰ�ڵ����һ���ڵ�
                    WaitNode next = q.next;

                    if (next == null)
                        break;

                    q.next = null; // unlink to help gc
                    q = next;
                }

                break;
            }
        }

        done();

        //��callable ����Ϊnull helpGC
        callable = null;        // to reduce footprint
    }

    /**
     * Awaits completion or aborts on interrupt or timeout.
     *
     * @param timed true if use timed waits
     * @param nanos time to wait, if timed
     * @return state upon completion
     */
    private int awaitDone(boolean timed, long nanos)
            throws InterruptedException {
        //0 ������ʱ
        final long deadline = timed ? System.nanoTime() + nanos : 0L;
        //���õ�ǰ�߳� ��װ�� WaitNode ����
        WaitNode q = null;
        //��ʾ��ǰ�߳� waitNode���� ��û�� ���/ѹջ
        boolean queued = false;
        //����
        for (;;) {

            //����������˵����ǰ�̻߳��� �Ǳ������߳�ʹ���ж����ַ�ʽ���ѵġ�interrupted()  Ҳ�п�����parkǰ�ͱ��ж���
            //����true ��Ὣ Thread���жϱ�����û�false.
            if (Thread.interrupted()) {
                //��ǰ�߳�node����
                removeWaiter(q);
                //get�����׳� �ж��쳣��
                throw new InterruptedException();
            }

            //���赱ǰ�߳��Ǳ������߳� ʹ��unpark(thread) ���ѵĻ����������������������߼���

            //��ȡ��ǰ��������״̬
            int s = state;
            //����������˵����ǰ���� �Ѿ��н����.. �����Ǻ� ������ ��..
            if (s > COMPLETING) {

                //����������˵���Ѿ�Ϊ��ǰ�̴߳�����node�ˣ���ʱ��Ҫ�� node.thread = null helpGC
                if (q != null)
                    q.thread = null;
                //ֱ�ӷ��ص�ǰ״̬.
                return s;
            }
            //����������˵����ǰ����ӽ����״̬...�����õ�ǰ�߳����ͷ�cpu ��������һ����ռcpu��
            else if (s == COMPLETING) // cannot time out yet
                Thread.yield();
                //������������һ����������ǰ�̻߳�δ���� WaitNode ���󣬴�ʱΪ��ǰ�̴߳��� WaitNode����
            else if (q == null)
                q = new WaitNode();
                //�����������ڶ�����������ǰ�߳��Ѿ����� WaitNode�����ˣ�����node����δ���
            else if (!queued){
                //��ǰ�߳�node�ڵ� next ָ�� ԭ ���е�ͷ�ڵ�   waiters һֱָ����е�ͷ��
                q.next = waiters;
                //cas��ʽ����waiters����ָ�� ��ǰ�߳�node�� �ɹ��Ļ� queued == true ���򣬿��������߳�����һ������ˡ�
                queued = UNSAFE.compareAndSwapObject(this, waitersOffset, waiters, q);
            }
            //�������������ᵽ���
            else if (timed) {
                nanos = deadline - System.nanoTime();
                if (nanos <= 0L) {
                    removeWaiter(q);
                    return state;
                }
                LockSupport.parkNanos(this, nanos);
            }
            else
                //��ǰget�������߳̾ͻᱻpark�ˡ�  �߳�״̬���Ϊ WAITING״̬���൱��������..
                //�����������߳̽��㻽��  ���� ����ǰ�߳� �жϡ�
                LockSupport.park(this);
        }
    }

    /**
     * Tries to unlink a timed-out or interrupted wait node to avoid
     * accumulating garbage.  Internal nodes are simply unspliced
     * without CAS since it is harmless if they are traversed anyway
     * by releasers.  To avoid effects of unsplicing from already
     * removed nodes, the list is retraversed in case of an apparent
     * race.  This is slow when there are a lot of nodes, but we don't
     * expect lists to be long enough to outweigh higher-overhead
     * schemes.
     */
    private void removeWaiter(WaitNode node) {
        if (node != null) {
            node.thread = null;
            retry:
            for (;;) {          // restart on removeWaiter race
                //s ��ǰ�ڵ���¸��ڵ�
                for (WaitNode pred = null, q = waiters, s; q != null; q = s) {
                    s = q.next;
                    if (q.thread != null)
                        pred = q;
                    else if (pred != null) {
                        pred.next = s;
                        if (pred.thread == null) // check for race
                            continue retry;
                    }
                    else if (!UNSAFE.compareAndSwapObject(this, waitersOffset,
                            q, s))
                        continue retry;
                }
                break;
            }
        }
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long stateOffset;
    private static final long runnerOffset;
    private static final long waitersOffset;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> k = FutureTask.class;
            stateOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("state"));
            runnerOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("runner"));
            waitersOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("waiters"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
