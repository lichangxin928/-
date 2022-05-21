package algorithm.chart;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Breadth-First-Search
 *
 * 1、把根节点压入栈中。
 * 2、每次从栈中弹出一个元素，搜索所有在它下一级的元素，把这些元素压入栈中。并把这个元素记为它下一级元素的前驱。
 * 3、找到所要找的元素时结束程序。
 * 4、如果遍历整个树还没有找到，结束程序。
 */
public class BFS {

    public static void main(String[] args) {

        int[][] G =
                {
                        {-1,3,-1,-1,6,5},
                        {1,-1,1,-1,-1,4},
                        {-1,1,-1,6,-1,4},
                        {-1,-1,6,-1,8,5},
                        {6,-1,-1,8,-1,2},
                        {5,4,4,5,2,-1}
                };
        BFS.BFS(G);
    }

    public static void BFS(int G[][]){

        Queue<Integer> queue = new ArrayDeque();
        int []assists = new int[G.length];
        // 防止重复读取节点
        for (int i = 0; i < assists.length; i++){
            assists[i] = -1;
        }
        // 将第一个节点加入
        queue.add(0);
        assists[0] = 1;
        // 只要队列不为空
        while (!queue.isEmpty()){
            int node = queue.poll();
            System.out.println(node);
            for (int i = 0;i < G.length;i++){
                if(G[node][i] != -1 && assists[i] == -1){
                    queue.add(i);
                    assists[i] = 1;
                 }
            }
        }
    }
}
