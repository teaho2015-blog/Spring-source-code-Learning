# Spring Boot自动装配

## 什么是自动转配

我们一般用如下面代码便运行起一个SpringBoot应用，  
可是，
* Spring Boot是用何种方式扫描我们项目里被@Component注解类里的@Autowired的属性呢，
* 又是如何加载我们引入进来的starter-redis，starter-web、starter-webflux等，他们是如何初始化的呢。

我们可以带着这些疑问去挖掘真相。

Spring Boot官方文档对于自动装配的描述：

> Spring Boot auto-configuration attempts to automatically configure your Spring application based on the jar dependencies that you have added. For example, if HSQLDB is on your classpath, and you have not manually configured any database connection beans, then Spring Boot auto-configures an in-memory database.
>  
>  You need to opt-in to auto-configuration by adding the @EnableAutoConfiguration or @SpringBootApplication annotations to one of your @Configuration classes.


Spring Boot自动装配致力于基于你所添加的依赖去自动化配置你的Spring应用。举例来说如果在类路径上存在HSQLDB，则用户无需手动配置任何数据库连接，Spring Boot会自动化配置一个内存数据库。

你能有选择地通过添加`@EnableAutoConfiguration or @SpringBootApplication`注解到你的一个带有@Configuration的类中，达到启动自动装配功能的效果。

## @SpringBootApplication分析

Spring的注解历史我在该篇文章有做说明：[Spring注解历史](./spring-annotation-history.md)。
知道有哪些注解及作用有便于我接下来的代码分析。

在“什么是自动装配”一节中，我写了一个启动Spring Boot应用的代码，并抛出了问题。
~~~
@SpringBootApplication
public class ApplicationMain {

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(ApplicationMain.class, args);
    }

}
~~~

这代码就加了@SpringBootApplication注解和`SpringApplication.run(ApplicationMain.class, args)`。
`SpringApplication.run(ApplicationMain.class, args)`我在[Spring boot启动原理](./spring-boot-initialization.md)已经做了源码分析。  
接下来，我们看看@SpringBootApplication做了什么。

~~~@SpringBootApplication

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(excludeFilters = {
		@Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
		@Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class) })
public @interface SpringBootApplication {

	@AliasFor(annotation = EnableAutoConfiguration.class)
	Class<?>[] exclude() default {};

	@AliasFor(annotation = EnableAutoConfiguration.class)
	String[] excludeName() default {};

	@AliasFor(annotation = ComponentScan.class, attribute = "basePackages")
	String[] scanBasePackages() default {};

	@AliasFor(annotation = ComponentScan.class, attribute = "basePackageClasses")
	Class<?>[] scanBasePackageClasses() default {};

}

~~~

* @SpringBootConfiguration 相当于@Configuration，意义在于能让@SpringBootApplication的配置类能被@ComponentScan扫描。
* @EnableAutoConfiguration 核心注解，用于启用自动装配，路径扫描、组件装配等通过此注解实现。
* @ComponentScan 用来扫描标注了@Component的类。注意此处添加了自定义扫描规则。
* Class<?>[] exclude() default {} 将给出的自动装配类(.class)排除装配。
* String[] excludeName() default {} 将给出的自动装配类名排除装配。
* String[] scanBasePackages() default {} 声明扫描组件的根包（可声明多个）。
* Class<?>[] scanBasePackageClasses() default {} 声明多个类或接口的类，扫描类所在包下的所有组件。

其实我单说@EnableAutoConfiguration分析就能够说清自动装配了，
不过，我们不禁会疑问在@EnableAutoConfiguration中的@Import的触发时机，
@ComponentScan起了什么作用，@SpringBootConfiguration由起了什么作用。（其实这些算是Spring Context的内容）  
所以，既然我前面分析过Spring Boot的整个初始化流程，这里，我会将Spring应用初始化作为切入点，
从@Configuration注解的处理逻辑开始，对自动装配和各注解进行讲解。

### @Configuration加载实现分析

<!--


在SpringApplication的prepareContext的`load(context, sources.toArray(new Object[0]));`  
=> BeanDefinitionLoader的load  
=> ClassPathBeanDefinitionScanner scanner  
=> AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry)  
=> register ConfigurationClassPostProcessor

在refresh Application context时，invokeBeanFactoryPostProcessors
=> PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors(beanFactory, getBeanFactoryPostProcessors())
=> ConfigurationClassPostProcessor.processConfigBeanDefinitions（同时看看ConfigurationClassParser）
=> ConfigurationClassBeanDefinitionReader.loadBeanDefinitionsForConfigurationClass、loadBeanDefinitionsFromRegistrars
=> ConfigurationPropertiesAutoConfiguration引入的EnableConfigurationPropertiesImportSelector
   引入的ConfigurationPropertiesBeanRegistrar（负责注册EnableConfigurationProperties注解value上的类）、ConfigurationPropertiesBindingPostProcessorRegistrar（负责注册ConfigurationPropertiesBindingPostProcessor去处理绑定propertySource(外部配置)到bean上）
