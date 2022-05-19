## SpringMVC 初始化

首先来看继承树



![image-20220328164025118](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220328164025118.png)



### Servlet 接口

首先来看最核心的 Servlet 接口，以及相关定义的方法

```java
public interface Servlet {
    
    // 初始化方法，第一次调用这个Servlet 时会调用这个方法，之后就不会调用这个方法
    public void init(ServletConfig config) throws ServletException;
    // 这里得到传入的config，servlet 的配置文件
    public ServletConfig getServletConfig();
    // 处理请求的方法
    public void service(ServletRequest req, ServletResponse res)
	throws ServletException, IOException;
    // 得到 Servlet 中的相关信息
    public String getServletInfo();
    // 当Servlet被销毁的时候就执行这个方法
    public void destroy();
}
```

### GenericServlet 抽象类

这个抽象类主要是对Servlet 接口的中的方法进行了一个加强，添加了额外的功能，实现了Serializable 表示支持序列化，下面是对这个抽象类的一些核心方法进行分析。

```java
public abstract class GenericServlet 
    implements Servlet, ServletConfig, java.io.Serializable
{
    private static final String LSTRING_FILE = "javax.servlet.LocalStrings";
    private static ResourceBundle lStrings =
        ResourceBundle.getBundle(LSTRING_FILE);
	// 存储 ServletConfig 信息，transient 修饰表示不会被反序列化
    private transient ServletConfig config;
    // 官方注解表示这个方法不做任何事情，初始化都在 init方法中执行
    public GenericServlet() { }
    // 空的destory 方法
    public void destroy() {}
    
    // 下面两个方法都是初始化属性
    public String getInitParameter(String name) {
        ServletConfig sc = getServletConfig();
        if (sc == null) {
            throw new IllegalStateException(
                lStrings.getString("err.servlet_config_not_initialized"));
        }

        return sc.getInitParameter(name);
    }
    public Enumeration<String> getInitParameterNames() {
        ServletConfig sc = getServletConfig();
        if (sc == null) {
            throw new IllegalStateException(
                lStrings.getString("err.servlet_config_not_initialized"));
        }

        return sc.getInitParameterNames();
    }   
    // 获取 config
    public ServletConfig getServletConfig() {
		return config;
    }
    public ServletContext getServletContext() {
        ServletConfig sc = getServletConfig();
        if (sc == null) {
            throw new IllegalStateException(
                lStrings.getString("err.servlet_config_not_initialized"));
        }

        return sc.getServletContext();
    }
    // init 的重载方法，初始化 config 然后执行 init 方法
    public void init(ServletConfig config) throws ServletException {
		this.config = config;
		this.init();
    }
    // 这里不对init 方法进行实现，在其子类对其进行实现
    public void init() throws ServletException {}
    // service 使用了一个抽象类，让其子类来进行实现
    public abstract void service(ServletRequest req, ServletResponse res)
	throws ServletException, IOException;
    
```

这个抽象类主要作用就是对于参数的初始化。

### HttpServlet 抽象类

HttpServlet 抽象类继承了GenericServlet抽象类，对其进一步的完善，添加了新的功能，这里只分析其中的核心方法

