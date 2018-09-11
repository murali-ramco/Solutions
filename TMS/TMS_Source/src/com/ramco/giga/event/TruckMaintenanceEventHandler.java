package com.ramco.giga.event;

import java.sql.Connection;
import java.sql.DriverManager;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;

import com.ramco.giga.constant.GigaConstants;
import com.ramco.giga.dbupload.GigaDBUpload;
import com.ramco.giga.utils.GigaUtils;
import com.rsp.core.base.RSPContainerHelper;
import com.rsp.core.base.encrypt.PasswordEncrypter;
import com.rsp.core.base.model.Activity;
import com.rsp.core.base.model.Order;
import com.rsp.core.base.model.Resource;
import com.rsp.core.base.model.parameter.BooleanParameter;
import com.rsp.core.base.model.parameter.DateParameter;
import com.rsp.core.base.model.parameter.DurationParameter;
import com.rsp.core.base.model.parameter.StringParameter;
import com.rsp.core.base.query.comparator.ActivityByEndComparator;
import com.rsp.core.planning.service.EventService;

public class TruckMaintenanceEventHandler {
    private final static Logger logger = Logger.getLogger(TruckMaintenanceEventHandler.class);
    private final static String TRUE = "true";
    private final static String FALSE = "false";

    public String doService(EventService.Argument argument) {
	logger.info("Inside doService method--->>>>");
	String result = TRUE;
	// String orderType = null;
	Connection conn = null;
	String truckId = null;
	String currentLocation = null;
	String orderId = null;
	String loadStatus = null;
	String truckStatus = null;
	String pickupStatus = null;
	long createdDate = 0;
	long resumeTime = 0;
	Boolean availableTomorrow = false;
	Boolean unavailableTomorrow = false;
	String flag = null;
	Order order = argument.order;
	if (order == null) {
	    return "order argument is null";
	}
	try {
	    Class.forName(argument.jdbcDriver);
	    conn = DriverManager.getConnection(argument.jdbcUrl, argument.jdbcUserId, PasswordEncrypter.decrypt(argument.jdbcPassword));
	    conn.setAutoCommit(false);
	    if (order != null) {
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		java.util.Date createdDateTime = df.parse(order.getParameterBy("createdDate").getValueAsString());
		java.util.Date resumeDateTime = df.parse(order.getParameterBy("resumeTime").getValueAsString());
		// orderType = order.getType();
		logger.info("order id: " + order.getId());
		if (order.hasParameter("truckId")) {
		    truckId = order.getParameterBy("truckId").getValueAsString();
		} else {
		    return "truckId parameter is missing in the event parameter list.";
		}
		if (order.hasParameter("currentLocation")) {
		    currentLocation = order.getParameterBy("currentLocation").getValueAsString();
		} else {
		    return "currentLocation parameter is missing in the event parameter list.";
		}
		if (order.hasParameter("orderId")) {
		    orderId = order.getParameterBy("orderId").getValueAsString();
		} else {
		    return "orderId parameter is missing in the event parameter list.";
		}
		if (order.hasParameter("loadStatus")) {
		    loadStatus = order.getParameterBy("loadStatus").getValueAsString();
		} else {
		    return "loadStatus parameter is missing in the event parameter list.";
		}
		if (order.hasParameter("truckStatus")) {
		    truckStatus = order.getParameterBy("truckStatus").getValueAsString();
		} else {
		    return "truckStatus parameter is missing in the event parameter list.";
		}
		if (order.hasParameter("pickupStatus")) {
		    pickupStatus = order.getParameterBy("pickupStatus").getValueAsString();
		} else {
		    return "pickupStatus parameter is missing in the event parameter list.";
		}
		if (order.hasParameter("createdDate")) {
		    createdDate = createdDateTime.getTime();
		} else {
		    return "createdDate parameter is missing in the event parameter list.";
		}
		if (order.hasParameter("resumeTime")) {
		    resumeTime = resumeDateTime.getTime();
		} else {
		    return "resumeTime parameter is missing in the event parameter list.";
		}
		if (order.hasParameter("availableTomorrow")) {
		    availableTomorrow = (Boolean) order.getParameterBy("availableTomorrow").getValue();
		} else {
		    return "availableTomorrow parameter is missing in the event parameter list.";
		}
		if (order.hasParameter("unavailableTomorrow")) {
		    unavailableTomorrow = (Boolean) order.getParameterBy("unavailableTomorrow").getValue();
		} else {
		    return "unavailableTomorrow parameter is missing in the event parameter list.";
		}
		if (order.hasParameter("flag")) {
		    flag = order.getParameterBy("flag").getValueAsString();
		} else {
		    return "flag parameter is missing in the event parameter list.";
		}
	    }
	    truckMaintenanceEvent(truckId, currentLocation, orderId, loadStatus, truckStatus, pickupStatus, createdDate, resumeTime, availableTomorrow, unavailableTomorrow, flag, conn);
	} catch (Exception e) {
	    result = FALSE;
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

    public static void truckMaintenanceEvent(String truckId, String currentLocation, String orderid, String loadStatus, String truckStatus, String pickupStatus, long createdDate, long resumeTime, Boolean availableTomorrow, Boolean unavailableTomorrow, String flag, Connection conn) {
	Resource truckRes = RSPContainerHelper.getResourceMap(true).getEntryBy(truckId);
	logger.info("inside truckMaintenanceEvent--->>>>");
	// ForceLocal update
	if (availableTomorrow) {
	    if (truckRes.hasParameter(GigaConstants.FORCELOCAL))
		((BooleanParameter) truckRes.getParameterBy(GigaConstants.FORCELOCAL)).setValue(false);
	    else {
		BooleanParameter forceLocalP = new BooleanParameter(GigaConstants.FORCELOCAL, GigaConstants.FORCELOCAL, false);
		truckRes.addParameter(forceLocalP);
	    }
	}
	if (unavailableTomorrow) {
	    if (truckRes.hasParameter(GigaConstants.FORCELOCAL))
		((BooleanParameter) truckRes.getParameterBy(GigaConstants.FORCELOCAL)).setValue(true);
	    else {
		BooleanParameter forceLocalP = new BooleanParameter(GigaConstants.FORCELOCAL, GigaConstants.FORCELOCAL, true);
		truckRes.addParameter(forceLocalP);
	    }
	}
	List<Activity> prevTasks = truckRes.getActivitiesInInterval(Long.MIN_VALUE, Long.MAX_VALUE);
	Collections.sort(prevTasks, new ActivityByEndComparator());
	long currentTime = System.currentTimeMillis();
	Order eventOrder = RSPContainerHelper.getOrderMap(true).getEntryBy(orderid);
	Activity currAct = truckRes.getActivityMap().get("/Workflow/" + orderid + "/Elements/chooseRes/Elements/selectTruck");
	long currActEndTime = 0;
	if (currAct == null) {
	    if (truckRes.getActivitiesAt(System.currentTimeMillis()).size() > 0) {
		currAct = truckRes.getActivitiesAt(System.currentTimeMillis()).get(0);
		currActEndTime = currAct.getEnd();
		eventOrder = RSPContainerHelper.getOrderMap(true).getEntryBy(currAct.getOrderNetId());
	    } else {
		currActEndTime = System.currentTimeMillis();
	    }
	}
	// UnderMaintenance Event		
	if (truckStatus.equalsIgnoreCase(GigaConstants.TRUCK_BD_STATUS_UNDERMAINTENANCE)) {
	    logger.info("inside UNDERMAINTENANCE--->>>>");
	    List<Activity> minToCurrActivity = truckRes.getActivitiesInInterval(Long.MIN_VALUE, currentTime);
	    List<Activity> currToMaxActivity = prevTasks;
	    currToMaxActivity.removeAll(minToCurrActivity);
	    logger.info(currToMaxActivity);
	    Collections.sort(currToMaxActivity, new ActivityByEndComparator());
	    UnplanOrders(currToMaxActivity, conn);
	    /*
	     * if (RSPContainerHelper.getOrderMap(true).getEntryBy(truckId + GigaConstants.MAINTENANCE_ORDER_SUFFIX) != null) ; else */
	    setTruckToMaintenance(truckId, truckRes.getActivitiesAt(currentTime));
	    planOrders(currToMaxActivity, GigaConstants.SCRIPT_PLANORDERCR);
	}
	// Resume Event				
	if (truckStatus.equalsIgnoreCase(GigaConstants.TRUCK_BD_STATUS_RESUME)) {
	    if (RSPContainerHelper.getOrderMap(true).getEntryBy(truckId + GigaConstants.MAINTENANCE_ORDER_SUFFIX) != null) {
		Order maintenanceOrder = RSPContainerHelper.getOrderMap(true).getEntryBy(truckId + GigaConstants.MAINTENANCE_ORDER_SUFFIX);
		GigaUtils.unplanOrders(maintenanceOrder);
		GigaUtils.evictOrder(maintenanceOrder);
		GigaUtils.removeOrder(maintenanceOrder);
	    }
	    List<Activity> currToMaxActivity = truckRes.getActivitiesInInterval(currActEndTime, Long.MAX_VALUE);
	    Collections.sort(currToMaxActivity, new ActivityByEndComparator());
	    if ((StringParameter) eventOrder.getParameterBy(GigaConstants.TRUCK_LOAD_PICKUP_STATUS) != null)
		((StringParameter) eventOrder.getParameterBy(GigaConstants.TRUCK_LOAD_PICKUP_STATUS)).setValue(pickupStatus);
	    else {
		StringParameter pickupOrderParameter = new StringParameter(GigaConstants.TRUCK_LOAD_PICKUP_STATUS, GigaConstants.TRUCK_LOAD_PICKUP_STATUS, pickupStatus);
		eventOrder.addParameter(pickupOrderParameter);
	    }
	    if ((StringParameter) eventOrder.getParameterBy(GigaConstants.TRUCK_EVENT_STATUS) != null)
		((StringParameter) eventOrder.getParameterBy(GigaConstants.TRUCK_EVENT_STATUS)).setValue(truckStatus);
	    else {
		StringParameter eventOrderParameter = new StringParameter(GigaConstants.TRUCK_EVENT_STATUS, GigaConstants.TRUCK_EVENT_STATUS, truckStatus);
		eventOrder.addParameter(eventOrderParameter);
	    }
	    List<String> clearDbUpload1 = new ArrayList<String>();
	    if (eventOrder != null) {
		clearDbUpload1.add(eventOrder.getId());
		GigaUtils.unplanOrders(eventOrder);
		GigaUtils.clearDriverLoadStatus(eventOrder);
		GigaUtils.changeDbStatus(eventOrder);
		GigaUtils.evictOrder(eventOrder);
	    }
	    GigaDBUpload.changeOrderStatus(clearDbUpload1, conn);
	    GigaDBUpload.clearStateEntries(clearDbUpload1, conn);
	    UnplanOrders(currToMaxActivity, conn);
	    if (currAct != null) {
		List<Activity> orderedActivity = new ArrayList<Activity>();
		if (currToMaxActivity.contains(currAct))
		    ;
		else {
		    orderedActivity.add(currAct);
		    orderedActivity.addAll(currToMaxActivity);
		    currToMaxActivity.clear();
		    currToMaxActivity.addAll(orderedActivity);
		}
	    }
	    logger.info("==currToMaxActivity==" + currToMaxActivity);
	    planOrders(currToMaxActivity, GigaConstants.SCRIPT_PLANMAINTENANCEORDERCR);
	}
	// Resume Event end 		
	if (truckStatus.equalsIgnoreCase(GigaConstants.TRUCK_BD_STATUS_SHORT_CLOSE)) {
	    List<Activity> currToMaxActivity = truckRes.getActivitiesInInterval(currActEndTime, Long.MAX_VALUE);
	    Collections.sort(currToMaxActivity, new ActivityByEndComparator());
	    if (currToMaxActivity.size() < 1)
		;
	    else {
		Order currentOrder = RSPContainerHelper.getOrderMap(true).getEntryBy(currToMaxActivity.get(0).getOrderNetId());
		if ((StringParameter) currentOrder.getParameterBy(GigaConstants.TRUCK_LOAD_PICKUP_STATUS) != null)
		    ((StringParameter) currentOrder.getParameterBy(GigaConstants.TRUCK_LOAD_PICKUP_STATUS)).setValue(pickupStatus);
		else {
		    StringParameter pickupOrderParameter = new StringParameter(GigaConstants.TRUCK_LOAD_PICKUP_STATUS, GigaConstants.TRUCK_LOAD_PICKUP_STATUS, pickupStatus);
		    currentOrder.addParameter(pickupOrderParameter);
		}
		if ((StringParameter) currentOrder.getParameterBy(GigaConstants.TRUCK_EVENT_STATUS) != null)
		    ((StringParameter) currentOrder.getParameterBy(GigaConstants.TRUCK_EVENT_STATUS)).setValue(truckStatus);
		else {
		    StringParameter eventOrderParameter = new StringParameter(GigaConstants.TRUCK_EVENT_STATUS, GigaConstants.TRUCK_EVENT_STATUS, truckStatus);
		    currentOrder.addParameter(eventOrderParameter);
		}
		GigaUtils.addPreviousTruckDriver(currentOrder, truckId, "");
	    }
	    List<String> clearDbUpload2 = new ArrayList<String>();
	    if (eventOrder != null) {
		clearDbUpload2.add(eventOrder.getId());
		GigaUtils.unplanOrders(eventOrder);
		GigaUtils.clearDriverLoadStatus(eventOrder);
		GigaUtils.changeDbStatus(eventOrder);
		GigaUtils.evictOrder(eventOrder);
	    }
	    GigaDBUpload.changeOrderStatus(clearDbUpload2, conn);
	    GigaDBUpload.clearStateEntries(clearDbUpload2, conn);
	    UnplanOrders(currToMaxActivity, conn);
	    setTruckToMaintenance(truckId, new ArrayList<Activity>());
	    if (currAct != null) {
		List<Activity> orderedActivity = new ArrayList<Activity>();
		if (currToMaxActivity.contains(currAct))
		    ;
		else {
		    orderedActivity.add(currAct);
		    orderedActivity.addAll(currToMaxActivity);
		    currToMaxActivity.clear();
		    currToMaxActivity.addAll(orderedActivity);
		}
	    }
	    planOrders(currToMaxActivity, GigaConstants.SCRIPT_PLANORDERCR);
	}
	logger.info("Before changeStatus");
	GigaDBUpload.changeStatus(conn, "", truckId, "truckLeaveEvent");
	logger.info("After changeStatus");
    }

    public static void planOrders(List<Activity> orderList, String script) {
	List<Order> unplannedOrders = new ArrayList<Order>();
	if (script.equalsIgnoreCase(GigaConstants.SCRIPT_PLANMAINTENANCEORDERCR)) {
	    logger.info("SCRIPT_PLANMAINTENANCEORDERCR");
	    for (int j = 0; j < orderList.size(); j++) {
		logger.info("==resume orders ids==" + orderList.get(j).getOrderNetId());
		Order currOrder = RSPContainerHelper.getOrderMap(true).getEntryBy(orderList.get(j).getOrderNetId());
		((StringParameter) currOrder.getParameterBy(GigaConstants.SCRIPT)).setValue(GigaConstants.SCRIPT_PLANMAINTENANCEORDERCR);
		try {
		    GigaUtils.planOrder(currOrder);
		} catch (Exception e) {
		} finally {
		    ((StringParameter) currOrder.getParameterBy(GigaConstants.SCRIPT)).setValue(GigaConstants.SCRIPT_PLANORDERCR);
		}
		((StringParameter) currOrder.getParameterBy(GigaConstants.SCRIPT)).setValue(GigaConstants.SCRIPT_PLANORDERCR);
		if (currOrder.getState().toString().equalsIgnoreCase(GigaConstants.PLANNED))
		    ;
		else
		    unplannedOrders.add(currOrder);
	    }
	} else if (script.equalsIgnoreCase(GigaConstants.SCRIPT_PLANORDERCR)) {
	    logger.info("SCRIPT_PLANORDERCR");
	    for (int j = 0; j < orderList.size(); j++) {
		Order currOrder = RSPContainerHelper.getOrderMap(true).getEntryBy(orderList.get(j).getOrderNetId());
		{
		    try {
			GigaUtils.planOrder(currOrder);
		    } catch (Exception e) {
			e.printStackTrace();
		    }
		}
	    }
	}
	for (int j = 0; j < unplannedOrders.size(); j++) {
	    try {
		((StringParameter) unplannedOrders.get(j).getParameterBy(GigaConstants.SCRIPT)).setValue(GigaConstants.SCRIPT_PLANORDERCR);
		GigaUtils.planOrder(unplannedOrders.get(j));
	    } catch (Exception e) {
		e.printStackTrace();
	    }
	}
    }

    public static void UnplanOrders(List<Activity> prevTasks, Connection conn) {
	List<String> clearDbUpload = new ArrayList<String>();
	for (int i = 0; i < prevTasks.size(); i++) {
	    Order existOrder = RSPContainerHelper.getOrderMap(true).getEntryBy(prevTasks.get(i).getOrderNetId());
	    clearDbUpload.add(existOrder.getId());
	    GigaUtils.unplanOrders(existOrder);
	    GigaUtils.clearDriverLoadStatus(existOrder);
	    GigaUtils.changeDbStatus(existOrder);
	    GigaUtils.evictOrder(existOrder);
	}
	GigaDBUpload.clearStateEntries(clearDbUpload, conn);
	if (clearDbUpload.size() > 0)
	    GigaDBUpload.changeOrderStatus(clearDbUpload, conn);
    }

    public static void setTruckToMaintenance(String truckId, List<Activity> currActivity) {
	if (RSPContainerHelper.getOrderMap(true).getEntryBy(truckId + GigaConstants.MAINTENANCE_ORDER_SUFFIX) != null) {
	    Order truckMaintenaceOrder = RSPContainerHelper.getOrderMap(true).getEntryBy(truckId + GigaConstants.MAINTENANCE_ORDER_SUFFIX);
	    if (truckMaintenaceOrder.getState().equals(GigaConstants.PLANNED))
		;
	    else {
		try {
		    GigaUtils.planOrder(truckMaintenaceOrder);
		} catch (Exception e) {
		}
	    }
	} else {
	    Order maintenanceOrder = null;
	    DateParameter startTimeParameter = null;
	    if (currActivity.size() > 0) {
		maintenanceOrder = new Order(truckId + GigaConstants.MAINTENANCE_ORDER_SUFFIX, truckId + GigaConstants.MAINTENANCE_ORDER_SUFFIX, GigaConstants.ORD_TYPE_MAINTENANCE, currActivity.get(0).getEnd());
		startTimeParameter = new DateParameter(GigaConstants.ORDER_STARTTIME, GigaConstants.ORDER_STARTTIME, currActivity.get(0).getEnd());
	    } else {
		maintenanceOrder = new Order(truckId + GigaConstants.MAINTENANCE_ORDER_SUFFIX, truckId + GigaConstants.MAINTENANCE_ORDER_SUFFIX, GigaConstants.ORD_TYPE_MAINTENANCE, System.currentTimeMillis());
		startTimeParameter = new DateParameter(GigaConstants.ORDER_STARTTIME, GigaConstants.ORDER_STARTTIME, System.currentTimeMillis());
	    }
	    StringParameter orderDetailsParameter = new StringParameter(GigaConstants.ORD_PARAM_ORDER_DETAILS, GigaConstants.ORD_PARAM_ORDER_DETAILS, "");
	    long duration = 24 * 60 * 60 * 1000;
	    DurationParameter maintDurationParameter = new DurationParameter(GigaConstants.ORDER_MAINDURATION, GigaConstants.ORDER_DURATION, duration);
	    StringParameter maintenanceTypeParmeter = new StringParameter(GigaConstants.MNT_ORD_PARAM_MAINTENANCE_TYPE, GigaConstants.MNT_ORD_PARAM_MAINTENANCE_TYPE, "");
	    DateParameter endTimeParameter = new DateParameter(GigaConstants.ORDER_ENDTIME, GigaConstants.ORDER_ENDTIME, Long.MAX_VALUE);
	    StringParameter truckNoParameter = new StringParameter(GigaConstants.MNT_ORD_PARAM_TRUCK_NO, GigaConstants.MNT_ORD_PARAM_TRUCK_NO, truckId);
	    StringParameter scriptParameter = new StringParameter(GigaConstants.SCRIPT, GigaConstants.SCRIPT, GigaConstants.SCRIPT_PLANMAINTENANCEORDERS);
	    maintenanceOrder.addParameter(startTimeParameter);
	    maintenanceOrder.addParameter(orderDetailsParameter);
	    maintenanceOrder.addParameter(maintDurationParameter);
	    maintenanceOrder.addParameter(maintenanceTypeParmeter);
	    maintenanceOrder.addParameter(endTimeParameter);
	    maintenanceOrder.addParameter(truckNoParameter);
	    maintenanceOrder.addParameter(scriptParameter);
	    /*
	     * AddOrderCommand aoc = new AddOrderCommand(); aoc.setOrder(maintenanceOrder); aoc.doCommand(); */
	    try {
		GigaUtils.addOrder(maintenanceOrder);
		GigaUtils.planOrder(maintenanceOrder);
	    } catch (Exception e) {
	    }
	}
    }
}