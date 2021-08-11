package java.util.concurrent.locks;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

/**
 * ClassName: MiniReentrantLock
 * Description:
 * date: 2020/3/28 14:04
 *
 * @author С����ʦ��΢�ţ�vv517956494
 * ���γ����� С����ʦ VIP Դ����ѵ��γ�
 * �Ͻ��Ƿ����ã����з��ַǷ���ȡ��Ϊ���ؽ�׷���������Σ�
 * <p>
 * ����ͬѧ���ַ� С����Դ�� �ٷ��Ŵ�������Ƶ��Դ������ϵ�ң�
 * @since 1.0.0
 */
public class MiniReentrantLock {
    /**
     * ������ʲô��
     * ��Դ -> state
     * 0 ��ʾδ����״̬
     * >0 ��ʾ��ǰlock�Ǽ���״̬..
     */
    private volatile int state;

    /**
     * ��ռģʽ��
     * ͬһʱ��ֻ��һ���߳̿��Գ��������������̣߳���δ��ȡ����ʱ���ᱻ����..
     */
    //��ǰ��ռ�����̣߳�ռ�����̣߳�
    private Thread exclusiveOwnerThread;


    /**
     * ��Ҫ������������ά�� ��������
     * 1.Head ָ����е�ͷ�ڵ�
     * 2.Tail ָ����е�β�ڵ�
     */
    //�Ƚ����⣺head�ڵ��Ӧ���߳� ���ǵ�ǰռ�������߳�
    private Node head;
    private Node tail;





    /**
     * �������̱߳���װ��ʲô����
     * Node�ڵ㣬Ȼ����뵽FIFO����
     */
    static final class Node {
        //ǰ�ýڵ�����
        Node prev;
        //���ýڵ�����
        Node next;
        //��װ���̱߳���
        Thread thread;

        public Node(Thread thread) {
            this.thread = thread;
        }

        public Node() {
        }
    }


    /**
     * ��ȡ��
     * ���赱ǰ����ռ�ã���������������߳�,ֱ����ռ����Ϊֹ
     * ģ�⹫ƽ��
     * ʲô�ǹ�ƽ���� ����һ�������󵽣�
     *
     * lock�Ĺ����������ģ�
     * �龰1���߳̽������֣���ǰstate == 0 �����ʱ��ͺ����� ֱ������.
     * �龰2���߳̽������֣���ǰstate > 0 , ���ʱ�����Ҫ����ǰ�߳���ӡ�
     */
   
    public void lock() {
        //��һ�λ�ȡ����ʱ����state == 1
        //��n������ʱ����state == n
        acquire(1);
    }

    /**
     * ������Դ
     * 1.���Ի�ȡ�����ɹ��� ռ���� �ҷ���..
     * 2.��ռ��ʧ�ܣ�������ǰ�߳�..
     */
    private void acquire(int arg) {
        if(!tryAcquire(arg)) {
            Node node = addWaiter();
            acquireQueued(node, arg);
        }
    }

    /**
     * ������ռ��ʧ�ܣ���Ҫ��ʲô�أ�
     * 1.��Ҫ����ǰ�̷߳�װ�� node�����뵽 ��������
     * 2.��Ҫ����ǰ�߳�park����ʹ�̴߳��� ���� ״̬
     *
     * ���Ѻ��أ�
     * 1.��鵱ǰnode�ڵ� �Ƿ�Ϊ head.next �ڵ�
     *   ��head.next �ڵ���ӵ����ռȨ�޵��߳�.������node��û����ռ��Ȩ�ޡ���
     * 2.��ռ
     *   �ɹ���1.����ǰnode����Ϊhead�����ϵ�head���Ӳ��������ص�ҵ�����
     *   ʧ�ܣ�2.����park���ȴ�������..
     *
     * ====>
     * 1.��ӵ� �������е��߼�  addWaiter()
     * 2.������Դ���߼�        acquireQueued()
     */


    private void acquireQueued(Node node, int arg) {
        //ֻ�е�ǰnode�ɹ���ȡ���� �Ժ� �Ż�����������
        for(;;) {
            //ʲô����£���ǰnode������֮����Գ���ȥ��ȡ���أ�
            //ֻ��һ���������ǰnode�� head �ĺ�̽ڵ㡣�������Ȩ�ޡ�


            //head �ڵ� ���� ��ǰ�����ڵ�..
            Node pred = node.prev;
            if(pred == head/*����������˵����ǰnodeӵ����ռȨ��..*/ && tryAcquire(arg)) {
                //�����棬˵����ǰ�߳� �������ɹ�����
                //��Ҫ����ʲô��
                //1.���õ�ǰhead Ϊ��ǰ�̵߳�node
                //2.Э�� ԭʼ head ����
                setHead(node);
                pred.next = null; // help GC
                return;
            }

            System.out.println("�̣߳�" + Thread.currentThread().getName() + "������");
            //����ǰ�̹߳���
            LockSupport.park();
            System.out.println("�̣߳�" + Thread.currentThread().getName() + "�����ѣ�");

            //ʲôʱ���ѱ�park���߳��أ�
            //unlock �����ˣ�
        }
    }

