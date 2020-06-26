# Spring Boot应用关闭分析

## 前言

该篇介绍优雅关闭SpringBoot 应用的方式，及一些分析。

## 应用关闭方式

### 使用spring容器的close方法关闭。

可通过在代码中获取SpringContext并调用close方法，去关闭容器。

### 使用SpringApplication的exit方法。

~~~ SpringApplication#exit

public static int exit(ApplicationContext context,
			ExitCodeGenerator... exitCodeGenerators) {
		Assert.notNull(context, "Context must not be null");
		int exitCode = 0;
		try {
			try {
                //获取ExitCodeGenerator的Bean并用ExitCodeGenerators管理
				ExitCodeGenerators generators = new ExitCodeGenerators();
				Collection<ExitCodeGenerator> beans = context
						.getBeansOfType(ExitCodeGenerator.class).values();
				generators.addAll(exitCodeGenerators);
				generators.addAll(beans);
				exitCode = generators.getExitCode();
				if (exitCode != 0) {
                    // 发布ExitCodeEvent事件
					context.publishEvent(new ExitCodeEvent(context, exitCode));
				}
			}
			finally {
				close(context);
			}
		}
		catch (Exception ex) {
			ex.printStackTrace();
			exitCode = (exitCode != 0) ? exitCode : 1;
		}
		return exitCode;
	}
~~~

上述代码，总的来说就是，获取ExitCodeGenerator的Bean并用ExitCodeGenerators管理，
注意getExitCode()的实现是取ExitCodeGenerator集合中最大的exitCode作为最终exitCode，

最后，关闭容器。

#### exitCode

SpringApplication#exit方法返回的exitCode还需要自行调用System#exit方法去指定。
该System#exit(int code)的参数，能被父进程获取并使用。一般按照惯例0为程序正常退出，非0位不正常退出。
我写了的运行demo：
~~~

@Slf4j
@SpringBootApplication
public class ApplicationMainShutDownBySpringApplication {

    public static void main(String[] args) {

        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(ApplicationMainShutDownBySpringApplication.class).build().run(args);

        int exitCode = SpringApplication.exit(ctx);
        log.info("exitCode is {}!", exitCode);
        System.exit(exitCode);
    }

    @Bean
    public ExitCodeGenerator exitCodeGenerator() {
        return () -> 10;
    }
}
~~~

~~~ 执行window bat
>java -jar spring-boot-mvc-shutdown-demo.jar & echo %ERRORLEVEL%

省略其他日志最终输出:
10

~~~

可以看最终输出是：10。

### 使用Actuator的shutdown http接口或JMX

可参考Actuator包的ShutdownEndpoint,实质上是调用spring容器的close方法关闭的。

http方式关闭：
![](Actuator_shutdown.png)

JMX方式关闭：
![](jconsole_jmx_shutdown.png)


### kill进程

一般的kill(kill -15)会触发应用在refreshContext时（并且SpringApplication实例的registerShutdownHook为true时）加上的注册到JVM的shutdownhook。

~~~ 注册shutdownhook代码
	public void registerShutdownHook() {
		if (this.shutdownHook == null) {
			// No shutdown hook registered yet.
			this.shutdownHook = new Thread() {
				@Override
				public void run() {
					synchronized (startupShutdownMonitor) {
						doClose();
					}
				}
			};
			Runtime.getRuntime().addShutdownHook(this.shutdownHook);
		}
	}
~~~

## spring容器close代码分析

这里对容器关闭进行一些分析，以注释的形式写在下面。
~~~

	/**
	 * Close this application context, destroying all beans in its bean factory.
	 * <p>Delegates to {@code doClose()} for the actual closing procedure.
	 * Also removes a JVM shutdown hook, if registered, as it's not needed anymore.
	 * @see #doClose()
	 * @see #registerShutdownHook()
	 */
	@Override
	public void close() {
		synchronized (this.startupShutdownMonitor) {
            //委托给钩子方法doClose去做
			doClose();
			// If we registered a JVM shutdown hook, we don't need it anymore now:
			// We've already explicitly closed the context.
			if (this.shutdownHook != null) {
				try {
                    //去掉shutdown hook
					Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
				}
				catch (IllegalStateException ex) {
					// ignore - VM is already shutting down
				}
			}
		}
	}