-->

上面说过，@SpringBootConfiguration相当于@Configuration。

在SpringApplication的run阶段，
在实例化应用上下文时，如果创建的是**AnnotationConfig的ApplicationContext**
都会初始化这两个类。
~~~
        //是一个便利的类，用来读取编程式注解的bean
		this.reader = new AnnotatedBeanDefinitionReader(this);
		this.scanner = new ClassPathBeanDefinitionScanner(this);
~~~

AnnotatedBeanDefinitionReader实例化时会调用如下方法
`AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);`。
注意该方法注册的PostProcessor。
~~~
    //加入ConfigurationClassPostProcessor用于处理@Configuration标注的类及类里面一系列逻辑
    if (!registry.containsBeanDefinition(CONFIGURATION_ANNOTATION_PROCESSOR_BEAN_NAME)) {
        RootBeanDefinition def = new RootBeanDefinition(ConfigurationClassPostProcessor.class);
        def.setSource(source);
        beanDefs.add(registerPostProcessor(registry, def, CONFIGURATION_ANNOTATION_PROCESSOR_BEAN_NAME));
    }
    //加入AutowiredAnnotationBeanPostProcessor，用于处理@Autowired注解
    if (!registry.containsBeanDefinition(AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME)) {
        RootBeanDefinition def = new RootBeanDefinition(AutowiredAnnotationBeanPostProcessor.class);
        def.setSource(source);
        beanDefs.add(registerPostProcessor(registry, def, AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME));
    }
    
    // Check for JSR-250 support, and if present add the CommonAnnotationBeanPostProcessor.
    // 检查JSR-250，即对@PostConstruct和@PreDestroy的处理，并加入CommonAnnotationBeanPostProcessor
    if (jsr250Present && !registry.containsBeanDefinition(COMMON_ANNOTATION_PROCESSOR_BEAN_NAME)) {
        RootBeanDefinition def = new RootBeanDefinition(CommonAnnotationBeanPostProcessor.class);
        def.setSource(source);
        beanDefs.add(registerPostProcessor(registry, def, COMMON_ANNOTATION_PROCESSOR_BEAN_NAME));
    }

    // Check for JPA support, and if present add the PersistenceAnnotationBeanPostProcessor.
    if (jpaPresent && !registry.containsBeanDefinition(PERSISTENCE_ANNOTATION_PROCESSOR_BEAN_NAME)) {
        RootBeanDefinition def = new RootBeanDefinition();
        try {
            def.setBeanClass(ClassUtils.forName(PERSISTENCE_ANNOTATION_PROCESSOR_CLASS_NAME,
                    AnnotationConfigUtils.class.getClassLoader()));
        }
        catch (ClassNotFoundException ex) {
            throw new IllegalStateException(
                    "Cannot load optional framework class: " + PERSISTENCE_ANNOTATION_PROCESSOR_CLASS_NAME, ex);
        }
        def.setSource(source);
        beanDefs.add(registerPostProcessor(registry, def, PERSISTENCE_ANNOTATION_PROCESSOR_BEAN_NAME));
    }
    //加入EventListenerMethodProcessor用于处理带有@EventListener注解的方法
    if (!registry.containsBeanDefinition(EVENT_LISTENER_PROCESSOR_BEAN_NAME)) {
        RootBeanDefinition def = new RootBeanDefinition(EventListenerMethodProcessor.class);
        def.setSource(source);
        beanDefs.add(registerPostProcessor(registry, def, EVENT_LISTENER_PROCESSOR_BEAN_NAME));
    }
    // 给EventListenerMethodProcessor使用，用于包装一个ApplicationListenerMethodAdapter去监听具体事件
    if (!registry.containsBeanDefinition(EVENT_LISTENER_FACTORY_BEAN_NAME)) {
        RootBeanDefinition def = new RootBeanDefinition(DefaultEventListenerFactory.class);
        def.setSource(source);
        beanDefs.add(registerPostProcessor(registry, def, EVENT_LISTENER_FACTORY_BEAN_NAME));
    }
~~~

接下来分析`ConfigurationClassPostProcessor`。

在refresh Application context时，会`invokeBeanFactoryPostProcessors`去调用bean工厂后置处理器。
通过`PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors(beanFactory, getBeanFactoryPostProcessors())`
执行。

紧接着
ConfigurationClassPostProcessor执行实现`BeanDefinitionRegistryPostProcessor`的接口方法` postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry)`，
做了一些校验，就执行核心方法`processConfigBeanDefinitions(registry)`。我会对该方法进行一些注释。