```java
public abstract class HttpServlet extend GenericServlet{
    
    // 这里定义的一些常量，与后面的service 方法有关
    private static final String METHOD_DELETE = "DELETE";
    private static final String METHOD_HEAD = "HEAD";
    private static final String METHOD_GET = "GET";
    private static final String METHOD_OPTIONS = "OPTIONS";
    private static final String METHOD_POST = "POST";
    private static final String METHOD_PUT = "PUT";
    private static final String METHOD_TRACE = "TRACE";

    private static final String HEADER_IFMODSINCE = "If-Modified-Since";
    private static final String HEADER_LASTMOD = "Last-Modified";
    
    private static final String LSTRING_FILE =
        "javax.servlet.http.LocalStrings";
    private static ResourceBundle lStrings =
        ResourceBundle.getBundle(LSTRING_FILE);
    
    
    // 这里就是处理业务的入口，主要是对 req 和 res 来进行判断传入的参数是否正确
    public void service(ServletRequest req, ServletResponse res)
        throws ServletException, IOException
    {
        HttpServletRequest  request;
        HttpServletResponse response;
        
        if (!(req instanceof HttpServletRequest &&
                res instanceof HttpServletResponse)) {
            throw new ServletException("non-HTTP request or response");
        }

        request = (HttpServletRequest) req;
        response = (HttpServletResponse) res;
		// 这里调用的才是真正处理的核心方法
        service(request, response);
    }
    
    // service 方法，也就是真正处理业务的方法，在这个方法中，将获取到的请求中的method进行判断，根据method 的值将
    // 逻辑分发到相应的方法，也算是一个解耦的过程，将不同的method方法分离。
    protected void service(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException
    {
        String method = req.getMethod();

        if (method.equals(METHOD_GET)) {
            long lastModified = getLastModified(req);
            if (lastModified == -1) {
                // servlet doesn't support if-modified-since, no reason
                // to go through further expensive logic
                doGet(req, resp);
            } else {
                long ifModifiedSince = req.getDateHeader(HEADER_IFMODSINCE);
                if (ifModifiedSince < lastModified) {
                    // If the servlet mod time is later, call doGet()
                    // Round down to the nearest second for a proper compare
                    // A ifModifiedSince of -1 will always be less
                    maybeSetLastModified(resp, lastModified);
                    doGet(req, resp);
                } else {
                    resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                }
            }

        } else if (method.equals(METHOD_HEAD)) {
            long lastModified = getLastModified(req);
            maybeSetLastModified(resp, lastModified);
            doHead(req, resp);

        } else if (method.equals(METHOD_POST)) {
            doPost(req, resp);
            
        } else if (method.equals(METHOD_PUT)) {
            doPut(req, resp);
            
        } else if (method.equals(METHOD_DELETE)) {
            doDelete(req, resp);
            
        } else if (method.equals(METHOD_OPTIONS)) {
            doOptions(req,resp);
            
        } else if (method.equals(METHOD_TRACE)) {
            doTrace(req,resp);
            
        } else {
            //
            // Note that this means NO servlet supports whatever
            // method was requested, anywhere on this server.
            //

            String errMsg = lStrings.getString("http.method_not_implemented");
            Object[] errArgs = new Object[1];
            errArgs[0] = method;
            errMsg = MessageFormat.format(errMsg, errArgs);
            
            resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, errMsg);
        }
    }
    
    // 这里是通过反射来获取所有的方法
    private Method[] getAllDeclaredMethods(Class<?> c) {

        if (c.equals(javax.servlet.http.HttpServlet.class)) {
            return null;
        }

        Method[] parentMethods = getAllDeclaredMethods(c.getSuperclass());
        Method[] thisMethods = c.getDeclaredMethods();
        
        if ((parentMethods != null) && (parentMethods.length > 0)) {
            Method[] allMethods =
                new Method[parentMethods.length + thisMethods.length];
            System.arraycopy(parentMethods, 0, allMethods, 0,
                             parentMethods.length);
            System.arraycopy(thisMethods, 0, allMethods, parentMethods.length,
                             thisMethods.length);

            thisMethods = allMethods;
        }

        return thisMethods;
    }
}
```

这个抽象类就是在GenericServlet 中初始化后，对业务的处理进行了实现，将service 方法根据 method 进行分发。如果子类没有重写这些 do* 方法，但是有请求到达到了这里，这里面的方法就会返回 Error。



### HttpServletBean 抽象类

分析完前面的接口和抽象类之后我们会发现，其实前面三个类是 JavaWeb 的基石，好像并没有看见SpringMvc 相关的方法，从这个抽象类开始就是和 SpringMvc 有关的了。这个抽象类中的核心方法就在这个 init 方法中

HttpServlet 的简单扩展，它将其配置参数（web.xml 中的 servlet 标记内的 init-param 条目）视为 bean 属性。任何类型的 servlet 的方便超类。配置参数的类型转换是自动的，使用转换后的值调用相应的 setter 方法。子类也可以指定所需的属性。没有匹配 bean 属性设置器的参数将被简单地忽略。此 servlet 将请求处理留给子类，继承 HttpServlet 的默认行为（doGet、doPost 等）。这个通用 servlet 基类不依赖于 Spring org.springframework.context.ApplicationContext 概念。简单的 servlet 通常不加载自己的上下文，而是从 Spring 根应用程序上下文访问服务 bean，可通过过滤器的 ServletContext 访问（参见org.springframework.web.context.support.WebApplicationContextUtils）。 FrameworkServlet 类是一个更具体的 servlet 基类，它加载自己的应用程序上下文。 FrameworkServlet 作为 Spring 成熟的 DispatcherServlet 的直接基类。另请参阅：addRequiredProperty、initServletBean、doGet、doPost

