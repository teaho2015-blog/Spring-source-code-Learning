# Spring注解历史

## 历史发展

我先来简单的聊聊Spring注解的发展史。  
Spring1.x时代，那时候注解的概念刚刚兴起，仅支持如@ManagedResource和@Transactional等注解。那时XML配置方式是唯一选择。  
到了2.x时代Spring的注解体系有了雏形，属于“过渡时代”，引入了 @Autowired 、 @Controller 这一系列骨架式的注解。不过尚未完全替换XML配置驱动。  
Spring 3.x是里程碑式的时代，它引入了配置类@Configuration及@ComponentScan，使我们可以替换XML配置方式，全面拥抱Spring注解，在3.1抽象了一套全新的配置属性API，包括Environment和PropertySources这两个核心API。  
Spring4.X是完善时代，趋于完善，引入了@Conditional（条件化注解），@Repeatable等。  
当下是5.X时代，是SpringBoot2.0的底层核心框架，变化不大，引入了一个@Indexed注解，以提升应用启动性能的。  
好了，以上是Spring注解的发展史，接下来针对场景做些归类。

##Spring核心注解按场景分类

1.模式注解

| Spring注解 | 场景说明 | 起始版本 |
| ---- | ---- | ---- |
| @Repository | 数据仓储模式注解 | 2.0 |
| @Component | 通用组件模式注解 | 2.5 |
| @Service | 服务模式注解 | 2.5 |
| @Controller | Web 控制器模式注解 | 2.5 |
| @Configuration | 配置类模式注解 | 3.0 |


2.装配注解
    
| Spring注解 | 场景说明 | 起始版本 |
| ---- | ---- | ---- |
| @ImportResource | 替代 XML 元素<import> | 2.5 |
| @Import | 限定@Autowired 依赖注入范围 | 2.5 |
| @componentScan | 扫描 指定 package 下标注spring 模式注解的类 | 3.1 |


3.依赖注入注解

| Spring注解 | 场景说明 | 起始版本 |
| ---- | ---- | ---- |        
| @Autowired | Bean 依赖注入，支持多种依赖查找方式 | 2.5 |
| @Qualifier | 细粒度的@Autowired 依赖查找 | 2.5 |
 
| Java注解 | 场景说明 | 起始版本 |
| ---- | ---- | ---- |      
| @Resouece | Bean 依赖注入，仅支持名称依赖查找方式 | 2.5 |


4.Bean 自定义注解

| Spring注解 | 场景说明 | 起始版本 |
| ---- | ---- | ---- |  
| @Bean | 替代 XML 元素&lt;bean&gt; | 3.0 |
| @DependsOn | 替代 XML 属性&lt;bean depends-on="..."/&gt; | 3.0 |
| @Lazy | 替代 XML 属性&lt;bean lazy0init="true&#124;falses"/&gt; | 3.0 |
| @Primary | 替代 XML 元素&lt;bean primary="true&#124;false"/&gt; | 3.0 |
| @Role | 替代 SML 元素&lt;bean role="..."/&gt; | 3.1 |
| @Lookup | 替代 XML 属性&lt;bean lookup-method="..."&gt; | 4.1 |


5.条件装配注解 
       
| Spring注解 | 场景说明 | 起始版本 |
| ---- | ---- | ---- |  
| @Profile | 配置化条件装配 | 3.1 |
| @Conditional | 编程条件装配 | 3.1 |                    


6. 配置属性注解

| Spring注解 | 场景说明 | 起始版本 |
| ---- | ---- | ---- |  
| @PropertySource | 配置属性抽象 PropertySource | 3.1 |
| @PropertySources | @PropertySource集合注解 | 4.0 |


7. 生命周期回调注解

| Spring注解 | 场景说明 | 起始版本 |
| ---- | ---- | ---- |  
| @PostConstruct | 替换 XML 元素&lt;bean init-method="..."/&gt;或 InitializingBean | 2.5 |
| @PreDestroy | 替换 XML 元素&lt;bean destroy-method="..." /&gt;或 DisposableBean | 2.5 |


8. 注解属性注解

| Spring注解 | 场景说明 | 起始版本 |
| ---- | ---- | ---- | 
| @AliasFor | 别名注解属，实现复用的目的 | 4.2 | 

9. 性能注解

| Spring注解 | 场景说明 | 起始版本 |
| ---- | ---- | ---- | 
| @Indexed | 提升 spring 模式注解的扫描效率 | 5.0 |
    
## references

[1]小马哥.Spring Boot编程思想[M].中国:电子工业出版社,2019