~~~ConfigurationClassPostProcessor.processConfigBeanDefinitions(registry)
	/**
	 * Build and validate a configuration model based on the registry of
	 * {@link Configuration} classes.
	 */
	public void processConfigBeanDefinitions(BeanDefinitionRegistry registry) {
		List<BeanDefinitionHolder> configCandidates = new ArrayList<>();
		String[] candidateNames = registry.getBeanDefinitionNames();
        //1.获取full和lite的@Configuration配置类，并塞到列表中，这里只获取到一开始我设置的primarySource（ApplicationMain）
		for (String beanName : candidateNames) {
			BeanDefinition beanDef = registry.getBeanDefinition(beanName);
			if (ConfigurationClassUtils.isFullConfigurationClass(beanDef) ||
					ConfigurationClassUtils.isLiteConfigurationClass(beanDef)) {
				if (logger.isDebugEnabled()) {
					logger.debug("Bean definition has already been processed as a configuration class: " + beanDef);
				}
			}
			else if (ConfigurationClassUtils.checkConfigurationClassCandidate(beanDef, this.metadataReaderFactory)) {
				configCandidates.add(new BeanDefinitionHolder(beanDef, beanName));
			}
		}
        
		// Return immediately if no @Configuration classes were found
		if (configCandidates.isEmpty()) {
			return;
		}

		// Sort by previously determined @Order value, if applicable
        //2.对列表中配置类bean definition进行排序，默认是最低优先级
		configCandidates.sort((bd1, bd2) -> {
			int i1 = ConfigurationClassUtils.getOrder(bd1.getBeanDefinition());
			int i2 = ConfigurationClassUtils.getOrder(bd2.getBeanDefinition());
			return Integer.compare(i1, i2);
		});

		// Detect any custom bean name generation strategy supplied through the enclosing application context
        // 3.查找出自定义的bean名称生成器
		SingletonBeanRegistry sbr = null;
		if (registry instanceof SingletonBeanRegistry) {
			sbr = (SingletonBeanRegistry) registry;
			if (!this.localBeanNameGeneratorSet) {
				BeanNameGenerator generator = (BeanNameGenerator) sbr.getSingleton(CONFIGURATION_BEAN_NAME_GENERATOR);
				if (generator != null) {
					this.componentScanBeanNameGenerator = generator;
					this.importBeanNameGenerator = generator;
				}
			}
		}

		if (this.environment == null) {
			this.environment = new StandardEnvironment();
		}

		// Parse each @Configuration class
        //4.解析每个@Configuration类
		ConfigurationClassParser parser = new ConfigurationClassParser(
				this.metadataReaderFactory, this.problemReporter, this.environment,
				this.resourceLoader, this.componentScanBeanNameGenerator, registry);

		Set<BeanDefinitionHolder> candidates = new LinkedHashSet<>(configCandidates);
		Set<ConfigurationClass> alreadyParsed = new HashSet<>(configCandidates.size());
		do {
            //5.进行解析
			parser.parse(candidates);
			parser.validate();

			Set<ConfigurationClass> configClasses = new LinkedHashSet<>(parser.getConfigurationClasses());
			configClasses.removeAll(alreadyParsed);

			// Read the model and create bean definitions based on its content
			if (this.reader == null) {
				this.reader = new ConfigurationClassBeanDefinitionReader(
						registry, this.sourceExtractor, this.resourceLoader, this.environment,
						this.importBeanNameGenerator, parser.getImportRegistry());
			}
            // 6.读取@Configuration类中的@Bean方法，并进行注册
			this.reader.loadBeanDefinitions(configClasses);
			alreadyParsed.addAll(configClasses);

			candidates.clear();
            // 7.下面逻辑主要做一些去重并检查registry中的新加的configuration类并塞入候选列表静待解析
			if (registry.getBeanDefinitionCount() > candidateNames.length) {
				String[] newCandidateNames = registry.getBeanDefinitionNames();
				Set<String> oldCandidateNames = new HashSet<>(Arrays.asList(candidateNames));
				Set<String> alreadyParsedClasses = new HashSet<>();
				for (ConfigurationClass configurationClass : alreadyParsed) {
					alreadyParsedClasses.add(configurationClass.getMetadata().getClassName());
				}
				for (String candidateName : newCandidateNames) {
					if (!oldCandidateNames.contains(candidateName)) {
						BeanDefinition bd = registry.getBeanDefinition(candidateName);
						if (ConfigurationClassUtils.checkConfigurationClassCandidate(bd, this.metadataReaderFactory) &&
								!alreadyParsedClasses.contains(bd.getBeanClassName())) {
							candidates.add(new BeanDefinitionHolder(bd, candidateName));
						}
					}
				}
				candidateNames = newCandidateNames;
			}
		}
		while (!candidates.isEmpty());

		// Register the ImportRegistry as a bean in order to support ImportAware @Configuration classes
		if (sbr != null && !sbr.containsSingleton(IMPORT_REGISTRY_BEAN_NAME)) {
			sbr.registerSingleton(IMPORT_REGISTRY_BEAN_NAME, parser.getImportRegistry());
		}

		if (this.metadataReaderFactory instanceof CachingMetadataReaderFactory) {
			// Clear cache in externally provided MetadataReaderFactory; this is a no-op
			// for a shared cache since it'll be cleared by the ApplicationContext.
			((CachingMetadataReaderFactory) this.metadataReaderFactory).clearCache();
		}
	}



