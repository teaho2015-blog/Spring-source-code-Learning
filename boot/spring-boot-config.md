# SpringBoot外部化配置分析

## 什么是外部化配置？

什么是外部化配置，Spring Boot官方文档没有正面给出一个定义。只有这一说法，
> Spring Boot lets you externalize your configuration so that you can work with the same application code in different environments. You can use properties files, YAML files, environment variables, and command-line arguments to externalize configuration.

翻译：Spring Boot允许你外部化你的配置达到同一个应用代码运行于不同环境中。  
你能够通过properties文件、YAML文件、环境变量还有命令行参数来外部化配置。

我认为相对于应用的内部硬编码的配置，独立于应用程序外的在运行环境中的配置可称为外部配置。
多环境应用、中间件，通常有一些功能需要配置参数。

### 如何分析外部化配置

如果我们没看过源码，只是用过Spring Boot，
在我们眼中要使用外部化配置的话，就是配置配置文件（YAML文件），
最终在代码中通过@Value或者Environment或者@ConfigurationProperties的Bean的形式来使用配置。

那么抽象来看，我提出如下问题进行分析：
* 配置如何从配置文件加载到运行环境
* 配置如何最终绑定到bean
* 配置加载顺序（优先级）的加载点
* ~~配置动态刷新~~(目前Spring Boot没有该特性（有需求可参考Spring Cloud自行实现），Spring Cloud Context的源码实现了该功能，我在Spring Cloud源码分析中进行拆解)

## Spring Boot配置加载顺序

在编写一个Spring Boot应用时，
我们会发现多种改变配置的方式，可以在application.yml改变配置，也可以system properties等等。
那么当同时在多个地方定义了同一个配置项时，Spring Boot的选用优先级怎样的呢？

Spring Boot文档的外部化配置一节说到了配置加载点的优先级。（列表越前面的优先级越高）

>Spring Boot uses a very particular PropertySource order that is designed to allow sensible overriding of values. Properties are considered in the following order:
>1. Devtools global settings properties on your home directory (~/.spring-boot-devtools.properties when devtools is active).
>2. @TestPropertySource annotations on your tests.
>3. properties attribute on your tests. Available on @SpringBootTest and the test annotations for testing a particular slice of your application.
>4. Command line arguments.
>5. Properties from SPRING_APPLICATION_JSON (inline JSON embedded in an environment variable or system property).
>6. ServletConfig init parameters.
>7. ServletContext init parameters.
>8. JNDI attributes from java:comp/env.
>9. Java System properties (System.getProperties()).
>10. OS environment variables.
>11. A RandomValuePropertySource that has properties only in random.*.
>12. Profile-specific application properties outside of your packaged jar (application-{profile}.properties and YAML variants).
>13. Profile-specific application properties packaged inside your jar (application-{profile}.properties and YAML variants).
>14. Application properties outside of your packaged jar (application.properties and YAML variants).
>15. Application properties packaged inside your jar (application.properties and YAML variants).
>16. @PropertySource annotations on your @Configuration classes. Please note that such property sources are not added to the Environment until the application context is being refreshed. This is too late to configure certain properties such as logging.* and spring.main.* which are read before refresh begins.
>17. Default properties (specified by setting SpringApplication.setDefaultProperties).

文档（代码也是）将PropertySource作为外部化配置的API描述。  
1-3是开发测试环境的参数修改，  
4是命令行参数，比如 java -jar xx.jar --spring.config.name="hello"  
5是指spring.application.json这个参数。可用过系统参数设置或environment设置。  
9是指系统参数，比如java -jar -Dspring.config.name="xxx" xxx.jar  
12-15，带-{profile}的优先，同名配置文件jar包外优先。  
16是指声明了@Configuration和@PropertySource的类，举例来说 @PropertySource("classpath:/com/myco/app.properties")。
在ConfigurationClassParser中加载，该加载步骤发生在refresh时，所以对于logging.*和spring.main.*等在refresh前已经使用的配置来说是无效的。 
17 SpringApplication.setDefaultProperties设置的默认配置。


>SpringApplication loads properties from application.properties files in the following locations and adds them to the Spring Environment:
>
>1. A /config subdirectory of the current directory
>2. The current directory
>3. A classpath /config package
>4. The classpath root
>The list is ordered by precedence (properties defined in locations higher in the list override those defined in lower locations).


