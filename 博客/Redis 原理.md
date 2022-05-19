

# Redis 原理

## Redis 数据结构

### 1. 动态字符串 SDS

我们都知道Redis中保存的key是字符串，value往往是字符串或者字符串的集合。可见字符串是Redis中最常用的一种数据结构

Redis没有直接使用C语言中的字符串，因为C语言字符串中存在很多问题

- 获取字符串长度需要通过运算
- 非二进制安全
- 不可修改

Redis 构建了一种新的字符串结构，称为 **简单动态字符串**，简称SDS

​	·

本质是一个结构体，源码如下：

```c
struct __attribute__((__packed__)) sdshdr8 {
    
    uint8_t len;	/* buf 已经保存的字符串字节数，不包含结果标示 */
    uint8_t alloc;  /* buf 申请的总的字节数，不包含结束标示 */
    unsigned char flags; /* 不同 SDS 的头类型，用来控制 SDS 的头大小 */
    char buf[];
    
};
```

内存连续



SDS之所以叫做动态字符串，是因为他具备动态扩容能力。

- 如果新字符串小于1M，则新空间为扩容后字符串长度的两倍+1
- 如果新字符串大于1M，则新空间为扩容后字符串长度+1M+1，称为内存预分配



**优点**：

1. 获取字符串长度的时间复杂度为O(1)
2. 支持动态扩容
3. 减少内存分配次数
4. 二进制安全



### 2. IntSet

IntSet 是 Redis中set 集合的一种实现方式，一句整数数组来实现，并且具备长度可变，有序等特征

```c
typedef struct intset{
    
    uint32_t encoding; /* 编码方式，支持16 位 32位，64位整数 */
    uint32_t length; /* 元素个数 */
    int8_t contents[]; /* 整数数组，保存集合数据 */
    
} intset;
```

 为了方便查找，Redis 会将intset 中所有的整数按照升序一次保存在contents数组中。



![image-20220510172907143](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510172907143.png)



**IntSet的升级**

当插入的数字超过 encoding 的范围时，会自动升级编码方式到合适的大小

1. 升级编码
2. 倒序依次将数组汇总的元素拷贝到扩容后的正确位置（倒序扩容）
3. 将待添加的元素放入数组末尾
4. 最终修改 intset 的头信息



**IntSet 插入方法源码**

```c
intset *intsetAdd(intset *is, int64_t value, uint8_t *success) {
    // 获取当前值的编码
    uint8_t valenc = _intsetValueEncoding(value);
    // 要插入的位置
    uint32_t pos;
    if (success) *success = 1;
    /* Upgrade encoding if necessary. If we need to upgrade, we know that
     * this value should be either appended (if > 0) or prepended (if < 0),
     * because it lies outside the range of existing values. */
    // 判断编码是否超过了当前 intset 的编码
    if (valenc > intrev32ifbe(is->encoding)) {
        /* This always succeeds, so we don't need to curry *success. */
        // 超出编码，需要升级，并且直接返回
        return intsetUpgradeAndAdd(is,value);
    } else {
        /* Abort if the value is already present in the set.
         * This call will populate "pos" with the right position to insert
         * the value when it cannot be found. */
        // 在当前 intset 中查找值与value一样的元素的角标 pos
        if (intsetSearch(is,value,&pos)) {
            if (success) *success = 0;  // 如果找到了，则无需插入，直接结束并返回失败
            return is;
        }
        // 数组扩容
        is = intsetResize(is,intrev32ifbe(is->length)+1);
        // 移动数组中 pos 之后的元素到 pos + 1，给新元素腾出空间
        if (pos < intrev32ifbe(is->length)) intsetMoveTail(is,pos,pos+1);
    }
    // 插入新元素
    _intsetSet(is,pos,value);
    // 元素个数加一
    is->length = intrev32ifbe(intrev32ifbe(is->length)+1);
    return is;
}
```



**IntSet 扩容源码**：

```c
/* Upgrades the intset to a larger encoding and inserts the given integer. */
static intset *intsetUpgradeAndAdd(intset *is, int64_t value) {
    // 获取当前 intset 编码
    uint8_t curenc = intrev32ifbe(is->encoding);
    // 获取新编码
    uint8_t newenc = _intsetValueEncoding(value);
    int length = intrev32ifbe(is->length);  // 获取元素个数
    // 判断新元素是大于0还是小于0，小于0插入队首，大于零插入队尾
    int prepend = value < 0 ? 1 : 0;

    /* First set new encoding and resize */
    // 重置编码
    is->encoding = intrev32ifbe(newenc);
    // 重置数组大小
    is = intsetResize(is,intrev32ifbe(is->length)+1);

    /* Upgrade back-to-front so we don't overwrite values.
     * Note that the "prepend" variable is used to make sure we have an empty
     * space at either the beginning or the end of the intset. */
    // 倒序遍历，逐个搬运元素到新的位置，_intsetGetEncoded 安装旧编码方式查找旧元素
    while(length--) // _intsetSet 按照新编码方式插入新元素
        _intsetSet(is,length+prepend,_intsetGetEncoded(is,length,curenc));

    /* Set the value at the beginning or the end. */
    if (prepend)
        _intsetSet(is,0,value);
    else
        _intsetSet(is,intrev32ifbe(is->length),value);
    // 修改数组长度
    is->length = intrev32ifbe(intrev32ifbe(is->length)+1);
    return is;
}
```



IntSet 可以看作是特殊的整数数组，具备一些特点：

1. Redis 会确保IntSet中的元素唯一，有序
2. 具备类型升级机制，可以节省内存空间
3. 底层采用二分查找的方法

### 3. Dict（字典）

我们知道Redis 是一个键值型的数据库，我们可以根据键实现快速的增删改查。而键与值的映射关系是通过 Dict 来实现的

Dict 由三个部分组成，分别是：哈希表、哈希节点、字典

**源码**：

```c
typedef struct dictEntry {
    // 键
    void *key;
    // 联合体，是这里面中的一个
    union {
        void *val;
        uint64_t u64;
        int64_t s64;
        double d;
    } v; // 值
    // 下一个 Entry 的指针
    struct dictEntry *next;
} dictEntry;

// 用来表示字典的类型
typedef struct dictType {
    uint64_t (*hashFunction)(const void *key);
    void *(*keyDup)(void *privdata, const void *key);
    void *(*valDup)(void *privdata, const void *obj);
    int (*keyCompare)(void *privdata, const void *key1, const void *key2);
    void (*keyDestructor)(void *privdata, void *key);
    void (*valDestructor)(void *privdata, void *obj);
    int (*expandAllowed)(size_t moreMem, double usedRatio);
} dictType;

/* This is our hash table structure. Every dictionary has two of this as we
 * implement incremental rehashing, for the old to the new table. */
typedef struct dictht {
    // entry 数组
    // 数组中保存的是指向entry 的指针
    dictEntry **table;
    // 哈希表大小 2 的n次方
    unsigned long size;
    // 哈希表大小的掩码，总等于 size - 1，用来计算哈希值做 & 运算
    unsigned long sizemask;
    // entry 个数
    unsigned long used;
} dictht;

typedef struct dict {
    dictType *type; // 字典类型，内置不同的hash 函数
    void *privdata; // 私有数据，在做特殊hash时用
    dictht ht[2];	// 一个Dict包含两个哈希表，其中一个是当前数据，另一个一般是空，rehash时使用
    long rehashidx; /* rehashing not in progress if rehashidx == -1 rehash 的进度 */
    int16_t pauserehash; /* If >0 rehashing is paused (<0 indicates coding error)  rehash 是否暂停*/
} dict;

```

