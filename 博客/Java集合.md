# Java集合

Java 8 中引入了函数式接口以及 lamdba 表达式，对 Java 的整个集合体系又增加了一些东西。此文章将从 Java 源码的角度来学习  Java 的集合类。 



首先是 Java 集合类的整体类图

![YK0AEQ.png](https://s1.ax1x.com/2020/05/08/YK0AEQ.png)



我们将从顶至底的方式来进行说明。

## 前置知识

 ### Lamdba表达式

在 Java 8 中出现了一个新的特性，那就是 Lamdba 表达式，可以把Lambda表达式理解为简洁的表示可传递的匿名函数的一种方式：它没有名称，但它有参数列表，函数主体，返回类型，可能还有一个可以抛出的异常列表。

如果之前有接触过 JavaScript 中的箭头函数，那么对Lamdba表达式就应该很好理解，其语法跟 JavaScript 中的箭头函数的语法也大致相同，但在此处还是简要讲解一下。

Java给 Lamdba 表达式赋予了一个新的操作符 " -> " ，我们讲 Lamdba 理解为一个函数，箭头的左侧是相当于要传递的参数，而箭头的右侧就是方法体分为一下几种情况

**值得注意的是当方法体只有一条语句时，可以将大括号和return省略,当传递的参数只有一个时，也能将小括号省略**

1. 无参数，无返回值，的用法

```java
()->System.out.printIn("Hello world");
()->{
    System.out.printIn("hello world");
	System.out.printIn("hello China");
}
// 这里表示返回 1 
()->1
```

2. 有参数时

```java
// 传入 x 返回 x+1
x->x+1;
(x,y)->{
	System.out.printIn(x);
	System.out.printIn(y);
	return x + y;
}
```

3. Lamdba 表达式的类型推断（传入的参数不需要指类型）

```java
// Lamdba 表达式需要函数时接口的的支持，在函数式接口中会标注泛型，这就是Lamdba表达式类型推断的基础
// 这里的 x 会自动为 String 类型
Consumer<String> con = (x) -> System.out.println(x);
```

### Lamdba表达式 方法的引用

在了解的了 Lamdba 表达式后，我们不得不考虑代码的复用问题，就是如果有很多 Lamdba 表达式都需要相同的业务逻辑，那么就不必要每次都编写一次，所以 Lamdba 表达式是支持传递一个方法的。

**一般做法**

```java
Comparator<Integer> cc = (x, y) -> Integer.compare(x, y);
```

这样就相当于这个Lamdba 表达式是执行 Integer.compare() 方法，确实通俗易懂，但是 Java给我们提供了一种更加简单的方法：

那就是 “：：” 符号

可以大致分为四种情况

- 类：：静态方法名
- 类：：实例方法名
- 对象：：实例方法名

- 构造方法：ClassName::new

所以刚刚的方法能够被改造为

```java
Comparator<Integer> bb = Integer::compare;
```

这样看起来是不是简单明了多了呢？还有两个注意点

- 方法引用所引用的方法的参数列表与返回值类型，需要与函数式接口中抽象方法的参数列表和返回值类型保持一致
- Lambda 的参数列表的第一个参数，是实例方法的调用者，第二个参数(或无参)是实例方法的参数时，格式ClassName::MethodName



### 函数式接口简介

在 Java8 中 Lamdba 所使用的接口，必须是函数式接口。

**特点**

函数式接口中**只能有一个抽象方法**，在Java8中给我们提供了很多函数式接口，比说，无参无返回值的（Runnable接口），有一个参数，无返回值的（Consumer），有多个参数有返回值的（BiFunction）……等等；

可以使用```@FunctionalInterface```注解来对函数式接口进行检查。



其实也不难理解，Lamdba 实际上就是对函数式接口中的抽象方法进行重写，这个过程省去了一般情况下我们需要实现接口再创建对象这个过程，或许与匿名内部类有点相似，**但其并非是匿名内部类的语法糖**

1. 所需的类型不一样

​		如果是匿名内部类，那么可以用接口，也可以用抽象类，甚至可以是普通的实体类

​		如果是lambda表达式，就只能用接口

2. 使用的限制不一样

​		如果接口当中有且只有一个抽象方法时，那么可以使用lambda表达式，也可以使用匿名内部类

​		但是如果接口当中抽象方法不唯一时，那么就只能使用匿名内部类，不能使用lambda表达式

3. 实现的原理不一样

​		匿名内部类：其实本身就是一个类，编译之后，直接产生一个单独的.class字节码文件

​		lambda表达式：编译之后，没有单独的.calss字节码文件，对应的字节码文件会在运行的时候生成

函数时接口大致就是这样，后面在对Java集合源码进行分析时，再对所遇到的进行函数式接口进行分析。





## Iterable 接口

我们首先来看 Iterable 的源码：

```java
public interface Iterable<T> {
    
    Iterator<T> iterator();
    
    default void forEach(Consumer<? super T> action) {
        Objects.requireNonNull(action);
        for (T t : this) {
            action.accept(t);
        }
    }
    
    default Spliterator<T> spliterator() {
        return Spliterators.spliteratorUnknownSize(iterator(), 0);
    }
}
```

在 Java 8 之前 Iterable 接口中都只有 ```Iterator<T> iterator();```  这一个方法，在 Java 8过后，由于 Lamdba 表达式和函数式接口的引入，于是加入后面这两个方法。



### Iterator\<T> iterator() 方法

首先我们来看iterator这个方法，作用是返回 T 类型元素的迭代器，这也符合 Iterable 接口，表示可迭代的意思，实现这个接口必实现iterator方法，从而返回一个迭代器，使用返回的迭代器，就能够实现对集合类的遍历

**Iterator接口**

```java
public interface Iterable<T> {
	// 判断集合是否还有下一个元素    
    boolean hasNext();
    // 返回集合的下一个元素，第一次调用这个方法时就是返回集合的第一个元素，然后指向下一个元素
    E next();
    // 调用这个方法会将迭代器当前返回的元素删除
    default void remove() {
        throw new UnsupportedOperationException("remove");
    }
	// 这个方法表示对每个元素执行相关操作，需要传入一个 Consumer （可以使用 Lamdba 表达式）
    default void forEachRemaining(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        while (hasNext())
            action.accept(next());
    }
    
}
```



**使用迭代器进行遍历**

```java
	public static void main(String[] args) {
        List list = new ArrayList();
        list.add(1);
        list.add(2);
        list.add(3);
        Iterator iterator = list.iterator();
        while(iterator.hasNext()){
            System.out.println(iterator.next());
        }
    }
```



这也解释了平时我们在使用迭代器对集合进行遍历时，所调用的方法的底层定义。



### void forEach(Consumer<? super T> action) 方法

这个方法是Java8 之后才有的方法我们来对其进行分析：

```java
    
	/*
		这里我们就接触到了第一个函数式接口 Consumer,其实看这个代码还是挺好理解的
		大致意思就是，传入一个Consumer 实现类（可用Lamdba表达式）来代替，先判断
		action是否为 NULL 如果不为空，则使用增强 for 循环对每一个 属性执行action
		中的 accept 方法
	*/
	default void forEach(Consumer<? super T> action) {
        // 对 action 进行判断
        Objects.requireNonNull(action);
        // 增强 for 循环来执行代码
        for (T t : this) {
            action.accept(t);
        }
    }	
```



在了解的 lamdba 表达式和函数式接口之后，对forEach 方法也是比较好理解的。

**例子：使用forEach来进行遍历**

```java
    public static void main(String[] args) {
        List list = new ArrayList();
        list.add(1);
        list.add(2);
        list.add(3);
        // 使用foreach来进行遍历
        list.forEach(r-> System.out.println(r));

    }
```

从这个例子可以看出，使用forEach来进行遍历，比使用迭代器来进行遍历要简单一些，当然具体场景用具体的方法



### Spliterator\<T> spliterator() 方法

Spliterator为JDK1.8最新添加的分割迭代器，位于java.util.Spliterator。

https://blog.csdn.net/TenaciousD/article/details/97100356



## Collection 接口

![image-20220215151507677](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220215151507677.png)

Collection 是一个顶层接口，它主要用来定义集合的约定，其大多数方法靠字面意思就能够理解到，这里就不一一解释了

List 接口也是一个顶层接口，它继承了 Collection 接口 ，同时也是 ArrayList、LinkedList 等集合元素的父类

Set 接口位于与 List 接口同级的层次上，它同时也继承了 Collection 接口。Set 接口提供了额外的规定。它对add、equals、hashCode  方法提供了额外的标准。

Queue 是和 List、Set 接口并列的 Collection 的三大接口之一。Queue 的设计用来在处理之前保持元素的访问次序。除了 Collection 基础的操作之外，队列提供了额外的插入，读取，检查操作。

SortedSet 接口直接继承于 Set 接口，使用 Comparable 对元素进行自然排序或者使用 Comparator 在创建时对元素提供定制的排序规则。set 的迭代器将按升序元素顺序遍历集合。

Map 是一个支持 key-value 存储的对象，Map 不能包含重复的 key，每个键最多映射一个值。这个接口代替了Dictionary 类，Dictionary 是一个抽象类而不是接口。



### List 接口

List 接口是List 的顶层接口，定义了一些必须实现的方法。为链式的数据结构

![image-20220330134455145](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220330134455145.png)

### ArrayList

ArrayList 是实现了 List 接口的`可扩容数组(动态数组)`，它的内部是基于数组实现的。它的具体定义如下：

```java
public class ArrayList<E> extends AbstractList<E> implements List<E>, RandomAccess, Cloneable, java.io.Serializable {...}
```

- ArrayList 可以实现所有可选择的列表操作，允许所有的元素，包括空值。ArrayList 还提供了内部存储 list 的方法，它能够完全替代 Vector，只有一点例外，ArrayList 不是线程安全的容器。
- ArrayList 有一个容量的概念，这个数组的容量就是 List 用来存储元素的容量。
- ArrayList 不是线程安全的容器，如果多个线程中至少有两个线程修改了 ArrayList 的结构的话就会导致线程安全问题，作为替代条件可以使用线程安全的 List，应使用 `Collections.synchronizedList` 。

```java
List list = Collections.synchronizedList(new ArrayList(...))
```

- ArrayList 具有 fail-fast 快速失败机制，能够对 ArrayList 作出失败检测。当在迭代集合的过程中该集合在结构上发生改变的时候，就有可能会发生 fail-fast，即抛出 `ConcurrentModificationException `异常。

#### 源码分析

```java
public class ArrayList<E> extends AbstractList<E>
        implements List<E>, RandomAccess, Cloneable, java.io.Serializable
{
	// 默认容量
	private static final int DEFAULT_CAPACITY = 10;
	// 空元素数据
	private static final Object[] EMPTY_ELEMENTDATA = {};
	// 默认容量空元素数据
	private static final Object[] DEFAULTCAPACITY_EMPTY_ELEMENTDATA = {};
	// 存储数据的数组
	transient Object[] elementData; // non-private to simplify nested class access
	// 数组大小
	private int size;
	
	
	/*
		构造方法
	*/
	public ArrayList(int initialCapacity) {
        if (initialCapacity > 0) {
        	// 创建传入的大小的数组
            this.elementData = new Object[initialCapacity];
        } else if (initialCapacity == 0) {
        	// 将空元素数组赋值给 elementData
            this.elementData = EMPTY_ELEMENTDATA;
        } else {
            throw new IllegalArgumentException("Illegal Capacity: "+
                                               initialCapacity);
        }
    }
    // 如果调用的是无参构造，则不会马上进行对数组初始化
    public ArrayList() {
        this.elementData = DEFAULTCAPACITY_EMPTY_ELEMENTDATA;
    }
    // 能够接收 Collection 来将 Collection 转换成为 ArrayList
    public ArrayList(Collection<? extends E> c) {
        elementData = c.toArray();
        if ((size = elementData.length) != 0) {
            // c.toArray might (incorrectly) not return Object[] (see 6260652)
            if (elementData.getClass() != Object[].class)
                elementData = Arrays.copyOf(elementData, size, Object[].class);
        } else {
            // replace with empty array.
            this.elementData = EMPTY_ELEMENTDATA;
        }
    }
    
    /*
    	add 方法
    */
    public boolean add(E e) {
    	// 这里调用ensureCapacityInternal判断容量是否足够
        ensureCapacityInternal(size + 1);  // Increments modCount!!
        // 赋值
        elementData[size++] = e;
        return true;
    }
    /*
    	ensureCapacityInternal 方法
    */
    private void ensureCapacityInternal(int minCapacity) {
    	// 实际上是调用的这个方法
        ensureExplicitCapacity(calculateCapacity(elementData, minCapacity));
    }
    /*
    	确认容量是否足够
    */
    private void ensureExplicitCapacity(int minCapacity) {
    	// 每次对 ArrayList 进行操作都会加一（已被结构修改的次数） 
        modCount++;

        // overflow-conscious code
        // 超过最大容量，需要进行扩容
        if (minCapacity - elementData.length > 0)
            grow(minCapacity);
    }
    private void grow(int minCapacity) {
        // overflow-conscious code
        int oldCapacity = elementData.length;
        // 将数组扩容为原来的 1.5 倍
        int newCapacity = oldCapacity + (oldCapacity >> 1);
        // 这里一般不会发生，但是如果初始容量为 1 的时候就可能发生这种情况
        if (newCapacity - minCapacity < 0)
            newCapacity = minCapacity;
        // newCapacity 比 MAX_ARRAY_SIZE 大时
        if (newCapacity - MAX_ARRAY_SIZE > 0)
            newCapacity = hugeCapacity(minCapacity);
        // minCapacity is usually close to size, so this is a win:
        // 将原来的数据拷贝到新的数组中去
        elementData = Arrays.copyOf(elementData, newCapacity);
    }
   	// 变为 1.5 倍后 newCapacity 越界了
    private static int hugeCapacity(int minCapacity) {
        if (minCapacity < 0) // overflow
            throw new OutOfMemoryError();
        // 如果需要的容量大于 MAX_ARRAY_SIZE 就将数组的容量变为 Integer.MAX_VALUE 
        return (minCapacity > MAX_ARRAY_SIZE) ?
            Integer.MAX_VALUE :
            MAX_ARRAY_SIZE;
    }
        
}
```

### Vector

Vector 同 ArrayList 一样，都是基于数组实现的，只不过 Vector 是一个线程安全的容器，它对内部的每个方法都简单粗暴的上锁，避免多线程引起的安全性问题，但是通常这种同步方式需要的开销比较大，因此，访问元素的效率要远远低于 ArrayList。

还有一点在于扩容上，ArrayList 扩容后的数组长度会增加 50%，而 Vector 的扩容长度后数组会增加一倍。底层和ArrayList 相似，这里就不单独拿出来分析了。

### LinkedList 类

LinkedList 是一个双向链表，允许存储任何元素(包括 null )。它的主要特性如下：

* LinkedList 所有的操作都可以表现为双向性的，索引到链表的操作将遍历从头到尾，视哪个距离近为遍历顺序。
* 注意这个实现也不是线程安全的，如果多个线程并发访问链表，并且至少其中的一个线程修改了链表的结构，那么这个链表必须进行外部加锁。或者使用

```java
List list = Collections.synchronizedList(new LinkedList(...))
```

#### 源码分析

```java
public class LinkedList<E>
    extends AbstractSequentialList<E>
    implements List<E>, Deque<E>, Cloneable, java.io.Serializable
{
	// 链表的 size
	transient int size = 0;
	// 双向链表的头
	transient Node<E> first;
	// 双向链表的尾
	transient Node<E> last;
    
    // add 方法
    public boolean add(E e) {
        linkLast(e);
        return true;
    }
    // 将新增的节点插入到链表尾部
    void linkLast(E e) {
        final Node<E> l = last;
        final Node<E> newNode = new Node<>(l, e, null);
        last = newNode;
        if (l == null)
            first = newNode;
        else
            l.next = newNode;
        size++;
        modCount++;
    }

}
```

### Stack

堆栈是我们常说的`后入先出(吃了吐)`的容器 。它继承了 Vector 类，提供了通常用的 push 和 pop 操作，以及在栈顶的 peek 方法，测试 stack 是否为空的 empty 方法，和一个寻找与栈顶距离的 search 方法。

第一次创建栈，不包含任何元素。一个更完善，可靠性更强的 LIFO 栈操作由 Deque 接口和他的实现提供，应该优先使用这个类

```java
Deque<Integer> stack = new ArrayDeque<Integer>()
```



#### 源码分析

```java
public class Stack<E> extends Vector<E> {

	// 空的构造方法，创建一个空的栈
	public Stack() {
    }
    
    // 向栈的顶部添加一个元素，其实是调用的 Vector 中的方法，向数组底部添加了一个对象，成功就返回这个对象
    // 因为 addElement 方法是线程安全的，所以这里不需要添加 synchronized 关键字
    public E push(E item) {
        addElement(item);

        return item;
    }
	// 这里要添加 synchronized 关键字主要是为了保证每个线程获取 len 的值都是有效的 避免多个线程获取到 同样
	// 的 len 值，进而确保了 removeElementAt 的安全性
	public synchronized E pop() {
        E       obj;
        int     len = size();

        obj = peek();
        removeElementAt(len - 1);

        return obj;
    }
    
    // 这个方法是返回 栈中的顶部元素，并且不对其删除
    public synchronized E peek() {
        int     len = size();

        if (len == 0)
            throw new EmptyStackException();
        return elementAt(len - 1);
    }
    
    // 遍历栈中是否有传入的参数
    public synchronized int search(Object o) {
        int i = lastIndexOf(o);

        if (i >= 0) {
            return size() - i;
        }
        return -1;
    }
}
```



### HashSet

HashSet 是 Set 接口的实现类，由哈希表支持(实际上 HashSet 是 HashMap 的一个实例)。它不能保证集合的迭代顺序。这个类允许 null 元素。

* 注意这个实现不是线程安全的。如果多线程并发访问 HashSet，并且至少一个线程修改了set，必须进行外部加锁。或者使用 `Collections.synchronizedSet()` 方法重写。
* 这个实现支持 fail-fast 机制。

HashSet 底层维护一个 HashMap ，当向 HashSet 添加一个属性时，相当于向HashMap 中添加一个键值对，但是 这个键值对中的Value是固定的，我们传入的对象是存到HashMap 中的 key里面的。

#### 源码分析

```java
public class HashSet<E>
    extends AbstractSet<E>
    implements Set<E>, Cloneable, java.io.Serializable
{
    // 序列化和反序列化中的校验有关
    static final long serialVersionUID = -5024744406713321676L;
	// map 集合
    private transient HashMap<E,Object> map;
	// hashmap 中需要存储的Object
    private static final Object PRESENT = new Object();
    
    // 无参构造，直接创建一个HashMap HashMap的属性都使用默认值
    public HashSet() {
        map = new HashMap<>();
    }
    // 传入一个 Collection 将其转换为 HashSet
    public HashSet(Collection<? extends E> c) {
        map = new HashMap<>(Math.max((int) (c.size()/.75f) + 1, 16));
        addAll(c);
    }
    // 传入HashMap 的大小和负载因子
    public HashSet(int initialCapacity, float loadFactor) {
        map = new HashMap<>(initialCapacity, loadFactor);
    }
    // 指定大小
    public HashSet(int initialCapacity) {
        map = new HashMap<>(initialCapacity);
    }
    // dummy 其实并没有用到， 使用这个参数主要是为了让其底层使用 LinkedHashMap
    HashSet(int initialCapacity, float loadFactor, boolean dummy) {
        map = new LinkedHashMap<>(initialCapacity, loadFactor);
    }
    // add 方法都是调用的HashMap 的put 方法
    public boolean add(E e) {
        return map.put(e, PRESENT)==null;
    }
    
    
}
```

可以看到 HashSet 还是比较简单的，基本上都是基于HashMap 的

### TreeSet

TreeSet 是一个基于 TreeMap 的 NavigableSet 实现。这些元素使用他们的自然排序或者在创建时提供的Comparator 进行排序，具体取决于使用的构造函数。

* 此实现为基本操作 add,remove 和 contains 提供了 log(n) 的时间成本。
* 注意这个实现不是线程安全的。如果多线程并发访问 TreeSet，并且至少一个线程修改了 set，必须进行外部加锁。或者使用

```java
SortedSet s = Collections.synchronizedSortedSet(new TreeSet(...))
```

* 这个实现持有 fail-fast 机制。

#### 源码分析

其理论和HashSet相似，底层用的是NavigableMap，将Value变为了一个固定的Object。

https://blog.csdn.net/weixin_34199335/article/details/91442674

### LinkedHashSet 类

LinkedHashSet 继承于 Set，先来看一下 LinkedHashSet 的继承体系：

<img src="https://s1.ax1x.com/2020/05/08/YK0FHg.png" alt="YK0FHg.png" style="zoom:50%;" />

LinkedHashSet 是 Set 接口的 Hash 表和 LinkedList 的实现。这个实现不同于 HashSet 的是它维护着一个贯穿所有条目的双向链表。此链表定义了元素插入集合的顺序。注意：如果元素重新插入，则插入顺序不会受到影响。

* LinkedHashSet 有两个影响其构成的参数： 初始容量和加载因子。它们的定义与 HashSet 完全相同。但请注意：对于 LinkedHashSet，选择过高的初始容量值的开销要比 HashSet 小，因为 LinkedHashSet 的迭代次数不受容量影响。
* 注意 LinkedHashSet 也不是线程安全的，如果多线程同时访问 LinkedHashSet，必须加锁，或者通过使用 

```java
Collections.synchronizedSet
```

* 该类也支持fail-fast机制

#### 源码分析

```java
public class LinkedHashSet<E>
    extends HashSet<E>
    implements Set<E>, Cloneable, java.io.Serializable {

    private static final long serialVersionUID = -2851667679971038690L;
    
    public LinkedHashSet(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor, true);
    }
    public LinkedHashSet(int initialCapacity) {
        super(initialCapacity, .75f, true);
    }
    public LinkedHashSet() {
        super(16, .75f, true);
    }
    public LinkedHashSet(Collection<? extends E> c) {
        super(Math.max(2*c.size(), 11), .75f, true);
        addAll(c);
    }
    @Override
    public Spliterator<E> spliterator() {
        return Spliterators.spliterator(this, Spliterator.DISTINCT | Spliterator.ORDERED);
    }
}
```

结合上面分析的HashSet源码，我们能够很显然的知道 LinkedHashSet 是调用的 HashSet 中的构造方法，而HashSet 中的构造方法又调用了LinkedHashMap 中的构造方法，所以我们能够推测，LinkedHashSet 和 HashSet 有相同的特点，在此之上还对LinkedHashSet 中的各个属性用一个双向链表来进行连接。其本质上就是一个LinkedHashMap，只不过Map 的 Value 是在内部定义好的同一个Object

### PriorityQueue

PriorityQueue 是 AbstractQueue 的实现类，优先级队列的元素根据自然排序或者通过在构造函数时期提供Comparator 来排序，具体根据构造器判断。PriorityQueue 不允许 null 元素。

* 队列的头在某种意义上是指定顺序的最后一个元素。队列查找操作 poll,remove,peek 和 element 访问队列头部元素。
* 优先级队列是无限制的，但具有内部 capacity，用于控制用于在队列中存储元素的数组大小。
* 该类以及迭代器实现了 Collection、Iterator 接口的所有可选方法。这个迭代器提供了 `iterator()` 方法不能保证以任何特定顺序遍历优先级队列的元素。如果你需要有序遍历，考虑使用 `Arrays.sort(pq.toArray())`。
* 注意这个实现不是线程安全的，多线程不应该并发访问 PriorityQueue 实例如果有某个线程修改了队列的话，使用线程安全的类 `PriorityBlockingQueue`。

#### 源码分析

https://blog.csdn.net/u010623927/article/details/87179364

### HashMap

HashMap 是一个利用哈希表原理来存储元素的集合，并且允许空的 key-value 键值对。HashMap 是非线程安全的，也就是说在多线程的环境下，可能会存在问题，而 Hashtable 是线程安全的容器。HashMap 也支持 fail-fast 机制。HashMap 的实例有两个参数影响其性能：初始容量 和加载因子。可以使用 `Collections.synchronizedMap(new HashMap(...))` 来构造一个线程安全的 HashMap。

#### 源码分析

https://blog.csdn.net/Mr_changxin/article/details/123842070

### TreeMap 类

一个基于 NavigableMap 实现的红黑树。这个 map 根据 key 自然排序存储，或者通过 Comparator 进行定制排序。

* TreeMap 为 containsKey,get,put 和remove方法提供了 log(n) 的时间开销。
* 注意这个实现不是线程安全的。如果多线程并发访问 TreeMap，并且至少一个线程修改了 map，必须进行外部加锁。这通常通过在自然封装集合的某个对象上进行同步来实现，或者使用 `SortedMap m = Collections.synchronizedSortedMap(new TreeMap(...))`。
* 这个实现持有fail-fast机制。

#### 源码分析

https://blog.csdn.net/weixin_37576193/article/details/107783584

### LinkedHashMap 类

LinkedHashMap 是 Map 接口的哈希表和链表的实现。这个实现与 HashMap 不同之处在于它维护了一个贯穿其所有条目的双向链表。这个链表定义了遍历顺序，通常是插入 map 中的顺序。

* 它提供一个特殊的 LinkedHashMap(int,float,boolean) 构造器来创建 LinkedHashMap，其遍历顺序是其最后一次访问的顺序。
* 可以重写 removeEldestEntry(Map.Entry) 方法，以便在将新映射添加到 map 时强制删除过期映射的策略。
* 这个类提供了所有可选择的 map 操作，并且允许 null 元素。由于维护链表的额外开销，性能可能会低于HashMap，有一条除外：遍历 LinkedHashMap 中的 collection-views 需要与 map.size 成正比，无论其容量如何。HashMap 的迭代看起来开销更大，因为还要求时间与其容量成正比。
* LinkedHashMap 有两个因素影响了它的构成：初始容量和加载因子。
* 注意这个实现不是线程安全的。如果多线程并发访问LinkedHashMap，并且至少一个线程修改了map，必须进行外部加锁。这通常通过在自然封装集合的某个对象上进行同步来实现 `Map m = Collections.synchronizedMap(new LinkedHashMap(...))`。
* 这个实现持有fail-fast机制。

#### 源码分析

```java
package java.util;
import java.io.*;
 
 
public class LinkedHashMap<K,V>
    extends HashMap<K,V>
    implements Map<K,V>
{
 
    private static final long serialVersionUID = 3801124242820219131L;
 
	//双向循环链表的头结点，整个LinkedHa只哟shMap中只有一个header，
	//它将哈希表中所有的Entry贯穿起来，header中不保存key-value对，只保存前后节点的引用
    private transient Entry<K,V> header;
 
	//双向链表中元素排序规则的标志位。
	//accessOrder为false，表示按插入顺序排序
	//accessOrder为true，表示按访问顺序排序
    private final boolean accessOrder;
 
	//调用HashMap的构造方法来构造底层的数组
    public LinkedHashMap(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
        accessOrder = false;	//链表中的元素默认按照插入顺序排序
    }
 
	//加载因子取默认的0.75f
    public LinkedHashMap(int initialCapacity) {
		super(initialCapacity);
        accessOrder = false;
    }
 
	//加载因子取默认的0.75f，容量取默认的16
    public LinkedHashMap() {
		super();
        accessOrder = false;
    }
 
	//含有子Map的构造方法，同样调用HashMap的对应的构造方法
	public LinkedHashMap(Map<? extends K, ? extends V> m) {
        super(m);
        accessOrder = false;
    }
 
	//该构造方法可以指定链表中的元素排序的规则
    public LinkedHashMap(int initialCapacity,float loadFactor,boolean accessOrder) {
        super(initialCapacity, loadFactor);
        this.accessOrder = accessOrder;
    }
 
	//覆写父类的init()方法（HashMap中的init方法为空），
	//该方法在父类的构造方法和Clone、readObject中在插入元素前被调用，
	//初始化一个空的双向循环链表，头结点中不保存数据，头结点的下一个节点才开始保存数据。
    void init() {
        header = new Entry<K,V>(-1, null, null, null);
        header.before = header.after = header;
    }
 
 
	//覆写HashMap中的transfer方法，它在父类的resize方法中被调用，
	//扩容后，将key-value对重新映射到新的newTable中
	//覆写该方法的目的是为了提高复制的效率，
	//这里充分利用双向循环链表的特点进行迭代，不用对底层的数组进行for循环。
    void transfer(HashMap.Entry[] newTable) {
        int newCapacity = newTable.length;
        for (Entry<K,V> e = header.after; e != header; e = e.after) {
            int index = indexFor(e.hash, newCapacity);
            e.next = newTable[index];
            newTable[index] = e;
        }
    }
 
 
	//覆写HashMap中的containsValue方法，
	//覆写该方法的目的同样是为了提高查询的效率，
	//利用双向循环链表的特点进行查询，少了对数组的外层for循环
    public boolean containsValue(Object value) {
        // Overridden to take advantage of faster iterator
        if (value==null) {
            for (Entry e = header.after; e != header; e = e.after)
                if (e.value==null)
                    return true;
        } else {
            for (Entry e = header.after; e != header; e = e.after)
                if (value.equals(e.value))
                    return true;
        }
        return false;
    }
 
 
	//覆写HashMap中的get方法，通过getEntry方法获取Entry对象。
	//注意这里的recordAccess方法，
	//如果链表中元素的排序规则是按照插入的先后顺序排序的话，该方法什么也不做，
	//如果链表中元素的排序规则是按照访问的先后顺序排序的话，则将e移到链表的末尾处。
    public V get(Object key) {
        Entry<K,V> e = (Entry<K,V>)getEntry(key);
        if (e == null)
            return null;
        e.recordAccess(this);
        return e.value;
    }
 
	//清空HashMap，并将双向链表还原为只有头结点的空链表
    public void clear() {
        super.clear();
        header.before = header.after = header;
    }
 
	//Enty的数据结构，多了两个指向前后节点的引用
    private static class Entry<K,V> extends HashMap.Entry<K,V> {
        // These fields comprise the doubly linked list used for iteration.
        Entry<K,V> before, after;
 
		//调用父类的构造方法
		Entry(int hash, K key, V value, HashMap.Entry<K,V> next) {
            super(hash, key, value, next);
        }
 
		//双向循环链表中，删除当前的Entry
        private void remove() {
            before.after = after;
            after.before = before;
        }
 
		//双向循环立链表中，将当前的Entry插入到existingEntry的前面
        private void addBefore(Entry<K,V> existingEntry) {
            after  = existingEntry;
            before = existingEntry.before;
            before.after = this;
            after.before = this;
        }
 
 
		//覆写HashMap中的recordAccess方法（HashMap中该方法为空），
		//当调用父类的put方法，在发现插入的key已经存在时，会调用该方法，
		//调用LinkedHashmap覆写的get方法时，也会调用到该方法，
		//该方法提供了LRU算法的实现，它将最近使用的Entry放到双向循环链表的尾部，
		//accessOrder为true时，get方法会调用recordAccess方法
		//put方法在覆盖key-value对时也会调用recordAccess方法
		//它们导致Entry最近使用，因此将其移到双向链表的末尾
        void recordAccess(HashMap<K,V> m) {
            LinkedHashMap<K,V> lm = (LinkedHashMap<K,V>)m;
			//如果链表中元素按照访问顺序排序，则将当前访问的Entry移到双向循环链表的尾部，
			//如果是按照插入的先后顺序排序，则不做任何事情。
            if (lm.accessOrder) {
                lm.modCount++;
				//移除当前访问的Entry
                remove();
				//将当前访问的Entry插入到链表的尾部
                addBefore(lm.header);
            }
        }
 
        void recordRemoval(HashMap<K,V> m) {
            remove();
        }
    }
 
	//迭代器
    private abstract class LinkedHashIterator<T> implements Iterator<T> {
	Entry<K,V> nextEntry    = header.after;
	Entry<K,V> lastReturned = null;
 
	/**
	 * The modCount value that the iterator believes that the backing
	 * List should have.  If this expectation is violated, the iterator
	 * has detected concurrent modification.
	 */
	int expectedModCount = modCount;
 
	public boolean hasNext() {
            return nextEntry != header;
	}
 
	public void remove() {
	    if (lastReturned == null)
		throw new IllegalStateException();
	    if (modCount != expectedModCount)
		throw new ConcurrentModificationException();
 
            LinkedHashMap.this.remove(lastReturned.key);
            lastReturned = null;
            expectedModCount = modCount;
	}
 
	//从head的下一个节点开始迭代
	Entry<K,V> nextEntry() {
	    if (modCount != expectedModCount)
		throw new ConcurrentModificationException();
            if (nextEntry == header)
                throw new NoSuchElementException();
 
            Entry<K,V> e = lastReturned = nextEntry;
            nextEntry = e.after;
            return e;
	}
    }
 
	//key迭代器
    private class KeyIterator extends LinkedHashIterator<K> {
	public K next() { return nextEntry().getKey(); }
    }
 
	//value迭代器
    private class ValueIterator extends LinkedHashIterator<V> {
	public V next() { return nextEntry().value; }
    }
 
	//Entry迭代器
    private class EntryIterator extends LinkedHashIterator<Map.Entry<K,V>> {
	public Map.Entry<K,V> next() { return nextEntry(); }
    }
 
    // These Overrides alter the behavior of superclass view iterator() methods
    Iterator<K> newKeyIterator()   { return new KeyIterator();   }
    Iterator<V> newValueIterator() { return new ValueIterator(); }
    Iterator<Map.Entry<K,V>> newEntryIterator() { return new EntryIterator(); }
 
 
	//覆写HashMap中的addEntry方法，LinkedHashmap并没有覆写HashMap中的put方法，
	//而是覆写了put方法所调用的addEntry方法和recordAccess方法，
	//put方法在插入的key已存在的情况下，会调用recordAccess方法，
	//在插入的key不存在的情况下，要调用addEntry插入新的Entry
    void addEntry(int hash, K key, V value, int bucketIndex) {
		//创建新的Entry，并插入到LinkedHashMap中
        createEntry(hash, key, value, bucketIndex);
 
        //双向链表的第一个有效节点（header后的那个节点）为近期最少使用的节点
        Entry<K,V> eldest = header.after;
		//如果有必要，则删除掉该近期最少使用的节点，
		//这要看对removeEldestEntry的覆写,由于默认为false，因此默认是不做任何处理的。
        if (removeEldestEntry(eldest)) {
            removeEntryForKey(eldest.key);
        } else {
			//扩容到原来的2倍
            if (size >= threshold)
                resize(2 * table.length);
        }
    }
 
    void createEntry(int hash, K key, V value, int bucketIndex) {
		//创建新的Entry，并将其插入到数组对应槽的单链表的头结点处，这点与HashMap中相同
        HashMap.Entry<K,V> old = table[bucketIndex];
		Entry<K,V> e = new Entry<K,V>(hash, key, value, old);
        table[bucketIndex] = e;
		//每次插入Entry时，都将其移到双向链表的尾部，
		//这便会按照Entry插入LinkedHashMap的先后顺序来迭代元素，
		//同时，新put进来的Entry是最近访问的Entry，把其放在链表末尾 ，符合LRU算法的实现
        e.addBefore(header);
        size++;
    }
 
	//该方法是用来被覆写的，一般如果用LinkedHashmap实现LRU算法，就要覆写该方法，
	//比如可以将该方法覆写为如果设定的内存已满，则返回true，这样当再次向LinkedHashMap中put
	//Entry时，在调用的addEntry方法中便会将近期最少使用的节点删除掉（header后的那个节点）。
    protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
        return false;
    }
}
```

https://blog.csdn.net/ns_code/article/details/37867985

### Hashtable 类

Hashtable 类实现了一个哈希表，能够将键映射到值。任何非空对象都可以用作键或值。

* 此实现类支持 fail-fast 机制
* 与新的集合实现不同，Hashtable 是线程安全的。如果不需要线程安全的容器，推荐使用 HashMap，如果需要多线程高并发，推荐使用 `ConcurrentHashMap`。

#### 源码分析

https://blog.csdn.net/dingjianmin/article/details/79774192

### IdentityHashMap 类

IdentityHashMap 是比较小众的 Map 实现了。

* 这个类不是一个通用的 Map 实现！虽然这个类实现了 Map 接口，但它故意违反了 Map 的约定，该约定要求在比较对象时使用 equals 方法，此类仅适用于需要引用相等语义的极少数情况。
* 同 HashMap，IdentityHashMap 也是无序的，并且该类不是线程安全的，如果要使之线程安全，可以调用`Collections.synchronizedMap(new IdentityHashMap(...))`方法来实现。
* 支持fail-fast机制

#### 源码分析

https://blog.csdn.net/codejas/article/details/88698568

### WeakHashMap 类

WeakHashMap 类基于哈希表的 Map 基础实现，带有弱键。WeakHashMap 中的 entry 当不再使用时还会自动移除。更准确的说，给定key的映射的存在将不会阻止 key 被垃圾收集器丢弃。

* 基于 map 接口，是一种弱键相连，WeakHashMap 里面的键会自动回收
* 支持 null 值和 null 键。
* fast-fail 机制
* 不允许重复
* WeakHashMap 经常用作缓存

#### 源码分析

https://blog.csdn.net/tangtong1/article/details/88958668