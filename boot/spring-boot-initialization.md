# Spring boot启动原理及相关组件

## Spring Boot应用启动

一个Spring Boot应用的启动通常如下：
~~~

@SpringBootApplication
@Slf4j
public class ApplicationMain {
    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(ApplicationMain.class, args);
    }
}

~~~

执行如上代码，Spring Boot程序启动成功。事实上启动Spring Boot应用离不开SpringApplication。  
所以，我们跟随`SpringApplication`的脚步，开始从源码角度分析Spring Boot的初始化过程。

btw，可参看[例子](#例子)一节，我对Spring Boot启动的拓展点都做了demo，可参照下面源码分析进行理解。

文档有一句话说了SpringApplication做了什么（目的）：
> Create an appropriate ApplicationContext instance (depending on your classpath)
  Register a CommandLinePropertySource to expose command line arguments as Spring properties
  Refresh the application context, loading all singleton beans
  Trigger any CommandLineRunner beans

## SpringApplication构造函数

启动代码先创建`SpringApplication`示例，在执行run方法：
~~~
	public static ConfigurableApplicationContext run(Class<?>[] primarySources,
			String[] args) {
		return new SpringApplication(primarySources).run(args);
	}
~~~

如下是SpringApplication的构造函数代码分析。
~~~

this.resourceLoader = resourceLoader;
Assert.notNull(primarySources, "PrimarySources must not be null");
this.primarySources = new LinkedHashSet<>(Arrays.asList(primarySources));
//通过Classloader探测不同web应用核心类是否存在，进而设置web应用类型
this.webApplicationType = WebApplicationType.deduceFromClasspath(); 
//找出所有spring.factories中声明的ApplicationContextInitializer并设置，
//ApplicationContextInitializer定义了回调接口，在refresh()前初始化调用（即在prepareContext的applyInitializers方法中调用）
setInitializers((Collection) getSpringFactoriesInstances(
ApplicationContextInitializer.class));
//找出所有spring.factories中声明的ApplicationListener（细节往后再叙），ApplicationListener继承了
//java.util.EventListener，实现了类似观察者模式的形式，通过实现ApplicationListener、SmartApplicationListener，能够监听Spring上下文的refresh、Prepared等事件或者是自定义事件
setListeners((Collection) getSpringFactoriesInstances(ApplicationListener.class));
//找出主启动类（有趣的是，是通过new一个runtime异常然后在异常栈里面找出来的）
this.mainApplicationClass = deduceMainApplicationClass();
~~~

在构造期间，主要做了：
1. 判定应用类型，为后面创建不同类型的spring context做准备。
2. 初始化ApplicationContextInitializer和ApplicationListener。
3. 找出启动类。

## run()

介绍`run()`方法前，先说说贯穿run方法的ApplicationRunListener，它有助于理解整个run()的运行周期。  
写在这里：[Spring Application事件机制](event-mechanism.md)

`run()`方法分析如下：
~~~

//java.awt.headless，是J2SE的一种模式，用于在缺失显示屏、鼠标或者键盘时的系统配置。
configureHeadlessProperty();
//将spring.factories中的SpringApplicationRunListener接口实现类拖出来，塞到SpringApplicationRunListeners（一个集合）中，统一批量执行
SpringApplicationRunListeners listeners = getRunListeners(args);
//触发runlistener的starting
listeners.starting();
try {
   ApplicationArguments applicationArguments = new DefaultApplicationArguments(
         args);
   ConfigurableEnvironment environment = prepareEnvironment(listeners,
         applicationArguments);
   //spring.beaninfo.ignore如果没有设置值，则把它设为true，具体情况具体设置，
   //如果没用的话，把它设为true可以ignore掉classloader对于不存在的BeanInfo的扫描，提高性能。
   configureIgnoreBeanInfo(environment);
   //banner打印。自定义banner挺好玩的
   Banner printedBanner = printBanner(environment);
   //根据webApplicationType（一开始推断的应用类型）去新建applicationContext
   context = createApplicationContext();
   //获取SpringBootExceptionReporter，回调接口类，提供启动时的异常报告
   exceptionReporters = getSpringFactoriesInstances(
         SpringBootExceptionReporter.class,
         new Class[] { ConfigurableApplicationContext.class }, context);
   //下面会说
   prepareContext(context, environment, listeners, applicationArguments,
         printedBanner);
   refreshContext(context);
   //do nothing
   afterRefresh(context, applicationArguments);
   //计时停止
   stopWatch.stop();
   //打日志
   if (this.logStartupInfo) {
      new StartupInfoLogger(this.mainApplicationClass)
            .logStarted(getApplicationLog(), stopWatch);
   }
   //启动
   listeners.started(context);
   //找出context的ApplicationRunner和CommandLineRunner，用AnnotationAwareOrderComparator排序，并执行
   callRunners(context, applicationArguments);


~~~

下面再分别说说两个方法（prepareEnvironment、refreshContext）的代码。



### prepareEnvironment

~~~

private ConfigurableEnvironment prepareEnvironment(
      SpringApplicationRunListeners listeners,
      ApplicationArguments applicationArguments) {
   // Create and configure the environment
   ConfigurableEnvironment environment = getOrCreateEnvironment();
   configureEnvironment(environment, applicationArguments.getSourceArgs());
   //发布environment prepared事件
   listeners.environmentPrepared(environment);
   //将获取到的environment中的spring.main配置绑定到SpringApplication中,
   //使用的是Binder这个spring boot2.0开始有的类
   bindToSpringApplication(environment);
   if (!this.isCustomEnvironment) {
      environment = new EnvironmentConverter(getClassLoader())
            .convertEnvironmentIfNecessary(environment, deduceEnvironmentClass());
   }
   //附加的解析器将动态跟踪底层 Environment 属性源的任何添加或删除，
   //关于ConfigurationPropertySourcesPropertySource和MutablePropertiySource
   //将在Environment中作进一步讲解
   ConfigurationPropertySources.attach(environment);
   return environment;
}
~~~

### refreshContext

~~~
private void prepareContext(ConfigurableApplicationContext context,
      ConfigurableEnvironment environment, SpringApplicationRunListeners listeners,
      ApplicationArguments applicationArguments, Banner printedBanner) {
   //为上下文设置environment（配置、profile）
   context.setEnvironment(environment);
   //对application做一些处理，设置一些组件，
   //比如BeanNameGenerator，ApplicationConversionService（包含一些默认的Converter和formatter）
   postProcessApplicationContext(context);
   //
   applyInitializers(context);
   listeners.contextPrepared(context);
   if (this.logStartupInfo) {
      logStartupInfo(context.getParent() == null);
      logStartupProfileInfo(context);
   }
   // Add boot specific singleton beans
   ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
   beanFactory.registerSingleton("springApplicationArguments", applicationArguments);
   if (printedBanner != null) {
      beanFactory.registerSingleton("springBootBanner", printedBanner);
   }
   if (beanFactory instanceof DefaultListableBeanFactory) {
      ((DefaultListableBeanFactory) beanFactory)
            .setAllowBeanDefinitionOverriding(this.allowBeanDefinitionOverriding);
   }
   // Load the sources
   Set<Object> sources = getAllSources();
   Assert.notEmpty(sources, "Sources must not be empty");
   //Load beans（其实是由sources构建beanDefinition） into the application context. 
   //构建BeanDefinitionLoader并执行BeanDefinitionLoader.load()
   load(context, sources.toArray(new Object[0]));
   //执行contextLoaded事件
   listeners.contextLoaded(context);
}


~~~

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
        //这些处理器是之前ContextInitializer
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
最后会在finally执行resetCommonCaches()，清除一些Spring core、beans加载和解析的Bean信息缓存（因为对于singleton bean来说已经不需要了）。



## 例子

在github里，我把Spring Boot应用启动的拓展组件（自定义的应用初始器、监听器、事件、ApplicationRunner）都写了例子，可参照阅读。
[代码在这 | spring-boot-none-startup](https://github.com/teaho2015-blog/spring-source-code-learning-demo/tree/master/spring-boot-none-startup)

日志如下：
~~~
2020-05-20 18:30:11.625  INFO 81568 --- [           main] n.t.d.s.b.s.n.s.r.SimpleRunListener      : environmentPrepared, env:StandardEnvironment {activeProfiles=[dev], defaultProfiles=[default], propertySources=[MapPropertySource {name='systemProperties'}, OriginAwareSystemEnvironmentPropertySource {name='systemEnvironment'}, RandomValuePropertySource {name='random'}, OriginTrackedMapPropertySource {name='applicationConfig: [classpath:/application-dev.yml]'}, OriginTrackedMapPropertySource {name='applicationConfig: [classpath:/application.yml]'}]}

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::        (v2.1.3.RELEASE)

2020-05-20 18:30:11.832  INFO 81568 --- [           main] n.t.d.s.b.s.n.s.r.SimpleRunListener      : contextPrepared, ctx:org.springframework.context.annotation.AnnotationConfigApplicationContext@1d730606, started on Thu May 01 08:00:00 CST 1970
2020-05-20 18:30:11.838  INFO 81568 --- [           main] n.t.d.s.b.startup.none.ApplicationMain   : Starting ApplicationMain on DESKTOP-OLDGHC1 with PID 81568 ( started by teash in )
2020-05-20 18:30:11.838  INFO 81568 --- [           main] n.t.d.s.b.startup.none.ApplicationMain   : The following profiles are active: dev
2020-05-20 18:30:11.894  INFO 81568 --- [           main] n.t.d.s.b.s.n.s.r.SimpleRunListener      : contextLoaded, context: org.springframework.context.annotation.AnnotationConfigApplicationContext@1d730606, started on Thu May 01 08:00:00 CST 1970
2020-05-20 18:30:12.404  INFO 81568 --- [           main] .s.b.s.n.s.SimpleApplicationContextAware : SimpleApplicationContextAware and send SimpleAppEvent
2020-05-20 18:30:12.441  INFO 81568 --- [           main] n.t.d.s.b.s.n.s.e.SimpleEventListener    : event: net.teaho.demo.spring.boot.startup.none.spring.event.SimpleAppEvent[source=event source], source: event source
2020-05-20 18:30:12.444  INFO 81568 --- [           main] n.t.d.s.b.s.n.config.BeanConfiguration   : [net.teaho.demo.spring.boot.startup.none.spring.spi.DemoSpringLoaderImpl@c96a4ea]
2020-05-20 18:30:12.484  INFO 81568 --- [           main] n.t.d.s.b.s.n.s.l.LoggingLifeCycle       : In Life cycle bean start().
2020-05-20 18:30:12.496  INFO 81568 --- [           main] n.t.d.s.b.startup.none.ApplicationMain   : Started ApplicationMain in 1.573 seconds (JVM running for 3.195)
2020-05-20 18:30:12.496  INFO 81568 --- [           main] n.t.d.s.b.s.n.s.r.SimpleRunListener      : started, context: org.springframework.context.annotation.AnnotationConfigApplicationContext@1d730606, started on Mon May 25 18:30:11 CST 2020
2020-05-20 18:30:12.497  INFO 81568 --- [           main] n.t.d.s.b.s.n.s.r.EchoApplicationRunner  : EchoApplicationRunner running, args:org.springframework.boot.DefaultApplicationArguments@45673f68
2020-05-20 18:30:12.497  INFO 81568 --- [           main] n.t.d.s.b.s.n.s.r.EchoCommandLineRunner  : EchoCommandLineRunner running
2020-05-20 18:30:12.497  INFO 81568 --- [           main] n.t.d.s.b.s.n.s.r.SimpleRunListener      : running, context: org.springframework.context.annotation.AnnotationConfigApplicationContext@1d730606, started on Mon May 25 18:30:11 CST 2020
2020-05-20 18:30:12.500  INFO 81568 --- [       Thread-3] n.t.d.s.b.s.n.s.l.LoggingLifeCycle       : In Life cycle bean stop().

~~~