当我们向Dirct 添加键值对时，Redis 首先根据 key 计算出 hash 值，然后利用 h & sizemask 来计算元素应该存储到数组中的哪个索引位置。

**Dict 的扩容**：

Dict 中的 HashTable 就是数组结合单向链表的实现，当集合中元素较多时，必然导致哈希冲突增多，链表过长，则查询效率会大大降低

Dict在每次更新键值对时会检查负载因子，满足以下两种情况时会触发哈希表扩容

- 哈希表的LoadFactory >= 1 ，并且服务器没有执行 bgsave或bgrewriteaof等后台进程
- 哈希表的 LoadFactory > 5

**扩容源码**：

```c
static int _dictExpandIfNeeded(dict *d)
{
    // 如果正在 rehash 就返回 DICT_OK
    if (dictIsRehashing(d)) return DICT_OK;

    // 如果哈希表为空，则初始化哈希表默认大小：4
    if (d->ht[0].size == 0) return dictExpand(d, DICT_HT_INITIAL_SIZE);

    // 当负载因子达到 1 以上，并且当前没有进行bgrewrite等进程操作
    // 或者负载因子超过 5 ，这进行扩容
    if (d->ht[0].used >= d->ht[0].size &&
        (dict_can_resize ||
         d->ht[0].used/d->ht[0].size > dict_force_resize_ratio) &&
        dictTypeExpandAllowed(d))
    {
        // 扩容大小为 used + 1 实际上找的是第一个大于等于 used + 1 的 2的n次方
        return dictExpand(d, d->ht[0].used + 1);
    }
    return DICT_OK;
}
```





**Dict 的收缩**：

Dict处理扩容以外，每次删除元素时，也会对负载因子做检查，当LoadFactor < 0.1 时，会做哈希表的收缩 

**相关源码**：

```c
/* Delete an element from a hash.
 * Return 1 on deleted and 0 on not found. */
int hashTypeDelete(robj *o, sds field) {
    int deleted = 0;
    // 如果编码是 ziplist
    if (o->encoding == OBJ_ENCODING_ZIPLIST) {
        unsigned char *zl, *fptr;

        zl = o->ptr;
        fptr = ziplistIndex(zl, ZIPLIST_HEAD);
        if (fptr != NULL) {
            fptr = ziplistFind(zl, fptr, (unsigned char*)field, sdslen(field), 1);
            if (fptr != NULL) {
                zl = ziplistDelete(zl,&fptr); /* Delete the key. */
                zl = ziplistDelete(zl,&fptr); /* Delete the value. */
                o->ptr = zl;
                deleted = 1;
            }
        }
    // 如果是 hash
    } else if (o->encoding == OBJ_ENCODING_HT) {
        if (dictDelete((dict*)o->ptr, field) == C_OK) {
            deleted = 1;
            // 删除成功后，检查是否需要重置Dict大小，如果需要则滴啊用dictResize 重置
            /* Always check if the dictionary needs a resize after a delete. */
            if (htNeedsResize(o->ptr)) dictResize(o->ptr);
        }

    } else {
        serverPanic("Unknown hash encoding");
    }
    return deleted;
}
```

```c
int htNeedsResize(dict *dict) {
    long long size, used;
    // 哈希表大小
    size = dictSlots(dict);
    // entry 数量
    used = dictSize(dict);
    // size > 4 && 负载因子低于 0.1 
    return (size > DICT_HT_INITIAL_SIZE &&
            (used*100/size < HASHTABLE_MIN_FILL));
}
```

```c
int dictResize(dict *d)
{
    unsigned long minimal;
    // 如果正在 bgwriteof 或者 rehash ，返回错误
    if (!dict_can_resize || dictIsRehashing(d)) return DICT_ERR;
    // 获取 used，也就是 entry 个数
    minimal = d->ht[0].used;
    // 如果 used 小于 4，则重置为 4
    if (minimal < DICT_HT_INITIAL_SIZE)
        minimal = DICT_HT_INITIAL_SIZE;
    // 重置大小为 minimal，其实是第一个大于等于minimal的 2的n次方
    return dictExpand(d, minimal);
}
```

**扩容或者创建 hash 表的源码**：

```c
/* Expand or create the hash table,
 * when malloc_failed is non-NULL, it'll avoid panic if malloc fails (in which case it'll be set to 1).
 * Returns DICT_OK if expand was performed, and DICT_ERR if skipped. */
int _dictExpand(dict *d, unsigned long size, int* malloc_failed)
{
    if (malloc_failed) *malloc_failed = 0;

    /* the size is invalid if it is smaller than the number of
     * elements already inside the hash table */
    // 如果当前 entry 数量超过了要申请的大小，或者正在 rehash 直接报错
    if (dictIsRehashing(d) || d->ht[0].used > size)
        return DICT_ERR;
    // 声明一个新的 hash 表
    dictht n; /* the new hash table */
    // 实际的哈希表 size 2的n次方
    unsigned long realsize = _dictNextPower(size);

    /* Detect overflows */
    // 超过最大的值，内存溢出就报错
    if (realsize < size || realsize * sizeof(dictEntry*) < realsize)
        return DICT_ERR;
    // 新的size 值和旧的size 值是一样的也报错
    /* Rehashing to the same table size is not useful. */
    if (realsize == d->ht[0].size) return DICT_ERR;

    // 必要的检查过后，就开始创建新的hash
    /* Allocate the new hash table and initialize all pointers to NULL */
    n.size = realsize;
    n.sizemask = realsize-1;
    if (malloc_failed) {
        // 分配内存
        n.table = ztrycalloc(realsize*sizeof(dictEntry*));
        *malloc_failed = n.table == NULL;
        if (*malloc_failed)
            return DICT_ERR;
    } else
        n.table = zcalloc(realsize*sizeof(dictEntry*));

    n.used = 0;

    /* Is this the first initialization? If so it's not really a rehashing
     * we just set the first hash table so that it can accept keys. */
    // 如果是第一次 直接赋值给 ht[0] 即可
    if (d->ht[0].table == NULL) {
        d->ht[0] = n;
        return DICT_OK;
    }

    /* Prepare a second hash table for incremental rehashing */
    // rehash 标识 rehash 开始了，但是没有立刻开始，渐进式迁移
    d->ht[1] = n;
    d->rehashidx = 0;
    return DICT_OK;
}
```



![image-20220510173038959](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510173038959.png)

**Dict 中的 rehash**：

不管是扩容还是收缩，必定会创建一个新的哈希表，导致哈希表的size 和sizemask 发生变化，而key的查询与 sizemask 有关。因此必须对哈希表中每个key重新计算索引，插入新的哈希表，这个过程称为 rehash。过程如下：

1. 计算新的 realeSize，值取决于当前是扩容还是收缩
   - 如果是扩容，则新的size 为第一个大于等于 dict.ht[0].used + 1 的 2^n
   - 如果是收缩，这为第一个大于等于 dict.ht[0].used 的 2^n （不小于4）
