package ���ݽṹ���㷨.����;



//ð������
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


    // ð������a��ʾ���飬n��ʾ�����С
    public void bubbleSort(int[] a, int n) {
        if (n <= 1) return;

        for(int i =0;i<n;i++){
            // ��ǰ�˳�ð��ѭ���ı�־λ
            boolean flag = false;
            for(int j=0;j<n-i-1;j++){
                if(a[j]>a[j+1]){
                    int tmp = a[j];
                    a[j] = a[j+1];
                    a[j+1] = tmp;
                    flag = true;
                }
            }
            if (!flag) break; // û�����ݽ�������ǰ�˳�
        }
         
    }



    
    
    
    
    
}
