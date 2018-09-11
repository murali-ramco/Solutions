package com.rsp.core.integration.service;

import java.lang.reflect.Method;

import com.featherlite.aps.base.Activator;
import com.rsp.core.base.db.DBUploadProperties;
import com.rsp.core.base.init.RSPConfigConstants;
import com.rsp.core.base.model.Event;
import com.rsp.core.base.model.Order;
import com.rsp.core.base.model.Resource;
import com.rsp.core.base.model.Workflow;
import com.rsp.core.base.service.AbstractService;
import com.rsp.core.base.service.ServiceArgument;
import com.rsp.core.base.service.ServiceResult;
import com.rsp.core.base.service.SimpleServiceResult;

/**
 * @author 10413
 *
 */
public class RemoteEventService extends AbstractService {


	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public class Argument implements ServiceArgument {

		/**t
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public String eventAdaptorClassName = null;
		public String jdbcDriver = null;
		public String jdbcUrl = null;
		public String jdbcUserId = null;
		public String jdbcPassword = null;		
		public String jdbcPath = null;

		public String uuid = null;
		public String taskId = null;
		public String actionId = null;

		public Event event;
		
		public Order order;
		
		public Resource resource;
		
		public Workflow workflow;
		
		public boolean isPersist;

	}

	@Override
	public ServiceResult doService(ServiceArgument argument) throws Exception {
		Argument arg = (Argument) argument;

		ClassLoader myClassLoader = Activator.class.getClassLoader();   			
		Class<?> myClass = myClassLoader.loadClass(arg.eventAdaptorClassName);			
		Object instance = myClass.newInstance();

		Method myMethod = myClass.getMethod(DBUploadProperties.METHOD_NAME, new Class[] {Argument.class});
		String returnValue = (String) myMethod.invoke(instance, new Object[] {arg});

		if(!returnValue.equals(RSPConfigConstants.TRUE)) {
			logger.info("Event execution fail due to "+ returnValue);
		} else {
			logger.info("Event executed successfully ......");
		}

		return new SimpleServiceResult();
	}

	@Override
	public Argument getArgumentInstance() {
		return new Argument();
	}


}