2. 按照新的 realeSize 来分配内存空间，创建dictht，并赋值给 dict.ht[1]
3. 表示 dict.rehashidx = 0 表示 rehash已经开始了
4. 每次执行增删改查操作时，都检查一下 rehashidx 是否大于1，如果是则将dict.ht[0].table[rehashidx] 中的entry 链表 rehash到 dict.ht[1] 中去，并 rehashidx ++ 。直到完成所有迁移
5. 将dict.ht[1] 赋值给 dict.ht[0]，将dict.ht[1] 初始化为空的哈希表释放原来的内存
6. rehashidx = -1时表示rehash 完成
7. 在rehash 过程中，新增操作直接到 ht[1] 中去，查询、修改、删除会依次到两个哈希表中去查询。





### 4. Ziplist

ZipList 是 一种特殊的“双端链表”，由一系列特殊编码的连接内存块组成。可以在任意一端任意进行压入/弹出操作，并且该操作的时间为O(1)

![image-20220510173209229](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510173209229.png)



![image-20220510124131508](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510124131508.png)



**ZipListEntry 定义**：

```c
typedef struct zlentry {
	// 用于记录内存编码后前一个entry的len占用了多少字节 即prevrawlen占用了多少字节
    unsigned int prevrawlensize; 
    // 用于记录前一个entry占用的长度 
    // 通过当前entry地址 - prevrawlen可以寻找到上一个entry的首地址
    unsigned int prevrawlen;     
    // 用于记录内存编码后当前entry的len占用了多少字节
    unsigned int lensize;        
    // 用于记录当前entry的长度
    unsigned int len;
    // 用于记录当前entry的header大小 即lensize + prevrawsize  
    unsigned int headersize;     
    // 用于记录当前节点编码格式,占用 1 个，2个或5个字节
    unsigned char encoding;
    // 指向当前节点的指针      
    unsigned char *p;       
} zlentry;

```



**Encoding编码**：

ZiplistEntry 中的 encoding 编码分为字符串和整数两种

- 字符串：如果 encoding 是以 “00”、“01”、“10” 开头，则证明 content 是字符串
- 如果encoding 是以 11 开始，则证明content 是整数，而且encoding 固定只占用一个字节



**ZipList 的连锁更新问题**：

ZipList 的每个Entry 都 包含previouts_entry_length 来记录上一个节点的大小，长度是1个或5个字节

- 如果前一个字节长度小于 254 字节，则采用一个字节来保存这个长度值
- 如果前一个字节长度大于 254 字节，则采用五个字节来保存。

新增和删除都有可能发送连锁更新。



**ZipList 特征**：

1. 压缩列表的可以看作一种连续内存空间的双向链表
2. 列表的接地那之间不是通过指针连接，而是记录上一节点和本节点长度来寻址，占用内存较低
3. 如果列表数据过多，导致链表过长，可能影响查询性能
4. 增或删较大数据时可能发生连续更新问题



### 5. QuickList

ZipList 存在的问题：

问题1：ZipList 虽然节省内容，但是申请内存必须是连续空间，如果内存占用较多，申请内存效率很低，怎么办？

- 为了解决这个问题，我们必须限制 ZipList 的长度和 Entry 的大小

问题2：但是我们要存储大量数据，超出ZipList 最佳的上限怎么办？

- 可以创建多个ZipList来分片存储数据

问题3：数据拆分后比较分散，不便于管理，多个 ZipList 如何建立联系

- QuickList 数据结构，双向链表，链表中每一个节点都是 ZipList



**list-max-ziplist-size 配置**



**QuickList 结构**：

```c
typedef struct quicklist {
    // 头结点指针
    quicklistNode *head;
    // 尾结点指针
    quicklistNode *tail;
    // 所有的 ziplist 中的 entry 数量
    unsigned long count;        /* total count of all entries in all ziplists */
	// ziplist 总数量
    unsigned long len;          /* number of quicklistNodes */
    // ziplist 的 entry 上限 默认为 -2
    int fill : QL_FILL_BITS;              /* fill factor for individual nodes */
    // 首位不压缩的节点数量
    unsigned int compress : QL_COMP_BITS; /* depth of end nodes not to compress;0=off */
    // 内存重分配时的书签数量及数组，一般用不到
    unsigned int bookmark_count: QL_BM_BITS;
    quicklistBookmark bookmarks[];
} quicklist;
```



**QuickListNode 结构**：

```c
typedef struct quicklistNode {
    // 第一个节点指针
    struct quicklistNode *prev;
    // 下一个节点指针
    struct quicklistNode *next;
    // 当前接地那的 ziplist 指针
    unsigned char *zl;
    // 当前节点的ZipList 的字节大小
    unsigned int sz;             /* ziplist size in bytes */
    // 当前节点的 ziplist entry 个数
    unsigned int count : 16;     /* count of items in ziplist */
    // 编码方式
    unsigned int encoding : 2;   /* RAW==1 or LZF==2 */
    // 数据容器类型
    unsigned int container : 2;  /* NONE==1 or ZIPLIST==2 */
    // 是否被解压缩
    unsigned int recompress : 1; /* was this node previous compressed? */
    unsigned int attempted_compress : 1; /* node can't compress; too small  测试用*/
    unsigned int extra : 10; /* more bits to steal for future usage  预留字段*/
} quicklistNode;
```



![image-20220510162849873](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510162849873.png)



**QuickList 特点**：

1. 是一个节点为 ZipList 的双端链表
2. 节点采用ZipList，解决了传统链表的内存占用问题
3. 控制了ZipList大小，解决联系内存空间申请效率问题
4. 中间节点能压缩，进一步节省内存



### 6. SkipList

SkipList（跳表）首先是链表，单与传统链表相比有几点差异

- 元素按照升序排列存储
- 节点可能包含多个指针，指针跨度不同

![image-20220510160937083](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510160937083.png)

**zskiplist结构**：

```c
typedef struct zskiplist {
    // 头尾节点指针
    struct zskiplistNode *header, *tail;
    // 节点数量
    unsigned long length;
    // 最大的索引层级
    int level;
} zskiplist;
```



**zskiplistNode 结构**：

```c
typedef struct zskiplistNode {
    // 节点存储的值
    sds ele;
    // 节点分数，排序，查找用
    double score;
    struct zskiplistNode *backward;	 // 前一个节点指针
    struct zskiplistLevel {	
        struct zskiplistNode *forward; // 下一个节点指针
        unsigned long span;	// 索引跨度
    } level[]; // 多级索引数组
} zskiplistNode;
```



![image-20220510163040273](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510163040273.png)

**特点**：

- 跳跃表是一个双向链表，每个节点都包含score和ele值

- 节点按照score值排序，score值一样则按照ele字典排序

- 每个节点都可以包含多层指针，层数是1到32之间的随机数

- 不同层指针到下一个节点的跨度不同，层级越高，跨度越大

- 增删改查效率与红黑树基本一致，实现却更简单



### 7. RedisObject

Redis中的任意数据类型的键和值都会被封装为一个RedisObject，也叫做Redis对象，源码如下：

