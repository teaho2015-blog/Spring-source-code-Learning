# Spring Application事件机制

## 前言

在Spring Boot应用构建期间会发出诸如ApplicationStartingEvent、ApplicationEnvironmentPreparedEvent等事件，
在Spring Cloud的配置刷新等地方，也用到了事件。       
事件机制就像是观察者模式，将变化通过事件广播出去，触发应用内各部件的联动。
通过自定义事件监听器或者自定义事件，我们能够在松耦合的结构下对应用变化做感知，达到我们期望的一致性。

~~~
SpringApplicationRunListener

SpringApplicationRunListeners

AbstractApplicationContext

SimpleApplicationEventMulticaster

EventPublishingRunListener

ApplicationListener

~~~


## 初始化时机

SpringApplicationRunListeners、SpringApplicationRunListener：  
Spring Application的run方法中，
将Spring.factories(SpringFactoryloader)声明的SpringApplicationRunListener子类（EventPublishingRunListener）实例化  
并以集合形式传入SpringApplicationRunListeners的构造函数中。

SimpleApplicationEventMulticaster：
1. 在EventPublishingRunListener的构造函数中实例化。
2. 在spring Application context refresh的时候，也会initApplicationEventMulticaster创建一个新的SimpleApplicationEventMulticaster用于事件分发。

ApplicationListener：  
在Spring Application构造函数中，将Spring.factories(SpringFactoryloader)声明的ApplicationListener的实现类实例化，  
并设置到listener属性中。



## 组织关系及调用关系

![event-mechanism.md](event-mechanism.md)

图中我对组件和相互关系做了解析，我再说一点：  
`SimpleApplicationEventMulticaster`分别会被
Spring boot应用启动时的EventPublishingRunListener和应用运行时的ApplicationContext实例化和调用。


## 例子

我在应用启动时的例子代码中自定义了事件，具体看：
[代码在这 | spring-boot-none-startup](https://github.com/teaho2015-blog/spring-source-code-learning-demo/tree/master/spring-boot-none-startup)

看SimpleAppEvent类和spring.factories中的org.springframework.context.ApplicationListener。


