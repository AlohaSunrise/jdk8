/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;
import java.lang.ref.*;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * This class provides thread-local variables.  These variables differ from
 * their normal counterparts in that each thread that accesses one (via its
 * {@code get} or {@code set} method) has its own, independently initialized
 * copy of the variable.  {@code ThreadLocal} instances are typically private
 * static fields in classes that wish to associate state with a thread (e.g.,
 * a user ID or Transaction ID).
 *
 * <p>For example, the class below generates unique identifiers local to each
 * thread.
 * A thread's id is assigned the first time it invokes {@code ThreadId.get()}
 * and remains unchanged on subsequent calls.
 * <pre>
 * import java.util.concurrent.atomic.AtomicInteger;
 *
 * public class ThreadId {
 *     // Atomic integer containing the next thread ID to be assigned
 *     private static final AtomicInteger nextId = new AtomicInteger(0);
 *
 *     // Thread local variable containing each thread's ID
 *     private static final ThreadLocal&lt;Integer&gt; threadId =
 *         new ThreadLocal&lt;Integer&gt;() {
 *             &#64;Override protected Integer initialValue() {
 *                 return nextId.getAndIncrement();
 *         }
 *     };
 *
 *     // Returns the current thread's unique ID, assigning it if necessary
 *     public static int get() {
 *         return threadId.get();
 *     }
 * }
 * </pre>
 * <p>Each thread holds an implicit reference to its copy of a thread-local
 * variable as long as the thread is alive and the {@code ThreadLocal}
 * instance is accessible; after a thread goes away, all of its copies of
 * thread-local instances are subject to garbage collection (unless other
 * references to these copies exist).
 *
 * �����ṩ���ֲ߳̾� (thread-local) ������ ��Щ������ͬ�����ǵ���ͨ��Ӧ�
 * ��Ϊ����ĳ��������ͨ���� get �� set ��������ÿ���̶߳����Լ��ľֲ�����
 * �������ڱ����ĳ�ʼ��������ThreadLocal ʵ��ͨ�������е� private static �ֶ�
 * ����ϣ����״̬��ĳһ���̣߳����磬�û� ID ������ ID���������
 *
 * ���磬���������ɶ�ÿ���߳�Ψһ�ľֲ���ʶ����
 *
 * �߳� ID ���ڵ�һ�ε��� UniqueThreadIdGenerator.getCurrentThreadId() ʱ����ģ�
 * �ں��������в�����ġ�
 * <pre>
 * import java.util.concurrent.atomic.AtomicInteger;
 *
 * public class ThreadId {
 *     // ԭ����������������һ��������߳�Thread ID
 *     private static final AtomicInteger nextId = new AtomicInteger(0);
 *
 *     // ÿһ���̶߳�Ӧ��Thread ID
 *     private static final ThreadLocal<Integer> threadId =
 *         new ThreadLocal<Integer>() {
 *             @Override protected Integer initialValue() {
 *                 return nextId.getAndIncrement();
 *         }
 *     };
 *
 *     // ���ص�ǰ�̶߳�Ӧ��ΨһThread ID, ��Ҫʱ����з���
 *     public static int get() {
 *         return threadId.get();
 *     }
 * }
 * </pre>
 * ÿ���̶߳����ֶ����ֲ߳̾�������������ʽ���ã�ֻҪ�߳��ǻ�Ĳ��� ThreadLocal ʵ���ǿɷ��ʵ�
 * ���߳���ʧ֮�����ֲ߳̾�ʵ�������и������ᱻ�������գ������Ǵ��ڶ���Щ�������������ã���
 *
 *
 * @author  Josh Bloch and Doug Lea
 * @since   1.2
 */
public class ThreadLocal<T> {
    /**
     * ThreadLocals rely on per-thread linear-probe hash maps attached
     * to each thread (Thread.threadLocals and
     * inheritableThreadLocals).  The ThreadLocal objects act as keys,
     * searched via threadLocalHashCode.  This is a custom hash code
     * (useful only within ThreadLocalMaps) that eliminates collisions
     * in the common case where consecutively constructed ThreadLocals
     * are used by the same threads, while remaining well-behaved in
     * less common cases.
     */
    //�̻߳�ȡthreadLocal.get()ʱ ����ǵ�һ����ĳ�� threadLocal������getʱ�������ǰ�̷߳���һ��value
    //���value �� ��ǰ��threadLocal���� ����װ��Ϊһ�� entry ���� key�� threadLocal����value��threadLocal�������ǰ�߳����ɵ�value
    //���entry��ŵ� ��ǰ�߳� threadLocals ���map���ĸ�Ͱλ�� �뵱ǰ threadLocal�����threadLocalHashCode �й�ϵ��
    // ʹ�� threadLocalHashCode & (table.length - 1) �ĵ���λ�� ���ǵ�ǰ entry��Ҫ��ŵ�λ�á�
    private final int threadLocalHashCode = nextHashCode();

    /**
     * The next hash code to be given out. Updated atomically. Starts at
     * zero.
     * ����ThreadLocal����ʱ ��ʹ�õ���ÿ����һ��threadLocal���� �ͻ�ʹ��nextHashCode ����һ��hashֵ���������
     */
    private static AtomicInteger nextHashCode =
            new AtomicInteger();

