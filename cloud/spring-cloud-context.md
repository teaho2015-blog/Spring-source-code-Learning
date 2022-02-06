# Spring Cloud Context

Spring Cloud Context是Spring Cloud的上下文，主要做了两件事：
1. 最主要的，采用Spring 父子应用上下文的设计，在初始化Spring Boot应用上下文前增加了bootstrap应用上下文。带来了如下特性：
   * bootstrap.yml配置加载
   * RefreshScope，允许运行时刷新bean（还有额外增加特性ThreadScope）
   * 配置刷新，配置加密
   * 应用重启
2. NamedContextFactory，能够根据名字创建多个子上下文