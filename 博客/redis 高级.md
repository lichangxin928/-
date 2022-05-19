## Redis 持久化

### 1. RDB

1. save：

   执行`save`命令会手动触发RDB持久化，但是`save`命令会阻塞Redis服务，直到RDB持久化完成。当Redis服务储存大量数据时，会造成较长时间的阻塞，不建议使用。

2. bgsave：

   执行`bgsave`命令也会手动触发RDB持久化，和`save`命令不同是：Redis服务一般不会阻塞。Redis进程会执行fork操作创建子进程，RDB持久化由子进程负责，不会阻塞Redis服务进程。Redis服务的阻塞只发生在fork阶段，一般情况时间很短。



**RDB 执行流程**

![image-20220509151319477](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220509151319477.png)



**优点**：RDB文件是一个紧凑的二进制压缩文件，是Redis在某个时间点的全部数据快照。所以使用RDB恢复数据的速度远远比AOF的快，非常适合备份、全量复制、灾难恢复等场景。

**缺点**：每次进行`bgsave`操作都要执行fork操作创建子经常，属于重量级操作，频繁执行成本过高，所以无法做到实时持久化，或者秒级持久化。另外，由于Redis版本的不断迭代，存在不同格式的RDB版本，有可能出现低版本的RDB格式无法兼容高版本RDB文件的问题。



**RDB 实现细节**

![image-20220509153619995](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220509153619995.png)

### 2. AOF

**AOF流程**：

![image-20220509152012605](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220509152012605.png)

1. 命令追加（append）：所有写命令都会被追加到AOF缓存区（aof_buf）中。
2. 文件同步（sync）：根据不同策略将AOF缓存区同步到AOF文件中。
3. 文件重写（rewrite）：定期对AOF文件进行重写，以达到压缩的目的。
4. 数据加载（load）：当需要恢复数据时，重新执行AOF文件中的命令。



**AOF 持久化策略**：这个持久化策略和MySQL中的 redo 日志的持久化策略很相似

1. always：每次写入缓存区都要同步到AOF文件中，硬盘的操作比较慢，限制了Redis高并发，不建议配置。
2. no：每次写入缓存区后不进行同步，同步到AOF文件的操作由操作系统负责，每次同步AOF文件的周期不可控，而且增大了每次同步的硬盘的数据量。
3. eversec：每次写入缓存区后，由专门的线程每秒钟同步一次，做到了兼顾性能和数据安全。是建议的同步策略，也是默认的策略。



**AOF 的触发**：

1. 手动触发：使用bgrewriteaof命令。
2. 自动触发：根据```auto-aof-rewrite-min-size和auto-aof-rewrite-percentage```配置确定自动触发的时机。```auto-aof-rewrite-min-size```表示运行AOF重写时文件大小的最小值，默认为64MB；```auto-aof-rewrite-percentage```表示当前AOF文件大小和上一次重写后AOF文件大小的比值的最小值，默认为100。只用前两者同时超过时才会自动触发文件重写。



### 3. AOF 和 RDB 对比

|                | RDB                                  | AOF                                                 |
| -------------- | ------------------------------------ | --------------------------------------------------- |
| 持久化方式     | 定时对整个内存做快照                 | 记录每一次写命令                                    |
| 数据完整性     | 不完整，两次备份时间内宕机回丢失数据 | 相对完整，取决于刷盘策略                            |
| 文件大小       | 以快照的形式，会有压缩，文件体积小   | 记录命令，体积大                                    |
| 宕机恢复时间   | 很快                                 | 较慢                                                |
| 数据恢复优先级 | 比AOF 低                             | 高                                                  |
| 系统资源占用   | 高，大量CPU和内存消耗                | 低，主要是磁盘的IO资源，但AOF 重写会占用大量CPU资源 |
| 使用场景       | 可以容忍数分钟的数据丢失             | 对数据安全有较高要求                                |



## Redis 主从

单节点Redis 的并发能力是有上限的，要进一步提供Redis的并发能力，就需要搭建主从集群

### 集群结构

我们搭建的主从集群结构如图：

![image-20210630111505799](C:\Users\24314\Desktop\讲义\博客\assets\image-20210630111505799.png)

共包含三个节点，一个主节点，两个从节点。

这里我们会在同一台虚拟机中开启3个redis实例，模拟主从集群，信息如下：