~~~

看上面注释，分析第5步。
会做一些校验和SourceClass适配包装。  

~~~

	public void parse(Set<BeanDefinitionHolder> configCandidates) {
		for (BeanDefinitionHolder holder : configCandidates) {
			BeanDefinition bd = holder.getBeanDefinition();
			try {
                //5.1对不同的待处理Configuration类的Bean定义执行不同处理
				if (bd instanceof AnnotatedBeanDefinition) {
					parse(((AnnotatedBeanDefinition) bd).getMetadata(), holder.getBeanName());
				}
				else if (bd instanceof AbstractBeanDefinition && ((AbstractBeanDefinition) bd).hasBeanClass()) {
					parse(((AbstractBeanDefinition) bd).getBeanClass(), holder.getBeanName());
				}
				else {
					parse(bd.getBeanClassName(), holder.getBeanName());
				}
			}
			catch (BeanDefinitionStoreException ex) {
				throw ex;
			}
			catch (Throwable ex) {
				throw new BeanDefinitionStoreException(
						"Failed to parse configuration class [" + bd.getBeanClassName() + "]", ex);
			}
		}
        //5.2最终执行DeferredImportSelector（延迟导入筛选器）
		this.deferredImportSelectorHandler.process();
	}


	protected void processConfigurationClass(ConfigurationClass configClass) throws IOException {
        //使用conditionEvaluator进行验证（conditionEvaluator是对各种条件配置注解（@Conditional）的处理校验器）
		if (this.conditionEvaluator.shouldSkip(configClass.getMetadata(), ConfigurationPhase.PARSE_CONFIGURATION)) {
			return;
		}

		ConfigurationClass existingClass = this.configurationClasses.get(configClass);
		if (existingClass != null) {
			if (configClass.isImported()) {
				if (existingClass.isImported()) {
					existingClass.mergeImportedBy(configClass);
				}
				// Otherwise ignore new imported config class; existing non-imported class overrides it.
				return;
			}
			else {
				// Explicit bean definition found, probably replacing an import.
				// Let's remove the old one and go with the new one.
				this.configurationClasses.remove(configClass);
				this.knownSuperclasses.values().removeIf(configClass::equals);
			}
		}

		// Recursively process the configuration class and its superclass hierarchy.
		SourceClass sourceClass = asSourceClass(configClass);
		do {
			sourceClass = doProcessConfigurationClass(configClass, sourceClass);
		}
		while (sourceClass != null);
        
		this.configurationClasses.put(configClass, configClass);
	}

~~~

最终来看核心方法doProcessConfigurationClass：

