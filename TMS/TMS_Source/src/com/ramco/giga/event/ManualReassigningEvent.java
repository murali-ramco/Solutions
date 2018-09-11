package com.ramco.giga.event;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;

import com.ramco.giga.constant.GigaConstants;
import com.ramco.giga.dbupload.GigaDBUpload;
import com.ramco.giga.utils.GigaUtils;
import com.rsp.core.base.FeatherliteAgent;
import com.rsp.core.base.RSPContainerHelper;
import com.rsp.core.base.encrypt.PasswordEncrypter;
import com.rsp.core.base.exception.RSPException;
import com.rsp.core.base.model.Activity;
import com.rsp.core.base.model.ElementMap;
import com.rsp.core.base.model.Event;
import com.rsp.core.base.model.Order;
import com.rsp.core.base.model.Resource;
import com.rsp.core.base.model.Workflow;
import com.rsp.core.base.model.constants.PlanningState;
import com.rsp.core.base.model.parameter.FloatListParameter;
import com.rsp.core.base.model.parameter.StringParameter;
import com.rsp.core.base.query.comparator.ActivityByEndComparator;
import com.rsp.core.base.transaction.RSPTransaction;
import com.rsp.core.i18n.RSPMessages;
import com.rsp.core.planning.command.PlanOrderCommand;
import com.rsp.core.planning.service.EventService;

public class ManualReassigningEvent {
    private final static Logger logger = Logger.getLogger(ManualReassigningEvent.class);
    private final String TRUE = "true";
    private final String FALSE = "false";