|       IP        | PORT |  角色  |
| :-------------: | :--: | :----: |
| 192.168.150.101 | 7001 | master |
| 192.168.150.101 | 7002 | slave  |
| 192.168.150.101 | 7003 | slave  |

### 准备实例和配置

要在同一台虚拟机开启3个实例，必须准备三份不同的配置文件和目录，配置文件所在目录也就是工作目录。

1）创建目录

我们创建三个文件夹，名字分别叫7001、7002、7003：

```sh
# 进入/tmp目录
cd /tmp
# 创建目录
mkdir 7001 7002 7003
```

如图：

![image-20210630113929868](C:\Users\24314\Desktop\讲义\博客\assets\image-20210630113929868.png)

2）恢复原始配置

修改redis-6.2.4/redis.conf文件，将其中的持久化模式改为默认的RDB模式，AOF保持关闭状态。

```properties
# 开启RDB
# save ""
save 3600 1
save 300 100
save 60 10000

# 关闭AOF
appendonly no
```



3）拷贝配置文件到每个实例目录

然后将redis-6.2.4/redis.conf文件拷贝到三个目录中（在/tmp目录执行下列命令）：

```sh
# 方式一：逐个拷贝
cp redis-6.2.4/redis.conf 7001
cp redis-6.2.4/redis.conf 7002
cp redis-6.2.4/redis.conf 7003

# 方式二：管道组合命令，一键拷贝
echo 7001 7002 7003 | xargs -t -n 1 cp redis-6.2.4/redis.conf
```



4）修改每个实例的端口、工作目录

修改每个文件夹内的配置文件，将端口分别修改为7001、7002、7003，将rdb文件保存位置都修改为自己所在目录（在/tmp目录执行下列命令）：

```sh
sed -i -e 's/6379/7001/g' -e 's/dir .\//dir \/tmp\/7001\//g' 7001/redis.conf
sed -i -e 's/6379/7002/g' -e 's/dir .\//dir \/tmp\/7002\//g' 7002/redis.conf
sed -i -e 's/6379/7003/g' -e 's/dir .\//dir \/tmp\/7003\//g' 7003/redis.conf
```



5）修改每个实例的声明IP

虚拟机本身有多个IP，为了避免将来混乱，我们需要在redis.conf文件中指定每一个实例的绑定ip信息，格式如下：

```properties
# redis实例的声明 IP
replica-announce-ip 192.168.150.101
```



每个目录都要改，我们一键完成修改（在/tmp目录执行下列命令）：

```sh
# 逐一执行
sed -i '1a replica-announce-ip 192.168.150.101' 7001/redis.conf
sed -i '1a replica-announce-ip 192.168.150.101' 7002/redis.conf
sed -i '1a replica-announce-ip 192.168.150.101' 7003/redis.conf

# 或者一键修改
printf '%s\n' 7001 7002 7003 | xargs -I{} -t sed -i '1a replica-announce-ip 192.168.150.101' {}/redis.conf
```



### 启动

为了方便查看日志，我们打开3个ssh窗口，分别启动3个redis实例，启动命令：

```sh
# 第1个
redis-server 7001/redis.conf
# 第2个
redis-server 7002/redis.conf
# 第3个
redis-server 7003/redis.conf
```



启动后：

![image-20210630183914491](C:\Users\24314\Desktop\讲义\博客\assets\image-20210630183914491.png)





如果要一键停止，可以运行下面命令：

```sh
printf '%s\n' 7001 7002 7003 | xargs -I{} -t redis-cli -p {} shutdown
```



### 开启主从关系

现在三个实例还没有任何关系，要配置主从可以使用replicaof 或者slaveof（5.0以前）命令。

有临时和永久两种模式：

- 修改配置文件（永久生效）

  - 在redis.conf中添加一行配置：```slaveof <masterip> <masterport>```

- 使用redis-cli客户端连接到redis服务，执行slaveof命令（重启后失效）：

  ```sh
  slaveof <masterip> <masterport>
  ```



<strong><font color='red'>注意</font></strong>：在5.0以后新增命令replicaof，与salveof效果一致。



这里我们为了演示方便，使用方式二。

通过redis-cli命令连接7002，执行下面命令：

```sh
# 连接 7002
redis-cli -p 7002
# 执行slaveof
slaveof 192.168.150.101 7001
```



通过redis-cli命令连接7003，执行下面命令：

```sh
# 连接 7003
redis-cli -p 7003
# 执行slaveof
slaveof 192.168.150.101 7001
```



