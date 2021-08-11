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
//            //�õ��Ķ���null
//            System.out.println("��ǰweakReference �Ķ�������"+weakReference.get());
//        }

        ReferenceQueue referenceQueue = new ReferenceQueue();


        byte[] buffer = new byte[1024 * 1024*10];
        WeakReference weakReference = new WeakReference<>(buffer, referenceQueue);
        Reference reference = referenceQueue.poll();
        System.out.println("GCִ��֮ǰqueue���Ƿ���������" + reference);
        System.out.println("GCִ��֮ǰref���õĶ���" + weakReference.get());
        buffer = null;
        System.gc();

        Thread.sleep(1000);

        reference = referenceQueue.poll();
        System.out.println("GCִ��֮��queue���Ƿ���������" + (reference == weakReference));
        System.out.println("GCִ��֮��weakReference �Ķ�������" + weakReference.get());


    }
}
