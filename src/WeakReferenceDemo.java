import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class WeakReferenceDemo {

    public static void main(String[] args) throws InterruptedException {
//        
//        List<WeakReference> weakReferenceList = new ArrayList<>();
//        for (int i = 0; i <100 ; i++) {
//            byte[]buffer = new byte[1024*1024];
//            WeakReference weakReference = new WeakReference<>(buffer);
//            weakReferenceList.add(weakReference);
//        }
//        System.gc();
//        
//        Thread.sleep(1000);
//
//        for (WeakReference weakReference : weakReferenceList) {
//            //拿到的都是null
//            System.out.println("当前weakReference 的对象引用"+weakReference.get());
//        }

        ReferenceQueue referenceQueue = new ReferenceQueue();


        byte[] buffer = new byte[1024 * 1024*10];
        WeakReference weakReference = new WeakReference<>(buffer, referenceQueue);
        Reference reference = referenceQueue.poll();
        System.out.println("GC执行之前queue里是否有数据呢" + reference);
        System.out.println("GC执行之前ref引用的对象" + weakReference.get());
        buffer = null;
        System.gc();

        Thread.sleep(1000);

        reference = referenceQueue.poll();
        System.out.println("GC执行之后queue里是否有数据呢" + (reference == weakReference));
        System.out.println("GC执行之后weakReference 的对象引用" + weakReference.get());


    }
}
