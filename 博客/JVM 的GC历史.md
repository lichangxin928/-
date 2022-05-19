# JVM 的GC历史

## 垃圾回收器算法

### 定位垃圾的算法



#### 1. Reference Count（引用计数法）

这个很简单，不过多赘述

#### 2. Root Searching (根可达算法)

GC roots：JVM stack，native method stack、runtime constant pool、static references in method area，Clazz

线程栈变量、静态变量、常量池、JNI指针



### 垃圾清除算法

1. Mark-Sweep（标记清除）

​		打上标记直接清除。碎片化严重

1. Copying（拷贝）

   只能有一半的空间，将找到的不是垃圾拷贝到另一边

2. Mark-Compact（标记压缩）

   回收的时候就将其整理好，效率最低



各种各样的垃圾清楚算法有利有弊，不同的垃圾回收器使用的方不尽相同，但是每种都有其特点



## 十种垃圾回收器



### GC 的演化

随着内存大小的不断增长而演进

- 几兆-几十兆 ：Serial
- 几十兆-上百兆1G：parallel Scavenge + parallel Old（JDK 1.8 默认）

​		

- 几十G Concurrent GC：CMS + ParNew、G1、ZGC 、Shenandoah（GC 线程和垃圾回收线程同时进行）



**堆内逻辑分区**

所有的垃圾回收器都将内存分为了新生代和老年代



new ： old  =  1：3

new    中	eden（伊甸）：survivor：survivor  =  8：1：1



第一次执行时扫描 eden 中的对象，将存活的对象拷贝到survivor区中，将eden中进行全部清除，下一次就对eden 和存储对象的survivor区进行扫描，将存活的对象放到另一个survivor区中，对eden 和 survivor全部清除，以此类推。如果survivor装不下了，就往 old 中扔。



### 1. Serial（年轻代）

a stop-the-world,copying which uses a single GC thread

单线程 STW 复制算法 年轻代垃圾回收



### 2. Serial Old（老年代）

a stop-the-world mark-sweep-compact collector that uses a single GC thread

单线程 STW 标记清除和标记压缩相结合 老年代垃圾回收



### 3. Parallel Scavenge（新生代）

多线程 STW 复制算法 新生代垃圾回收

### 4. Parallel Old（老年代）

多线程 STW 标记清除 +  标记压缩 老年代垃圾回收



### 5. CMS （老年代）

Concurrent Mark Sweep 

三色标记算法

四个过程：初始标记 -> 并发标记 -> 重新标记 -> 并发清理

​		1、找到 root 标记 此时 STW

​		2、并发标记，不会产生 STW ，但是会存在标错的情况（三色标记算法）

​					黑色：自己已经标记，并且fields都标记完成

​					灰色：自己标记完成，还没来得及标记fields

​					白色：还  没有遍历到的节点

​		3、此时对错误的重新进行修正，会产生 STW

​		4、并发清理

### 6. ParNew（年轻代）

配合 CMS 来进行使用，Parallel Scavenger 的加强版



### 7. Epsilon（什么都不干垃圾回收器）

### 8. G1（放弃了分代模型）

物理上不分代，逻辑上分代

### 9. ZGC（分页算法）

### 10.Shenandoah



## 调优

1. 根据需求进行jvm规划和预调优
2. 优化运行jvm运行环境
3. 解决 jvm 运行过程中出现的各种各样的问题 OOM



#### 常用参数

jps 显示当前运行的java 程序

jinfo  port 显示当前运行程序的详细信息

jstat  -gc  port   java的跟踪信息

jstack port   打印线程栈信息

top linux 显示进程 占用 cpu情况

top -Hp port 显示进程中的线程占用 cpu 的情况

jmap - histo port 不同的类占用内存的情况（STW）

jmap -dump:format=b,file=filename.hprof

arthas







