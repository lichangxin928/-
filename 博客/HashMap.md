# HashMap 源码分析

## jdk 1.7 

jdk 1.7 中的 HashMap 采用的是数组 + 链表的方式来进行数据的存储，链表采用的是头插法。下面对各个源码中的各个核心方法进行对比

### 内部常量

```java
public class HashMap<K,V>
    extends AbstractMap<K,V>
    implements Map<K,V>, Cloneable, Serializable
{
    // HashMap 默认大小 16
	static final int DEFAULT_INITIAL_CAPACITY = 1 << 4; // aka 16
    // HashMap 最大大小 2的30次方
    static final int MAXIMUM_CAPACITY = 1 << 30;
    // 默认负载因子
    static final float DEFAULT_LOAD_FACTOR = 0.75f;
    // 表未膨胀时要共享的空表实例。
    static final Entry<?,?>[] EMPTY_TABLE = {};
    // 表，根据需要调整大小。长度必须始终是 2 的幂。
    transient Entry<K,V>[] table = (Entry<K,V>[]) EMPTY_TABLE;
    // 表的长度
    transient int size;
    // 要调整大小的下一个大小值（容量负载因子）。
    int threshold;
    // 负载因子的值，如果构造方法没有指定就赋 DEFAULT_LOAD_FACTOR
    final float loadFactor;
    // 此 HashMap 已被结构修改的次数 结构修改是指更改 HashMap 中的映射数量或以其他方式修改其内部结构（例如，重新散列）
    transient int modCount;
    // 映射容量的默认阈值，高于该阈值的替代散列用于字符串键。由于 String 键的散列码计算较弱，替代散列降低了冲突的发生率。
    // 该值可以通过定义系统属性 jdk.map.althashing.threshold 来覆盖。属性值为 1 会强制始终使用替代散列，
    // 而 -1 值确保永远不会使用替代散列
    static final int ALTERNATIVE_HASHING_THRESHOLD_DEFAULT = Integer.MAX_VALUE;
    
    // 与此实例关联的随机值，应用于键的哈希码，以使哈希冲突更难找到。如果为 0，则禁用替代散列
    transient int hashSeed = 0;

}
```

这些值都是对 HashMap 内部所需要的属性的一个定义其作用看注解

### 静态内部类 Holder 类

保存在 VM 启动之前无法初始化的值，这也是一些初始化操作，和业务逻辑没有太大关系

```java
private static class Holder {

    /**
     * Table capacity above which to switch to use alternative hashing.
     */
    static final int ALTERNATIVE_HASHING_THRESHOLD;

    static {
        String altThreshold = java.security.AccessController.doPrivileged(
            new sun.security.action.GetPropertyAction(
                "jdk.map.althashing.threshold"));

        int threshold;
        try {
            threshold = (null != altThreshold)
                    ? Integer.parseInt(altThreshold)
                    : ALTERNATIVE_HASHING_THRESHOLD_DEFAULT;

            // disable alternative hashing if -1
            if (threshold == -1) {
                threshold = Integer.MAX_VALUE;
            }

            if (threshold < 0) {
                throw new IllegalArgumentException("value must be positive integer.");
            }
        } catch(IllegalArgumentException failed) {
            throw new Error("Illegal value for 'jdk.map.althashing.threshold'", failed);
        }

        ALTERNATIVE_HASHING_THRESHOLD = threshold;
    }
}
```



### 构造方法

一共有四个构造方法

```java

// 所有的构造方法都会调用这个构造方法

public HashMap(int initialCapacity, float loadFactor) {
    // 这三个 if 是对参数进行基本的判断
    if (initialCapacity < 0)
        throw new IllegalArgumentException("Illegal initial capacity: " +
                                           initialCapacity);
    if (initialCapacity > MAXIMUM_CAPACITY)
        initialCapacity = MAXIMUM_CAPACITY;
    if (loadFactor <= 0 || Float.isNaN(loadFactor))
        throw new IllegalArgumentException("Illegal load factor: " +
                                           loadFactor);

    // 初始化map参数
    this.loadFactor = loadFactor;
    threshold = initialCapacity;
    // 调用 init 方法，这里并没有进行实现 LinkedHashMap 对其进行了实现
    init();
}

/**
	传入Map 的大小
 */
public HashMap(int initialCapacity) {
    this(initialCapacity, DEFAULT_LOAD_FACTOR);
}

/**
	无参构造，默认大小 16 负载因子 0.75
 */
public HashMap() {
    this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
}

/**
构造一个与指定 Map 具有相同映射的新 HashMap。 HashMap 是使用默认加载因子 (0.75) 和足以容纳指定 Map 中的映射的初始容量创建的。相当于将一个老的 Map 传入，构造出一个新的 Map
 */
public HashMap(Map<? extends K, ? extends V> m) {
    this(Math.max((int) (m.size() / DEFAULT_LOAD_FACTOR) + 1,
                  DEFAULT_INITIAL_CAPACITY), DEFAULT_LOAD_FACTOR);
    inflateTable(threshold);

    putAllForCreate(m);
}
```