    /**
     * ��ǰ�߳����
     * ���ص�ǰ�̶߳�Ӧ��Node�ڵ�
     *
     * addWaiter����ִ����Ϻ󣬱�֤��ǰ�߳��Ѿ���ӳɹ���
     */
    private Node addWaiter() {
        Node newNode = new Node(Thread.currentThread());
        //�������أ�
        //1.�ҵ�newNode��ǰ�ýڵ� pred
        //2.����newNode.prev = pred
        //3.CAS ����tail Ϊ newNode
        //4.���� pred.next = newNode

        //ǰ�������������Ѿ��еȴ���node�ˣ���ǰnode ���ǵ�һ����ӵ�node
        Node pred = tail;
        if(pred != null) {
            newNode.prev = pred;
            //����������˵����ǰ�̳߳ɹ���ӣ�
            if(compareAndSetTail(pred, newNode)) {
                pred.next = newNode;
                return newNode;
            }
        }

        //ִ�е�������м��������
        //1.tail == null �����ǿն���
        //2.cas ���õ�ǰnewNode Ϊ tail ʱ ʧ����...�������߳�����һ����...
        enq(newNode);
        return newNode;
    }

    /**
     * ������ӣ�ֻ�гɹ���ŷ���.
     *
     * 1.tail == null �����ǿն���
     * 2.cas ���õ�ǰnewNode Ϊ tail ʱ ʧ����...�������߳�����һ����...
     */
    private void enq(Node node) {
        for(;;) {
            //��һ������������ǿն���
            //==> ��ǰ�߳��ǵ�һ����ռ��ʧ�ܵ��߳�..
            //��ǰ���������̣߳���û�����ù��κ� node,������Ϊ���̵߳ĵ�һ����������Ҫ������ƨ�ɡ�
            //����ǰ���������߳� ����һ�� node ��Ϊhead�ڵ㡣  head�ڵ� �κ�ʱ�򣬶�����ǰռ�������̡߳�
            if(tail == null) {
                //����������˵����ǰ�߳� �� ��ǰ���������߳� ���� head�����ɹ���..
                if(compareAndSetHead(new Node())) {
                    tail = head;
                    //ע�⣺��û��ֱ�ӷ��أ������������...
                }
            } else {
                //˵������ǰ�������Ѿ���node�ˣ�������һ��׷��node�Ĺ��̡�

                //�������أ�
                //1.�ҵ�newNode��ǰ�ýڵ� pred
                //2.����newNode.prev = pred
                //3.CAS ����tail Ϊ newNode
                //4.���� pred.next = newNode

                //ǰ�������������Ѿ��еȴ���node�ˣ���ǰnode ���ǵ�һ����ӵ�node
                Node pred = tail;
                if(pred != null) {
                    node.prev = pred;
                    //����������˵����ǰ�̳߳ɹ���ӣ�
                    if(compareAndSetTail(pred, node)) {
                        pred.next = node;
                        //ע�⣺��ӳɹ�֮��һ��Ҫreturn����
                        return;
                    }
                }
            }
        }
    }




    /**
     * ���Ի�ȡ�������������߳�
     * true -> ��ռ�ɹ�
     * false -> ��ռʧ��
     */
    private boolean tryAcquire(int arg) {

        if(state == 0) {
            //��ǰstate == 0 ʱ���Ƿ����ֱ�������أ�
            //�����ԣ���Ϊ����ģ�����  ��ƽ����������..
            //����һ��!hasQueuedPredecessor() ȡ��֮��ֵΪtrue ��ʾ��ǰ�߳�ǰ��û�еȴ����̡߳�
            //��������compareAndSetState(0, arg)  Ϊʲôʹ��CAS ? ��Ϊlock���������ж��̵߳��õ����..
            //      ������˵����ǰ�߳������ɹ�
            if(!hasQueuedPredecessor() && compareAndSetState(0, arg)) {
                //�����ɹ��ˣ���Ҫ�ɵ�ɶ��
                //1.��Ҫ��exclusiveOwnerThread ����Ϊ ��ǰ����if���е��߳�
                this.exclusiveOwnerThread = Thread.currentThread();
                return true;
            }
            //ʲôʱ���ִ�� else if ������
            //��ǰ���Ǳ�ռ�õ�ʱ������������������..

            //����������Thread.currentThread() == this.exclusiveOwnerThread
            //˵����ǰ�̼߳�Ϊ�����̣߳�����Ҫ����true�ģ�
            //���Ҹ���stateֵ��
        } else if(Thread.currentThread() == this.exclusiveOwnerThread) {
            //��������ڲ���ô�� �����ڵ� �� ֻ�е�ǰ�������̣߳�����Ȩ���޸�state��
            //�����������
            //˵����ǰ�̼߳�Ϊ�����̣߳�����Ҫ����true�ģ�
            int c = getState();
            c = c + arg;
            //Խ���ж�..
            this.state = c;
            return true;
        }

        //ʲôʱ��᷵��false?
        //1.CAS����ʧ��
        //2.state > 0 �� ��ǰ�̲߳���ռ�����̡߳�
        return false;
    }


