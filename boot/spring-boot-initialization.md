# Spring boot启动原理及相关组件






## run()


### refreshContext()



### 容器refresh

~~~
// ServletWebServerApplicationContext 
public final void refresh() throws BeansException, IllegalStateException {
   try {
      super.refresh();
   }
   catch (RuntimeException ex) {
      //停止webserver
      stopAndReleaseWebServer();
      throw ex;
   }
}


org.springframework.context.support.AbstractApplicationContext refresh()
public void refresh() throws BeansException, IllegalStateException {
   // 单线程执行
   synchronized (this.startupShutdownMonitor) {
      // Prepare this context for refreshing.
      // 1、设置Spring容器的启动时间，撤销关闭状态，开启活跃状态。2、初始化属性源信息(Property)3、验证环境信息里一些必须存在的属性
      prepareRefresh();
      // Tell the subclass to refresh the internal bean factory.
      // 如果是RefreshtableApplicationContext会做了很多事情：
      // 1、让子类刷新内部beanFactory ，创建IoC容器（DefaultListableBeanFactory--ConfigurableListableBeanFactory 的实现类）
      // 2、加载解析XML文件（最终存储到Document对象中）
      // 3、读取Document对象，并完成BeanDefinition的加载和注册工作
      ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();
      // Prepare the bean factory for use in this context.
      //从Spring容器获取BeanFactory(Spring Bean容器)并进行相关的设置为后续的使用做准备：
      //1、设置classloader(用于加载bean)，设置表达式解析器(解析bean定义中的一些表达式)，添加属性编辑注册器(注册属性编辑器)
      //2、添加ApplicationContextAwareProcessor这个BeanPostProcessor。取消ResourceLoaderAware、ApplicationEventPublisherAware、MessageSourceAware、ApplicationContextAware、EnvironmentAware这5个接口的自动注入。因为ApplicationContextAwareProcessor把这5个接口的实现工作做了
      //3、设置特殊的类型对应的bean。BeanFactory对应刚刚获取的BeanFactory；ResourceLoader、ApplicationEventPublisher、ApplicationContext这3个接口对应的bean都设置为当前的Spring容器
      //4、注入一些其它信息的bean，比如environment、systemProperties等
      prepareBeanFactory(beanFactory);
      try {
         // Allows post-processing of the bean factory in context subclasses.
         postProcessBeanFactory(beanFactory);
         // Invoke factory processors registered as beans in the context.
         invokeBeanFactoryPostProcessors(beanFactory);
         // Register bean processors that intercept bean creation.
         registerBeanPostProcessors(beanFactory);
         // Initialize message source for this context.
         initMessageSource();
         // Initialize event multicaster for this context.
         initApplicationEventMulticaster();
         // Initialize other special beans in specific context subclasses.
         onRefresh();
         // Check for listener beans and register them.
         registerListeners();
         // Instantiate all remaining (non-lazy-init) singletons.
         finishBeanFactoryInitialization(beanFactory);
         // Last step: publish corresponding event.
         finishRefresh();
      } catch (BeansException ex) {
         if (logger.isWarnEnabled()) {
            logger.warn("Exception encountered during context initialization - " +
                  "cancelling refresh attempt: " + ex);
         }
         // Destroy already created singletons to avoid dangling resources.
         destroyBeans();
         // Reset 'active' flag.
         cancelRefresh(ex);
         // Propagate exception to caller.
         throw ex;
      }

      finally {
         // Reset common introspection caches in Spring's core, since we
         // might not ever need metadata for singleton beans anymore...
         resetCommonCaches();
      }
   }
}


~~~


#### postProcessBeanFactory()

设置BeanFactory之后再进行后续的一些BeanFactory操作。

不同的Context会进行不同的操作。
比如，AnnotationConfigServletWebServerApplicationContext

~~~
	protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        // 父类实现，会注册web应用特有的factory scope， 
		super.postProcessBeanFactory(beanFactory);
        //查看basePackages属性，如果设置了会使用ClassPathBeanDefinitionScanner去扫描basePackages包下的bean并注
		if (this.basePackages != null && this.basePackages.length > 0) {
			this.scanner.scan(this.basePackages);
		}
        // 查看annotatedClasses属性，如果设置了会使用AnnotatedBeanDefinitionReader去注册这些bean
		if (!this.annotatedClasses.isEmpty()) {
			this.reader.register(ClassUtils.toClassArray(this.annotatedClasses));
		}
	}
~~~


#### invokeBeanFactoryPostProcessors()

~~~

	/**
	 * Instantiate and invoke all registered BeanFactoryPostProcessor beans,
	 * respecting explicit order if given.
	 * <p>Must be called before singleton instantiation.
	 */
	protected void invokeBeanFactoryPostProcessors(ConfigurableListableBeanFactory beanFactory) {
        //执行AbstractContext持有的BeanFactory后置处理器
        //这些处理器是之前ContextInitializor
		PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors(beanFactory, getBeanFactoryPostProcessors());

		// Detect a LoadTimeWeaver and prepare for weaving, if found in the meantime
		// (e.g. through an @Bean method registered by ConfigurationClassPostProcessor)
        // 如果通过-javaagent参数设置了LTW的织入器类包，那么增加LTW的BeanProcessor。
		if (beanFactory.getTempClassLoader() == null && beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
			beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
			beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
		}
	}