~~~doProcessConfigurationClass
	@Nullable
	protected final SourceClass doProcessConfigurationClass(ConfigurationClass configClass, SourceClass sourceClass)
			throws IOException {
        
		if (configClass.getMetadata().isAnnotated(Component.class.getName())) {
			// Recursively process any member (nested) classes first
            //5.1.1 递归处理@Configuration类，查看@Configuration类是否存在@Configuration内部类，比如，starter-mvc和webflux某些配置类就是如此
			processMemberClasses(configClass, sourceClass);
		}

		// Process any @PropertySource annotations
        //5.1.2处理@PropertySource注解
		for (AnnotationAttributes propertySource : AnnotationConfigUtils.attributesForRepeatable(
				sourceClass.getMetadata(), PropertySources.class,
				org.springframework.context.annotation.PropertySource.class)) {
			if (this.environment instanceof ConfigurableEnvironment) {
				processPropertySource(propertySource);
			}
			else {
				logger.info("Ignoring @PropertySource annotation on [" + sourceClass.getMetadata().getClassName() +
						"]. Reason: Environment must implement ConfigurableEnvironment");
			}
		}

		// Process any @ComponentScan annotations
        //5.1.3 解析@ComponentScan注解（如果存在的话）
		Set<AnnotationAttributes> componentScans = AnnotationConfigUtils.attributesForRepeatable(
				sourceClass.getMetadata(), ComponentScans.class, ComponentScan.class);
		if (!componentScans.isEmpty() &&
				!this.conditionEvaluator.shouldSkip(sourceClass.getMetadata(), ConfigurationPhase.REGISTER_BEAN)) {
			for (AnnotationAttributes componentScan : componentScans) {
				// The config class is annotated with @ComponentScan -> perform the scan immediately
                // 5.1.3.1执行ComponentScanAnnotationParser的parse方法，通过ClassPathBeanDefinitionScanner检索出所有符合条件的component
				Set<BeanDefinitionHolder> scannedBeanDefinitions =
						this.componentScanParser.parse(componentScan, sourceClass.getMetadata().getClassName());
				// Check the set of scanned definitions for any further config classes and parse recursively if needed
				for (BeanDefinitionHolder holder : scannedBeanDefinitions) {
					BeanDefinition bdCand = holder.getBeanDefinition().getOriginatingBeanDefinition();
					if (bdCand == null) {
						bdCand = holder.getBeanDefinition();
					}
                    //处理@Configuration类
					if (ConfigurationClassUtils.checkConfigurationClassCandidate(bdCand, this.metadataReaderFactory)) {
						parse(bdCand.getBeanClassName(), holder.getBeanName());
					}
				}
			}
		}

		// Process any @Import annotations
        //5.1.4处理@Import注解
		processImports(configClass, sourceClass, getImports(sourceClass), true);

		// 5.1.5Process any @ImportResource annotations
		AnnotationAttributes importResource =
				AnnotationConfigUtils.attributesFor(sourceClass.getMetadata(), ImportResource.class);
		if (importResource != null) {
			String[] resources = importResource.getStringArray("locations");
			Class<? extends BeanDefinitionReader> readerClass = importResource.getClass("reader");
			for (String resource : resources) {
				String resolvedResource = this.environment.resolveRequiredPlaceholders(resource);
				configClass.addImportedResource(resolvedResource, readerClass);
			}
		}

		// 5.1.6Process individual @Bean methods
		Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata(sourceClass);
		for (MethodMetadata methodMetadata : beanMethods) {
			configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
		}

		// 5.1.7Process default methods on interfaces
		processInterfaces(configClass, sourceClass);

		// 5.1.8Process superclass, if any
		if (sourceClass.getMetadata().hasSuperClass()) {
			String superclass = sourceClass.getMetadata().getSuperClassName();
			if (superclass != null && !superclass.startsWith("java") &&
					!this.knownSuperclasses.containsKey(superclass)) {
				this.knownSuperclasses.put(superclass, configClass);
				// Superclass found, return its annotation metadata and recurse
				return sourceClass.getSuperClass();
			}
		}

		// No superclass -> processing is complete
		return null;
	}

~~~

#### @ComponentScan的扫描逻辑

看上面5.1.3，执行ComponentScanAnnotationParser的parse方法，
做一些初始化（比如，defaultFilter配置，根据@ComponentScan注解值的一些初始化处理）
最终执行核心方法doScan：
~~~ComponentScanAnnotationParser.doScan

	protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
		Assert.notEmpty(basePackages, "At least one base package must be specified");
		Set<BeanDefinitionHolder> beanDefinitions = new LinkedHashSet<>();
		for (String basePackage : basePackages) {
            //根据类路径和includeFilter、excludeFilter找到符合条件的组件
			Set<BeanDefinition> candidates = findCandidateComponents(basePackage);
			for (BeanDefinition candidate : candidates) {
                //获取scope
				ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(candidate);
				candidate.setScope(scopeMetadata.getScopeName());
                //生成bean name
				String beanName = this.beanNameGenerator.generateBeanName(candidate, this.registry);
				if (candidate instanceof AbstractBeanDefinition) {
                    //主要设置该bean是否可以autowired到其他bean
					postProcessBeanDefinition((AbstractBeanDefinition) candidate, beanName);
				}
				if (candidate instanceof AnnotatedBeanDefinition) {
                    //查找并设置一些通用的注解的值
					AnnotationConfigUtils.processCommonDefinitionAnnotations((AnnotatedBeanDefinition) candidate);
				}
				if (checkCandidate(beanName, candidate)) {
					BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(candidate, beanName);
					definitionHolder =
							AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);
					beanDefinitions.add(definitionHolder);
                    //注册beandefinition
					registerBeanDefinition(definitionHolder, this.registry);
				}
			}
		}
		return beanDefinitions;
	}


~~~


#### 条件注解的实现原理

上面在processConfigurationClass的源码解读中，我提到“使用conditionEvaluator进行验证（conditionEvaluator是对各种条件配置注解（@Conditional）的处理校验器）”。

我们看看在引入mvc组件的自动装配类。
~~~
@Configuration
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass({ Servlet.class, DispatcherServlet.class, WebMvcConfigurer.class })
@ConditionalOnMissingBean(WebMvcConfigurationSupport.class)
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE + 10)
@AutoConfigureAfter({ DispatcherServletAutoConfiguration.class,
		TaskExecutionAutoConfiguration.class, ValidationAutoConfiguration.class })