然后连接 7001节点，查看集群状态：

```sh
# 连接 7001
redis-cli -p 7001
# 查看状态
info replication
```

结果：

![image-20210630201258802](C:\Users\24314\Desktop\讲义\博客\assets\image-20210630201258802.png)



### 测试

执行下列操作以测试：

- 利用redis-cli连接7001，执行```set num 123```

- 利用redis-cli连接7002，执行```get num```，再执行```set num 666```

- 利用redis-cli连接7003，执行```get num```，再执行```set num 888```



可以发现，只有在7001这个master节点上可以执行写操作，7002和7003这两个slave节点只能执行读操作。



### 数据同步原理

1. 主从第一次同步是全量同步

![image-20220509192009823](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220509192009823.png)

**两个概念**：

- Replication id：简称replid，是数据集的标记，id一致则说明是同一个数据集，每一个master 都有一个唯一的replid，slave 则会继承master节点的replid
- offset：偏移量，随着记录在repl_baklog 中的数据增加而逐渐增大。slave 完成同步时会记录当前同步的offset。如果小于master 中的offset 表明需要更新

**全量同步流程**：

1. slave 节点请求增量同步
2. master节点判断replid，发现不一致，拒绝增量同步
3. master将完整内存数据生成RDB，发送RDB到slave
4. slave清空本地数据，加载master的RDB
5. master将RDB期间的命令记录在repl_bakiog，并持续将log 中的命令发送给slave中去

**增量同步**：

判断replid 是否相同，发送offset后的数据。如果超过了repl_bakiog的存储上限，会做一次全量同步，



## 搭建哨兵集群

### 集群结构

这里我们搭建一个三节点形成的Sentinel集群，来监管之前的Redis主从集群。如图：

![image-20210701215227018](C:\Users\24314\Desktop\讲义\博客\assets\image-20210701215227018.png)



三个sentinel实例信息如下：

| 节点 |       IP        | PORT  |
| ---- | :-------------: | :---: |
| s1   | 192.168.150.101 | 27001 |
| s2   | 192.168.150.101 | 27002 |
| s3   | 192.168.150.101 | 27003 |

### 准备实例和配置

要在同一台虚拟机开启3个实例，必须准备三份不同的配置文件和目录，配置文件所在目录也就是工作目录。

我们创建三个文件夹，名字分别叫s1、s2、s3：

```sh
# 进入/tmp目录
cd /tmp
# 创建目录
mkdir s1 s2 s3
```

如图：

![image-20210701215534714](C:\Users\24314\Desktop\讲义\博客\assets\image-20210701215534714.png)

然后我们在s1目录创建一个sentinel.conf文件，添加下面的内容：

```ini
port 27001
sentinel announce-ip 192.168.150.101
sentinel monitor mymaster 192.168.150.101 7001 2
sentinel down-after-milliseconds mymaster 5000
sentinel failover-timeout mymaster 60000
dir "/tmp/s1"
```

解读：

- `port 27001`：是当前sentinel实例的端口
- `sentinel monitor mymaster 192.168.150.101 7001 2`：指定主节点信息
  - `mymaster`：主节点名称，自定义，任意写
  - `192.168.150.101 7001`：主节点的ip和端口
  - `2`：选举master时的quorum值



然后将s1/sentinel.conf文件拷贝到s2、s3两个目录中（在/tmp目录执行下列命令）：

```sh
# 方式一：逐个拷贝
cp s1/sentinel.conf s2
cp s1/sentinel.conf s3
# 方式二：管道组合命令，一键拷贝
echo s2 s3 | xargs -t -n 1 cp s1/sentinel.conf
```



修改s2、s3两个文件夹内的配置文件，将端口分别修改为27002、27003：

```sh
sed -i -e 's/27001/27002/g' -e 's/s1/s2/g' s2/sentinel.conf
sed -i -e 's/27001/27003/g' -e 's/s1/s3/g' s3/sentinel.conf
```

### 启动

为了方便查看日志，我们打开3个ssh窗口，分别启动3个redis实例，启动命令：

```sh
# 第1个
redis-sentinel s1/sentinel.conf
# 第2个
redis-sentinel s2/sentinel.conf
# 第3个
redis-sentinel s3/sentinel.conf
```



启动后：

![image-20210701220714104](C:\Users\24314\Desktop\讲义\博客\assets\image-20210701220714104.png)

### 测试