## 配置如何从配置文件加载到运行环境

前面我在[Spring boot启动原理及相关组件#prepareEnvironment](spring-boot-initialization.md#prepareenvironment)
分析了Spring Boot启动过程中prepareEnvironment方法的源码，注意三行，
~~~
   // Create and configure the environment
   ConfigurableEnvironment environment = getOrCreateEnvironment();
   configureEnvironment(environment, applicationArguments.getSourceArgs());
   //发布environment prepared事件
   listeners.environmentPrepared(environment);
~~~

getOrCreateEnvironment()里面，根据设置的webApplicationType去分别新建Environment。
非web程序新建StandardEnvironment实例。AbstractEnvironment有这两个属性，propertySource是
配置的API描述，注意Environment初始化还会调用模板方法customizePropertySources()，web还是非web Environment
都会初始化一些propertySource
~~~

	private final MutablePropertySources propertySources = new MutablePropertySources();

	private final ConfigurablePropertyResolver propertyResolver =
			new PropertySourcesPropertyResolver(this.propertySources);

~~~

注意configureEnvironment中的这个方法，这里将上一节说到的配置加载点第4、第17点包装成PropertySource加载到Environment实例中。
~~~
	protected void configurePropertySources(ConfigurableEnvironment environment,
			String[] args) {
		MutablePropertySources sources = environment.getPropertySources();
        // SpringApplication.setDefaultProperties设置的默认配置，如果设置了则加载进去
		if (this.defaultProperties != null && !this.defaultProperties.isEmpty()) {
			sources.addLast(
					new MapPropertySource("defaultProperties", this.defaultProperties));
		}
        // 命令行存在参数则包装成PropertySource加载到Environment中
		if (this.addCommandLineProperties && args.length > 0) {
			String name = CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME;
			if (sources.contains(name)) {
				PropertySource<?> source = sources.get(name);
				CompositePropertySource composite = new CompositePropertySource(name);
				composite.addPropertySource(new SimpleCommandLinePropertySource(
						"springApplicationCommandLineArgs", args));
				composite.addPropertySource(source);
				sources.replace(name, composite);
			}
			else {
				sources.addFirst(new SimpleCommandLinePropertySource(args));
			}
		}
	}

~~~

接着发布environment prepared事件后，触发`org.springframework.boot.context.config.ConfigFileApplicationListener`
类的执行：（触发的是ConfigFileApplicationListener.onApplicationEvent(ApplicationEvent event)方法，不过ConfigFileApplicationListener这个类我会基本注释一遍）
~~~
public class ConfigFileApplicationListener
		implements EnvironmentPostProcessor, SmartApplicationListener, Ordered {
    //省略代码...

	private static final String DEFAULT_PROPERTIES = "defaultProperties";

	// Note the order is from least to most specific (last one wins)
	private static final String DEFAULT_SEARCH_LOCATIONS = "classpath:/,classpath:/config/,file:./,file:./config/";

	private static final String DEFAULT_NAMES = "application";
	/**
	 * The "active profiles" property name.
	 */
	public static final String ACTIVE_PROFILES_PROPERTY = "spring.profiles.active";

	/**
	 * The "includes profiles" property name.
	 */
	public static final String INCLUDE_PROFILES_PROPERTY = "spring.profiles.include";

	/**
	 * The "config name" property name.
	 */
	public static final String CONFIG_NAME_PROPERTY = "spring.config.name";

	/**
	 * The "config location" property name.
	 */
	public static final String CONFIG_LOCATION_PROPERTY = "spring.config.location";

	/**
	 * The "config additional location" property name.
	 */
	public static final String CONFIG_ADDITIONAL_LOCATION_PROPERTY = "spring.config.additional-location";

	/**
	 * The default order for the processor.
	 */
	public static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;
    
    // 省略...
    
	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		if (event instanceof ApplicationEnvironmentPreparedEvent) {
			onApplicationEnvironmentPreparedEvent(
					(ApplicationEnvironmentPreparedEvent) event);
		}
		if (event instanceof ApplicationPreparedEvent) {
			onApplicationPreparedEvent(event);
		}
	}

	private void onApplicationEnvironmentPreparedEvent(
			ApplicationEnvironmentPreparedEvent event) {
        //加载EnvironmentPostProcessor3
        // org.springframework.boot.test.web.SpringBootTestRandomPortEnvironmentPostProcessor
        //org.springframework.boot.cloud.CloudFoundryVcapEnvironmentPostProcessor // 设置一些Vcap的属性
        //org.springframework.boot.env.SpringApplicationJsonEnvironmentPostProcessor // 就是前面“配置加载顺序”一节的第5点，处理spring.application.json
        //org.springframework.boot.env.SystemEnvironmentPropertySourceEnvironmentPostProcessor //对StandardEnvironment导入的SystemProperty进行处理
        //org.springframework.boot.devtools.env.DevToolsHomePropertiesPostProcessor // 对应前面“配置加载顺序”一节的第1点，加载.spring-boot-devtools.properties文件，PropertySource设置到最前面，
        //org.springframework.boot.devtools.env.DevToolsPropertyDefaultsPostProcessor // 包装一个MapPropertySource，加一些DevTool的属性
		List<EnvironmentPostProcessor> postProcessors = loadPostProcessors();
        // 把自己也加上去并排序
		postProcessors.add(this);
		AnnotationAwareOrderComparator.sort(postProcessors);
		for (EnvironmentPostProcessor postProcessor : postProcessors) {
            // 执行EnvironmentPostProcessor
			postProcessor.postProcessEnvironment(event.getEnvironment(),
					event.getSpringApplication());
		}
	}

	List<EnvironmentPostProcessor> loadPostProcessors() {
		return SpringFactoriesLoader.loadFactories(EnvironmentPostProcessor.class,
				getClass().getClassLoader());
	}

	protected void addPropertySources(ConfigurableEnvironment environment,
			ResourceLoader resourceLoader) {
        //对应配置加载顺序的第11点，增加对random.*配置，源码里将Random实例作为propertySource，解释key的时候(比如random.int(10))调用random实例对应方法。
		RandomValuePropertySource.addToEnvironment(environment);
        //内部类Loader
		new Loader(environment, resourceLoader).load();
	}

/*
    Loader类注意点：
    * 构造方法中加载PropertySourceLoader
			this.propertySourceLoaders = SpringFactoriesLoader.loadFactories(
					PropertySourceLoader.class, getClass().getClassLoader());
    * 核心方法load()
    * 属性private Map<Profile, MutablePropertySources> loaded
*/   
        
		public void load() {
			this.profiles = new LinkedList<>();
			this.processedProfiles = new LinkedList<>();
			this.activatedProfiles = false;
			this.loaded = new LinkedHashMap<>();
            //判断并设置environment的activeProfile。
			initializeProfiles();
			while (!this.profiles.isEmpty()) {
				Profile profile = this.profiles.poll();
				if (profile != null && !profile.isDefaultProfile()) {
					addProfileToEnvironment(profile.getName());
				}
				load(profile, this::getPositiveProfileFilter,
						addToLoaded(MutablePropertySources::addLast, false));
				this.processedProfiles.add(profile);
			}
			resetEnvironmentProfiles(this.processedProfiles);
            // 加载文件到成PropertySource并塞到MutablePropertySources中，注意loadDocuments、addToLoaded方法和YamlPropertySourceLoader类和Document类
			load(null, this::getNegativeProfileFilter,
					addToLoaded(MutablePropertySources::addFirst, true));
            //将加载到类中的MutablePropertySources丢到Environment中
			addLoadedPropertySources();
		}

}