    /**
     * The difference between successively generated hash codes - turns
     * implicit sequential thread-local IDs into near-optimally spread
     * multiplicative hash values for power-of-two-sized tables.
     * ÿ����һ��ThreadLocal�������ThreadLocal.nextHashCode ���ֵ�ͻ����� 0x61c88647 ��
     * ���ֵ �����⣬���� 쳲�������  Ҳ�� �ƽ�ָ�����hash����Ϊ ������֣������ĺô����� hash�ֲ��ǳ����ȡ�
     */
    private static final int HASH_INCREMENT = 0x61c88647;

    /**
     * Returns the next hash code.
     * �����µ�ThreadLocal����ʱ  �����ǰ�������һ��hash��ʹ�����������
     */
    private static int nextHashCode() {
        return nextHashCode.getAndAdd(HASH_INCREMENT);
    }

    /**
     * Returns the current thread's "initial value" for this
     * thread-local variable.  This method will be invoked the first
     * time a thread accesses the variable with the {@link #get}
     * method, unless the thread previously invoked the {@link #set}
     * method, in which case the {@code initialValue} method will not
     * be invoked for the thread.  Normally, this method is invoked at
     * most once per thread, but it may be invoked again in case of
     * subsequent invocations of {@link #remove} followed by {@link #get}.
     *
     * <p>This implementation simply returns {@code null}; if the
     * programmer desires thread-local variables to have an initial
     * value other than {@code null}, {@code ThreadLocal} must be
     * subclassed, and this method overridden.  Typically, an
     * anonymous inner class will be used.
     *
     * @return the initial value for this thread-local
     * Ĭ�Ϸ���null��һ������� ���Ƕ�����Ҫ��д��������ġ�
     */
    protected T initialValue() {
        return null;
    }

    /**
     * Creates a thread local variable. The initial value of the variable is
     * determined by invoking the {@code get} method on the {@code Supplier}.
     *
     * @param <S> the type of the thread local's value
     * @param supplier the supplier to be used to determine the initial value
     * @return a new thread local variable
     * @throws NullPointerException if the specified supplier is null
     * @since 1.8
     */
    public static <S> ThreadLocal<S> withInitial(Supplier<? extends S> supplier) {
        return new SuppliedThreadLocal<>(supplier);
    }

    /**
     * Creates a thread local variable.
     * @see #withInitial(java.util.function.Supplier)
     */
    public ThreadLocal() {
    }

    /**
     * Returns the value in the current thread's copy of this
     * thread-local variable.  If the variable has no value for the
     * current thread, it is first initialized to the value returned
     * by an invocation of the {@link #initialValue} method.
     *
     * ���ص�ǰ�߳��뵱ǰThreadLocal����������� �ֲ߳̾��������������ֻ�е�ǰ�߳��ܷ��ʵ���
     * �����ǰ�߳� û�з��䣬�����ǰ�߳�ȥ���䣨ʹ��initialValue������
     *
     * @return the current thread's value of this thread-local
     */
    public T get() {
        //��ȡ��ǰ�߳�
        Thread t = Thread.currentThread();
        //��ȡ����ǰ�߳�Thread����� threadLocals map����
        ThreadLocalMap map = getMap(t);
        //����������˵����ǰ�߳��Ѿ�ӵ���Լ��� ThreadLocalMap ������
        if (map != null) {
            //key����ǰthreadLocal����
            //����map.getEntry() ���� ��ȡthreadLocalMap �и�threadLocal������ entry
            ThreadLocalMap.Entry e = map.getEntry(this);
            //����������˵����ǰ�߳� ��ʼ���� �뵱ǰthreadLocal����������� �ֲ߳̾�����
            if (e != null) {
                @SuppressWarnings("unchecked")
                T result = (T)e.value;
                //����value..
                return result;
            }
        }

        //ִ�е������м��������
        //1.��ǰ�̶߳�Ӧ��threadLocalMap�ǿ�
        //2.��ǰ�߳��뵱ǰthreadLocal����û�����ɹ�������� �ֲ߳̾�����..

        //setInitialValue������ʼ����ǰ�߳��뵱ǰthreadLocal���� �������value��
        //�� ��ǰ�߳����û��threadLocalMap�Ļ��������ʼ������map��
        return setInitialValue();
    }

    /**
     * Variant of set() to establish initialValue. Used instead
     * of set() in case user has overridden the set() method.
     *
     * setInitialValue������ʼ����ǰ�߳��뵱ǰthreadLocal���� �������value��
     * �� ��ǰ�߳����û��threadLocalMap�Ļ��������ʼ������map��
     * @return the initial value
     */
    private T setInitialValue() {
        //���õĵ�ǰThreadLocal�����initialValue������������� �󲿷���������Ƕ�����д��
        //value ���ǵ�ǰThreadLocal�����뵱ǰ�߳�������� �ֲ߳̾�������
        T value = initialValue();
        //��ȡ��ǰ�̶߳���
        Thread t = Thread.currentThread();
        //��ȡ��ǰ�߳��ڲ���threadLocals    threadLocalMap����
        ThreadLocalMap map = getMap(t);
        //����������˵����ǰ�߳��ڲ��Ѿ���ʼ���� threadLocalMap�����ˡ� ���̵߳�threadLocals ֻ���ʼ��һ�Ρ���
        if (map != null)
            //���浱ǰthreadLocal�뵱ǰ�߳����ɵ� �ֲ߳̾�������
            //key: ��ǰthreadLocal����   value���߳��뵱ǰthreadLocal��صľֲ�����
            map.set(this, value);
        else
            //ִ�е����˵�� ��ǰ�߳��ڲ���δ��ʼ�� threadLocalMap ���������createMap ����ǰ�̴߳���map

            //����1����ǰ�߳�   ����2���߳��뵱ǰthreadLocal��صľֲ�����
            createMap(t, value);

        //�����߳��뵱ǰthreadLocal��صľֲ�����
        return value;
    }