public class WebMvcAutoConfiguration {
~~~

诸如@ConditionalOnWebApplication、@ConditionalOnClass、@ConditionalOnMissingBean是Spring提供的一类注解--条件注解。
条件注解的作用是能够在应用运行时动态选择符合条件的bean。
比如上面的`@ConditionalOnClass(xxx.class)`，作用是在加载过程中判断存在xxx类才加载该配置类。

##### 一些概念

* ConditionEvaluator处理条件注解的评估器。

* `Condition`单个条件的抽象。

* `@Conditional`注解，指定仅当所有指定条件都匹配时，组件才有资格注册。
~~~

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Conditional {

    //必须匹配所有Condition的实现类，才能被注册
	Class<? extends Condition>[] value();

}
~~~

以@ConditionalOnClass实现（代码如下）为例，
OnClassCondition类实现了Condition接口是实际条件判断者，
ConditionEvaluator类获取OnClassCondition并实例化进行判断。
~~~
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnClassCondition.class)
public @interface ConditionalOnClass {
	Class<?>[] value() default {};
	String[] name() default {};

}
~~~



##### ConditionEvaluator的执行分析

条件评估存在两个阶段：
Configuration类解析阶段（ConfigurationPhase.PARSE_CONFIGURATION）、
bean注册阶段（ConfigurationPhase.REGISTER_BEAN）。

我们来分析条件评估器(ConditionEvaluator)的核心方法shouldSkip：
~~~

class ConditionEvaluator {

	private final ConditionContextImpl context;

    //省略
    
	public boolean shouldSkip(@Nullable AnnotatedTypeMetadata metadata, @Nullable ConfigurationPhase phase) {
        //没有条件注解跳过
        if (metadata == null || !metadata.isAnnotated(Conditional.class.getName())) {
			return false;
		}
        //参数phase为空的话判断阶段
		if (phase == null) {
			if (metadata instanceof AnnotationMetadata &&
					ConfigurationClassUtils.isConfigurationCandidate((AnnotationMetadata) metadata)) {
				return shouldSkip(metadata, ConfigurationPhase.PARSE_CONFIGURATION);
			}
			return shouldSkip(metadata, ConfigurationPhase.REGISTER_BEAN);
		}

        //获取该元数据的条件注解的Conditions，并实例化。
		List<Condition> conditions = new ArrayList<>();
		for (String[] conditionClasses : getConditionClasses(metadata)) {
			for (String conditionClass : conditionClasses) {
				Condition condition = getCondition(conditionClass, this.context.getClassLoader());
				conditions.add(condition);
			}
		}
        //对条件类进行排序。
		AnnotationAwareOrderComparator.sort(conditions);

		for (Condition condition : conditions) {
			ConfigurationPhase requiredPhase = null;
            //获取条件的评估阶段，用于跳过不在该阶段的条件
			if (condition instanceof ConfigurationCondition) {
				requiredPhase = ((ConfigurationCondition) condition).getConfigurationPhase();
			}
            //判断条件是否匹配，例如，执行OnClassCondition的matches方法
			if ((requiredPhase == null || requiredPhase == phase) && !condition.matches(this.context, metadata)) {
				return true;
			}
		}

		return false;
	}

    //省略

}

~~~


#### @Import的处理逻辑和spring对@EnableAutoConfiguration的具体处理

我们来看看@EnableAutoConfiguration的注解代码：
~~~
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@AutoConfigurationPackage
@Import(AutoConfigurationImportSelector.class)
public @interface EnableAutoConfiguration {
    
    //可以通过该配置项禁用自动装配
	String ENABLED_OVERRIDE_PROPERTY = "spring.boot.enableautoconfiguration";

	/**
	 * Exclude specific auto-configuration classes such that they will never be applied.
	 * @return the classes to exclude
	 */
    //排除指定的auto-configuration类
	Class<?>[] exclude() default {};

	/**
	 * Exclude specific auto-configuration class names such that they will never be
	 * applied.
	 * @return the class names to exclude
	 * @since 1.3.0
	 */
    //排除指定的auto-configuration类名
	String[] excludeName() default {};

}

~~~

注意`@Import(AutoConfigurationImportSelector.class)`和@AutoConfigurationPackage引入的
`@Import(AutoConfigurationPackages.Registrar.class)`

接下来，我会分析@Import的处理逻辑，并具体关注auto-configuration引入的AutoConfigurationPackages.Registrar和AutoConfigurationImportSelector的处理逻辑。

