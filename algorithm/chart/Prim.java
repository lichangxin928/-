package algorithm.chart;

import java.util.ArrayList;

/**
 *
    1).输入：一个加权连通图，其中顶点集合为V，边集合为E；
    2).初始化：Vnew = {x}，其中x为集合V中的任一节点（起始点），Enew = {},为空；
    3).重复下列操作，直到Vnew = V：
        a.在集合E中选取权值最小的边<u, v>，其中u为集合Vnew中的元素，而v不在Vnew集合当中，并且v∈V（如果存在有多条满足前述条件即具有相同权值的边，则可任意选取其中之一）；
        b.将v加入集合Vnew中，将<u, v>边加入集合Enew中；
    4).输出：使用集合Vnew和Enew来描述所得到的最小生成树。
 */
public class Prim {
    public static void main(String[] args) {
        int[][] G =
                {
                        {-1,3,-1,-1,6,5},
                        {1,-1,1,-1,-1,4},
                        {-1,1,-1,6,-1,4},
                        {-1,-1,6,-1,8,5},
                        {6,-1,-1,8,-1,1},
                        {5,4,4,5,1,-1}
                };
        Prim.Prim(G);
    }

    public static void Prim(int [][] G) {
        /* 创建一个一维数组来记录每个顶点的遍历情况，如果该顶点已经遍历了就设置为 1 否则为 -1 */
        int[] flag = new int[G.length];
        for (int i = 0; i < flag.length; i++) {
            flag[i] = -1;
        }
        ArrayList<Integer> array = new ArrayList(G.length);  // 这个用来保存节点的访问顺序
        /* 开始设置一个起点 */
        flag[0] = 1;
        array.add(0);

        // 节点的访问顺序小于 节点总数就进行循环
        int count = 0;
        while (array.size() < G.length) {
            int minDistance = Integer.MAX_VALUE;
            int from = -1;
            int index = -1;
            for (int i = 0; i < array.size(); i++) { // 这里需要注意的是，访问了几个节点，就从这几个节点开始找最小的边
                int v1 = array.get(i);  // 从这个节点开始找
                for (int j = 0; j < G.length; j++) {    // 遍历这个节点所有的边
                    if (flag[j] != 1) { // 如果不等于 1 就表示这个节点还没有被访问到
                        if(G[v1][j] < minDistance && G[v1][j] != -1){
                            minDistance = G[v1][j];
                            /* 这里是用来保存此次循环中最小的边 */
                            from = v1;
                            index = j;
                        }
                    }
                }
            }
            count += G[from][index];
            flag[index] = 1;
            array.add(index);
        }
        System.out.println("使用MyPrim算法，对于给定图中的顶点访问顺序为：");
        System.out.println(array);
        System.out.println("权重总和为：" + count);
    }


}
