## **1. refresh方法**

```java
public void refresh() throws BeansException, IllegalStateException {
		synchronized (this.startupShutdownMonitor) {
			// Prepare this context for refreshing.
            // 容器刷新前的准备工作
			prepareRefresh();

			// Tell the subclass to refresh the internal bean factory.
            // 加载 xml 配置文件的属性值到当前工厂中，最重要的是 BeanDefinition
			ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();
			// 此时除了 BeanDefinition 有值，其他属性都没有值，所以需要给 beanFactory 的属性进行填充
			// Prepare the bean factory for use in this context.
            // beanFactory 的准备工作，对各种属性进行填充
			prepareBeanFactory(beanFactory);

			try {
				// Allows post-processing of the bean factory in context subclasses.
                // 模板方法，设计模式
				postProcessBeanFactory(beanFactory);

				// Invoke factory processors registered as beans in the context.
                // 调用 BeanFactoryPostProcessors 方法
				invokeBeanFactoryPostProcessors(beanFactory);

				// Register bean processors that intercept bean creation.
                // 注册 bean 处理器，这里只是注册功能，真正调用的是 getBean 方法
				registerBeanPostProcessors(beanFactory);

				// Initialize message source for this context.
                // 国际化
				initMessageSource();

				// Initialize event multicaster for this context.
                // 初始化事件监听和多路广播
				initApplicationEventMulticaster();

				// Initialize other special beans in specific context subclasses.
                // 给后续接口进行实现
				onRefresh();

				// Check for listener beans and register them.
                // 注册监听器
				registerListeners();

				// Instantiate all remaining (non-lazy-init) singletons.
                // 完成Bean 初始化
				finishBeanFactoryInitialization(beanFactory);

				// Last step: publish corresponding event.
				finishRefresh();
			}

			catch (BeansException ex) {
				if (logger.isWarnEnabled()) {
					logger.warn("Exception encountered during context initialization - " +
							"cancelling refresh attempt: " + ex);
				}

				// Destroy already created singletons to avoid dangling resources.
				destroyBeans();

				// Reset 'active' flag.
				cancelRefresh(ex);

				// Propagate exception to caller.
				throw ex;
			}

			finally {
				// Reset common introspection caches in Spring's core, since we
				// might not ever need metadata for singleton beans anymore...
				resetCommonCaches();
			}
		}
	}

```

### 1.prepareRefresh 方法

```java
protected void prepareRefresh() {
		// Switch to active.
		// 设置容器启动的时间
		this.startupDate = System.currentTimeMillis();
		// 容器的关闭标志位
		this.closed.set(false);
		// 容器的激活标志位
		this.active.set(true);

		// 记录日志
		if (logger.isDebugEnabled()) {
			if (logger.isTraceEnabled()) {
				logger.trace("Refreshing " + this);
			}
			else {
				logger.debug("Refreshing " + getDisplayName());
			}
		}

		// Initialize any placeholder property sources in the context environment.
		// 留给子类覆盖，初始化属性资源
		initPropertySources();

		// Validate that all properties marked as required are resolvable:
		// see ConfigurablePropertyResolver#setRequiredProperties
		// 创建并获取环境对象，验证需要的属性文件是否都已经放入环境中
		getEnvironment().validateRequiredProperties();

		// Store pre-refresh ApplicationListeners...
		// 判断刷新前的应用程序监听器集合是否为空，如果为空，则将监听器添加到此集合中
		if (this.earlyApplicationListeners == null) {
			this.earlyApplicationListeners = new LinkedHashSet<>(this.applicationListeners);
		}
		else {
			// Reset local application listeners to pre-refresh state.
			// 如果不等于空，则清空集合元素对象
			this.applicationListeners.clear();
			this.applicationListeners.addAll(this.earlyApplicationListeners);
		}

		// Allow for the collection of early ApplicationEvents,
		// to be published once the multicaster is available...
		// 创建刷新前的监听事件集合
		this.earlyApplicationEvents = new LinkedHashSet<>();
	}

```



### 2. obtainFreshBeanFactory 方法