![image-20220510163424465](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510163424465.png)



**Redis的编码方式**：



Redis中会根据存储的数据类型不同，选择不同的编码方式，共包含11种不同类型：

| **编号** | **编码方式**            | **说明**               |
| -------- | ----------------------- | ---------------------- |
| 0        | OBJ_ENCODING_RAW        | raw编码动态字符串      |
| 1        | OBJ_ENCODING_INT        | long类型的整数的字符串 |
| 2        | OBJ_ENCODING_HT         | hash表（字典dict）     |
| 3        | OBJ_ENCODING_ZIPMAP     | 已废弃                 |
| 4        | OBJ_ENCODING_LINKEDLIST | 双端链表               |
| 5        | OBJ_ENCODING_ZIPLIST    | 压缩列表               |
| 6        | OBJ_ENCODING_INTSET     | 整数集合               |
| 7        | OBJ_ENCODING_SKIPLIST   | 跳表                   |
| 8        | OBJ_ENCODING_EMBSTR     | embstr的动态字符串     |
| 9        | OBJ_ENCODING_QUICKLIST  | 快速列表               |
| 10       | OBJ_ENCODING_STREAM     | Stream流               |



**五种数据结构**：

Redis中会根据存储的数据类型不同，选择不同的编码方式。每种数据类型的使用的编码方式如下：

| **数据类型** | **编码方式**                                       |
| ------------ | -------------------------------------------------- |
| OBJ_STRING   | int、embstr、raw                                   |
| OBJ_LIST     | LinkedList和ZipList(3.2以前)、QuickList（3.2以后） |
| OBJ_SET      | intset、HT                                         |
| OBJ_ZSET     | ZipList、HT、SkipList                              |
| OBJ_HASH     | ZipList、HT                                        |



## 五种数据类型

### 1. String

String 是 Redis 中最常见的数据存储类型

- 其基本编码方式是 **RAW**，基于简单动态字符串（SDS）实现，存储上限为 512mb
- 如果存储的SDS长度小于44（44 字节时分配空间刚好为 64 字节复合alloc 算法）字节，则会采用**EMBSTR**编码，此时object head与SDS是一段连续空间。申请内存时只需要调用一次内存分配函数，效率更高。

- 如果存储的字符串是整数值，并且大小在LONG_MAX范围内，则会采用**INT**编码：直接将数据保存在RedisObject的ptr指针位置（刚好8字节），不再需要SDS了。

![image-20220510164414130](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510164414130.png)



**总结**：

![image-20220510165135826](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510165135826.png)



### 2. List

Redis的List类型可以从首、尾操作列表中的元素：

![image-20220510165601096](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510165601096.png)

- LinkedList ：普通链表，可以从双端访问，内存占用较高，内存碎片较多

- ZipList ：压缩列表，可以从双端访问，内存占用低，存储上限低

- QuickList：LinkedList + ZipList，可以从双端访问，内存占用较低，包含多个ZipList，存储上限高





Redis的List结构类似一个双端链表，可以从首、尾操作列表中的元素：

- 在3.2版本之前，Redis采用ZipList和LinkedList来实现List，当元素数量小于512并且元素大小小于64字节时采用ZipList编码，超过则采用LinkedList编码。
- 在3.2版本之后，Redis统一采用QuickList来实现List：

```c
void pushGenericCommand(client *c, int where, int xx) {
    int j;
    // 尝试找到KEY对应的list
    robj *lobj = lookupKeyWrite(c->db, c->argv[1]);
    // 检查类型是否正确
    if (checkType(c,lobj,OBJ_LIST)) return;
    // 检查是否为空
    if (!lobj) {
        if (xx) {
            addReply(c, shared.czero);
            return;
        }
        // 为空，则创建新的QuickList
        lobj = createQuicklistObject();
        quicklistSetOptions(lobj->ptr, server.list_max_ziplist_size,
                            server.list_compress_depth);
        dbAdd(c->db,c->argv[1],lobj);
    }
    // 略 ...
}
```

```c
robj *createQuicklistObject(void) {
    // 申请内存并初始化QuickList
    quicklist *l = quicklistCreate();
    // 创建RedisObject，type为OBJ_LIST
    // ptr指向 QuickList
    robj *o = createObject(OBJ_LIST,l);
    // 设置编码为 QuickList
    o->encoding = OBJ_ENCODING_QUICKLIST;
    return o;
}
```

![image-20220510170902032](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510170902032.png)



### 3. Set 

Set 是 Redis 中的单列集合，满足一下特点：

- 不保证有序性
- 保证元素唯一（可以判断元素是否存在）
- 求交集、并集、差集

![image-20220510171951709](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510171951709.png)

- HashTable 也就是 Dict ，将 value 统一给为 null

- 当存储的值都是整数，并且元素数量不超过 set-max-inset-entries 时，Set 会采用IntSet编码，以节省内存



**创建 Set 源码**：

```c
robj *setTypeCreate(sds value) {
    // 判断 value 是否是数值类型，long long
    if (isSdsRepresentableAsLongLong(value,NULL) == C_OK)
        // 如果是数值类型，则采用 IntSet 编码
        return createIntsetObject();
    // 否则采用默认编码，也就是HT
    return createSetObject();
}
```

```c
robj *createIntsetObject(void) {
    // 初始化Intset并申请内存空间
    intset *is = intsetNew();
    // 创建RedisObject
    robj *o = createObject(OBJ_SET,is);
    // 指定编码为Intset
    o->encoding = OBJ_ENCODING_INTSET;
    return o;
}
```

```c
robj *createSetObject(void) {
    // 初始化为 Dict 类型
    dict *d = dictCreate(&setDictType,NULL);
    // 创建 RedisObject
    robj *o = createObject(OBJ_SET,d);
    // 设置 encoding 为HT
    o->encoding = OBJ_ENCODING_HT;
    return o;
}
```

**添加数据源码**：

```c
int setTypeAdd(robj *subject, sds value) {
    long long llval;
    if (subject->encoding == OBJ_ENCODING_HT) { // 已经是 HT 编码，直接添加元素
        dict *ht = subject->ptr;
        dictEntry *de = dictAddRaw(ht,value,NULL);
        if (de) {
            dictSetKey(ht,de,sdsdup(value));
            dictSetVal(ht,de,NULL);
            return 1;
        }
    } else if (subject->encoding == OBJ_ENCODING_INTSET) { // 目前是 IntSet
    // 判断value 是否是整数
        if (isSdsRepresentableAsLongLong(value,&llval) == C_OK) {
            uint8_t success = 0; // 是整数，直接添加
            subject->ptr = intsetAdd(subject->ptr,llval,&success);
            if (success) {
                /* Convert to regular set when the intset contains
                 * too many entries. */
                // 当 intset元素数量超出set_max_intset_entries 则转换为HT
                size_t max_entries = server.set_max_intset_entries;
                /* limit to 1G entries due to intset internals. */
                if (max_entries >= 1<<30) max_entries = 1<<30;
                if (intsetLen(subject->ptr) > max_entries)
                    setTypeConvert(subject,OBJ_ENCODING_HT);
                return 1;
            }
        } else { // 不是整数直接转换为HT
            /* Failed to get integer from object, convert to regular set. */
            setTypeConvert(subject,OBJ_ENCODING_HT);

            /* The set *was* an intset and this value is not integer
             * encodable, so dictAdd should always work. */
            serverAssert(dictAdd(subject->ptr,sdsdup(value),NULL) == DICT_OK);
            return 1;
        }
    } else {
        serverPanic("Unknown set encoding");
    }
    return 0;
}
```

