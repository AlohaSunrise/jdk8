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

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;

/**
 * An {@link ExecutorService} that executes each submitted task using
 * one of possibly several pooled threads, normally configured
 * using {@link Executors} factory methods.
 *
 * <p>Thread pools address two different problems: they usually
 * provide improved performance when executing large numbers of
 * asynchronous tasks, due to reduced per-task invocation overhead,
 * and they provide a means of bounding and managing the resources,
 * including threads, consumed when executing a collection of tasks.
 * Each {@code ThreadPoolExecutor} also maintains some basic
 * statistics, such as the number of completed tasks.
 *
 * <p>To be useful across a wide range of contexts, this class
 * provides many adjustable parameters and extensibility
 * hooks. However, programmers are urged to use the more convenient
 * {@link Executors} factory methods {@link
 * Executors#newCachedThreadPool} (unbounded thread pool, with
 * automatic thread reclamation), {@link Executors#newFixedThreadPool}
 * (fixed size thread pool) and {@link
 * Executors#newSingleThreadExecutor} (single background thread), that
 * preconfigure settings for the most common usage
 * scenarios. Otherwise, use the following guide when manually
 * configuring and tuning this class:
 *
 * <dl>
 *
 * <dt>Core and maximum pool sizes</dt>
 *
 * <dd>A {@code ThreadPoolExecutor} will automatically adjust the
 * pool size (see {@link #getPoolSize})
 * according to the bounds set by
 * corePoolSize (see {@link #getCorePoolSize}) and
 * maximumPoolSize (see {@link #getMaximumPoolSize}).
 *
 * When a new task is submitted in method {@link #execute(Runnable)},
 * and fewer than corePoolSize threads are running, a new thread is
 * created to handle the request, even if other worker threads are
 * idle.  If there are more than corePoolSize but less than
 * maximumPoolSize threads running, a new thread will be created only
 * if the queue is full.  By setting corePoolSize and maximumPoolSize
 * the same, you create a fixed-size thread pool. By setting
 * maximumPoolSize to an essentially unbounded value such as {@code
 * Integer.MAX_VALUE}, you allow the pool to accommodate an arbitrary
 * number of concurrent tasks. Most typically, core and maximum pool
 * sizes are set only upon construction, but they may also be changed
 * dynamically using {@link #setCorePoolSize} and {@link
 * #setMaximumPoolSize}. </dd>
 *
 * <dt>On-demand construction</dt>
 *
 * <dd>By default, even core threads are initially created and
 * started only when new tasks arrive, but this can be overridden
 * dynamically using method {@link #prestartCoreThread} or {@link
 * #prestartAllCoreThreads}.  You probably want to prestart threads if
 * you construct the pool with a non-empty queue. </dd>
 *
 * <dt>Creating new threads</dt>
 *
 * <dd>New threads are created using a {@link ThreadFactory}.  If not
 * otherwise specified, a {@link Executors#defaultThreadFactory} is
 * used, that creates threads to all be in the same {@link
 * ThreadGroup} and with the same {@code NORM_PRIORITY} priority and
 * non-daemon status. By supplying a different ThreadFactory, you can
 * alter the thread's name, thread group, priority, daemon status,
 * etc. If a {@code ThreadFactory} fails to create a thread when asked
 * by returning null from {@code newThread}, the executor will
 * continue, but might not be able to execute any tasks. Threads
 * should possess the "modifyThread" {@code RuntimePermission}. If
 * worker threads or other threads using the pool do not possess this
 * permission, service may be degraded: configuration changes may not
 * take effect in a timely manner, and a shutdown pool may remain in a
 * state in which termination is possible but not completed.</dd>
 *
 * <dt>Keep-alive times</dt>
 *
 * <dd>If the pool currently has more than corePoolSize threads,
 * excess threads will be terminated if they have been idle for more
 * than the keepAliveTime (see {@link #getKeepAliveTime(TimeUnit)}).
 * This provides a means of reducing resource consumption when the
 * pool is not being actively used. If the pool becomes more active
 * later, new threads will be constructed. This parameter can also be
 * changed dynamically using method {@link #setKeepAliveTime(long,
 * TimeUnit)}.  Using a value of {@code Long.MAX_VALUE} {@link
 * TimeUnit#NANOSECONDS} effectively disables idle threads from ever
 * terminating prior to shut down. By default, the keep-alive policy
 * applies only when there are more than corePoolSize threads. But
 * method {@link #allowCoreThreadTimeOut(boolean)} can be used to
 * apply this time-out policy to core threads as well, so long as the
 * keepAliveTime value is non-zero. </dd>
 *
 * <dt>Queuing</dt>
 *
 * <dd>Any {@link BlockingQueue} may be used to transfer and hold
 * submitted tasks.  The use of this queue interacts with pool sizing:
 *
 * <ul>
 *
 * <li> If fewer than corePoolSize threads are running, the Executor
 * always prefers adding a new thread
 * rather than queuing.</li>
 *
 * <li> If corePoolSize or more threads are running, the Executor
 * always prefers queuing a request rather than adding a new
 * thread.</li>
 *
 * <li> If a request cannot be queued, a new thread is created unless
 * this would exceed maximumPoolSize, in which case, the task will be
 * rejected.</li>
 *
 * </ul>
 *
 * There are three general strategies for queuing:
 * <ol>
 *
 * <li> <em> Direct handoffs.</em> A good default choice for a work
 * queue is a {@link SynchronousQueue} that hands off tasks to threads
 * without otherwise holding them. Here, an attempt to queue a task
 * will fail if no threads are immediately available to run it, so a
 * new thread will be constructed. This policy avoids lockups when
 * handling sets of requests that might have internal dependencies.
 * Direct handoffs generally require unbounded maximumPoolSizes to
 * avoid rejection of new submitted tasks. This in turn admits the
 * possibility of unbounded thread growth when commands continue to
 * arrive on average faster than they can be processed.  </li>
 *
 * <li><em> Unbounded queues.</em> Using an unbounded queue (for
 * example a {@link LinkedBlockingQueue} without a predefined
 * capacity) will cause new tasks to wait in the queue when all
 * corePoolSize threads are busy. Thus, no more than corePoolSize
 * threads will ever be created. (And the value of the maximumPoolSize
 * therefore doesn't have any effect.)  This may be appropriate when
 * each task is completely independent of others, so tasks cannot
 * affect each others execution; for example, in a web page server.
 * While this style of queuing can be useful in smoothing out
 * transient bursts of requests, it admits the possibility of
 * unbounded work queue growth when commands continue to arrive on
 * average faster than they can be processed.  </li>
 *
 * <li><em>Bounded queues.</em> A bounded queue (for example, an
 * {@link ArrayBlockingQueue}) helps prevent resource exhaustion when
 * used with finite maximumPoolSizes, but can be more difficult to
 * tune and control.  Queue sizes and maximum pool sizes may be traded
 * off for each other: Using large queues and small pools minimizes
 * CPU usage, OS resources, and context-switching overhead, but can
 * lead to artificially low throughput.  If tasks frequently block (for
 * example if they are I/O bound), a system may be able to schedule
 * time for more threads than you otherwise allow. Use of small queues
 * generally requires larger pool sizes, which keeps CPUs busier but
 * may encounter unacceptable scheduling overhead, which also
 * decreases throughput.  </li>
 *
 * </ol>
 *
 * </dd>
 *
 * <dt>Rejected tasks</dt>
 *
 * <dd>New tasks submitted in method {@link #execute(Runnable)} will be
 * <em>rejected</em> when the Executor has been shut down, and also when
 * the Executor uses finite bounds for both maximum threads and work queue
 * capacity, and is saturated.  In either case, the {@code execute} method
 * invokes the {@link
 * RejectedExecutionHandler#rejectedExecution(Runnable, ThreadPoolExecutor)}
 * method of its {@link RejectedExecutionHandler}.  Four predefined handler
 * policies are provided:
 *
 * <ol>
 *
 * <li> In the default {@link ThreadPoolExecutor.AbortPolicy}, the
 * handler throws a runtime {@link RejectedExecutionException} upon
 * rejection. </li>
 *
 * <li> In {@link ThreadPoolExecutor.CallerRunsPolicy}, the thread
 * that invokes {@code execute} itself runs the task. This provides a
 * simple feedback control mechanism that will slow down the rate that
 * new tasks are submitted. </li>
 *
 * <li> In {@link ThreadPoolExecutor.DiscardPolicy}, a task that
 * cannot be executed is simply dropped.  </li>
 *
 * <li>In {@link ThreadPoolExecutor.DiscardOldestPolicy}, if the
 * executor is not shut down, the task at the head of the work queue
 * is dropped, and then execution is retried (which can fail again,
 * causing this to be repeated.) </li>
 *
 * </ol>
 *
 * It is possible to define and use other kinds of {@link
 * RejectedExecutionHandler} classes. Doing so requires some care
 * especially when policies are designed to work only under particular
 * capacity or queuing policies. </dd>
 *
 * <dt>Hook methods</dt>
 *
 * <dd>This class provides {@code protected} overridable
 * {@link #beforeExecute(Thread, Runnable)} and
 * {@link #afterExecute(Runnable, Throwable)} methods that are called
 * before and after execution of each task.  These can be used to
 * manipulate the execution environment; for example, reinitializing
 * ThreadLocals, gathering statistics, or adding log entries.
 * Additionally, method {@link #terminated} can be overridden to perform
 * any special processing that needs to be done once the Executor has
 * fully terminated.
 *
 * <p>If hook or callback methods throw exceptions, internal worker
 * threads may in turn fail and abruptly terminate.</dd>
 *
 * <dt>Queue maintenance</dt>
 *
 * <dd>Method {@link #getQueue()} allows access to the work queue
 * for purposes of monitoring and debugging.  Use of this method for
 * any other purpose is strongly discouraged.  Two supplied methods,
 * {@link #remove(Runnable)} and {@link #purge} are available to
 * assist in storage reclamation when large numbers of queued tasks
 * become cancelled.</dd>
 *
 * <dt>Finalization</dt>
 *
 * <dd>A pool that is no longer referenced in a program <em>AND</em>
 * has no remaining threads will be {@code shutdown} automatically. If
 * you would like to ensure that unreferenced pools are reclaimed even
 * if users forget to call {@link #shutdown}, then you must arrange
 * that unused threads eventually die, by setting appropriate
 * keep-alive times, using a lower bound of zero core threads and/or
 * setting {@link #allowCoreThreadTimeOut(boolean)}.  </dd>
 *
 * </dl>
 *
 * <p><b>Extension example</b>. Most extensions of this class
 * override one or more of the protected hook methods. For example,
 * here is a subclass that adds a simple pause/resume feature:
 *
 *  <pre> {@code
 * class PausableThreadPoolExecutor extends ThreadPoolExecutor {
 *   private boolean isPaused;
 *   private ReentrantLock pauseLock = new ReentrantLock();
 *   private Condition unpaused = pauseLock.newCondition();
 *
 *   public PausableThreadPoolExecutor(...) { super(...); }
 *
 *   protected void beforeExecute(Thread t, Runnable r) {
 *     super.beforeExecute(t, r);
 *     pauseLock.lock();
 *     try {
 *       while (isPaused) unpaused.await();
 *     } catch (InterruptedException ie) {
 *       t.interrupt();
 *     } finally {
 *       pauseLock.unlock();
 *     }
 *   }
 *
 *   public void pause() {
 *     pauseLock.lock();
 *     try {
 *       isPaused = true;
 *     } finally {
 *       pauseLock.unlock();
 *     }
 *   }
 *
 *   public void resume() {
 *     pauseLock.lock();
 *     try {
 *       isPaused = false;
 *       unpaused.signalAll();
 *     } finally {
 *       pauseLock.unlock();
 *     }
 *   }
 * }}</pre>
 *
 * @since 1.5
 * @author Doug Lea
 */