尝试让master节点7001宕机，查看sentinel日志：

![image-20210701222857997](C:\Users\24314\Desktop\讲义\博客\assets\image-20210701222857997.png)

查看7003的日志：

![image-20210701223025709](C:\Users\24314\Desktop\讲义\博客\assets\image-20210701223025709.png)

查看7002的日志：

![image-20210701223131264](C:\Users\24314\Desktop\讲义\博客\assets\image-20210701223131264.png)



### 哨兵的作用

![image-20220509194015071](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220509194015071.png)

**Sentinel基于心跳机制检测服务状态，每隔一秒向集群的每个实例发送ping命令**

- 主观下线：如果sentinel节点发现某个实例未在规定时间内响应，则认为该实例主观下线
- 客观下线：如果超过指定数量的sentinel都认为该实例主观下线，则该实例客观下线。quorum值最好超过Sentinel实例数量的一半



**推举新的master**：

1. 首先排除数据过旧的节点。（与master 节点断开时间长短来进行判断）
2. slave-priority值，越小优先级越高
3. 优先级一样，判断offset值
4. 最后判断运行id大小

**故障转移**：

1. 给备选的slave接地那发送 slaveof no one 命令
2. 其它slave 发送命令，成为新的主节点的从节点
3. 将故障节点标记为slave，写在配置文件中





### SpringBoot 访问哨兵集群

1. 在pom 文件中引入redis的start依据

```xml
<dependency>
	<groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

2. 在配置文件 application.yml 中指定sentinel相关信息

```yaml
spring:
  redis:
    sentinel:
      master:	# 指定 master 名称
      nodes:	# 指定 sentinel 集群信息
```

3. 配置主从读写分离

```java
@Bean
public LettuceClientConfigurationBuilderCustomizer configurationBuilderCustomizer(){
    return configBuilder -> configBuilder.readFrom(ReadFrom.REPLICA_PREFERRED);
}
```



## 搭建分片集群

主从和哨兵能够解决高可用、高并发读的问题。但是依然有两个问题没有解决：

- 海量数据存储问题
- 高并发写问题

使用分片集群能够解决上述问题：

- 集群中有多个master，每个master保存不同数据
- 每个master都可以有多个slave节点
- master之间通过ping检测彼此健康状态
- 客户端请求可以范围集群任意节点，最终都会被转发到任意节点

### 集群结构

分片集群需要的节点数量较多，这里我们搭建一个最小的分片集群，包含3个master节点，每个master包含一个slave节点，结构如下：

![image-20210702164116027](C:\Users\24314\Desktop\讲义\博客\assets\image-20210702164116027.png)



这里我们会在同一台虚拟机中开启6个redis实例，模拟分片集群，信息如下：

|       IP        | PORT |  角色  |
| :-------------: | :--: | :----: |
| 192.168.150.101 | 7001 | master |
| 192.168.150.101 | 7002 | master |
| 192.168.150.101 | 7003 | master |
| 192.168.150.101 | 8001 | slave  |
| 192.168.150.101 | 8002 | slave  |
| 192.168.150.101 | 8003 | slave  |

### 准备实例和配置

删除之前的7001、7002、7003这几个目录，重新创建出7001、7002、7003、8001、8002、8003目录：

```sh
# 进入/tmp目录
cd /tmp
# 删除旧的，避免配置干扰
rm -rf 7001 7002 7003
# 创建目录
mkdir 7001 7002 7003 8001 8002 8003
```



在/tmp下准备一个新的redis.conf文件，内容如下：

```ini
port 6379
# 开启集群功能
cluster-enabled yes
# 集群的配置文件名称，不需要我们创建，由redis自己维护
cluster-config-file /tmp/6379/nodes.conf
# 节点心跳失败的超时时间
cluster-node-timeout 5000
# 持久化文件存放目录
dir /tmp/6379
# 绑定地址
bind 0.0.0.0
# 让redis后台运行
daemonize yes
# 注册的实例ip
replica-announce-ip 192.168.150.101
# 保护模式
protected-mode no
# 数据库数量
databases 1
# 日志
logfile /tmp/6379/run.log
```

将这个文件拷贝到每个目录下：

```sh
# 进入/tmp目录
cd /tmp
# 执行拷贝
echo 7001 7002 7003 8001 8002 8003 | xargs -t -n 1 cp redis.conf
```



修改每个目录下的redis.conf，将其中的6379修改为与所在目录一致：

```sh
# 进入/tmp目录
cd /tmp
# 修改配置文件
printf '%s\n' 7001 7002 7003 8001 8002 8003 | xargs -I{} -t sed -i 's/6379/{}/g' {}/redis.conf
```

### 启动

因为已经配置了后台启动模式，所以可以直接启动服务：

```sh
# 进入/tmp目录
cd /tmp
# 一键启动所有服务
printf '%s\n' 7001 7002 7003 8001 8002 8003 | xargs -I{} -t redis-server {}/redis.conf
```

通过ps查看状态：

```sh
ps -ef | grep redis
```

发现服务都已经正常启动：

![image-20210702174255799](C:\Users\24314\Desktop\讲义\博客\assets\image-20210702174255799.png)



如果要关闭所有进程，可以执行命令：

```sh
ps -ef | grep redis | awk '{print $2}' | xargs kill
```

或者（推荐这种方式）：

```sh
printf '%s\n' 7001 7002 7003 8001 8002 8003 | xargs -I{} -t redis-cli -p {} shutdown
```

### 创建集群

虽然服务启动了，但是目前每个服务之间都是独立的，没有任何关联。

我们需要执行命令来创建集群，在Redis5.0之前创建集群比较麻烦，5.0之后集群管理命令都集成到了redis-cli中。



1）Redis5.0之前

Redis5.0之前集群命令都是用redis安装包下的src/redis-trib.rb来实现的。因为redis-trib.rb是有ruby语言编写的所以需要安装ruby环境。

 ```sh