```java

	protected ConfigurableListableBeanFactory obtainFreshBeanFactory() {
		refreshBeanFactory();
		return getBeanFactory();
	}

// refreshBeanFactory();

	@Override
	protected final void refreshBeanFactory() throws BeansException {
		// 如果存在beanFactory，则销毁beanFactory
		if (hasBeanFactory()) {
			destroyBeans();
			closeBeanFactory();
		}
		try {
			// 创建DefaultListableBeanFactory对象
			DefaultListableBeanFactory beanFactory = createBeanFactory();
			// 为了序列化指定id，可以从id反序列化到beanFactory对象
			beanFactory.setSerializationId(getId());
			// 定制beanFactory，设置相关属性，包括是否允许覆盖同名称的不同定义的对象以及循环依赖
			customizeBeanFactory(beanFactory);
			// 初始化documentReader,并进行XML文件读取及解析,默认命名空间的解析，自定义标签的解析
			loadBeanDefinitions(beanFactory);
			this.beanFactory = beanFactory;
		}
		catch (IOException ex) {
			throw new ApplicationContextException("I/O error parsing bean definition source for " + getDisplayName(), ex);
		}
	}

// loadBeanDefinitions

	protected void loadBeanDefinitions(DefaultListableBeanFactory beanFactory) throws BeansException, IOException {
		// Create a new XmlBeanDefinitionReader for the given BeanFactory.
		// 创建一个xml的beanDefinitionReader，并通过回调设置到beanFactory中
		XmlBeanDefinitionReader beanDefinitionReader = new XmlBeanDefinitionReader(beanFactory);

		// Configure the bean definition reader with this context's
		// resource loading environment.
		// 给reader对象设置环境对象
		beanDefinitionReader.setEnvironment(this.getEnvironment());
		beanDefinitionReader.setResourceLoader(this);
		beanDefinitionReader.setEntityResolver(new ResourceEntityResolver(this));

		// Allow a subclass to provide custom initialization of the reader,
		// then proceed with actually loading the bean definitions.
		//  初始化beanDefinitionReader对象，此处设置配置文件是否要进行验证
		initBeanDefinitionReader(beanDefinitionReader);
		// 开始完成beanDefinition的加载
		loadBeanDefinitions(beanDefinitionReader);
	}
```



### 3. prepareBeanFactory 

到这一步时 IOC 容器中 BeanDefinition 的值，其他值并没有赋值，此方法就是赋值

```java
	protected void prepareBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		// Tell the internal bean factory to use the context's class loader etc.
		// 设置beanFactory的classloader为当前context的classloader
		beanFactory.setBeanClassLoader(getClassLoader());
		// 设置beanfactory的表达式语言处理器
		beanFactory.setBeanExpressionResolver(new StandardBeanExpressionResolver(beanFactory.getBeanClassLoader()));
		// 为beanFactory增加一个默认的propertyEditor，这个主要是对bean的属性等设置管理的一个工具类
		beanFactory.addPropertyEditorRegistrar(new ResourceEditorRegistrar(this, getEnvironment()));

		// Configure the bean factory with context callbacks.
		// 添加beanPostProcessor,ApplicationContextAwareProcessor此类用来完成某些Aware对象的注入
		beanFactory.addBeanPostProcessor(new ApplicationContextAwareProcessor(this));
		// 设置要忽略自动装配的接口，很多同学理解不了为什么此处要对这些接口进行忽略，原因非常简单，这些接口的实现是由容器通过set方法进行注入的，
		// 所以在使用autowire进行注入的时候需要将这些接口进行忽略
		beanFactory.ignoreDependencyInterface(EnvironmentAware.class);
		beanFactory.ignoreDependencyInterface(EmbeddedValueResolverAware.class);
		beanFactory.ignoreDependencyInterface(ResourceLoaderAware.class);
		beanFactory.ignoreDependencyInterface(ApplicationEventPublisherAware.class);
		beanFactory.ignoreDependencyInterface(MessageSourceAware.class);
		beanFactory.ignoreDependencyInterface(ApplicationContextAware.class);

		// BeanFactory interface not registered as resolvable type in a plain factory.
		// MessageSource registered (and found for autowiring) as a bean.
		// 设置几个自动装配的特殊规则,当在进行ioc初始化的如果有多个实现，那么就使用指定的对象进行注入
		beanFactory.registerResolvableDependency(BeanFactory.class, beanFactory);
		beanFactory.registerResolvableDependency(ResourceLoader.class, this);
		beanFactory.registerResolvableDependency(ApplicationEventPublisher.class, this);
		beanFactory.registerResolvableDependency(ApplicationContext.class, this);

		// Register early post-processor for detecting inner beans as ApplicationListeners.
		// 注册BPP
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(this));

		// Detect a LoadTimeWeaver and prepare for weaving, if found.
		// 增加对AspectJ的支持，在java中织入分为三种方式，分为编译器织入，类加载器织入，运行期织入，编译器织入是指在java编译器，采用特殊的编译器，将切面织入到java类中，
		// 而类加载期织入则指通过特殊的类加载器，在类字节码加载到JVM时，织入切面，运行期织入则是采用cglib和jdk进行切面的织入
		// aspectj提供了两种织入方式，第一种是通过特殊编译器，在编译器，将aspectj语言编写的切面类织入到java类中，第二种是类加载期织入，就是下面的load time weaving，此处后续讲
		if (beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
			beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
			// Set a temporary ClassLoader for type matching.
			beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
		}

		// Register default environment beans.
		// 注册默认的系统环境bean到一级缓存中
		if (!beanFactory.containsLocalBean(ENVIRONMENT_BEAN_NAME)) {
			beanFactory.registerSingleton(ENVIRONMENT_BEAN_NAME, getEnvironment());
		}
		if (!beanFactory.containsLocalBean(SYSTEM_PROPERTIES_BEAN_NAME)) {
			beanFactory.registerSingleton(SYSTEM_PROPERTIES_BEAN_NAME, getEnvironment().getSystemProperties());
		}
		if (!beanFactory.containsLocalBean(SYSTEM_ENVIRONMENT_BEAN_NAME)) {
			beanFactory.registerSingleton(SYSTEM_ENVIRONMENT_BEAN_NAME, getEnvironment().getSystemEnvironment());
		}
	}
```