**流程**：

![image-20220510193104056](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510193104056.png)



### 4. ZSet

ZSet 也就是 SortedSet ，其中每一个元素都需要指定一个score 值和 member 值

- 可以根据 score 值排序
- member 必须唯一，后添加的 member 会覆盖前面的 score 值
- 可以根据 member 查询分数



![image-20220510193257225](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510193257225.png)

因此，zset底层数据结构必须满足**键值存储、键必须唯一、可排序**这几个需求。之前学习的哪种编码结构可以满足？

- **SkipList**：可以排序，并且可以同时存储score和ele值（member）

- **HT**：可以键值存储，并且可以根据key找value



**源码**：

```c
// zset结构
typedef struct zset {
    // Dict指针
    dict *dict;
    // SkipList指针
    zskiplist *zsl;
} zset;

```

```c
robj *createZsetObject(void) {
    zset *zs = zmalloc(sizeof(*zs));
    robj *o;
    // 创建Dict
    zs->dict = dictCreate(&zsetDictType,NULL);
    // 创建SkipList
    zs->zsl = zslCreate(); 
    o = createObject(OBJ_ZSET,zs);
    o->encoding = OBJ_ENCODING_SKIPLIST;
    return o;
}

```



![image-20220510193812382](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510193812382.png)

**缺点很明显：占用空间大，于是：**

当元素数量不多时，HT和SkipList的优势不明显，而且更耗内存。因此zset还会采用ZipList结构来节省内存，不过需要同时满足两个条件：

1. 元素数量小于zset_max_ziplist_entries，默认值128

2. 每个元素都小于zset_max_ziplist_value字节，默认值64



**源码**：

![image-20220510194421262](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510194421262.png)



```c
int zsetAdd(robj *zobj, double score, sds ele, int in_flags, int *out_flags, double *newscore) {
    /* 判断编码方式*/
    if (zobj->encoding == OBJ_ENCODING_ZIPLIST) {// 是ZipList编码
        unsigned char *eptr;
        // 判断当前元素是否已经存在，已经存在则更新score即可        if ((eptr = zzlFind(zobj->ptr,ele,&curscore)) != NULL) {
            //...略
            return 1;
        } else if (!xx) {
            // 元素不存在，需要新增，则判断ziplist长度有没有超、元素的大小有没有超
            if (zzlLength(zobj->ptr)+1 > server.zset_max_ziplist_entries
 		|| sdslen(ele) > server.zset_max_ziplist_value 
 		|| !ziplistSafeToAdd(zobj->ptr, sdslen(ele)))
            { // 如果超出，则需要转为SkipList编码
                zsetConvert(zobj,OBJ_ENCODING_SKIPLIST);
            } else {
                zobj->ptr = zzlInsert(zobj->ptr,ele,score);
                if (newscore) *newscore = score;
                *out_flags |= ZADD_OUT_ADDED;
                return 1;
            }
        } else {
            *out_flags |= ZADD_OUT_NOP;
            return 1;
        }
    }    // 本身就是SKIPLIST编码，无需转换
    if (zobj->encoding == OBJ_ENCODING_SKIPLIST) {
       // ...略
    } else {
        serverPanic("Unknown sorted set encoding");
    }
    return 0; /* Never reached. */
}

```



### 5. Hash

Hash结构与Redis中的Zset非常类似：

- 都是键值存储

- 都需求根据键获取值

- 键必须唯一

区别如下：

- zset的键是member，值是score；hash的键和值都是任意值

- zset要根据score排序；hash则无需排序



因此，Hash底层采用的编码与Zset也基本一致，只需要把排序有关的SkipList去掉即可：

- Hash结构默认采用ZipList编码，用以节省内存。 ZipList中相邻的两个entry 分别保存field和value

- 当数据量较大时，Hash结构会转为HT编码，也就是Dict，触发条件有两个：
  1. ZipList中的元素数量超过了hash-max-ziplist-entries（默认512）
  2. ZipList中的任意entry大小超过了hash-max-ziplist-value（默认64字节）



![image-20220510195356767](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510195356767.png)



![image-20220510195438762](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510195438762.png)



## Redis 网络模型

### 用户空间和内核空间



![image-20220510200358683](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510200358683.png)





任何Linux发行版，其系统内核都是Linux。我们的应用都需要通过Linux内核与硬件交互。为了避免用户应用导致冲突甚至内核崩溃，用户应用与内核是分离的：

- 进程的寻址空间会划分为两部分：**内核空间、用户空间**

- **用户空间**只能执行受限的命令（Ring3），而且不能直接调用系统资源，必须通过内核提供的接口来访问

- **内核空间**可以执行特权命令（Ring0），调用一切系统资源



Linux系统为了提高IO效率，会在用户空间和内核空间都加入缓冲区：

- 写数据时，要把用户缓冲数据拷贝到内核缓冲区，然后写入设备

- 读数据时，要从设备读取数据到内核缓冲区，然后拷贝到用户缓冲区



![image-20220510200621319](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510200621319.png)



### 阻塞IO



![image-20220510201143512](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510201143512.png)

顾名思义，阻塞IO就是两个阶段都必须阻塞等待：

阶段一：

1. 用户进程尝试读取数据（比如网卡数据）

2. 此时数据尚未到达，内核需要等待数据

3. 此时用户进程也处于阻塞状态

阶段二：

1. 数据到达并拷贝到内核缓冲区，代表已就绪

2. 将内核数据拷贝到用户缓冲区

3. 拷贝过程中，用户进程依然阻塞等待

4. 拷贝完成，用户进程解除阻塞，处理数据



**可以看到，阻塞IO模型中，用户进程在两个阶段都是阻塞状态。**



![image-20220510201321079](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510201321079.png)



### 非阻塞IO

顾名思义，非阻塞IO的recvfrom操作会立即返回结果而不是阻塞用户进程。

阶段一：

1. 用户进程尝试读取数据（比如网卡数据）

2. 此时数据尚未到达，内核需要等待数据

3. 返回异常给用户进程

4. 用户进程拿到error后，再次尝试读取

5. 循环往复，直到数据就绪

阶段二：

1. 将内核数据拷贝到用户缓冲区

2. 拷贝过程中，用户进程依然阻塞等待

3. 拷贝完成，用户进程解除阻塞，处理数据

可以看到，非阻塞IO模型中，用户进程在第一个阶段是非阻塞，第二个阶段是阻塞状态。虽然是非阻塞，但性能并没有得到提高。而且忙等机制会导致CPU空转，CPU使用率暴增。



![image-20220510201444509](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510201444509.png)

### IO多路复用

无论是阻塞IO还是非阻塞IO，用户应用在一阶段都需要调用recvfrom来获取数据，差别在于无数据时的处理方案：

- 如果调用recvfrom时，恰好**没有**数据，阻塞IO会使CPU阻塞，非阻塞IO使CPU空转，都不能充分发挥CPU的作用。

