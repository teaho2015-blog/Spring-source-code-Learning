# Spring Cloud配置中心--Spring Cloud Context、Spring Cloud Zookeeper Config的实现分析

## 简介

Spring Cloud项目（或Spring Cloud中间件）无一例外都引入了Spring Cloud Context和Spring Cloud Commons模块。他们定义了Spring Cloud项目的约定用法和通用组件。



* PropertySourceBootstrapConfiguration
* PropertySourceLocator
* RefreshEventListener
* ContextRefresher
* RefreshEvent
* RefreshScope
* @RefreshScope


## Spring Cloud Zookeeper Config

![spring-cloud-config-zk-refresh.jpg](spring-cloud-config-zk-refresh.jpg)





## Reference

[1][源码分析一下spring的scoped-proxy（一）](https://juejin.cn/post/6869402006017507335)  
[2][]()  