ConfigurationClassParser处理@Import代码如下：
~~~

	private void processImports(ConfigurationClass configClass, SourceClass currentSourceClass,
			Collection<SourceClass> importCandidates, boolean checkForCircularImports) {

		if (importCandidates.isEmpty()) {
			return;
		}
        // 5.1.4.1检查是否循环引用(import)了
		if (checkForCircularImports && isChainedImportOnStack(configClass)) {
			this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
		}
		else {
			this.importStack.push(configClass);
			try {
				for (SourceClass candidate : importCandidates) {
                    // 5.1.4.2如果import的类是实现了ImportSelector接口
					if (candidate.isAssignable(ImportSelector.class)) {
						// Candidate class is an ImportSelector -> delegate to it to determine imports
						Class<?> candidateClass = candidate.loadClass();
						ImportSelector selector = BeanUtils.instantiateClass(candidateClass, ImportSelector.class);
						//5.1.4.3如果ImportSelector实现了BeanClassLoaderAware、BeanFactoryAware、EnvironmentAware、ResourceLoaderAware接口，
                        //则将相应对象set进ImportSelector，方便后续处理
						ParserStrategyUtils.invokeAwareMethods(
								selector, this.environment, this.resourceLoader, this.registry);
                        //5.1.4.4如果是延迟导入选择器，则deferredImportSelectorHandler进行处理
						if (selector instanceof DeferredImportSelector) {
							this.deferredImportSelectorHandler.handle(
									configClass, (DeferredImportSelector) selector);
						}
						else {
                            //5.1.4.5执行selectImports，对返回的classname处理成SourceClasss并递归执行processImports
							String[] importClassNames = selector.selectImports(currentSourceClass.getMetadata());
							Collection<SourceClass> importSourceClasses = asSourceClasses(importClassNames);
							processImports(configClass, currentSourceClass, importSourceClasses, false);
						}
					}
                    //5.1.4.6判断是ImportBeanDefinitionRegistrar的实现类
					else if (candidate.isAssignable(ImportBeanDefinitionRegistrar.class)) {
						// Candidate class is an ImportBeanDefinitionRegistrar ->
						// delegate to it to register additional bean definitions
						Class<?> candidateClass = candidate.loadClass();
						ImportBeanDefinitionRegistrar registrar =
								BeanUtils.instantiateClass(candidateClass, ImportBeanDefinitionRegistrar.class);
						//5.1.4.7如果ImportBeanDefinitionRegistrar实现了BeanClassLoaderAware、BeanFactoryAware、EnvironmentAware、ResourceLoaderAware接口，
                        //则将相应对象set进ImportSelector，方便后续处理
						ParserStrategyUtils.invokeAwareMethods(
								registrar, this.environment, this.resourceLoader, this.registry);
                        //5.1.4.8把Registrar放到当前处理的configClass的Registrar集合中，留待解析完Configuration类后的加载beanDefinition环节中执行
						configClass.addImportBeanDefinitionRegistrar(registrar, currentSourceClass.getMetadata());
					}
					else {
						// Candidate class not an ImportSelector or ImportBeanDefinitionRegistrar ->
						// process it as an @Configuration class
                        //5.1.4.9非ImportSelector非ImportBeanDefinitionRegistrar，则是Configuration,按@Configuration逻辑执行处理
						this.importStack.registerImport(
								currentSourceClass.getMetadata(), candidate.getMetadata().getClassName());
						processConfigurationClass(candidate.asConfigClass(configClass));
					}
				}
			}
			catch (BeanDefinitionStoreException ex) {
				throw ex;
			}
			catch (Throwable ex) {
				throw new BeanDefinitionStoreException(
						"Failed to process import candidates for configuration class [" +
						configClass.getMetadata().getClassName() + "]", ex);
			}
			finally {
				this.importStack.pop();
			}
		}
	}

~~~

上面代码注释中，
* 5.1.4.4一般情况下，会将和AutoConfigurationImportSelector放到deferredImportSelectorHandler中。
在执行完解析后，在5.2`this.deferredImportSelectorHandler.process();`进行处理。不过在已经处于延后（defer）状态时，
`DeferredImportSelectorHandler`会马上执行当前deferedSelector。   
* 5.1.4.8会将AutoConfigurationPackages.Registrar加到队列中，留待解析完Configuration类后的加载beanDefinition环节中执行。

分别说说两者的作用，AutoConfigurationPackages.Registrar就是将根包名保存到BasePackages类中，然后通过BeanDefinitionRegistry将其注册。
~~~

	static class Registrar implements ImportBeanDefinitionRegistrar, DeterminableImports {

		@Override
		public void registerBeanDefinitions(AnnotationMetadata metadata,
				BeanDefinitionRegistry registry) {
			register(registry, new PackageImport(metadata).getPackageName());
		}

		@Override
		public Set<Object> determineImports(AnnotationMetadata metadata) {
			return Collections.singleton(new PackageImport(metadata));
		}

	}

~~~


说到AutoConfigurationImportSelector，
我会跳过DeferredImportSelector.Group的分析，group的作用就是去除重复的Import和configuration类，提高执行性能。
有兴趣可阅读`DeferredImportSelectorGrouping`、`DeferredImportSelector.Group`
和`DeferredImportSelectorGroupingHandler`的`register`和`processGroupImports`方法的源码。