~~~

从容器中找出BeanDefinitionRegistryPostProcessor、BeanFactoryPostProcessor（二者的区别是，一个使用BeanDefinitionRegistry作处理，一个使用BeanFactory做处理），
并按一定的规则顺序执行。

ConfigurationClassPostProcessor的优先级为最高，它会对项目中的@Configuration注解修饰的类(@Component、@ComponentScan、@Import、@ImportResource修饰的类也会被处理)进行解析，解析完成之后把这些bean注册到BeanFactory中。
需要注意的是这个时候注册进来的bean还没有实例化。

ConfigurationClassPostProcessor的流程之后会独立进行分析。


#### registerBeanPostProcessors(ConfigurableListableBeanFactory beanFactory)方法

~~~
	/**
	 * Instantiate and invoke all registered BeanPostProcessor beans,
	 * respecting explicit order if given.
	 * <p>Must be called before any instantiation of application beans.
	 */
	protected void registerBeanPostProcessors(ConfigurableListableBeanFactory beanFactory) {
        //委派PostProcessorRegistrationDelegate去做
		PostProcessorRegistrationDelegate.registerBeanPostProcessors(beanFactory, this);
	}

~~~

从Spring容器中按一定顺序（PriorityOrdered、Ordered、非PriorityOrdered非Ordered）找出实现了BeanPostProcessor接口的bean，并设置到BeanFactory的属性中。之后bean被实例化的时候会调用这个BeanPostProcessor。




#### initMessageSource()

初始化一些国际化相关的属性。

Spring boot的国际化配置可阅读MessageSourceAutoConfiguration。
默认情况会设置一个DelegatingMessageSource，是一个空实现，因为ApplicationContext接口拓展了MessageSource接口，所以Spring容器都有getMessage方法，
可是，在实现上又允许空MessageSource，所以，通过一个DelegatingMessageSource去适配。

#### initApplicationEventMulticaster()

Initialize event multicaster for this context.
初始化事件广播器。默认实现是SimpleApplicationEventMulticaster。


#### onRefresh()

模板方法，给不同的Spring应用容器去实例化一些特殊的类。

比如，AnnotationConfigServletWebServerApplicationContext、AnnotationConfigReactiveWebServerApplicationContext会去创建web server（createWebServer()）。
spring boot的mvc内置支持有tomcat、Undertow、jetty三种server，而reactive web server则内置支持tomcat、jetty、netty三种。

~~~

				// Unlike Jetty, all Tomcat threads are daemon threads. We create a
				// blocking non-daemon to stop immediate shutdown
				startDaemonAwaitThread();

~~~

btw，如果是tomcat server的话，spring boot会启动多一个线程防止退出。

#### registerListeners()

把BeanFactory的ApplicationListener拿出来塞到事件广播器里。

如果ApplicationContext的earlyApplicationEvents属性有值，则广播该属性持有的early事件。


#### finishBeanFactoryInitialization(beanFactory)

实例化BeanFactory中已经被注册但是未实例化的所有实例(懒加载的不需要实例化)。

比如invokeBeanFactoryPostProcessors方法中根据各种注解解析出来的类，在这个时候都会被初始化。

#### finishRefresh()

~~~
// ReactiveWebServerApplicationContext
	@Override
	protected void finishRefresh() {
		super.finishRefresh();
		WebServer webServer = startReactiveWebServer();
		if (webServer != null) {
			publishEvent(new ReactiveWebServerInitializedEvent(webServer, this));
		}
	}

// AbstractApplicationContext
	/**
	 * Finish the refresh of this context, invoking the LifecycleProcessor's
	 * onRefresh() method and publishing the
	 * {@link org.springframework.context.event.ContextRefreshedEvent}.
	 */
	protected void finishRefresh() {
		// Clear context-level resource caches (such as ASM metadata from scanning).
        // 容器完成刷新，清除资源缓存
		clearResourceCaches();

		// Initialize lifecycle processor for this context.
        // 初始化lifeCycleProcessor, 默认实现是DefaultLifeCycleProcessor，实现了BeanFactoryAware接口，通过BeanFactory找出LifeCycle bean
        // 可通过自定义实现LifeCycle接口的Bean，来监听容器的生命周期。
		initLifecycleProcessor();

		// Propagate refresh to lifecycle processor first.
        //粗发生命周期处理器的onRefresh方法，顺带一说，在程序正常退出时，会粗发shutdownHook，那时会粗发生命周期处理器的onClose方法
		getLifecycleProcessor().onRefresh();

		// Publish the final event.
        // 广播ContextRefreshed事件
		publishEvent(new ContextRefreshedEvent(this));

		// Participate in LiveBeansView MBean, if active.
        // 将ApplicationContext注册到Spring tool suite里
		LiveBeansView.registerApplicationContext(this);
	}
~~~



#### resetCommonCaches()


~~~

                // Reset common introspection caches in Spring's core, since we
				// might not ever need metadata for singleton beans anymore...
				resetCommonCaches();

~~~
最后会在finally执行resetCommonCaches()，执行一些Spring core、beans加载和解析的Bean信息（因为对于singleton bean来说已经不需要了）。

































## 例子


在

