### put 过程



```java
    public V put(K key, V value) {
        // 如果 table 为空，则创建一个 table
        if (table == EMPTY_TABLE) {
            // 创建 table 的具体实现
            inflateTable(threshold);
        }
        // 如果 key 的值为null
        if (key == null)
            // 执行插入 null 的方法
            return putForNullKey(value);
        // 不为空，获取 hash 值
        int hash = hash(key);
        // 通过 hash 值获取数组
        int i = indexFor(hash, table.length);
        // 这里遍历查找 HashMap 中是否存在相同的 key，如果存在，就进行替换
        for (Entry<K,V> e = table[i]; e != null; e = e.next) {
            Object k;
            if (e.hash == hash && ((k = e.key) == key || key.equals(k))) {
                V oldValue = e.value;
                e.value = value;
                e.recordAccess(this);
                return oldValue;
            }
        }
        // 此 HashMap 已被结构修改的次数 结构修改是指更改 HashMap 中的映射数量或以其他方式修改其内部结构
        modCount++;
        // 插入方法，具体分析看后面
        addEntry(hash, key, value, i);
        return null;
    }

	// 当 table 为 null 时所执行的逻辑,创建 table
	private void inflateTable(int toSize) {
        // Find a power of 2 >= toSize
        // 这里让 size 的大小变为 2 的次幂向上取整
        int capacity = roundUpToPowerOf2(toSize);
		// 下次要调整的负载因子的大小，不超过 MAXIMUM_CAPACITY
        threshold = (int) Math.min(capacity * loadFactor, MAXIMUM_CAPACITY + 1);
        // 创建了一个 table 数组
        table = new Entry[capacity];
        // 初始化散列掩码值。我们推迟初始化，直到我们真正需要它。
        initHashSeedAsNeeded(capacity);
    }

	// 当 key 的值为 null 时，就执行这个方法
	private V putForNullKey(V value) {
        // 这里是存储到数组的 0 号下标
        for (Entry<K,V> e = table[0]; e != null; e = e.next) {
            // 如果已经有 key 为 null 就将其 value 进行替换
            if (e.key == null) {
                V oldValue = e.value;
                e.value = value;
                // 这个方法在LinkListHashMap 中实现了，这里并没有实现
                e.recordAccess(this);
                // 这里也可以看出 如果 put 成功就返回 oldValue
                return oldValue;
            }
        }
        modCount++;
        // 到这里就表示 0 号下标中没有 key 为 null 的值，就执行插入方法
        addEntry(0, null, value, 0);
        return null;
    }
	// 这个插入方法是通用的方法，所有节点的插入都是使用的这个方法
	void addEntry(int hash, K key, V value, int bucketIndex) {
        // 这里判断是否需要扩容，如果数组的这个节点没有值也暂时不会扩容
        // size 比较的大小是 负载因子 * 容量
        if ((size >= threshold) && (null != table[bucketIndex])) {
            // 扩容方法
            resize(2 * table.length);
            hash = (null != key) ? hash(key) : 0;
            bucketIndex = indexFor(hash, table.length);
        }
		
        createEntry(hash, key, value, bucketIndex);
    }

	// 进行扩容，这里的主要逻辑就是判断 HashMap 是否越界
	void resize(int newCapacity) {
        Entry[] oldTable = table;
        int oldCapacity = oldTable.length;
        if (oldCapacity == MAXIMUM_CAPACITY) {
            threshold = Integer.MAX_VALUE;
            return;
        }
		// 新创建的table 数组赋值给 table
        Entry[] newTable = new Entry[newCapacity];
        // 数据迁移
        transfer(newTable, initHashSeedAsNeeded(newCapacity));
        table = newTable;
        // size * loadFctory
        threshold = (int)Math.min(newCapacity * loadFactor, MAXIMUM_CAPACITY + 1);
    }
	// 将旧的数据保存到新的数据中去，重新 hash
	void transfer(Entry[] newTable, boolean rehash) {
        int newCapacity = newTable.length;
        // 从左到右，从上到下来进行
        for (Entry<K,V> e : table) {
            while(null != e) {
                Entry<K,V> next = e.next;
                if (rehash) {
                    e.hash = null == e.key ? 0 : hash(e.key);
                }
                int i = indexFor(e.hash, newCapacity);
                e.next = newTable[i];
                newTable[i] = e;
                e = next;
            }
        }
    }
	
	// 头插法将新添加的节点插入
	void createEntry(int hash, K key, V value, int bucketIndex) {
        Entry<K,V> e = table[bucketIndex];
        table[bucketIndex] = new Entry<>(hash, key, value, e);
        size++;
    }

```



