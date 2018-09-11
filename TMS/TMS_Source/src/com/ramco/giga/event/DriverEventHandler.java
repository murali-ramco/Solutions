package com.ramco.giga.event;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;

import com.ramco.giga.constant.GigaConstants;
import com.ramco.giga.dbupload.GigaDBUpload;
import com.ramco.giga.utils.GigaUtils;
import com.rsp.core.base.RSPContainerHelper;
import com.rsp.core.base.encrypt.PasswordEncrypter;
import com.rsp.core.base.model.Activity;
import com.rsp.core.base.model.ElementMap;
import com.rsp.core.base.model.Order;
import com.rsp.core.base.model.Resource;
import com.rsp.core.base.model.parameter.BooleanParameter;
import com.rsp.core.base.model.parameter.StringParameter;
import com.rsp.core.planning.service.EventService;

public class DriverEventHandler {
    private final static Logger logger = Logger.getLogger(DriverEventHandler.class);
    List<String> orderList = new ArrayList<String>(); // re-plan order list
    List<String> outstationOrderList = new ArrayList<String>();
    long halfDayCutOffTime = 0;

    public String doService(EventService.Argument argument) {
	logger.info("Inside doService method--->>>>");
	String result = "true";
	// String orderType = null;
	Connection conn = null;
	String driverId = null;
	String truckId = null;
	int tabletLogin = 0;
	int unavailableTomorrow = 0;
	int availableToday = 0;
	int availableTomorrow = 0;
	int unavailableToday = 0;
	Order order = argument.order;
	if (order == null) {
	    return "order argument is null";
	}
	try {
	    Class.forName(argument.jdbcDriver);
	    conn = DriverManager.getConnection(argument.jdbcUrl, argument.jdbcUserId, PasswordEncrypter.decrypt(argument.jdbcPassword));
	    conn.setAutoCommit(false);
	    if (order != null) {
		// orderType = order.getType();
		logger.info("order id: " + order.getId());
		if (order.hasParameter("driverId")) {
		    driverId = order.getParameterBy("driverId").getValueAsString();
		} else {
		    return "driverId parameter is missing in the event parameter list.";
		}
		if (order.hasParameter("truckId")) {
		    truckId = order.getParameterBy("truckId").getValueAsString();
		} else {
		    return "truckId parameter is missing in the event parameter list.";
		}
		if (order.hasParameter("tabletLogin")) {
		    tabletLogin = (Integer) order.getParameterBy("tabletLogin").getValue();
		} else {
		    return "tabletLogin parameter is missing in the event parameter list.";
		}
		if (order.hasParameter("availableToday")) {
		    availableToday = (Integer) order.getParameterBy("availableToday").getValue();
		} else {
		    return "availableToday parameter is missing in the event parameter list.";
		}
		if (order.hasParameter("unavailableToday")) {
		    unavailableToday = (Integer) order.getParameterBy("unavailableToday").getValue();
		} else {
		    return "unavailableToday parameter is missing in the event parameter list.";
		}
		if (order.hasParameter("availableTomorrow")) {
		    availableTomorrow = (Integer) order.getParameterBy("availableTomorrow").getValue();
		} else {
		    return "availableTomorrow parameter is missing in the event parameter list.";
		}
		if (order.hasParameter("unavailableTomorrow")) {
		    unavailableTomorrow = (Integer) order.getParameterBy("unavailableTomorrow").getValue();
		} else {
		    return "unavailableTomorrow parameter is missing in the event parameter list.";
		}
		halfDayCutOffTime = getDateValue();
	    }
	    driverEvent(driverId, truckId, tabletLogin, availableToday, unavailableToday, availableTomorrow, unavailableTomorrow, halfDayCutOffTime, conn);
	} catch (Exception e) {
	    e.printStackTrace();
	} finally {
	    if (conn != null) {
		try {
		    conn.close();
		    conn = null;
		} catch (Exception e) {
		    e.printStackTrace();
		}
	    }
	}
	logger.info("After doService method--->>>>");
	return result;
    }