```java
public abstract class HttpServletBean extends HttpServlet implements EnvironmentCapable, EnvironmentAware {
	
	
    public final void init() throws ServletException {
        // 1. 操作配置文件 web.xml 里的属性，后面我们进入到这个方法进行分析
        PropertyValues pvs = new ServletConfigPropertyValues(getServletConfig(),this.requiredProperties);

        if (!pvs.isEmpty()) {
            try {
                // 2.获取目标对象的beanwrapper对象
                BeanWrapper bw = PropertyAccessorFactory.forBeanPropertyAccess(this);
                // 3.创建 ResourceLoader
                ResourceLoader resourceLoader = new ServletContextResourceLoader(getServletContext());
                // 4.将 ResourceLoader 加入到 beanWrapper对象中
                bw.registerCustomEditor(Resource.class, new ResourceEditor(resourceLoader, getEnvironment()));
                // 空实现。模板方法，可以在子类中调用，做一些初始化工作，bw代表DispatcherServelt
                initBeanWrapper(bw);
                // 5.将 PropertyValues 添加到 BeanWrapper 
                bw.setPropertyValues(pvs, true);
                }catch (BeansException ex) {
                    throw ex;
                }
               }
        // 空方法 让子类实现
        initServletBean();

    }
    /*
    此方法的两个参数：
    ServletConfig就是servlet的配置对象，通过他可以获取到web.xml里的参数
    requiredProperties 就是一些我们指定必须存在servlet参数名
    PropertyValue 对象
    此时 name = "contextConfigLocation"   value=" classpath:springmvc.xml"

    */

    public ServletConfigPropertyValues(ServletConfig config, Set<String> requiredProperties)throws ServletException {

        Set<String> missingProps = (!CollectionUtils.isEmpty(requiredProperties) ?new HashSet<>(requiredProperties) : null);
        // 1. 获取web.xml里springmvc的属性
        Enumeration<String> paramNames = config.getInitParameterNames();
        while (paramNames.hasMoreElements()) {
            // 标签名称 web.xml 里的 <param-name>
            String property = paramNames.nextElement();
            // 标签值  web.xml 里的 <param-value>
            Object value = config.getInitParameter(property);
            // 2.将上面获取到的key-value封装进 perpertyValue 对象
            addPropertyValue(new PropertyValue(property, value));

            if (missingProps != null) {
                missingProps.remove(property);
            }
        }
        if (!CollectionUtils.isEmpty(missingProps)) {
            // 一些异常处理操作    
        }
    }
}
```

这里就开始对 web.xml 中的配置信息进行分析了，将获取到的配置信息保存，并且在 init 的最后还调用 了```initServletBean()```这个方法，这个方法在initServletBean 中实现



### FrameworkServlet 抽象类

首先我们来观察官方的注解