public class ThreadPoolExecutor extends AbstractExecutorService {
    /**
     * The main pool control state, ctl, is an atomic integer packing
     * two conceptual fields
     *   workerCount, indicating the effective number of threads
     *   runState,    indicating whether running, shutting down etc
     *
     * In order to pack them into one int, we limit workerCount to
     * (2^29)-1 (about 500 million) threads rather than (2^31)-1 (2
     * billion) otherwise representable. If this is ever an issue in
     * the future, the variable can be changed to be an AtomicLong,
     * and the shift/mask constants below adjusted. But until the need
     * arises, this code is a bit faster and simpler using an int.
     *
     * The workerCount is the number of workers that have been
     * permitted to start and not permitted to stop.  The value may be
     * transiently different from the actual number of live threads,
     * for example when a ThreadFactory fails to create a thread when
     * asked, and when exiting threads are still performing
     * bookkeeping before terminating. The user-visible pool size is
     * reported as the current size of the workers set.
     *
     * The runState provides the main lifecycle control, taking on values:
     *
     *   RUNNING:  Accept new tasks and process queued tasks
     *   SHUTDOWN: Don't accept new tasks, but process queued tasks
     *   STOP:     Don't accept new tasks, don't process queued tasks,
     *             and interrupt in-progress tasks
     *   TIDYING:  All tasks have terminated, workerCount is zero,
     *             the thread transitioning to state TIDYING
     *             will run the terminated() hook method
     *   TERMINATED: terminated() has completed
     *
     * The numerical order among these values matters, to allow
     * ordered comparisons. The runState monotonically increases over
     * time, but need not hit each state. The transitions are:
     *
     * RUNNING -> SHUTDOWN
     *    On invocation of shutdown(), perhaps implicitly in finalize()
     * (RUNNING or SHUTDOWN) -> STOP
     *    On invocation of shutdownNow()
     * SHUTDOWN -> TIDYING
     *    When both queue and pool are empty
     * STOP -> TIDYING
     *    When pool is empty
     * TIDYING -> TERMINATED
     *    When the terminated() hook method has completed
     *
     * Threads waiting in awaitTermination() will return when the
     * state reaches TERMINATED.
     *
     * Detecting the transition from SHUTDOWN to TIDYING is less
     * straightforward than you'd like because the queue may become
     * empty after non-empty and vice versa during SHUTDOWN state, but
     * we can only terminate if, after seeing that it is empty, we see
     * that workerCount is 0 (which sometimes entails a recheck -- see
     * below).
     */
    //��3λ����ʾ��ǰ�̳߳�����״̬   ��ȥ��3λ֮��ĵ�λ����ʾ��ǰ�̳߳�����ӵ�е��߳�����
    private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
    //��ʾ��ctl�У���COUNT_BITSλ �����ڴ�ŵ�ǰ�߳�������λ��
    private static final int COUNT_BITS = Integer.SIZE - 3;
    //��COUNT_BITSλ ���ܱ��������ֵ�� 000 11111111111111111111 => 5�ڶࡣ
    private static final int CAPACITY   = (1 << COUNT_BITS) - 1;

    // runState is stored in the high-order bits
    //111 000000000000000000  ת������������ʵ��һ������
    private static final int RUNNING    = -1 << COUNT_BITS;
    //000 000000000000000000
    private static final int SHUTDOWN   =  0 << COUNT_BITS;
    //001 000000000000000000
    private static final int STOP       =  1 << COUNT_BITS;
    //010 000000000000000000
    private static final int TIDYING    =  2 << COUNT_BITS;
    //011 000000000000000000
    private static final int TERMINATED =  3 << COUNT_BITS;

    // Packing and unpacking ctl
    //��ȡ��ǰ�̳߳�����״̬
    //~000 11111111111111111111 => 111 000000000000000000000
    //c == ctl = 111 000000000000000000111
    //111 000000000000000000111
    //111 000000000000000000000
    //111 000000000000000000000
    private static int runStateOf(int c)     { return c & ~CAPACITY; }

    //��ȡ��ǰ�̳߳��߳�����
    //c == ctl = 111 000000000000000000111
    //111 000000000000000000111
    //000 111111111111111111111
    //000 000000000000000000111 => 7
    private static int workerCountOf(int c)  { return c & CAPACITY; }

    //�������õ�ǰ�̳߳�ctlֵʱ  ���õ�
    //rs ��ʾ�̳߳�״̬   wc ��ʾ��ǰ�̳߳���worker���̣߳�����
    //111 000000000000000000
    //000 000000000000000111
    //111 000000000000000111
    private static int ctlOf(int rs, int wc) { return rs | wc; }

    /*
     * Bit field accessors that don't require unpacking ctl.
     * These depend on the bit layout and on workerCount being never negative.
     */
    //�Ƚϵ�ǰ�̳߳�ctl����ʾ��״̬���Ƿ�С��ĳ��״̬s
    //c = 111 000000000000000111 <  000 000000000000000000 == true
    //��������£�RUNNING < SHUTDOWN < STOP < TIDYING < TERMINATED
    private static boolean runStateLessThan(int c, int s) {
        return c < s;
    }

    //�Ƚϵ�ǰ�̳߳�ctl����ʾ��״̬���Ƿ���ڵ���ĳ��״̬s
    private static boolean runStateAtLeast(int c, int s) {
        return c >= s;
    }

    //С��SHUTDOWN ��һ����RUNNING�� SHUTDOWN == 0
    private static boolean isRunning(int c) {
        return c < SHUTDOWN;
    }