    public long getDateValue() {
	String halfDayCutOff = null;
	Resource gigaParamRes = RSPContainerHelper.getResourceMap(true).getEntryBy(GigaConstants.RES_GIGA_PARAM);
	if (gigaParamRes.hasParameter(GigaConstants.GIGA_PARAM_HALF_DAY_CUT_OFF_TIME)) {
	    halfDayCutOff = gigaParamRes.getParameterBy(GigaConstants.GIGA_PARAM_HALF_DAY_CUT_OFF_TIME).getValueAsString();
	}
	Date today = Calendar.getInstance().getTime();
	SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd hh.mm.ss");
	String currentDate = formatter.format(today); // current date time
	String dateAndTime[] = currentDate.split(" ");
	String _date = dateAndTime[0]; // current date
	logger.info("current_date: " + _date);
	// time we will get it from halfDayCutOff time
	String myDate = _date + " " + halfDayCutOff; // halfDayCutOff should be
	// in HH:mm:ss format in
	// the db
	SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
	Date date = null;
	try {
	    date = sdf.parse(myDate);
	} catch (ParseException e) {
	    e.printStackTrace();
	} catch (Exception e) {
	    e.printStackTrace();
	}
	long millis = date.getTime();
	logger.info("HalfDayCutOffTime in long value: " + millis);
	return millis;
    }

