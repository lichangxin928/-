package algorithm.sort;

/*
*  冒泡排序
* */
public class BubbleSort {

    public static void main(String[] args) {
        int[] arr = new int[]{4,2,21,3,5,31,54};
        BubbleSort.Sort(arr);
        for (int num: arr
             ) {
            System.out.println(num);

        }
    }

    public static int[] Sort(int[] nums){

        for (int i = 0; i < nums.length - 1; i++) {
            for (int j = 1; j < nums.length; j++) {
                if(nums[j - 1] > nums[j]){
                    int temp = nums[j - 1];
                    nums[j - 1] = nums[j];
                    nums[j] = temp;
                }
            }
        }


        return nums;
    }
}