### 4. postProcessBeanFactory 方法

模板方法的体现，供子类去做扩展

```java
	protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
	
    }

```

### 5.invokeBeanFactoryPostProcessors 方法

```java
	protected void invokeBeanFactoryPostProcessors(ConfigurableListableBeanFactory beanFactory) {
		// 获取到当前应用程序上下文的beanFactoryPostProcessors变量的值，并且实例化调用执行所有已经注册的beanFactoryPostProcessor
		// 默认情况下，通过getBeanFactoryPostProcessors()来获取已经注册的BFPP，但是默认是空的，那么问题来了，如果你想扩展，怎么进行扩展工作？
		PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors(beanFactory, getBeanFactoryPostProcessors());

		// Detect a LoadTimeWeaver and prepare for weaving, if found in the meantime
		// (e.g. through an @Bean method registered by ConfigurationClassPostProcessor)
		if (beanFactory.getTempClassLoader() == null && beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
			beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
			beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
		}
	}



	// 具体实现
    public static void invokeBeanFactoryPostProcessors(
            ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

        // Invoke BeanDefinitionRegistryPostProcessors first, if any.
        // 无论是什么情况，优先执行BeanDefinitionRegistryPostProcessors
        // 将已经执行过的BFPP存储在processedBeans中，防止重复执行
        Set<String> processedBeans = new HashSet<>();

        // 判断beanfactory是否是BeanDefinitionRegistry类型，此处是DefaultListableBeanFactory,实现了BeanDefinitionRegistry接口，所以为true
        if (beanFactory instanceof BeanDefinitionRegistry) {
            // 类型转换
            BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
            // 此处希望大家做一个区分，两个接口是不同的，BeanDefinitionRegistryPostProcessor是BeanFactoryPostProcessor的子集
            // BeanFactoryPostProcessor主要针对的操作对象是BeanFactory，而BeanDefinitionRegistryPostProcessor主要针对的操作对象是BeanDefinition
            // 存放BeanFactoryPostProcessor的集合
            List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();
            // 存放BeanDefinitionRegistryPostProcessor的集合
            List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>();

            // 首先处理入参中的beanFactoryPostProcessors,遍历所有的beanFactoryPostProcessors，将BeanDefinitionRegistryPostProcessor
            // 和BeanFactoryPostProcessor区分开
            for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
                // 如果是BeanDefinitionRegistryPostProcessor
                if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
                    BeanDefinitionRegistryPostProcessor registryProcessor =
                            (BeanDefinitionRegistryPostProcessor) postProcessor;
                    // 直接执行BeanDefinitionRegistryPostProcessor接口中的postProcessBeanDefinitionRegistry方法
                    registryProcessor.postProcessBeanDefinitionRegistry(registry);
                    // 添加到registryProcessors，用于后续执行postProcessBeanFactory方法
                    registryProcessors.add(registryProcessor);
                } else {
                    // 否则，只是普通的BeanFactoryPostProcessor，添加到regularPostProcessors，用于后续执行postProcessBeanFactory方法
                    regularPostProcessors.add(postProcessor);
                }
            }

            // Do not initialize FactoryBeans here: We need to leave all regular beans
            // uninitialized to let the bean factory post-processors apply to them!
            // Separate between BeanDefinitionRegistryPostProcessors that implement
            // PriorityOrdered, Ordered, and the rest.
            // 用于保存本次要执行的BeanDefinitionRegistryPostProcessor
            List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

            // First, invoke the BeanDefinitionRegistryPostProcessors that implement PriorityOrdered.
            // 调用所有实现PriorityOrdered接口的BeanDefinitionRegistryPostProcessor实现类
            // 找到所有实现BeanDefinitionRegistryPostProcessor接口bean的beanName
            String[] postProcessorNames =
                    beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
            // 遍历处理所有符合规则的postProcessorNames
            for (String ppName : postProcessorNames) {
                // 检测是否实现了PriorityOrdered接口
                if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
                    // 获取名字对应的bean实例，添加到currentRegistryProcessors中
                    currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
                    // 将要被执行的BFPP名称添加到processedBeans，避免后续重复执行
                    processedBeans.add(ppName);
                }
            }
            // 按照优先级进行排序操作
            sortPostProcessors(currentRegistryProcessors, beanFactory);
            // 添加到registryProcessors中，用于最后执行postProcessBeanFactory方法
            registryProcessors.addAll(currentRegistryProcessors);
            // 遍历currentRegistryProcessors，执行postProcessBeanDefinitionRegistry方法
            invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
            // 执行完毕之后，清空currentRegistryProcessors
            currentRegistryProcessors.clear();

            // Next, invoke the BeanDefinitionRegistryPostProcessors that implement Ordered.
            // 调用所有实现Ordered接口的BeanDefinitionRegistryPostProcessor实现类
            // 找到所有实现BeanDefinitionRegistryPostProcessor接口bean的beanName，
            // 此处需要重复查找的原因在于上面的执行过程中可能会新增其他的BeanDefinitionRegistryPostProcessor
            postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
            for (String ppName : postProcessorNames) {
                // 检测是否实现了Ordered接口，并且还未执行过
                if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
                    // 获取名字对应的bean实例，添加到currentRegistryProcessors中
                    currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
                    // 将要被执行的BFPP名称添加到processedBeans，避免后续重复执行
                    processedBeans.add(ppName);
                }
            }
            // 按照优先级进行排序操作
            sortPostProcessors(currentRegistryProcessors, beanFactory);
            // 添加到registryProcessors中，用于最后执行postProcessBeanFactory方法
            registryProcessors.addAll(currentRegistryProcessors);
            // 遍历currentRegistryProcessors，执行postProcessBeanDefinitionRegistry方法
            invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
            // 执行完毕之后，清空currentRegistryProcessors
            currentRegistryProcessors.clear();

            // Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear.
            // 最后，调用所有剩下的BeanDefinitionRegistryPostProcessors
            boolean reiterate = true;
            while (reiterate) {
                reiterate = false;
                // 找出所有实现BeanDefinitionRegistryPostProcessor接口的类
                postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
                // 遍历执行
                for (String ppName : postProcessorNames) {
                    // 跳过已经执行过的BeanDefinitionRegistryPostProcessor
                    if (!processedBeans.contains(ppName)) {
                        // 获取名字对应的bean实例，添加到currentRegistryProcessors中
                        currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
                        // 将要被执行的BFPP名称添加到processedBeans，避免后续重复执行
                        processedBeans.add(ppName);
                        reiterate = true;
                    }
                }
                // 按照优先级进行排序操作
                sortPostProcessors(currentRegistryProcessors, beanFactory);
                // 添加到registryProcessors中，用于最后执行postProcessBeanFactory方法
                registryProcessors.addAll(currentRegistryProcessors);
                // 遍历currentRegistryProcessors，执行postProcessBeanDefinitionRegistry方法
                invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
                // 执行完毕之后，清空currentRegistryProcessors
                currentRegistryProcessors.clear();
            }

            // Now, invoke the postProcessBeanFactory callback of all processors handled so far.
            // 调用所有BeanDefinitionRegistryPostProcessor的postProcessBeanFactory方法
            invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
            // 最后，调用入参beanFactoryPostProcessors中的普通BeanFactoryPostProcessor的postProcessBeanFactory方法
            invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
        } else {
            // Invoke factory processors registered with the context instance.
            // 如果beanFactory不归属于BeanDefinitionRegistry类型，那么直接执行postProcessBeanFactory方法
            invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
        }

        // 到这里为止，入参beanFactoryPostProcessors和容器中的所有BeanDefinitionRegistryPostProcessor已经全部处理完毕，下面开始处理容器中
        // 所有的BeanFactoryPostProcessor
        // 可能会包含一些实现类，只实现了BeanFactoryPostProcessor，并没有实现BeanDefinitionRegistryPostProcessor接口

        // Do not initialize FactoryBeans here: We need to leave all regular beans
        // uninitialized to let the bean factory post-processors apply to them!
        // 找到所有实现BeanFactoryPostProcessor接口的类
        String[] postProcessorNames =
                beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

        // Separate between BeanFactoryPostProcessors that implement PriorityOrdered,
        // Ordered, and the rest.
        // 用于存放实现了PriorityOrdered接口的BeanFactoryPostProcessor
        List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
        // 用于存放实现了Ordered接口的BeanFactoryPostProcessor的beanName
//		List<String> orderedPostProcessorNames = new ArrayList<>();
        List<BeanFactoryPostProcessor> orderedPostProcessor = new ArrayList<>();
        // 用于存放普通BeanFactoryPostProcessor的beanName
//		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
        List<BeanFactoryPostProcessor> nonOrderedPostProcessorNames = new ArrayList<>();
        // 遍历postProcessorNames,将BeanFactoryPostProcessor按实现PriorityOrdered、实现Ordered接口、普通三种区分开
        for (String ppName : postProcessorNames) {
            // 跳过已经执行过的BeanFactoryPostProcessor
            if (processedBeans.contains(ppName)) {
                // skip - already processed in first phase above
            }
            // 添加实现了PriorityOrdered接口的BeanFactoryPostProcessor到priorityOrderedPostProcessors
            else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
                priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
            }
            // 添加实现了Ordered接口的BeanFactoryPostProcessor的beanName到orderedPostProcessorNames
            else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
//				orderedPostProcessorNames.add(ppName);
                orderedPostProcessor.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
            } else {
                // 添加剩下的普通BeanFactoryPostProcessor的beanName到nonOrderedPostProcessorNames
//				nonOrderedPostProcessorNames.add(ppName);
                nonOrderedPostProcessorNames.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
            }
        }

        // First, invoke the BeanFactoryPostProcessors that implement PriorityOrdered.
        // 对实现了PriorityOrdered接口的BeanFactoryPostProcessor进行排序
        sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
        // 遍历实现了PriorityOrdered接口的BeanFactoryPostProcessor，执行postProcessBeanFactory方法
        invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

        // Next, invoke the BeanFactoryPostProcessors that implement Ordered.
        // 创建存放实现了Ordered接口的BeanFactoryPostProcessor集合
//		List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
        // 遍历存放实现了Ordered接口的BeanFactoryPostProcessor名字的集合
//		for (String postProcessorName : orderedPostProcessorNames) {
        // 将实现了Ordered接口的BeanFactoryPostProcessor添加到集合中
//			orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
//		}
        // 对实现了Ordered接口的BeanFactoryPostProcessor进行排序操作
//		sortPostProcessors(orderedPostProcessors, beanFactory);
        sortPostProcessors(orderedPostProcessor, beanFactory);
        // 遍历实现了Ordered接口的BeanFactoryPostProcessor，执行postProcessBeanFactory方法
//		invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);
        invokeBeanFactoryPostProcessors(orderedPostProcessor, beanFactory);

        // Finally, invoke all other BeanFactoryPostProcessors.
        // 最后，创建存放普通的BeanFactoryPostProcessor的集合
//		List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
        // 遍历存放实现了普通BeanFactoryPostProcessor名字的集合
//		for (String postProcessorName : nonOrderedPostProcessorNames) {
        // 将普通的BeanFactoryPostProcessor添加到集合中
//			nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
//		}
        // 遍历普通的BeanFactoryPostProcessor，执行postProcessBeanFactory方法
//		invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);
        invokeBeanFactoryPostProcessors(nonOrderedPostProcessorNames, beanFactory);

        // Clear cached merged bean definitions since the post-processors might have
        // modified the original metadata, e.g. replacing placeholders in values...
        // 清除元数据缓存（mergeBeanDefinitions、allBeanNamesByType、singletonBeanNameByType）
        // 因为后置处理器可能已经修改了原始元数据，例如，替换值中的占位符
        beanFactory.clearMetadataCache();
    }
```