    /**
     * Sets the current thread's copy of this thread-local variable
     * to the specified value.  Most subclasses will have no need to
     * override this method, relying solely on the {@link #initialValue}
     * method to set the values of thread-locals.
     *
     * �޸ĵ�ǰ�߳��뵱ǰthreadLocal����������� �ֲ߳̾�������
     *
     * @param value the value to be stored in the current thread's copy of
     *        this thread-local.
     */
    public void set(T value) {
        //��ȡ��ǰ�߳�
        Thread t = Thread.currentThread();
        //��ȡ��ǰ�̵߳�threadLocalMap����
        ThreadLocalMap map = getMap(t);
        //����������˵����ǰ�̵߳�threadLocalMap�Ѿ���ʼ������
        if (map != null)
            //����threadLocalMap.set���� ������д ���� ��ӡ�
            map.set(this, value);
        else
            //ִ�е����˵����ǰ�̻߳�δ���� threadLocalMap����

            //����1����ǰ�߳�   ����2���߳��뵱ǰthreadLocal��صľֲ�����
            createMap(t, value);
    }

    /**
     * Removes the current thread's value for this thread-local
     * variable.  If this thread-local variable is subsequently
     * {@linkplain #get read} by the current thread, its value will be
     * reinitialized by invoking its {@link #initialValue} method,
     * unless its value is {@linkplain #set set} by the current thread
     * in the interim.  This may result in multiple invocations of the
     * {@code initialValue} method in the current thread.
     *
     * �Ƴ���ǰ�߳��뵱ǰthreadLocal����������� �ֲ߳̾�������
     *
     * @since 1.5
     */
    public void remove() {
        //��ȡ��ǰ�̵߳� threadLocalMap����
        ThreadLocalMap m = getMap(Thread.currentThread());
        //����������˵����ǰ�߳��Ѿ���ʼ���� threadLocalMap������
        if (m != null)
            //����threadLocalMap.remove( key = ��ǰthreadLocal)
            m.remove(this);
    }

    /**
     * Get the map associated with a ThreadLocal. Overridden in
     * InheritableThreadLocal.
     *
     * @param  t the current thread
     * @return the map
     */
    ThreadLocalMap getMap(Thread t) {
        //���ص�ǰ�̵߳� threadLocals
        return t.threadLocals;
    }

    /**
     * Create the map associated with a ThreadLocal. Overridden in
     * InheritableThreadLocal.
     *
     * @param t the current thread
     * @param firstValue value for the initial entry of the map
     */
    void createMap(Thread t, T firstValue) {
        //����t ��������� Ҫ���� ��ǰ����߳� t.threadLocals �ֶΣ�������ֶγ�ʼ����


        //new ThreadLocalMap(this, firstValue)
        //����һ��ThreadLocalMap���� ��ʼ k-v Ϊ �� this <��ǰthreadLocal����> ���߳��뵱ǰthreadLocal��صľֲ�����
        t.threadLocals = new ThreadLocalMap(this, firstValue);
    }

    /**
     * Factory method to create map of inherited thread locals.
     * Designed to be called only from Thread constructor.
     *
     * @param  parentMap the map associated with parent thread
     * @return a map containing the parent's inheritable bindings
     */
    static ThreadLocalMap createInheritedMap(ThreadLocalMap parentMap) {
        return new ThreadLocalMap(parentMap);
    }

    /**
     * Method childValue is visibly defined in subclass
     * InheritableThreadLocal, but is internally defined here for the
     * sake of providing createInheritedMap factory method without
     * needing to subclass the map class in InheritableThreadLocal.
     * This technique is preferable to the alternative of embedding
     * instanceof tests in methods.
     */
    T childValue(T parentValue) {
        throw new UnsupportedOperationException();
    }

    /**
     * An extension of ThreadLocal that obtains its initial value from
     * the specified {@code Supplier}.
     */
    static final class SuppliedThreadLocal<T> extends ThreadLocal<T> {

        private final Supplier<? extends T> supplier;

        SuppliedThreadLocal(Supplier<? extends T> supplier) {
            this.supplier = Objects.requireNonNull(supplier);
        }

        @Override
        protected T initialValue() {
            return supplier.get();
        }
    }