~~~

### 小结

以上，分析完。在上面分析中，可以一一找到Spring Boot外部化配置加载顺序一节提到的17个配置加载点。  
总得说，外部化配置加载到应用中，存储在AbstractEnvironment的MutablePropertySources属性中。  
在prepareEnvironment时开始进行加载。  
在发布environmentPrepared事件后，会触发ConfigFileApplicationListener去执行EnvironmentPostProcessors最终加载yaml或properties文件。

## 配置如何最终绑定到bean

我在下一节“例子”中，  
写了基本的外部化配置例子，
写了两种外部化配置绑定bean的方式的例子。
一种是通过`EnableConfigurationProperties`注解的value填上配置类，
~~~
@EnableConfigurationProperties(SimpleAnnotatedEnableConfigProperties.class)
~~~
第二种是直接把配置类在Configuration类中配置为bean。
~~~
    @Bean
    public SimpleConfigProperties simpleConfigProperties() {
        return new SimpleConfigProperties();
    }
~~~
两个例子其实源代码上处理的逻辑殊途同归。

@Configuration的源码处理逻辑，我在自动装配一文已经谈过，  
这里接着说说源码对于`EnableConfigurationProperties`和`ConfigurationProperties`注解的处理。

Spring context在refresh时，
期间会扫描到此类：
~~~
org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration

@Configuration
@EnableConfigurationProperties
public class ConfigurationPropertiesAutoConfiguration { }

~~~

注解`EnableConfigurationProperties`会`@Import(EnableConfigurationPropertiesImportSelector.class)`
EnableConfigurationPropertiesImportSelector做的是什么呢？它引入了这两个ImportBeanDefinitionRegistrar：
~~~
	private static final String[] IMPORTS = {
			ConfigurationPropertiesBeanRegistrar.class.getName(),
			ConfigurationPropertiesBindingPostProcessorRegistrar.class.getName() };

	@Override
	public String[] selectImports(AnnotationMetadata metadata) {
		return IMPORTS;
	}

~~~
ImportBeanDefinitionRegistrar是一个接口，实现了该接口的类用@Import或者importSelector导入，
作用是在处理@Configuration注解是协助加入一些额外的bean definition。

* ConfigurationPropertiesBeanRegistrar负责注册EnableConfigurationProperties注解value上的类

~~~ConfigurationPropertiesBeanRegistrar（做了一些简要注释）

	public static class ConfigurationPropertiesBeanRegistrar
			implements ImportBeanDefinitionRegistrar {
        
		@Override
		public void registerBeanDefinitions(AnnotationMetadata metadata,
				BeanDefinitionRegistry registry) {
			getTypes(metadata).forEach((type) -> register(registry,
					(ConfigurableListableBeanFactory) registry, type));
		}
        //获取EnableConfigurationProperties注解的value提供的类
		private List<Class<?>> getTypes(AnnotationMetadata metadata) {
			MultiValueMap<String, Object> attributes = metadata
					.getAllAnnotationAttributes(
							EnableConfigurationProperties.class.getName(), false);
			return collectClasses((attributes != null) ? attributes.get("value")
					: Collections.emptyList());
		}

		private List<Class<?>> collectClasses(List<?> values) {
			return values.stream().flatMap((value) -> Arrays.stream((Object[]) value))
					.map((o) -> (Class<?>) o).filter((type) -> void.class != type)
					.collect(Collectors.toList());
		}
        // 检查是否有该bean的definition，没有则注册
		private void register(BeanDefinitionRegistry registry,
				ConfigurableListableBeanFactory beanFactory, Class<?> type) {
			String name = getName(type);
			if (!containsBeanDefinition(beanFactory, name)) {
				registerBeanDefinition(registry, name, type);
			}
		}

		private String getName(Class<?> type) {
			ConfigurationProperties annotation = AnnotationUtils.findAnnotation(type,
					ConfigurationProperties.class);
			String prefix = (annotation != null) ? annotation.prefix() : "";
			return (StringUtils.hasText(prefix) ? prefix + "-" + type.getName()
					: type.getName());
		}

		private boolean containsBeanDefinition(
				ConfigurableListableBeanFactory beanFactory, String name) {
			if (beanFactory.containsBeanDefinition(name)) {
				return true;
			}
			BeanFactory parent = beanFactory.getParentBeanFactory();
			if (parent instanceof ConfigurableListableBeanFactory) {
				return containsBeanDefinition((ConfigurableListableBeanFactory) parent,
						name);
			}
			return false;
		}
        // 检测是否提供的类是否标记了ConfigurationProperties注解，如果有则注册bean definition
		private void registerBeanDefinition(BeanDefinitionRegistry registry, String name,
				Class<?> type) {
			assertHasAnnotation(type);
			GenericBeanDefinition definition = new GenericBeanDefinition();
			definition.setBeanClass(type);
			registry.registerBeanDefinition(name, definition);
		}

		private void assertHasAnnotation(Class<?> type) {
			Assert.notNull(
					AnnotationUtils.findAnnotation(type, ConfigurationProperties.class),
					() -> "No " + ConfigurationProperties.class.getSimpleName()
							+ " annotation found on  '" + type.getName() + "'.");
		}

	}