### 6. 监听器、广播、国际化方法

```java
// 为上下文初始化message源，即不同语言的消息体，国际化处理,在springmvc的时候通过国际化的代码重点讲
				initMessageSource();

				// Initialize event multicaster for this context.
				// 初始化事件监听多路广播器
				initApplicationEventMulticaster();

				// Initialize other special beans in specific context subclasses.
				// 留给子类来初始化其他的bean
				onRefresh();

				// Check for listener beans and register them.
				// 在所有注册的bean中查找listener bean,注册到消息广播器中
				registerListeners();
```

### 7. finishBeanFactoryInitialization 方法

```java
	/**
	 * Finish the initialization of this context's bean factory,
	 * initializing all remaining singleton beans.
	 */
	protected void finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory) {
		// Initialize conversion service for this context.
		// 为上下文初始化类型转换器
		if (beanFactory.containsBean(CONVERSION_SERVICE_BEAN_NAME) &&
				beanFactory.isTypeMatch(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class)) {
			beanFactory.setConversionService(
					beanFactory.getBean(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class));
		}

		// Register a default embedded value resolver if no bean post-processor
		// (such as a PropertyPlaceholderConfigurer bean) registered any before:
		// at this point, primarily for resolution in annotation attribute values.
		// 如果beanFactory之前没有注册嵌入值解析器，则注册默认的嵌入值解析器，主要用于注解属性值的解析
		if (!beanFactory.hasEmbeddedValueResolver()) {
			beanFactory.addEmbeddedValueResolver(strVal -> getEnvironment().resolvePlaceholders(strVal));
		}

		// Initialize LoadTimeWeaverAware beans early to allow for registering their transformers early.
		// 尽早初始化loadTimeWeaverAware bean,以便尽早注册它们的转换器
		String[] weaverAwareNames = beanFactory.getBeanNamesForType(LoadTimeWeaverAware.class, false, false);
		for (String weaverAwareName : weaverAwareNames) {
			getBean(weaverAwareName);
		}

		// Stop using the temporary ClassLoader for type matching.
		// 禁止使用临时类加载器进行类型匹配
		beanFactory.setTempClassLoader(null);

		// Allow for caching all bean definition metadata, not expecting further changes.
		// 冻结所有的bean定义，说明注册的bean定义将不被修改或任何进一步的处理
		beanFactory.freezeConfiguration();

		// Instantiate all remaining (non-lazy-init) singletons.
		// 实例化剩下的单例对象
		beanFactory.preInstantiateSingletons();
	}

public void preInstantiateSingletons() throws BeansException {
		if (logger.isTraceEnabled()) {
			logger.trace("Pre-instantiating singletons in " + this);
		}

		// Iterate over a copy to allow for init methods which in turn register new bean definitions.
		// While this may not be part of the regular factory bootstrap, it does otherwise work fine.
		List<String> beanNames = new ArrayList<>(this.beanDefinitionNames);

		// Trigger initialization of all non-lazy singleton beans...
		for (String beanName : beanNames) {
            // 拿到所有的 BeanDefinition
			RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);
            // 判断这个 bean 是否为 非抽象的、单例、非懒加载的
			if (!bd.isAbstract() && bd.isSingleton() && !bd.isLazyInit()) {
                // 如果是通过 FactoryBean 来创建 Bean 就执行这个 if 逻辑
				if (isFactoryBean(beanName)) {
					Object bean = getBean(FACTORY_BEAN_PREFIX + beanName);
					if (bean instanceof FactoryBean) {
						final FactoryBean<?> factory = (FactoryBean<?>) bean;
						boolean isEagerInit;
						if (System.getSecurityManager() != null && factory instanceof SmartFactoryBean) {
							isEagerInit = AccessController.doPrivileged((PrivilegedAction<Boolean>)
											((SmartFactoryBean<?>) factory)::isEagerInit,
									getAccessControlContext());
						}
						else {
							isEagerInit = (factory instanceof SmartFactoryBean &&
									((SmartFactoryBean<?>) factory).isEagerInit());
						}
						if (isEagerInit) {
							getBean(beanName);
						}
					}
				}
				else {
                    // 一般情况下是执行这个 getBean 方法来进行实例化
                    // -> doGetBean -> createBean 
					getBean(beanName);
				}
			}
		}

		// Trigger post-initialization callback for all applicable beans...
		for (String beanName : beanNames) {
			Object singletonInstance = getSingleton(beanName);
			if (singletonInstance instanceof SmartInitializingSingleton) {
				final SmartInitializingSingleton smartSingleton = (SmartInitializingSingleton) singletonInstance;
				if (System.getSecurityManager() != null) {
					AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
						smartSingleton.afterSingletonsInstantiated();
						return null;
					}, getAccessControlContext());
				}
				else {
					smartSingleton.afterSingletonsInstantiated();
				}
			}
		}
	}

```