    /**
     * ThreadLocalMap is a customized hash map suitable only for
     * maintaining thread local values. No operations are exported
     * outside of the ThreadLocal class. The class is package private to
     * allow declaration of fields in class Thread.  To help deal with
     * very large and long-lived usages, the hash table entries use
     * WeakReferences for keys. However, since reference queues are not
     * used, stale entries are guaranteed to be removed only when
     * the table starts running out of space.
     *
     *
     * ThreadLocalMap ��һ�����Ƶ��Զ��� hashMap ��ϣ��ֻ�ʺ�����ά��
     * �̶߳�ӦThreadLocal��ֵ. ����ķ���û����ThreadLocal ���ⲿ��¶��
     * ������˽�еģ������� Thread �������ֶε���ʽ���� ��
     * �����ڴ���洢�����������ڳ���ʹ����;��
     * ���ඨ�ƵĹ�ϣ��ʵ���ֵ��ʹ��������WeakReferences ��Ϊkey��
     * ����, һ�����ò��ڱ�ʹ�ã�ֻ�е���ϣ���еĿռ䱻�ľ�ʱ����Ӧ����ʹ�õļ�ֵ��ʵ��Ż�ȷ�����Ƴ����ա�
     */
    static class ThreadLocalMap {

        /**
         * The entries in this hash map extend WeakReference, using
         * its main ref field as the key (which is always a
         * ThreadLocal object).  Note that null keys (i.e. entry.get()
         * == null) mean that the key is no longer referenced, so the
         * entry can be expunged from table.  Such entries are referred to
         * as "stale entries" in the code that follows.
         *
         * ʲô���������أ�
         * A a = new A();     //ǿ����
         * WeakReference weakA = new WeakReference(a);  //������
         *
         * a = null;
         * ��һ��GC ʱ ����a�ͱ������ˣ������û�� ������ �Ƿ��ڹ����������
         *
         * key ʹ�õ��������ñ�����key�������threadLocal����
         * value ʹ�õ���ǿ���ã�value������� threadLocal�����뵱ǰ�߳�������� value��
         *
         * entry#key ���������ʲô�ô��أ�
         * ��threadLocal����ʧȥǿ�����Ҷ���GC���պ�ɢ�б��е��� threadLocal����������� entry#key �ٴ�ȥkey.get() ʱ���õ�����null��
         * վ��map�ǶȾͿ������ֳ���Щentry�ǹ��ڵģ���Щentry�Ƿǹ��ڵġ�
         *
         *
         */
        static class Entry extends WeakReference<ThreadLocal<?>> {
            /** The value associated with this ThreadLocal. */
            Object value;

            Entry(ThreadLocal<?> k, Object v) {
                super(k);
                value = v;
            }
        }

        /**
         * The initial capacity -- MUST be a power of two.
         * ��ʼ����ǰmap�ڲ� ɢ�б�����ĳ�ʼ���� 16
         */
        private static final int INITIAL_CAPACITY = 16;

        /**
         * The table, resized as necessary.
         * table.length MUST always be a power of two.
         * threadLocalMap �ڲ�ɢ�б��������ã�����ĳ��� ������ 2�Ĵη���
         */
        private Entry[] table;

        /**
         * The number of entries in the table.
         * ��ǰɢ�б����� ռ���������Ŷ��ٸ�entry��
         */
        private int size = 0;

        /**
         * The next size value at which to resize.
         * ���ݴ�����ֵ����ʼֵΪ�� len * 2/3
         * ��������� rehash() ������
         * rehash() ��������һ��ȫ�����ȫ�� �������ݣ���ɢ�б������й��ڵ�entry�Ƴ���
         * ����Ƴ�֮�� ��ǰ ɢ�б��е�entry ������Ȼ�ﵽ  threshold - threshold/4  �ͽ������ݡ�
         */
        private int threshold; // Default to 0

        /**
         * Set the resize threshold to maintain at worst a 2/3 load factor.
         * ����ֵ����Ϊ ����ǰ���鳤�� * 2��/ 3��
         */
        private void setThreshold(int len) {
            threshold = len * 2 / 3;
        }

        /**
         * Increment i modulo len.
         * ����1����ǰ�±�
         * ����2����ǰɢ�б����鳤��
         */
        private static int nextIndex(int i, int len) {
            //��ǰ�±�+1 С��ɢ�б�����Ļ������� +1���ֵ
            //���� ������� �±�+1 == len ������0
            //ʵ���γ�һ������ʽ�ķ��ʡ�
            return ((i + 1 < len) ? i + 1 : 0);
        }

        /**
         * Decrement i modulo len.
         * ����1����ǰ�±�
         * ����2����ǰɢ�б����鳤��
         */
        private static int prevIndex(int i, int len) {
            //��ǰ�±�-1 ���ڵ���0 ���� -1���ֵ��ok��
            //���� ˵�� ��ǰ�±�-1 == -1. ��ʱ ����ɢ�б�����±ꡣ
            //ʵ���γ�һ������ʽ�ķ��ʡ�
            return ((i - 1 >= 0) ? i - 1 : len - 1);
        }

