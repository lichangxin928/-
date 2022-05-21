package algorithm.chart;

public class Dijkstra {

    public static final int MAX = Integer.MAX_VALUE;
    public static final int[] path = new int[6];
    public static void main(String[] args) {
        int [][]matrix  = new int[][]{
                { 0,  3,  MAX,  MAX,  6,  5},
                { 1,  0,  1,  MAX,  MAX,  4},
                { MAX,  1,  0,  6,  MAX,  4},
                { MAX,  MAX,  6,  0,  8,  5},
                { 6,  MAX,  MAX,  8,  0,  5},
                { 5,  4,  4,  5,  5,  0}
        };
        int[] res = Dijkstra.Dijkstra(matrix);
        for (int i = 0;i < res.length;i++){
            System.out.println("0 --> " + i + " 的最短距离为：" + res[i]);
        }
        System.out.println("\n");
        printPath();
    }

    public static int[] Dijkstra(int[][] matrix){

        // 用来记录是否访问了
        int[] assists = new int[matrix.length];
        int[] res = new int[matrix.length];

        int m = 0;
        // 初始化 res 数组
        for (int i = 0; i < res.length; i++) {
            res[i] = matrix[0][i];
            assists[i] = 0;
            path[i] = 0;
        }
        assists[0] = 1;
        path[0] = 0;
        int begin = 0;
        // 进行 length - 1 个循环，每次循环找到一个最小的，计算出距离
        for (int i = 1; i < matrix.length; i++) {
            int min = MAX;
            // 找到最小的 min
            for (int j = 0;j < matrix.length;j++){
                // 这里使用 assists 来判断为 false 是为了防止已经找到的最小路径来影响后面对 min 的赋值
                if(assists[j] == 0 && res[j] < min){
                    min = res[j];
                    // 用 m 记录下 j 的下标，表示下一从这个点开始寻找
                    m = j;
                }
            }
            // 找到了最小值后，将其对应的assists 赋值为 true 表示起点到这个点的最短距离找到了
            assists[m] = 1;


            // 新的周期过程
            for(int j = 1;j < matrix.length;j++){
                // 这里的  (!assists[j]) 是起到优化作用
                if((matrix[m][j] != MAX) && (assists[j] == 0)){
                    if(res[j] > (res[m] + matrix[m][j])){
                        res[j] = res[m] + matrix[m][j];
                        path[j] = m;
                    }
                }
            }
        }
        System.out.println();
        for (int i = 0; i < path.length; i++) {
            System.out.print(path[i] + " ");
        }
        System.out.println(" ");
        return res;
    }

    public static void printPath(){
        for (int i = 1; i < 6; i++) {
            System.out.println(" 0 到:" + i);
            printPath(0,i);
            System.out.println("\n");
        }
    }
    public static void printPath(int from,int to){
        if(from == to) return;
        else if(path[to] == 0) System.out.print("0----->" + to);
        else{
            printPath(0,path[to]);
            System.out.print("----->" + to);
        }
    }
}
