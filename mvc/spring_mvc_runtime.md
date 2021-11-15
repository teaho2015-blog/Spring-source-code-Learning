# SpringMVC请求处理及组件分析

## 总览

在[SpringMVC启动分析](spring-mvc-startup.md)一节，
我们聊到DispatcherServlet这一SpringMVC的核心处理器的继承关系。  
![DispatcherServlet_inheritance.png](DispatcherServlet_inheritance.png)

DispatcherServlet作为Servlet的一个实现，
我们从Servlet容器（Tomcat、Jetty等）的角度看看他们处理HTTP请求时
是如何调用Servlet实现的。

首先会调用到Servlet的service方法。
~~~
    public void service(ServletRequest req, ServletResponse res)
            throws ServletException, IOException;
            
~~~

DispatcherServlet的父类调用源码不粘贴在此处分析（因为有点累赘），  
实际上，是HttpServlet实现了service方法，并分发到doGet、doPost、doPut、doDelete等方法，  
然后其子类FrameworkServlet实现了这些方法，并最终调用自身的抽象doService方法`protected abstract void doService(HttpServletRequest request, HttpServletResponse response)throws Exception;`  
而DispatcherServlet实现了doService方法，然后来到了SpringMVC的处理逻辑。

doService方法会设置一些框架对象，WebApplicationContext等到request属性中，接着
我们看看核心方法doDispatch:

~~~
	protected void doDispatch(HttpServletRequest request, HttpServletResponse response) throws Exception {
		HttpServletRequest processedRequest = request;
		HandlerExecutionChain mappedHandler = null;
		boolean multipartRequestParsed = false;
        //1 给需要异步处理的请求使用，一般是给SPI使用，不是通用管理器
		WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);

		try {
			ModelAndView mv = null;
			Exception dispatchException = null;

			try {
		        //2 使用multipartResolver检查并解析出文件数据封装到MultipartFile并加到request属性中
				processedRequest = checkMultipart(request);
				multipartRequestParsed = (processedRequest != request);

				// Determine handler for the current request.
				//3 找到请求处理handler
                mappedHandler = getHandler(processedRequest);
				if (mappedHandler == null) {
					noHandlerFound(processedRequest, response);
					return;
				}

				// Determine handler adapter for the current request.
				//4
				HandlerAdapter ha = getHandlerAdapter(mappedHandler.getHandler());

				// Process last-modified header, if supported by the handler.
				String method = request.getMethod();
				boolean isGet = "GET".equals(method);
				if (isGet || "HEAD".equals(method)) {
					long lastModified = ha.getLastModified(request, mappedHandler.getHandler());
					if (new ServletWebRequest(request, response).checkNotModified(lastModified) && isGet) {
						return;
					}
				}

				if (!mappedHandler.applyPreHandle(processedRequest, response)) {
					return;
				}

				// Actually invoke the handler.
				mv = ha.handle(processedRequest, response, mappedHandler.getHandler());

				if (asyncManager.isConcurrentHandlingStarted()) {
					return;
				}

				applyDefaultViewName(processedRequest, mv);
				mappedHandler.applyPostHandle(processedRequest, response, mv);
			}
			catch (Exception ex) {
				dispatchException = ex;
			}
			catch (Throwable err) {
				// As of 4.3, we're processing Errors thrown from handler methods as well,
				// making them available for @ExceptionHandler methods and other scenarios.
				dispatchException = new NestedServletException("Handler dispatch failed", err);
			}
			processDispatchResult(processedRequest, response, mappedHandler, mv, dispatchException);
		}
		catch (Exception ex) {
			triggerAfterCompletion(processedRequest, response, mappedHandler, ex);
		}
		catch (Throwable err) {
			triggerAfterCompletion(processedRequest, response, mappedHandler,
					new NestedServletException("Handler processing failed", err));
		}
		finally {
			if (asyncManager.isConcurrentHandlingStarted()) {
				// Instead of postHandle and afterCompletion
				if (mappedHandler != null) {
					mappedHandler.applyAfterConcurrentHandlingStarted(processedRequest, response);
				}
			}
			else {
				// Clean up any resources used by a multipart request.
				if (multipartRequestParsed) {
					cleanupMultipart(processedRequest);
				}
			}
		}
	}
	
	//返回HandlerExecutionChain，请求处理链
    @Nullable
	protected HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception {
		if (this.handlerMappings != null) {
			for (HandlerMapping mapping : this.handlerMappings) {
                //3.1 
				HandlerExecutionChain handler = mapping.getHandler(request);
				if (handler != null) {
					return handler;
				}
			}
		}
		return null;
	}


~~~




## DispatcherServlet如何定位Controller

### Controller方法如何被加载


### 处理器调用