容器和对象的创建流程

1、先创建容器

2、读取配置文件或注解进行解析

3、执行```BeanFactoryPostProcessor ```方法

- 准备```BeanPostProcessor```
- 国际化方法
- 准备监听器事件和广播器

4、实例化

5、初始化

6、获取到完整对象

## **2. 自定义一个简单的IOC容器**

```java
public class LcxApplicationContext {

    private Class configClass;
    // 需要实例化的class对象
    private List<Class> classList;
    private Map<String,BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<>();   // BeanDefinition
    private Map<String,Object> singletonObjects = new ConcurrentHashMap<>();    // 单例池
    private List<BeanPostProcessor>  beanPostProcessorList = new LinkedList();

    public LcxApplicationContext(Class configClass){
        this.configClass = configClass;
        classList = new LinkedList<>();

        // 扫描
        scan(configClass);
        // 解析
        initBeanDefinition();
        // 基于class 去创建
        instanceSingletonBean();
    }


    /**
     * 扫描路径，根据 ComponentScan 中传入的路径，进行扫描，如果被component修饰，就将需要管理的bean对象的class 对象存到 classList 中去
     * @param configClass
     */
    private void scan(Class configClass) {
        // 扫描
        ComponentScan annotation = (ComponentScan) configClass.getAnnotation(ComponentScan.class);
        String path = annotation.value();
        path = path.replace(".","/");

        ClassLoader loader = LcxApplicationContext.class.getClassLoader();
        URL url = loader.getResource(path);
        File file = new File(url.getFile());
        if(file.isDirectory()){
            File[] files = file.listFiles();
            for(File f:files){
                String absolutePath = f.getAbsolutePath();
                absolutePath = absolutePath.substring(absolutePath.indexOf("com"), absolutePath.indexOf(".class"));
                absolutePath = absolutePath.replace("\\",".");
                System.out.println(absolutePath);
                try {
                    Class<?> aClass = loader.loadClass(absolutePath);
                    if(aClass.isAnnotationPresent(Component.class))
                        classList.add(aClass);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 通过扫描过后，对List<Class> 中的对象进行解析，对该类中使用的注解进行解析
     */
    private void initBeanDefinition() {
        for(Class clazz:classList){

            if(BeanPostProcessor.class.isAssignableFrom(clazz)){
                try {
                    BeanPostProcessor instance = (BeanPostProcessor) clazz.getDeclaredConstructor().newInstance();
                    beanPostProcessorList.add(instance);
                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                }
            }

            BeanDefinition beanDefinition = new BeanDefinition();

            Component component = (Component) clazz.getAnnotation(Component.class);
            String beanName = component.value();

            if(clazz.isAnnotationPresent(Scope.class)){
                Scope scopeAnnotation = (Scope) clazz.getAnnotation(Scope.class);
                beanDefinition.setScope(scopeAnnotation.value());
            } else {
                beanDefinition.setScope("singleton");
            }
            // Lazy 类似
            beanDefinition.setBeanClass(clazz);
            beanDefinitionMap.put(beanName,beanDefinition);
        }
    }

    /**
     * 如果是单例，就进行实例化，初始化单例池
     */
    private void instanceSingletonBean(){
        for (String beanName:beanDefinitionMap.keySet()){
            BeanDefinition beanDefinition = beanDefinitionMap.get(beanName);
            // 如果是单例的,就尝试拿，如果没有拿到，就尝试创建
            if(beanDefinition.getScope().equals("singleton")){
                if(!singletonObjects.containsKey(beanName)){
                    Object bean = doCreateBean(beanName,beanDefinition);
                    singletonObjects.put(beanName,bean);
                }

            }
        }
    }

    /**
     * 开始创建Bean对象
     * @param beanName bean 名字
     * @param beanDefinition
     * @return
     */
    private Object doCreateBean(String beanName, BeanDefinition beanDefinition) {

        Class beanClass = beanDefinition.getBeanClass();
        Object o = null;
        try {
            // 1. 实例化
            o = beanClass.getDeclaredConstructor().newInstance();
            // 2. 属性填充
            Field[] declaredFields = beanClass.getDeclaredFields();
            for(Field field:declaredFields){
                if(field.isAnnotationPresent(Autowired.class)){
                    Object bean = getBean(field.getName());
                    field.setAccessible(true);
                    field.set(o,bean);
                }
            }
            // aware
            if(o instanceof BeanNameAware){
                BeanNameAware aware = (BeanNameAware) o;
                ((BeanNameAware) o).setBeanName(beanName);
            }
            // 初始化前
            for (BeanPostProcessor beanPostProcessor : beanPostProcessorList) {
                o = beanPostProcessor.postProcessBeforeInitialization(o,beanName);
            }
            // 初始化
            if(o instanceof InitializingBean){
                ((InitializingBean)o).afterPropertiesSet();
            }
            // 初始化后
            for (BeanPostProcessor beanPostProcessor : beanPostProcessorList) {
                o = beanPostProcessor.postProcessAfterInitialization(o,beanName);
            }

        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }

        return o;
    }
    public Object getBean(String name){
        BeanDefinition beanDefinition = beanDefinitionMap.get(name);
        if(beanDefinition.getScope().equals("prototype")){
            // 创建 bean
            Object bean = doCreateBean(name,beanDefinition);
            return bean;
        }else if(beanDefinition.getScope().equals("singleton")){
            // 单例池中找
            Object bean =  singletonObjects.get(name);
            if(bean == null){
                Object bean1 = doCreateBean(name, beanDefinition);
                singletonObjects.put(name,bean1);
                return bean1;
            }
            return bean;
        }
        return null;
    }

}
```

