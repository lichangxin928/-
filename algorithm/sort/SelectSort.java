package algorithm.sort;


/*
*
* 选择排序
* */
public class SelectSort {

    public static void main(String[] args) {
        int[] arr = new int[]{4,2,21,3,5,31,54};
        SelectSort.Sort(arr);
        for (int num: arr) {
            System.out.println(num);

        }
    }

    public static int[] Sort(int nums[]){

        for (int i = 0; i < nums.length - 1; i++) {
            for (int j = i; j < nums.length; j++) {
                if(nums[i] < nums[j]){
                    int temp = nums[j];
                    nums[j] = nums[i];
                    nums[i] = temp;
                }
            }
        }

        return nums;
    }
}