        /**
         * Construct a new map initially containing (firstKey, firstValue).
         * ThreadLocalMaps are constructed lazily, so we only create
         * one when we have at least one entry to put in it.
         *
         * ��ΪThread.threadLocals�ֶ����ӳٳ�ʼ���ģ�ֻ���̵߳�һ�δ洢 threadLocal-value ʱ �Żᴴ�� threadLocalMap����
         *
         * firstKey :threadLocal����
         * firstValue: ��ǰ�߳���threadLocal���������value��
         */
        ThreadLocalMap(ThreadLocal<?> firstKey, Object firstValue) {
            //����entry���鳤��Ϊ16����ʾthreadLocalMap�ڲ���ɢ�б�
            table = new Entry[INITIAL_CAPACITY];
            //Ѱַ�㷨��key.threadLocalHashCode & (table.length - 1)
            //table����ĳ���һ���� 2 �Ĵη�����
            //2�Ĵη���-1 ��ʲô�����أ�  ת��Ϊ2���ƺ���1.    16==> 1 0000 - 1 => 1111
            //1111 ���κ���ֵ����&����� �õ�����ֵ һ���� <= 1111

            //i ��������Ľ�� һ���� <= B1111
            int i = firstKey.threadLocalHashCode & (INITIAL_CAPACITY - 1);

            //����entry���� ��ŵ� ָ��λ�õ�slot�С�
            table[i] = new Entry(firstKey, firstValue);
            //����size=1
            size = 1;
            //����������ֵ ����ǰ���鳤�� * 2��/ 3  => 16 * 2 / 3 => 10
            setThreshold(INITIAL_CAPACITY);
        }

        /**
         * Construct a new map including all Inheritable ThreadLocals
         * from given parent map. Called only by createInheritedMap.
         *
         * @param parentMap the map associated with parent thread.
         */
        private ThreadLocalMap(ThreadLocalMap parentMap) {
            Entry[] parentTable = parentMap.table;
            int len = parentTable.length;
            setThreshold(len);
            table = new Entry[len];

            for (int j = 0; j < len; j++) {
                Entry e = parentTable[j];
                if (e != null) {
                    @SuppressWarnings("unchecked")
                    ThreadLocal<Object> key = (ThreadLocal<Object>) e.get();
                    if (key != null) {
                        Object value = key.childValue(e.value);
                        Entry c = new Entry(key, value);
                        int h = key.threadLocalHashCode & (len - 1);
                        while (table[h] != null)
                            h = nextIndex(h, len);
                        table[h] = c;
                        size++;
                    }
                }
            }
        }

        /**
         * Get the entry associated with key.  This method
         * itself handles only the fast path: a direct hit of existing
         * key. It otherwise relays to getEntryAfterMiss.  This is
         * designed to maximize performance for direct hits, in part
         * by making this method readily inlinable.
         * ThreadLocal���� get() ���� ʵ�������� ThreadLocalMap.getEntry() ������ɵġ�
         *
         * key:ĳ�� ThreadLocal������Ϊ ɢ�б��д洢��entry.key ������ ThreadLocal��
         *
         * @param  key the thread local object
         * @return the entry associated with key, or null if no such
         */
        private Entry getEntry(ThreadLocal<?> key) {
            //·�ɹ��� ThreadLocal.threadLocalHashCode & (table.length - 1) ==�� index
            int i = key.threadLocalHashCode & (table.length - 1);
            //����ɢ�б��� ָ��ָ��λ�õ� slot
            Entry e = table[i];
            //����һ������ ˵��slot��ֵ
            //������������ ˵�� entry#key �뵱ǰ��ѯ��keyһ�£����ص�ǰentry ���ϲ�Ϳ����ˡ�
            if (e != null && e.get() == key)
                return e;
            else
                //�м��������ִ�е����
                //1.e == null
                //2.e.key != key


                //getEntryAfterMiss ���� �������ǰͰλ����������� e.key == key ��entry.

                //Ϊʲô�������أ���
                //��Ϊ �洢ʱ  ����hash��ͻ�󣬲�û����entry�����γ� ����.. �洢ʱ�Ĵ��� �������Ե�����ҵ�һ������ʹ�õ�slot�����Ҵ�Ž�ȥ��
                return getEntryAfterMiss(key, i, e);
        }

        /**
         * Version of getEntry method for use when key is not found in
         * its direct hash slot.
         *
         * @param  key the thread local object           threadLocal���� ��ʾkey
         * @param  i the table index for key's hash code  key���������index
         * @param  e the entry at table[i]                table[index] �е� entry
         * @return the entry associated with key, or null if no such
         */
        private Entry getEntryAfterMiss(ThreadLocal<?> key, int i, Entry e) {
            //��ȡ��ǰthreadLocalMap�е�ɢ�б� table
            Entry[] tab = table;
            //��ȡtable����
            int len = tab.length;

            //������e != null ˵�� �����ҵķ�Χ�����޵ģ����� slot == null �����������������
            //e:ѭ������ĵ�ǰԪ��
            while (e != null) {
                //��ȡ��ǰslot ��entry�����key
                ThreadLocal<?> k = e.get();
                //����������˵������ѯ�������ҵ����ʵ�entry�ˣ�����entry��ok�ˡ�
                if (k == key)
                    //�ҵ�������£��ʹ����ﷵ���ˡ�
                    return e;
                //����������˵����ǰslot�е�entry#key ������ ThreadLocal�����Ѿ���GC������.. ��Ϊkey �������ã� key = e.get() == null.
                if (k == null)
                    //��һ�� ̽��ʽ�������ݻ��ա�
                    expungeStaleEntry(i);
                else
                    //����index���������������
                    i = nextIndex(i, len);
                //��ȡ��һ��slot�е�entry��
                e = tab[i];
            }

            //ִ�е����˵�����������ڶ�û�ҵ���Ӧ���ݡ�
            return null;
        }

