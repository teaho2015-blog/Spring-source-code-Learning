/**
 * @author teaho2015@gmail.com
 * since 2017/5/26
 */
package com.tea.ioc;

import com.tea.ioc.dao.impl.UserDAOImpl;
import com.tea.ioc.model.User;
import com.tea.ioc.service.UserService;
import org.junit.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;

public class IocTester {

    /**
     * simulation spring beanFactory generates beans by setter methods
     */
    @Test
    public void testBeanFactoryGenerateBeans_bindViaCode() {
        DefaultListableBeanFactory beanRegistry = new DefaultListableBeanFactory();
        BeanFactory beanFactory = bindViaCode(beanRegistry);
        UserService userService = (UserService) beanFactory.getBean("userService");
        userService.add(new User());

    }

    /**
     * simulation spring beanFactory generates beans by setter methods
     */
    @Test
    public void testBeanFactoryGenerateBeans_bindViaXml() {
        DefaultListableBeanFactory beanRegistry = new DefaultListableBeanFactory();
        BeanFactory beanFactory = bindViaXML(beanRegistry);
        UserService userService = (UserService) beanFactory.getBean("userService");
        userService.add(new User());

    }


    public BeanFactory bindViaCode(BeanDefinitionRegistry registry) {

        AbstractBeanDefinition userService = new RootBeanDefinition(UserService.class);
        AbstractBeanDefinition userDAOImpl = new RootBeanDefinition(UserDAOImpl.class);

        registry.registerBeanDefinition("userService", userService);
        registry.registerBeanDefinition("userDAO", userDAOImpl); //"userDAO" is bean id

        // bind injection, this way uses "setter"
        MutablePropertyValues propertyValues = new MutablePropertyValues();
        propertyValues.addPropertyValue(new PropertyValue("userDAO", userDAOImpl)); //judge invoke setUserDAO
        userService.setPropertyValues(propertyValues);

        // bind injection, this way is "constructor"
       /* ConstructorArgumentValues argumentValues = new ConstructorArgumentValues();
        argumentValues.addIndexedArgumentValue(0, userDAOImpl);
        userService.setConstructorArgumentValues(argumentValues);*/
        return (BeanFactory) registry;
    }

    public BeanFactory bindViaXML(BeanDefinitionRegistry registry) {
        XmlBeanDefinitionReader xmlBeanDefinitionReader = new XmlBeanDefinitionReader(registry);
        xmlBeanDefinitionReader.loadBeanDefinitions("classpath:ioc/applicationContext-ioc-bean.xml");
        return (BeanFactory) registry;
    }
}