## 3. 循环依赖问题

根本原因：属性间的赋值

属性赋值有两种方式：1、构造器方式		2、set 方法赋值

构造器方法不能解决循环依赖问题，用set 方法赋值能够解决（三级缓存）

实例化和初始化分开

### 1. Bean对象创建过程

![](C:\Users\24314\Pictures\spring\循环依赖.png) 

### 2.循环依赖问题分析

![](C:\Users\24314\Pictures\spring\三级缓存问题.png)



我们可以这样来将对象分类

- 成品 -----> 完整对象

- 半成品 -----> 实例化完成但是没有初始化完成



添加了一个**半成品 Map** 来进行存储半成品对象，**达到解开闭环的目的**。并且**还有一个成品对象 Map 来放置已经初始化好了的对象**

此时就有了两级缓存了，但是为什么 Spring 是三级缓存呢 ？

### 3. 三级缓存

三级缓存 ------> 三个 Map 结构

```java
public class DefaultSingletonBeanRegistry extends SimpleAliasRegistry implements SingletonBeanRegistry {

	/**
	 * 一级缓存
	 * 用于保存BeanName和创建bean实例之间的关系
	 *
	 * Cache of singleton objects: bean name to bean instance. */
//	private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);
	public final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

	/**
	 * 三级缓存
	 * 用于保存BeanName和创建bean的工厂之间的关系
	 *
	 * Cache of singleton factories: bean name to ObjectFactory. */
//	private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);
	public final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);

	/**
	 * 二级缓存
	 * 保存BeanName和创建bean实例之间的关系，与singletonFactories的不同之处在于，当一个单例bean被放到这里之后，那么当bean还在创建过程中
	 * 就可以通过getBean方法获取到，可以方便进行循环依赖的检测
	 *
	 * Cache of early singleton objects: bean name to bean instance. */
//	private final Map<String, Object> earlySingletonObjects = new ConcurrentHashMap<>(16);
	public final Map<String, Object> earlySingletonObjects = new ConcurrentHashMap<>(16);

	/**
	 * 用来保存当前所有已经注册的bean
	 *
	 * Set of registered singletons, containing the bean names in registration order. */
//	private final Set<String> registeredSingletons = new LinkedHashSet<>(256);
	public final Set<String> registeredSingletons = new LinkedHashSet<>(256);

	/**
	 * 正在创建过程中的beanName集合
	 *
	 * Names of beans that are currently in creation. */
	private final Set<String> singletonsCurrentlyInCreation =
			Collections.newSetFromMap(new ConcurrentHashMap<>(16));

	/**
	 * 当前在创建检查中排除的bean名
	 *
	 * Names of beans currently excluded from in creation checks. */
	private final Set<String> inCreationCheckExclusions =
			Collections.newSetFromMap(new ConcurrentHashMap<>(16));

	/**
	 * 抑制的异常列表，可用于关联相关原因
	 *
	 * Collection of suppressed Exceptions, available for associating related causes. */
	@Nullable
	private Set<Exception> suppressedExceptions;
}
```

