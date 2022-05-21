package algorithm.sort;


/*
*
* 直接插入排序
* */
public class InsertSort {

    public static void main(String[] args) {
        int[] arr = new int[]{4,2,21,3,5,31,54};
        InsertSort.Sort(arr);
        for (int num: arr) {
            System.out.println(num);

        }
    }

    public static int[] Sort(int nums[]){

        for (int i = 1; i < nums.length; i++) {
            int count = i;
            for (int j = i - 1; j >= 0; j--) {
                if(nums[count] > nums[j]){
                    int temp = nums[count];
                    nums[count] = nums[j];
                    nums[j] = temp;
                    count--;
                }
            }
        }
        
        
        return  nums;
    }
}
