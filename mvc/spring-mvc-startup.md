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
			    //讲DispatcherServlet构造成BeanWrapper，并将resource
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
		if (wac == null) {
			// No context instance was injected at construction time -> see if one
			// has been registered in the servlet context. If one exists, it is assumed
			// that the parent context (if any) has already been set and that the
			// user has performed any initialization such as setting the context id
			wac = findWebApplicationContext();
		}
		if (wac == null) {
			// No context instance is defined for this servlet -> create a local one
			wac = createWebApplicationContext(rootContext);
		}

		if (!this.refreshEventReceived) {
			// Either the context is not a ConfigurableApplicationContext with refresh
			// support or the context injected at construction time had already been
			// refreshed -> trigger initial onRefresh manually here.
			synchronized (this.onRefreshMonitor) {
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





## 