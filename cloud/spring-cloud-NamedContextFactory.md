# NamedContextFactory

## 介绍

在Spring Cloud Context下面`org.springframework.cloud.context.named`包有两个类`NamedContextFactory`、
`ClientFactoryObjectProvider`（为了延后ObjectProvider的获取），我来重点说说这个类`NamedContextFactory`。

在Feign和Ribbon这两个组件中，我们会看到它们通过NamedContextFactory获取`ILoadBalancer`等组件，那么NamedContextFactory到底做了什么呢。

本文先说结论，该类根据传参的名字创建分别独立的子应用上下文，这是为了给不同client的调用提供和维护不同的组件。

## 核心方法分析

我们跟随获取对象getInstance方法来看：

~~~
	public <T> T getInstance(String name, Class<T> type) {
        //获取name对应的context
		AnnotationConfigApplicationContext context = getContext(name);
		if (BeanFactoryUtils.beanNamesForTypeIncludingAncestors(context,
				type).length > 0) {
			return context.getBean(type);
		}
		return null;
	}
~~~

我们看到，获取对象时会先getContext(name)获取对应子上下文，
getContext(name)会先获取，不存在则创建（createContext）：

~~~

	protected AnnotationConfigApplicationContext createContext(String name) {
		//创建新子上下文
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		//为子上下文注册相应配置
        if (this.configurations.containsKey(name)) {
			for (Class<?> configuration : this.configurations.get(name)
					.getConfiguration()) {
				context.register(configuration);
			}
		}
        //注册默认配置
		for (Map.Entry<String, C> entry : this.configurations.entrySet()) {
			if (entry.getKey().startsWith("default.")) {
				for (Class<?> configuration : entry.getValue().getConfiguration()) {
					context.register(configuration);
				}
			}
		}
        //注册配置的占位符解析
		context.register(PropertyPlaceholderAutoConfiguration.class,
				this.defaultConfigType);
        //添加当前子上下文的配置标识
		context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
				this.propertySourceName,
				Collections.<String, Object> singletonMap(this.propertyName, name)));
        //设置父上下文
        if (this.parent != null) {
			// Uses Environment from parent as well as beans
			context.setParent(this.parent);
		}
        //设置上下文名称
		context.setDisplayName(generateDisplayName(name));
        //刷新，上下文初始化
		context.refresh();
		return context;
	}

~~~


## 例子


我的demo实现在
[github|代码在这](https://github.com/teaho2015-blog/spring-source-code-learning-demo)
的spring-cloud-context-demo的`net.teaho.demo.spring.cloud.context.named`包中。
测试启动类`TestNamedContextFactory`。

基本上就是类似Ribbon应用NamedContextFactory的实现，
如果要模拟Feign的实现，则加上@Import去自动装配相应注解信息就行（我在Feign的源码分析里有分析到相关代码）。


