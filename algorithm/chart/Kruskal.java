package algorithm.chart;
import java.util.Arrays;
import java.util.Scanner;

/**
 * 实现克鲁斯卡尔算法的难点在于“如何判断一个新边是否会和已选择的边构成环路”，
 * 这里教大家一种判断的方法：初始状态下，为连通网中的各个顶点配置不同的标记。
 * 对于一个新边，如果它两端顶点的标记不同，就不会构成环路，可以组成最小生成树。
 * 一旦新边被选择，需要将它的两个顶点以及和它直接相连的所有已选边两端的顶点改为相同的标记；
 * 反之，如果新边两端顶点的标记相同，就表示会构成环路。
 */
public class Kruskal {

    static int N = 9; // 图中边的数量
    static int P = 6; // 图中顶点的数量
    // 构建表示路径的类
    public static class edge implements Comparable<edge>{
        //每个路径都有 2 个顶点和 1 个权值
        int initial;
        int end;
        int weight;
        public edge(int initial, int end, int weight) {
            this.initial = initial;
            this.end = end;
            this.weight = weight;
        }
        //对每个 edge 对象根据权值做升序排序
        @Override
        public int compareTo(edge o) {
            return this.weight - o.weight;
        }
    }

    public static void kruskal_MinTree(edge[] edges,edge [] minTree) {
        int []assists = new int[P];
        //每个顶点配置一个不同的标记值
        for (int i = 0; i < P; i++) {
            assists[i] = i;
        }
        //根据权值，对所有边进行升序排序
        Arrays.sort(edges);
        //遍历所有的边
        int num = 0;
        for (int i = 0; i < N; i++) {
            //找到当前边的两个顶点在 assists 数组中的位置下标
            int initial = edges[i].initial - 1;
            int end = edges[i].end - 1;
            //如果顶点位置存在且顶点的标记不同，说明不在一个集合中，不会产生回路
            if (assists[initial] != assists[end]) {
                //记录该边，作为最小生成树的组成部分
                minTree[num] = edges[i];
                //计数+1
                num++;
                int elem = assists[end];
                //将新加入生成树的顶点标记全部更改为一样的
                for (int k = 0; k < P; k++) {
                    if (assists[k] == elem) {
                        assists[k] = assists[initial];
                    }
                }
                //如果选择的边的数量和顶点数相差1，证明最小生成树已经形成，退出循环
                if (num == P - 1) {
                    break;
                }
            }
        }
    }
    public static void display(edge [] minTree) {
        System.out.println("最小生成树为：");
        int cost = 0;
        for (int i = 0; i < P - 1; i++) {
            System.out.println(minTree[i].initial+" - "+ minTree[i].end+" 权值为："+minTree[i].weight);
            cost += minTree[i].weight;
        }
        System.out.print("总权值为:"+cost);
    }

    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        // 保存了图的信息
        edge[] edges = new edge[N];
        // 最小树存储在这里
        edge[] minTree = new edge[P-1];
        System.out.println("请输入图中各个边的信息：");
        for(int i=0;i<N;i++) {
            int initial = scn.nextInt(), end = scn.nextInt(), weight = scn.nextInt();
            edges[i] = new edge(initial,end,weight);
        }
        kruskal_MinTree(edges,minTree);
        display(minTree);
    }
}