    /**
     * true -> ��ʾ��ǰ�߳�ǰ���еȴ����߳�
     * false -> ��ǰ�߳�ǰ��û�������ȴ����̡߳�
     *
     * ������
     * lock -> acquire -> tryAcquire  -> hasQueuedPredecessor (ps��stateֵΪ0 ʱ������ǰLock��������״̬..)
     *
     * ʲôʱ�򷵻�false�أ�
     * 1.��ǰ�����ǿ�..
     * 2.��ǰ�߳�Ϊhead.next�ڵ� �߳�.. head.next���κ�ʱ����Ȩ��ȥ��ȡһ��lock
     */
    private boolean hasQueuedPredecessor() {
        Node h = head;
        Node t = tail;
        Node s;

        //����һ��h != t
        //������˵����ǰ�����Ѿ���node��...
        //��������1. h == t == null   2. h == t == head ��һ����ȡ��ʧ�ܵ��̣߳���Ϊ��ǰ���������߳� ���䴴��һ�� head �ڵ㡣


        //��������ǰ������������һ���� ((s = h.next) == null || s.thread != Thread.currentThread())
        //�ų��������
        //����2.1��(s = h.next) == null
        //�����������һ����ȡ��ʧ�ܵ��̣߳���Ϊ �����߳� ���䴴�� head ��Ȼ����������ӣ�  1. cas tail() �ɹ��ˣ�2. pred��head��.next = node;
        //��ʵ����ľ��ǣ��Ѿ���head.next�ڵ��ˣ������߳�������ʱ  ��Ҫ���� true��

        //����2.2��ǰ������,h.next ���ǿա� s.thread != Thread.currentThread()
        //����������˵����ǰ�̣߳��Ͳ���h.next�ڵ��Ӧ���߳�...����true��
        //������������˵����ǰ�̣߳�����h.next�ڵ��Ӧ���̣߳���Ҫ����false����ͷ�̻߳�ȥ�������ˡ�
        return h != t && ((s = h.next) == null || s.thread != Thread.currentThread());
    }




    /**
     * �ͷ���
     */
   
    public void unlock() {
        release(1);
    }

    private void release(int arg) {
        //����������˵���߳��Ѿ���ȫ�ͷ�����
        //��Ҫ�ɵ�ɶ�أ�
        //�����������滹�кü���˯�����߳��أ� �ǲ��� Ӧ�ú���һ���߳��أ�
        if(tryRelease(arg)) {
            Node head = this.head;

            //���֪������û�еȴ��ߣ�   �����ж� head.next == null  ˵��û�еȴ��ߣ� head.next != null ˵���еȴ���..
            if(head.next != null) {
                //��ƽ�������ǻ���head.next�ڵ�
                unparkSuccessor(head);
            }
        }
    }

    private void unparkSuccessor(Node node) {
        Node s = node.next;
        if(s != null && s.thread != null) {
            LockSupport.unpark(s.thread);
        }
    }

    /**
     * ��ȫ�ͷ����ɹ����򷵻�true
     * ����˵����ǰstate > 0 ������false.
     */
    private boolean tryRelease(int arg) {
        int c = getState() - arg;

        if(getExclusiveOwnerThread() != Thread.currentThread()) {
            throw new RuntimeException("fuck you! must getLock!");
        }
        //���ִ�е�������ڲ���ô�� ֻ��һ���߳� ExclusiveOwnerThread ���������

        //����������˵����ǰ�̳߳��е�lock�� �Ѿ���ȫ�ͷ���..
        if(c == 0) {
            //��Ҫ��ʲô�أ�
            //1.ExclusiveOwnerThread ��Ϊnull
            //2.���� state == 0
            this.exclusiveOwnerThread = null;
            this.state = c;
            return true;
        }

        this.state = c;
        return false;
    }


    private void setHead(Node node) {
        this.head = node;
        //Ϊʲô�� ��Ϊ��ǰnode�Ѿ��ǻ�ȡ���ɹ����߳���...
        node.thread = null;
        node.prev = null;
    }

    public int getState() {
        return state;
    }

    public Thread getExclusiveOwnerThread() {
        return exclusiveOwnerThread;
    }

    public Node getHead() {
        return head;
    }

    public Node getTail() {
        return tail;
    }

    private static final Unsafe unsafe;
    private static final long stateOffset;
    private static final long headOffset;
    private static final long tailOffset;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);

            stateOffset = unsafe.objectFieldOffset
                    (MiniReentrantLock.class.getDeclaredField("state"));
            headOffset = unsafe.objectFieldOffset
                    (MiniReentrantLock.class.getDeclaredField("head"));
            tailOffset = unsafe.objectFieldOffset
                    (MiniReentrantLock.class.getDeclaredField("tail"));

        } catch (Exception ex) { throw new Error(ex); }
    }

    private final boolean compareAndSetHead(Node update) {
        return unsafe.compareAndSwapObject(this, headOffset, null, update);
    }

    private final boolean compareAndSetTail(Node expect, Node update) {
        return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }

    protected final boolean compareAndSetState(int expect, int update) {
        // See below for intrinsics setup to support this
        return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
    }
}
