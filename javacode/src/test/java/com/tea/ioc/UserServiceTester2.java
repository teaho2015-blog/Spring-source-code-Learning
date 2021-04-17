/**
 * @author teaho2015@gmail.com
 * since 2017/5/26
 */
package com.tea.ioc;


import com.tea.ioc.model.User;
import com.tea.ioc.service.UserService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * use ClassPathXmlApplicationContext for bean generation
 */
public class UserServiceTester2 {


    private ClassPathXmlApplicationContext ctx;

    @Before
    public void setup() {
        ctx = new ClassPathXmlApplicationContext("classpath:spring/applicationContext-ioc-bean.xml");
//        System.out.println(ctx.getId());
//        for (String s : ctx.getBeanDefinitionNames()) {
//            System.out.println(s);
//        }
    }

    @Test
    public void testAddUser_success() {
        UserService userService = (UserService)ctx.getBean("iocService");
        User user = new User();
        user.setUsername("default");
        user.setPassword("pwd");
        userService.add(user);

    }

    @After
    public void endUp() {
        ctx.destroy();
    }

}