- singletonObjects 一级缓存
- earySingletonObject 二级缓存
- singletonFactories 三级缓存    （函数式接口）



### 4. 创建流程

```
finishBeanFactoryInitialization --> beanFactory.preInstantiateSingletons(); --> getBean(beanName);
--> doGetBean --->sharedInstance --> getSingleton(beanName, () -> {try {return createBean(beanName, mbd, args);} --> singletonObject = singletonFactory.getObject(); --> createBean(beanName, mbd, args); --> doCreateBean(beanName, mbdToUse, args); --> createBeanInstance(beanName, mbd, args); --> addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean)); --> populateBean(beanName, mbd, instanceWrapper); --> applyPropertyValues(beanName, mbd, bw, pvs); --> valueResolver.resolveValueIfNecessary(pv, originalValue); --> resolveReference(argName, ref); --> bean = this.beanFactory.getBean(resolvedName); --> doGetBean
```

RunTimeBeanReference



放入三级缓存代码

```java
	protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(singletonFactory, "Singleton factory must not be null");
		synchronized (this.singletonObjects) {
			if (!this.singletonObjects.containsKey(beanName)) {
				this.singletonFactories.put(beanName, singletonFactory);
				this.earlySingletonObjects.remove(beanName);
				this.registeredSingletons.add(beanName);
			}
		}
	}
```

