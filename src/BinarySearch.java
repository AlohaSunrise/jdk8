import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;

public class BinarySearch {

    public static void main(String[] args) {
        
        int i =1;
        Integer a = new Integer(1);
        a.equals(i);

        Random random = new Random();
        System.out.println(random.nextInt(3));
        System.out.println(random.nextInt(3));
        System.out.println(random.nextInt(3));
        System.out.println(random.nextInt(3));
        System.out.println(random.nextInt(3));
        System.out.println(random.nextInt(3));
        System.out.println(random.nextInt(3));
        System.out.println(random.nextInt(3));
        
        
        
        
//        binarySearch(new int[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14},5);
//        System.out.println(binarySearch(new int[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14},13));
    }


    public static final int binarySearch(int[] array, int data) {
       
        int min = 0;
        int high = array.length-1;
        
        while (min<=high){
            int mid = min+ (high-min)/2;
            if(array[mid] < data){
                min = mid+1;
            }else if(array[mid] == data){
                return mid;
            }else {
                high = mid-1;
            }
            
        }
        return -1;
        
        
    }

}