~~~


~~~
	protected void doClose() {
		// Check whether an actual close attempt is necessary...
		if (this.active.get() && this.closed.compareAndSet(false, true)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Closing " + this);
			}

			LiveBeansView.unregisterApplicationContext(this);

			try {
				// Publish shutdown event.
                //发布事件，有需要，可写ApplicationListener对ContextClosedEvent事件进行监听，在容器关闭时执行自定义的操作
				publishEvent(new ContextClosedEvent(this));
			}
			catch (Throwable ex) {
				logger.warn("Exception thrown from ApplicationListener handling ContextClosedEvent", ex);
			}

			// Stop all Lifecycle beans, to avoid delays during individual destruction.
            //触发lifecycle bean的关闭周期
			if (this.lifecycleProcessor != null) {
				try {
					this.lifecycleProcessor.onClose();
				}
				catch (Throwable ex) {
					logger.warn("Exception thrown from LifecycleProcessor on context close", ex);
				}
			}

			// Destroy all cached singletons in the context's BeanFactory.
            // 期间会触发@PreDestroy、DisposableBean接口方法、@Bean的destroyMethod等等，具体执行顺序请参照BeanFactory接口的JavaDoc，里面定义了初始化和销毁期的方法执行顺序。
			destroyBeans();

			// Close the state of this context itself.
            //目前没做什么操作
			closeBeanFactory();

			// Let subclasses do some final clean-up if they wish...
            // 模板方法，比如，AnnotationConfigServletApplicationContext会触发tomcat服务器的关闭和释放
			onClose();

			// 重置listeners为初始状态，因为在容器启动过程中会对ApplicationListener做了一些更改
			if (this.earlyApplicationListeners != null) {
				this.applicationListeners.clear();
				this.applicationListeners.addAll(this.earlyApplicationListeners);
			}

			// 将容器的激活状态设为false
			this.active.set(false);
		}
~~~


## 示例

请参考https://github.com/teaho2015-blog/spring-source-code-learning-demo的spring boot mvc shutdown模块，
我分别将上述关闭方式和拓展点（事件，LifeCycleProcessor等）写了demo。



## 容器关闭拓展点

容器关闭时的日志如下，
~~~ 日志输出
2020-06-27 01:23:36.294  INFO 241764 --- [           main] .w.s.s.ApplicationMainShutDownByActuator : Actuator shutdown result: {"message":"Shutting down, bye..."}
2020-06-27 01:23:36.761  INFO 241764 --- [      Thread-25] s.s.ApplicationContextCloseEventListener : event: org.springframework.context.event.ContextClosedEvent[source=org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext@2f48b3d2, started on Sat Jun 27 01:22:57 CST 2020], source: org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext@2f48b3d2, started on Sat Jun 27 01:22:57 CST 2020
2020-06-27 01:23:39.914  INFO 241764 --- [      Thread-25] n.t.d.s.w.s.s.spring.LoggingLifeCycle    : In Life cycle bean stop().
2020-06-27 01:23:39.915  INFO 241764 --- [      Thread-25] o.s.s.concurrent.ThreadPoolTaskExecutor  : Shutting down ExecutorService 'applicationTaskExecutor'
2020-06-27 01:23:39.916  INFO 241764 --- [      Thread-25] n.t.d.s.w.s.shutdown.bean.SimpleBean     : @PreDestroy!
2020-06-27 01:23:39.916  INFO 241764 --- [      Thread-25] n.t.d.s.w.s.shutdown.bean.SimpleBean     : DisposableBean is destroying!
2020-06-27 01:23:39.916  INFO 241764 --- [      Thread-25] n.t.d.s.w.s.shutdown.bean.SimpleBean     : On my way to destroy!
~~~

可看到的是一般可供使用的容器关闭时的拓展点不多，分别有这两个：
* 监听ContextClosedEvent事件，对应例子是demo中的ApplicationContextCloseEventListener类。
* LifeCycle/SmartLifeCycle的stop()方法，对应例子是demo中的LoggingLifeCycle类。

他们的触发时机在上面的close代码的分析中有注释。


