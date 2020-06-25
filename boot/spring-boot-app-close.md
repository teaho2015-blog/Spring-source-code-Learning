# Spring Boot应用关闭分析


## 应用关闭方式

1. 使用spring容器的close方法关闭。
2. 使用SpringApplication的exit方法。
3. 使用Actuator的shutdown接口。

请参考https://github.com/teaho2015-blog/spring-source-code-learning-demo的spring boot mvc shutdown模块，我分别将。


## spring容器close代码分析

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
            // 期间会触发@PreDestroy、DisposableBean接口方法、@Bean的destroyMethod
			destroyBeans();

			// Close the state of this context itself.
			closeBeanFactory();

			// Let subclasses do some final clean-up if they wish...
            // 模板方法，比如，AnnotationConfigServletApplicationContext会触发tomcat服务器的关闭和释放
			onClose();

			// 重置listeners为初始状态，因为在容器启动过程中会对ApplicationListener做了一些更改
			if (this.earlyApplicationListeners != null) {
				this.applicationListeners.clear();
				this.applicationListeners.addAll(this.earlyApplicationListeners);
			}

			// 
			this.active.set(false);
		}
~~~


## 应用关闭扩展点

@PreDestroy

ExitCodeGenerator

SpringApplication#exit()


ApplicationContext#close()

Event

Actuator  shutdown接口

MBean





拓展写：

kill & kill -9 JVM做了什么变化

端口释放等问题