        /**
         * Set the value associated with key.
         *
         * ThreadLocal ʹ��set���� ����ǰ�߳���� threadLocal-value   ��ֵ�ԡ�
         *
         * @param key the thread local object
         * @param value the value to be set
         */
        private void set(ThreadLocal<?> key, Object value) {
            //��ȡɢ�б�
            Entry[] tab = table;
            //��ȡɢ�б����鳤��
            int len = tab.length;
            //���㵱ǰkey �� ɢ�б��еĶ�Ӧ��λ��
            int i = key.threadLocalHashCode & (len-1);


            //�Ե�ǰkey��Ӧ��slotλ�� ����ѯ���ҵ�����ʹ�õ�slot��
            //ʲôslot����ʹ���أ���
            //1.k == key ˵�����滻
            //2.����һ�����ڵ� slot �����ʱ�� ���ǿ���ǿ��ռ���¡�
            //3.���ҹ����� ���� slot == null �ˡ�
            for (Entry e = tab[i];
                 e != null;
                 e = tab[i = nextIndex(i, len)]) {

                //��ȡ��ǰԪ��key
                ThreadLocal<?> k = e.get();

                //����������˵����ǰset������һ���滻������
                if (k == key) {
                    //���滻�߼���
                    e.value = value;
                    return;
                }

                //����������˵�� ����Ѱ�ҹ����� ����entry#key == null ������ˣ�˵����ǰentry �ǹ������ݡ�
                if (k == null) {
                    //����һ�����ڵ� slot �����ʱ�� ���ǿ���ǿ��ռ���¡�
                    //�滻�������ݵ��߼���
                    replaceStaleEntry(key, value, i);
                    return;
                }
            }



            //ִ�е����˵��forѭ�������� slot == null �������
            //�ں��ʵ�slot�� ����һ���µ�entry����
            tab[i] = new Entry(key, value);
            //��Ϊ������� ����++size.
            int sz = ++size;

            //��һ������ʽ����
            //����һ��!cleanSomeSlots(i, sz) ������˵������ʽ������ δ�����κ�����..
            //��������sz >= threshold ������˵����ǰtable�ڵ�entry�Ѿ��ﵽ������ֵ��..�ᴥ��rehash������
            if (!cleanSomeSlots(i, sz) && sz >= threshold)
                rehash();
        }

        /**
         * Remove the entry for key.
         */
        private void remove(ThreadLocal<?> key) {
            Entry[] tab = table;
            int len = tab.length;
            int i = key.threadLocalHashCode & (len-1);
            for (Entry e = tab[i];
                 e != null;
                 e = tab[i = nextIndex(i, len)]) {
                if (e.get() == key) {
                    e.clear();
                    expungeStaleEntry(i);
                    return;
                }
            }
        }

        /**
         * Replace a stale entry encountered during a set operation
         * with an entry for the specified key.  The value passed in
         * the value parameter is stored in the entry, whether or not
         * an entry already exists for the specified key.
         *
         * As a side effect, this method expunges all stale entries in the
         * "run" containing the stale entry.  (A run is a sequence of entries
         * between two null slots.)
         *
         * @param  key the key
         * @param  value the value to be associated with key
         * @param  staleSlot index of the first stale entry encountered while
         *         searching for key.
         * key: �� threadLocal����
         * value: val
         * staleSlot: �ϲ㷽�� set��������������ʱ ���ֵĵ�ǰ���slot��һ�����ڵ� entry��
         */
        private void replaceStaleEntry(ThreadLocal<?> key, Object value,
                                       int staleSlot) {
            //��ȡɢ�б�
            Entry[] tab = table;
            //��ȡɢ�б����鳤��
            int len = tab.length;
            //��ʱ����
            Entry e;

            //��ʾ ��ʼ̽��ʽ����������ݵ� ��ʼ�±ꡣĬ�ϴӵ�ǰ staleSlot��ʼ��
            int slotToExpunge = staleSlot;


            //�Ե�ǰstaleSlot��ʼ ��ǰ�������ң�����û�й��ڵ����ݡ�forѭ��һֱ������null������
            for (int i = prevIndex(staleSlot, len);
                 (e = tab[i]) != null;
                 i = prevIndex(i, len)){
                //����������˵����ǰ�ҵ��˹������ݣ����� ̽������������ݵĿ�ʼ�±�Ϊ i
                if (e.get() == null){
                    slotToExpunge = i;
                }
            }

            //�Ե�ǰstaleSlot���ȥ���ң�ֱ������nullΪֹ��
            for (int i = nextIndex(staleSlot, len);
                 (e = tab[i]) != null;
                 i = nextIndex(i, len)) {
                //��ȡ��ǰԪ�� key
                ThreadLocal<?> k = e.get();

                //����������˵��������һ�� �滻�߼���
                if (k == key) {
                    //�滻�����ݡ�
                    e.value = value;

                    //����λ�õ��߼�..
                    //��table[staleSlot]����������� �ŵ� ��ǰѭ������ table[i] ���λ�á�
                    tab[i] = tab[staleSlot];
                    //��tab[staleSlot] �б���Ϊ ��ǰentry�� �����Ļ��������������λ�þͱ��Ż���..
                    tab[staleSlot] = e;

                    //����������
                    // 1.˵��replaceStaleEntry һ��ʼʱ ����ǰ���ҹ������� ��δ�ҵ����ڵ�entry.
                    // 2.����������Ҳδ���ֹ�������..
                    if (slotToExpunge == staleSlot)
                        //��ʼ̽��ʽ����������ݵ��±� �޸�Ϊ ��ǰѭ����index��
                        slotToExpunge = i;


                    //cleanSomeSlots ������ʽ����
                    cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
                    return;
                }

                //����1��k == null ������˵����ǰ������entry��һ����������..
                //����2��slotToExpunge == staleSlot ������һ��ʼʱ ����ǰ���ҹ������� ��δ�ҵ����ڵ�entry.
                if (k == null && slotToExpunge == staleSlot)
                    //��Ϊ����ѯ�����в��ҵ�һ�����������ˣ�����slotToExpunge Ϊ ��ǰλ�á�
                    //ǰ�������� ǰ��ɨ��ʱ δ���� ��������..
                    slotToExpunge = i;
            }

            //ʲôʱ��ִ�е������أ�
            //�����ҹ����� ��δ���� k == key ��entry��˵����ǰset���� ��һ������߼�..

            //ֱ�ӽ���������ӵ� table[staleSlot] ��Ӧ��slot�С�
            tab[staleSlot].value = null;
            tab[staleSlot] = new Entry(key, value);


            //�������������˵�ǰstaleSlot ���� �������������Ĺ���slot��.. ����Ҫ���� �������ݵ��߼�..
            if (slotToExpunge != staleSlot)
                cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
        }

