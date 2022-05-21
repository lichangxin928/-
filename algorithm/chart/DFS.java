package algorithm.chart;


/**
 * Depth-First-Search
 */
public class DFS {

    private static int assists[] = new int[6];
    private static  int[][] G;

    public static void main(String[] args) {
        for (int i = 0;i< assists.length ;i++){
            assists[i] = -1;
        }
        G  = new int[][]{
                {-1,  3, -1, -1,  6,  5},
                { 1, -1,  1, -1, -1,  4},
                {-1,  1, -1,  6, -1,  4},
                {-1, -1,  6, -1,  8,  5},
                { 6, -1, -1,  8, -1,  5},
                { 5,  4,  4,  5,  5, -1}
        };
        assists[0] = 1;
        DFS(0);

    }
    public static void DFS(int index){
        boolean flag = true;
        for(int i = 0;i < G.length ;i++){
            if (G[i][index] != -1 && assists[i] == -1){
                assists[i] = 1;
                // 递归调用
                DFS(i);
                System.out.println(index);
                flag = false;
            }

        }
        // 表示该节点已经到最底层了，需要进行回溯了
        if(flag){
            System.out.println(index);
        }
    }
}
