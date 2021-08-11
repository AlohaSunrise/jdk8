package 数据结构和算法.排序;

public class selectSort {
    
    public static void selectSort(int [] arr,int n){
        for (int i = 0; i < n - 1; i++) {
            int index = i;
            
            // 找出最小值得元素下标
            for (int j = i + 1; j < n; j++) {
                if (arr[j] < arr[index]) {
                    index = j;
                }
            }
            int tmp = arr[index];
            arr[index] = arr[i];
            arr[i] = tmp;
        }

    }

}