~~~


* ConfigurationPropertiesBindingPostProcessorRegistrar负责注册  
  `ConfigurationPropertiesBindingPostProcessor`去绑定propertySource(外部配置)到配置bean上

~~~ConfigurationPropertiesBindingPostProcessor（做了一些简要注释）
public class ConfigurationPropertiesBindingPostProcessor implements BeanPostProcessor,
		PriorityOrdered, ApplicationContextAware, InitializingBean {

	/**
	 * The bean name that this post-processor is registered with.
	 */
	public static final String BEAN_NAME = ConfigurationPropertiesBindingPostProcessor.class
			.getName();

	/**
	 * The bean name of the configuration properties validator.
	 */
	public static final String VALIDATOR_BEAN_NAME = "configurationPropertiesValidator";

	private ConfigurationBeanFactoryMetadata beanFactoryMetadata;

	private ApplicationContext applicationContext;

	private ConfigurationPropertiesBinder configurationPropertiesBinder;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext)
			throws BeansException {
		this.applicationContext = applicationContext;
	}
    
    //重写InitializingBean的方法，将ConfigurationBeanFactoryMetadata和ConfigurationPropertiesBinder实例化
	@Override
	public void afterPropertiesSet() throws Exception {
		// We can't use constructor injection of the application context because
		// it causes eager factory bean initialization
		this.beanFactoryMetadata = this.applicationContext.getBean(
				ConfigurationBeanFactoryMetadata.BEAN_NAME,
				ConfigurationBeanFactoryMetadata.class);
		this.configurationPropertiesBinder = new ConfigurationPropertiesBinder(
				this.applicationContext, VALIDATOR_BEAN_NAME);
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 1;
	}
    
    // 在bean初始化前调用，获取ConfigurationProperties判断是否为空，并进行绑定
	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName)
			throws BeansException {
		ConfigurationProperties annotation = getAnnotation(bean, beanName,
				ConfigurationProperties.class);
		if (annotation != null) {
			bind(bean, beanName, annotation);
		}
		return bean;
	}
    
    //这里进行绑定属性到配置bean上
	private void bind(Object bean, String beanName, ConfigurationProperties annotation) {
		ResolvableType type = getBeanType(bean, beanName);
		Validated validated = getAnnotation(bean, beanName, Validated.class);
		Annotation[] annotations = (validated != null)
				? new Annotation[] { annotation, validated }
				: new Annotation[] { annotation };
		Bindable<?> target = Bindable.of(type).withExistingValue(bean)
				.withAnnotations(annotations);
		try {
			this.configurationPropertiesBinder.bind(target);
		}
		catch (Exception ex) {
			throw new ConfigurationPropertiesBindException(beanName, bean, annotation,
					ex);
		}
	}

	private ResolvableType getBeanType(Object bean, String beanName) {
		Method factoryMethod = this.beanFactoryMetadata.findFactoryMethod(beanName);
		if (factoryMethod != null) {
			return ResolvableType.forMethodReturnType(factoryMethod);
		}
		return ResolvableType.forClass(bean.getClass());
	}

	private <A extends Annotation> A getAnnotation(Object bean, String beanName,
			Class<A> type) {
		A annotation = this.beanFactoryMetadata.findFactoryAnnotation(beanName, type);
		if (annotation == null) {
			annotation = AnnotationUtils.findAnnotation(bean.getClass(), type);
		}
		return annotation;
	}

}

~~~

由上面ConfigurationPropertiesBindingPostProcessor的代码分析可知，  
为配置bean绑定属性的是内部类ConfigurationPropertiesBinder的bind方法。

~~~ConfigurationPropertiesBinder
class ConfigurationPropertiesBinder {

	private final ApplicationContext applicationContext;

	private final PropertySources propertySources;

	private final Validator configurationPropertiesValidator;

	private final boolean jsr303Present;

	private volatile Validator jsr303Validator;

	private volatile Binder binder;

