package algorithm.sort;

/*
*
* 快速排序
* */
public class QuickSort {

    public static void main(String[] args) {
        int[] arr = new int[]{4,2,21,3,5,31,54};
        QuickSort.Sort(arr,0,arr.length - 1);
        for (int num: arr) {
            System.out.println(num);
        }
    }

    public static void Sort(int[] arr, int begin, int end) {

        if (begin > end)
            return;
        int tmp = arr[begin];
        int i = begin;
        int j = end;
        while (i != j) {
            while (arr[j] >= tmp && j > i)
                j--;
            while (arr[i] <= tmp && j > i)
                i++;
            if (j > i) {
                int t = arr[i];
                arr[i] = arr[j];
                arr[j] = t;
            }
        }
        arr[begin] = arr[i];
        arr[i] = tmp;
        Sort(arr, begin, i - 1);
        Sort(arr, i + 1, end);
    }

}