```java
/*
Spring Web 框架的基本 servlet。在基于 JavaBean 的整体解决方案中提供与 Spring 应用程序上下文的集成。此类提供以下功能： 管理每个 servlet 的 WebApplicationContext 实例。 servlet 的配置由 servlet 命名空间中的 bean 确定。在请求处理时发布事件，无论请求是否成功处理。子类必须实现 doService 来处理请求。因为这扩展了 HttpServletBean 而不是直接扩展 HttpServlet，所以 bean 属性会自动映射到它上面。子类可以覆盖 initFrameworkServlet() 以进行自定义初始化。在 servlet init-param 级别检测“contextClass”参数，如果未找到，则回退到默认上下文类 XmlWebApplicationContext。请注意，使用默认的 FrameworkServlet，自定义上下文类需要实现 ConfigurableWebApplicationContext SPI。接受一个可选的“contextInitializerClasses”servlet init-param，它指定一个或多个 ApplicationContextInitializer 类。托管的 Web 应用程序上下文将委托给这些初始化程序，允许进行额外的编程配置，例如针对上下文环境添加属性源或激活配置文件。另请参阅支持“contextInitializerClasses”上下文参数的 ContextLoader，它与“根”Web 应用程序上下文具有相同的语义。将“contextConfigLocation”servlet init-param 传递给上下文实例，将其解析为可能的多个文件路径，这些路径可以用任意数量的逗号和空格分隔，例如“test-servlet.xml，myServlet.xml”。如果没有明确指定，上下文实现应该从 servlet 的命名空间构建一个默认位置。注意：在多个配置位置的情况下，以后的 bean 定义将覆盖之前加载的文件中定义的那些，至少在使用 Spring 的默认 ApplicationContext 实现时是这样。这可以用来通过额外的 XML 文件故意覆盖某些 bean 定义。默认命名空间是“'servlet-name'-servlet”，例如servlet 名称为“test”的“test-servlet”（导致带有 XmlWebApplicationContext 的“WEB-INFtest-servlet.xml”默认位置）。命名空间也可以通过“命名空间”servlet init-param 显式设置。从 Spring 3.1 开始，FrameworkServlet 现在可以注入 Web 应用程序上下文，而不是在内部创建自己的上下文。这在支持 servlet 实例的编程注册的 Servlet 3.0+ 环境中很有用。有关详细信息，请参阅 FrameworkServlet(WebApplicationContext) Javadoc。另请参阅：doService、setContextClass、setContextConfigLocation、setContextInitializerClasses、setNamespace

*/
public abstract class FrameworkServlet extends HttpServletBean implements ApplicationContextAware {
	// 一堆属性
    
    
    
    // 这个方法就重写了HttpServletBean 中的 initServletBean 方法，更进一步的初始化
    @Override
	protected final void initServletBean() throws ServletException {
		getServletContext().log("Initializing Spring " + getClass().getSimpleName() + " '" + getServletName() + "'");
		if (logger.isInfoEnabled()) {
			logger.info("Initializing Servlet '" + getServletName() + "'");
		}
		long startTime = System.currentTimeMillis();

		try {
			this.webApplicationContext = initWebApplicationContext();
			initFrameworkServlet();
		}
		catch (ServletException | RuntimeException ex) {
			logger.error("Context initialization failed", ex);
			throw ex;
		}

		if (logger.isDebugEnabled()) {
			String value = this.enableLoggingRequestDetails ?
					"shown which may lead to unsafe logging of potentially sensitive data" :
					"masked to prevent unsafe logging of potentially sensitive data";
			logger.debug("enableLoggingRequestDetails='" + this.enableLoggingRequestDetails +
					"': request parameters and headers will be " + value);
		}

		if (logger.isInfoEnabled()) {
			logger.info("Completed initialization in " + (System.currentTimeMillis() - startTime) + " ms");
		}
	}
    
    protected WebApplicationContext initWebApplicationContext() {
        // 1、获取到根容器
		WebApplicationContext rootContext =
				WebApplicationContextUtils.getWebApplicationContext(getServletContext());
        // 2、webApplicationContext
		WebApplicationContext wac = null;
		// 3、如果已经存在了 wac
		if (this.webApplicationContext != null) {
			// A context instance was injected at construction time -> use it
			wac = this.webApplicationContext;
			if (wac instanceof ConfigurableWebApplicationContext) {
				ConfigurableWebApplicationContext cwac = (ConfigurableWebApplicationContext) wac;
				if (!cwac.isActive()) {
					// The context has not yet been refreshed -> provide services such as
					// setting the parent context, setting the application context id, etc
					if (cwac.getParent() == null) {
						// The context instance was injected without an explicit parent -> set
						// the root application context (if any; may be null) as the parent
						cwac.setParent(rootContext);
					}
					configureAndRefreshWebApplicationContext(cwac);
				}
			}
		}
        // 不存在就从 WebApplicationContextUtils 这个工具类中的方法来进行
		if (wac == null) {
			// No context instance was injected at construction time -> see if one
			// has been registered in the servlet context. If one exists, it is assumed
			// that the parent context (if any) has already been set and that the
			// user has performed any initialization such as setting the context id
			wac = findWebApplicationContext();
		}
        // 还不在就创建一个 wac
		if (wac == null) {
			// No context instance is defined for this servlet -> create a local one
			wac = createWebApplicationContext(rootContext);
		}

		if (!this.refreshEventReceived) {
			// Either the context is not a ConfigurableApplicationContext with refresh
			// support or the context injected at construction time had already been
			// refreshed -> trigger initial onRefresh manually here.
			synchronized (this.onRefreshMonitor) {
                // 最终会执行到这个方法中去
				onRefresh(wac);
			}
		}

		if (this.publishContext) {
			// Publish the context as a servlet context attribute.
			String attrName = getServletContextAttributeName();
			getServletContext().setAttribute(attrName, wac);
		}

		return wac;
	}
    
    @Nullable
	protected WebApplicationContext findWebApplicationContext() {
		String attrName = getContextAttribute();
		if (attrName == null) {
			return null;
		}
		WebApplicationContext wac =
				WebApplicationContextUtils.getWebApplicationContext(getServletContext(), attrName);
		if (wac == null) {
			throw new IllegalStateException("No WebApplicationContext found: initializer not registered?");
		}
		return wac;
	}
    // 空的方法，这个方法在在后面最核心的 DispatcherServlet 来进行实现的
	protected void onRefresh(ApplicationContext context) {
		// For subclasses: do nothing by default.
	}

}
```

