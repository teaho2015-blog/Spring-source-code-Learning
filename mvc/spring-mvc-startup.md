# Spring MVC启动分析

## 简介

基于Spring Boot，围绕DispatcherServlet来进行分析。


## 寻找初始化源头

寻找Spring Boot对Tomcat的初始化，无非是看
`ServletWebServerApplicationContext`的`onRefresh`方法。
看Spring Boot createWebServer获取ServletWebServerFactory的bean并初始化WebServer这一Spring Boot web服务器封装。
然后留意TomcatWebServer的initialize（）方法。

而对于Spring MVC DispatcherServlet和相关组件的初始化，并非在tomcat服务器构建起时就做，而要从下面拉开帷幕。


~~~
package javax.servlet;

public interface Servlet {

    public void init(ServletConfig config) throws ServletException;

    public ServletConfig getServletConfig();
    
    public void service(ServletRequest req, ServletResponse res)
            throws ServletException, IOException;
            
    public String getServletInfo();

    public void destroy();
}


~~~

![DispatcherServlet_inheritance.png](DispatcherServlet_inheritance.png)




## 