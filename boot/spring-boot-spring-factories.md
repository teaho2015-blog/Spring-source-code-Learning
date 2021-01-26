# Spring Boot spring.factories文件组件解析


## 前言

相信一些人会写starter，比如，我曾为一个团队写的国际化通用和通用聚合服务等即插即用的功能包，就是用的starter。  
那么就会自己声明spring.factories文件。

这是一种工厂加载机制（factory loading mechanism），也可说成是SPI机制。原理分析在[Spring SPI与Java SPI、Dubbo SPI](spring-spi.md)

Spring Boot jar包下的spring.factories文件也声明了一些组件，  
为方便我的阐述，我把它列在了附录中。我会按照 介绍作用、初始化时机 去做分析。


## org.springframework.boot.env.PropertySourceLoader

### 介绍和作用

策略接口，用于加载PropertySource（配置）。实现有PropertiesPropertySourceLoader、YamlPropertySourceLoader。

### 初始化时机

当ConfigFileApplicationListener收到ApplicationEnvironmentPreparedEvent事件时，
创建SourceLoader并执行load，加载配置。


## org.springframework.boot.SpringApplicationRunListener

### 介绍和作用

用于监听spring boot启动并作出相应处理。
> Listener for the SpringApplication run method。

### 初始化时机

在SpringApplication启动时进行初始化。

##  org.springframework.boot.SpringBootExceptionReporter

### 介绍和作用

回调接口，用于对Spring Boot应用启动失败（发生异常）后，进行异常播报的组件。

### 初始化时机

在SpringApplication启动时（创建完Application context后）进行初始化。


##  org.springframework.context.ApplicationContextInitializer

### 介绍和作用

用于在刷新之前初始化Spring ConfigurableApplicationContext的回调接口。比如，servlet web容器会用该组件设置一些额外的属性。

### 初始化时机

Spring Application构造时创建。

## org.springframework.context.ApplicationListener

### 介绍和作用

用于监听事件并作处理。可看：[Spring Application事件机制](event-mechanism.md)

### 初始化时机

Spring Application构造时创建。

##  org.springframework.boot.env.EnvironmentPostProcessor

### 介绍和作用

在context刷新前可对environment进行修改的组件。

### 初始化时机

在run listener发出ApplicationEnvironmentPreparedEvent事件后触发。

##  org.springframework.boot.diagnostics.FailureAnalyzer

### 介绍和作用

分析错误，展示给用户诊断结果。

### 初始化时机

在SpringApplication启动时（创建完Application context后）进行初始化。
伴随一个SpringBootExceptionReporter(即org.springframework.boot.diagnostics.FailureAnalyzers)
的实例化而实例化。


## org.springframework.boot.diagnostics.FailureAnalysisReporter

### 介绍和作用

在错误分析完成后，向用户展示结果。（Spring Boot默认实现是通过日志展示出来）

### 初始化时机

在SpringApplication启动时（创建完Application context后）发生错误的情况下进行初始化。


## 附录

### spring boot包的spring.factories文件

~~~

# PropertySource Loaders
org.springframework.boot.env.PropertySourceLoader=\
org.springframework.boot.env.PropertiesPropertySourceLoader,\
org.springframework.boot.env.YamlPropertySourceLoader

# Run Listeners
org.springframework.boot.SpringApplicationRunListener=\
org.springframework.boot.context.event.EventPublishingRunListener

# Error Reporters
org.springframework.boot.SpringBootExceptionReporter=\
org.springframework.boot.diagnostics.FailureAnalyzers

# Application Context Initializers
org.springframework.context.ApplicationContextInitializer=\
org.springframework.boot.context.ConfigurationWarningsApplicationContextInitializer,\
org.springframework.boot.context.ContextIdApplicationContextInitializer,\
org.springframework.boot.context.config.DelegatingApplicationContextInitializer,\
org.springframework.boot.web.context.ServerPortInfoApplicationContextInitializer

# Application Listeners
org.springframework.context.ApplicationListener=\
org.springframework.boot.ClearCachesApplicationListener,\
org.springframework.boot.builder.ParentContextCloserApplicationListener,\
org.springframework.boot.context.FileEncodingApplicationListener,\
org.springframework.boot.context.config.AnsiOutputApplicationListener,\
org.springframework.boot.context.config.ConfigFileApplicationListener,\
org.springframework.boot.context.config.DelegatingApplicationListener,\
org.springframework.boot.context.logging.ClasspathLoggingApplicationListener,\
org.springframework.boot.context.logging.LoggingApplicationListener,\
org.springframework.boot.liquibase.LiquibaseServiceLocatorApplicationListener

# Environment Post Processors
org.springframework.boot.env.EnvironmentPostProcessor=\
org.springframework.boot.cloud.CloudFoundryVcapEnvironmentPostProcessor,\
org.springframework.boot.env.SpringApplicationJsonEnvironmentPostProcessor,\
org.springframework.boot.env.SystemEnvironmentPropertySourceEnvironmentPostProcessor

# Failure Analyzers
org.springframework.boot.diagnostics.FailureAnalyzer=\
org.springframework.boot.diagnostics.analyzer.BeanCurrentlyInCreationFailureAnalyzer,\
org.springframework.boot.diagnostics.analyzer.BeanDefinitionOverrideFailureAnalyzer,\
org.springframework.boot.diagnostics.analyzer.BeanNotOfRequiredTypeFailureAnalyzer,\
org.springframework.boot.diagnostics.analyzer.BindFailureAnalyzer,\
org.springframework.boot.diagnostics.analyzer.BindValidationFailureAnalyzer,\
org.springframework.boot.diagnostics.analyzer.UnboundConfigurationPropertyFailureAnalyzer,\
org.springframework.boot.diagnostics.analyzer.ConnectorStartFailureAnalyzer,\
org.springframework.boot.diagnostics.analyzer.NoSuchMethodFailureAnalyzer,\
org.springframework.boot.diagnostics.analyzer.NoUniqueBeanDefinitionFailureAnalyzer,\
org.springframework.boot.diagnostics.analyzer.PortInUseFailureAnalyzer,\
org.springframework.boot.diagnostics.analyzer.ValidationExceptionFailureAnalyzer,\
org.springframework.boot.diagnostics.analyzer.InvalidConfigurationPropertyNameFailureAnalyzer,\
org.springframework.boot.diagnostics.analyzer.InvalidConfigurationPropertyValueFailureAnalyzer

# FailureAnalysisReporters
org.springframework.boot.diagnostics.FailureAnalysisReporter=\
org.springframework.boot.diagnostics.LoggingFailureAnalysisReporter

~~~