    public void driverEvent(String driverId, String truckId, int tabletLogin, int availableToday, int unavailableToday, int availableTomorrow, int unavailableTomorrow, long halfDayCutOffTime, Connection conn) {
	if (truckId == null) {
	    logger.info("truck id cannot be null");
	} else {
	    try {
		Resource truckResource = RSPContainerHelper.getResourceMap(true).getEntryBy(truckId);
		Resource driverResource = RSPContainerHelper.getResourceMap(true).getEntryBy(driverId);
		if (truckResource != null) {
		    if (unavailableToday == 1) {
			// make the driver associated with the truck as TBA
			if (truckResource.hasParameter("driverId"))
			    ((StringParameter) truckResource.getParameterBy("driverId")).setValue("TBA");
			else
			    logger.info("The driverId parameter is missing in the truck resource: " + truckId);
		    } else if (availableToday == 1) {
			// set driver id = driver id from event
			if (truckResource.hasParameter("driverId"))
			    ((StringParameter) truckResource.getParameterBy("driverId")).setValue(driverId);
			else
			    logger.info("The driverId parameter is missing in the truck resource: " + truckId);
		    }
		} else {
		    logger.info("Truck with resource id " + truckId + "is not available.");
		}
		if (driverResource != null) {
		    if (unavailableTomorrow == 1) {
			// set forceLocal as true
			// ((StringParameter)truckResource.getParameterBy(GigaConstants.FORCELOCAL)).setValue("True");
			if (driverResource.hasParameter(GigaConstants.FORCELOCAL))
			    ((BooleanParameter) driverResource.getParameterBy(GigaConstants.FORCELOCAL)).setValue(true);
			else
			    logger.info("The forceLocal parameter is missing in the driver resource: " + driverId);
		    } else if (availableTomorrow == 1) {
			// set forceLocal as false
			// ((StringParameter)truckResource.getParameterBy(GigaConstants.FORCELOCAL)).setValue("False");
			if (driverResource.hasParameter(GigaConstants.FORCELOCAL))
			    ((BooleanParameter) driverResource.getParameterBy(GigaConstants.FORCELOCAL)).setValue(false);
			else
			    logger.info("The forceLocal parameter is missing in the driver resource: " + driverId);
		    }
		} else {
		    logger.info("Driver with resource id " + driverId + "is not available.");
		}
		// List<Activity> activities =
		// truckResource.getActivitiesInInterval(Long.MIN_VALUE,
		// Long.MAX_VALUE);
		if (truckResource != null) {
		    List<Activity> activities = truckResource.getActivitiesInInterval(System.currentTimeMillis(), Long.MAX_VALUE);
		    // List<Activity> activitieAtCurrentTime =
		    // truckResource.getActivitiesAt(System.currentTimeMillis());
		    if (unavailableToday == 1) {
			ElementMap<Order> orders = RSPContainerHelper.getOrderMap(true);
			if (activities.size() > 0) {
			    long startTime = 0;
			    long endTime = 0;
			    logger.info("start time: " + startTime);
			    logger.info("end time: " + endTime);
			    for (Activity act : activities) {
				startTime = act.getStart();
				endTime = act.getEnd();
				logger.info("start time: " + startTime);
				logger.info("end time: " + endTime);
				if (startTime > System.currentTimeMillis()) {
				    String orderId = act.getOrderNetId();
				    logger.info("orderId:+++>>>: " + orderId);
				    Order existOrder = orders.getEntryBy(orderId);
				    String orderType = existOrder.getType();
				    if (!orderType.equalsIgnoreCase(GigaConstants.ORD_TYPE_MAINTENANCE)) {
					orderList.add(orderId);
					logger.info("OrderIdList: " + orderList);
				    }
				}
			    }
			}
			if (tabletLogin == 0) {
			    if (System.currentTimeMillis() >= halfDayCutOffTime) {
				// nothing to do
			    } else {
				// Get activities start before current time and Add
				// to re-plan order list
				List<Activity> activitiesBeforeStartTime = truckResource.getActivitiesInInterval(Long.MIN_VALUE, System.currentTimeMillis());
				for (Activity activity : activitiesBeforeStartTime) {
				    String orderId = activity.getOrderNetId();
				    Order existOrder = orders.getEntryBy(orderId);
				    String orderType = existOrder.getType();
				    if (!orderType.equalsIgnoreCase(GigaConstants.ORD_TYPE_MAINTENANCE)) {
					orderList.add(orderId);
				    }
				}
			    }
			} else {
			    // do nothing
			}
		    } else if (availableToday == 0 && unavailableToday == 0 && unavailableTomorrow == 1) {
			// get the outstation order and add to the re-plan order
			// list
			logger.info("activities size: " + activities.size());
			if (activities.size() > 0) {
			    ElementMap<Order> orders = RSPContainerHelper.getOrderMap(true);
			    long startTime = 0;
			    for (Activity act : activities) {
				startTime = act.getStart();
				logger.info("outstation startTime: " + startTime);
				logger.info("outstation endTime: " + act.getEnd());
				logger.info("system current time: " + System.currentTimeMillis());
				if (startTime > System.currentTimeMillis()) {
				    String orderId = act.getOrderNetId();
				    logger.info("orderId: " + orderId);
				    Order existOrder = orders.getEntryBy(orderId);
				    String orderType = existOrder.getType();
				    String orderJobType = existOrder.getParameterBy(GigaConstants.ORD_PARAM_JOB_TYPE).getValueAsString();
				    if (!orderType.equalsIgnoreCase(GigaConstants.ORD_TYPE_MAINTENANCE) && GigaConstants.ORDER_TYPE_OUTSTATION.equalsIgnoreCase(orderJobType)) {
					orderList.add(orderId);
				    }
				}
			    }
			}
			logger.info("before unplan: " + orderList);
		    }
		    // unplan all the order from replan order list
		    ElementMap<Order> orders = RSPContainerHelper.getOrderMap(true);
		    if (orderList.size() > 0) {
			GigaDBUpload.clearStateEntries(orderList, conn); // remove the order status from ral_Order_StateVariable_Update table
			GigaDBUpload.changeOrderStatus(orderList, conn); // change the order status to unplan in the ral_ipo_order_dtl table
			for (int i = 0; i < orderList.size(); i++) {
			    Order existOrder = orders.getEntryBy(orderList.get(i));
			    GigaUtils.unplanOrders(existOrder);
			    GigaUtils.clearDriverLoadStatus(existOrder);
			    /*String truck = truckId.concat(" / ").concat(truckResource.getParameterBy("truckRegNo").getValueAsString());
			    String driver = driverId.concat(" / ").concat(driverResource.getName());
			    StringParameter prevTruck = new StringParameter("previousTruck", "Previous Truck", truck);
			    StringParameter prevDriver = new StringParameter("previousDriver", "Previous Driver", driver);
			    existOrder.addParameter(prevTruck);
			    existOrder.addParameter(prevDriver);*/
			    StringParameter batchIdParam = (StringParameter) existOrder.getParameterBy("batchId");
			    String batchId = batchIdParam.getValueAsString();
			    batchIdParam.setValue(batchId.concat("_abc"));
			    GigaUtils.addPreviousTruckDriver(existOrder, truckId, driverId);
			    GigaUtils.planOrder(existOrder); // planning the order
			    GigaUtils.changeDbStatus(existOrder); // change it to zero
			}
		    }
		    PreparedStatement pst = null;
		    try {
			pst = conn.prepareStatement("update DriverLeaveEvent set flag = 'C' where driverid = ? and flag = 'P'");
			pst.setString(1, driverId);
			int x = pst.executeUpdate();
			logger.info(x + " updated successfully");
			logger.info("flag changed successfully");
			conn.commit();
		    } catch (Exception e) {
			e.printStackTrace();
		    } finally {
			if (pst != null) {
			    try {
				pst.close();
			    } catch (SQLException e) {
				e.printStackTrace();
			    } catch (Exception e2) {
				e2.printStackTrace();
			    }
			}
		    }
		}
	    } catch (Exception e) {
		e.printStackTrace();
	    }
	}
    } // closing of the driver event method
}