# 安装依赖
yum -y install zlib ruby rubygems
gem install redis
 ```



然后通过命令来管理集群：

```sh
# 进入redis的src目录
cd /tmp/redis-6.2.4/src
# 创建集群
./redis-trib.rb create --replicas 1 192.168.150.101:7001 192.168.150.101:7002 192.168.150.101:7003 192.168.150.101:8001 192.168.150.101:8002 192.168.150.101:8003
```



2）Redis5.0以后

我们使用的是Redis6.2.4版本，集群管理以及集成到了redis-cli中，格式如下：

```sh
redis-cli --cluster create --cluster-replicas 1 192.168.150.101:7001 192.168.150.101:7002 192.168.150.101:7003 192.168.150.101:8001 192.168.150.101:8002 192.168.150.101:8003
```

命令说明：

- `redis-cli --cluster`或者`./redis-trib.rb`：代表集群操作命令
- `create`：代表是创建集群
- `--replicas 1`或者`--cluster-replicas 1` ：指定集群中每个master的副本个数为1，此时`节点总数 ÷ (replicas + 1)` 得到的就是master的数量。因此节点列表中的前n个就是master，其它节点都是slave节点，随机分配到不同master



运行后的样子：

![image-20210702181101969](C:\Users\24314\Desktop\讲义\博客\assets\image-20210702181101969.png)

这里输入yes，则集群开始创建：

![image-20210702181215705](C:\Users\24314\Desktop\讲义\博客\assets\image-20210702181215705.png)



通过命令可以查看集群状态：

```sh
redis-cli -p 7001 cluster nodes
```

![image-20210702181922809](C:\Users\24314\Desktop\讲义\博客\assets\image-20210702181922809.png)

### 测试

尝试连接7001节点，存储一个数据：

```sh
# 连接
redis-cli -p 7001
# 存储数据
set num 123
# 读取数据
get num
# 再次存储
set a 1
```

结果悲剧了：

![image-20210702182343979](C:\Users\24314\Desktop\讲义\博客\assets\image-20210702182343979.png)

集群操作时，需要给`redis-cli`加上`-c`参数才可以：

```sh
redis-cli -c -p 7001
```

这次可以了：

![image-20210702182602145](C:\Users\24314\Desktop\讲义\博客\assets\image-20210702182602145.png)



### 散列插槽

Redis 会吧每一个master节点映射到0-16383 个插槽上（hash slot）上，查看集群信息时就能看到

数据key不是跟节点绑定的，而是与插槽绑定，redis会根据key的有效部分计算机插槽值

- key中包含“{}”，且“{}” 中至少包含一个字符，“{}”中的是有效部分，保存一类数据
- key中不包含“{}”,整个key都是有效部分

CRC16算法得到一个hash值，然后对16383 取余



### 集群伸缩

添加一个节点到集群

redis-cli --cluster 提供了很多操作集群的命令 可以通过 ```redis-cli --cluster help```来进行查看

```add-node``` 命令

reshard 重新分片



### 故障转移

当集群中有一个master 宕机会发生什么？

1. 首先是该实例与其他实例失去连接
2. 让后是疑似宕机
3. 最后是确定下线，自动提升一个slave 为master

数据迁移：

1. master 拒绝请求，阻塞
2. offset同步
3. 开始故障转移
4. 广播通知



### SpringBoot 访问分片集群



1. 引入redis的starter 依赖
2. 配置分片集群地址

```yaml
spring:
  redis:
    cluster:
      nodes: #指定分片集群中的每一个节点信息
