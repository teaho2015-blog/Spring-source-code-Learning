# Spring注解历史

## 历史发展

我们先来简单的聊聊Spring注解的发展史。
Spring1.x时代，那时候注解的概念刚刚兴起，仅支持如 @Transactional 等注解。
到了2.x时代Spring的注解体系有了雏形，引入了 @Autowired 、 @Controller 这一系列骨架式的注解。
3.x是黄金时代，它除了引入 @Enable 模块驱动概念，加快了Spring注解体系的成型，还引入了配置类 @Configuration 及 @ComponentScan ，使我们可以抛弃XML配置文件的形式，全面拥抱Spring注解，但Spring并未完全放弃XML配置文件，它提供了 @ImportResource 允许导入遗留的XML配置文件。此外还提供了 @Import 允许导入一个或多个Java类成为Spring Bean。
4.X则趋于完善，引入了条件化注解 @Conditional ，使装配更加的灵活。
当下是5.X时代，是SpringBoot2.0的底层核心框架，目前来看，变化不是很大，但也引入了一个 @Indexed 注解，主要是用来提升启动性能的。
好了，以上是Spring注解的发展史，接下来我们对Spring注解体系的几个议题进行讲解。

##按场景分类

1.模式注解

| Spring注解 | 场景说明 | 起始版本 |
| ---- | ---- | ---- |
| @Repository | 数据仓储模式注解 | |
| @Component | 通用组件模式注解 | |
| @Service | 服务模式注解 | |
| @Controller | Web 控制器模式注解 | |
| @Configuration | 配置类模式注解 | |


2.装配注解
    
    @ImportResource        替代 XML 元素<import>

    @Import                        限定@Autowired 依赖注入范围

    @componentScan          扫描 指定 package 下标注spring 模式注解的类

 

3.依赖注入注解
    
      
    @Autowired                  Bean 依赖注入，支持多种依赖查找方式

    @Qualifier                     细粒度的@Autowired 依赖查找

    @Resouece                    Bean 依赖注入，仅支持名称依赖查找方式

 

4.Bean 自定义注解

    @Bean                    替代 XML 元素<bean>

    @DependsOn          替代 XML 属性<bean depends-on="..."/>

    @Lazy                      替代 XML 属性<bean lazy0init="true|falses"/>

    @Primary                替代 XML 元素<bean primary="true|false"/>

    @Role                       替代 SML 元素<bean role="..."/>

    @Lookup                  替代 XML 属性<bean lookup-method="...">

 

5.条件装配注解        

    @Profile                    配置化条件装配

    @Conditional            编程条件装配                    

 

6. 配置属性注解

    @PropertySource        配置属性抽象 PropertySource

    @PropertySources        @PropertySource集合注解

 

7. 生命周期回调注解

    @PostConstruct           替换 XML 元素<bean init-method="..."/>或 InitializingBean

    @PreDestroy                替换 XML 元素<bean destroy-method="..." />或 DisposableBean

 

8. 注解属性注解

    @AliasFor                别名注解属，实现复用的目的

 

9. 性能注解

    @Indexed        提升 spring 模式注解的扫描效率
    
    
    
## references

[1]小马哥.Spring Boot编程思想[M].中国:电子工业出版社,2019