        /**
         * Expunge a stale entry by rehashing any possibly colliding entries
         * lying between staleSlot and the next null slot.  This also expunges
         * any other stale entries encountered before the trailing null.  See
         * Knuth, Section 6.4
         *
         * ���� staleSlot   table[staleSlot] ����һ���������ݣ������λ�ÿ�ʼ ���������ҹ������ݣ�ֱ������ slot == null �����������
         *
         * @param staleSlot index of slot known to have null key
         * @return the index of the next null slot after staleSlot
         * (all between staleSlot and this slot will have been checked
         * for expunging).
         */
        private int expungeStaleEntry(int staleSlot) {
            //��ȡɢ�б�
            Entry[] tab = table;
            //��ȡɢ�б�ǰ����
            int len = tab.length;

            // expunge entry at staleSlot
            //help gc
            tab[staleSlot].value = null;
            //��ΪstaleSlotλ�õ�entry �ǹ��ڵ� ����ֱ����ΪNull
            tab[staleSlot] = null;
            //��Ϊ����ɵ�һ��Ԫ�أ����� -1.
            size--;

            // Rehash until we encounter null
            //e����ʾ��ǰ�����ڵ�
            Entry e;
            //i����ʾ��ǰ������index
            int i;

            //forѭ���� staleSlot + 1��λ�ÿ�ʼ�����������ݣ�ֱ������ slot == null ������
            for (i = nextIndex(staleSlot, len);
                 (e = tab[i]) != null;
                 i = nextIndex(i, len)) {
                //���뵽forѭ������ ��ǰentryһ����Ϊnull


                //��ȡ��ǰ�����ڵ� entry ��key.
                ThreadLocal<?> k = e.get();

                //����������˵��k��ʾ��threadLocal���� �Ѿ���GC������... ��ǰentry������������...
                if (k == null) {
                    //help gc
                    e.value = null;
                    //�����ݶ�Ӧ��slot��Ϊnull
                    tab[i] = null;
                    //��Ϊ����ɵ�һ��Ԫ�أ����� -1.
                    size--;
                } else {
                    //ִ�е����˵����ǰ������slot�ж�Ӧ��entry �Ƿǹ�������
                    //��Ϊǰ���п���������˼����������ݡ�
                    //�ҵ�ǰentry �洢ʱ�п�������hash��ͻ�ˣ�����ƫ�ƴ洢�ˣ����ʱ�� Ӧ��ȥ�Ż�λ�ã������λ�ø����� ��ȷλ�á�
                    //�����Ļ�����ѯ��ʱ�� Ч�ʲŻ���ߣ�

                    //���¼��㵱ǰentry��Ӧ�� index
                    int h = k.threadLocalHashCode & (len - 1);
                    //����������˵����ǰentry�洢ʱ ���Ƿ�����hash��ͻ��Ȼ�����ƫ�ƹ���...
                    if (h != i) {
                        //��entry��ǰλ�� ����Ϊnull
                        tab[i] = null;

                        // Unlike Knuth 6.4 Algorithm R, we must scan until
                        // null because multiple entries could have been stale.


                        //h ����ȷλ�á�

                        //����ȷλ��h ��ʼ�������ҵ�һ�� ���Դ��entry��λ�á�
                        while (tab[h] != null)
                            h = nextIndex(h, len);

                        //����ǰԪ�ط��뵽 ������ȷλ�� ������λ�ã��п��ܾ�����ȷλ�ã���
                        tab[h] = e;
                    }
                }
            }
            return i;
        }