	ConfigurationPropertiesBinder(ApplicationContext applicationContext,
			String validatorBeanName) {
		this.applicationContext = applicationContext;
        //使用PropertySourcesDeducer获取当前应用上下文的配置源信息
		this.propertySources = new PropertySourcesDeducer(applicationContext)
				.getPropertySources();
        //获取配置属性校验器
		this.configurationPropertiesValidator = getConfigurationPropertiesValidator(
				applicationContext, validatorBeanName);
        //验证是否存在实现jsr303规范的jar包，jsr303就是Validator的JAVA子规范，存在则校验配置
		this.jsr303Present = ConfigurationPropertiesJsr303Validator
				.isJsr303Present(applicationContext);
	}

	public void bind(Bindable<?> target) {
		ConfigurationProperties annotation = target
				.getAnnotation(ConfigurationProperties.class);
		Assert.state(annotation != null,
				() -> "Missing @ConfigurationProperties on " + target);
        //获取校验器
		List<Validator> validators = getValidators(target);
        //获取绑定处理器，BindHandler是一个装饰模式的实现
		BindHandler bindHandler = getBindHandler(annotation, validators);
        //新建Binder类进行配置绑定，
        //annotation.prefix()是@ConfigurationProperties注解的前缀值；target是待绑定的bean；bindhandler是绑定处理链用来做一些绑定属性外的额外逻辑，比如验证属性合法性等
		getBinder().bind(annotation.prefix(), target, bindHandler);
	}

//省略
    
