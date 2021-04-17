#


Spring Boot makes it easy to create stand-alone, production-grade Spring-based Applications that you can run. We take an opinionated view of the Spring platform and third-party libraries, so that you can get started with minimum fuss. Most Spring Boot applications need very little Spring configuration.

You can use Spring Boot to create Java applications that can be started by using java -jar or more traditional war deployments. We also provide a command line tool that runs “spring scripts”.

Our primary goals are:

* Provide a radically faster and widely accessible getting-started experience for all Spring development.
* Be opinionated out of the box but get out of the way quickly as requirements start to diverge from the defaults.
* Provide a range of non-functional features that are common to large classes of projects (such as embedded servers, security, metrics, health checks, and externalized configuration).
* Absolutely no code generation and no requirement for XML configuration.


## things to write

全景图

启动、关闭 √
   bean生命周期 √
   过程中各组件作用（包括事件和EventListener、各种Processor、ContextInitializer还有spring.factories中各组件的作用等等）√
   
配置的整体存储结构，外部化配置，配置的加载及优先级，配置更新，√
configurationpropertysources、Environment

自动装配 √
@Configuration注解分析√
条件注解 √

Spring SPI对比Dubbo SPI、JDK SPI √

Spring boot loader（Jar launcher）√

Spring Cloud common、context所额外做的
    load balance
    discoveryClient
    endpoint
    CircuitBreaker

Spring boot Actuator √

System.getSecurityManager

externallyManagedInitMethods、externallyManagedDestroyMethods


Take care not to cause early instantiation of all FactoryBeans？如何触发early instantiation 