    public String doService(EventService.Argument argument) throws SQLException, Exception {
	String result = FALSE;
	Connection conn = null;
	try {
	    Event event = argument.event;
	    if (event == null)
		return "The argument event is null.";
	    RSPTransaction tx = FeatherliteAgent.getTransaction(this);
	    if (event.getParameterBy("orderId") == null)
		return "Parameter with id orderId is not found in event.";
	    if (event.getParameterBy("truckId") == null)
		return "Parameter with id truckId is not found in event.";
	    if (event.getParameterBy("driverId") == null)
		return "Parameter with id driverId is not found in event.";
	    String orderId = event.getParameterBy("orderId").getValueAsString();
	    logger.info("orderId---> " + orderId);
	    String truckId = event.getParameterBy("truckId").getValueAsString();
	    logger.info("truckId---> " + truckId);
	    String driverId = event.getParameterBy("driverId").getValueAsString();
	    logger.info("driverId---> " + driverId);
	    //get the order
	    Class.forName(argument.jdbcDriver);
	    conn = DriverManager.getConnection(argument.jdbcUrl, argument.jdbcUserId, PasswordEncrypter.decrypt(argument.jdbcPassword));
	    conn.setAutoCommit(false);
	    Order order = RSPContainerHelper.getOrderMap(true).getEntryBy(orderId);
	    if (driverId.toLowerCase().contains("unplanned")) {
		try {
		    List<String> orderList = new ArrayList<String>();
		    orderList.add(order.getId());
		    GigaDBUpload.clearStateEntries(orderList, conn);
		    GigaUtils.clearDriverLoadStatus(order);
		    GigaUtils.unplanOrders(order);
		    GigaUtils.evictOrder(order);
		    GigaDBUpload.changeOrderStatus(orderList, conn);
		    conn.commit();
		    return TRUE;
		} catch (Exception e) {
		    e.printStackTrace();
		    logger.info("Exception in ManualReassignment" + e.getMessage());
		}
	    } else {
		if (order == null)
		    return "Order with id " + orderId + " is not found.";
		String pickup = order.getParameterBy("pickupLocation").getValueAsString();
		logger.info("pickup: " + pickup);
		String delivery = order.getParameterBy("deliveryLocation").getValueAsString();
		logger.info("delivery: " + delivery);
		StringParameter remarks = (StringParameter) order.getParameterBy("remarks");
		ElementMap<Resource> resourceMap = RSPContainerHelper.getResourceMap(true);
		Resource pickupLocation = (Resource) resourceMap.getEntryBy(pickup);
		//logger.info("pickupLocation is not null");
		if (pickupLocation == null) {
		    remarks.setValue("Location with id " + pickup + " is not available in Routes");
		    return "Location with id " + pickup + " is not available in Routes";
		}
		FloatListParameter deliveryLoc = (FloatListParameter) pickupLocation.getParameterBy(delivery);
		if (deliveryLoc == null) {
		    remarks.setValue("Parameter with id " + delivery + " is not found in  Location with id " + pickup);
		    return "Parameter with id " + delivery + " is not found in  Location with id " + pickup;
		}
		StringParameter truck_id = (StringParameter) order.getParameterBy("truckId");
		StringParameter driver_id = (StringParameter) order.getParameterBy("driverId");
		StringParameter log = (StringParameter) order.getParameterBy("remarks");
		Collection<Order> MaintenanceOrders = RSPContainerHelper.getOrderMap(true).getByType("Maintenance");
		logger.info("MaintenanceOrders " + MaintenanceOrders);
		List<String> trucksUnderMaintenance = new ArrayList<String>();
		for (Order o : MaintenanceOrders) {
		    if (o.getState().compareTo(PlanningState.PLANNED) == 0) {
			String truckNo = o.getParameterBy("truckNo").getValueAsString();
			logger.info("truckNO: " + truckNo);
			trucksUnderMaintenance.add(truckNo);
			logger.info("abc");
		    }
		}
		//---Gpi-530 starts
		driverId = driverId.substring(0, driverId.indexOf("/")).trim();
		Resource newTruckRes = resourceMap.getEntryBy(truckId);
		StringParameter newTruckMappedDriverParam = (StringParameter) newTruckRes.getParameterBy(GigaConstants.RES_PARAM_DRIVERID);
		String newTruckMappedDriver = newTruckMappedDriverParam.getValueAsString();
		String mappedTruckGivenDriver = null;
		if (!newTruckMappedDriver.equalsIgnoreCase(driverId)) {
		    if (newTruckMappedDriver.equalsIgnoreCase(GigaConstants.TRUCK_WITHOUT_DRIVER)) {
			Collection<Resource> truckResList = resourceMap.getByType("truck_CR");
			boolean newDriverTruckUnderMnt = false;
			for (Resource truckRes : truckResList) {
			    String mappedDriverId = truckRes.getParameterBy(GigaConstants.RES_PARAM_DRIVERID).getValueAsString();
			    if (mappedDriverId.equalsIgnoreCase(driverId)) {
				if (trucksUnderMaintenance.contains(truckRes.getId())) {
				    newTruckMappedDriverParam.setValue(driverId);
				    ((StringParameter) truckRes.getParameterBy(GigaConstants.RES_PARAM_DRIVERID)).setValue(GigaConstants.TRUCK_WITHOUT_DRIVER);
				    newDriverTruckUnderMnt = true;
				    break;
				} else {
				    mappedTruckGivenDriver = truckRes.getId();
				    break;
				}
			    }
			}
			if (!newDriverTruckUnderMnt) {
			    throw RSPMessages.newRspMsgEx("The driver " + driverId + " is mapped with another truck " + truckId + " which is not in under maintenance.");
			}
		    } else {
			throw RSPMessages.newRspMsgEx("The truck " + truckId + " is already mapped with the driver id " + newTruckMappedDriver + ".");
		    }
		}
		//---Gpi-530 ends
		ElementMap<Order> orderMap = RSPContainerHelper.getOrderMap(true);
		Workflow wf = RSPContainerHelper.getWorkflowMap(true).getEntryBy(orderId);
		List<Activity> newTruckActivityList = resourceMap.getEntryBy(truckId).getActivitiesInInterval(System.currentTimeMillis(), java.lang.Long.MAX_VALUE);
		Collections.sort(newTruckActivityList, new ActivityByEndComparator());
		List<String> newTruckOrderList = new ArrayList<String>();
		for (Activity activity : newTruckActivityList) {
		    long activityStart = activity.getStart();
		    if (activityStart > System.currentTimeMillis()) {
			Order newTruckOrder = orderMap.getEntryBy(activity.getOrderNetId());
			if (newTruckOrder.hasParameter(GigaConstants.ORDER_PARAM_TRUCK_ID) && newTruckOrder.hasParameter(GigaConstants.ORDER_PARAM_DRIVER_ID))
			    GigaUtils.addPreviousTruckDriver(newTruckOrder, newTruckOrder.getParameterBy(GigaConstants.ORDER_PARAM_TRUCK_ID).getValueAsString(), newTruckOrder.getParameterBy(GigaConstants.ORDER_PARAM_DRIVER_ID).getValueAsString());
			GigaUtils.clearDriverLoadStatus(newTruckOrder);
			GigaUtils.unplanOrders(newTruckOrder);
			GigaUtils.evictOrder(newTruckOrder);
			newTruckOrderList.add(newTruckOrder.getId());
		    }
		}
		if (newTruckOrderList.size() > 0)
		    GigaDBUpload.clearStateEntries(newTruckOrderList, conn);
		if (wf == null) {
		    logger.info("Workflow with id " + orderId + " is not planned.");
		    StringParameter script = (StringParameter) order.getParameterBy("script");
		    script.setValue("ReassignManually");
		    log.setValue("");
		    StringParameter evaluatedTrucks = (StringParameter) order.getParameterBy("evaluatedTrucks");
		    evaluatedTrucks.setValue(truckId);
		    /*if (order.getParameterBy("BD").getValueAsString().equalsIgnoreCase("CR") && !orderMake.equalsIgnoreCase("others") && !truckMake.equalsIgnoreCase(orderMake)) {
		    remarks.setValue("Order " + orderId + " requires a truck with makeId " + orderMake + " but the makeId of the truck is " + truckMake + ". Choose a truck with makeID: " + orderMake);
		    logger.info("makeProblm");
		    throw RSPMessages.newRspMsgEx("Order " + orderId + " requires a truck with makeId " + orderMake + " but the makeId of the truck is: " + truckMake + ". Choose a truck with makeID " + orderMake);
		    }
		    if (orderedTruckType.equalsIgnoreCase("articulated") && (truckType.equalsIgnoreCase("rigid") || truckType.equalsIgnoreCase("single"))) {
		    remarks.setValue("Truck type for Order " + orderId + " is " + orderedTruckType + " but truck " + truck.getId() + " is of truckType: " + truckType + ". Choose an articulated truck.");
		    logger.info("sizeProblem");
		    throw RSPMessages.newRspMsgEx("Truck type for Order " + orderId + " is " + orderedTruckType + " but truck " + truck.getId() + " is of truckType: " + truckType + ". Choose an articulated truck.");
		    }
		    if (orderedTruckType.equalsIgnoreCase("rigid") && truckType.equalsIgnoreCase("single")) {
		    remarks.setValue("Truck type for Order " + orderId + " is " + orderedTruckType + " but truck " + truck.getId() + " is of truckType: " + truckType + ". Choose either a rigid truck or an articulated truck.");
		    logger.info("sizeProblem");
		    throw RSPMessages.newRspMsgEx("Truck type for Order " + orderId + " is " + orderedTruckType + " but truck " + truck.getId() + " is of truckType: " + truckType + ". Choose either a rigid truck or an articulated truck.");
		    }*/
		    if (trucksUnderMaintenance.contains(truckId)) {
			remarks.setValue("truck " + truckId + " is under maintenance. Kindly assign another truck.");
			logger.info("maintenance");
			throw RSPMessages.newRspMsgEx("truck " + truckId + " is under maintenance. Kindly assign another truck.");
		    }
		    StringParameter remarksLog = new StringParameter("remarksLog", "planningLog", "");
		    order.addParameter(remarksLog);
		    PlanOrderCommand poc = new PlanOrderCommand();
		    poc.setOrder(order);
		    tx.addCommand(poc);
		    try {
			tx.commit();
			result = TRUE;
		    } catch (RSPException e) {
			e.printStackTrace();
			result = e.getMessage();
		    }
		    logger.info("order planned");
		} else {
		    logger.info("workflow present");
		    /*if (order.getParameterBy("BD").getValueAsString().equalsIgnoreCase("CR") && !orderMake.equalsIgnoreCase("others") && !truckMake.equalsIgnoreCase(orderMake)) {
		    remarks.setValue("Order " + orderId + " requires a truck with makeId " + orderMake + " but the makeId of the truck is " + truckMake + ". Choose a truck with makeID: " + orderMake);
		    logger.info("makeProblm");
		    throw RSPMessages.newRspMsgEx("Order " + orderId + " requires a truck with makeId " + orderMake + " but the makeId of the truck is: " + truckMake + ". Choose a truck with makeID " + orderMake);
		    }
		    if (orderedTruckType.equalsIgnoreCase("articulated") && (truckType.equalsIgnoreCase("rigid") || truckType.equalsIgnoreCase("single"))) {
		    remarks.setValue("Truck type for Order " + orderId + " is " + orderedTruckType + " but truck " + truck.getId() + " is of truckType: " + truckType + ". Choose an articulated truck.");
		    logger.info("sizeProblem");
		    throw RSPMessages.newRspMsgEx("Truck type for Order " + orderId + " is " + orderedTruckType + " but truck " + truck.getId() + " is of truckType: " + truckType + ". Choose an articulated truck.");
		    }
		    if (orderedTruckType.equalsIgnoreCase("rigid") && truckType.equalsIgnoreCase("single")) {
		    remarks.setValue("Truck type for Order " + orderId + " is " + orderedTruckType + " but truck " + truck.getId() + " is of truckType: " + truckType + ". Choose either a rigid truck or an articulated truck.");
		    logger.info("sizeProblem");
		    throw RSPMessages.newRspMsgEx("Truck type for Order " + orderId + " is " + orderedTruckType + " but truck " + truck.getId() + " is of truckType: " + truckType + ". Choose either a rigid truck or an articulated truck.");
		    }*/
		    if (trucksUnderMaintenance.contains(truckId)) {
			remarks.setValue("truck " + truckId + " is under maintenance. Kindly assign another truck.");
			logger.info("maintenance");
			throw RSPMessages.newRspMsgEx("truck " + truckId + " is under maintenance. Kindly assign another truck.");
		    } else {
			if (order.hasParameter(GigaConstants.ORDER_PARAM_TRUCK_ID) && order.hasParameter(GigaConstants.ORDER_PARAM_DRIVER_ID))
			    GigaUtils.addPreviousTruckDriver(order, order.getParameterBy(GigaConstants.ORDER_PARAM_TRUCK_ID).getValueAsString(), order.getParameterBy(GigaConstants.ORDER_PARAM_DRIVER_ID).getValueAsString());
			GigaUtils.clearDriverLoadStatus(order);
			GigaUtils.unplanOrders(order);
			List<String> orderList = new ArrayList<String>();
			orderList.add(order.getId());
			GigaDBUpload.clearStateEntries(orderList, conn);
			StringParameter preceding_DM = (StringParameter) order.getParameterBy("preceding_DM");
			preceding_DM.setValue("?");
			StringParameter succeeding_DM = (StringParameter) order.getParameterBy("succeeding_DM");
			succeeding_DM.setValue("?");
			StringParameter travel_duration = (StringParameter) order.getParameterBy("travel_Duration");
			travel_duration.setValue("?");
			StringParameter loading_unloading_timebuffer = (StringParameter) order.getParameterBy("loading_unloading_timeBuffer");
			loading_unloading_timebuffer.setValue("?");
			StringParameter rest_Waiting_timebuffer = (StringParameter) order.getParameterBy("rest_Waiting_timeBuffer");
			rest_Waiting_timebuffer.setValue("?");
			StringParameter Base_location_StartTime = (StringParameter) order.getParameterBy("base_location_StartTime");
			Base_location_StartTime.setValue("?");
			truck_id.setValue("?");
			driver_id.setValue("?");
			StringParameter evaluatedODM_Km = (StringParameter) order.getParameterBy("evaluatedODM_Km");
			evaluatedODM_Km.setValue("?");
			StringParameter evaluatedODM_Hrs = (StringParameter) order.getParameterBy("evaluatedODM_Hrs");
			evaluatedODM_Hrs.setValue("?");
			StringParameter estPickupTime = (StringParameter) order.getParameterBy("estPickupTime");
			estPickupTime.setValue("?");
			StringParameter estDeliveryTime = (StringParameter) order.getParameterBy("estDeliveryTime");
			estDeliveryTime.setValue("?");
			StringParameter est_TraveltimeinMins = (StringParameter) order.getParameterBy("estTravelTime");
			est_TraveltimeinMins.setValue("?");
			StringParameter evalTravelDistance = (StringParameter) order.getParameterBy("estimatedTravelDistance");
			evalTravelDistance.setValue("?");
			StringParameter evalTravelDuration = (StringParameter) order.getParameterBy("estimatedTravelDuration");
			evalTravelDuration.setValue("?");
			log.setValue("?");
			StringParameter script = (StringParameter) order.getParameterBy("script");
			script.setValue("ReassignManually");
			StringParameter evaluatedTrucks = (StringParameter) order.getParameterBy("evaluatedTrucks");
			evaluatedTrucks.setValue(truckId);
			StringParameter remarksLog = new StringParameter("remarksLog", "planningLog", "");
			order.addParameter(remarksLog);
			GigaUtils.planOrder(order);
		    }
		}
		for (String orderID : newTruckOrderList) {
		    Order orderToPlan = orderMap.getEntryBy(orderID);
		    StringParameter remarksLog = new StringParameter("remarksLog", "planningLog", "");
		    orderToPlan.addParameter(remarksLog);
		    GigaUtils.planOrder(orderToPlan);
		}
		newTruckOrderList.add(orderId);
		boolean failure = false;
		for (String orderIdToPlan : newTruckOrderList) {
		    Order orderToUpdate = orderMap.getEntryBy(orderIdToPlan);
		    if (orderToUpdate.getState().equals(PlanningState.PLANNED)) {
			GigaUtils.changeDbStatus(orderToUpdate);
			List<String> orderList = new ArrayList<String>();
			orderList.add(orderToUpdate.getId());
			GigaDBUpload.changeOrderStatus(orderList, conn);
			logger.info("Completed successfully2...");
			GigaDBUpload.insertPlannedOrdersIntoDB(conn);
			logger.info("Completed successfully3...");
			result = TRUE;
		    }
		}
		if (order.getState().equals(PlanningState.CREATED)) {
		    logger.info("==Inside created...");
		    List<String> orderList = new ArrayList<String>();
		    orderList.add(order.getId());
		    GigaDBUpload.changeOrderStatus(orderList, conn);
		    String planning_log = log.getValueAsString();
		    planning_log = order.getParameterBy("remarksLog").getValueAsString();
		    logger.info("==planning_log==>" + planning_log);
		    if (planning_log.contains(truckId)) {
			planning_log = planning_log.substring(planning_log.indexOf(truckId), planning_log.length());
			int finalIndex = planning_log.indexOf("~");
			if (finalIndex < 0)
			    finalIndex = planning_log.length();
			planning_log = planning_log.substring(0, finalIndex);
			result = planning_log;
			throw RSPMessages.newRspMsgEx("Truck " + truckId + " is not feasible to fullfill this load " + order.getId() + ". Reason: " + planning_log);
		    } else {
			throw RSPMessages.newRspMsgEx("Truck " + truckId + " is not feasible to fullfill this load " + order.getId() + ".");
		    }
		}
		logger.info("Completed successfully1...");
		return TRUE;
	    }
	} catch (Exception e) {
	    e.printStackTrace();
	    logger.info("exception" + e.getMessage());
	    result = e.getMessage();
	}
	return result;
    }
}