        /**
         * Heuristically scan some cells looking for stale entries.
         * This is invoked when either a new element is added, or
         * another stale one has been expunged. It performs a
         * logarithmic number of scans, as a balance between no
         * scanning (fast but retains garbage) and a number of scans
         * proportional to number of elements, that would find all
         * garbage but would cause some insertions to take O(n) time.
         *
         * @param i a position known NOT to hold a stale entry. The
         * scan starts at the element after i.
         *
         * @param n scan control: {@code log2(n)} cells are scanned,
         * unless a stale entry is found, in which case
         * {@code log2(table.length)-1} additional cells are scanned.
         * When called from insertions, this parameter is the number
         * of elements, but when from replaceStaleEntry, it is the
         * table length. (Note: all this could be changed to be either
         * more or less aggressive by weighting n instead of just
         * using straight log n. But this version is simple, fast, and
         * seems to work well.)
         *
         * ���� i ����ʽ��������ʼλ��
         * ���� n һ�㴫�ݵ��� table.length ������n Ҳ��ʾ����������
         * @return true if any stale entries have been removed.
         */
        private boolean cleanSomeSlots(int i, int n) {
            //��ʾ����ʽ������ �Ƿ��������������
            boolean removed = false;
            //��ȡ��ǰmap��ɢ�б�����
            Entry[] tab = table;
            //��ȡ��ǰɢ�б����鳤��
            int len = tab.length;

            do {
                //����Ϊʲô���Ǵ�i�ͼ���أ�
                //��ΪcleanSomeSlots(i = expungeStaleEntry(???), n)  expungeStaleEntry(???) ����ֵһ����null��

                //��ȡ��ǰi����һ�� �±�
                i = nextIndex(i, len);
                //��ȡtable�е�ǰ�±�Ϊi��Ԫ��
                Entry e = tab[i];
                //����һ��e != null ����
                //��������e.get() == null ������˵����ǰslot�б����entry ��һ�����ڵ�����..
                if (e != null && e.get() == null) {
                    //���¸���nΪ table���鳤��
                    n = len;
                    //��ʾ���������.
                    removed = true;
                    //�Ե�ǰ���ڵ�slotΪ��ʼ�ڵ� ��һ�� ̽��ʽ������
                    i = expungeStaleEntry(i);
                }


                // ����table����Ϊ16
                // 16 >>> 1 ==> 8
                // 8 >>> 1 ==> 4
                // 4 >>> 1 ==> 2
                // 2 >>> 1 ==> 1
                // 1 >>> 1 ==> 0
            } while ( (n >>>= 1) != 0);

            return removed;
        }

        /**
         * Re-pack and/or re-size the table. First scan the entire
         * table removing stale entries. If this doesn't sufficiently
         * shrink the size of the table, double the table size.
         */
        private void rehash() {
            //�������ִ����󣬵�ǰɢ�б��ڵ����й��ڵ����ݣ����ᱻ�ɵ���
            expungeStaleEntries();


            // Use lower threshold for doubling to avoid hysteresis
            //����������˵�������� �������ݺ󣬵�ǰɢ�б��ڵ�entry������Ȼ�ﵽ�� threshold * 3/4���������� ���ݣ�
            if (size >= threshold - threshold / 4)
                //���ݡ�
                resize();
        }

        /**
         * Double the capacity of the table.
         */
        private void resize() {
            //��ȡ��ǰɢ�б�
            Entry[] oldTab = table;
            //��ȡ��ǰɢ�б���
            int oldLen = oldTab.length;
            //��������ݺ�ı��С  oldLen * 2
            int newLen = oldLen * 2;
            //����һ���µ�ɢ�б�
            Entry[] newTab = new Entry[newLen];
            //��ʾ��table�е�entry������
            int count = 0;

            //�����ϱ� Ǩ�����ݵ��±�
            for (int j = 0; j < oldLen; ++j) {
                //�����ϱ��ָ��λ�õ�slot
                Entry e = oldTab[j];
                //����������˵���ϱ��е�ָ��λ�� ������
                if (e != null) {
                    //��ȡentry#key
                    ThreadLocal<?> k = e.get();
                    //����������˵���ϱ��еĵ�ǰλ�õ�entry ��һ����������..
                    if (k == null) {
                        e.value = null; // Help the GC
                    } else {
                        //ִ�е����˵���ϱ�ĵ�ǰλ�õ�Ԫ���Ƿǹ������� �������ݣ���ҪǨ�Ƶ����ݺ���±���

                        //�������ǰentry�����ݺ���±�� �洢λ�á�
                        int h = k.threadLocalHashCode & (newLen - 1);
                        //whileѭ�� �����õ�һ������h�����һ������ʹ�õ�slot��
                        while (newTab[h] != null)
                            h = nextIndex(h, newLen);

                        //�����ݴ�ŵ� �±�� ���ʵ�slot�С�
                        newTab[h] = e;
                        //����+1
                        count++;
                    }
                }
            }


            //������һ�δ������ݵ�ָ�ꡣ
            setThreshold(newLen);
            size = count;
            //�����ݺ���±� �����ñ��浽 threadLocalMap ����� table�����
            table = newTab;
        }

        /**
         * Expunge all stale entries in the table.
         */
        private void expungeStaleEntries() {
            Entry[] tab = table;
            int len = tab.length;
            for (int j = 0; j < len; j++) {
                Entry e = tab[j];
                if (e != null && e.get() == null)
                    expungeStaleEntry(j);
            }
        }
    }
}