- 如果调用recvfrom时，恰好**有**数据，则用户进程可以直接进入第二阶段，读取并处理数据

而在单线程情况下，只能依次处理IO事件，如果正在处理的IO事件恰好未就绪（数据不可读或不可写），线程就会被阻塞，所有IO事件都必须等待，性能自然会很差。

就比如服务员给顾客点餐，分两步：

1. 顾客思考要吃什么（等待数据就绪）

2. 顾客想好了，开始点餐（读取数据）

要提高效率有几种办法？

- 方案一：增加更多服务员（多线程）
- 方案二：不排队，谁想好了吃什么（数据就绪了），服务员就给谁点餐（用户应用就去读取数据）



那么问题来了：用户进程如何知道内核中数据是否就绪呢？





**文件描述符**（File Descriptor）：简称FD，是一个从0 开始的无符号整数，用来关联Linux中的一个文件。在Linux中，一切皆文件，例如常规文件、视频、硬件设备等，当然也包括网络套接字（Socket）。

**IO多路复用**：是利用单个线程来同时监听多个FD，并在某个FD可读、可写时得到通知，从而避免无效的等待，充分利用CPU资源。

阶段一：

1. 用户进程调用select，指定要监听的FD集合

2. 内核监听FD对应的多个socket

3. 任意一个或多个socket数据就绪则返回readable

4. 此过程中用户进程阻塞

阶段二：

1. 用户进程找到就绪的socket

2. 依次调用recvfrom读取数据

3. 内核将数据拷贝到用户空间

4. 用户进程处理数据

![image-20220510202304920](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510202304920.png)

**IO多路复用**是利用单个线程来同时监听多个FD，并在某个FD可读、可写时得到通知，从而避免无效的等待，充分利用CPU资源。不过监听FD的方式、通知的方式又有多种实现，常见的有：

- select

- poll

- epoll

差异：

- select和poll只会通知用户进程有FD就绪，但不确定具体是哪个FD，需要用户进程逐个遍历FD来确认

- epoll则会在通知用户进程FD就绪的同时，把已就绪的FD写入用户空间



**IO多路复用 - select**

```c
// 定义类型别名 __fd_mask，本质是 long int
typedef long int __fd_mask;
/* fd_set 记录要监听的fd集合，及其对应状态 */
typedef struct {
    // fds_bits是long类型数组，长度为 1024/32 = 32
    // 共1024个bit位，每个bit位代表一个fd，0代表未就绪，1代表就绪
    __fd_mask fds_bits[__FD_SETSIZE / __NFDBITS];
    // ...
} fd_set;
// select函数，用于监听fd_set，也就是多个fd的集合
int select(
    int nfds, // 要监视的fd_set的最大fd + 1
    fd_set *readfds, // 要监听读事件的fd集合
    fd_set *writefds,// 要监听写事件的fd集合
    fd_set *exceptfds, // // 要监听异常事件的fd集合
    // 超时时间，null-用不超时；0-不阻塞等待；大于0-固定等待时间
    struct timeval *timeout
);

```

**按照比特位存储要监听的 fd**

![image-20220510203441115](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510203441115.png)

**select模式存在的问题**：

1. 需要将整个fd_set从用户空间拷贝到内核空间，select结束还要再次拷贝回用户空间

2. select无法得知具体是哪个fd就绪，需要遍历整个fd_set

3. fd_set监听的fd数量不能超过1024



**IO多路复用 - poll**

```c
// pollfd 中的事件类型
#define POLLIN     //可读事件
#define POLLOUT    //可写事件
#define POLLERR    //错误事件
#define POLLNVAL   //fd未打开

// pollfd结构
struct pollfd {
    int fd;     	  /* 要监听的fd  */
    short int events; /* 要监听的事件类型：读、写、异常 */
    short int revents;/* 实际发生的事件类型 */
};
// poll函数
int poll(
    struct pollfd *fds, // pollfd数组，可以自定义大小
    nfds_t nfds, // 数组元素个数
    int timeout // 超时时间
);

```

**poll**模式对select模式做了简单改进，但性能提升不明显，部分关键代码如下：

IO流程：

1. 创建pollfd数组，向其中添加关注的fd信息，数组大小自定义

2. 调用poll函数，将pollfd数组拷贝到内核空间，转链表存储，无上限

3. 内核遍历fd，判断是否就绪

4. 数据就绪或超时后，拷贝pollfd数组到用户空间，返回就绪fd数量n

5. 用户进程判断n是否大于0

6. 大于0则遍历pollfd数组，找到就绪的fd

与select对比：

- select模式中的fd_set大小固定为1024，而pollfd在内核中采用链表，理论上无上限

- 监听FD越多，每次遍历消耗时间也越久，性能反而会下降



**IO多路复用 - epoll**

epoll模式是对select和poll的改进，它提供了三个函数：

```c
struct eventpoll {
    //...
    struct rb_root  rbr; // 一颗红黑树，记录要监听的FD
    struct list_head rdlist;// 一个链表，记录就绪的FD
    //...
};
// 1.创建一个epoll实例,内部是event poll，返回对应的句柄epfd
int epoll_create(int size);
// 2.将一个FD添加到epoll的红黑树中，并设置ep_poll_callback
// callback触发时，就把对应的FD加入到rdlist这个就绪列表中
int epoll_ctl(
    int epfd,  // epoll实例的句柄
    int op,    // 要执行的操作，包括：ADD、MOD、DEL
    int fd,    // 要监听的FD
    struct epoll_event *event // 要监听的事件类型：读、写、异常等
);
// 3.检查rdlist列表是否为空，不为空则返回就绪的FD的数量
int epoll_wait(
    int epfd,                   // epoll实例的句柄
    struct epoll_event *events, // 空event数组，用于接收就绪的FD
    int maxevents,              // events数组的最大长度
    int timeout   // 超时时间，-1用不超时；0不阻塞；大于0为阻塞时间
);

```

![image-20220510204632608](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510204632608.png)



**总结**：

**select模式存在的三个问题**：

1. 能监听的FD最大不超过1024

2. 每次select都需要把所有要监听的FD都拷贝到内核空间

3. 每次都要遍历所有FD来判断就绪状态

**poll模式的问题**：

1. poll利用链表解决了select中监听FD上限的问题，但依然要遍历所有FD，如果监听较多，性能会下降

**epoll模式中如何解决这些问题的？**

1. 基于epoll实例中的红黑树保存要监听的FD，理论上无上限，而且增删改查效率都非常高

2. 每个FD只需要执行一次epoll_ctl添加到红黑树，以后每次epol_wait无需传递任何参数，无需重复拷贝FD到内核空间

3. 利用ep_poll_callback机制来监听FD状态，无需遍历所有FD，因此性能不会随监听的FD数量增多而下降





**事件通知机制**：

当FD有数据可读时，我们调用epoll_wait（或者select、poll）可以得到通知。但是事件通知的模式有两种：

- LevelTriggered：简称LT，也叫做水平触发。只要某个FD中有数据可读，每次调用epoll_wait都会得到通知。

- EdgeTriggered：简称ET，也叫做边沿触发。只有在某个FD有状态变化时，调用epoll_wait才会被通知。

举个栗子：