    //新建Binder类
	private Binder getBinder() {
		if (this.binder == null) {
			this.binder = new Binder(getConfigurationPropertySources(),
					getPropertySourcesPlaceholdersResolver(), getConversionService(),
					getPropertyEditorInitializer());
		}
		return this.binder;
	}

//省略

~~~


最终，通过Binder去给bean绑定属性。   
至此，`配置如何最终绑定到bean`这一节分析完成。

注：Binder类和BinderHandler类是Spring Boot2.0后添加的，至于它常用例子和代码分析，放在这里模糊了重点。我把它放在下一小节单独聊聊。

### 拓展解读：Binder

#### 例子

我在[github|spring-boot-externalized-configuration-demo](https://github.com/teaho2015-blog/spring-source-code-learning-demo/tree/master/spring-boot-externalized-configuration-demo)
的test目录下写了Binder的test cases（BinderTest类），有兴趣可参考一下。

#### 分析

我在例子中的第二个test case是和外部化配置绑定到bean（即ConfigurationPropertiesBinder）的源码实现是基本一致的。
~~~
    //省略

    @Test(expected = BindException.class)
    public void testBinder_withVilidator_bindFail() throws Exception {
        BindHandler handler = new IgnoreTopLevelConverterNotFoundBindHandler();
        handler = new ValidationBindHandler(handler, new SimpleConfigurationPropertiesJsr303Validator(configurableApplicationContext));
        handler = new SimpleEchoBindHandler(handler);
        TestPropertyConfig bc = Binder.get(environment)
            .bind("binder.test", Bindable.of(TestPropertyConfig.class), handler)
            .get();

    //省略
~~~

从这里为入口，我们看看Binder做了什么。

说几个Binder相关的类，方便理解：
* `Bindable<T>`是对待绑定配置属性实例的一些封装实现，里面提取了配置属性类的类型、注解作为类属性。
* `BindHandler`绑定回调处理器，有`onStart`、`onSuccess`、`onFailure`、`onFinish`方法，在Binder的绑定执行过程中作一些回调函数处理。
* `BindResult`对绑定结果作容器包装，和`Optional`的功能类似。
* `BindContext`用于绑定过程中记录递归配置深度和记录待绑定bean队列
* `BeanBinder` Internal strategy used by Binder to bind beans.
* `BeanPropertyBinder` 一个绑定器接口，被BeanBinder的实现类用于递归绑定bean属性。

首先看Binder的get方法
~~~
    
	public static Binder get(Environment environment) {
		return new Binder(ConfigurationPropertySources.get(environment),
				new PropertySourcesPlaceholdersResolver(environment));
	}
~~~
`ConfigurationPropertySources.get(environment)`
是将environment底层存储外部化配置的属性MutablePropertySources转换为SpringConfigurationPropertySource以便于更快执行。  
`PropertySourcesPlaceholdersResolver`是处理形如`${xxx}`这样的占位符的类。

进入到bind方法，bind方法在Binder类中有很多重载实现，看看核心方法(该方法会被调用多次)：
~~~
	protected final <T> T bind(ConfigurationPropertyName name, Bindable<T> target,
			BindHandler handler, Context context, boolean allowRecursiveBinding) {
		context.clearConfigurationProperty();
		try {
            //执行handler的onStart方法
			target = handler.onStart(name, target, context);
			if (target == null) {
				return null;
			}
			Object bound = bindObject(name, target, handler, context,
					allowRecursiveBinding);
            // 处理绑定结果，如果bound有值则会执行handler的onSuccess并使用convert进行转换，无论结果，最终执行handler的onFinish方法
			return handleBindResult(name, target, handler, context, bound);
		}
		catch (Exception ex) {
            // 里面会执行handler的failure方法
			return handleBindError(name, target, handler, context, ex);
		}
	}

~~~

另一个核心方法：
~~~

	private <T> Object bindObject(ConfigurationPropertyName name, Bindable<T> target,
			BindHandler handler, Context context, boolean allowRecursiveBinding) {
        // 找到是否存在当前key（即这个ConfigurationPropertyName）的配置
		ConfigurationProperty property = findProperty(name, context);
		if (property == null && containsNoDescendantOf(context.getSources(), name)) {
			return null;
		}
		AggregateBinder<?> aggregateBinder = getAggregateBinder(target, context);
		if (aggregateBinder != null) {
			return bindAggregate(name, target, handler, context, aggregateBinder);
		}
        //找到了的话，往target绑定配置属性
		if (property != null) {
			try {
				return bindProperty(target, context, property);
			}
			catch (ConverterNotFoundException ex) {
                //有可能是报异常，因为有可能属性也是bean
				// We might still be able to bind it as a bean
				Object bean = bindBean(name, target, handler, context,
						allowRecursiveBinding);
				if (bean != null) {
					return bean;
				}
				throw ex;
			}
		}
        // 没找到对应name，去绑定bean
		return bindBean(name, target, handler, context, allowRecursiveBinding);
	}
~~~

bindBean方法，注意绑定属性采用递归调用
~~~ bindBean

	private Object bindBean(ConfigurationPropertyName name, Bindable<?> target,
			BindHandler handler, Context context, boolean allowRecursiveBinding) {
		if (containsNoDescendantOf(context.getSources(), name)
				|| isUnbindableBean(name, target, context)) {
			return null;
		}
        // 绑定属性的函数接口，递归调用。
        //比如，我的例子中在运行时，propertyName、propertyTarget可以分别是“name”和 java.lang.String，然后在bindObject的bindProperty将拿到的外部化配置的value值经过转换返回回来
		BeanPropertyBinder propertyBinder = (propertyName, propertyTarget) -> bind(
				name.append(propertyName), propertyTarget, handler, context, false);
		Class<?> type = target.getType().resolve(Object.class);
		if (!allowRecursiveBinding && context.hasBoundBean(type)) {
			return null;
		}
		return context.withBean(type, () -> {
			Stream<?> boundBeans = BEAN_BINDERS.stream()
					.map((b) -> b.bind(name, target, context, propertyBinder));
			return boundBeans.filter(Objects::nonNull).findFirst().orElse(null);
		});
	}
~~~

至此，分析完Binder的实现了。



### 小结

我们从上面分析代码可以看到，Spring Boot的外部化配置的整个结构，
* 他的抽象设计很合理（思路是面向对象）--environment放到里面的propertySource中而不是直接将外部化配置保存到配置bean。
这有利于我们对Environment（也可以说时运行时环境变量）进行拓展。  
* 同时，从上面源码来看，设计者预留了不少拓展点可供实现自己的逻辑。

说回外部化配置绑定到bean。
* 我们通过@ConfigurationProperties配置prefix
* ConfigurationPropertiesAutoConfiguration的@EnableConfigurationProperties注解引入registar进行一些预处理
* 最终注册并使用ConfigurationPropertiesBindingPostProcessor进行属性绑定

## 例子

我在github上写了一个基本的外部化配置的demo：
[github|spring-boot-externalized-configuration-demo](https://github.com/teaho2015-blog/spring-source-code-learning-demo/tree/master/spring-boot-externalized-configuration-demo)


## 后记

Spring Boot 2.4后，加载配置文件有一个大变动，不再使用ConfigFileApplicationListener加载配置，并作出了一堆特性优化。

## Reference





