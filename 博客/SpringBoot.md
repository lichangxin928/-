## SpringBoot



### 1. 加载配置文件核心方法

```java

	public SpringApplication(ResourceLoader resourceLoader, Class<?>... primarySources) {
        // 初始化 resourceLoader，此时传入的恒为 null
		this.resourceLoader = resourceLoader;
        // 判断 primarySources 是否为空，这里是primarySources 对象是指传入的class 对象
		Assert.notNull(primarySources, "PrimarySources must not be null");
        // 将获取到的 primarySources 转换为一个 HashSet
		this.primarySources = new LinkedHashSet<>(Arrays.asList(primarySources));
        // 这里得到的是WebApp 的类型，如 Servlet 或者 webflux
		this.webApplicationType = WebApplicationType.deduceFromClasspath();
		this.bootstrapRegistryInitializers = new ArrayList<>(
            	得到SpringFactory 实例
				getSpringFactoriesInstances(BootstrapRegistryInitializer.class));
        // 加载 META-INF/spring.factories 下的configuration文件
		setInitializers((Collection) getSpringFactoriesInstances(ApplicationContextInitializer.class));
        // 加载监听器
		setListeners((Collection) getSpringFactoriesInstances(ApplicationListener.class));
        // 得到主类信息
		this.mainApplicationClass = deduceMainApplicationClass();
	}
```



### 2. run 方法核心过程

```java
	public ConfigurableApplicationContext run(String... args) {
       	// 开始计时
		long startTime = System.nanoTime();
        // 创建根容器
		DefaultBootstrapContext bootstrapContext = createBootstrapContext();
		ConfigurableApplicationContext context = null;
		configureHeadlessProperty();
		SpringApplicationRunListeners listeners = getRunListeners(args);
        // 加载监听器
		listeners.starting(bootstrapContext, this.mainApplicationClass);
		try {
            // 将args包装成 DefaultApplicationArguments 对象
			ApplicationArguments applicationArguments = new DefaultApplicationArguments(args);
            // 处理配置数据
			ConfigurableEnvironment environment = prepareEnvironment(listeners, bootstrapContext, applicationArguments);
			configureIgnoreBeanInfo(environment);
            // 启动时打印banner
			Banner printedBanner = printBanner(environment);
            // 获取当前上下文对象
			context = createApplicationContext();
			context.setApplicationStartup(this.applicationStartup);
            // 准备此时的上下文
			prepareContext(bootstrapContext, context, environment, listeners, applicationArguments, printedBanner);
            // 启动 spring 容器 Refresh方法
			refreshContext(context);
			afterRefresh(context, applicationArguments);
			Duration timeTakenToStartup = Duration.ofNanos(System.nanoTime() - startTime);
			if (this.logStartupInfo) {
				new StartupInfoLogger(this.mainApplicationClass).logStarted(getApplicationLog(), timeTakenToStartup);
			}
			listeners.started(context, timeTakenToStartup);
			callRunners(context, applicationArguments);
		}
		catch (Throwable ex) {
			handleRunFailure(context, ex, listeners);
			throw new IllegalStateException(ex);
		}
		try {
			Duration timeTakenToReady = Duration.ofNanos(System.nanoTime() - startTime);
			listeners.ready(context, timeTakenToReady);
		}
		catch (Throwable ex) {
			handleRunFailure(context, ex, null);
			throw new IllegalStateException(ex);
		}
		return context;
	}
```

