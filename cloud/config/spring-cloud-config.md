# Spring Cloud远程配置中心--Spring Cloud Context、Spring Cloud Zookeeper Config的实现分析

## 简介

Spring Cloud Context是Spring Cloud应用上下文的实现。

谈到Spring Cloud应用如何加载和刷新远程配置，就绕不开分析Spring Cloud Context（上下文）的启动和刷新，
而分析Spring Cloud Context的源码，又会有大量的篇幅是分析源码中大量加载配置，处理environment的部分。
所以我将Spring Cloud应用上下文的启动合并到这里分析。

Spring Cloud Context的一些组件，在这里先简单说说他们的作用：
* BootstrapApplicationListener
* PropertySourceBootstrapConfiguration
* PropertySourceLocator
* RefreshEventListener
* ContextRefresher
* RefreshEvent
* RefreshScope
* @RefreshScope

## Spring Cloud Context启动


Spring Cloud Context初始化是跟随Spring Boot启动，首先会初始化类`BootstrapApplicationListener`。
`BootstrapApplicationListener`的执行时机是，应用初始化时接受到EnvironmentParented事件后。
~~~

	@Override
	public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
	    //获取environment，注意此时application.yml尚未加载，BootstrapApplicationListener的order高于ConfigFileApplicationListener。
		ConfigurableEnvironment environment = event.getEnvironment();
		if (!environment.getProperty("spring.cloud.bootstrap.enabled", Boolean.class,
				true)) {
			return;
		}
		// 已经加载了bootstrap配置的话，则跳过
		if (environment.getPropertySources().contains(BOOTSTRAP_PROPERTY_SOURCE_NAME)) {
			return;
		}
		ConfigurableApplicationContext context = null;
		//配置文件名默认bootstrap(.yml/.properties)
		String configName = environment
				.resolvePlaceholders("${spring.cloud.bootstrap.name:bootstrap}");
        //查找当前应用是否已通过ParentContextApplicationContextInitializer去存储了parent。
		for (ApplicationContextInitializer<?> initializer : event.getSpringApplication()
				.getInitializers()) {
			if (initializer instanceof ParentContextApplicationContextInitializer) {
				context = findBootstrapContext(
						(ParentContextApplicationContextInitializer) initializer,
						configName);
			}
		}
		
		if (context == null) {
		//1. 设置相关cloud配置属性, 比如spring.config.name=bootstrap
		//2. 启动一个WebApplicationType=NONE的SpringApplication应用，用于加载bootstrap(.yml/.properties)
		//3. 加载BootstrapConfiguration配置类
		//4. 将加载的应用上下文作为父上下文
			context = bootstrapServiceContext(environment, event.getSpringApplication(),
					configName);
			event.getSpringApplication().addListeners(new CloseContextOnFailureApplicationListener(context));
		}

		apply(context, event.getSpringApplication(), environment);
	}
 
~~~





## Spring Cloud Zookeeper Config

![spring-cloud-config-zk-refresh.jpg](spring-cloud-config-zk-refresh.jpg)


## Reference

[1][源码分析一下spring的scoped-proxy（一）](https://juejin.cn/post/6869402006017507335)  
[2][]()  