    /**
     * Attempts to CAS-increment the workerCount field of ctl.
     */
    //ʹ��CAS��ʽ ��ctlֵ+1 ���ɹ�����true, ʧ�ܷ���false
    private boolean compareAndIncrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect + 1);
    }

    /**
     * Attempts to CAS-decrement the workerCount field of ctl.
     */
    //ʹ��CAS��ʽ ��ctlֵ-1 ���ɹ�����true, ʧ�ܷ���false
    private boolean compareAndDecrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect - 1);
    }

    /**
     * Decrements the workerCount field of ctl. This is called only on
     * abrupt termination of a thread (see processWorkerExit). Other
     * decrements are performed within getTask.
     */
    //��ctlֵ��һ���������һ���ɹ�
    private void decrementWorkerCount() {
        //�����һֱ���ԣ�ֱ���ɹ�Ϊֹ��
        do {} while (! compareAndDecrementWorkerCount(ctl.get()));
    }

    /**
     * The queue used for holding tasks and handing off to worker
     * threads.  We do not require that workQueue.poll() returning
     * null necessarily means that workQueue.isEmpty(), so rely
     * solely on isEmpty to see if the queue is empty (which we must
     * do for example when deciding whether to transition from
     * SHUTDOWN to TIDYING).  This accommodates special-purpose
     * queues such as DelayQueues for which poll() is allowed to
     * return null even if it may later return non-null when delays
     * expire.
     */
    //������У����̳߳��е��̴߳ﵽ�����߳�����ʱ�����ύ���� �ͻ�ֱ���ύ�� workQueue
    //workQueue  instanceOf ArrayBrokingQueue   LinkedBrokingQueue  ͬ������
    private final BlockingQueue<Runnable> workQueue;

    /**
     * Lock held on access to workers set and related bookkeeping.
     * While we could use a concurrent set of some sort, it turns out
     * to be generally preferable to use a lock. Among the reasons is
     * that this serializes interruptIdleWorkers, which avoids
     * unnecessary interrupt storms, especially during shutdown.
     * Otherwise exiting threads would concurrently interrupt those
     * that have not yet interrupted. It also simplifies some of the
     * associated statistics bookkeeping of largestPoolSize etc. We
     * also hold mainLock on shutdown and shutdownNow, for the sake of
     * ensuring workers set is stable while separately checking
     * permission to interrupt and actually interrupting.
     */
    //�̳߳�ȫ����������worker ���� worker ʱ��Ҫ����mainLock �� �޸��̳߳�����״̬ʱ��Ҳ��Ҫ��
    private final ReentrantLock mainLock = new ReentrantLock();

    /**
     * Set containing all worker threads in pool. Accessed only when
     * holding mainLock.
     */
    //�̳߳���������� worker->thread �ĵط���
    private final HashSet<Worker> workers = new HashSet<Worker>();

    /**
     * Wait condition to support awaitTermination
     */
    //���ⲿ�̵߳���  awaitTermination() ����ʱ���ⲿ�̻߳�ȴ���ǰ�̳߳�״̬Ϊ Termination Ϊֹ��
    //�ȴ������ʵ�ֵģ� ���ǽ��ⲿ�߳� ��װ�� waitNode ���뵽 Condition �������ˣ� waitNode.Thread �����ⲿ�̣߳��ᱻpark��������WAITING״̬����
    //���̳߳� ״̬ ��Ϊ Terminationʱ����ȥ������Щ�̡߳�ͨ�� termination.signalAll() ������֮����Щ�̻߳���뵽 �������У�Ȼ��ͷ����ȥ��ռmainLock��
    //��ռ�����̣߳������ִ��awaitTermination() ���������Щ�߳���󣬶�������ִ�С�
    //����⣺termination.await() �Ὣ�߳��������⡣
    //         termination.signalAll() �Ὣ����������߳����λ���
    private final Condition termination = mainLock.newCondition();

    /**
     * Tracks largest attained pool size. Accessed only under
     * mainLock.
     */
    //��¼�̳߳����������� �߳������ֵ
    private int largestPoolSize;

    /**
     * Counter for completed tasks. Updated only on termination of
     * worker threads. Accessed only under mainLock.
     */
    //��¼�̳߳�������������� ����worker�˳�ʱ�Ὣ worker��ɵ������ۻ���completedTaskCount
    private long completedTaskCount;

    /*
     * All user control parameters are declared as volatiles so that
     * ongoing actions are based on freshest values, but without need
     * for locking, since no internal invariants depend on them
     * changing synchronously with respect to other actions.
     */

    /**
     * Factory for new threads. All threads are created using this
     * factory (via method addWorker).  All callers must be prepared
     * for addWorker to fail, which may reflect a system or user's
     * policy limiting the number of threads.  Even though it is not
     * treated as an error, failure to create threads may result in
     * new tasks being rejected or existing ones remaining stuck in
     * the queue.
     *
     * We go further and preserve pool invariants even in the face of
     * errors such as OutOfMemoryError, that might be thrown while
     * trying to create threads.  Such errors are rather common due to
     * the need to allocate a native stack in Thread.start, and users
     * will want to perform clean pool shutdown to clean up.  There
     * will likely be enough memory available for the cleanup code to
     * complete without encountering yet another OutOfMemoryError.
     */
    //�����߳�ʱ��ʹ�� �̹߳�����������ʹ�� Executors.newFix...  newCache... �����̳߳�ʱ��ʹ�õ��� DefaultThreadFactory
    //һ�㲻����ʹ��Default�̳߳أ��Ƽ��Լ�ʵ��ThreadFactory
    private volatile ThreadFactory threadFactory;

    /**
     * Handler called when saturated or shutdown in execute.
     */
    //�ܾ����ԣ�juc���ṩ��4�з�ʽ��Ĭ�ϲ��� Abort..�׳��쳣�ķ�ʽ��
    private volatile RejectedExecutionHandler handler;

    /**
     * Timeout in nanoseconds for idle threads waiting for work.
     * Threads use this timeout when there are more than corePoolSize
     * present or if allowCoreThreadTimeOut. Otherwise they wait
     * forever for new work.
     */
    //�����̴߳��ʱ�䣬��allowCoreThreadTimeOut == false ʱ����ά�������߳������ڵ��̴߳��������ֻᱻ��ʱ��
    //allowCoreThreadTimeOut == true ���������ڵ��߳� ����ʱ Ҳ�ᱻ���ա�
    private volatile long keepAliveTime;

    /**
     * If false (default), core threads stay alive even when idle.
     * If true, core threads use keepAliveTime to time out waiting
     * for work.
     */
    //���ƺ����߳������ڵ��߳� �Ƿ���Ա����ա�true ���ԣ�false�����ԡ�
    private volatile boolean allowCoreThreadTimeOut;

    /**
     * Core pool size is the minimum number of workers to keep alive
     * (and not allow to time out etc) unless allowCoreThreadTimeOut
     * is set, in which case the minimum is zero.
     */
    //�����߳��������ơ�
    private volatile int corePoolSize;

    /**
     * Maximum pool size. Note that the actual maximum is internally
     * bounded by CAPACITY.
     */
    //�̳߳�����߳��������ơ�
    private volatile int maximumPoolSize;

    /**
     * The default rejected execution handler
     */
    //ȱʡ�ܾ����ԣ����õ���AbortPolicy �׳��쳣�ķ�ʽ��
    private static final RejectedExecutionHandler defaultHandler =
            new AbortPolicy();

    /**
     * Permission required for callers of shutdown and shutdownNow.
     * We additionally require (see checkShutdownAccess) that callers
     * have permission to actually interrupt threads in the worker set
     * (as governed by Thread.interrupt, which relies on
     * ThreadGroup.checkAccess, which in turn relies on
     * SecurityManager.checkAccess). Shutdowns are attempted only if
     * these checks pass.
     *
     * All actual invocations of Thread.interrupt (see
     * interruptIdleWorkers and interruptWorkers) ignore
     * SecurityExceptions, meaning that the attempted interrupts
     * silently fail. In the case of shutdown, they should not fail
     * unless the SecurityManager has inconsistent policies, sometimes
     * allowing access to a thread and sometimes not. In such cases,
     * failure to actually interrupt threads may disable or delay full
     * termination. Other uses of interruptIdleWorkers are advisory,
     * and failure to actually interrupt will merely delay response to
     * configuration changes so is not handled exceptionally.
     */
    private static final RuntimePermission shutdownPerm =
            new RuntimePermission("modifyThread");

    /* The context to be used when executing the finalizer, or null. */
    private final AccessControlContext acc;

    /**
     * Class Worker mainly maintains interrupt control state for
     * threads running tasks, along with other minor bookkeeping.
     * This class opportunistically extends AbstractQueuedSynchronizer
     * to simplify acquiring and releasing a lock surrounding each
     * task execution.  This protects against interrupts that are
     * intended to wake up a worker thread waiting for a task from
     * instead interrupting a task being run.  We implement a simple
     * non-reentrant mutual exclusion lock rather than use
     * ReentrantLock because we do not want worker tasks to be able to
     * reacquire the lock when they invoke pool control methods like
     * setCorePoolSize.  Additionally, to suppress interrupts until
     * the thread actually starts running tasks, we initialize lock
     * state to a negative value, and clear it upon start (in
     * runWorker).
     */
    private final class Worker
            extends AbstractQueuedSynchronizer
            implements Runnable
    {
        //Worker������AQS�Ķ�ռģʽ
        //��ռģʽ��������Ҫ����  state  ��  ExclusiveOwnerThread
        //state��0ʱ��ʾδ��ռ�� > 0ʱ��ʾ��ռ��   < 0 ʱ ��ʾ��ʼ״̬����������²��ܱ�������
        //ExclusiveOwnerThread:��ʾ��ռ�����̡߳�
        /**
         * This class will never be serialized, but we provide a
         * serialVersionUID to suppress a javac warning.
         */
        private static final long serialVersionUID = 6138294804551838833L;

        /** Thread this worker is running in.  Null if factory fails. */
        //worker�ڲ���װ�Ĺ����߳�
        final Thread thread;
        /** Initial task to run.  Possibly null. */
        //����firstTask��Ϊ�գ���ô��worker�������ڲ����߳�����)������ִ��firstTask����ִ����firstTask�󣬻ᵽqueue��ȥ��ȡ��һ������
        Runnable firstTask;

        /** Per-thread task counter */
        //��¼��ǰworker���������������
        volatile long completedTasks;

        /**
         * Creates with given first task and thread from ThreadFactory.
         * @param firstTask the first task (null if none)
         */
        //firstTask����Ϊnull��Ϊnull ������ᵽqueue�л�ȡ��
        Worker(Runnable firstTask) {
            //����AQS��ռģʽΪ��ʼ����״̬�����ʱ�� ���ܱ���ռ����
            setState(-1); // inhibit interrupts until runWorker
            this.firstTask = firstTask;
            //ʹ���̹߳���������һ���̣߳����ҽ���ǰworker ָ��Ϊ Runnable��Ҳ����˵��thread������ʱ�򣬻���worker.run()Ϊ��ڡ�
            this.thread = getThreadFactory().newThread(this);
        }

        /** Delegates main run loop to outer runWorker  */
        //��worker����ʱ����ִ��run()
        public void run() {
            //ThreadPoolExecutor->runWorker() ����Ǻ��ķ������Ⱥ������worker�������߼�ʱ�����������롣
            runWorker(this);
        }

        // Lock methods
        //
        // The value 0 represents the unlocked state.
        // The value 1 represents the locked state.
        //�жϵ�ǰworker�Ķ�ռ���Ƿ񱻶�ռ��
        //0 ��ʾδ��ռ��
        //1 ��ʾ��ռ��
        protected boolean isHeldExclusively() {
            return getState() != 0;
        }


        //����ȥռ��worker�Ķ�ռ��
        //����ֵ ��ʾ�Ƿ���ռ�ɹ�
        protected boolean tryAcquire(int unused) {
            //ʹ��CAS�޸� AQS�е� state ������ֵΪ0(0ʱ��ʾδ��ռ�ã����޸ĳɹ���ʾ��ǰ�߳���ռ�ɹ�
            //��ô������ ExclusiveOwnerThread Ϊ��ǰ�̡߳�
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }

            return false;
        }

        //�ⲿ����ֱ�ӵ���������� ���������AQS �ڵ��õģ��ⲿ����unlockʱ ��unlock->AQS.release() ->tryRelease()
        protected boolean tryRelease(int unused) {
            setExclusiveOwnerThread(null);
            setState(0);
            return true;
        }

        //����������ʧ��ʱ����������ǰ�̣߳�ֱ����ȡ����λ�á�
        public void lock()        { acquire(1); }

        //����ȥ�����������ǰ����δ������״̬����ô�����ɹ��� �᷵��true�����򲻻�������ǰ�̣߳�ֱ�ӷ���false.
        public boolean tryLock()  { return tryAcquire(1); }

        //һ������£����ǵ���unlock Ҫ��֤ ��ǰ�߳��ǳ������ġ�
        //�����������worker��state == -1 ʱ������unlock ��ʾ��ʼ��state ����state == 0
        //����worker֮ǰ���ȵ���unlock()�����������ǿ��ˢ��ExclusiveOwnerThread == null State==0
        public void unlock()      { release(1); }

        //���Ƿ��ص�ǰworker��lock�Ƿ�ռ�á�
        public boolean isLocked() { return isHeldExclusively(); }

        void interruptIfStarted() {
            Thread t;
            if (getState() >= 0 && (t = thread) != null && !t.isInterrupted()) {
                try {
                    t.interrupt();
                } catch (SecurityException ignore) {
                }
            }
        }
    }

    /*
     * Methods for setting control state
     */

    /**
     * Transitions runState to given target, or leaves it alone if
     * already at least the given target.
     *
     * @param targetState the desired state, either SHUTDOWN or STOP
     *        (but not TIDYING or TERMINATED -- use tryTerminate for that)
     */
    private void advanceRunState(int targetState) {
        //����
        for (;;) {
            int c = ctl.get();
            //��������������targetState == SHUTDOWN��˵�� ��ǰ�̳߳�״̬�� >= SHUTDOWN
            //����������������targetState == SHUTDOWN ��˵����ǰ�̳߳�״̬��RUNNING��
            if (runStateAtLeast(c, targetState) ||
                    ctl.compareAndSet(c, ctlOf(targetState, workerCountOf(c))))
                break;
        }
    }

    /**
     * Transitions to TERMINATED state if either (SHUTDOWN and pool
     * and queue empty) or (STOP and pool empty).  If otherwise
     * eligible to terminate but workerCount is nonzero, interrupts an
     * idle worker to ensure that shutdown signals propagate. This
     * method must be called following any action that might make
     * termination possible -- reducing worker count or removing tasks
     * from the queue during shutdown. The method is non-private to
     * allow access from ScheduledThreadPoolExecutor.
     */
    final void tryTerminate() {
        //����
        for (;;) {
            //��ȡ����ctlֵ
            int c = ctl.get();
            //����һ��isRunning(c)  ������ֱ�ӷ��ؾ��У��̳߳غ�������
            //��������runStateAtLeast(c, TIDYING) ˵�� �Ѿ��������߳� ��ִ�� TIDYING -> TERMINATED״̬��,��ǰ�߳�ֱ�ӻ�ȥ��
            //��������(runStateOf(c) == SHUTDOWN && ! workQueue.isEmpty())
            //SHUTDOWN�����������������������ֱ�ӻ�ȥ���õȶ����е���������Ϻ���ת��״̬��
            if (isRunning(c) ||
                    runStateAtLeast(c, TIDYING) ||
                    (runStateOf(c) == SHUTDOWN && ! workQueue.isEmpty()))
                return;

            //ʲô�����ִ�е����
            //1.�̳߳�״̬ >= STOP
            //2.�̳߳�״̬Ϊ SHUTDOWN �� �����Ѿ�����

            //������������ǰ�̳߳��е��߳����� > 0
            if (workerCountOf(c) != 0) { // Eligible to terminate
                //�ж�һ�������̡߳�
                //�����̣߳����Ŀ����أ� queue.take() | queue.poll()
                //1.���Ѻ���߳� ����getTask()��������null
                //2.ִ���˳��߼���ʱ����ٴε���tryTerminate() ������һ�������߳�
                //3.��Ϊ�̳߳�״̬�� ���̳߳�״̬ >= STOP || �̳߳�״̬Ϊ SHUTDOWN �� �����Ѿ����ˣ� ���յ���addWorkerʱ����ʧ�ܡ�
                //���տ����̶߳����������˳����ǿ����߳� ��ִ���굱ǰtaskʱ��Ҳ�����tryTerminate�������п��ܻ��ߵ����
                interruptIdleWorkers(ONLY_ONE);
                return;
            }

            //ִ�е�������߳���˭��
            //workerCountOf(c) == 0 ʱ�����������
            //���һ���˳����̡߳� ����֪������ ���̳߳�״̬ >= STOP || �̳߳�״̬Ϊ SHUTDOWN �� �����Ѿ����ˣ�
            //�̻߳��Ѻ󣬶���ִ���˳��߼����˳������� �� �Ƚ� workerCount���� -1 => ctl -1��
            //����tryTerminate ����֮ǰ���Ѿ������ˣ�����0ʱ����ʾ�������һ���˳����߳��ˡ�

            final ReentrantLock mainLock = this.mainLock;
            //��ȡ�̳߳�ȫ����
            mainLock.lock();
            try {
                //�����̳߳�״̬ΪTIDYING״̬��
                if (ctl.compareAndSet(c, ctlOf(TIDYING, 0))) {
                    try {
                        //���ù��ӷ���
                        terminated();
                    } finally {
                        //�����̳߳�״̬ΪTERMINATED״̬��
                        ctl.set(ctlOf(TERMINATED, 0));
                        //���ѵ��� awaitTermination() �������̡߳�
                        termination.signalAll();
                    }
                    return;
                }
            } finally {
                //�ͷ��̳߳�ȫ������
                mainLock.unlock();
            }
            // else retry on failed CAS
        }
    }

    /*
     * Methods for controlling interrupts to worker threads.
     */

    /**
     * If there is a security manager, makes sure caller has
     * permission to shut down threads in general (see shutdownPerm).
     * If this passes, additionally makes sure the caller is allowed
     * to interrupt each worker thread. This might not be true even if
     * first check passed, if the SecurityManager treats some threads
     * specially.
     */
    private void checkShutdownAccess() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkPermission(shutdownPerm);
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                for (Worker w : workers)
                    security.checkAccess(w.thread);
            } finally {
                mainLock.unlock();
            }
        }
    }

    /**
     * Interrupts all threads, even if active. Ignores SecurityExceptions
     * (in which case some threads may remain uninterrupted).
     */
    private void interruptWorkers() {
        final ReentrantLock mainLock = this.mainLock;
        //��ȡ�̳߳�ȫ����
        mainLock.lock();
        try {
            //��������worker
            for (Worker w : workers)
                //interruptIfStarted() ���worker�ڵ�thread ������״̬�������һ���ж��źš���
                w.interruptIfStarted();
        } finally {
            //�ͷ��̳߳�ȫ����
            mainLock.unlock();
        }
    }

    /**
     * Interrupts threads that might be waiting for tasks (as
     * indicated by not being locked) so they can check for
     * termination or configuration changes. Ignores
     * SecurityExceptions (in which case some threads may remain
     * uninterrupted).
     *
     * @param onlyOne If true, interrupt at most one worker. This is
     * called only from tryTerminate when termination is otherwise
     * enabled but there are still other workers.  In this case, at
     * most one waiting worker is interrupted to propagate shutdown
     * signals in case all threads are currently waiting.
     * Interrupting any arbitrary thread ensures that newly arriving
     * workers since shutdown began will also eventually exit.
     * To guarantee eventual termination, it suffices to always
     * interrupt only one idle worker, but shutdown() interrupts all
     * idle workers so that redundant workers exit promptly, not
     * waiting for a straggler task to finish.
     */
    //onlyOne == true ˵��ֻ�ж�һ���߳� ��false ���ж������߳�
    //��ͬǰ�ᣬworker�ǿ���״̬��
    private void interruptIdleWorkers(boolean onlyOne) {
        final ReentrantLock mainLock = this.mainLock;
        //����ȫ����
        mainLock.lock();
        try {
            //��������worker
            for (Worker w : workers) {
                //��ȡ��ǰworker���߳� ���浽t
                Thread t = w.thread;
                //����һ������������!t.isInterrupted()  == true  ˵����ǰ����������߳���δ�жϡ�
                //��������w.tryLock() ����������˵����ǰworker���ڿ���״̬������ȥ����һ���ж��źš� Ŀǰworker�ڵ��߳� �� queue.take() | queue.poll()
                //�����С���Ϊworkerִ��taskʱ���Ǽ�����!
                if (!t.isInterrupted() && w.tryLock()) {
                    try {
                        //����ǰ�߳��ж��ź�..����queue�������̣߳��ᱻ���ѣ����Ѻ󣬽�����һ������ʱ�����ܻ�return null��ִ���˳���ص��߼���
                        t.interrupt();
                    } catch (SecurityException ignore) {
                    } finally {
                        //�ͷ�worker�Ķ�ռ����
                        w.unlock();
                    }
                }
                if (onlyOne)
                    break;
            }

        } finally {
            //�ͷ�ȫ������
            mainLock.unlock();
        }
    }

    /**
     * Common form of interruptIdleWorkers, to avoid having to
     * remember what the boolean argument means.
     */
    private void interruptIdleWorkers() {
        interruptIdleWorkers(false);
    }

    private static final boolean ONLY_ONE = true;

    /*
     * Misc utilities, most of which are also exported to
     * ScheduledThreadPoolExecutor
     */

    /**
     * Invokes the rejected execution handler for the given command.
     * Package-protected for use by ScheduledThreadPoolExecutor.
     */
    final void reject(Runnable command) {
        handler.rejectedExecution(command, this);
    }

    /**
     * Performs any further cleanup following run state transition on
     * invocation of shutdown.  A no-op here, but used by
     * ScheduledThreadPoolExecutor to cancel delayed tasks.
     */
    void onShutdown() {
    }

    /**
     * State check needed by ScheduledThreadPoolExecutor to
     * enable running tasks during shutdown.
     *
     * @param shutdownOK true if should return true if SHUTDOWN
     */
    final boolean isRunningOrShutdown(boolean shutdownOK) {
        int rs = runStateOf(ctl.get());
        return rs == RUNNING || (rs == SHUTDOWN && shutdownOK);
    }

    /**
     * Drains the task queue into a new list, normally using
     * drainTo. But if the queue is a DelayQueue or any other kind of
     * queue for which poll or drainTo may fail to remove some
     * elements, it deletes them one by one.
     */
    private List<Runnable> drainQueue() {
        BlockingQueue<Runnable> q = workQueue;
        ArrayList<Runnable> taskList = new ArrayList<Runnable>();
        q.drainTo(taskList);
        if (!q.isEmpty()) {
            for (Runnable r : q.toArray(new Runnable[0])) {
                if (q.remove(r))
                    taskList.add(r);
            }
        }
        return taskList;
    }

    /*
     * Methods for creating, running and cleaning up after workers
     */

    /**
     * Checks if a new worker can be added with respect to current
     * pool state and the given bound (either core or maximum). If so,
     * the worker count is adjusted accordingly, and, if possible, a
     * new worker is created and started, running firstTask as its
     * first task. This method returns false if the pool is stopped or
     * eligible to shut down. It also returns false if the thread
     * factory fails to create a thread when asked.  If the thread
     * creation fails, either due to the thread factory returning
     * null, or due to an exception (typically OutOfMemoryError in
     * Thread.start()), we roll back cleanly.
     *
     * @param firstTask the task the new thread should run first (or
     * null if none). Workers are created with an initial first task
     * (in method execute()) to bypass queuing when there are fewer
     * than corePoolSize threads (in which case we always start one),
     * or when the queue is full (in which case we must bypass queue).
     * Initially idle threads are usually created via
     * prestartCoreThread or to replace other dying workers.
     *
     * @param core if true use corePoolSize as bound, else
     * maximumPoolSize. (A boolean indicator is used here rather than a
     * value to ensure reads of fresh values after checking other pool
     * state).
     * @return true if successful
     */
    //firstTask ����Ϊnull����ʾ����worker֮��worker�Զ���queue�л�ȡ����.. �������null����worker����ִ��firstTask
    //core ���õ��߳������� ���Ϊtrue ���� �����߳�������  false���� maximumPoolSize�߳�������.

    //����ֵ�ܽ᣺
    //true ��ʾ����worker�ɹ������߳�����

    //false ��ʾ����ʧ�ܡ�
    //1.�̳߳�״̬rs > SHUTDOWN (STOP/TIDYING/TERMINATION)
    //2.rs == SHUTDOWN ���Ƕ������Ѿ�û�������� ���� ��ǰ״̬��SHUTDOWN�Ҷ���δ�գ�����firstTask��Ϊnull
    //3.��ǰ�̳߳��Ѿ��ﵽָ��ָ�꣨coprePoolSize ���� maximumPoolSIze��
    //4.threadFactory �������߳���null
    //5.thread��start֮ǰ����alive״̬
    private boolean addWorker(Runnable firstTask, boolean core) {
        //���� �жϵ�ǰ�̳߳�״̬�Ƿ��������̵߳����顣
        retry:
        for (;;) {
            //��ȡ��ǰctlֵ���浽c
            int c = ctl.get();
            //��ȡ��ǰ�̳߳�����״̬ ���浽rs��
            int rs = runStateOf(c);

            // Check if queue empty only if necessary.

            //����һ��rs >= SHUTDOWN ������˵����ǰ�̳߳�״̬����running״̬
            //��������ǰ����������ǰ���̳߳�״̬����running״̬  ! (rs == SHUTDOWN && firstTask == null && ! workQueue.isEmpty())
            //rs == SHUTDOWN && firstTask == null && ! workQueue.isEmpty()
            //��ʾ����ǰ�̳߳�״̬��SHUTDOWN״̬ & �ύ�������ǿգ�addWorker����������ܲ���execute���õġ� & ��ǰ������в��ǿ�
            //�ų��������������ǰ�̳߳���SHUTDOWN״̬�����Ƕ������滹��������δ�����꣬���ʱ�����������worker�����ǲ������ٴ��ύtask��
            if (rs >= SHUTDOWN &&
                    ! (rs == SHUTDOWN &&
                            firstTask == null &&
                            ! workQueue.isEmpty()))
                //ʲô����»ط���false?
                //�̳߳�״̬ rs > SHUTDOWN
                //rs == SHUTDOWN ���Ƕ������Ѿ�û�������� ���� rs == SHUTDOWN �� firstTask != null
                return false;

            //������Щ���룬�����ж� ��ǰ�̳߳�״̬ �Ƿ���������̡߳�


            //�ڲ����� ��ȡ�����߳����ƵĹ��̡�
            for (;;) {
                //��ȡ��ǰ�̳߳����߳����� ���浽wc��
                int wc = workerCountOf(c);

                //����һ��wc >= CAPACITY ��Զ����������ΪCAPACITY��һ��5�ڶ�������
                //��������wc >= (core ? corePoolSize : maximumPoolSize)
                //core == true ,�жϵ�ǰ�߳������Ƿ�>=corePoolSize�����ú����߳����������ơ�
                //core == false,�жϵ�ǰ�߳������Ƿ�>=maximumPoolSize����������߳����������ơ�
                if (wc >= CAPACITY ||
                        wc >= (core ? corePoolSize : maximumPoolSize))
                    //ִ�е����˵����ǰ�޷�����߳��ˣ��Ѿ��ﵽָ��������
                    return false;

                //����������˵����¼�߳������Ѿ���1�ɹ����൱�����뵽��һ�����ơ�
                //����ʧ�ܣ�˵�������������̣߳��޸Ĺ�ctl���ֵ�ˡ�
                //���ܷ�����ʲô�£�
                //1.�����߳�execute() ����������ˣ�����֮ǰ������CASʧ��
                //2.�ⲿ�߳̿��ܵ��ù� shutdown() ���� shutdownNow() �����̳߳�״̬�����仯�ˣ�����֪�� ctl ��3λ��ʾ״̬
                //״̬�ı��casҲ��ʧ�ܡ�
                if (compareAndIncrementWorkerCount(c))
                    //���뵽�����棬һ����cas�ɹ��������뵽������
                    //ֱ�������� retry �ⲿ���for������
                    break retry;

                //CASʧ�ܣ�û�гɹ������뵽����
                //��ȡ���µ�ctlֵ
                c = ctl.get();  // Re-read ctl
                //�жϵ�ǰ�̳߳�״̬�Ƿ������仯,����ⲿ����֮ǰ���ù�shutdown. shutdownNow �ᵼ��״̬�仯��
                if (runStateOf(c) != rs)
                    //״̬�����仯��ֱ�ӷ��ص����ѭ�������ѭ�������жϵ�ǰ�̳߳�״̬���Ƿ��������̡߳�
                    continue retry;
                // else CAS failed due to workerCount change; retry inner loop
            }
        }





        //��ʾ������worker�Ƿ��Ѿ�������falseδ����  true����
        boolean workerStarted = false;
        //��ʾ������worker�Ƿ���ӵ��������� Ĭ��false δ��� true����ӡ�
        boolean workerAdded = false;

        //w��ʾ���洴��worker��һ�����á�
        Worker w = null;
        try {
            //����Worker��ִ������߳�Ӧ�����Ѿ��������ˡ�
            w = new Worker(firstTask);

            //���´�����worker�ڵ���߳� ��ֵ�� t
            final Thread t = w.thread;

            //ΪʲôҪ�� t != null ����жϣ�
            //Ϊ�˷�ֹThreadFactory ʵ������bug����ΪThreadFactory ��һ���ӿڣ�˭������ʵ�֡�
            //��һ�ĸ� С��� ����һ�ȣ���bug�������������߳� ��null����
            //Doug lea���ǵıȽ�ȫ�档�϶����ֹ���Լ��ĳ��򱨿�ָ�룬��������һ��Ҫ����
            if (t != null) {
                //��ȫ���������ñ��浽mainLock
                final ReentrantLock mainLock = this.mainLock;
                //����ȫ���������ܻ�������ֱ����ȡ�ɹ�Ϊֹ��ͬһʱ�� ���� �̳߳��ڲ���صĲ����������������
                mainLock.lock();
                //���������֮�������߳� ���޷��޸ĵ�ǰ�̳߳�״̬�ġ�
                try {
                    // Recheck while holding lock.
                    // Back out on ThreadFactory failure or if
                    // shut down before lock acquired.
                    //��ȡ�����̳߳�����״̬���浽rs��
                    int rs = runStateOf(ctl.get());

                    //����һ��rs < SHUTDOWN ������������״̬����ǰ�̳߳�ΪRUNNING״̬.
                    //��������ǰ����������ǰ�̳߳�״̬����RUNNING״̬��
                    //(rs == SHUTDOWN && firstTask == null)  ��ǰ״̬ΪSHUTDOWN״̬��firstTaskΪ�ա���ʵ�жϵľ���SHUTDOWN״̬�µ����������
                    //ֻ�������ﲻ���ж϶����Ƿ�Ϊ����
                    if (rs < SHUTDOWN ||
                            (rs == SHUTDOWN && firstTask == null)) {
                        //t.isAlive() ���߳�start���߳�isAlive�᷵��true��
                        //��ֹ���ӷ��ȵĳ���Ա��ThreadFactory�����̷߳��ظ��ⲿ֮ǰ�����߳�start�ˡ���
                        if (t.isAlive()) // precheck that t is startable
                            throw new IllegalThreadStateException();

                        //�����Ǵ�����worker��ӵ��̳߳��С�
                        workers.add(w);
                        //��ȡ���µ�ǰ�̳߳��߳�����
                        int s = workers.size();
                        //����������˵����ǰ�߳�������һ���¸ߡ�����largestPoolSize
                        if (s > largestPoolSize)
                            largestPoolSize = s;
                        //��ʾ�߳��Ѿ�׷�ӽ��̳߳����ˡ�
                        workerAdded = true;
                    }
                } finally {
                    //�ͷ��̳߳�ȫ������
                    mainLock.unlock();
                }
                //��������:˵�� ���worker�ɹ�
                //����ʧ�ܣ�˵���̳߳���lock֮ǰ���̳߳�״̬�����˱仯���������ʧ�ܡ�
                if (workerAdded) {
                    //�ɹ����򽫴�����worker�������߳�������
                    t.start();
                    //�����������Ϊtrue
                    workerStarted = true;
                }
            }

        } finally {
            //����������! workerStarted ˵������ʧ�ܣ���Ҫ����������
            if (! workerStarted)
                //ʧ��ʱ��ʲô��������
                //1.�ͷ�����
                //2.����ǰworker�����workers����
                addWorkerFailed(w);
        }

        //�����´������߳��Ƿ�������
        return workerStarted;
    }

    /**
     * Rolls back the worker thread creation.
     * - removes worker from workers, if present
     * - decrements worker count
     * - rechecks for termination, in case the existence of this
     *   worker was holding up termination
     */
    private void addWorkerFailed(Worker w) {
        final ReentrantLock mainLock = this.mainLock;
        //�����̳߳�ȫ��������Ϊ���������̳߳���صĶ�����
        mainLock.lock();
        try {
            //������������Ҫ��worker��workers�������ȥ��
            if (w != null)
                workers.remove(w);
            //���̳߳ؼ����ָ�-1��ǰ��+1����������Ϊʧ�ܣ�����Ҫ-1���൱�ڹ黹���ơ�
            decrementWorkerCount();
            //��ͷ����shutdown shutdownNow��˵��
            tryTerminate();
        } finally {
            //�ͷ��̳߳�ȫ������
            mainLock.unlock();
        }

    }

    /**
     * Performs cleanup and bookkeeping for a dying worker. Called
     * only from worker threads. Unless completedAbruptly is set,
     * assumes that workerCount has already been adjusted to account
     * for exit.  This method removes thread from worker set, and
     * possibly terminates the pool or replaces the worker if either
     * it exited due to user task exception or if fewer than
     * corePoolSize workers are running or queue is non-empty but
     * there are no workers.
     *
     * @param w the worker
     * @param completedAbruptly if the worker died due to user exception
     */
    private void processWorkerExit(Worker w, boolean completedAbruptly) {
        //��������������ǰw ���worker�Ƿ����쳣�˳��ģ�task����ִ�й����������׳��쳣��..
        //�쳣�˳�ʱ��ctl��������û��-1
        if (completedAbruptly) // If abrupt, then workerCount wasn't adjusted
            decrementWorkerCount();

        //��ȡ�̳߳ص�ȫ��������
        final ReentrantLock mainLock = this.mainLock;
        //����
        mainLock.lock();
        try {
            //����ǰworker��ɵ�task���������ܵ��̳߳ص�completedTaskCount
            completedTaskCount += w.completedTasks;
            //��worker�ӳ������Ƴ�..
            workers.remove(w);
        } finally {
            //�ͷ�ȫ����
            mainLock.unlock();
        }


        tryTerminate();

        //��ȡ����ctlֵ
        int c = ctl.get();
        //������������ǰ�̳߳�״̬Ϊ RUNNING ���� SHUTDOWN״̬
        if (runStateLessThan(c, STOP)) {

            //������������ǰ�߳��������˳�..
            if (!completedAbruptly) {

                //min��ʾ�̳߳���ͳ��е��߳�����
                //allowCoreThreadTimeOut == true => ˵�������߳����ڵ��̣߳�Ҳ�ᳬʱ�����ա� min == 0
                //allowCoreThreadTimeOut == false => min == corePoolSize
                int min = allowCoreThreadTimeOut ? 0 : corePoolSize;


                //�̳߳�״̬��RUNNING SHUTDOWN
                //����һ������min == 0 ����
                //��������! workQueue.isEmpty() ˵����������л�������������Ҫ��һ���̡߳�
                if (min == 0 && ! workQueue.isEmpty())
                    min = 1;

                //�����������̳߳��л�ӵ���㹻���̡߳�
                //����һ�����⣺ workerCountOf(c) >= min  =>  (0 >= 0) ?
                //�п��ܣ�
                //ʲô����£� ���̳߳��еĺ����߳����ǿ��Ա����յ�����£�����������������������£���ǰ�̳߳��е��߳��� ���Ϊ0
                //�´����ύ����ʱ�����ٴ����̡߳�
                if (workerCountOf(c) >= min)
                    return; // replacement not needed
            }

            //1.��ǰ�߳���ִ��taskʱ �����쳣������һ��Ҫ����һ����worker����ȥ��
            //2.!workQueue.isEmpty() ˵����������л�������������Ҫ��һ���̡߳� ��ǰ״̬Ϊ RUNNING || SHUTDOWN
            //3.��ǰ�߳����� < corePoolSizeֵ����ʱ�ᴴ���̣߳�ά���̳߳�������corePoolSize����
            addWorker(null, false);
        }
    }

    /**
     * Performs blocking or timed wait for a task, depending on
     * current configuration settings, or returns null if this worker
     * must exit because of any of:
     * 1. There are more than maximumPoolSize workers (due to
     *    a call to setMaximumPoolSize).
     * 2. The pool is stopped.
     * 3. The pool is shutdown and the queue is empty.
     * 4. This worker timed out waiting for a task, and timed-out
     *    workers are subject to termination (that is,
     *    {@code allowCoreThreadTimeOut || workerCount > corePoolSize})
     *    both before and after the timed wait, and if the queue is
     *    non-empty, this worker is not the last thread in the pool.
     *
     * @return task, or null if the worker must exit, in which case
     *         workerCount is decremented
     */
    //ʲô����»᷵��null��
    //1.rs >= STOP ����˵������ǰ��״̬���Ҳ��STOP״̬��һ��Ҫ����null��
    //2.ǰ������ ״̬�� SHUTDOWN ��workQueue.isEmpty()
    //3.�̳߳��е��߳����� ���� �������ʱ������һ�����̷߳���Null
    //4.�̳߳��е��߳�������corePoolSizeʱ������һ�����߳� ��ʱ�󣬷���null��
    private Runnable getTask() {
        //��ʾ��ǰ�̻߳�ȡ�����Ƿ�ʱ Ĭ��false true��ʾ�ѳ�ʱ
        boolean timedOut = false; // Did the last poll() time out?

        //����
        for (;;) {
            //��ȡ����ctlֵ���浽c�С�
            int c = ctl.get();
            //��ȡ�̳߳ص�ǰ����״̬
            int rs = runStateOf(c);

            // Check if queue empty only if necessary.
            //����һ��rs >= SHUTDOWN ����������˵����ǰ�̳߳��Ƿ�RUNNING״̬�������� SHUTDOWN/STOP....
            //��������(rs >= STOP || workQueue.isEmpty())
            //2.1:rs >= STOP ����˵������ǰ��״̬���Ҳ��STOP״̬��һ��Ҫ����null��
            //2.2��ǰ������ ״̬�� SHUTDOWN ��workQueue.isEmpty()����������˵����ǰ�̳߳�״̬ΪSHUTDOWN״̬ �� ��������ѿգ���ʱһ������null��
            //����null��runWorker�����ͻὫ����Null���߳�ִ���߳��˳��̳߳ص��߼���
            if (rs >= SHUTDOWN && (rs >= STOP || workQueue.isEmpty())) {
                //ʹ��CAS+��ѭ���ķ�ʽ�� ctlֵ -1
                decrementWorkerCount();
                return null;
            }

            //ִ�е�����м��������
            //1.�̳߳���RUNNING״̬
            //2.�̳߳���SHUTDOWN״̬ ���Ƕ��л�δ�գ���ʱ���Դ����̡߳�

            //��ȡ�̳߳��е��߳�����
            int wc = workerCountOf(c);

            // Are workers subject to culling?
            //timed == true ��ʾ��ǰ����߳� ��ȡ task ʱ ��֧�ֳ�ʱ���Ƶģ�ʹ��queue.poll(xxx,xxx); ����ȡtask��ʱ������£���һ�������Ϳ��ܷ���null�ˡ�
            //timed == false ��ʾ��ǰ����߳� ��ȡ task ʱ �ǲ�֧�ֳ�ʱ���Ƶģ���ǰ�̻߳�ʹ�� queue.take();

            //���1��allowCoreThreadTimeOut == true ��ʾ�����߳������ڵ��߳� Ҳ���Ա����ա�
            //�����߳� ����ʹ��queue.poll(xxx,xxx) ��ʱ�������ַ�ʽ��ȡtask.
            //���2��allowCoreThreadTimeOut == false ��ʾ��ǰ�̳߳ػ�ά�����������ڵ��̡߳�
            //wc > corePoolSize
            //������������ǰ�̳߳��е��߳������Ǵ��ں����߳����ģ���ʱ������·��������̣߳�������poll ֧�ֳ�ʱ�ķ�ʽȥ��ȡ����
            //�������ͻ������һ�����̻߳�ȡ�������񣬻�ȡ�������� ����Null��Ȼ��..runWorker��ִ���߳��˳��߼���
            boolean timed = allowCoreThreadTimeOut || wc > corePoolSize;


            //����һ��(wc > maximumPoolSize || (timed && timedOut))
            //1.1��wc > maximumPoolSize  Ϊʲô�������setMaximumPoolSize()�����������ⲿ�߳̽��̳߳�����߳�������Ϊ�ȳ�ʼ��ʱ��ҪС
            //1.2: (timed && timedOut) ����������ǰ����������ǰ�߳�ʹ�� poll��ʽ��ȡtask����һ��ѭ��ʱ  ʹ��poll��ʽ��ȡ����ʱ����ʱ��
            //����һ Ϊtrue ��ʾ �߳̿��Ա����գ��ﵽ���ձ�׼����ȷʵ��Ҫ����ʱ�ٻ��ա�

            //��������(wc > 1 || workQueue.isEmpty())
            //2.1: wc > 1  ����������˵����ǰ�̳߳��л��������̣߳���ǰ�߳̿���ֱ�ӻ��գ�����null
            //2.2: workQueue.isEmpty() ǰ������ wc == 1�� ����������˵����ǰ������� �Ѿ����ˣ����һ���̣߳�Ҳ���Է��ĵ��˳���
            if ((wc > maximumPoolSize || (timed && timedOut))
                    && (wc > 1 || workQueue.isEmpty())) {
                //ʹ��CAS���� �� ctlֵ -1 ,��1�ɹ����̣߳�����null
                //CAS�ɹ��ģ�����Null
                //CASʧ�ܣ� Ϊʲô��CASʧ�ܣ�
                //1.�����߳�����һ���˳���
                //2.�̳߳�״̬�����仯�ˡ�
                if (compareAndDecrementWorkerCount(c))
                    return null;
                //�ٴ�����ʱ��timed�п��ܾ���false�ˣ���Ϊ��ǰ�߳�casʧ�ܣ����п�������Ϊ�����̳߳ɹ��˳����µģ��ٴ���ѯʱ
                //��鷢�֣���ǰ�߳� �Ϳ������� ����Ҫ���շ�Χ���ˡ�
                continue;
            }




            try {
                //��ȡ������߼�


                Runnable r = timed ?
                        workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) :
                        workQueue.take();

                //������������������
                if (r != null)
                    return r;

                //˵����ǰ�̳߳�ʱ��...
                timedOut = true;
            } catch (InterruptedException retry) {
                timedOut = false;
            }
        }
    }

    /**
     * Main worker run loop.  Repeatedly gets tasks from queue and
     * executes them, while coping with a number of issues:
     *
     * 1. We may start out with an initial task, in which case we
     * don't need to get the first one. Otherwise, as long as pool is
     * running, we get tasks from getTask. If it returns null then the
     * worker exits due to changed pool state or configuration
     * parameters.  Other exits result from exception throws in
     * external code, in which case completedAbruptly holds, which
     * usually leads processWorkerExit to replace this thread.
     *
     * 2. Before running any task, the lock is acquired to prevent
     * other pool interrupts while the task is executing, and then we
     * ensure that unless pool is stopping, this thread does not have
     * its interrupt set.
     *
     * 3. Each task run is preceded by a call to beforeExecute, which
     * might throw an exception, in which case we cause thread to die
     * (breaking loop with completedAbruptly true) without processing
     * the task.
     *
     * 4. Assuming beforeExecute completes normally, we run the task,
     * gathering any of its thrown exceptions to send to afterExecute.
     * We separately handle RuntimeException, Error (both of which the
     * specs guarantee that we trap) and arbitrary Throwables.
     * Because we cannot rethrow Throwables within Runnable.run, we
     * wrap them within Errors on the way out (to the thread's
     * UncaughtExceptionHandler).  Any thrown exception also
     * conservatively causes thread to die.
     *
     * 5. After task.run completes, we call afterExecute, which may
     * also throw an exception, which will also cause thread to
     * die. According to JLS Sec 14.20, this exception is the one that
     * will be in effect even if task.run throws.
     *
     * The net effect of the exception mechanics is that afterExecute
     * and the thread's UncaughtExceptionHandler have as accurate
     * information as we can provide about any problems encountered by
     * user code.
     *
     * @param w the worker
     */
    //w ��������worker
    final void runWorker(Worker w) {
        //wt == w.thread
        Thread wt = Thread.currentThread();
        //����ʼִ��task��ֵ��task
        Runnable task = w.firstTask;
        //��յ�ǰw.firstTask����
        w.firstTask = null;
        //����Ϊʲô�ȵ���unlock? ����Ϊ�˳�ʼ��worker state == 0 �� exclusiveOwnerThread ==null
        w.unlock(); // allow interrupts

        //�Ƿ���ͻȻ�˳���true->�����쳣�ˣ���ǰ�߳���ͻȻ�˳�����ͷ��Ҫ��һЩ����
        //false->�����˳���
        boolean completedAbruptly = true;

        try {
            //����һ��task != null ָ�ľ���firstTask�ǲ���null���������null��ֱ��ִ��ѭ�������档
            //��������(task = getTask()) != null   ����������˵����ǰ�߳���queue�л�ȡ����ɹ���getTask���������һ���������̵߳ķ���
            //getTask�������null����ǰ�߳���Ҫִ�н����߼���
            while (task != null || (task = getTask()) != null) {
                //worker���ö�ռ�� Ϊ��ǰ�߳�
                //ΪʲôҪ���ö�ռ���أ�shutdownʱ���жϵ�ǰworker״̬�����ݶ�ռ���Ƿ�������жϵ�ǰworker�Ƿ����ڹ�����
                w.lock();
                // If pool is stopping, ensure thread is interrupted;
                // if not, ensure thread is not interrupted.  This
                // requires a recheck in second case to deal with
                // shutdownNow race while clearing interrupt

                //����һ��runStateAtLeast(ctl.get(), STOP)  ˵���̳߳�Ŀǰ����STOP/TIDYING/TERMINATION ��ʱ�߳�һ��Ҫ����һ���ж��ź�
                //����һ������runStateAtLeast(ctl.get(), STOP)&& !wt.isInterrupted()
                //�������������˵����ǰ�̳߳�״̬��>=STOP �� ��ǰ�߳���δ�����ж�״̬�ģ���ʱ��Ҫ���뵽if���棬����ǰ�߳�һ���жϡ�

                //���裺runStateAtLeast(ctl.get(), STOP) == false
                // (Thread.interrupted() && runStateAtLeast(ctl.get(), STOP)) �ڸ����أ�
                // Thread.interrupted() ��ȡ��ǰ�ж�״̬���������ж�λΪfalse�������������Σ����interrupted()���� �ڶ���һ���Ƿ���false.
                // runStateAtLeast(ctl.get(), STOP) ��������ﻹ��false.
                // ��ʵ����ǿ��ˢ�µ�ǰ�̵߳��жϱ��λ false����Ϊ�п�����һ��ִ��taskʱ��ҵ��������潫��ǰ�̵߳��жϱ��λ ����Ϊ�� true����û�д���
                // ����һ��Ҫǿ��ˢ��һ�¡�������Ӱ�쵽�����task�ˡ�
                //���裺Thread.interrupted() == true  �� runStateAtLeast(ctl.get(), STOP)) == true
                //��������з�������ô��
                //�п��ܣ���Ϊ�ⲿ�߳��� ��һ�� (runStateAtLeast(ctl.get(), STOP) == false ���л������shutdown ��shutdownNow���������̳߳�״̬�޸�
                //���ʱ��Ҳ�Ὣ��ǰ�̵߳��жϱ��λ �ٴ����û� �ж�״̬��
                if ((runStateAtLeast(ctl.get(), STOP) ||
                        (Thread.interrupted() &&
                                runStateAtLeast(ctl.get(), STOP))) &&
                        !wt.isInterrupted())
                    wt.interrupt();


                try {
                    //���ӷ�������������ʵ�ֵ�
                    beforeExecute(wt, task);
                    //��ʾ�쳣��������thrown��Ϊ�գ���ʾ task���й����� ���ϲ��׳��쳣�ˡ�
                    Throwable thrown = null;
                    try {
                        //task ������FutureTask Ҳ������ ��ͨ��Runnable�ӿ�ʵ���ࡣ
                        //���ǰ����ͨ��submit()�ύ�� runnable/callable �ᱻ��װ�� FutureTask�������������뿴��һ�ڣ���bվ��
                        task.run();
                    } catch (RuntimeException x) {
                        thrown = x; throw x;
                    } catch (Error x) {
                        thrown = x; throw x;
                    } catch (Throwable x) {
                        thrown = x; throw new Error(x);
                    } finally {
                        //���ӷ�������������ʵ�ֵ�
                        afterExecute(task, thrown);
                    }
                } finally {
                    //���ֲ�����task��ΪNull
                    task = null;
                    //����worker�����������
                    w.completedTasks++;

                    //worker������һ������󣬻��ͷŵ���ռ��
                    //1.��������£����ٴλص�getTask()�����ȡ����  while(getTask...)
                    //2.task.run()ʱ�ڲ��׳��쳣��..
                    w.unlock();
                }
            }

            //ʲô����£����������
            //getTask()��������nullʱ��˵����ǰ�߳�Ӧ��ִ���˳��߼��ˡ�
            completedAbruptly = false;
        } finally {

            //task.run()�ڲ��׳��쳣ʱ��ֱ�Ӵ� w.unlock() ���� ������һ�С�
            //�����˳� completedAbruptly == false
            //�쳣�˳� completedAbruptly == true
            processWorkerExit(w, completedAbruptly);
        }
    }

    // Public constructors and methods

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters and default thread factory and rejected execution handler.
     * It may be more convenient to use one of the {@link Executors} factory
     * methods instead of this general purpose constructor.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *        pool
     * @param keepAliveTime when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     *        executed.  This queue will hold only the {@code Runnable}
     *        tasks submitted by the {@code execute} method.
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                Executors.defaultThreadFactory(), defaultHandler);
    }

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters and default rejected execution handler.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *        pool
     * @param keepAliveTime when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     *        executed.  This queue will hold only the {@code Runnable}
     *        tasks submitted by the {@code execute} method.
     * @param threadFactory the factory to use when the executor
     *        creates a new thread
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue}
     *         or {@code threadFactory} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                threadFactory, defaultHandler);
    }

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters and default thread factory.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *        pool
     * @param keepAliveTime when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     *        executed.  This queue will hold only the {@code Runnable}
     *        tasks submitted by the {@code execute} method.
     * @param handler the handler to use when execution is blocked
     *        because the thread bounds and queue capacities are reached
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue}
     *         or {@code handler} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              RejectedExecutionHandler handler) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                Executors.defaultThreadFactory(), handler);
    }

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *        pool
     * @param keepAliveTime when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     *        executed.  This queue will hold only the {@code Runnable}
     *        tasks submitted by the {@code execute} method.
     * @param threadFactory the factory to use when the executor
     *        creates a new thread
     * @param handler the handler to use when execution is blocked
     *        because the thread bounds and queue capacities are reached
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue}
     *         or {@code threadFactory} or {@code handler} is null
     */
    public ThreadPoolExecutor(int corePoolSize,//�����߳�������
                              int maximumPoolSize,//����߳�����
                              long keepAliveTime,//�����̴߳��ʱ��
                              TimeUnit unit,//ʱ�䵥λ seconds nano..
                              BlockingQueue<Runnable> workQueue,//�������
                              ThreadFactory threadFactory,//�̹߳���
                              RejectedExecutionHandler handler/*�ܾ�����*/) {
        //�жϲ����Ƿ�Խ��
        if (corePoolSize < 0 ||
                maximumPoolSize <= 0 ||
                maximumPoolSize < corePoolSize ||
                keepAliveTime < 0)
            throw new IllegalArgumentException();

        //�������� �� �̹߳��� �� �ܾ����� ������Ϊ�ա�
        if (workQueue == null || threadFactory == null || handler == null)
            throw new NullPointerException();


        this.acc = System.getSecurityManager() == null ?
                null :
                AccessController.getContext();

        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.workQueue = workQueue;
        this.keepAliveTime = unit.toNanos(keepAliveTime);
        this.threadFactory = threadFactory;
        this.handler = handler;
    }

    /**
     * Executes the given task sometime in the future.  The task
     * may execute in a new thread or in an existing pooled thread.
     *
     * If the task cannot be submitted for execution, either because this
     * executor has been shutdown or because its capacity has been reached,
     * the task is handled by the current {@code RejectedExecutionHandler}.
     *
     * @param command the task to execute
     * @throws RejectedExecutionException at discretion of
     *         {@code RejectedExecutionHandler}, if the task
     *         cannot be accepted for execution
     * @throws NullPointerException if {@code command} is null
     */
    //command ��������ͨ��Runnable ʵ���࣬Ҳ������ FutureTask
    public void execute(Runnable command) {
        //�ǿ��ж�..
        if (command == null)
            throw new NullPointerException();
        /*
         * Proceed in 3 steps:
         *
         * 1. If fewer than corePoolSize threads are running, try to
         * start a new thread with the given command as its first
         * task.  The call to addWorker atomically checks runState and
         * workerCount, and so prevents false alarms that would add
         * threads when it shouldn't, by returning false.
         *
         * 2. If a task can be successfully queued, then we still need
         * to double-check whether we should have added a thread
         * (because existing ones died since last checking) or that
         * the pool shut down since entry into this method. So we
         * recheck state and if necessary roll back the enqueuing if
         * stopped, or start a new thread if there are none.
         *
         * 3. If we cannot queue task, then we try to add a new
         * thread.  If it fails, we know we are shut down or saturated
         * and so reject the task.
         */
        //��ȡctl����ֵ��ֵ��c��ctl ����3λ ��ʾ�̳߳�״̬����λ��ʾ��ǰ�̳߳��߳�������
        int c = ctl.get();
        //workerCountOf(c) ��ȡ����ǰ�߳�����
        //������������ʾ��ǰ�߳�����С�ں����߳������˴��ύ����ֱ�Ӵ���һ���µ�worker����Ӧ�̳߳��ж���һ���µ��̡߳�
        if (workerCountOf(c) < corePoolSize) {
            //addWorker ��Ϊ�����̵߳Ĺ��̣��ᴴ��worker���󣬲��ҽ�command��ΪfirstTask
            //core == true ��ʾ���ú����߳���������  false��ʾ���� maximumPoolSize
            if (addWorker(command, true)) 
                //�����ɹ���ֱ�ӷ��ء�addWorker��������������´�����worker����firstTaskִ�С�
                return;

            //ִ�е�������䣬˵��addWorkerһ����ʧ����...
            //�м��ֿ����أ���
            //1.���ڲ�������execute�����ǿ����ж���߳�ͬʱ���õģ���workerCountOf(c) < corePoolSize������
            //�����߳̿���Ҳ�����ˣ��������̳߳��д�����worker�����ʱ���̳߳��еĺ����߳����Ѿ��ﵽ������...
            //2.��ǰ�̳߳�״̬�����ı��ˡ� RUNNING SHUTDOWN STOP TIDYING��TERMINATION
            //���̳߳�״̬�Ƿ�RUNNING״̬ʱ��addWorker(firstTask!=null, true|false) һ����ʧ�ܡ�
            //SHUTDOWN ״̬�£�Ҳ�п��ܴ����ɹ���ǰ�� firstTask == null ���ҵ�ǰ queue  ��Ϊ�ա����������
            c = ctl.get();
        }


        //ִ�е������м��������
        //1.��ǰ�߳������Ѿ��ﵽcorePoolSize
        //2.addWorkerʧ��..

        //����������˵����ǰ�̳߳ش���running״̬�����Խ� task ���뵽workQueue�С�
        if (isRunning(c) && workQueue.offer(command)) {
            //ִ�е����˵��offer�ύ����ɹ���..

            //�ٴλ�ȡctl���浽recheck��
            int recheck = ctl.get();

            //����һ��! isRunning(recheck) ������˵�����ύ������֮���̳߳�״̬���ⲿ�̸߳��޸� ���磺shutdown() shutdownNow()
            //������� ��Ҫ�Ѹո��ύ������ɾ������
            //��������remove(command) �п��ܳɹ���Ҳ�п���ʧ��
            //�ɹ����ύ֮���̳߳��е��̻߳�δ���ѣ�����
            //ʧ�ܣ��ύ֮����shutdown() shutdownNow()֮ǰ���ͱ��̳߳��е��߳� ������
            if (! isRunning(recheck) && remove(command))
                //�ύ֮���̳߳�״̬Ϊ ��running �� ������ӳɹ����߸��ܾ����ԡ�
                reject(command);

                //�м�������ᵽ���
                //1.��ǰ�̳߳���running״̬(����������)
                //2.�̳߳�״̬�Ƿ�running״̬ ����remove�ύ������ʧ��.

                //���� ��ǰ�̳߳���running״̬�������̳߳��еĴ���߳�������0�����ʱ�������0�Ļ���������Σ�����û�߳�ȥ����,
                //������ʵ��һ���������ƣ���֤�̳߳���running״̬�£����������һ���߳��ڹ�����
            else if (workerCountOf(recheck) == 0)
                addWorker(null, false);
        }

        //ִ�е�����м��������
        //1.offerʧ��
        //2.��ǰ�̳߳��Ƿ�running״̬

        //1.offerʧ�ܣ���Ҫ��ʲô�� ˵����ǰqueue ���ˣ����ʱ�� �����ǰ�߳�������δ�ﵽmaximumPoolSize�Ļ����ᴴ���µ�workerֱ��ִ��command
        //���赱ǰ�߳������ﵽmaximumPoolSize�Ļ�������Ҳ��ʧ�ܣ�Ҳ�߾ܾ����ԡ�

        //2.�̳߳�״̬Ϊ��running״̬�����ʱ����Ϊ command != null addWorker һ���Ƿ���false��
        else if (!addWorker(command, false))
            reject(command);

    }

    /**
     * Initiates an orderly shutdown in which previously submitted
     * tasks are executed, but no new tasks will be accepted.
     * Invocation has no additional effect if already shut down.
     *
     * <p>This method does not wait for previously submitted tasks to
     * complete execution.  Use {@link #awaitTermination awaitTermination}
     * to do that.
     *
     * @throws SecurityException {@inheritDoc}
     */
    public void shutdown() {
        final ReentrantLock mainLock = this.mainLock;
        //��ȡ�̳߳�ȫ����
        mainLock.lock();
        try {
            checkShutdownAccess();
            //�����̳߳�״̬ΪSHUTDOWN
            advanceRunState(SHUTDOWN);
            //�жϿ����߳�
            interruptIdleWorkers();
            //�շ��������������չ
            onShutdown(); // hook for ScheduledThreadPoolExecutor
        } finally {
            //�ͷ��̳߳�ȫ����
            mainLock.unlock();
        }
        //��ͷ˵..
        tryTerminate();
    }

    /**
     * Attempts to stop all actively executing tasks, halts the
     * processing of waiting tasks, and returns a list of the tasks
     * that were awaiting execution. These tasks are drained (removed)
     * from the task queue upon return from this method.
     *
     * <p>This method does not wait for actively executing tasks to
     * terminate.  Use {@link #awaitTermination awaitTermination} to
     * do that.
     *
     * <p>There are no guarantees beyond best-effort attempts to stop
     * processing actively executing tasks.  This implementation
     * cancels tasks via {@link Thread#interrupt}, so any task that
     * fails to respond to interrupts may never terminate.
     *
     * @throws SecurityException {@inheritDoc}
     */
    public List<Runnable> shutdownNow() {
        //����ֵ����
        List<Runnable> tasks;
        final ReentrantLock mainLock = this.mainLock;
        //��ȡ�̳߳�ȫ����
        mainLock.lock();
        try {
            checkShutdownAccess();
            //�����̳߳�״̬ΪSTOP
            advanceRunState(STOP);
            //�ж��̳߳��������߳�
            interruptWorkers();
            //����δ�����task
            tasks = drainQueue();
        } finally {
            mainLock.unlock();
        }

        tryTerminate();
        //���ص�ǰ��������� δ���������
        return tasks;
    }

    public boolean isShutdown() {
        return ! isRunning(ctl.get());
    }

    /**
     * Returns true if this executor is in the process of terminating
     * after {@link #shutdown} or {@link #shutdownNow} but has not
     * completely terminated.  This method may be useful for
     * debugging. A return of {@code true} reported a sufficient
     * period after shutdown may indicate that submitted tasks have
     * ignored or suppressed interruption, causing this executor not
     * to properly terminate.
     *
     * @return {@code true} if terminating but not yet terminated
     */
    public boolean isTerminating() {
        int c = ctl.get();
        return ! isRunning(c) && runStateLessThan(c, TERMINATED);
    }

    public boolean isTerminated() {
        return runStateAtLeast(ctl.get(), TERMINATED);
    }

    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (;;) {
                if (runStateAtLeast(ctl.get(), TERMINATED))
                    return true;
                if (nanos <= 0)
                    return false;
                nanos = termination.awaitNanos(nanos);
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Invokes {@code shutdown} when this executor is no longer
     * referenced and it has no threads.
     */
    protected void finalize() {
        SecurityManager sm = System.getSecurityManager();
        if (sm == null || acc == null) {
            shutdown();
        } else {
            PrivilegedAction<Void> pa = () -> { shutdown(); return null; };
            AccessController.doPrivileged(pa, acc);
        }
    }

    /**
     * Sets the thread factory used to create new threads.
     *
     * @param threadFactory the new thread factory
     * @throws NullPointerException if threadFactory is null
     * @see #getThreadFactory
     */
    public void setThreadFactory(ThreadFactory threadFactory) {
        if (threadFactory == null)
            throw new NullPointerException();
        this.threadFactory = threadFactory;
    }

    /**
     * Returns the thread factory used to create new threads.
     *
     * @return the current thread factory
     * @see #setThreadFactory(ThreadFactory)
     */
    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    /**
     * Sets a new handler for unexecutable tasks.
     *
     * @param handler the new handler
     * @throws NullPointerException if handler is null
     * @see #getRejectedExecutionHandler
     */
    public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        if (handler == null)
            throw new NullPointerException();
        this.handler = handler;
    }

    /**
     * Returns the current handler for unexecutable tasks.
     *
     * @return the current handler
     * @see #setRejectedExecutionHandler(RejectedExecutionHandler)
     */
    public RejectedExecutionHandler getRejectedExecutionHandler() {
        return handler;
    }

    /**
     * Sets the core number of threads.  This overrides any value set
     * in the constructor.  If the new value is smaller than the
     * current value, excess existing threads will be terminated when
     * they next become idle.  If larger, new threads will, if needed,
     * be started to execute any queued tasks.
     *
     * @param corePoolSize the new core size
     * @throws IllegalArgumentException if {@code corePoolSize < 0}
     * @see #getCorePoolSize
     */
    public void setCorePoolSize(int corePoolSize) {
        if (corePoolSize < 0)
            throw new IllegalArgumentException();
        int delta = corePoolSize - this.corePoolSize;
        this.corePoolSize = corePoolSize;
        if (workerCountOf(ctl.get()) > corePoolSize)
            interruptIdleWorkers();
        else if (delta > 0) {
            // We don't really know how many new threads are "needed".
            // As a heuristic, prestart enough new workers (up to new
            // core size) to handle the current number of tasks in
            // queue, but stop if queue becomes empty while doing so.
            int k = Math.min(delta, workQueue.size());
            while (k-- > 0 && addWorker(null, true)) {
                if (workQueue.isEmpty())
                    break;
            }
        }
    }

    /**
     * Returns the core number of threads.
     *
     * @return the core number of threads
     * @see #setCorePoolSize
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * Starts a core thread, causing it to idly wait for work. This
     * overrides the default policy of starting core threads only when
     * new tasks are executed. This method will return {@code false}
     * if all core threads have already been started.
     *
     * @return {@code true} if a thread was started
     */
    public boolean prestartCoreThread() {
        return workerCountOf(ctl.get()) < corePoolSize &&
                addWorker(null, true);
    }

    /**
     * Same as prestartCoreThread except arranges that at least one
     * thread is started even if corePoolSize is 0.
     */
    void ensurePrestart() {
        int wc = workerCountOf(ctl.get());
        if (wc < corePoolSize)
            addWorker(null, true);
        else if (wc == 0)
            addWorker(null, false);
    }

    /**
     * Starts all core threads, causing them to idly wait for work. This
     * overrides the default policy of starting core threads only when
     * new tasks are executed.
     *
     * @return the number of threads started
     */
    public int prestartAllCoreThreads() {
        int n = 0;
        while (addWorker(null, true))
            ++n;
        return n;
    }

    /**
     * Returns true if this pool allows core threads to time out and
     * terminate if no tasks arrive within the keepAlive time, being
     * replaced if needed when new tasks arrive. When true, the same
     * keep-alive policy applying to non-core threads applies also to
     * core threads. When false (the default), core threads are never
     * terminated due to lack of incoming tasks.
     *
     * @return {@code true} if core threads are allowed to time out,
     *         else {@code false}
     *
     * @since 1.6
     */
    public boolean allowsCoreThreadTimeOut() {
        return allowCoreThreadTimeOut;
    }

    /**
     * Sets the policy governing whether core threads may time out and
     * terminate if no tasks arrive within the keep-alive time, being
     * replaced if needed when new tasks arrive. When false, core
     * threads are never terminated due to lack of incoming
     * tasks. When true, the same keep-alive policy applying to
     * non-core threads applies also to core threads. To avoid
     * continual thread replacement, the keep-alive time must be
     * greater than zero when setting {@code true}. This method
     * should in general be called before the pool is actively used.
     *
     * @param value {@code true} if should time out, else {@code false}
     * @throws IllegalArgumentException if value is {@code true}
     *         and the current keep-alive time is not greater than zero
     *
     * @since 1.6
     */
    public void allowCoreThreadTimeOut(boolean value) {
        if (value && keepAliveTime <= 0)
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        if (value != allowCoreThreadTimeOut) {
            allowCoreThreadTimeOut = value;
            if (value)
                interruptIdleWorkers();
        }
    }

    /**
     * Sets the maximum allowed number of threads. This overrides any
     * value set in the constructor. If the new value is smaller than
     * the current value, excess existing threads will be
     * terminated when they next become idle.
     *
     * @param maximumPoolSize the new maximum
     * @throws IllegalArgumentException if the new maximum is
     *         less than or equal to zero, or
     *         less than the {@linkplain #getCorePoolSize core pool size}
     * @see #getMaximumPoolSize
     */
    public void setMaximumPoolSize(int maximumPoolSize) {
        if (maximumPoolSize <= 0 || maximumPoolSize < corePoolSize)
            throw new IllegalArgumentException();
        this.maximumPoolSize = maximumPoolSize;
        if (workerCountOf(ctl.get()) > maximumPoolSize)
            interruptIdleWorkers();
    }

    /**
     * Returns the maximum allowed number of threads.
     *
     * @return the maximum allowed number of threads
     * @see #setMaximumPoolSize
     */
    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    /**
     * Sets the time limit for which threads may remain idle before
     * being terminated.  If there are more than the core number of
     * threads currently in the pool, after waiting this amount of
     * time without processing a task, excess threads will be
     * terminated.  This overrides any value set in the constructor.
     *
     * @param time the time to wait.  A time value of zero will cause
     *        excess threads to terminate immediately after executing tasks.
     * @param unit the time unit of the {@code time} argument
     * @throws IllegalArgumentException if {@code time} less than zero or
     *         if {@code time} is zero and {@code allowsCoreThreadTimeOut}
     * @see #getKeepAliveTime(TimeUnit)
     */
    public void setKeepAliveTime(long time, TimeUnit unit) {
        if (time < 0)
            throw new IllegalArgumentException();
        if (time == 0 && allowsCoreThreadTimeOut())
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        long keepAliveTime = unit.toNanos(time);
        long delta = keepAliveTime - this.keepAliveTime;
        this.keepAliveTime = keepAliveTime;
        if (delta < 0)
            interruptIdleWorkers();
    }

    /**
     * Returns the thread keep-alive time, which is the amount of time
     * that threads in excess of the core pool size may remain
     * idle before being terminated.
     *
     * @param unit the desired time unit of the result
     * @return the time limit
     * @see #setKeepAliveTime(long, TimeUnit)
     */
    public long getKeepAliveTime(TimeUnit unit) {
        return unit.convert(keepAliveTime, TimeUnit.NANOSECONDS);
    }

    /* User-level queue utilities */

    /**
     * Returns the task queue used by this executor. Access to the
     * task queue is intended primarily for debugging and monitoring.
     * This queue may be in active use.  Retrieving the task queue
     * does not prevent queued tasks from executing.
     *
     * @return the task queue
     */
    public BlockingQueue<Runnable> getQueue() {
        return workQueue;
    }

    /**
     * Removes this task from the executor's internal queue if it is
     * present, thus causing it not to be run if it has not already
     * started.
     *
     * <p>This method may be useful as one part of a cancellation
     * scheme.  It may fail to remove tasks that have been converted
     * into other forms before being placed on the internal queue. For
     * example, a task entered using {@code submit} might be
     * converted into a form that maintains {@code Future} status.
     * However, in such cases, method {@link #purge} may be used to
     * remove those Futures that have been cancelled.
     *
     * @param task the task to remove
     * @return {@code true} if the task was removed
     */
    public boolean remove(Runnable task) {
        boolean removed = workQueue.remove(task);
        tryTerminate(); // In case SHUTDOWN and now empty
        return removed;
    }

    /**
     * Tries to remove from the work queue all {@link Future}
     * tasks that have been cancelled. This method can be useful as a
     * storage reclamation operation, that has no other impact on
     * functionality. Cancelled tasks are never executed, but may
     * accumulate in work queues until worker threads can actively
     * remove them. Invoking this method instead tries to remove them now.
     * However, this method may fail to remove tasks in
     * the presence of interference by other threads.
     */
    public void purge() {
        final BlockingQueue<Runnable> q = workQueue;
        try {
            Iterator<Runnable> it = q.iterator();
            while (it.hasNext()) {
                Runnable r = it.next();
                if (r instanceof Future<?> && ((Future<?>)r).isCancelled())
                    it.remove();
            }
        } catch (ConcurrentModificationException fallThrough) {
            // Take slow path if we encounter interference during traversal.
            // Make copy for traversal and call remove for cancelled entries.
            // The slow path is more likely to be O(N*N).
            for (Object r : q.toArray())
                if (r instanceof Future<?> && ((Future<?>)r).isCancelled())
                    q.remove(r);
        }

        tryTerminate(); // In case SHUTDOWN and now empty
    }

    /* Statistics */

    /**
     * Returns the current number of threads in the pool.
     *
     * @return the number of threads
     */
    public int getPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // Remove rare and surprising possibility of
            // isTerminated() && getPoolSize() > 0
            return runStateAtLeast(ctl.get(), TIDYING) ? 0
                    : workers.size();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the approximate number of threads that are actively
     * executing tasks.
     *
     * @return the number of threads
     */
    public int getActiveCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            int n = 0;
            for (Worker w : workers)
                if (w.isLocked())
                    ++n;
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the largest number of threads that have ever
     * simultaneously been in the pool.
     *
     * @return the number of threads
     */
    public int getLargestPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            return largestPoolSize;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the approximate total number of tasks that have ever been
     * scheduled for execution. Because the states of tasks and
     * threads may change dynamically during computation, the returned
     * value is only an approximation.
     *
     * @return the number of tasks
     */
    public long getTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers) {
                n += w.completedTasks;
                if (w.isLocked())
                    ++n;
            }
            return n + workQueue.size();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the approximate total number of tasks that have
     * completed execution. Because the states of tasks and threads
     * may change dynamically during computation, the returned value
     * is only an approximation, but one that does not ever decrease
     * across successive calls.
     *
     * @return the number of tasks
     */
    public long getCompletedTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers)
                n += w.completedTasks;
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns a string identifying this pool, as well as its state,
     * including indications of run state and estimated worker and
     * task counts.
     *
     * @return a string identifying this pool, as well as its state
     */
    public String toString() {
        long ncompleted;
        int nworkers, nactive;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            ncompleted = completedTaskCount;
            nactive = 0;
            nworkers = workers.size();
            for (Worker w : workers) {
                ncompleted += w.completedTasks;
                if (w.isLocked())
                    ++nactive;
            }
        } finally {
            mainLock.unlock();
        }
        int c = ctl.get();
        String rs = (runStateLessThan(c, SHUTDOWN) ? "Running" :
                (runStateAtLeast(c, TERMINATED) ? "Terminated" :
                        "Shutting down"));
        return super.toString() +
                "[" + rs +
                ", pool size = " + nworkers +
                ", active threads = " + nactive +
                ", queued tasks = " + workQueue.size() +
                ", completed tasks = " + ncompleted +
                "]";
    }

    /* Extension hooks */

    /**
     * Method invoked prior to executing the given Runnable in the
     * given thread.  This method is invoked by thread {@code t} that
     * will execute task {@code r}, and may be used to re-initialize
     * ThreadLocals, or to perform logging.
     *
     * <p>This implementation does nothing, but may be customized in
     * subclasses. Note: To properly nest multiple overridings, subclasses
     * should generally invoke {@code super.beforeExecute} at the end of
     * this method.
     *
     * @param t the thread that will run task {@code r}
     * @param r the task that will be executed
     */
    protected void beforeExecute(Thread t, Runnable r) { }

    /**
     * Method invoked upon completion of execution of the given Runnable.
     * This method is invoked by the thread that executed the task. If
     * non-null, the Throwable is the uncaught {@code RuntimeException}
     * or {@code Error} that caused execution to terminate abruptly.
     *
     * <p>This implementation does nothing, but may be customized in
     * subclasses. Note: To properly nest multiple overridings, subclasses
     * should generally invoke {@code super.afterExecute} at the
     * beginning of this method.
     *
     * <p><b>Note:</b> When actions are enclosed in tasks (such as
     * {@link FutureTask}) either explicitly or via methods such as
     * {@code submit}, these task objects catch and maintain
     * computational exceptions, and so they do not cause abrupt
     * termination, and the internal exceptions are <em>not</em>
     * passed to this method. If you would like to trap both kinds of
     * failures in this method, you can further probe for such cases,
     * as in this sample subclass that prints either the direct cause
     * or the underlying exception if a task has been aborted:
     *
     *  <pre> {@code
     * class ExtendedExecutor extends ThreadPoolExecutor {
     *   // ...
     *   protected void afterExecute(Runnable r, Throwable t) {
     *     super.afterExecute(r, t);
     *     if (t == null && r instanceof Future<?>) {
     *       try {
     *         Object result = ((Future<?>) r).get();
     *       } catch (CancellationException ce) {
     *           t = ce;
     *       } catch (ExecutionException ee) {
     *           t = ee.getCause();
     *       } catch (InterruptedException ie) {
     *           Thread.currentThread().interrupt(); // ignore/reset
     *       }
     *     }
     *     if (t != null)
     *       System.out.println(t);
     *   }
     * }}</pre>
     *
     * @param r the runnable that has completed
     * @param t the exception that caused termination, or null if
     * execution completed normally
     */
    protected void afterExecute(Runnable r, Throwable t) { }

    /**
     * Method invoked when the Executor has terminated.  Default
     * implementation does nothing. Note: To properly nest multiple
     * overridings, subclasses should generally invoke
     * {@code super.terminated} within this method.
     */
    protected void terminated() { }

    /* Predefined RejectedExecutionHandlers */

    /**
     * A handler for rejected tasks that runs the rejected task
     * directly in the calling thread of the {@code execute} method,
     * unless the executor has been shut down, in which case the task
     * is discarded.
     */
    public static class CallerRunsPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code CallerRunsPolicy}.
         */
        public CallerRunsPolicy() { }

        /**
         * Executes task r in the caller's thread, unless the executor
         * has been shut down, in which case the task is discarded.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                r.run();
            }
        }
    }

    /**
     * A handler for rejected tasks that throws a
     * {@code RejectedExecutionException}.
     */
    public static class AbortPolicy implements RejectedExecutionHandler {
        /**
         * Creates an {@code AbortPolicy}.
         */
        public AbortPolicy() { }

        /**
         * Always throws RejectedExecutionException.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         * @throws RejectedExecutionException always
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            throw new RejectedExecutionException("Task " + r.toString() +
                    " rejected from " +
                    e.toString());
        }
    }

    /**
     * A handler for rejected tasks that silently discards the
     * rejected task.
     */
    public static class DiscardPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code DiscardPolicy}.
         */
        public DiscardPolicy() { }

        /**
         * Does nothing, which has the effect of discarding task r.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        }
    }

    /**
     * A handler for rejected tasks that discards the oldest unhandled
     * request and then retries {@code execute}, unless the executor
     * is shut down, in which case the task is discarded.
     */
    public static class DiscardOldestPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code DiscardOldestPolicy} for the given executor.
         */
        public DiscardOldestPolicy() { }

        /**
         * Obtains and ignores the next task that the executor
         * would otherwise execute, if one is immediately available,
         * and then retries execution of task r, unless the executor
         * is shut down, in which case task r is instead discarded.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                e.getQueue().poll();
                e.execute(r);
            }
        }
    }
}