①假设一个客户端socket对应的FD已经注册到了epoll实例中

②客户端socket发送了2kb的数据

③服务端调用epoll_wait，得到通知说FD就绪

④服务端从FD读取了1kb数据

⑤回到步骤3（再次调用epoll_wait，形成循环）

结果：

- 如果我们采用LT模式，因为FD中仍有1kb数据，则第⑤步依然会返回结果，并且得到通知

- 如果我们采用ET模式，因为第③步已经消费了FD可读事件，第⑤步FD状态没有变化，因此epoll_wait不会返回，数据无法读取，客户端响应超时。

**结论**：

- LT：事件通知频率较高，会有重复通知，影响性能

- ET：仅通知一次，效率高。可以基于非阻塞IO循环读取解决数据读取不完整问题

select 和 poll 仅支持LT模式，epoll可以自由选择LT和ET两种模式



**IO多路复用-web服务流程**

![image-20220510211204677](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510211204677.png)





### 信号驱动 IO

**信号驱动IO**是与内核建立SIGIO的信号关联并设置回调，当内核有FD就绪时，会发出SIGIO信号通知用户，期间用户应用可以执行其它业务，无需阻塞等待。



阶段一：

①用户进程调用sigaction，注册信号处理函数

②内核返回成功，开始监听FD

③用户进程不阻塞等待，可以执行其它业务

④当内核数据就绪后，回调用户进程的SIGIO处理函数

阶段二：

①收到SIGIO回调信号

②调用recvfrom，读取

③内核将数据拷贝到用户空间

④用户进程处理数据

当有大量IO操作时，信号较多，SIGIO处理函数不能及时处理可能导致信号队列溢出，而且内核空间与用户空间的频繁信号交互性能也较低。

![image-20220510211737062](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510211737062.png)



### 异步IO

**异步IO**的整个过程都是非阻塞的，用户进程调用完异步API后就可以去做其它事情，内核等待数据就绪并拷贝到用户空间后才会递交信号，通知用户进程。

阶段一：

①用户进程调用aio_read，创建信号回调函数

②内核等待数据就绪

③用户进程无需阻塞，可以做任何事情

阶段二：

①内核数据就绪

②内核数据拷贝到用户缓冲区

③拷贝完成，内核递交信号触发aio_read中的回调函数

④用户进程处理数据

可以看到，异步IO模型中，用户进程在两个阶段都是非阻塞状态

**还是存在问题的，在并发量过高的情况下，用户请求通知内核完成数据拷贝，内核积累的IO读写的任务会越来越多，这样很有可能导致系统崩溃的情况**

![image-20220510211921183](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510211921183.png)



**同步和异步**

IO操作是同步还是异步，关键看数据在内核空间与用户空间的拷贝过程（数据读写的IO操作），也就是阶段二是同步还是异步：

![image-20220510212331157](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220510212331157.png)



### Redis 网络模型



Redis 到底是单线程还是多线程？

- 如果仅仅是Redis的核心业务部分（命令处理），答案是单线程
- 如果聊的是整个Redis，那么答案就是多线程

在Redis版本迭代过程中，在两个重要的时间节点上引入了多线程的支持

- Redis 4.0：引入多线程异步处理一些耗时较长的任务，例如异步删除命令 unlink
- Redis 6.0：在核心网络模型中引入 多线程，进一步提高对于多核CPU的利用率



为什么Redis 要选择单线程？

1. 抛开持久化不谈，Redis是纯内存操作，执行速度非常快，它的性能瓶颈是网络延迟而不是执行速度，因此多线程并不会带来巨大的性能提升。
2. 多线程会导致过多的上下文切换，带来不必要的开销
3. 引入多线程会面临线程安全问题，必然要引入线程锁这样的安全手段，实现复杂度增高，而且性能也会大打折扣



**Redis通过IO多路复用来提高网络性能，并且支持各种不同的多路复用实现，并且将这些实现进行封装， 提供了统一的高性能事件库API库 AE：**

![image-20220511143743138](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220511143743138.png)

 

![image-20220511144748193](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220511144748193.png)

![image-20220511145504879](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220511145504879.png)

来看下Redis单线程网络模型的整个流程：

![image-20220511150437571](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220511150437571.png)

**readQueryFromClient 源码**：

```c
// 命令请求处理器，处理 client socket 的请求
void readQueryFromClient(connection *conn) {
    // 获取当前客户端，客户端中有缓冲区用来读和写
    client *c = connGetPrivateData(conn);
    // 获取c->querybuf缓冲区大小
    long int qblen = sdslen(c->querybuf);
    // 读取请求数据到 c->querybuf 缓冲区
    connRead(c->conn, c->querybuf+qblen, readlen);
    // ... 
    // 解析缓冲区字符串，转为Redis命令参数存入 c->argv 数组
    processInputBuffer(c);
    // ...
    // 处理 c->argv 中的命令
    processCommand(c);
}
// 处理命令
int processCommand(client *c) {
    // ...
    // 根据命令名称，寻找命令对应的command，例如 setCommand、getCommand
    c->cmd = c->lastcmd = lookupCommand(c->argv[0]->ptr);
    // ...
    // 执行command，得到响应结果，例如ping命令，对应pingCommand
    c->cmd->proc(c);
    // 把执行结果写出，例如ping命令，就返回"pong"给client，
    // shared.pong是 字符串"pong"的SDS对象
    addReply(c,shared.pong); 
}
void addReply(client *c, robj *obj) {
    // 尝试把结果写到 c-buf 客户端写缓存区
    if (_addReplyToBuffer(c,obj->ptr,sdslen(obj->ptr)) != C_OK)
            // 如果c->buf写不下，则写到 c->reply，这是一个链表，容量无上限
            _addReplyProtoToList(c,obj->ptr,sdslen(obj->ptr));
    // 将客户端添加到server.clients_pending_write这个队列，等待被写出
    listAddNodeHead(server.clients_pending_write,c);
}

```

Redis 6.0版本中引入了多线程，目的是为了提高IO读写效率。因此在**解析客户端命令**、**写响应结果**时采用了多线程。核心的命令执行、IO多路复用模块依然是由主线程执行。

![image-20220511151128581](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220511151128581.png)



## Redis 通信协议

### RESP 协议

Redis是一个CS架构的软件，通信一般分两步（不包括pipeline和PubSub）：

1. 客户端（client）向服务端（server）发送一条命令

2. 服务端解析并执行命令，返回响应结果给客户端

因此客户端发送命令的格式、服务端响应结果的格式必须有一个规范，这个规范就是通信协议。



而在Redis中采用的是**RESP**（Redis Serialization Protocol）协议：

- Redis 1.2版本引入了RESP协议

- Redis 2.0版本中成为与Redis服务端通信的标准，称为RESP2

- Redis 6.0版本中，从RESP2升级到了RESP3协议，增加了更多数据类型并且支持6.0的新特性--客户端缓存



**数据类型**：

在RESP中，通过**首字节**的字符来区分不同数据类型，常用的数据类型包括5种：

- 单行字符串：首字节是 ‘**+**’ ，后面跟上单行字符串，以CRLF（ "**\r\n**" ）结尾。例如返回"OK"： "+OK\r\n"

