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

我们来关注初始化，
Servlet的实现DispatcherServlet初始化时，也会调用init方法，
首先来到的是`HttpServletBean`的init方法。






## 