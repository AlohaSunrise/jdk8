package 数据结构和算法.排序;



//冒泡排序
public class bubbleSort {

    public static void main(String[] args) {
//        System.out.println(123);
//
//
//        ArrayList a = new ArrayList();
//        a.add(1);
        
        
        for(int i=0;i<5;++i){
            System.out.println(i);
        }
        
    }


    // 冒泡排序，a表示数组，n表示数组大小
    public void bubbleSort(int[] a, int n) {
        if (n <= 1) return;

        for(int i =0;i<n;i++){
            // 提前退出冒泡循环的标志位
            boolean flag = false;
            for(int j=0;j<n-i-1;j++){
                if(a[j]>a[j+1]){
                    int tmp = a[j];
                    a[j] = a[j+1];
                    a[j+1] = tmp;
                    flag = true;
                }
            }
            if (!flag) break; // 没有数据交换，提前退出
        }
         
    }



    
    
    
    
    
}