最终执行到的核心方法：
AutoConfigurationImportSelector.getAutoConfigurationEntry。
~~~ AutoConfigurationImportSelector.getAutoConfigurationEntry

	protected AutoConfigurationEntry getAutoConfigurationEntry(
			AutoConfigurationMetadata autoConfigurationMetadata,
			AnnotationMetadata annotationMetadata) {
        //判断配置里的自动配置是否开启
		if (!isEnabled(annotationMetadata)) {
			return EMPTY_ENTRY;
		}
		AnnotationAttributes attributes = getAttributes(annotationMetadata);
        //通过SPI获取Configuration类
		List<String> configurations = getCandidateConfigurations(annotationMetadata,
				attributes);
        //去重
		configurations = removeDuplicates(configurations);
        //排除声明的exclude和excludeName和配置里spring.autoconfigure.exclude声明的exclude配置类
		Set<String> exclusions = getExclusions(annotationMetadata, attributes);
		checkExcludedClasses(configurations, exclusions);
		configurations.removeAll(exclusions);
        //获取SPI中的AutoConfigurationImportFilter进行过滤
		configurations = filter(configurations, autoConfigurationMetadata);
        //获取SPI中的AutoConfigurationImportListener并直接发布事件（不通过context和SpringApplication的广播器发布）
		fireAutoConfigurationImportEvents(configurations, exclusions);
		return new AutoConfigurationEntry(configurations, exclusions);
	}

~~~

留意`AutoConfigurationImportFilter`、`AutoConfigurationImportListener`这两个拓展点。

最终通过processImports并执行processConfigurationClass去解析导入进来的Configuration。


至此，@EnableAutoConfiguration的执行逻辑在分析@Configuration的解析过程中也基本说完了。

#### @Bean的处理

上面说到，
~~~
6.读取@Configuration类中的@Bean方法，并进行注册
this.reader.loadBeanDefinitions(configClasses);
~~~
我再分析一下执行的核心方法：
`ConfigurationClassBeanDefinitionReader.loadBeanDefinitionsForConfigurationClass`

~~~
	/**
	 * Read a particular {@link ConfigurationClass}, registering bean definitions
	 * for the class itself and all of its {@link Bean} methods.
	 */
	private void loadBeanDefinitionsForConfigurationClass(
			ConfigurationClass configClass, TrackedConditionEvaluator trackedConditionEvaluator) {
        //检查ConditionalXXX注解，符合跳出条件去除beanDefinition
		if (trackedConditionEvaluator.shouldSkip(configClass)) {
			String beanName = configClass.getBeanName();
			if (StringUtils.hasLength(beanName) && this.registry.containsBeanDefinition(beanName)) {
				this.registry.removeBeanDefinition(beanName);
			}
			this.importRegistry.removeImportingClass(configClass.getMetadata().getClassName());
			return;
		}
        //将已导入的@Configuration注册成bean
		if (configClass.isImported()) {
			registerBeanDefinitionForImportedConfigurationClass(configClass);
		}
        //获取前面解析的@Bean方法，进行解析
		for (BeanMethod beanMethod : configClass.getBeanMethods()) {
			loadBeanDefinitionsForBeanMethod(beanMethod);
		}
        //解析ImportResource导入的xml或groovy配置文件
		loadBeanDefinitionsFromImportedResources(configClass.getImportedResources());
        //执行前面解析import加入的ImportBeanDefinitionRegistrar
		loadBeanDefinitionsFromRegistrars(configClass.getImportBeanDefinitionRegistrars());
	}

~~~

<!--
=> ConfigurationClassPostProcessor.processConfigBeanDefinitions（同时看看ConfigurationClassParser）
=> ConfigurationClassBeanDefinitionReader.loadBeanDefinitionsForConfigurationClass、loadBeanDefinitionsFromRegistrars
=> ConfigurationPropertiesAutoConfiguration引入的EnableConfigurationPropertiesImportSelector
   引入的ConfigurationPropertiesBeanRegistrar（负责注册EnableConfigurationProperties注解value上的类）、ConfigurationPropertiesBindingPostProcessorRegistrar（负责注册ConfigurationPropertiesBindingPostProcessor去处理绑定propertySource(外部配置)到bean上）
-->


## 总结

本文初衷为分析自动装配，从Spring Boot的启动demo开始分析，
* 说到@Configuration的处理类`ConfigurationClassPostProcessor`的初始化时机--在SpringApplication初始化ApplicationContext时。
* 在refresh上下文时，进行@Configuration解析。
* doProcessConfigurationClass进行处理@Configuration类的各种注解处理。
* 处理@Import时导入进来AutoConfigurationImportSelector（延后导入选择器）
* 通过Spring SPI导入spring.factories的EnableAutoConfiguration的value类。
* 最后通过ConfigurationClassBeanDefinitionReader去注册相关beanDefinition。

期间也分析到ComponentScan等注解的处理。
