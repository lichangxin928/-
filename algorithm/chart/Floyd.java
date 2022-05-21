package algorithm.chart;

public class Floyd {
    public static final int MAX = 99999;

    public static void main(String[] args) {
        int [][]matrix  = new int[][]{
                { 0,  3,  MAX,  MAX,  6,  5},
                { 3,  0,  1,  MAX,  MAX,  4},
                { MAX,  1,  0,  6,  MAX,  4},
                { MAX,  MAX,  6,  0,  8,  5},
                { 6,  MAX,  MAX,  8,  0,  5},
                { 5,  4,  4,  5,  5,  0}
        };
        int [][]path = new int[6][6];
        for (int i = 0; i < path.length; i++) {
            for (int j = 0; j < path.length; j++) {
                path[i][j] = 0;
            }
        }
       Floyd(matrix,path);
        for (int i = 0;i < matrix.length;i ++ ){
            for (int j = 0; j < matrix.length; j++) {
                System.out.print(matrix[i][j] + "  ");
            }
            System.out.println("");
        }
        System.out.println("前驱 -----------------");
        for (int i = 0;i < path.length;i ++ ){
            for (int j = 0; j < path.length; j++) {
                System.out.print(path[i][j] + "  ");
            }
            System.out.println("");
        }
        System.out.println("0 到 3");
        System.out.print("0");
        printPath(0,3,path);
    }

    public static void Floyd(int[][]matrix,int[][] path){
        // 保存距离
        int len = 0;
        // k 为中间顶点
        for (int k = 0; k < matrix.length; k++) {
            // i 为开始顶点
            for (int i = 0; i < matrix.length; i++) {
                // j 为结束顶点
                for (int j = 0; j < matrix.length; j++) {
                    len = matrix[i][k] + matrix[k][j];
                    if( len < matrix[i][j]){
                        matrix[i][j] = len;
                        matrix[j][i] = len;
                        path[i][j] = k;
                    }
                }
            }
        }
    }

    public static void printPath(int from,int to,int[][] path){
        if(from == to) System.out.println(from + " to " + to + " 0 ");
        else if(path[from][to] == 0) System.out.print("--->" + to);
        else {
            printPath(from,path[from][to],path);
            printPath(path[from][to],to,path);
        }
    }
}
