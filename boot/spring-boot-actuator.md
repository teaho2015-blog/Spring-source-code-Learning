# Spring Boot Actuator分析

## 介绍

Spring Boot Actuator是一个特殊存在，
它一个需要额外引入的库，可是它又在Spring Boot的文档中。
Spring Cloud应用都引入了它，比如API Gateway、Alibaba Nacos、config server等。
Spring Boot的文档标题将它和`Production-ready features`（生产就绪特性）捆绑在一起。

先谈谈它的定义和功能：
> Spring Boot includes a number of additional features to help you monitor and manage your application when you push it to production. You can choose to manage and monitor your application by using HTTP endpoints or with JMX. Auditing, health, and metrics gathering can also be automatically applied to your application.
>
> The spring-boot-actuator module provides all of Spring Boot’s production-ready features. 

Spring Boot囊括了一些额外特性，其能帮助你在应用上生产后通过HTTP和JMX方式进行监控和管理。
功能包含审计、健康、指标收集。  
spring-boot-actuator模块包含了所有的Spring Boot生产就绪特性。

我翻阅了不少资料，“生产就绪”这一概念是众说纷纭的，
我认为Spring Boot定义的“生产就绪”是狭窄的，
在开发工程师的角度来看，我更认同小马哥在《Spring Boot编程思想》提及的[12 factor app](https://12factor.net/)

不过，Spring Boot这么定义也有其道理，毕竟其他未被Spring Boot囊括进“生产就绪”的特性（诸如外部化配置），
它们具有通用性，也就是说，像外部化配置、基准代码它本身就是一个好的应该奉行的特性，而不是说要“生产就绪”的应用才应该有。

下面聊聊Actuator的实现。

## Actuator模块分析

我会按3步来分析：  
1. 相关概念和功能
2. 自定义实现
3. 从源码角度解释实现


### Actuator相关概念

#### Endpoint

Actuator端点（endpoint）使你可以监控你的应用和与之交互，
比如health端点提供了应用的健康信息，
比如我在Spring Boot关闭分析一章中谈到的用于优雅关闭应用的shutdown端点，
比如我们能通过Actuator接口loggers在运行时改变日志级别。  
每个端点均可独立启用或禁用。

`@Endpoint`注解用于声明端点。
`@WriteOperation`、`@ReadOperation`、`@DeleteOperation`注解，用于标注endpoint类的请求方法。
`HealthIndicator`是一个接口，Actuator用其收集应用健康信息。

##### 自定义实现
  
我在[github|spring-source-code-learning-demo](https://github.com/teaho2015-blog/spring-source-code-learning-demo)
的spring-boot-mvc-actuator-demo模块写了自定义实现。详细参考MyHealthIndicator、SimpleEndpoint类。

##### endpoint源码分析

以mvc为例，不看源码前，看过mvc或webflux源码的朋友就能猜出来Actuator多半是通过增加自定义的handlerMapping来解析及匹配endpoint。
事实也是这样。我这里谈一下找到endpoint并转化为handlerMethod的过程。

通过注册的bean--EndpointsSupplier，找到endpoints并转换为DiscoveredWebEndpoint。
~~~

public abstract class EndpointDiscoverer<E extends ExposableEndpoint<O>, O extends Operation>
		implements EndpointsSupplier<E> {
    //省略

	private Collection<E> discoverEndpoints() {
		Collection<EndpointBean> endpointBeans = createEndpointBeans();
		addExtensionBeans(endpointBeans);
        //完成discoveredOperation的转换
		return convertToEndpoints(endpointBeans);
	}

	private Collection<EndpointBean> createEndpointBeans() {
		Map<EndpointId, EndpointBean> byId = new LinkedHashMap<>();
        //找到我们注册的endpoint beans
		String[] beanNames = BeanFactoryUtils.beanNamesForAnnotationIncludingAncestors(
				this.applicationContext, Endpoint.class);
		for (String beanName : beanNames) {
			if (!ScopedProxyUtils.isScopedTarget(beanName)) {
                //进行封装，预解析一些endpoint信息
				EndpointBean endpointBean = createEndpointBean(beanName);
				EndpointBean previous = byId.putIfAbsent(endpointBean.getId(),
						endpointBean);
				Assert.state(previous == null,
						() -> "Found two endpoints with the id '" + endpointBean.getId()
								+ "': '" + endpointBean.getBeanName() + "' and '"
								+ previous.getBeanName() + "'");
			}
		}
		return byId.values();
	}

    //省略
~~~

解析出来的ExposableEndpoint将会作为WebMvcEndpointHandlerMapping的endpoints属性。

接着在初始化WebMvcEndpointHandlerMapping bean时，因为（AbstractHandlerMethodMapping）实现了InitializingBean接口，会执行afterPropertiesSet并初始化handlerMethods。

~~~
	@Override
	public void afterPropertiesSet() {
		initHandlerMethods();
	}
~~~

调用到WebMvcEndpointHandlerMapping的initHandlerMethods方法，
~~~

	@Override
	protected void initHandlerMethods() {
		for (ExposableWebEndpoint endpoint : this.endpoints) {
			for (WebOperation operation : endpoint.getOperations()) {
				registerMappingForOperation(endpoint, operation);
			}
		}
		if (StringUtils.hasText(this.endpointMapping.getPath())) {
			registerLinksMapping();
		}
	}

	private void registerMappingForOperation(ExposableWebEndpoint endpoint,
			WebOperation operation) {
        //actuator的WebOperation包装成可以处理servlet请求的ServletWebOperation
		ServletWebOperation servletWebOperation = wrapServletWebOperation(endpoint,
				operation, new ServletWebOperationAdapter(operation));
        //注册mapping，创建供处理请求的mappinginfo和HandlerMethod
		registerMapping(createRequestMappingInfo(operation),
				new OperationHandler(servletWebOperation), this.handleMethod);
	}

~~~

### Metrics的分析

Spring Boot Actuator为Micrometer提供依赖管理和自动配置，Micrometer是一种支持多种监视系统的应用程序指标门面（facade）。

Spring Boot目前支持的监控指标：
>Spring Boot registers the following core metrics when applicable:
> * JVM metrics, report utilization of:
>   * Various memory and buffer pools
>   * Statistics related to garbage collection
>   * Threads utilization
>   * Number of classes loaded/unloaded
> * CPU metrics
> * File descriptor metrics
> * Kafka consumer metrics
> * Log4j2 metrics: record the number of events logged to Log4j2 at each level
> * Logback metrics: record the number of events logged to Logback at each level
> * Uptime metrics: report a gauge for uptime and a fixed gauge representing the application’s absolute start time
> * Tomcat metrics
> * Spring Integration metrics

#### 自定义指标

我在[github|spring-source-code-learning-demo](https://github.com/teaho2015-blog/spring-source-code-learning-demo)
的spring-boot-mvc-actuator-demo模块写了自定义实现。

我写了如下指标生成类，
~~~
@Component
public class SimpleMetricHandler {

	private final Counter counter;

	public SimpleMetricHandler(MeterRegistry registry) {
		this.counter = registry.counter("received.messages");
	}

	public void handleMessage(String message) {
		this.counter.increment();
		// handle message implementation
	}

}
~~~

并新增了定时任务MeterTask类定时increment，
使用IDEA的朋友启动demo后可执行actuator.http文件的如下请求查看该指标。  
`GET http://localhost:{{port}}/actuator/metrics/received.messages`

当然这是简单实现，像敝司我们架构组的两位负责监控的同事，写监控包做到无感知的运行时自定义打tag，
收集各种公司需要的指标数据。对Spring Boot应用，他们也是通过Actuator metric去做自定义。

