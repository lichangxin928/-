package algorithm.sort;


/*
*
* 希尔排序
* */
public class ShellSort {


    public static void main(String[] args) {
        int[] arr = new int[]{4,2,21,3,5,31,54};
        ShellSort.Sort(arr);
        for (int num: arr) {
            System.out.println(num);

        }
    }

    public static int[] Sort(int[] array){

        // 跳步数
        int gap = array.length;
        while (true) {
            gap /= 2;   //增量每次减半
            // gap 数量为分组的个数
            for (int i = 0; i < gap; i++) {
                for (int j = i + gap; j < array.length; j += gap) {//这个循环里其实就是一个插入排序
                    int k = j - gap;
                    while (k >= 0 && array[k] > array[k+gap]) {
                        int temp = array[k];
                        array[k] = array[k+gap];
                        array[k + gap] = temp;
                        k -= gap;
                    }
                }
            }
            if (gap == 1)
                break;
        }

        return array;
    }
}