从这里我们就不难看出，FrameworkServlet 的主要作用就是创建一个 webApplicationContext，给子类留下了一个 onRefresh 方法。



### DispatcherServlet 类

这个类的重要性相信大家都非常清楚，这也是SpringMVC 中最最核心的类，我们来看官方给的定义

```java
/*
HTTP 请求处理程序控制器的中央调度程序，例如用于 Web UI 控制器或基于 HTTP 的远程服务导出器。分派给已注册的处理程序以处理 Web 请求，提供方便的映射和异常处理设施。这个 servlet 非常灵活：它可以与几乎任何工作流一起使用，只需安装适当的适配器类。它提供了以下与其他请求驱动的 Web MVC 框架不同的功能： 它基于 JavaBeans 配置机制。它可以使用任何 HandlerMapping 实现——预先构建或作为应用程序的一部分提供——来控制请求到处理程序对象的路由。默认是 org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping 和 org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping。 HandlerMapping 对象可以在 servlet 的应用程序上下文中定义为 bean，实现 HandlerMapping 接口，覆盖默认的 HandlerMapping（如果存在）。可以为 HandlerMappings 指定任何 bean 名称（它们按类型进行测试）。它可以使用任何 HandlerAdapter；这允许使用任何处理程序接口。默认适配器是 org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter、org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter，用于 Spring 的 org.springframework.web.HttpRequestHandler 和 org.springframework.web.servlet.mvc.Controller 接口，分别。默认的 org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter 也将被注册。 HandlerAdapter 对象可以作为bean 添加到应用程序上下文中，覆盖默认的HandlerAdapters。与 HandlerMappings 一样，可以为 HandlerAdapters 指定任何 bean 名称（它们按类型进行测试）。调度程序的异常解决策略可以通过 HandlerExceptionResolver 指定，例如将某些异常映射到错误页面。默认为 org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver、org.springframework.web.servlet.mvc.annotation.ResponseStatusExceptionResolver 和 org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver。这些 HandlerExceptionResolver 可以通过应用程序上下文覆盖。 HandlerExceptionResolver 可以被赋予任何 bean 名称（它们按类型进行测试）。它的视图解析策略可以通过 ViewResolver 实现指定，将符号视图名称解析为视图对象。默认是 org.springframework.web.servlet.view.InternalResourceViewResolver。 ViewResolver 对象可以作为 bean 添加到应用程序上下文中，覆盖默认的 ViewResolver。 ViewResolvers 可以被赋予任何 bean 名称（它们按类型进行测试）。如果用户未提供视图或视图名称，则配置的 RequestToViewNameTranslator 会将当前请求转换为视图名称。对应的bean名称为“viewNameTranslator”；默认是 org.springframework.web.servlet.view.DefaultRequestToViewNameTranslator。调度程序解决多部分请求的策略由 MultipartResolver 实现确定。包括 Apache Commons FileUpload 和 Servlet 3 的实现；典型的选择是 org.springframework.web.multipart.commons.CommonsMultipartResolver。 MultipartResolver bean 名称是“multipartResolver”；默认为无。其语言环境解析策略由 LocaleResolver 确定。开箱即用的实现通过 HTTP 接受标头、cookie 或会话工作。 LocaleResolver bean 名称是“localeResolver”；默认是 org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver。其主题解析策略由 ThemeResolver 确定。包括固定主题和 cookie 和会话存储的实现。 ThemeResolver bean 名称是“themeResolver”；默认是 org.springframework.web.servlet.theme.FixedThemeResolver。注意：@RequestMapping 注释只有在调度程序中存在相应的 HandlerMapping（用于类型级注释）和或 HandlerAdapter（用于方法级注释）时才会被处理。默认情况下是这种情况。但是，如果您要定义自定义 HandlerMappings 或 HandlerAdapter，那么您需要确保也定义了相应的自定义 RequestMappingHandlerMapping 和或 RequestMappingHandlerAdapter - 前提是您打算使用 @RequestMapping。 Web 应用程序可以定义任意数量的 DispatcherServlet。每个 servlet 将在自己的命名空间中运行，使用映射、处理程序等加载自己的应用程序上下文。只有由 org.springframework.web.context.ContextLoaderListener 加载的根应用程序上下文（如果有）将被共享。从 Spring 3.1 开始，DispatcherServlet 现在可以注入 Web 应用程序上下文，而不是在内部创建自己的上下文。这在支持 servlet 实例的编程注册的 Servlet 3.0+ 环境中很有用。有关详细信息，请参阅 DispatcherServlet(WebApplicationContext) javadoc。另见：org.springframework.web.HttpRequestHandler、org.springframework.web.servlet.mvc.Controller、org.springframework.web.context.ContextLoaderListener

*/

public class DispatcherServlet extends FrameworkServlet {
    
    // 这里是重写了 FrameworkServlet 抽象类中的 onRefresh 方法
    @Override
	protected void onRefresh(ApplicationContext context) {
        // 实际上调用的方法
		initStrategies(context);
	}

	/**
	 * 在这里初始化SpringMVC 中的八大组件
	 * Initialize the strategy objects that this servlet uses.
	 * <p>May be overridden in subclasses in order to initialize further strategy objects.
	 */
	protected void initStrategies(ApplicationContext context) {
		initMultipartResolver(context);
		initLocaleResolver(context);
		initThemeResolver(context);
		initHandlerMappings(context);
		initHandlerAdapters(context);
		initHandlerExceptionResolvers(context);
		initRequestToViewNameTranslator(context);
		initViewResolvers(context);
		initFlashMapManager(context);
	}
    
    
}
```



