/**
 * @author teaho2015@gmail.com
 * since 2017/5/26
 */
package com.tea.ioc;


import com.tea.ioc.model.User;
import com.tea.ioc.service.UserService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:ioc/applicationContext-ioc-bean.xml"
})
public class UserServiceTester {
    @Autowired
    private UserService iocService;

    @Before
    public void setup() {
//        MockitoAnnotations.initMocks(this);

    }

    @Test
    public void testAddUser_success() {
        User user = new User();
        user.setUsername("default");
        user.setPassword("pwd");
        iocService.add(user);

    }

}