- 错误（Errors）：首字节是 ‘**-**’ ，与单行字符串格式一样，只是字符串是异常信息，例如："-Error message\r\n"

- 数值：首字节是 ‘**:**’ ，后面跟上数字格式的字符串，以CRLF结尾。例如：":10\r\n"

- 多行字符串：首字节是 ‘**$**’ ，表示二进制安全的字符串，最大支持512MB：

  - 如果大小为0，则代表空字符串："$0\r\n\r\n"

  - 如果大小为-1，则代表不存在："$-1\r\n"

- 数组：首字节是 ‘*****’，后面跟上数组元素个数，再跟上元素，元素数据类型不限:



### 模拟Redis 客户端

```java
public class RedisDemo {
    static Socket s;  static PrintWriter writer;  static BufferedReader reader;
    public static void main(String[] args) throws IOException {
        // 1.定义连接参数
        String host = "192.168.150.101";
        int port = 6379;
        // 2.连接 Redis
        s = new Socket(host, port);
        // 2.1.获取输入流
        reader = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
        // 2.2.获取输出流
        writer =new PrintWriter(s.getOutputStream());
        // TODO 3.发送请求
        sendRequest();
        // TODO 4.接收响应
        Object obj = handleResponse();
        System.out.println(obj);
        // 5.关闭连接
        if (reader != null) reader.close();
        if (writer != null) writer.close();
        if (s != null) s.close();
    }
    private static Object handleResponse() {
        try {
            // 当前前缀
            char prefix = (char) reader.read();
            switch (prefix) {
                case '+': // 单行字符串，直接返回
                    return reader.readLine();
                case '-': // 异常，直接抛出
                    throw new RuntimeException(reader.readLine());
                case ':': // 数值，转为 int 返回
                    return Integer.valueOf(reader.readLine());
                case '$': // 多行字符串，先读长度
                    int length = Integer.parseInt(reader.readLine());
                    // 如果为空，直接返回
                    if(length == 0 || length == -1) return ""; 
                    // 不为空，则读取下一行
                    return reader.readLine();
                case '*': // 数组，遍历读取
                    return readBulkString();
                default:
                    return null;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void sendRequest() {
        writer.println("*3");
        writer.println("$3");
        writer.println("set");
        writer.println("$4");
        writer.println("name"); 
        writer.println("$6");
        writer.println("虎哥");
        writer.flush();
    }
    private static List<Object> readBulkString() throws IOException {
        // 当前数组大小
        int size = Integer.parseInt(reader.readLine());
        // 数组为空，直接返回 null
        if(size == 0 || size == -1){
            return null;
        }
        List<Object> rs = new ArrayList<>(size);
        for (int i = size; i > 0; i--) {
            try { // 递归读取
                rs.add(handleResponse());
            } catch (Exception e) {
                rs.add(e);
            }
        }
        return rs;
    }

}

```



### Redis内存回收



Redis之所以性能强，最主要的原因就是基于内存存储。然而单节点的Redis其内存大小不宜过大，会影响持久化或主从同步性能。

我们可以通过修改配置文件来设置Redis的最大内存：

当内存使用达到上限时，就无法存储更多数据了。为了解决这个问题，Redis提供了一些策略实现内存回收：

- 内存过期策略

- 内存淘汰策略



**内存过期策略**：

这里有两个问题需要我们思考：

1. **Redis是如何知道一个key是否过期呢？**

   可以利用亮哥哥Dict 分别记录 key-value 对以及 key-ttl 对

   Redis本身是一个典型的key-value内存存储数据库，因此所有的key、value都保存在之前学习过的Dict结构中。不过在其database结构体中，有两个Dict：一个用来记录key-value；另一个用来记录key-TTL。

   ![image-20220511160030319](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220511160030319.png)

   ![image-20220511160352362](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220511160352362.png)

2. **是不是TTL 到起后就立刻删除？**

   答：惰性删除、周期删除

   **惰性删除**：顾明思议并不是在TTL到期后就立刻删除，而是在访问一个key的时候，检查该key的存活时间，如果已经过期才执行删除。

   ![image-20220511160758531](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220511160758531.png)

   **周期删除**：顾明思议是通过一个定时任务，周期性的**抽样部分过期的key**，然后执行删除。执行周期有两种：

   - Redis服务初始化函数initServer()中设置定时任务，按照server.hz的频率来执行过期key清理，模式为SLOW

   - Redis的每个事件循环前会调用beforeSleep()函数，执行过期key清理，模式为FAST

   ![image-20220511161008276](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220511161008276.png)

   **SLOW**模式规则：

   ①执行频率受server.hz影响，默认为10，即每秒执行10次，每个执行周期100ms。

   ②执行清理耗时不超过一次执行周期的25%.默认slow模式耗时不超过25ms

   ③逐个遍历db，逐个遍历db中的bucket，抽取20个key判断是否过期

   ④如果没达到时间上限（25ms）并且过期key比例大于10%，再进行一次抽样，否则结束

   **FAST**模式规则（过期key比例小于10%不执行 ）：

   ①执行频率受beforeSleep()调用频率影响，但两次FAST模式间隔不低于2ms

   ②执行清理耗时不超过1ms

   ③逐个遍历db，逐个遍历db中的bucket，抽取20个key判断是否过期

   ④如果没达到时间上限（1ms）并且过期key比例大于10%，再进行一次抽样，否则结束



**内存淘汰策略**：

**内存淘汰**：就是当Redis内存使用达到设置的上限时，主动挑选**部分****key**删除以释放更多内存的流程。Redis会在处理客户端命令的方法processCommand()中尝试做内存淘汰：



```c
int processCommand(client *c) {
    // 如果服务器设置了server.maxmemory属性，并且并未有执行lua脚本
    if (server.maxmemory && !server.lua_timedout) {
        // 尝试进行内存淘汰performEvictions
        int out_of_memory = (performEvictions() == EVICT_FAIL);
        // ...
        if (out_of_memory && reject_cmd_on_oom) {
            rejectCommand(c, shared.oomerr);
            return C_OK;
        }
        // ....
    }
}

```



Redis支持8种不同策略来选择要删除的key：

- noeviction： 不淘汰任何key，但是内存满时不允许写入新数据，默认就是这种策略。

- volatile-ttl： 对设置了TTL的key，比较key的剩余TTL值，TTL越小越先被淘汰

- allkeys-random：对全体key ，随机进行淘汰。也就是直接从db->dict中随机挑选

- volatile-random：对设置了TTL的key ，随机进行淘汰。也就是从db->expires中随机挑选。

- allkeys-lru： 对全体key，基于LRU算法进行淘汰

- volatile-lru： 对设置了TTL的key，基于LRU算法进行淘汰

- allkeys-lfu： 对全体key，基于LFU算法进行淘汰

- volatile-lfu： 对设置了TTL的key，基于LFI算法进行淘汰

比较容易混淆的有两个：

- **LRU**（**L**east **R**ecently **U**sed），最少最近使用。用当前时间减去最后一次访问时间，这个值越大则淘汰优先级越高。

- **LFU**（**L**east **F**requently **U**sed），最少频率使用。会统计每个key的访问频率，值越小淘汰优先级越高。



**淘汰策略流程**：

![image-20220511172536904](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220511172536904.png)

****