到这里，SpringMVC的初始化方法就完成了，其中还有非常多的细节这里就不再一一赘述了



## 中央调度器核心方法 doDispatch

之前写过一篇博客关于 doDispatch 方法，所以此处只总结一些概念

### [请求处理源码分析](https://blog.csdn.net/Mr_changxin/article/details/121547883)

### SpringMVC流程

1、 用户发送请求至前端控制器DispatcherServlet。

2、 DispatcherServlet收到请求调用HandlerMapping处理器映射器。

3、 处理器映射器找到具体的处理器(可以根据xml配置、注解进行查找)，生成处理器对象及处理器拦截器(如果有则生成)一并返回给DispatcherServlet。

4、 DispatcherServlet调用HandlerAdapter处理器适配器。

5、 HandlerAdapter经过适配调用具体的处理器(Controller，也叫后端控制器)。

6、 Controller执行完成返回ModelAndView。

7、 HandlerAdapter将controller执行结果ModelAndView返回给DispatcherServlet。

8、 DispatcherServlet将ModelAndView传给ViewReslover视图解析器。

9、 ViewReslover解析后返回具体View。

10、DispatcherServlet根据View进行渲染视图（即将模型数据填充至视图中）。

11、 DispatcherServlet响应用户。

### 组件说明：

以下组件通常使用框架提供实现：

DispatcherServlet：作为前端控制器，整个流程控制的中心，控制其它组件执行，统一调度，降低组件之间的耦合性，提高每个组件的扩展性。