### put 整体流程图

![image-20220330094419349](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220330094419349.png)

put	

  ---判断数组是否为空，如果为空进行初始化，初始化的是数组容量（@Q6:必须为2的幂次方）
  ---判断key是否为空,执行方法，key为null存在index为0的位置
  ---根据key得到hash，对key进行hashcode,（@Q9进行右移和异或运算）
  ---根据hash值和容量得到下标，indexFor方法（@Q6hash值s与容量-1进行按位与操作）
  ---覆盖逻辑，遍历链表，短路与先判断hash值是否相等，再判断key,value覆盖，返回oldvalue
  ---addEntry(hash,key,value,i),先有（@Q6扩容机制），再根据四个值，头插法或尾插法插入
get	

  ---判断key是否为空
  ---根据key获得entry，计算hash值，得到下标，遍历链表，先比较hash再比较key，就得到了
  ---根据entry获得value



## jdk 1.8

在jdk 1.8 中，对 HashMap 进行了优化，底层采用的是 **数组 + 链表（红黑树）** 的数据结构，对于一些逻辑，例如 Hash 值的计算也有一些小的改动，下面我们继续从 put 方法来进行分析。



### 增加的常量

```java
/**
	这个变量代表树化的临界点，当链表长度大于 8 的时候才会有可能转化为红黑树
	
*/
static final int TREEIFY_THRESHOLD = 8;

/**
	这个变量代表从红黑树转化为链表的临界点，不用8是为了防止频繁的树化和链表化
 */
static final int UNTREEIFY_THRESHOLD = 6;

/**
	这个变量值，只有当总的 size 大于64 时，才会将链表长度大于8 的进行树化，所以不是链表长度一旦大于 8 就会树化
 */
static final int MIN_TREEIFY_CAPACITY = 64;
```

### Node 类

因为要将链表转化为红黑树，所以在 HashMap 中新定义了一个静态内部类，存储树的节点

```java
static class Node<K,V> implements Map.Entry<K,V> {
    // hash值
    final int hash;
    // key 值
    final K key;
    // value 值
    V value;
    // 指向下一个节点指针
    Node<K,V> next;

    Node(int hash, K key, V value, Node<K,V> next) {
        this.hash = hash;
        this.key = key;
        this.value = value;
        this.next = next;
    }

    public final K getKey()        { return key; }
    public final V getValue()      { return value; }
    public final String toString() { return key + "=" + value; }

    public final int hashCode() {
        return Objects.hashCode(key) ^ Objects.hashCode(value);
    }

    public final V setValue(V newValue) {
        V oldValue = value;
        value = newValue;
        return oldValue;
    }

    public final boolean equals(Object o) {
        if (o == this)
            return true;
        if (o instanceof Map.Entry) {
            Map.Entry<?,?> e = (Map.Entry<?,?>)o;
            if (Objects.equals(key, e.getKey()) &&
                Objects.equals(value, e.getValue()))
                return true;
        }
        return false;
    }
}
```

### Hash 值计算的变化

```java

// JDK 1.8
static final int hash(Object key) {
    int h;
    // 这里的又移主要是为了让高位也参与到数组下标的计算中来
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```

### 构造方法的变化

```java
public HashMap() {
    this.loadFactor = DEFAULT_LOAD_FACTOR; // all other fields defaulted
}
```

我们能够看到，在 JDK 1.8 过后的无参构造只是对 loadFactor 赋了默认值。整体和 JDK1.7 一样，都是在put方法中创建 table

