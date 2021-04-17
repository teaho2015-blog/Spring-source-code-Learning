# Spring Cloud的注册与发现--Spring Cloud Commons、Netflix Eureka、Alibaba Nacos的实现分析

## 介绍

Spring Cloud Commons定义了分布式系统中的规范。本文我们来看Spring Cloud Commons的服务发现和注册相关内容。

注，在分析Alibaba Nacos时为了弄清楚一些问题，我额外分析了Nacos源码的应用启动，服务注册，一致性算法等核心内容。
由于内容和篇幅，我拆分开另一文。[Nacos分析](./nacos.md)


## Spring Cloud Commons discovery和registry模块组件

<!--
发现
DiscoveryClient
EnableDiscoveryClient
EnableDiscoveryClientImportSelector

注册

ServiceRegistry
Registration extends ServiceInstance
AutoServiceRegistration
AbstractAutoServiceRegistration<R extends Registration> implements AutoServiceRegistration, ApplicationContextAware, ApplicationListener<WebServerInitializedEvent>
-->

谈谈Spring Cloud注册发现各组件。


* org.springframework.cloud.client.discovery.EnableDiscoveryClient注解
  表示开启服务发现功能，该注解有一个属性autoRegister，EnableDiscoveryClientImportSelector会根据该值设置AutoServiceRegistrationConfiguration和spring.cloud.service-registry.auto-registration.enabled的值，
  像EurekaClientAutoConfiguration和NacosDiscoveryAutoConfiguration都是通过判断该值是否为true来决定是否初始化Registration、ServiceRegistry。
* org.springframework.cloud.client.discovery.DiscoveryClient
  指代对服务发现的通用读取操作
  
~~~
public interface DiscoveryClient extends Ordered {
	int DEFAULT_ORDER = 0;

    //被HealthIndicator使用的一个用户可读的描述。
	String description();

    //基于服务id参数获取对应的所有服务实例。
	List<ServiceInstance> getInstances(String serviceId);

    //获取所有已知的服务id
	List<String> getServices();

    //默认获取order实现
	@Override
	default int getOrder() {
		return DEFAULT_ORDER;
	}

~~~
* org.springframework.cloud.client.serviceregistry.ServiceRegistry
  注册和注销实例的封装
~~~
public interface ServiceRegistry<R extends Registration> {
    //注册该注册项，一个注册项通常包含一个实例的信息，如主机名和端口号。
	void register(R registration);
    //注销该注册项
	void deregister(R registration);
    //关闭该ServiceRegistry，这是一个生命周期方法。
	void close();
	/**
	 * 设置注册项的状态，状态值集合由具体实现决定
	 * @see org.springframework.cloud.client.serviceregistry.endpoint.ServiceRegistryEndpoint
	 * @param registration The registration to update.
	 * @param status The status to set.
	 */
	void setStatus(R registration, String status);
    //获取注册项的状态
	<T> T getStatus(R registration);
}
~~~
* org.springframework.cloud.client.serviceregistry.Registration 本身没有定义新接口，继承ServiceInstance接口。

~~~
public interface ServiceInstance {
    
    //返回唯一的实例id
	default String getInstanceId() {
		return null;
	}
    //返回服务id
	String getServiceId();
    //返回该已注册的服务实例的host名
	String getHost();
    //返回该已注册的服务实例的端口号
	int getPort();
    //该服务实例是否使用HTTPS
	boolean isSecure();
    //返回服务的URI地址
	URI getUri();
    //返回服务实例的元数据
	Map<String, String> getMetadata();
    //返回协议
	default String getScheme() {
		return null;
	}
}

~~~

* AutoServiceRegistrationAutoConfiguration  
  添加AutoServiceRegistrationProperties；AutoServiceRegistration初始化检查

* org.springframework.cloud.client.serviceregistry.AutoServiceRegistration和org.springframework.cloud.client.serviceregistry.AbstractAutoServiceRegistration<R extends Registration>
  提供了对ServiceRegistry有用且常见的生命周期方法。
  
~~~

public abstract class AbstractAutoServiceRegistration<R extends Registration>
		implements AutoServiceRegistration, ApplicationContextAware, ApplicationListener<WebServerInitializedEvent> {
//省略
    
    //监听WebServerInitializedEvent，在webServer启动后发布
	@Override
	@SuppressWarnings("deprecation")
	public void onApplicationEvent(WebServerInitializedEvent event) {
		bind(event);
	}
    //设置port
	@Deprecated
	public void bind(WebServerInitializedEvent event) {
		ApplicationContext context = event.getApplicationContext();
		if (context instanceof ConfigurableWebServerApplicationContext) {
			if ("management".equals(
					((ConfigurableWebServerApplicationContext) context).getServerNamespace())) {
				return;
			}
		}
		this.port.compareAndSet(0, event.getWebServer().getPort());
		this.start();
	}
    //1.发布InstancePreRegisteredEvent事件
    //2.注册服务。
    //3.注册服务管理（看具体实现，比如Nacos的实现是不提供实现）
    //4.InstanceRegisteredEvent
	public void start() {
		if (!isEnabled()) {
			if (logger.isDebugEnabled()) {
				logger.debug("Discovery Lifecycle disabled. Not starting");
			}
			return;
		}

		// only initialize if nonSecurePort is greater than 0 and it isn't already running
		// because of containerPortInitializer below
		if (!this.running.get()) {
			this.context.publishEvent(new InstancePreRegisteredEvent(this, getRegistration()));
			register();
			if (shouldRegisterManagement()) {
				registerManagement();
			}
			this.context.publishEvent(
					new InstanceRegisteredEvent<>(this, getConfiguration()));
			this.running.compareAndSet(false, true);
		}
	}
    //用ServiceRegistry注册服务
	protected void register() {
		this.serviceRegistry.register(getRegistration());
	}

    //销毁
	@PreDestroy
	public void destroy() {
		stop();
	}
    //用serviceRegistry注销服务并关闭serviceRegistry
	public void stop() {
		if (this.getRunning().compareAndSet(true, false) && isEnabled()) {
			deregister();
			if (shouldRegisterManagement()) {
				deregisterManagement();
			}
			this.serviceRegistry.close();
		}
	}

//省略

}

~~~