HandlerMapping：通过扩展处理器映射器实现不同的映射方式，例如：配置文件方式，实现接口方式，注解方式等。 

HandlAdapter：通过扩展处理器适配器，支持更多类型的处理器。

ViewResolver：通过扩展视图解析器，支持更多类型的视图解析，例如：jsp、freemarker、pdf、excel等。

**组件：**
**1、前端控制器DispatcherServlet（不需要工程师开发）,由框架提供**
作用：接收请求，响应结果，相当于转发器，中央处理器。有了dispatcherServlet减少了其它组件之间的耦合度。
用户请求到达前端控制器，它就相当于mvc模式中的c，dispatcherServlet是整个流程控制的中心，由它调用其它组件处理用户的请求，dispatcherServlet的存在降低了组件之间的耦合性。

**2、处理器映射器HandlerMapping(不需要工程师开发),由框架提供**
作用：根据请求的url查找Handler
HandlerMapping负责根据用户请求找到Handler即处理器，springmvc提供了不同的映射器实现不同的映射方式，例如：配置文件方式，实现接口方式，注解方式等。

**3、处理器适配器HandlerAdapter**
作用：按照特定规则（HandlerAdapter要求的规则）去执行Handler
通过HandlerAdapter对处理器进行执行，这是适配器模式的应用，通过扩展适配器可以对更多类型的处理器进行执行。

**4、处理器Handler(需要工程师开发)**
**注意：编写Handler时按照HandlerAdapter的要求去做，这样适配器才可以去正确执行Handler**
Handler 是继DispatcherServlet前端控制器的后端控制器，在DispatcherServlet的控制下Handler对具体的用户请求进行处理。
由于Handler涉及到具体的用户业务请求，所以一般情况需要工程师根据业务需求开发Handler。

**5、视图解析器View resolver(不需要工程师开发),由框架提供**
作用：进行视图解析，根据逻辑视图名解析成真正的视图（view）
View Resolver负责将处理结果生成View视图，View Resolver首先根据逻辑视图名解析成物理视图名即具体的页面地址，再生成View视图对象，最后对View进行渲染将处理结果通过页面展示给用户。 springmvc框架提供了很多的View视图类型，包括：jstlView、freemarkerView、pdfView等。
一般情况下需要通过页面标签或页面模版技术将模型数据通过页面展示给用户，需要由工程师根据业务需求开发具体的页面。

**6、视图View(需要工程师开发jsp...)**
View是一个接口，实现类支持不同的View类型（jsp、freemarker、pdf...）

### **核心架构的具体流程步骤如下：**

1、首先用户发送请求——>DispatcherServlet，前端控制器收到请求后自己不进行处理，而是委托给其他的解析器进行处理，作为统一访问点，进行全局的流程控制；
2、DispatcherServlet——>HandlerMapping， HandlerMapping 将会把请求映射为HandlerExecutionChain 对象（包含一个Handler 处理器（页面控制器）对象、多个HandlerInterceptor 拦截器对象，通过这种策略模式，很容易添加新的映射策略；
3、DispatcherServlet——>HandlerAdapter，HandlerAdapter 将会把处理器包装为适配器，从而支持多种类型的处理器，即适配器设计模式的应用，从而很容易支持很多类型的处理器；
4、HandlerAdapter——>处理器功能处理方法的调用，HandlerAdapter 将会根据适配的结果调用真正的处理器的功能处理方法，完成功能处理；并返回一个ModelAndView 对象（包含模型数据、逻辑视图名）；
5、ModelAndView的逻辑视图名——> ViewResolver， ViewResolver 将把逻辑视图名解析为具体的View，通过这种策略模式，很容易更换其他视图技术；
6、View——>渲染，View会根据传进来的Model模型数据进行渲染，此处的Model实际是一个Map数据结构，因此很容易支持其他视图技术；
7、返回控制权给DispatcherServlet，由DispatcherServlet返回响应给用户，到此一个流程结束。

**下边两个组件通常情况下需要开发：**

1. Handler：处理器，即后端控制器用controller表示。

2. View：视图，即展示给用户的界面，视图中通常需要标签语言展示模型数据。



### 总体流程图

![image-20220328205429713](C:\Users\24314\AppData\Roaming\Typora\typora-user-images\image-20220328205429713.png)