### put 方法

```java
	// 实际上是调用了 putVal 方法
	public V put(K key, V value) {
    	return putVal(hash(key), key, value, false, true);
	}

	/**
     * Implements Map.put and related methods.
     *
     * @param hash key的哈希值
     * @param key the key
     * @param value the value to put
     * @param onlyIfAbsent 如果为 true 就不会改变已经存在的值
     * @param evict 如果为false，则表处于创建模式。
     * @return 上一个值，如果没有，则为null
     */
    final V putVal(int hash, K key, V value, boolean onlyIfAbsent,
                   boolean evict) {
        Node<K,V>[] tab; Node<K,V> p; int n, i;
        // 如果 tab == null 表示此时还没有进行初始化
        if ((tab = table) == null || (n = tab.length) == 0)
            // resize() 方法中有初始化和扩容的逻辑，和JDK1.7的方法一样
            n = (tab = resize()).length;
        // 如果这个数组下标为null
        if ((p = tab[i = (n - 1) & hash]) == null)
            // 直接 new一个新节点直接赋值
            tab[i] = newNode(hash, key, value, null);
        else {
            Node<K,V> e; K k;
            // 先hash 再equals
            if (p.hash == hash &&
                ((k = p.key) == key || (key != null && key.equals(k))))
                e = p;
            // 如果是树节点
            else if (p instanceof TreeNode)
                e = ((TreeNode<K,V>)p).putTreeVal(this, tab, hash, key, value);
            else {
                // 还是一个链表
                for (int binCount = 0; ; ++binCount) {
                    if ((e = p.next) == null) {
                        p.next = newNode(hash, key, value, null);
                        // 计算长度与 TREEIFY_THRESHOLD-1 进行对比，判断是否需要树化
                        if (binCount >= TREEIFY_THRESHOLD - 1) // -1 for 1st
                            treeifyBin(tab, hash);
                        break;
                    }
                    // 判断是否有相同的对象
                    if (e.hash == hash &&
                        ((k = e.key) == key || (key != null && key.equals(k))))
                        break;
                    p = e;
                }
            }
            if (e != null) { // existing mapping for key
                V oldValue = e.value;
                if (!onlyIfAbsent || oldValue == null)
                    e.value = value;
                afterNodeAccess(e);
                return oldValue;
            }
        }
        ++modCount;
        // 先添加再扩容，和JDK1.7 中的先扩容再添加不同
        if (++size > threshold)
            resize();
        afterNodeInsertion(evict);
        // 如果添加的是新的key-value 就会返回null
        return null;
    }

	// 初始化或加倍表大小。如果为空，则按照字段阈值中保存的初始容量目标进行分配。否则，因为我们使用二次幂展开，
	// 每个 bin 中的元素必须保持相同的索引，或者在新表中以二次幂的偏移量移动。
	final Node<K,V>[] resize() {
        Node<K,V>[] oldTab = table;
        // 判断 talbe 是否是一个空的表
        int oldCap = (oldTab == null) ? 0 : oldTab.length;
        // 阈值 size * 负载因子
        int oldThr = threshold;
        // 新的容量和阈值
        int newCap, newThr = 0;
        // 如果 oldCap > 0 表示这是一个扩容操作
        if (oldCap > 0) {
            // 判断是否达到了最大容量
            if (oldCap >= MAXIMUM_CAPACITY) {
                threshold = Integer.MAX_VALUE;
                return oldTab;
            }
            // 这里进行扩容操作，赋值 newCap 和newThr
            else if ((newCap = oldCap << 1) < MAXIMUM_CAPACITY &&
                     oldCap >= DEFAULT_INITIAL_CAPACITY)
                newThr = oldThr << 1; // double threshold
        }
        else if (oldThr > 0) // 初始容量被置于阈值
            newCap = oldThr;
        else {               // 为 0 表示用初始值
            newCap = DEFAULT_INITIAL_CAPACITY;
            newThr = (int)(DEFAULT_LOAD_FACTOR * DEFAULT_INITIAL_CAPACITY);
        }
        // 阈值等于 0 时
        if (newThr == 0) {
            float ft = (float)newCap * loadFactor;
            newThr = (newCap < MAXIMUM_CAPACITY && ft < (float)MAXIMUM_CAPACITY ?
                      (int)ft : Integer.MAX_VALUE);
        }
        // 将阈值赋给对象中的threshold
        threshold = newThr;
        @SuppressWarnings({"rawtypes","unchecked"})
        // 根据 newCap 来创建一个 table 数组
        Node<K,V>[] newTab = (Node<K,V>[])new Node[newCap];
        table = newTab;
        // oldTabl != null 表示需要扩容
        if (oldTab != null) {
            // 从左到右
            for (int j = 0; j < oldCap; ++j) {
                Node<K,V> e;
                // 数组中的节点不为空。e为当前链表的第一个节点
                if ((e = oldTab[j]) != null) {
                    // 这行代码可能便于垃圾回收
                    oldTab[j] = null;
                    // 如果e.next == null 就表示数组中的这个下标只有一个节点。直接赋值
                    if (e.next == null)
                        newTab[e.hash & (newCap - 1)] = e;
                    else if (e instanceof TreeNode)
                        // 将树箱中的节点拆分为较低和较高的树箱，如果现在太小，则取消树化。仅从调整大小调用
                        ((TreeNode<K,V>)e).split(this, newTab, j, oldCap);
                    // 表示是一个链表
                    else { // preserve order
                        Node<K,V> loHead = null, loTail = null;
                        Node<K,V> hiHead = null, hiTail = null;
                        Node<K,V> next;
                        do {
                            next = e.next;
                            if ((e.hash & oldCap) == 0) {
                                if (loTail == null)
                                    loHead = e;
                                else
                                    loTail.next = e;
                                loTail = e;
                            }
                            else {
                                if (hiTail == null)
                                    hiHead = e;
                                else
                                    hiTail.next = e;
                                hiTail = e;
                            }
                        } while ((e = next) != null);
                        if (loTail != null) {
                            loTail.next = null;
                            newTab[j] = loHead;
                        }
                        if (hiTail != null) {
                            hiTail.next = null;
                            newTab[j + oldCap] = hiHead;
                        }
                    }
                }
            }
        }
        // 返回结果
        return newTab;
    }


```



