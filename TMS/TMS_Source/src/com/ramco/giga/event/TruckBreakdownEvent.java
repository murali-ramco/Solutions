package com.ramco.giga.event;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;

import com.rsp.core.base.FeatherliteAgent;
import com.rsp.core.base.RSPContainerHelper;
import com.rsp.core.base.model.Activity;
import com.rsp.core.base.model.Event;
import com.rsp.core.base.model.Order;
import com.rsp.core.base.model.Resource;
import com.rsp.core.base.model.Workflow;
import com.rsp.core.base.model.parameter.StringParameter;
import com.rsp.core.base.transaction.RSPTransaction;
import com.rsp.core.control.command.EvictOrderToCreatedCommand;
import com.rsp.core.planning.command.ClearWorkflowCommand;

public class TruckBreakdownEvent {
    private final static Logger logger = Logger.getLogger(TruckBreakdownEvent.class);
    private final String TRUE = "true";
    private final String FALSE = "false";

    @SuppressWarnings("null")
    public String doService(com.rsp.core.planning.service.EventService.Argument argument) throws SQLException, Exception {
	String result = FALSE;
	try {
	    Event event = argument.event;
	    if (event == null)
		return "The argument event is null.";
	    RSPTransaction tx = FeatherliteAgent.getTransaction(this);
	    String newOid = event.getId();
	    if (event.getParameterBy("truckId") == null)
		return "Parameter with id truckId is not found in event.";
	    if (event.getParameterBy("Location") == null)
		return "Parameter with id Location is not found in event.";
	    String truckId = event.getParameterBy("truckId").getValueAsString();
	    logger.info("truckId--> " + truckId);
	    String breakdownLocation = event.getParameterBy("Location").getValueAsString();
	    logger.info("Location--> " + breakdownLocation);
	    long breakdownTime = event.getDate();
	    logger.info("breakdownTime: " + new Date(breakdownTime));
	    Resource truck = RSPContainerHelper.getResourceMap(true).getEntryBy(truckId);
	    List<Activity> List = truck.getActivitiesAt(breakdownTime);
	    List<Order> AllOrdersList = new ArrayList<Order>();
	    List<Order> PendingOrdersList = new ArrayList<Order>();
	    Collection<Activity> ActivityMap = truck.getActivityMap().values();
	    Order currentOrder = RSPContainerHelper.getOrderMap(true).getEntryBy(List.get(0).getOrderNetId());
	    StringParameter PickupLocation = (StringParameter) currentOrder.getParameterBy("pickupLocation");
	    PickupLocation.setValue(breakdownLocation);
	    for (Activity orderId : ActivityMap) {
		Order o = RSPContainerHelper.getOrderMap(true).getEntryBy(orderId.getId());
		AllOrdersList.add(o);
	    }
	    logger.info("AllOrdersList " + AllOrdersList);
	    for (Order o : AllOrdersList) {
		long estimatedDeliveryTime = (Long) o.getParameterBy("estDeliveryTime").getValue();
		if (estimatedDeliveryTime > breakdownTime)
		    PendingOrdersList.add(o);
	    }
	    logger.info("PendingOrdersList " + PendingOrdersList);
	    for (Order order : PendingOrdersList) {
		Workflow wf = RSPContainerHelper.getWorkflowMap(true).getEntryBy(order.getId());
		ClearWorkflowCommand uoCom = new ClearWorkflowCommand();
		uoCom.setWorkflow(wf);
		EvictOrderToCreatedCommand evictOrderCommand = new EvictOrderToCreatedCommand();
		evictOrderCommand.setOrder(order);
		tx.addCommand(evictOrderCommand);
		tx.addCommand(uoCom);
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
		StringParameter truck_id = (StringParameter) order.getParameterBy("truckId");
		truck_id.setValue("?");
		StringParameter driver_id = (StringParameter) order.getParameterBy("driverId");
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
		StringParameter log = (StringParameter) order.getParameterBy("remarks");
		log.setValue("breakdown");
		StringParameter evaluatedTrucks = (StringParameter) order.getParameterBy("evaluatedTrucks");
		evaluatedTrucks.setValue("?");
		RSPContainerHelper.getResourceMap(true).removeEntry(truck);
	    }
	} catch (Exception e) {
	    e.printStackTrace();
	    logger.info("exception");
	    result = e.getMessage();
	}
	return result;
    }
}