```

3. 配置读写分离

```java
@Bean
public LettuceClientConfigurationBuilderCustomizer configurationBuilderCustomizer(){
    return configBuilder -> configBuilder.readFrom(ReadFrom.REPLICA_PREFERRED);
}
```



## Redis 键值设计

### 优雅的 key 结构

- 遵循基本格式：\[业务名称]:[数据名]:[id]
  - 可读性强
  - 避免key冲突
  - 方便管理
- 长度不超过44字节
  - 更节省空间，key 是String，底层包含int embstr 和 raw。embstr在小于44字节使用（不同版本可能不一样），采用连续内存
- 不包含特殊字符



### 拒绝 BigKey

BigKey通常指 value过大

- key本身的数据量过大：一个String类型的key，它的值为 5mb
- key中的成员数过多：一个ZSET 类型的Key，它的成员数量为10000个
- key中成员的数据量过大：一个Hash类型的key，它的成员数量虽然只有1000个，但是总大小为100mb

**推荐**：

- 单个key的value值小于10kb
- 对于集合类型的key，建议元素数量小于1000

**危害**：

- 网络阻塞
- 数据倾斜
- Redis阻塞
- CPU压力

**如何发现**：

- redis-cli --bigkeys
- scan扫描
- 第三方工具（Redis-Rdb-Tools）
- 网络监控

**恰当的数据类型**



## 批处理优化

- MSET（局限性）

- PipLine

### 集群下的批处理

如果是一个集群，就必须落在一个插槽中去，否则请求失效。

...

## 服务端优化



### 持久化配置：

1. 用来做缓存实例尽量不要开启持久化功能
2. 建议关闭RDB 使用AOF
3. 利用脚本定期在slave 节点做RDB，实现数据备份
4. 合理的rewrite阈值
5. 配置no-appendfsync-on-rewrite = yes，禁止在rewrite期间做aof

**部署**：

1. redis 实例的物理机要预留足够内存
2. 耽搁Redis实例内容上限不要太大
3. 不要与CPU密集型应用部署在一起
4. 不要与高硬盘负载应用一起部署。如消息队列、数据库

### 慢查询：

- slowlog-log-slower-than：慢查询阈值，单位是微秒，建议1000，默认10000

- 慢查询会被放入慢查询日志中，日志的长度上限可以通过 slowlog-max-len：的长度，默认128，建议1000
- slowlog len ：查询慢查询日志长度
- slowlog get[n]：读取n条慢查询日志
- slowlog reset：清空慢查询列表

**命令及安全命令**：

- 要设置密码
- 禁止线上使用：keys，flushall，flushdb，config set 等命令。可以利用rename-command禁用
- bind：现在网卡，禁止外网网卡访问
- 开启防火墙
- 不要使用Root账号启动redis
- 尽量不是有默认的端口

### 内存配置

- info memory
- memory xxx

**内存缓冲区配置**：

- 复制缓冲区：主从复制的repl_backlog_buf
- AOF缓冲区：AOF刷盘之前的区域
- 客户端缓冲区：输入缓冲区和输出缓冲区



 ### 集群还是主从

1. 集群完整性问题：

   在redis的默认配置中，如果一个插槽不能使用了，则整个集群都会定制对外服务

   建议将 cluster-require-full-coverage false

2. 集群带宽问题

   集群之间会不断的相互ping来确定集群中其他节点的状态。每次ping懈怠的信息至少包括

   - 插槽信息
   - 集群状态信息

   集群中节点越多，集群状态信息也越大，每次集群互通需要的带宽会非常高

   解决途径：

   1. 避免大集群，集群节点数不要太多，最好少于1000，如果业务庞大，则建立多个集群
   2. 避免在耽搁物理机中运行太多Redis实例
   3. 配置核算的cluster-node-timeout值