### 总体流程

jdk1.8
1---判断数组是否为空，调用resize方法进行初始化（resize方法李有初始化逻辑和扩容逻辑）
1---判断数组元素是否为空（计算出下标得到的这个数组元素，其实就是头节点）
      2---为空则添加新节点
1---数组元素不为空意味着存在一个或一个以上元素，则为链表或红黑树
      2---判断hash值和key，短路与操作，是同一个节点则继续往下执行2，不是则执行判断3
      	    3---判断是否为红黑树
          4---是树，则添加新节点
          4---不是树则为链表，遍历链表，判断是否为尾节点
         5---是尾节点则添加新节点
          4---判断hash和key，返回e执行下一步的2操作覆盖操作
      2---同一个节点则执行覆盖操作，同jdk1.7
结果不是了添加新节点，就是因为相同所以覆盖原来的旧节点
jdk1.8先加数据再扩容，如果扩容可以解决链表太长问题就不变红黑树

1.7和1.8差不多流程都是
1.判断数组是否为空
2.遍历链表（1.8就是多加了遍历红黑树）
3.判断hash和key,相同则覆盖，不同就是加新元素

## 常见问题

Q0：HashMap是如何定位下标的？
A：先获取Key，然后对Key进行hash，获取一个hash值，然后用hash值对HashMap的容量进行取余（实际上不是真的取余，而是使用按位与操作，原因参考Q6），最后得到下标。

Q1：HashMap由什么组成？
A：数组+单链表，jdk1.8以后又加了红黑树，当链表节点个数超过8个（m默认值）以后，开始使用红黑树，使用红黑树一个综合取优的选择，相对于其他数据结构，红黑树的查询和插入效率都比较高。而当红黑树的节点个数小于6个（默认值）以后，又开始使用链表。这两个阈值为什么不相同呢？主要是为了防止出现节点个数频繁在一个相同的数值来回切换，举个极端例子，现在单链表的节点个数是9，开始变成红黑树，然后红黑树节点个数又变成8，就又得变成单链表，然后节点个数又变成9，就又得变成红黑树，这样的情况消耗严重浪费，因此干脆错开两个阈值的大小，使得变成红黑树后“不那么容易”就需要变回单链表，同样，使得变成单链表后，“不那么容易”就需要变回红黑树。

