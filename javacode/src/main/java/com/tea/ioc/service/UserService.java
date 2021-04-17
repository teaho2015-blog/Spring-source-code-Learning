package com.tea.ioc.service;


import com.tea.ioc.dao.UserDAO;
import com.tea.ioc.model.User;

public class UserService {
	private UserDAO userDAO;

	public UserService() {

	}

	public UserService(UserDAO userDAO) {
		this.userDAO = userDAO;
	}
	public void add(User user) {
		userDAO.save(user);
	}
	public UserDAO getUserDAO() {
		return userDAO;
	}
	public void setUserDAO(UserDAO userDAO) {
		this.userDAO = userDAO;
	}
}
