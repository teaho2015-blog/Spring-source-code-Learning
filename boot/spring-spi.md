# Spring SPI与Java SPI、Dubbo SPI

## 前言

此文简述SPI，同时分析Spring SPI的源码及作用，并分析Java、Dubbo的SPI，做一个对比。

### 什么是SPI

> Service Provider Interface (SPI) is an API intended to be implemented or extended by a third party. It can be used to enable framework extension and replaceable components.

服务提供者接口 (SPI，Service Provider Interface)，是一个设计来可供第三方实现或拓展的API。  
它可用于打造框架拓展和可插拔插件。

一个服务（Service）指的是一组约定俗成的接口或抽象类。一个服务提供者（service Provider）指的是一个服务特定的实现。
服务提供者能组织成插件形式（在Java是jar包）并放置到拓展目录或classpath，便能达到动态地为服务替换实现。

该机制从原理上看类似设计模式中的抽象工厂模式（这也是为什么Spring将其SPI机制称为工厂加载机制）。

原理说明了，下面介绍代码，最后分析下三种实现的差别。

## Java SPI

核心类是java.util.ServiceLoader(作者是Mark Reinhold，Java Platform Group的首席架构师)。

> A simple service-provider loading facility.

一个简单的服务提供者加载工具。


### 例子

[github|spring-source-code-learning-demo/java-spi-demo/](https://github.com/teaho2015-blog/spring-source-code-learning-demo/tree/master/java-spi-demo)

我写了一个Java SPI例子。模拟了通过声明实现了接口MusicalInstrument的吉他类和钢琴类，来达到可插拔的乐器播放模拟。

步骤：  
创建服务接口
~~~
public interface MusicalInstrument {
    void play();
}
~~~
创建SPI文件
![java_spi_meta_inf.png](java_spi_meta_inf.png)

~~~
//在文件中添加服务提供者实现
net.teaho.demo.java.spi.Piano
net.teaho.demo.java.spi.Guitar
~~~

服务提供者实现。
~~~
public class Piano implements MusicalInstrument {
    
    @Override
    public void play() {
        System.out.println("Piano is playing.");
    }
}

public class Guitar implements MusicalInstrument {

    @Override
    public void play() {
        System.out.println("Guitar is playing.");
    }
}
~~~

最终执行
~~~
public class RunMain {

    public static void main(String[] args) {
        ServiceLoader<MusicalInstrument> serviceLoader = ServiceLoader.load(MusicalInstrument.class);
        System.out.println("Java SPI");
        serviceLoader.forEach(MusicalInstrument::play);
    }
}
//控制台输出
Java SPI
Piano is playing.
Guitar is playing.
~~~

### ServiceLoader代码分析

源码比较简单，我不一行行贴出来分析，说两点，    
* 通过parse方法读取配置文件。
* 在load的时候不加载配置，而是写了个LazyIterator类，在（重加载后）第一次循环执行时进行读取和实例化。

有兴趣可看看里面的LazyIterator类和parse方法。


## Spring SPI

核心类是org.springframework.core.io.support.SpringFactoriesLoader。  
通过此类将定义在spring.factories文件中的可插拔组件加载出来。

### 例子

[代码在这 | spring-boot-none-startup](https://github.com/teaho2015-blog/spring-source-code-learning-demo/tree/master/spring-boot-none-startup)

我在spring boot启动的示例里写了Spring SPI的示例。

~~~
//定义service
public interface DemoSpringLoader {

}

//服务提供者实现
public class DemoSpringLoaderImpl implements DemoSpringLoader {
}

// resources/META-INF/spring.factories文件下添加自定义键值对
net.teaho.demo.spring.boot.startup.none.spring.spi.DemoSpringLoader=\
net.teaho.demo.spring.boot.startup.none.spring.spi.DemoSpringLoaderImpl


//调用
List<DemoSpringLoader> inst = new ArrayList<>(
        SpringFactoriesLoader.loadFactories(DemoSpringLoader.class, this.getClass().getClassLoader()));

log.info(inst.toString());

//输出
[net.teaho.demo.spring.boot.startup.none.spring.spi.DemoSpringLoaderImpl@11a82d0f]

~~~

### SpringFactoriesLoader

对类中重要方法做简单注释：
~~~

	public static <T> List<T> loadFactories(Class<T> factoryClass, @Nullable ClassLoader classLoader) {
		Assert.notNull(factoryClass, "'factoryClass' must not be null");
		ClassLoader classLoaderToUse = classLoader;
		if (classLoaderToUse == null) {
			classLoaderToUse = SpringFactoriesLoader.class.getClassLoader();
		}
        //找出META-INF/spring.factories文件中对应factoryClass名称的实现类名称
		List<String> factoryNames = loadFactoryNames(factoryClass, classLoaderToUse);
		if (logger.isTraceEnabled()) {
			logger.trace("Loaded [" + factoryClass.getName() + "] names: " + factoryNames);
		}
		List<T> result = new ArrayList<>(factoryNames.size());
		for (String factoryName : factoryNames) {
            //检查是否为service的子类并实例化
			result.add(instantiateFactory(factoryName, factoryClass, classLoaderToUse));
		}
		AnnotationAwareOrderComparator.sort(result);
		return result;
	}

~~~


## Dubbo SPI

Dubbo SPI的设计比较复杂。核心类是`org.apache.dubbo.common.extension.ExtensionLoader<T>`。

Dubbo SPI的思想脱胎于Java SPI，机制上进行了加强。

Dubbo SPI的一些概念：  
拓展点相当于接口，也相当于服务（service），  
拓展（extension）则相当于服务提供者（service provider）。

拓展定义约定：
> 在扩展类的 jar 包内，放置扩展点配置文件 META-INF/dubbo/接口全限定名，内容为：配置名=扩展实现类全限定名，多个实现类用换行符分隔。

简单来说，有三种获取拓展的方式。
1. ExtensionLoader的getExtension(name)通过名字找到配置名的拓展。
2. 通过getActivateExtension()，也就是扩展点自动激活，可同时加载多个拓展实现。可以通过一些筛选条件获取一个拓展集合。
   比如，通过一些条件获取一个Filter集合。
3. 拓展点自适应（adaptive extension），能够（通过java assist生成的代理对象）直到扩展点方法执行时才决定调用哪一个扩展点实现。
   包括拓展点的注入拓展也如此。 
   
Dubbo SPI这一小节文章以后会搬到这里：[https://dubbo-learning.gitbook.teaho.net/](https://dubbo-learning.gitbook.teaho.net/)
   
### 例子

我在[github|dubbo spi demo](https://github.com/teaho2015-blog/spring-source-code-learning-demo/tree/master/dubbo-spi-demo)
的service模块里写了getActivateExtension，extension，和adaptive extension的test case。

### 代码分析

Dubbo的文档比较完善，而且还涉及到框架设计和源码解读（文章对源代码进行了大量注释），  
请查看这两篇文章：  
[Dubbo doc|Dubbo SPI][Links: Dubbo doc|Dubbo SPI]  
[Dubbo doc|SPI 自适应拓展][Links: Dubbo doc|SPI 自适应拓展]  

对源码进行了行级注释，如有疑问可通过上一节的例子（test case）进行调试。

## 对比

Java SPI是SPI的一个基础实现。

Spring SPI和Java SPI大同小异，不过Spring SPI允许在一个文件里定义多个不同的SPI服务。
一些拓展过滤，并非在loader处实现，基本通过Conditional注解和ImportSelector来完成。

Dubbo SPI的优点正如文档所说：
> Dubbo 改进了 JDK 标准的 SPI 的以下问题： 
>  * JDK 标准的 SPI 会一次性实例化扩展点所有实现，如果有扩展实现初始化很耗时，但如果没用上也加载，会很浪费资源。
>  * 如果扩展点加载失败，连扩展点的名称都拿不到了。比如：JDK 标准的 ScriptEngine，通过 getName() 获取脚本类型的名称，但如果 RubyScriptEngine 因为所依赖的 jruby.jar 不存在，导致 RubyScriptEngine 类加载失败，这个失败原因被吃掉了，和 ruby 对应不起来，当用户执行 ruby 脚本时，会报不支持 ruby，而不是真正失败的原因。
>  * 增加了对扩展点 IoC 和 AOP 的支持，一个扩展点可以直接 setter 注入其它扩展点。

补充一点，Dubbo SPI还有@Activate注解，可以通过一些过滤条件获取一个拓展集合。

## Reference

[1] [wikipedia|Service provider interface](https://en.wikipedia.org/wiki/Service_provider_interface)  
[2] [Java 8 doc|Class ServiceLoader<S>](https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html)  
[3] [Dubbo doc|Dubbo SPI][Links: Dubbo doc|Dubbo SPI]  
[4] [Dubbo doc|SPI 自适应拓展][Links: Dubbo doc|SPI 自适应拓展]   
[5] [Dubbo doc|扩展点加载](https://dubbo.apache.org/zh/docs/v2.7/dev/spi/)

[Links: Dubbo doc|Dubbo SPI]: http://dubbo.apache.org/zh/docs/v2.7/dev/source/dubbo-spi
[Links: Dubbo doc|SPI 自适应拓展]: http://dubbo.apache.org/zh/docs/v2.7/dev/source/adaptive-extension/#21-%E8%8E%B7%E5%8F%96%E8%87%AA%E9%80%82%E5%BA%94%E6%8B%93%E5%B1%95





