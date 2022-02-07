# Spring Cloud远程配置中心--Spring Cloud Context、Spring Cloud Zookeeper Config的实现分析

## 简介

Spring Cloud Context是Spring Cloud应用上下文的实现。

谈到Spring Cloud应用如何加载和刷新远程配置，就绕不开分析Spring Cloud Context（上下文）的启动和刷新，
而分析Spring Cloud Context的源码，又会有大量的篇幅是分析源码中大量加载配置，处理environment的部分。
所以我将Spring Cloud应用上下文的启动合并到这里分析。

Spring Cloud Context的一些组件，在这里先简单说说他们的作用：
* PropertySourceBootstrapConfiguration
* PropertySourceLocator
* RefreshEventListener
* ContextRefresher
* RefreshEvent
* RefreshScope
* @RefreshScope
* BootstrapApplicationListener

## Spring Cloud Context启动






## Spring Cloud Zookeeper Config

![spring-cloud-config-zk-refresh.jpg](spring-cloud-config-zk-refresh.jpg)


## Reference

[1][源码分析一下spring的scoped-proxy（一）](https://juejin.cn/post/6869402006017507335)  
[2][]()  