Q2：Java的HashMap为什么不用取余的方式存储数据？
A：实际上HashMap的indexFor方法用的是跟HashMap的容量-1做按位与操作，而不是%求余。（这里有个硬性要求，容量必须是2的指数倍，原因参考Q6）

Q3：HashMap往链表里插入节点的方式？
A：jdk1.7以前是头插法，jdk1.8以后是尾插法，因为引入红黑树之后，就需要判断单链表的节点个数（超过8个后要转换成红黑树），所以干脆使用尾插法，正好遍历单链表，读取节点个数。也正是因为尾插法，使得HashMap在插入节点时，可以判断是否有重复节点。

Q4：HashMap默认容量和负载因子的大小是多少？
A：jdk1.7以前默认容量是16，负载因子是0.75。

Q5：HashMap初始化时，如果指定容量大小为10，那么实际大小是多少？
A：16，因为HashMap的初始化函数中规定容量大小要是2的指数倍，即2，4，8，16，所以当指定容量为10时，实际容量为16。

Q6：容量大小为什么要取2的指数倍？
A：两个原因：1，提升计算效率：因为2的指数倍的二进制都是只有一个1，而2的指数倍-1的二进制就都是左全0右全1。那么跟（2^n - 1）做按位与运算的话，得到的值就一定在【0,（2^n - 1）】区间内，这样的数就刚合适可以用来作为哈希表的容量大小，因为往哈希表里插入数据，就是要对其容量大小取余，从而得到下标。所以用2^n做为容量大小的话，就可以用按位与操作替代取余操作，提升计算效率。2.便于动态扩容后的重新计算哈希位置时能均匀分布元素：因为动态扩容仍然是按照2的指数倍，所以按位与操作的值的变化就是二进制高位+1，比如16扩容到32，二进制变化就是从0000 1111（即15）到0001 1111（即31），那么这种变化就会使得需要扩容的元素的哈希值重新按位与操作之后所得的下标值要么不变，要么+16（即挪动扩容后容量的一半的位置），这样就能使得原本在同一个链表上的元素均匀（相隔扩容后的容量的一半）分布到新的哈希表中。（注意：原因2（也可以理解成优点2），在jdk1.8之后才被发现并使用）

Q7：HashMap满足扩容条件的大小（即扩容阈值）怎么计算？
A：扩容阈值=min(容量*负载因子,MAXIMUM_CAPACITY+1)，MAXIMUM_CAPACITY非常大，所以一般都是取（容量*负载因子）

Q8：HashMap是否支持元素为null？
A：支持。

Q9：HashMap的 hash(Obeject k)方法中为什么在调用 k.hashCode()方法获得hash值后，为什么不直接对这个hash进行取余，而是还要将hash值进行右移和异或运算？
A：如果HashMap容量比较小而hash值比较大的时候，哈希冲突就容易变多。基于HashMap的indexFor底层设计，假设容量为16，那么就要对二进制0000 1111（即15）进行按位与操作，那么hash值的二进制的高28位无论是多少，都没意义，因为都会被0&，变成0。所以哈希冲突容易变多。那么hash(Obeject k)方法中在调用 k.hashCode()方法获得hash值后，进行的一步运算：h^=(h>>>20)^(h>>>12);有什么用呢？首先，h>>>20和h>>>12是将h的二进制中高位右移变成低位。其次异或运算是利用了特性：同0异1原则，尽可能的使得h>>>20和h>>>12在将来做取余（按位与操作方式）时都参与到运算中去。综上，简单来说，通过h^=(h>>>20)^(h>>>12);运算，可以使k.hashCode()方法获得的hash值的二进制中高位尽可能多地参与按位与操作，从而减少哈希冲突。

Q10：哈希值相同，对象一定相同吗？对象相同，哈希值一定相同吗？
A：不一定。一定。

Q11：HashMap的扩容与插入元素的顺序关系？
A：jdk1.7以前是先扩容再插入，jdk1.8以后是先插入再扩容。

Q12：HashMap扩容的原因？
A：提升HashMap的get、put等方法的效率，因为如果不扩容，链表就会越来越长，导致插入和查询效率都会变低。

Q13：jdk1.8引入红黑树后，如果单链表节点个数超过8个，是否一定会树化？
A：不一定，它会先去判断是否需要扩容（即判断当前节点个数是否大于扩容的阈值），如果满足扩容条件，直接扩容，不会树化，因为扩容不仅能增加容量，还能缩短单链表的节点数，一举两得。
