# Spring MVC启动分析

## 简介

基于Spring Boot，围绕DispatcherServlet来进行分析。


## 寻找初始化源头

寻找Spring Boot对Tomcat的初始化，无非是看
`ServletWebServerApplicationContext`的`onRefresh`方法。
看Spring Boot createWebServer获取ServletWebServerFactory的bean并初始化WebServer这一Spring Boot web服务器封装。
然后留意TomcatWebServer的initialize（）方法。

而对于Spring MVC DispatcherServlet和相关组件的初始化，并非在tomcat服务器构建起时就做，而要从下面的分析中拉开帷幕。

我们来看看DispatcherServlet的继承关系：
![DispatcherServlet_inheritance.png](DispatcherServlet_inheritance.png)

接口Servlet的定义：
~~~
package javax.servlet;

public interface Servlet {

    public void init(ServletConfig config) throws ServletException;
    public void service(ServletRequest req, ServletResponse res)
            throws ServletException, IOException;
    public void destroy();
    
    //省略非核心接口……
}
~~~

Servlet是一个运行在Web服务器中的小Java程序。Servlet接口定义了初始化Servlet、处理请求、从server中剔除Servlet的这些生命周期方法。
调用顺序如下：
1. Servlet被初始化并调用init方法
2. service方法处理客户端请求
3. Servlet被剔除出服务中，那么会调用destroy，最终被垃圾收集器处理掉。



我们来关注SpringMVC初始化，
Servlet的实现DispatcherServlet初始化时，也会调用init方法，
首先来到的是`HttpServletBean`的init方法。


~~~ 
HttpServletBean.init()

		// Set bean properties from init parameters.
		//初始化web.xml中servlet标签设置的init-param。
		PropertyValues pvs = new ServletConfigPropertyValues(getServletConfig(), this.requiredProperties);
		if (!pvs.isEmpty()) {
			try {
			    //讲DispatcherServlet构造成BeanWrapper，并将resource设进去
			    //通过BeanWrapper的封装可以访问到 Servlet 的所有参数、资源加载器加载的资源
				BeanWrapper bw = PropertyAccessorFactory.forBeanPropertyAccess(this);
				ResourceLoader resourceLoader = new ServletContextResourceLoader(getServletContext());
				//构造属性editor
				bw.registerCustomEditor(Resource.class, new ResourceEditor(resourceLoader, getEnvironment()));
				initBeanWrapper(bw);
				//设置propertyvalue
				bw.setPropertyValues(pvs, true);
			}
			catch (BeansException ex) {
				if (logger.isErrorEnabled()) {
					logger.error("Failed to set bean properties on servlet '" + getServletName() + "'", ex);
				}
				throw ex;
			}
		}

		//供子类初始化的方法
		initServletBean();

~~~

接下来调用到FrameworkServlet的initServletBean方法。

~~~
org.springframework.web.servlet.FrameworkServlet

	@Override
	protected final void initServletBean() throws ServletException {
        //……
		try {
			this.webApplicationContext = initWebApplicationContext();
			initFrameworkServlet();
		}
		catch (ServletException | RuntimeException ex) {
			logger.error("Context initialization failed", ex);
			throw ex;
		}

        //……
	}
	
	protected WebApplicationContext initWebApplicationContext() {
		WebApplicationContext rootContext =
				WebApplicationContextUtils.getWebApplicationContext(getServletContext());
		WebApplicationContext wac = null;

        //省略……

		if (!this.refreshEventReceived) {
			// Either the context is not a ConfigurableApplicationContext with refresh
			// support or the context injected at construction time had already been
			// refreshed -> trigger initial onRefresh manually here.
			synchronized (this.onRefreshMonitor) {
		        //模板方法，web应用context刷新后做一些初始化，我们进入DispatcherServlet看其实现
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

~~~

上面调用onRefresh将会调用到实现类DispatcherServlet的实现。

~~~
DispatcherServlet

	/**
	 * 实现模板方法并调用initStrategies
	 * 
	 */
	@Override
	protected void onRefresh(ApplicationContext context) {
		initStrategies(context);
	}
	
	/**
	 * 初始化各种策略对象
	 * <p>May be overridden in subclasses in order to initialize further strategy objects.
	 */
	protected void initStrategies(ApplicationContext context) {
	    //初始化 处理multipart的resolver
		initMultipartResolver(context);
		//初始化 处理语言本地化的resolver
		initLocaleResolver(context);
		//初始化 主题resolver
		initThemeResolver(context);
		//请求和处理器映射的初始化
		initHandlerMappings(context);
		//处理器适配器，用于调用真正的处理器
		initHandlerAdapters(context);
		//处理器异常resolver，一般用这个做全局异常处理
		initHandlerExceptionResolvers(context);
		//请求与视图的匹配器。see：DefaultRequestToViewNameTranslator
		initRequestToViewNameTranslator(context);
		//视图解析器，解析例如“jsp:”这样的语法。
		initViewResolvers(context);
		//FlashMap简单来说就是一个HashMap，用于数据保存，比如，重定向前保存数据到FlashMap，然后重定向后拿出来。
		initFlashMapManager(context);
	}

~~~


## 总结


所以，我们来看看DispatcherServlet在初始化时做了什么：
1. HttpServletBean将servlet标签下的参数拿出来，并随同包装Servlet对象包装到BeanWrapper中去
2. FrameworkServlet初始化WebApplicationContext（如果还没有初始化和refresh）。
3. DispatcherServlet初始化各种策略对象