例如现在有对象 A 和对象 B 相互依赖

主要流程就是先创建A，在创建A的过程中，执行getBean(A)方法，并且在创建的过程中将一些回调方法保存到三级缓存中去，先对A进行实例化，对A中的属性进行赋值时发现B对象没有创建，此时调用 getBea(B) 这个时候的A就是处于半创建状态，在getBean的过程中，跟A一样实例化，直到再给B对象赋值时又再次进行调用getBean(A) 方法，在调用getBean 方法的时候会进行一个判断，判断A 是否是处于创建过程中，如果是，就将A加入到二级缓存中去，最终B就能够得到半初始化的A，此刻B对象就创建好了，将创建好的B对象放到一级缓存中去第二次的getBean(B) 方法就拿到了创建好了B对象，再将这个对象赋值给A对象中的属性，最终就解决了循环依赖问题。



## 4. 常见问题

### 1. 三级缓存都分别保存的是什么对象

- 一级：成品对象
- 二级：半成品对象
- 三级：lambda表达式

### 2. 如果只使用一级缓存 行不行

不行，因为半成品对象和成品对象会放到一起，在进行对象获取的时候可能获取到半成品对象

### 3. 如果只有二级缓存行不行

只有二级缓存的时候也可以解决循环依赖的问题

添加AOP 的实现之后会报错：没有使用最终版本的Bean 对象

只有 getSingleto(String beanName,boolean allowEarlyReference) 和 doCreateBean(beanName,bean)

### 4. 三级缓存的存在，到底做了什么事？

如果一个对象需要被代理，生成代理对象，那么这个对象需要一线生成非代理对象吗？**需要**

lambda：getEarlyBeanReference() 只要搞清楚这个方法的具体执行逻辑即可



首先从第三级缓存说起（就是key是BeanName，Value为ObjectFactory）。我们的对象是单例的，有可能A对象依赖的B对象是有AOP的（B对象需要代理）假设没有第三级缓存，只有第二级缓存（Value存对象，而不是工厂对象）。那如果有AOP的情况下，岂不是在存入第二级缓存之前都需要先去做AOP代理？这不合适嘛。这里肯定是需要考虑代理的情况的，比如A对象是一个被AOP增量的对象，B依赖A时，得到的A肯定是代理对象的。所以，三级缓存的Value是ObjectFactory，可以从里边拿到代理对象。而二级缓存存在的必要就是为了性能，从三级缓存的工厂里创建出对象，再扔到二级缓存（这样就不用每次都要从工厂里拿）



在当前方法中，有可能会用代理对象替换非代理对象，如果没有三级缓存的话，那么就无法得到代理对象，换句话说，在整个容器中，包含了同名对象的代理对象和非代理对象，这是不可以的，容器中，对象都是单例的，根据名称只能获取一个对象的值，此时如果同时存在两个的话，使用哪一个？无法判断， 谁也无法确认会调用当前对象，是在其他对象的执行过程中来进行调用的，而不是认为指定的，所以必须要保证容器中任何时候都只有一个对象供外部调用，所以在三级缓存中，完成了一件代理对象替换非代理对象的工作，确定返回的是唯一的对象



三级缓存是为了解决在AOP代理过程中产生的循环依赖问题，如果没有AOP的话，二级缓存就够了



相当于是一个回调机制，当我需要使用当前对象的时候，会判断此对象是否需要被代理实现，如果直接替换，不需要直接返回非代理对象即可

[为什么是三级缓存](https://blog.csdn.net/lan861698789/article/details/109554429)

![](https://pic2.zhimg.com/v2-11b3ab224a03b0b6514179ab6d4ee59f_r.jpg?source=1940ef5c)

![](https://img-blog.csdnimg.cn/20201107235653162.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2xhbjg2MTY5ODc4OQ==,size_16,color_FFFFFF,t_70)

![](https://img-blog.csdnimg.cn/a2c60e4414db4142b634e8a658565e5e.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA5pet5pel55qE6Iqs6Iqz,size_20,color_FFFFFF,t_70,g_se,x_16)

