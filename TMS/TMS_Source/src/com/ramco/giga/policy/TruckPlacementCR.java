package com.ramco.giga.policy;

import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.ramco.giga.constant.GigaConstants;
import com.ramco.giga.formula.FindDistanceDuration;
import com.ramco.giga.utils.GigaUtils;
import com.rsp.core.base.RSPContainerHelper;
import com.rsp.core.base.exception.DataModelException;
import com.rsp.core.base.model.Activity;
import com.rsp.core.base.model.ElementMap;
import com.rsp.core.base.model.IPolicyContainer;
import com.rsp.core.base.model.Objectives;
import com.rsp.core.base.model.Order;
import com.rsp.core.base.model.ParameterizedElement;
import com.rsp.core.base.model.Resource;
import com.rsp.core.base.model.Task;
import com.rsp.core.base.model.TimeInterval;
import com.rsp.core.base.model.WorkCalendar;
import com.rsp.core.base.model.parameter.BooleanMapParameter;
import com.rsp.core.base.model.parameter.BooleanParameter;
import com.rsp.core.base.model.parameter.DateParameter;
import com.rsp.core.base.model.parameter.FloatListParameter;
import com.rsp.core.base.model.parameter.FloatParameter;
import com.rsp.core.base.model.parameter.IntegerParameter;
import com.rsp.core.base.model.parameter.StringParameter;
import com.rsp.core.base.model.stateVariable.FloatState;
import com.rsp.core.base.model.stateVariable.StateValue;
import com.rsp.core.base.model.stateVariable.StringState;
import com.rsp.core.base.query.comparator.ActivityByStartComparator;
import com.rsp.core.helper.ISO8601FormatFactory;
import com.rsp.core.i18n.RSPMessages;
import com.rsp.core.planning.policy.CalendarPolicy;
import com.rsp.core.planning.policy.PlacementResult;
import com.rsp.core.planning.policy.placement.CapacityPlacement;
import com.rsp.core.planning.policy.placement.DefaultPlacementResult;

public class TruckPlacementCR extends CapacityPlacement {
    private static final long serialVersionUID = 1L;
    private static String CAPACITYSTATE_KEY = "Capacity";
    private static String LOCATION_KEY = "Location";
    private static String QUANTITY_KEY = "quantity";
    private static String DURATION_KEY = "duration";
    protected FloatState Capacity;
    protected StringState Location;
    protected double quantity;
    protected long duration;
    protected String locationVal;
    protected Double capacityLowerBound;
    protected Double capacityUpperBound;

    public void evaluateObjectives(Task task) {
	try {
	    this.quantity = getFloatObjective(task, QUANTITY_KEY);
	    this.duration = getDurationObjective(task, DURATION_KEY);
	} catch (DataModelException e) {
	    DataModelException dme = new DataModelException("Error in placement policy $policyName while reading objectives of task $taskId", "datamodel.objectivesRead", e);
	    dme.addProperty("policyName", getName());
	    dme.addProperty("taskId", task == null ? "null" : task.getId());
	    throw dme;
	}
    }

    public void evaluateBounds(Resource resource) {
	this.capacityLowerBound = ((Double) getFloatState(resource, CAPACITYSTATE_KEY).getLowerBound());
	this.capacityUpperBound = ((Double) getFloatState(resource, CAPACITYSTATE_KEY).getUpperBound());
    }

    public PlacementResult place(Task task, Resource resource, TimeInterval horizon, CalendarPolicy calendarPolicy, WorkCalendar workCalendar) {
	String direction = getStringObjective(task, "direction");
	return place(task, resource, horizon, direction, calendarPolicy, workCalendar);
    }

    public PlacementResult place(Task task, Resource resource, TimeInterval horizon, String direction, CalendarPolicy calendarPolicy, WorkCalendar workCalendar) {
	evaluateObjectives(task);
	evaluateBounds(resource);
	PlacementResult placementResult = null;
	long start;
	long end;
	ParameterizedElement tmp = task;
	String orderNetId = null;
	while (tmp != null) {
	    orderNetId = tmp.getId();
	    tmp = tmp.getParent();
	}
	logger.debug("orderNetId: " + orderNetId);
	Order order = RSPContainerHelper.getOrderMap(true).getEntryBy(orderNetId);
	StringParameter orderDetailsP = new StringParameter("orderDetails", "orderDetails", "");
	order.addParameter(orderDetailsP);
	StringParameter remarks1 = (StringParameter) order.getParameterBy("remarksLog");
	StringParameter remarks = (StringParameter) order.getParameterBy("remarks");
	SimpleDateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
	Collection<Resource> customerLocationMapList = RSPContainerHelper.getResourceMap(true).getByType("Customer_Location_Mapping");
	String customer_pickupLocId = null;
	String customer_deliveryLocId = null;
	double currLat = 0;
	double currLong = 0;
	Resource giga_parameters = RSPContainerHelper.getResourceMap(true).getEntryBy("giga_parameters");
	Resource currLatLong = RSPContainerHelper.getResourceMap(true).getEntryBy("truckLatitudeLongitude");
	Double durationCalculator = ((FloatParameter) giga_parameters.getParameterBy("durationCalculation")).getValue();
	String truckType = resource.getParameterBy("truckType").getValueAsString();
	double maxWorkHrsValue = (Double) giga_parameters.getParameterBy("maxWorkHrs").getValue();
	long maxWorkHrs = (long) (maxWorkHrsValue * 3.6e6);
	double loadingBufferValue = (Double) giga_parameters.getParameterBy("loadingBuffer").getValue();
	long loadingBuffer = (long) (loadingBufferValue * 60000L);
	double unloadingBufferValue = (Double) giga_parameters.getParameterBy("unloadingBuffer").getValue();
	long unloadingBuffer = (long) (unloadingBufferValue * 60000L);
	double restHrs4value = (Double) giga_parameters.getParameterBy("restHrs4").getValue();
	long restHrs4 = (long) (restHrs4value * 60000L);
	double restHrs8value = (Double) giga_parameters.getParameterBy("restHrs8").getValue();
	long restHrs8 = (long) (restHrs8value * 60000L);
	double restHrs111value = (Double) giga_parameters.getParameterBy("restHrs111").getValue();
	long restHrs111 = (long) (restHrs111value * 60000L);
	double restHrs211value = (Double) giga_parameters.getParameterBy("restHrs211").getValue();
	long restHrs211 = (long) (restHrs211value * 60000L);
	long restDuration = 0;
	boolean isDayOSPossible = true;
	Long endTimeBuffer = (((FloatParameter) RSPContainerHelper.getResourceMap(true).getEntryBy("giga_parameters").getParameterBy("deliveryTimeBuffer")).getValue()).longValue() * 60 * 1000;
	if ((order.getType().equalsIgnoreCase("Maintenance")) || (order.getType().equalsIgnoreCase("PlannedLeave"))) {
	    start = horizon.getLower();
	    placementResult = new DefaultPlacementResult(PlacementResult.SUCCESS);
	    placementResult.setStart(start);
	    placementResult.setEnd(start + this.duration);
	    placementResult.setQuantity(this.quantity);
	    placementResult.setResource(resource);
	    return placementResult;
	} else {
	    String customerID = order.getParameterBy("customerID").getValueAsString();
	    String orderedTruckType = order.getParameterBy("truckType").getValueAsString();
	    logger.debug("orderedTruckType->" + orderedTruckType);
	    String customerType = order.getParameterBy("orderType").getValueAsString();
	    String pickupLocation = order.getParameterBy("pickupLocation").getValueAsString();
	    logger.debug("pickupLocation" + pickupLocation);
	    String deliveryLocation = order.getParameterBy("deliveryLocation").getValueAsString();
	    logger.debug("deliveryLocation" + deliveryLocation);
	    double noOfDrops = (Double) order.getParameterBy("noOfDrops").getValue();
	    long pickupTime = (Long) order.getParameterBy("pickupDate").getValue();
	    logger.debug("pickupOrdertime: " + new Date(pickupTime));
	    long deliveryTime = (Long) order.getParameterBy("deliveryDate").getValue();
	    if (order.getParameterBy(GigaConstants.ORD_PARAM_JOB_TYPE).getValueAsString().equalsIgnoreCase("outstation")) {
		if (pickupTime != deliveryTime)
		    deliveryTime = deliveryTime - 86400000L;
	    }
	    logger.debug("deliveryOrdertime: " + new Date(deliveryTime));
	    ElementMap<Resource> resourceMap = RSPContainerHelper.getResourceMap(true);
	    Resource customer_pickupLocRes = null;
	    Resource customer_deliveryLocRes = null;
	    String pickupLocPeakStart1 = null;
	    String pickupLocPeakEnd1 = null;
	    String pickupLocPeakStart2 = null;
	    String pickupLocPeakEnd2 = null;
	    String pickupLocPeakStart3 = null;
	    String pickupLocPeakEnd3 = null;
	    String pickupLocPeakStart4 = null;
	    String pickupLocPeakEnd4 = null;
	    String deliveryLocPeakStart1 = null;
	    String deliveryLocPeakEnd1 = null;
	    String deliveryLocPeakStart2 = null;
	    String deliveryLocPeakEnd2 = null;
	    String deliveryLocPeakStart3 = null;
	    String deliveryLocPeakEnd3 = null;
	    String deliveryLocPeakStart4 = null;
	    String deliveryLocPeakEnd4 = null;
	    String logMessage = "";
	    StringBuilder orderDetails = new StringBuilder();
	    orderDetails.append("Load Number: ,");
	    orderDetails.append(order.getId().concat(","));
	    orderDetails.append("~Truck Make: ,");
	    orderDetails.append(order.getParameterBy("make").getValueAsString().concat(","));
	    orderDetails.append("~Load Type: ,");
	    orderDetails.append(order.getParameterBy(GigaConstants.ORD_PARAM_JOB_TYPE).getValueAsString().concat(","));
	    orderDetails.append("~Truck Type: ,");
	    orderDetails.append(order.getParameterBy("truckType").getValueAsString().concat(","));
	    orderDetails.append("~No of drops: ,");
	    orderDetails.append(order.getParameterBy("noOfDrops").getValueAsString().concat(","));
	    long tripDuration = GigaUtils.getDuration(pickupLocation, deliveryLocation);
	    GigaUtils.addParamLocalOS(order, resource.getId(), true);
	    if (order.hasParameter(GigaConstants.TRUCK_EVENT_STATUS)) {
		String eventStatus = order.getParameterBy(GigaConstants.TRUCK_EVENT_STATUS).getValueAsString();
		if (eventStatus.equalsIgnoreCase(GigaConstants.TRUCK_BD_STATUS_SHORT_CLOSE) || eventStatus.equalsIgnoreCase(GigaConstants.TRUCK_BD_STATUS_RESUME)) {
		    if (order.hasParameter(GigaConstants.TRUCK_LOAD_PICKUP_STATUS)) {
			String loadStatus = order.getParameterBy(GigaConstants.TRUCK_LOAD_PICKUP_STATUS).getValueAsString();
			if (loadStatus.equalsIgnoreCase(GigaConstants.TRUCK_LOAD_PICKUP_COMPLETED)) {
			    Resource delLatLongRes = resourceMap.getEntryBy(deliveryLocation);
			    double deliveryLat = (Double) delLatLongRes.getParameterBy("lat").getValue();
			    double deliveryLong = (Double) delLatLongRes.getParameterBy("long").getValue();
			    Resource truckLatLongRes = resourceMap.getEntryBy(GigaConstants.RES_TRUCK_LAT_LONG);
			    String[] truckLatLong = truckLatLongRes.getParameterBy(resource.getId()).getValueAsString().split(",");
			    if (eventStatus.equalsIgnoreCase(GigaConstants.TRUCK_BD_STATUS_SHORT_CLOSE))
				truckLatLong = truckLatLongRes.getParameterBy(order.getParameterBy(GigaConstants.ORDER_PARAM_PREV_TRUCK_ID).getValueAsString()).getValueAsString().split(",");
			    String[] currToPickupDuration = FindDistanceDuration.getDistanceDuration(Double.valueOf(truckLatLong[0]), Double.valueOf(truckLatLong[1]), deliveryLat, deliveryLong).split(",");
			    tripDuration = Long.parseLong(currToPickupDuration[1]);
			}
		    }
		}
	    }
	    if (order.getParameterBy(GigaConstants.ORD_PARAM_BUSINESS_DIVISION).getValueAsString().equalsIgnoreCase(GigaConstants.BUSINESS_DIVISION_KUCHING)) {
		Double truckDrivingFactor = ((FloatParameter) giga_parameters.getParameterBy(GigaConstants.GIGA_PARAM_TRUCK_DRIVING_FACTOR)).getValue();
		tripDuration = (long) (tripDuration / truckDrivingFactor);
	    }
	    if (resourceMap.getEntryBy(pickupLocation).getName().equalsIgnoreCase("EAST_COAST"))
		orderDetails.append("~Pickup Location (EAST_COAST): ,");
	    else
		orderDetails.append("~Pickup Location: ,");
	    orderDetails.append(pickupLocation.concat(","));
	    if (resourceMap.getEntryBy(deliveryLocation).getName().equalsIgnoreCase("EAST_COAST"))
		orderDetails.append("~Delivery Location (EAST_COAST): ,");
	    else
		orderDetails.append("~Delivery Location: ,");
	    orderDetails.append(deliveryLocation.concat(","));
	    orderDetails.append("~Pickup to delivery Duration: ,");
	    orderDetails.append(convertDurationToTime(ISO8601FormatFactory.getInstance().formatDuration(tripDuration).concat(",")));
	    logger.debug("tripDuration" + tripDuration / 3600000);
	    end = deliveryTime;
	    logger.debug("orderEnd : " + new Date(end));
	    if (order.getParameterBy(GigaConstants.ORD_PARAM_BUSINESS_DIVISION).getValueAsString().equalsIgnoreCase(GigaConstants.BUSINESS_DIVISION_EAST_MALAYSIA)) {
		customer_pickupLocRes = RSPContainerHelper.getResourceMap(true).getEntryBy("OTHERS_OTHERS_KKCT");
		customer_pickupLocId = "OTHERS_OTHERS_KKCT";
		customer_deliveryLocId = "OTHERS_OTHERS_KKCT";
		pickupLocPeakStart1 = customer_pickupLocRes.getParameterBy("peakStart1").getValueAsString();
		pickupLocPeakEnd1 = customer_pickupLocRes.getParameterBy("peakEnd1").getValueAsString();
		pickupLocPeakStart2 = customer_pickupLocRes.getParameterBy("peakStart2").getValueAsString();
		pickupLocPeakEnd2 = customer_pickupLocRes.getParameterBy("peakEnd2").getValueAsString();
		pickupLocPeakStart3 = customer_pickupLocRes.getParameterBy("peakStart3").getValueAsString();
		pickupLocPeakEnd3 = customer_pickupLocRes.getParameterBy("peakEnd3").getValueAsString();
		pickupLocPeakStart4 = customer_pickupLocRes.getParameterBy("peakStart4").getValueAsString();
		pickupLocPeakEnd4 = customer_pickupLocRes.getParameterBy("peakEnd4").getValueAsString();
		customer_deliveryLocRes = RSPContainerHelper.getResourceMap(true).getEntryBy("OTHERS_OTHERS_KKCT");
		deliveryLocPeakStart1 = customer_deliveryLocRes.getParameterBy("peakStart1").getValueAsString();
		deliveryLocPeakEnd1 = customer_deliveryLocRes.getParameterBy("peakEnd1").getValueAsString();
		deliveryLocPeakStart2 = customer_deliveryLocRes.getParameterBy("peakStart2").getValueAsString();
		deliveryLocPeakEnd2 = customer_deliveryLocRes.getParameterBy("peakEnd2").getValueAsString();
		deliveryLocPeakStart3 = customer_deliveryLocRes.getParameterBy("peakStart3").getValueAsString();
		deliveryLocPeakEnd3 = customer_deliveryLocRes.getParameterBy("peakEnd3").getValueAsString();
		deliveryLocPeakStart4 = customer_deliveryLocRes.getParameterBy("peakStart4").getValueAsString();
		deliveryLocPeakEnd4 = customer_deliveryLocRes.getParameterBy("peakEnd4").getValueAsString();
	    } else {
		for (Resource r : customerLocationMapList) {
		    if ((customerID + "_" + pickupLocation).equalsIgnoreCase(r.getId()))
			customer_pickupLocId = r.getId();
		    if ((customerID + "_" + deliveryLocation).equalsIgnoreCase(r.getId()))
			customer_deliveryLocId = r.getId();
		}
		if (customer_pickupLocId == null) {
		    for (Resource r : customerLocationMapList) {
			boolean custPickIdMatched = false;
			String custCode = r.getParameterBy("customerCode").getValueAsString();
			String location = r.getParameterBy("location").getValueAsString();
			if (custCode.equalsIgnoreCase(customerID) && !location.equalsIgnoreCase(pickupLocation)) {
			    customer_pickupLocId = custCode + "_OTHERS";
			    custPickIdMatched = true;
			} else if (!custCode.equalsIgnoreCase(customerID))
			    customer_pickupLocId = "OTHERS_OTHERS";
			customer_pickupLocRes = RSPContainerHelper.getResourceMap(true).getEntryBy(customer_pickupLocId);
			if (pickupLocation.equalsIgnoreCase(location)) {
			    pickupLocPeakStart1 = customer_pickupLocRes.getParameterBy("peakStart1").getValueAsString();
			    pickupLocPeakEnd1 = customer_pickupLocRes.getParameterBy("peakEnd1").getValueAsString();
			    pickupLocPeakStart2 = customer_pickupLocRes.getParameterBy("peakStart2").getValueAsString();
			    pickupLocPeakEnd2 = customer_pickupLocRes.getParameterBy("peakEnd2").getValueAsString();
			    pickupLocPeakStart3 = customer_pickupLocRes.getParameterBy("peakStart3").getValueAsString();
			    pickupLocPeakEnd3 = customer_pickupLocRes.getParameterBy("peakEnd3").getValueAsString();
			    pickupLocPeakStart4 = customer_pickupLocRes.getParameterBy("peakStart4").getValueAsString();
			    pickupLocPeakEnd4 = customer_pickupLocRes.getParameterBy("peakEnd4").getValueAsString();
			}
			if (custPickIdMatched == true)
			    break;
		    }
		}
		if (customer_deliveryLocId == null) {
		    for (Resource r : customerLocationMapList) {
			String custCode = r.getParameterBy("customerCode").getValueAsString();
			String location = r.getParameterBy("location").getValueAsString();
			if (custCode.equalsIgnoreCase(customerID) && !location.equalsIgnoreCase(deliveryLocation)) {
			    customer_deliveryLocId = custCode + "_OTHERS";
			} /*else if(!custCode.equalsIgnoreCase(customerID)&&location.equalsIgnoreCase(deliveryLocation)) 
			  		customer_deliveryLocId = "Others_"+location; */
			else if (!custCode.equalsIgnoreCase(customerID))
			    customer_deliveryLocId = "OTHERS_OTHERS";
			customer_deliveryLocRes = RSPContainerHelper.getResourceMap(true).getEntryBy(customer_deliveryLocId);
			if (deliveryLocation.equalsIgnoreCase(location)) {
			    deliveryLocPeakStart1 = customer_deliveryLocRes.getParameterBy("peakStart1").getValueAsString();
			    deliveryLocPeakEnd1 = customer_deliveryLocRes.getParameterBy("peakEnd1").getValueAsString();
			    deliveryLocPeakStart2 = customer_deliveryLocRes.getParameterBy("peakStart2").getValueAsString();
			    deliveryLocPeakEnd2 = customer_deliveryLocRes.getParameterBy("peakEnd2").getValueAsString();
			    deliveryLocPeakStart3 = customer_deliveryLocRes.getParameterBy("peakStart3").getValueAsString();
			    deliveryLocPeakEnd3 = customer_deliveryLocRes.getParameterBy("peakEnd3").getValueAsString();
			    deliveryLocPeakStart4 = customer_deliveryLocRes.getParameterBy("peakStart4").getValueAsString();
			    deliveryLocPeakEnd4 = customer_deliveryLocRes.getParameterBy("peakEnd4").getValueAsString();
			}
		    }
		}
	    }
	    logger.debug("customer_pickupLocId->" + customer_pickupLocId);
	    logger.debug("customer_deliveryLocId->" + customer_deliveryLocId);
	    customer_deliveryLocRes = RSPContainerHelper.getResourceMap(true).getEntryBy(customer_deliveryLocId);
	    logger.debug("latestDeliveryTime:" + customer_deliveryLocRes.getParameterBy("deliveryEndTime").getValueAsString());
	    String latestDeliveryTime = customer_deliveryLocRes.getParameterBy("deliveryEndTime").getValueAsString();
	    boolean disableDeliveryTime = ((BooleanParameter) giga_parameters.getParameterBy(GigaConstants.GIGA_PARAM_DISABLE_DELIVERY_TIME)).getValue();
	    if (disableDeliveryTime) {
		latestDeliveryTime = giga_parameters.getParameterBy(GigaConstants.GIGA_PARAM_DEFAULT_DELIVERY_TIME).getValueAsString();
	    }
	    String earliestDeliveryTime = customer_deliveryLocRes.getParameterBy("deliveryStartTime").getValueAsString();
	    customer_pickupLocRes = RSPContainerHelper.getResourceMap(true).getEntryBy(customer_pickupLocId);
	    logger.debug("earliestPickupTime:" + customer_pickupLocRes.getParameterBy("pickupStartTime").getValueAsString());
	    String earliestPickupTime = customer_pickupLocRes.getParameterBy("pickupStartTime").getValueAsString();
	    String latestPickupTime = customer_pickupLocRes.getParameterBy("pickupEndTime").getValueAsString();
	    boolean disablePickupTime = ((BooleanParameter) giga_parameters.getParameterBy(GigaConstants.GIGA_PARAM_DISABLE_PICKUP_TIME)).getValue();
	    if (disablePickupTime) {
		latestPickupTime = giga_parameters.getParameterBy(GigaConstants.GIGA_PARAM_DEFAULT_PICKUP_TIME).getValueAsString();
	    }
	    logger.debug("latestpickuptimeff " + latestPickupTime);
	    try {
		String startTime = giga_parameters.getParameterBy("dayStartTime").getValueAsString();
		long startTimeoftheDay = getTime(pickupTime, startTime);
		logger.debug("startoftheDay->" + new Date(startTimeoftheDay));
		long earliestPickupTimeOfTheDay = getTime(pickupTime, earliestPickupTime);
		long latestPickupTimeOfTheDay = getTime(pickupTime, latestPickupTime) - loadingBuffer;
		if (latestPickupTimeOfTheDay < earliestPickupTimeOfTheDay)
		    latestPickupTimeOfTheDay = (long) (latestPickupTimeOfTheDay + 24 * 3.6e6);
		long earliestDeliveryTimeOfTheDay = getTime(deliveryTime, earliestDeliveryTime);
		long latestDeliveryTimeOfTheDay = getTime(deliveryTime, latestDeliveryTime);
		if (latestDeliveryTimeOfTheDay < earliestDeliveryTimeOfTheDay)
		    latestDeliveryTimeOfTheDay = (long) (latestDeliveryTimeOfTheDay + 24 * 3.6e6);
		logger.debug("earliestPickupTimeOfTheDay" + new Date(earliestPickupTimeOfTheDay));
		logger.debug("latestPickupTimeOfTheDay" + new Date(latestPickupTimeOfTheDay));
		logger.debug("earliestDeliveryTimeOfTheDay" + new Date(earliestDeliveryTimeOfTheDay));
		logger.debug("latestDeliveryTimeOfTheDay" + new Date(latestDeliveryTimeOfTheDay));
		Calendar ept = Calendar.getInstance();
		ept.setTimeInMillis(earliestPickupTimeOfTheDay);
		orderDetails.append("~Earliest Pickup Time: ,");
		orderDetails.append(ept.getTime().toString().concat(","));
		ept.setTimeInMillis(latestPickupTimeOfTheDay);
		orderDetails.append("~Latest Pickup Time: ,");
		orderDetails.append(ept.getTime().toString().concat(","));
		ept.setTimeInMillis(earliestDeliveryTimeOfTheDay);
		orderDetails.append("~Earliest Delivery Time: ,");
		orderDetails.append(ept.getTime().toString().concat(","));
		ept.setTimeInMillis(latestDeliveryTimeOfTheDay);
		orderDetails.append("~Latest Delivery Time: ,");
		orderDetails.append(ept.getTime().toString().concat(","));
		orderDetails.append("~Order Created Time: ,");
		ept.setTimeInMillis(System.currentTimeMillis());
		orderDetails.append(ept.getTime().toString().concat(","));
		orderDetails.append("~Loading Unloading Duration: ,");
		double loadBuffer = ((FloatParameter) giga_parameters.getParameterBy(GigaConstants.GIGA_PARAM_LOADING_BUFFER)).getValue();
		double unLoadBuffer = ((FloatParameter) giga_parameters.getParameterBy(GigaConstants.GIGA_PARAM_UNLOADING_BUFFER)).getValue();
		double loadUnLoadBuffer = (loadBuffer + unLoadBuffer) * 60000L;
		orderDetails.append(convertDurationToTime(ISO8601FormatFactory.getInstance().getDurationFormat().format(Double.valueOf(loadUnLoadBuffer).longValue()).toString().concat(",")));
		orderDetails.append("~Addition Drop Duration: ,");
		double transitTime = 0;
		if (giga_parameters.hasParameter(GigaConstants.GIGA_PARAM_TRANSIT_TIME))
		    transitTime = ((FloatParameter) giga_parameters.getParameterBy(GigaConstants.GIGA_PARAM_TRANSIT_TIME)).getValue();
		else
		    transitTime = 15;
		Long totalTransitTime = Double.valueOf(transitTime * noOfDrops).longValue();
		orderDetails.append(convertDurationToTime(ISO8601FormatFactory.getInstance().getDurationFormat().format(totalTransitTime * 60000L).toString().concat(",")));
		orderDetails.append("~Rest Hour Duration: ,");
		double pickup_AT = (Double) customer_pickupLocRes.getParameterBy("articulated").getValue();
		double delivery_AT = (Double) customer_deliveryLocRes.getParameterBy("articulated").getValue();
		double pickup_RT = (Double) customer_pickupLocRes.getParameterBy("rigid").getValue();
		double delivery_RT = (Double) customer_deliveryLocRes.getParameterBy("rigid").getValue();
		double pickup_ST = (Double) customer_pickupLocRes.getParameterBy("single").getValue();
		double delivery_ST = (Double) customer_deliveryLocRes.getParameterBy("single").getValue();
		logger.info("Placement started for truck: " + resource.getId());
		end = latestDeliveryTimeOfTheDay;
		String CurrLoc = null;
		long earliestAvailTime = 0;
		//List<Activity> prevTasks = resource.getActivitiesInInterval(Long.MIN_VALUE, Long.MAX_VALUE);
		List<Activity> prevTasks = resource.getActivitiesInInterval(System.currentTimeMillis(), Long.MAX_VALUE);
		if (prevTasks.size() < 1) {
		    StateValue<?> currentLocValue = resource.getStateVariableBy("Location").getValueAt(end);
		    earliestAvailTime = resource.getStateVariableBy("Location").getValueAt(end).getTime();
		    CurrLoc = currentLocValue.getValueAsString();
		    logger.debug("currentLocValue " + CurrLoc);
		    String prevOSOrderId = null;
		    int prevOSorderNoOfDrops = 0;
		    int prevOSorderNoOfDropsCompleted = 0;
		    boolean isDelayedOutstationDelivery = false;
		    if (giga_parameters.hasParameter(GigaConstants.GIGA_PARAM_DELAYED_OS_DELIVERY))
			isDelayedOutstationDelivery = ((BooleanParameter) giga_parameters.getParameterBy(GigaConstants.GIGA_PARAM_DELAYED_OS_DELIVERY)).getValue();
		    if (isDelayedOutstationDelivery) {
			if (resource.hasParameter(GigaConstants.PARAM_PREV_OS_ORDER_ID)) {
			    prevOSOrderId = resource.getParameterBy(GigaConstants.PARAM_PREV_OS_ORDER_ID).getValueAsString();
			    if (resource.hasParameter(GigaConstants.PARAM_PREV_OS_ORDER_NO_OF_DROPS)) {
				prevOSorderNoOfDrops = ((IntegerParameter) resource.getParameterBy(GigaConstants.PARAM_PREV_OS_ORDER_NO_OF_DROPS)).getValue();
			    }
			    if (prevOSorderNoOfDrops > 1) {
				Resource prevOSLoadStatusRes = resourceMap.getEntryBy(GigaConstants.RES_PREV_OS_LOAD_STATUS);
				if (prevOSLoadStatusRes != null) {
				    if (prevOSLoadStatusRes.hasParameter(prevOSOrderId)) {
					prevOSorderNoOfDropsCompleted = ((IntegerParameter) prevOSLoadStatusRes.getParameterBy(prevOSOrderId)).getValue();
				    }
				    if (prevOSorderNoOfDropsCompleted < (prevOSorderNoOfDrops - 1)) {
					Resource truckLatLongRes = resourceMap.getEntryBy(GigaConstants.RES_TRUCK_LAT_LONG);
					String[] truckLatLong = truckLatLongRes.getParameterBy(resource.getId()).getValueAsString().split(",");
					currLat = Double.valueOf(truckLatLong[0]);
					currLong = Double.valueOf(truckLatLong[1]);
					String prevOSorderDelLocation = resource.getParameterBy(GigaConstants.PARAM_PREV_OS_ORDER_DELIVERY_LOCATION).getValueAsString();
					Resource prevOSorderDelLocationRes = resourceMap.getEntryBy(prevOSorderDelLocation);
					double prevOSDelLocLat = ((FloatParameter) prevOSorderDelLocationRes.getParameterBy(GigaConstants.ROUTE_PARAM_LATITUDE)).getValue();
					double prevOSDelLocLong = ((FloatParameter) prevOSorderDelLocationRes.getParameterBy(GigaConstants.ROUTE_PARAM_LONGITUDE)).getValue();
					String[] prevOSFinalDelDuration = FindDistanceDuration.getDistanceDuration(currLat, currLong, prevOSDelLocLat, prevOSDelLocLong).split(",");
					long prevOSFinalDelDurWithUnload = Long.parseLong(prevOSFinalDelDuration[1]) + unloadingBuffer;
					earliestAvailTime = System.currentTimeMillis() + prevOSFinalDelDurWithUnload;
				    }
				}
			    }
			}
		    }
		    String[] latLongValues = currLatLong.getParameterBy(resource.getId()).getValueAsString().split(",");
		    currLat = Double.valueOf(latLongValues[0]);
		    currLong = Double.valueOf(latLongValues[1]);
		} else {
		    Collections.sort(prevTasks, new ActivityByStartComparator());
		    for (Activity act : prevTasks) {
			String orderId = act.getOrderNetId();
			Order prevOrder = RSPContainerHelper.getOrderMap(true).getEntryBy(orderId);
			String OrderType = prevOrder.getType();
			logger.debug("prevOrder->" + orderId);
			if (OrderType.equalsIgnoreCase("Maintenance")) {
			    logger.debug("It is a maintenance order");
			    continue;
			}
			logger.debug("prevOrder->" + orderId);
			CurrLoc = prevOrder.getParameterBy("deliveryLocation").getValueAsString();
			earliestAvailTime = act.getEnd();
			if (prevOrder.hasParameter(GigaConstants.ORD_PARAM_JOB_TYPE)) {
			    if (prevOrder.getParameterBy(GigaConstants.ORD_PARAM_JOB_TYPE).getValueAsString().equalsIgnoreCase("Outstation")) {
				long prevOrderStartTime = act.getStart() - 2 * 60000L;
				earliestAvailTime = resource.getStateVariableBy("Location").getValueAt(prevOrderStartTime).getTime();
				CurrLoc = resource.getStateVariableBy("Location").getValueAt(earliestAvailTime).getValueAsString();
				Resource thisOrderDel = RSPContainerHelper.getResourceMap(true).getEntryBy(order.getParameterBy("deliveryLocation").getValueAsString());
				Resource prevOrderPickDel = RSPContainerHelper.getResourceMap(true).getEntryBy(prevOrder.getParameterBy("pickupLocation").getValueAsString());
				if (durationCalculator < 3) {
				    if (thisOrderDel.getParameterBy(prevOrderPickDel.getId()) == null) {
					String[] curDelToNextPickupDuration = GigaUtils.getDynamicDuration(thisOrderDel, prevOrderPickDel).split(",");
					long curDelToNextPickupDur = Long.parseLong(curDelToNextPickupDuration[1]);
					long prevOrderPickupTime = df.parse(prevOrder.getParameterBy("estPickupTime").getValueAsString()).getTime();
					latestDeliveryTimeOfTheDay = prevOrderPickupTime - curDelToNextPickupDur;
					List<Double> listDistDur = new ArrayList<Double>();
					listDistDur.add(Double.parseDouble(curDelToNextPickupDuration[0]));
					listDistDur.add(Double.parseDouble(curDelToNextPickupDuration[1]));
					FloatListParameter newDest = new FloatListParameter(prevOrderPickDel.getId(), prevOrderPickDel.getId(), listDistDur);
					thisOrderDel.addParameter(newDest);
					break;
				    } else {
					long curDelToNextPickupDur = GigaUtils.getDuration(thisOrderDel.getId(), prevOrderPickDel.getId());
					long prevOrderPickupTime = df.parse(prevOrder.getParameterBy("estPickupTime").getValueAsString()).getTime();
					latestDeliveryTimeOfTheDay = prevOrderPickupTime - curDelToNextPickupDur;
					break;
				    }
				}
			    }
			}
		    }
		    logger.debug("currentLocValue " + CurrLoc);
		}
		if (earliestAvailTime > startTimeoftheDay)
		    start = earliestAvailTime + 60000;
		else
		    start = startTimeoftheDay;
		long currentTime = System.currentTimeMillis();
		if (start < currentTime)
		    start = currentTime + 10 * 60000L;
		logger.debug("startTimeOfTheDay: " + startTimeoftheDay);
		logger.debug("earliestAvailTime: " + earliestAvailTime);
		logger.debug("EstimatedStartTime:" + start);
		logger.debug("truckCurrLoc: " + CurrLoc);
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(resource.getStateVariableBy("Location").getValueTail(Long.MIN_VALUE).get(resource.getStateVariableBy("Location").getValueTail(Long.MIN_VALUE).size() - 1).getTime());
		Resource pickupDynamic = RSPContainerHelper.getResourceMap(true).getEntryBy(pickupLocation);
		long currToPickupDur = 0;
		boolean canHaveCurrLatLong = GigaUtils.canHaveCurrLatLong(resource);
		String eventStatus = null;
		if (order.hasParameter(GigaConstants.TRUCK_EVENT_STATUS)) {
		    eventStatus = order.getParameterBy(GigaConstants.TRUCK_EVENT_STATUS).getValueAsString();
		}
		if (eventStatus == null) {
		    if (durationCalculator < 3) {
			if (currLat != 0) {
			    if (canHaveCurrLatLong) {
				double pickUpLat = (Double) pickupDynamic.getParameterBy("lat").getValue();
				double pickUpLong = (Double) pickupDynamic.getParameterBy("long").getValue();
				String[] currToPickupDuration = FindDistanceDuration.getDistanceDuration(currLat, currLong, pickUpLat, pickUpLong).split(",");
				currToPickupDur = Long.parseLong(currToPickupDuration[1]);
			    } else {
				currToPickupDur = GigaUtils.getAverageDurationForCoLocatedBaseLocation(CurrLoc, pickupLocation);
			    }
			} else {
			    currToPickupDur = GigaUtils.getAverageDurationForCoLocatedBaseLocation(CurrLoc, pickupLocation);
			}
		    } else {
			currToPickupDur = GigaUtils.getAverageDurationForCoLocatedBaseLocation(CurrLoc, pickupLocation);
		    }
		} else {
		    if (eventStatus.equalsIgnoreCase(GigaConstants.TRUCK_BD_STATUS_SHORT_CLOSE)) {
			if (order.hasParameter(GigaConstants.TRUCK_LOAD_PICKUP_STATUS)) {
			    String loadStatus = order.getParameterBy(GigaConstants.TRUCK_LOAD_PICKUP_STATUS).getValueAsString();
			    if (loadStatus.equalsIgnoreCase(GigaConstants.TRUCK_LOAD_PICKUP_COMPLETED)) {
				Resource truckLatLongRes = resourceMap.getEntryBy(GigaConstants.RES_TRUCK_LAT_LONG);
				String[] truckLatLong = truckLatLongRes.getParameterBy(order.getParameterBy(GigaConstants.ORDER_PARAM_PREV_TRUCK_ID).getValueAsString()).getValueAsString().split(",");
				String[] currToPickupDuration = FindDistanceDuration.getDistanceDuration(currLat, currLong, Double.valueOf(truckLatLong[0]), Double.valueOf(truckLatLong[1])).split(",");
				currToPickupDur = Long.parseLong(currToPickupDuration[1]);
			    }
			}
		    } else if (eventStatus.equalsIgnoreCase(GigaConstants.TRUCK_BD_STATUS_RESUME)) {
			if (order.hasParameter(GigaConstants.TRUCK_LOAD_PICKUP_STATUS)) {
			    String loadStatus = order.getParameterBy(GigaConstants.TRUCK_LOAD_PICKUP_STATUS).getValueAsString();
			    if (loadStatus.equalsIgnoreCase(GigaConstants.TRUCK_LOAD_PICKUP_COMPLETED)) {
				currToPickupDur = 60000;
				loadingBuffer = 60000;
			    }
			}
		    }
		}
		if ((currToPickupDur + tripDuration > 7200000.0D) && (currToPickupDur + tripDuration <= 14400000.0D))
		    restDuration = restHrs4;
		else if ((currToPickupDur + tripDuration > 14400000.0D) && (currToPickupDur + tripDuration <= 28800000.0D))
		    restDuration = restHrs8;
		else if ((currToPickupDur + tripDuration > 28800000.0D))
		    restDuration = restHrs211 + restHrs111;
		orderDetails.append(convertDurationToTime(ISO8601FormatFactory.getInstance().getDurationFormat().format(restDuration).toString().concat(",\n")));
		long estcurrToPickupTime = start + currToPickupDur;
		if (estcurrToPickupTime < earliestPickupTimeOfTheDay)
		    start = start + (earliestPickupTimeOfTheDay - estcurrToPickupTime);
		//----------------------------------------
		String pickupRegion = resourceMap.getEntryBy(pickupLocation).getName();
		String deliveryRegion = resourceMap.getEntryBy(deliveryLocation).getName();
		{
		    //if (!pickupRegion.equalsIgnoreCase(GigaConstants.CENTRAL_REGION) && !deliveryRegion.equalsIgnoreCase(GigaConstants.CENTRAL_REGION))
		    logger.debug("LocalOrders");
		    if (noOfDrops > 1.0) {
			logger.debug("Local Order With Multiple No.of Drops");
			long deliveryTime1 = start + currToPickupDur + tripDuration + loadingBuffer;
			logger.debug("1st delivery: " + new Date(deliveryTime1));
			long inTransitTravelTime = (long) 9e5;
			end = (long) (deliveryTime1 + unloadingBuffer + (noOfDrops - 1) * (inTransitTravelTime + unloadingBuffer));
			logger.debug("start->" + new Date(start));
			logger.debug("end->" + new Date(end));
			List<Activity> previousActs = resource.getActivitiesInInterval(startTimeoftheDay, start);
			long prevDur = 0L;
			for (Activity acts : previousActs) {
			    long prevDuration = acts.getDuration();
			    prevDur = +prevDuration;
			}
			FloatState capacityState = (FloatState) resource.getStateVariableBy(CAPACITYSTATE_KEY);
			double startCapacity = ((Double) capacityState.getValueAt(start).getValue()).doubleValue();
			double endCapacity = ((Double) capacityState.getValueAt(end).getValue()).doubleValue();
			double actualCapacity = ((Double) capacityState.getActualState().getValue()).doubleValue();
			Objectives we = task.getObjectives();
			FloatParameter fp = (FloatParameter) we.getParameterBy("quantity");
			double taskQty = fp.getValue().doubleValue();
			if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("articulated") && pickup_AT == 0.0) {
			    logger.debug("22 : For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
			    String planningLog = remarks1.getValueAsString();
			    logMessage = "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation";
			    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
			    remarks1.setValue(planningLog);
			    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
			} else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("articulated") && delivery_AT == 0.0) {
			    logger.debug("23 : For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
			    String planningLog = remarks1.getValueAsString();
			    logMessage = "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation";
			    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
			    remarks1.setValue(planningLog);
			    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
			} else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("rigid") && pickup_RT == 0.0) {
			    logger.debug("24 : For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
			    String planningLog = remarks1.getValueAsString();
			    logMessage = "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation";
			    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
			    remarks1.setValue(planningLog);
			    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
			} else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("rigid") && delivery_RT == 0.0) {
			    logger.debug("25 : For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
			    String planningLog = remarks1.getValueAsString();
			    logMessage = "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation";
			    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
			    remarks1.setValue(planningLog);
			    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
			} else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("single") && pickup_ST == 0.0) {
			    logger.debug("26 : For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
			    String planningLog = remarks1.getValueAsString();
			    logMessage = "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation";
			    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
			    remarks1.setValue(planningLog);
			    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
			} else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("single") && delivery_ST == 0.0) {
			    logger.debug("27 : For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
			    String planningLog = remarks1.getValueAsString();
			    logMessage = "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation";
			    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
			    remarks1.setValue(planningLog);
			    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
			} else if ((startCapacity + taskQty < this.capacityLowerBound.doubleValue()) || (startCapacity + taskQty > this.capacityUpperBound.doubleValue())) {
			    logger.debug("28 : All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			    String planningLog = remarks1.getValueAsString();
			    logMessage = "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end);
			    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
			    remarks1.setValue(planningLog);
			    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			} else if (resource.getActivitiesInInterval(start, end).size() > 0) {
			    logger.debug("29 : All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			    String planningLog = remarks1.getValueAsString();
			    logMessage = "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end);
			    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
			    remarks1.setValue(planningLog);
			    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			} else if ((startCapacity > this.capacityLowerBound.doubleValue()) || (endCapacity > this.capacityLowerBound.doubleValue())) {
			    logger.debug("30 : All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			    String planningLog = remarks1.getValueAsString();
			    logMessage = "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end);
			    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
			    remarks1.setValue(planningLog);
			    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			} else if ((actualCapacity < 0.0D) || (actualCapacity > 1.0D)) {
			    logger.debug("31 : All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			    String planningLog = remarks1.getValueAsString();
			    logMessage = "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end);
			    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
			    remarks1.setValue(planningLog);
			    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			    throw RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			} else if (prevDur + (end - start) > maxWorkHrs) {
			    if (order.getType().contains("Local")) {
				logger.debug("32 : Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
				String planningLog = remarks1.getValueAsString();
				logMessage = "Standard working hours for the evaluated driver " + resource.getParameterBy("driverId").getValueAsString() + " is exceeding the limits if this order is allocated.";
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "Standard working hours for the evaluated driver " + resource.getParameterBy("driverId").getValueAsString() + "is exceeding the limits if this order is allocated.");
			    } else if (order.getType().contains("Outstation")) {
				GigaUtils.addParamLocalOS(order, resource.getId(), false);
				isDayOSPossible = false;
			    }
			} else if (start < System.currentTimeMillis()) {
			    if (order.getType().contains("Local")) {
				logger.debug("33 : Start time to fulfill this order is less than the current time");
				String planningLog = remarks1.getValueAsString();
				logMessage = "The Expected pickup time can not be fulfilled since the current time crossed over.";
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The Expected pickup time can not be fulfilled since the current time crossed over.");
			    } else if (order.getType().contains("Outstation")) {
				GigaUtils.addParamLocalOS(order, resource.getId(), false);
				isDayOSPossible = false;
			    }
			} else if (start + currToPickupDur > latestPickupTimeOfTheDay) {
			    if (order.getType().contains("Local")) {
				logger.debug("34 : The Expected pickup time can not be fulfilled since the latest pickup time will cross over.");
				String planningLog = remarks1.getValueAsString();
				logMessage = "The Expected pickup time can not be fulfilled since the latest pickup time will cross over.";
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The Expected pickup time can not be fulfilled since the latest pickup time will cross over.");
			    } else if (order.getType().contains("Outstation")) {
				GigaUtils.addParamLocalOS(order, resource.getId(), false);
				isDayOSPossible = false;
			    }
			} else if (end > latestDeliveryTimeOfTheDay) {
			    latestDeliveryTimeOfTheDay = latestDeliveryTimeOfTheDay + endTimeBuffer;
			    if (end > latestDeliveryTimeOfTheDay) {
				if (order.getType().contains("Local")) {
				    logger.debug("35 : Derived End time is greater than the required delivery time");
				    String planningLog = remarks1.getValueAsString();
				    logMessage = "The feasible trucks to fulfill this order cannot complete the trip within End time is greater than the required delivery time.";
				    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				    remarks1.setValue(planningLog);
				    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The feasible trucks to fulfill this order cannot complete the trip within End time is greater than the required delivery time.");
				} else if (order.getType().contains("Outstation")) {
				    GigaUtils.addParamLocalOS(order, resource.getId(), false);
				    isDayOSPossible = false;
				}
			    } else {
				logger.debug("start->" + new Date(start));
				logger.debug("end->" + new Date(end));
				placementResult = new DefaultPlacementResult(PlacementResult.SUCCESS);
				placementResult.setStart(start);
				placementResult.setEnd(end);
				placementResult.setQuantity(this.quantity);
				placementResult.setResource(resource);
				remarks.setValue("");
				logger.debug("PlacementResult: " + placementResult);
			    }
			} else {
			    logger.debug("START: " + new Date(start));
			    logger.debug("END:" + new Date(end));
			    placementResult = new DefaultPlacementResult(PlacementResult.SUCCESS);
			    placementResult.setStart(start);
			    placementResult.setEnd(end);
			    placementResult.setQuantity(this.quantity);
			    placementResult.setResource(resource);
			    remarks.setValue("");
			    logger.debug("PlacementResult: " + placementResult);
			}
		    } else {
			long totalTripDur = currToPickupDur + tripDuration;
			logger.debug("totalTripDuration: " + totalTripDur);
			Date Startdate = new Date(start);
			logger.debug("startTime:" + Startdate);
			logger.debug("resource evaluated in placement" + resource);
			Date endDate = new Date(end);
			logger.debug("endTime" + endDate);
			if (totalTripDur <= 7200000.0D) {
			    logger.debug("totalTripDur is less than 2 hrs");
			    logger.debug("tripDuration" + totalTripDur / 3600000);
			    logger.debug("currToPickupDur: " + currToPickupDur / 3600000);
			    logger.debug("STARTBefore:" + new Date(start));
			    logger.debug("EndBefore:" + new Date(end));
			    long estPickupTime = start + currToPickupDur;
			    if ((estPickupTime + loadingBuffer) >= getTime(pickupTime, pickupLocPeakStart1) && estPickupTime <= getTime(pickupTime, pickupLocPeakEnd1)) {
				logger.debug("pickupTime lies between peak1");
				long latePickupBuffer = getTime(pickupTime, pickupLocPeakEnd1) - estPickupTime;
				start = start + latePickupBuffer;
			    }
			    if ((estPickupTime + loadingBuffer) >= getTime(pickupTime, pickupLocPeakStart2) && estPickupTime <= getTime(pickupTime, pickupLocPeakEnd2)) {
				logger.debug("pickupTime lies between peak2");
				long latePickupBuffer = getTime(pickupTime, pickupLocPeakEnd2) - estPickupTime;
				start = start + latePickupBuffer;
			    }
			    if ((estPickupTime + loadingBuffer) >= getTime(pickupTime, pickupLocPeakStart3) && estPickupTime <= getTime(pickupTime, pickupLocPeakEnd3)) {
				logger.debug("pickupTime lies between peak3");
				long latePickupBuffer = getTime(pickupTime, pickupLocPeakEnd3) - estPickupTime;
				start = start + latePickupBuffer;
			    }
			    if ((estPickupTime + loadingBuffer) >= getTime(pickupTime, pickupLocPeakStart4) && estPickupTime <= getTime(pickupTime, pickupLocPeakEnd4)) {
				logger.debug("pickupTime lies between peak4");
				long latePickupBuffer = getTime(pickupTime, pickupLocPeakEnd1) - estPickupTime;
				start = start + latePickupBuffer;
			    }
			    end = start + currToPickupDur + tripDuration + loadingBuffer + unloadingBuffer;
			    long estimatedDelivery = end - unloadingBuffer;
			    logger.debug("estimatedDelivery->" + estimatedDelivery);
			    if ((estimatedDelivery + unloadingBuffer) >= getTime(deliveryTime, deliveryLocPeakStart1) && (end - unloadingBuffer) <= getTime(deliveryTime, deliveryLocPeakEnd1)) {
				logger.debug("deliveryTime lies between peak1");
				long lateDeliveryBuffer = getTime(deliveryTime, deliveryLocPeakEnd1) - estimatedDelivery;
				start = start + lateDeliveryBuffer;
				end = end + lateDeliveryBuffer;
			    }
			    if ((estimatedDelivery + unloadingBuffer) >= getTime(deliveryTime, deliveryLocPeakStart2) && (end - unloadingBuffer) <= getTime(deliveryTime, deliveryLocPeakEnd2)) {
				logger.debug("deliveryTime lies between peak2");
				long lateDeliveryBuffer = getTime(deliveryTime, deliveryLocPeakEnd2) - estimatedDelivery;
				start = start + lateDeliveryBuffer;
				end = end + lateDeliveryBuffer;
			    }
			    if ((estimatedDelivery + unloadingBuffer) >= getTime(deliveryTime, deliveryLocPeakStart3) && (end - unloadingBuffer) <= getTime(deliveryTime, deliveryLocPeakEnd3)) {
				logger.debug("deliveryTime lies between peak3");
				long lateDeliveryBuffer = getTime(deliveryTime, deliveryLocPeakEnd3) - estimatedDelivery;
				start = start + lateDeliveryBuffer;
				end = end + lateDeliveryBuffer;
			    }
			    if ((estimatedDelivery + unloadingBuffer) >= getTime(deliveryTime, deliveryLocPeakStart4) && (end - unloadingBuffer) <= getTime(deliveryTime, deliveryLocPeakEnd4)) {
				logger.debug("deliveryTime lies between peak4");
				long lateDeliveryBuffer = getTime(deliveryTime, deliveryLocPeakEnd4) - estimatedDelivery;
				start = start + lateDeliveryBuffer;
				end = end + lateDeliveryBuffer;
			    }
			    logger.debug("currentTime: " + new Date(currentTime) + " START: " + new Date(start));
			    logger.debug("END:" + new Date(end));
			    List<Activity> previousActs = resource.getActivitiesInInterval(startTimeoftheDay, start);
			    long prevDur = 0L;
			    for (Activity acts : previousActs) {
				long prevDuration = acts.getDuration();
				prevDur = prevDuration;
			    }
			    FloatState capacityState = (FloatState) resource.getStateVariableBy(CAPACITYSTATE_KEY);
			    double startCapacity = ((Double) capacityState.getValueAt(start).getValue()).doubleValue();
			    double endCapacity = ((Double) capacityState.getValueAt(end).getValue()).doubleValue();
			    double actualCapacity = ((Double) capacityState.getActualState().getValue()).doubleValue();
			    Objectives we = task.getObjectives();
			    FloatParameter fp = (FloatParameter) we.getParameterBy("quantity");
			    double taskQty = fp.getValue().doubleValue();
			    if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("articulated") && pickup_AT == 0.0) {
				logger.debug("36 : For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
				String planningLog = remarks1.getValueAsString();
				logMessage = "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation";
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
			    } else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("articulated") && delivery_AT == 0.0) {
				logger.debug("37 : For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
				String planningLog = remarks1.getValueAsString();
				logMessage = "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation";
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
			    } else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("rigid") && pickup_RT == 0.0) {
				logger.debug("38 : For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
				String planningLog = remarks1.getValueAsString();
				logMessage = "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation";
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
			    } else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("rigid") && delivery_RT == 0.0) {
				logger.debug("39 : For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
				String planningLog = remarks1.getValueAsString();
				logMessage = "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation";
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
			    } else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("single") && pickup_ST == 0.0) {
				logger.debug("40 : For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
				String planningLog = remarks1.getValueAsString();
				logMessage = "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation";
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
			    } else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("single") && delivery_ST == 0.0) {
				logger.debug("41 : For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
				String planningLog = remarks1.getValueAsString();
				logMessage = "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation";
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
			    } else if ((startCapacity + taskQty < this.capacityLowerBound.doubleValue()) || (startCapacity + taskQty > this.capacityUpperBound.doubleValue())) {
				logger.debug("42 : All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
				String planningLog = remarks1.getValueAsString();
				logMessage = "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end);
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			    } else if (resource.getActivitiesInInterval(start, end).size() > 0) {
				logger.debug("43 : All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
				String planningLog = remarks1.getValueAsString();
				logMessage = "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end);
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			    } else if ((startCapacity > this.capacityLowerBound.doubleValue()) || (endCapacity > this.capacityLowerBound.doubleValue())) {
				logger.debug("44 : All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
				String planningLog = remarks1.getValueAsString();
				logMessage = "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end);
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			    } else if ((actualCapacity < 0.0D) || (actualCapacity > 1.0D)) {
				logger.debug("45 : All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
				String planningLog = remarks1.getValueAsString();
				logMessage = "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end);
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
				throw RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			    } else if (prevDur + (end - start) > maxWorkHrs) {
				if (order.getType().contains("Local")) {
				    logger.debug("46 : Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
				    String planningLog = remarks1.getValueAsString();
				    logMessage = "Standard working hours for the evaluated driver " + resource.getParameterBy("driverId").getValueAsString() + " is exceeding the limits if this order is allocated.";
				    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				    remarks1.setValue(planningLog);
				    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "Standard working hours for the evaluated driver " + resource.getParameterBy("driverId").getValueAsString() + "is exceeding the limits if this order is allocated.");
				} else if (order.getType().contains("Outstation")) {
				    GigaUtils.addParamLocalOS(order, resource.getId(), false);
				    isDayOSPossible = false;
				}
			    } else if (start < System.currentTimeMillis()) {
				if (order.getType().contains("Local")) {
				    logger.debug("47 : Start time to fulfill this order is less than the current time");
				    String planningLog = remarks1.getValueAsString();
				    logMessage = "The Expected pickup time can not be fulfilled since the current time crossed over.";
				    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				    remarks1.setValue(planningLog);
				    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The Expected pickup time can not be fulfilled since the current time crossed over.");
				} else if (order.getType().contains("Outstation")) {
				    GigaUtils.addParamLocalOS(order, resource.getId(), false);
				    isDayOSPossible = false;
				}
			    } else if (estPickupTime > latestPickupTimeOfTheDay) {
				if (order.getType().contains("Local")) {
				    logger.debug("48 : The Expected pickup time can not be fulfilled since the latest pickup time will cross over.");
				    String planningLog = remarks1.getValueAsString();
				    logMessage = "The Expected pickup time can not be fulfilled since the latest pickup time will cross over.";
				    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				    remarks1.setValue(planningLog);
				    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The Expected pickup time can not be fulfilled since the latest pickup time will cross over.");
				} else if (order.getType().contains("Outstation")) {
				    GigaUtils.addParamLocalOS(order, resource.getId(), false);
				    isDayOSPossible = false;
				}
			    } else if (end > latestDeliveryTimeOfTheDay) {
				latestDeliveryTimeOfTheDay = latestDeliveryTimeOfTheDay + endTimeBuffer;
				if (end > latestDeliveryTimeOfTheDay) {
				    if (order.getType().contains("Local")) {
					logger.debug("49 : Derived End time is greater than the required delivery time");
					String planningLog = remarks1.getValueAsString();
					logMessage = "The feasible trucks to fulfill this order cannot complete the trip within End time is greater than the required delivery time.";
					planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
					remarks1.setValue(planningLog);
					placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The feasible trucks to fulfill this order cannot complete the trip within End time is greater than the required delivery time.");
				    } else if (order.getType().contains("Outstation")) {
					GigaUtils.addParamLocalOS(order, resource.getId(), false);
					isDayOSPossible = false;
				    }
				} else {
				    logger.debug("start->" + new Date(start));
				    logger.debug("end->" + new Date(end));
				    placementResult = new DefaultPlacementResult(PlacementResult.SUCCESS);
				    placementResult.setStart(start);
				    placementResult.setEnd(end);
				    placementResult.setQuantity(this.quantity);
				    placementResult.setResource(resource);
				    remarks.setValue("");
				    logger.debug("PlacementResult: " + placementResult);
				}
			    } else {
				logger.debug("START: " + new Date(start));
				logger.debug("END:" + new Date(end));
				placementResult = new DefaultPlacementResult(PlacementResult.SUCCESS);
				placementResult.setStart(start);
				placementResult.setEnd(end);
				placementResult.setQuantity(this.quantity);
				placementResult.setResource(resource);
				remarks.setValue("");
				logger.debug("PlacementResult: " + placementResult);
			    }
			} else if ((totalTripDur > 7200000.0D) && (totalTripDur <= 14400000.0D)) {
			    logger.debug("totalTripDur is less than 4 hrs and greater than 2 hrs");
			    logger.debug("tripDuration" + totalTripDur / 3600000);
			    logger.debug("currToPickupDur: " + currToPickupDur / 3600000);
			    logger.debug("STARTBefore:" + new Date(start));
			    logger.debug("EndBefore:" + new Date(end));
			    long estPickupTime = 0;
			    if (start + currToPickupDur < 7200000.0D)
				estPickupTime = start + currToPickupDur;
			    else if (start + currToPickupDur > 7200000.0D)
				estPickupTime = start + currToPickupDur + restHrs4;
			    if ((estPickupTime + loadingBuffer) >= getTime(pickupTime, pickupLocPeakStart1) && estPickupTime <= getTime(pickupTime, pickupLocPeakEnd1)) {
				logger.debug("pickupTime lies between peak1");
				long latePickupBuffer = getTime(pickupTime, pickupLocPeakEnd1) - estPickupTime;
				start = start + latePickupBuffer;
			    }
			    if ((estPickupTime + loadingBuffer) >= getTime(pickupTime, pickupLocPeakStart2) && estPickupTime <= getTime(pickupTime, pickupLocPeakEnd2)) {
				logger.debug("pickupTime lies between peak2");
				long latePickupBuffer = getTime(pickupTime, pickupLocPeakEnd2) - estPickupTime;
				start = start + latePickupBuffer;
			    }
			    if ((estPickupTime + loadingBuffer) >= getTime(pickupTime, pickupLocPeakStart3) && estPickupTime <= getTime(pickupTime, pickupLocPeakEnd3)) {
				logger.debug("pickupTime lies between peak3");
				long latePickupBuffer = getTime(pickupTime, pickupLocPeakEnd3) - estPickupTime;
				start = start + latePickupBuffer;
			    }
			    if ((estPickupTime + loadingBuffer) >= getTime(pickupTime, pickupLocPeakStart4) && estPickupTime <= getTime(pickupTime, pickupLocPeakEnd4)) {
				logger.debug("pickupTime lies between peak4");
				long latePickupBuffer = getTime(pickupTime, pickupLocPeakEnd1) - estPickupTime;
				start = start + latePickupBuffer;
			    }
			    end = start + currToPickupDur + tripDuration + restHrs4 + loadingBuffer + unloadingBuffer;
			    long estimatedDelivery = end - unloadingBuffer;
			    logger.debug("estimatedDelivery->" + estimatedDelivery);
			    if ((estimatedDelivery + unloadingBuffer) >= getTime(deliveryTime, deliveryLocPeakStart1) && (end - unloadingBuffer) <= getTime(deliveryTime, deliveryLocPeakEnd1)) {
				logger.debug("deliveryTime lies between peak1");
				long lateDeliveryBuffer = getTime(deliveryTime, deliveryLocPeakEnd1) - estimatedDelivery;
				start = start + lateDeliveryBuffer;
				end = end + lateDeliveryBuffer;
			    }
			    if ((estimatedDelivery + unloadingBuffer) >= getTime(deliveryTime, deliveryLocPeakStart2) && (end - unloadingBuffer) <= getTime(deliveryTime, deliveryLocPeakEnd2)) {
				logger.debug("deliveryTime lies between peak2");
				long lateDeliveryBuffer = getTime(deliveryTime, deliveryLocPeakEnd2) - estimatedDelivery;
				start = start + lateDeliveryBuffer;
				end = end + lateDeliveryBuffer;
			    }
			    if ((estimatedDelivery + unloadingBuffer) >= getTime(deliveryTime, deliveryLocPeakStart3) && (end - unloadingBuffer) <= getTime(deliveryTime, deliveryLocPeakEnd3)) {
				logger.debug("deliveryTime lies between peak3");
				long lateDeliveryBuffer = getTime(deliveryTime, deliveryLocPeakEnd3) - estimatedDelivery;
				start = start + lateDeliveryBuffer;
				end = end + lateDeliveryBuffer;
			    }
			    if ((estimatedDelivery + unloadingBuffer) >= getTime(deliveryTime, deliveryLocPeakStart4) && (end - unloadingBuffer) <= getTime(deliveryTime, deliveryLocPeakEnd4)) {
				logger.debug("deliveryTime lies between peak4");
				long lateDeliveryBuffer = getTime(deliveryTime, deliveryLocPeakEnd4) - estimatedDelivery;
				start = start + lateDeliveryBuffer;
				end = end + lateDeliveryBuffer;
			    }
			    logger.debug("currentTime: " + new Date(currentTime) + " START: " + new Date(start));
			    logger.debug("END:" + new Date(end));
			    List<Activity> previousActs = resource.getActivitiesInInterval(startTimeoftheDay, start);
			    long prevDur = 0L;
			    for (Activity acts : previousActs) {
				long prevDuration = acts.getDuration();
				prevDur = prevDuration;
			    }
			    FloatState capacityState = (FloatState) resource.getStateVariableBy(CAPACITYSTATE_KEY);
			    double startCapacity = ((Double) capacityState.getValueAt(start).getValue()).doubleValue();
			    double endCapacity = ((Double) capacityState.getValueAt(end).getValue()).doubleValue();
			    double actualCapacity = ((Double) capacityState.getActualState().getValue()).doubleValue();
			    Objectives we = task.getObjectives();
			    FloatParameter fp = (FloatParameter) we.getParameterBy("quantity");
			    double taskQty = fp.getValue().doubleValue();
			    if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("articulated") && pickup_AT == 0.0) {
				logger.debug("50 : For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
				String planningLog = remarks1.getValueAsString();
				logMessage = "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation";
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
			    } else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("articulated") && delivery_AT == 0.0) {
				logger.debug("51 : For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
				String planningLog = remarks1.getValueAsString();
				logMessage = "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation";
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
			    } else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("rigid") && pickup_RT == 0.0) {
				logger.debug("52 : For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
				String planningLog = remarks1.getValueAsString();
				logMessage = "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation";
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
			    } else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("rigid") && delivery_RT == 0.0) {
				logger.debug("53 : For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
				String planningLog = remarks1.getValueAsString();
				logMessage = "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation";
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
			    } else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("single") && pickup_ST == 0.0) {
				logger.debug("54 : For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
				String planningLog = remarks1.getValueAsString();
				logMessage = "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation";
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
			    } else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("single") && delivery_ST == 0.0) {
				logger.debug("55 : For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
				String planningLog = remarks1.getValueAsString();
				logMessage = "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation";
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
			    } else if ((startCapacity + taskQty < this.capacityLowerBound.doubleValue()) || (startCapacity + taskQty > this.capacityUpperBound.doubleValue())) {
				logger.debug("56 : All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
				String planningLog = remarks1.getValueAsString();
				logMessage = "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end);
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			    } else if ((startCapacity > this.capacityLowerBound.doubleValue()) || (endCapacity > this.capacityLowerBound.doubleValue())) {
				logger.debug("57 : All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
				String planningLog = remarks1.getValueAsString();
				logMessage = "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end);
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			    } else if ((actualCapacity < 0.0D) || (actualCapacity > 1.0D)) {
				logger.debug("58 : All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
				String planningLog = remarks1.getValueAsString();
				logMessage = "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end);
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			    } else if (resource.getActivitiesInInterval(start, end).size() > 0) {
				logger.debug("59 : All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
				String planningLog = remarks1.getValueAsString();
				logMessage = "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end);
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			    } else if (prevDur + (end - start) > maxWorkHrs) {
				if (order.getType().contains("Local")) {
				    logger.debug("60 : Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
				    String planningLog = remarks1.getValueAsString();
				    logMessage = "Standard working hours for the evaluated driver " + resource.getParameterBy("driverId").getValueAsString() + " is exceeding the limits if this order is allocated.";
				    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				    remarks1.setValue(planningLog);
				    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "Standard working hours for the evaluated driver " + resource.getParameterBy("driverId").getValueAsString() + "is exceeding the limits if this order is allocated.");
				} else if (order.getType().contains("Outstation")) {
				    GigaUtils.addParamLocalOS(order, resource.getId(), false);
				    isDayOSPossible = false;
				}
			    } else if (start < System.currentTimeMillis()) {
				if (order.getType().contains("Local")) {
				    logger.debug("61 : Start time to fulfill this order is less than the current time");
				    String planningLog = remarks1.getValueAsString();
				    logMessage = "The Expected pickup time can not be fulfilled since the current time crossed over.";
				    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				    remarks1.setValue(planningLog);
				    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The Expected pickup time can not be fulfilled since the current time crossed over.");
				} else if (order.getType().contains("Outstation")) {
				    GigaUtils.addParamLocalOS(order, resource.getId(), false);
				    isDayOSPossible = false;
				}
			    } else if (estPickupTime > latestPickupTimeOfTheDay) {
				if (order.getType().contains("Local")) {
				    logger.debug("62 : The Expected pickup time can not be fulfilled since the latest pickup time will cross over.");
				    String planningLog = remarks1.getValueAsString();
				    logMessage = "The Expected pickup time can not be fulfilled since the latest pickup time will cross over.";
				    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				    remarks1.setValue(planningLog);
				    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The Expected pickup time can not be fulfilled since the latest pickup time will cross over.");
				} else if (order.getType().contains("Outstation")) {
				    GigaUtils.addParamLocalOS(order, resource.getId(), false);
				    isDayOSPossible = false;
				}
			    } else if (end > latestDeliveryTimeOfTheDay) {
				latestDeliveryTimeOfTheDay = latestDeliveryTimeOfTheDay + endTimeBuffer;
				if (end > latestDeliveryTimeOfTheDay) {
				    if (order.getType().contains("Local")) {
					logger.debug("63 : Derived End time is greater than the required delivery time");
					String planningLog = remarks1.getValueAsString();
					logMessage = "The feasible trucks to fulfill this order cannot complete the trip within End time is greater than the required delivery time.";
					planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
					remarks1.setValue(planningLog);
					placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The feasible trucks to fulfill this order cannot complete the trip within End time is greater than the required delivery time.");
				    } else if (order.getType().contains("Outstation")) {
					GigaUtils.addParamLocalOS(order, resource.getId(), false);
					isDayOSPossible = false;
				    }
				} else {
				    logger.debug("start->" + new Date(start));
				    logger.debug("end->" + new Date(end));
				    placementResult = new DefaultPlacementResult(PlacementResult.SUCCESS);
				    placementResult.setStart(start);
				    placementResult.setEnd(end);
				    placementResult.setQuantity(this.quantity);
				    placementResult.setResource(resource);
				    remarks.setValue("");
				    logger.debug("PlacementResult: " + placementResult);
				}
			    } else {
				logger.debug("START: " + new Date(start));
				logger.debug("END:" + new Date(end));
				placementResult = new DefaultPlacementResult(PlacementResult.SUCCESS);
				placementResult.setStart(start);
				placementResult.setEnd(end);
				placementResult.setQuantity(this.quantity);
				placementResult.setResource(resource);
				remarks.setValue("");
				logger.debug("PlacementResult: " + placementResult);
			    }
			} else if ((totalTripDur > 14400000.0D) && (totalTripDur <= 28800000.0D)) {
			    logger.debug("totalTripDur is less than 8 hrs and greater than 4 hrs");
			    logger.debug("tripDuration" + totalTripDur / 3600000);
			    logger.debug("currToPickupDur: " + currToPickupDur / 3600000);
			    logger.debug("STARTBefore:" + new Date(start));
			    logger.debug("EndBefore:" + new Date(end));
			    long estPickupTime = 0;
			    if (start + currToPickupDur < 14400000.0D)
				estPickupTime = start + currToPickupDur;
			    if (start + currToPickupDur > 14400000.0D)
				estPickupTime = start + currToPickupDur + restHrs8;
			    if ((estPickupTime + loadingBuffer) >= getTime(pickupTime, pickupLocPeakStart1) && estPickupTime <= getTime(pickupTime, pickupLocPeakEnd1)) {
				logger.debug("pickupTime lies between peak1");
				long latePickupBuffer = getTime(pickupTime, pickupLocPeakEnd1) - estPickupTime;
				start = start + latePickupBuffer;
			    }
			    if ((estPickupTime + loadingBuffer) >= getTime(pickupTime, pickupLocPeakStart2) && estPickupTime <= getTime(pickupTime, pickupLocPeakEnd2)) {
				logger.debug("pickupTime lies between peak2");
				long latePickupBuffer = getTime(pickupTime, pickupLocPeakEnd2) - estPickupTime;
				start = start + latePickupBuffer;
			    }
			    if ((estPickupTime + loadingBuffer) >= getTime(pickupTime, pickupLocPeakStart3) && estPickupTime <= getTime(pickupTime, pickupLocPeakEnd3)) {
				logger.debug("pickupTime lies between peak3");
				long latePickupBuffer = getTime(pickupTime, pickupLocPeakEnd3) - estPickupTime;
				start = start + latePickupBuffer;
			    }
			    if ((estPickupTime + loadingBuffer) >= getTime(pickupTime, pickupLocPeakStart4) && estPickupTime <= getTime(pickupTime, pickupLocPeakEnd4)) {
				logger.debug("pickupTime lies between peak4");
				long latePickupBuffer = getTime(pickupTime, pickupLocPeakEnd1) - estPickupTime;
				start = start + latePickupBuffer;
			    }
			    end = start + currToPickupDur + tripDuration + restHrs8 + loadingBuffer + unloadingBuffer;
			    long estimatedDelivery = end - unloadingBuffer;
			    logger.debug("estimatedDelivery->" + estimatedDelivery);
			    if ((estimatedDelivery + unloadingBuffer) >= getTime(deliveryTime, deliveryLocPeakStart1) && (end - unloadingBuffer) <= getTime(deliveryTime, deliveryLocPeakEnd1)) {
				logger.debug("deliveryTime lies between peak1");
				long lateDeliveryBuffer = getTime(deliveryTime, deliveryLocPeakEnd1) - estimatedDelivery;
				start = start + lateDeliveryBuffer;
				end = end + lateDeliveryBuffer;
			    }
			    if ((estimatedDelivery + unloadingBuffer) >= getTime(deliveryTime, deliveryLocPeakStart2) && (end - unloadingBuffer) <= getTime(deliveryTime, deliveryLocPeakEnd2)) {
				logger.debug("deliveryTime lies between peak2");
				long lateDeliveryBuffer = getTime(deliveryTime, deliveryLocPeakEnd2) - estimatedDelivery;
				start = start + lateDeliveryBuffer;
				end = end + lateDeliveryBuffer;
			    }
			    if ((estimatedDelivery + unloadingBuffer) >= getTime(deliveryTime, deliveryLocPeakStart3) && (end - unloadingBuffer) <= getTime(deliveryTime, deliveryLocPeakEnd3)) {
				logger.debug("deliveryTime lies between peak3");
				long lateDeliveryBuffer = getTime(deliveryTime, deliveryLocPeakEnd3) - estimatedDelivery;
				start = start + lateDeliveryBuffer;
				end = end + lateDeliveryBuffer;
			    }
			    if ((estimatedDelivery + unloadingBuffer) >= getTime(deliveryTime, deliveryLocPeakStart4) && (end - unloadingBuffer) <= getTime(deliveryTime, deliveryLocPeakEnd4)) {
				logger.debug("deliveryTime lies between peak4");
				long lateDeliveryBuffer = getTime(deliveryTime, deliveryLocPeakEnd4) - estimatedDelivery;
				start = start + lateDeliveryBuffer;
				end = end + lateDeliveryBuffer;
			    }
			    logger.debug("currentTime: " + new Date(currentTime) + " START: " + new Date(start));
			    logger.debug("END: " + new Date(end));
			    List<Activity> previousActs = resource.getActivitiesInInterval(startTimeoftheDay, start);
			    long prevDur = 0L;
			    for (Activity acts : previousActs) {
				long prevDuration = acts.getDuration();
				prevDur = prevDuration;
			    }
			    FloatState capacityState = (FloatState) resource.getStateVariableBy(CAPACITYSTATE_KEY);
			    double startCapacity = ((Double) capacityState.getValueAt(start).getValue()).doubleValue();
			    double endCapacity = ((Double) capacityState.getValueAt(end).getValue()).doubleValue();
			    double actualCapacity = ((Double) capacityState.getActualState().getValue()).doubleValue();
			    Objectives we = task.getObjectives();
			    FloatParameter fp = (FloatParameter) we.getParameterBy("quantity");
			    double taskQty = fp.getValue().doubleValue();
			    if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("articulated") && pickup_AT == 0.0) {
				logger.debug("64 : For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
				String planningLog = remarks1.getValueAsString();
				logMessage = "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation";
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
			    } else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("articulated") && delivery_AT == 0.0) {
				logger.debug("65 : For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
				String planningLog = remarks1.getValueAsString();
				logMessage = "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation";
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
			    } else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("rigid") && pickup_RT == 0.0) {
				logger.debug("66 : For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
				String planningLog = remarks1.getValueAsString();
				logMessage = "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation";
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
			    } else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("rigid") && delivery_RT == 0.0) {
				logger.debug("67 : For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
				String planningLog = remarks1.getValueAsString();
				logMessage = "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation";
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
			    } else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("single") && pickup_ST == 0.0) {
				logger.debug("68 : For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
				String planningLog = remarks1.getValueAsString();
				logMessage = "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation";
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
			    } else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("single") && delivery_ST == 0.0) {
				logger.debug("69 : For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
				String planningLog = remarks1.getValueAsString();
				logMessage = "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation";
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
			    } else if ((startCapacity + taskQty < this.capacityLowerBound.doubleValue()) || (startCapacity + taskQty > this.capacityUpperBound.doubleValue())) {
				logger.debug("70 : All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
				String planningLog = remarks1.getValueAsString();
				logMessage = "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end);
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			    } else if ((startCapacity > this.capacityLowerBound.doubleValue()) || (endCapacity > this.capacityLowerBound.doubleValue())) {
				logger.debug("71 : All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
				String planningLog = remarks1.getValueAsString();
				logMessage = "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end);
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			    } else if ((actualCapacity < 0.0D) || (actualCapacity > 1.0D)) {
				logger.debug("72 : All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
				String planningLog = remarks1.getValueAsString();
				logMessage = "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end);
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			    } else if (resource.getActivitiesInInterval(start, end).size() > 0) {
				logger.debug("73 : All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
				String planningLog = remarks1.getValueAsString();
				logMessage = "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end);
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			    } else if (prevDur + (end - start) > maxWorkHrs) {
				if (order.getType().contains("Local")) {
				    logger.debug("74 : Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
				    String planningLog = remarks1.getValueAsString();
				    logMessage = "Standard working hours for the evaluated driver " + resource.getParameterBy("driverId").getValueAsString() + " is exceeding the limits if this order is allocated.";
				    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				    remarks1.setValue(planningLog);
				    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "Standard working hours for the evaluated driver " + resource.getParameterBy("driverId").getValueAsString() + "is exceeding the limits if this order is allocated.");
				} else if (order.getType().contains("Outstation")) {
				    GigaUtils.addParamLocalOS(order, resource.getId(), false);
				    isDayOSPossible = false;
				}
			    } else if (start < System.currentTimeMillis()) {
				if (order.getType().contains("Local")) {
				    logger.debug("75 : Start time to fulfill this order is less than the current time");
				    String planningLog = remarks1.getValueAsString();
				    logMessage = "The Expected pickup time can not be fulfilled since the current time crossed over.";
				    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				    remarks1.setValue(planningLog);
				    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The Expected pickup time can not be fulfilled since the current time crossed over.");
				} else if (order.getType().contains("Outstation")) {
				    GigaUtils.addParamLocalOS(order, resource.getId(), false);
				    isDayOSPossible = false;
				}
			    } else if (estPickupTime > latestPickupTimeOfTheDay) {
				if (order.getType().contains("Local")) {
				    logger.debug("76 : The Expected pickup time can not be fulfilled since the latest pickup time will cross over.");
				    String planningLog = remarks1.getValueAsString();
				    logMessage = "The Expected pickup time can not be fulfilled since the latest pickup time will cross over.";
				    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				    remarks1.setValue(planningLog);
				    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The Expected pickup time can not be fulfilled since the latest pickup time will cross over.");
				} else if (order.getType().contains("Outstation")) {
				    GigaUtils.addParamLocalOS(order, resource.getId(), false);
				    isDayOSPossible = false;
				}
			    } else if (end > latestDeliveryTimeOfTheDay) {
				latestDeliveryTimeOfTheDay = latestDeliveryTimeOfTheDay + endTimeBuffer;
				if (end > latestDeliveryTimeOfTheDay) {
				    if (order.getType().contains("Local")) {
					logger.debug("77 : Derived End time is greater than the required delivery time");
					String planningLog = remarks1.getValueAsString();
					logMessage = "The feasible trucks to fulfill this order cannot complete the trip within End time is greater than the required delivery time.";
					planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
					remarks1.setValue(planningLog);
					placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The feasible trucks to fulfill this order cannot complete the trip within End time is greater than the required delivery time.");
				    } else if (order.getType().contains("Outstation")) {
					GigaUtils.addParamLocalOS(order, resource.getId(), false);
					isDayOSPossible = false;
				    }
				} else {
				    logger.debug("start->" + new Date(start));
				    logger.debug("end->" + new Date(end));
				    placementResult = new DefaultPlacementResult(PlacementResult.SUCCESS);
				    placementResult.setStart(start);
				    placementResult.setEnd(end);
				    placementResult.setQuantity(this.quantity);
				    placementResult.setResource(resource);
				    remarks.setValue("");
				    logger.debug("PlacementResult: " + placementResult);
				}
			    } else {
				logger.debug("START: " + new Date(start));
				logger.debug("END:" + new Date(end));
				placementResult = new DefaultPlacementResult(PlacementResult.SUCCESS);
				placementResult.setStart(start);
				placementResult.setEnd(end);
				placementResult.setQuantity(this.quantity);
				placementResult.setResource(resource);
				remarks.setValue("");
				logger.debug("PlacementResult: " + placementResult);
			    }
			} else if ((totalTripDur > 28800000.0D)) {
			    logger.debug("totalTripDur is greater than 8 hrs");
			    logger.debug("tripDuration" + totalTripDur / 3600000);
			    logger.debug("currToPickupDur: " + currToPickupDur / 3600000);
			    logger.debug("STARTBefore:" + new Date(start));
			    logger.debug("EndBefore:" + new Date(end));
			    long estPickupTime = 0;
			    if (start + currToPickupDur <= 14400000.0D)
				estPickupTime = start + currToPickupDur;
			    else if ((start + currToPickupDur) > 14400000.0D && (start + currToPickupDur) <= 28800000.0D)
				estPickupTime = start + currToPickupDur + restHrs111;
			    else if (start + currToPickupDur > 28800000.0D)
				estPickupTime = start + currToPickupDur + restHrs111 + restHrs211;
			    if ((estPickupTime + loadingBuffer) >= getTime(pickupTime, pickupLocPeakStart1) && estPickupTime <= getTime(pickupTime, pickupLocPeakEnd1)) {
				logger.debug("pickupTime lies between peak1");
				long latePickupBuffer = getTime(pickupTime, pickupLocPeakEnd1) - estPickupTime;
				start = start + latePickupBuffer;
			    }
			    if ((estPickupTime + loadingBuffer) >= getTime(pickupTime, pickupLocPeakStart2) && estPickupTime <= getTime(pickupTime, pickupLocPeakEnd2)) {
				logger.debug("pickupTime lies between peak2");
				long latePickupBuffer = getTime(pickupTime, pickupLocPeakEnd2) - estPickupTime;
				start = start + latePickupBuffer;
			    }
			    if ((estPickupTime + loadingBuffer) >= getTime(pickupTime, pickupLocPeakStart3) && estPickupTime <= getTime(pickupTime, pickupLocPeakEnd3)) {
				logger.debug("pickupTime lies between peak3");
				long latePickupBuffer = getTime(pickupTime, pickupLocPeakEnd3) - estPickupTime;
				start = start + latePickupBuffer;
			    }
			    if ((estPickupTime + loadingBuffer) >= getTime(pickupTime, pickupLocPeakStart4) && estPickupTime <= getTime(pickupTime, pickupLocPeakEnd4)) {
				logger.debug("pickupTime lies between peak4");
				long latePickupBuffer = getTime(pickupTime, pickupLocPeakEnd1) - estPickupTime;
				start = start + latePickupBuffer;
			    }
			    end = start + currToPickupDur + tripDuration + restHrs111 + restHrs211 + loadingBuffer + unloadingBuffer;
			    long estimatedDelivery = end - unloadingBuffer;
			    logger.debug("estimatedDelivery->" + estimatedDelivery);
			    if ((estimatedDelivery + unloadingBuffer) >= getTime(deliveryTime, deliveryLocPeakStart1) && (end - unloadingBuffer) <= getTime(deliveryTime, deliveryLocPeakEnd1)) {
				logger.debug("deliveryTime lies between peak1");
				long lateDeliveryBuffer = getTime(deliveryTime, deliveryLocPeakEnd1) - estimatedDelivery;
				start = start + lateDeliveryBuffer;
				end = end + lateDeliveryBuffer;
			    }
			    if ((estimatedDelivery + unloadingBuffer) >= getTime(deliveryTime, deliveryLocPeakStart2) && (end - unloadingBuffer) <= getTime(deliveryTime, deliveryLocPeakEnd2)) {
				logger.debug("deliveryTime lies between peak2");
				long lateDeliveryBuffer = getTime(deliveryTime, deliveryLocPeakEnd2) - estimatedDelivery;
				start = start + lateDeliveryBuffer;
				end = end + lateDeliveryBuffer;
			    }
			    if ((estimatedDelivery + unloadingBuffer) >= getTime(deliveryTime, deliveryLocPeakStart3) && (end - unloadingBuffer) <= getTime(deliveryTime, deliveryLocPeakEnd3)) {
				logger.debug("deliveryTime lies between peak3");
				long lateDeliveryBuffer = getTime(deliveryTime, deliveryLocPeakEnd3) - estimatedDelivery;
				start = start + lateDeliveryBuffer;
				end = end + lateDeliveryBuffer;
			    }
			    if ((estimatedDelivery + unloadingBuffer) >= getTime(deliveryTime, deliveryLocPeakStart4) && (end - unloadingBuffer) <= getTime(deliveryTime, deliveryLocPeakEnd4)) {
				logger.debug("deliveryTime lies between peak4");
				long lateDeliveryBuffer = getTime(deliveryTime, deliveryLocPeakEnd4) - estimatedDelivery;
				start = start + lateDeliveryBuffer;
				end = end + lateDeliveryBuffer;
			    }
			    logger.debug("currentTime: " + new Date(currentTime) + " START: " + new Date(start));
			    logger.debug("END:" + new Date(end));
			    List<Activity> previousActs = resource.getActivitiesInInterval(startTimeoftheDay, start);
			    long prevDur = 0L;
			    for (Activity acts : previousActs) {
				long prevDuration = acts.getDuration();
				prevDur = prevDuration;
			    }
			    FloatState capacityState = (FloatState) resource.getStateVariableBy(CAPACITYSTATE_KEY);
			    double startCapacity = ((Double) capacityState.getValueAt(start).getValue()).doubleValue();
			    double endCapacity = ((Double) capacityState.getValueAt(end).getValue()).doubleValue();
			    double actualCapacity = ((Double) capacityState.getActualState().getValue()).doubleValue();
			    Objectives we = task.getObjectives();
			    FloatParameter fp = (FloatParameter) we.getParameterBy("quantity");
			    double taskQty = fp.getValue().doubleValue();
			    if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("articulated") && pickup_AT == 0.0) {
				logger.debug("78 : For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
				String planningLog = remarks1.getValueAsString();
				logMessage = "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation";
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
			    } else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("articulated") && delivery_AT == 0.0) {
				logger.debug("79 : For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
				String planningLog = remarks1.getValueAsString();
				logMessage = "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation";
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
			    } else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("rigid") && pickup_RT == 0.0) {
				logger.debug("80 : For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
				String planningLog = remarks1.getValueAsString();
				logMessage = "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation";
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
			    } else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("rigid") && delivery_RT == 0.0) {
				logger.debug("81 : For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
				String planningLog = remarks1.getValueAsString();
				logMessage = "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation";
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
			    } else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("single") && pickup_ST == 0.0) {
				logger.debug("82 : For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
				String planningLog = remarks1.getValueAsString();
				logMessage = "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation";
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
			    } else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("single") && delivery_ST == 0.0) {
				logger.debug("83 : For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
				String planningLog = remarks1.getValueAsString();
				logMessage = "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation";
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
			    } else if ((startCapacity + taskQty < this.capacityLowerBound.doubleValue()) || (startCapacity + taskQty > this.capacityUpperBound.doubleValue())) {
				logger.debug("84 : All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
				String planningLog = remarks1.getValueAsString();
				logMessage = "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end);
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			    } else if ((startCapacity > this.capacityLowerBound.doubleValue()) || (endCapacity > this.capacityLowerBound.doubleValue())) {
				logger.debug("85 : All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
				String planningLog = remarks1.getValueAsString();
				logMessage = "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end);
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			    } else if ((actualCapacity < 0.0D) || (actualCapacity > 1.0D)) {
				logger.debug("86 : All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
				String planningLog = remarks1.getValueAsString();
				logMessage = "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end);
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			    } else if (resource.getActivitiesInInterval(start, end).size() > 0) {
				logger.debug("87 : All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
				String planningLog = remarks1.getValueAsString();
				logMessage = "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end);
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			    } else if (prevDur + (end - start) > maxWorkHrs) {
				if (order.getType().contains("Local")) {
				    logger.debug("88 : Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
				    String planningLog = remarks1.getValueAsString();
				    logMessage = "Standard working hours for the evaluated driver " + resource.getParameterBy("driverId").getValueAsString() + " is exceeding the limits if this order is allocated.";
				    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				    remarks1.setValue(planningLog);
				    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "Standard working hours for the evaluated driver " + resource.getParameterBy("driverId").getValueAsString() + "is exceeding the limits if this order is allocated.");
				} else if (order.getType().contains("Outstation")) {
				    GigaUtils.addParamLocalOS(order, resource.getId(), false);
				    isDayOSPossible = false;
				}
			    } else if (start < System.currentTimeMillis()) {
				if (order.getType().contains("Local")) {
				    logger.debug("89 : Start time to fulfill this order is less than the current time");
				    String planningLog = remarks1.getValueAsString();
				    logMessage = "The Expected pickup time can not be fulfilled since the current time crossed over.";
				    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				    remarks1.setValue(planningLog);
				    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The Expected pickup time can not be fulfilled since the current time crossed over.");
				} else if (order.getType().contains("Outstation")) {
				    GigaUtils.addParamLocalOS(order, resource.getId(), false);
				    isDayOSPossible = false;
				}
			    } else if (estPickupTime > latestPickupTimeOfTheDay) {
				if (order.getType().contains("Local")) {
				    logger.debug("90 : The Expected pickup time can not be fulfilled since the latest pickup time will cross over.");
				    String planningLog = remarks1.getValueAsString();
				    logMessage = "The Expected pickup time can not be fulfilled since the latest pickup time will cross over.";
				    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				    remarks1.setValue(planningLog);
				    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The Expected pickup time can not be fulfilled since the latest pickup time will cross over.");
				} else if (order.getType().contains("Outstation")) {
				    GigaUtils.addParamLocalOS(order, resource.getId(), false);
				    isDayOSPossible = false;
				}
			    } else if (end > latestDeliveryTimeOfTheDay) {
				latestDeliveryTimeOfTheDay = latestDeliveryTimeOfTheDay + endTimeBuffer;
				if (end > latestDeliveryTimeOfTheDay) {
				    if (order.getType().contains("Local")) {
					logger.debug("91 : Derived End time is greater than the required delivery time");
					String planningLog = remarks1.getValueAsString();
					logMessage = "The feasible trucks to fulfill this order cannot complete the trip within End time is greater than the required delivery time.";
					planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
					remarks1.setValue(planningLog);
					placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The feasible trucks to fulfill this order cannot complete the trip within End time is greater than the required delivery time.");
				    } else if (order.getType().contains("Outstation")) {
					GigaUtils.addParamLocalOS(order, resource.getId(), false);
					isDayOSPossible = false;
				    }
				} else {
				    logger.debug("start->" + new Date(start));
				    logger.debug("end->" + new Date(end));
				    placementResult = new DefaultPlacementResult(PlacementResult.SUCCESS);
				    placementResult.setStart(start);
				    placementResult.setEnd(end);
				    placementResult.setQuantity(this.quantity);
				    placementResult.setResource(resource);
				    remarks.setValue("");
				    logger.debug("PlacementResult: " + placementResult);
				}
			    } else {
				logger.debug("START: " + new Date(start));
				logger.debug("END:" + new Date(end));
				placementResult = new DefaultPlacementResult(PlacementResult.SUCCESS);
				placementResult.setStart(start);
				placementResult.setEnd(end);
				placementResult.setQuantity(this.quantity);
				placementResult.setResource(resource);
				remarks.setValue("");
				logger.debug("PlacementResult: " + placementResult);
				return placementResult;
			    }
			}
		    }
		    if (customerType.contains("Outstation") && !isDayOSPossible) {
			logger.debug(customerType);
			int localDeliveryCount = 0;
			earliestDeliveryTimeOfTheDay = earliestDeliveryTimeOfTheDay + 86400000L;
			latestDeliveryTimeOfTheDay = latestDeliveryTimeOfTheDay + 86400000L;
			long estimatedStart = earliestDeliveryTimeOfTheDay - tripDuration - currToPickupDur - loadingBuffer;
			logger.debug("estimatedStart:" + new Date(estimatedStart));
			if (estimatedStart + currToPickupDur + loadingBuffer <= latestPickupTimeOfTheDay) {
			    start = estimatedStart;
			    logger.debug("Case1_start: " + new Date(start));
			} else if ((estimatedStart + currToPickupDur + loadingBuffer) > latestPickupTimeOfTheDay) {
			    start = latestPickupTimeOfTheDay - currToPickupDur - loadingBuffer;
			    logger.debug(new Date(latestPickupTimeOfTheDay) + "-----" + currToPickupDur / 3600000);
			    logger.debug("Case2_start: " + new Date(start));
			}
			long estPickupTime = start + currToPickupDur;
			if ((estPickupTime + loadingBuffer) >= getTime(pickupTime, pickupLocPeakStart1) && estPickupTime <= getTime(pickupTime, pickupLocPeakEnd1)) {
			    logger.debug("pickupTime lies between peak1");
			    long latePickupBuffer = estPickupTime + loadingBuffer - getTime(pickupTime, pickupLocPeakStart1);
			    start = start - latePickupBuffer;
			}
			if ((estPickupTime + loadingBuffer) >= getTime(pickupTime, pickupLocPeakStart2) && estPickupTime <= getTime(pickupTime, pickupLocPeakEnd2)) {
			    logger.debug("pickupTime lies between peak2");
			    long latePickupBuffer = estPickupTime + loadingBuffer - getTime(pickupTime, pickupLocPeakStart2);
			    start = start - latePickupBuffer;
			}
			if ((estPickupTime + loadingBuffer) >= getTime(pickupTime, pickupLocPeakStart3) && estPickupTime <= getTime(pickupTime, pickupLocPeakEnd3)) {
			    logger.debug("pickupTime lies between peak3");
			    long latePickupBuffer = estPickupTime + loadingBuffer - getTime(pickupTime, pickupLocPeakStart3);
			    start = start - latePickupBuffer;
			}
			if ((estPickupTime + loadingBuffer) >= getTime(pickupTime, pickupLocPeakStart4) && estPickupTime <= getTime(pickupTime, pickupLocPeakEnd4)) {
			    logger.debug("pickupTime lies between peak4");
			    long latePickupBuffer = estPickupTime + loadingBuffer - getTime(pickupTime, pickupLocPeakStart4);
			    start = start - latePickupBuffer;
			}
			if (noOfDrops > 1.0) {
			    logger.debug("Outstation Order With Multiple No.of Drops");
			    long deliveryTime1 = earliestDeliveryTimeOfTheDay;
			    logger.debug("1st delivery: " + new Date(deliveryTime1));
			    long inTransitTravelTime = (long) 9e5;
			    end = (long) (deliveryTime1 + unloadingBuffer + (noOfDrops - 1) * (inTransitTravelTime + unloadingBuffer));
			    logger.debug("start->" + new Date(start));
			    logger.debug("end->" + new Date(end));
			} else {
			    end = earliestDeliveryTimeOfTheDay + unloadingBuffer;
			    long estimatedDelivery = earliestDeliveryTimeOfTheDay;
			    logger.debug("estimatedDelivery->" + estimatedDelivery);
			    if ((estimatedDelivery + unloadingBuffer) >= getTime(deliveryTime, deliveryLocPeakStart1) && (end - unloadingBuffer) <= getTime(deliveryTime, deliveryLocPeakEnd1)) {
				logger.debug("deliveryTime lies between peak1");
				long lateDeliveryBuffer = getTime(deliveryTime, deliveryLocPeakEnd1) - estimatedDelivery;
				end = end + lateDeliveryBuffer;
			    }
			}
			logger.debug("start->" + new Date(start));
			logger.debug("end->" + new Date(end));
			List<Activity> previousActs = resource.getActivitiesInInterval(startTimeoftheDay, end);
			for (Activity activity : previousActs) {
			    String PrevOrderID = activity.getOrderNetId();
			    Order PrevOrder = (Order) RSPContainerHelper.getOrderMap(true).getEntryBy(PrevOrderID);
			    if (PrevOrder.hasParameter(GigaConstants.ORD_PARAM_JOB_TYPE)) {
				String prevJobType = PrevOrder.getParameterBy(GigaConstants.ORD_PARAM_JOB_TYPE).getValueAsString();
				if (prevJobType.equals("Local"))
				    localDeliveryCount++;
			    }
			}
			logger.debug("localDeliveryCount" + localDeliveryCount);
			List<Activity> availCheck = resource.getActivitiesInInterval(start, end);
			logger.debug("activities for the whole day" + availCheck);
			FloatState capacityState = (FloatState) resource.getStateVariableBy(CAPACITYSTATE_KEY);
			double startCapacity = ((Double) ((FloatState) capacityState).getValueAt(start).getValue()).doubleValue();
			double endCapacity = ((Double) ((FloatState) capacityState).getValueAt(end).getValue()).doubleValue();
			double actualCapacity = ((Double) ((FloatState) capacityState).getActualState().getValue()).doubleValue();
			Objectives we = task.getObjectives();
			FloatParameter fp = (FloatParameter) we.getParameterBy("quantity");
			double taskQty = fp.getValue().doubleValue();
			if (start < earliestAvailTime) {
			    logger.debug("20 : The Evaluated StartTime is crossing the earliest Available Time");
			    String planningLog = remarks1.getValueAsString();
			    logMessage = "The Evaluated StartTime is crossing the earliest Available Time";
			    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
			    remarks1.setValue(planningLog);
			    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The Evaluated StartTime is crossing the earliest Available Time.");
			} else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("articulated") && pickup_AT == 0.0) {
			    logger.debug("8 :For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
			    String planningLog = remarks1.getValueAsString();
			    logMessage = "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation";
			    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
			    remarks1.setValue(planningLog);
			    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
			} else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("articulated") && delivery_AT == 0.0) {
			    logger.debug("9 : For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
			    String planningLog = remarks1.getValueAsString();
			    logMessage = "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation";
			    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
			    remarks1.setValue(planningLog);
			    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
			} else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("rigid") && pickup_RT == 0.0) {
			    logger.debug("10 : For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
			    String planningLog = remarks1.getValueAsString();
			    logMessage = "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation,";
			    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
			    remarks1.setValue(planningLog);
			    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
			} else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("rigid") && delivery_RT == 0.0) {
			    logger.debug("11 : For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
			    String planningLog = remarks1.getValueAsString();
			    logMessage = "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation";
			    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
			    remarks1.setValue(planningLog);
			    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
			} else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("single") && pickup_ST == 0.0) {
			    logger.debug("12 : For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
			    String planningLog = remarks1.getValueAsString();
			    logMessage = "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation,";
			    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
			    remarks1.setValue(planningLog);
			    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and pickup location " + pickupLocation + " the truck type permitted are not available for allocation");
			} else if (!truckType.equalsIgnoreCase(orderedTruckType) && truckType.equalsIgnoreCase("single") && delivery_ST == 0.0) {
			    logger.debug("13 : For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
			    String planningLog = remarks1.getValueAsString();
			    logMessage = "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation,";
			    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
			    remarks1.setValue(planningLog);
			    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "For the given customer " + customerID + " and delivery location " + deliveryLocation + " the truck type permitted are not available for allocation");
			} else if ((startCapacity + taskQty < this.capacityLowerBound.doubleValue()) || (startCapacity + taskQty > this.capacityUpperBound.doubleValue())) {
			    logger.debug("14 : All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			    String planningLog = remarks1.getValueAsString();
			    logMessage = "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end);
			    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
			    remarks1.setValue(planningLog);
			    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			} else if ((startCapacity > this.capacityLowerBound.doubleValue()) || (endCapacity > this.capacityLowerBound.doubleValue())) {
			    logger.debug("15 : All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			    String planningLog = remarks1.getValueAsString();
			    logMessage = "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end);
			    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
			    remarks1.setValue(planningLog);
			    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			} else if ((actualCapacity < 0.0D) || (actualCapacity > 1.0D)) {
			    logger.debug("16 : All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			    String planningLog = remarks1.getValueAsString();
			    logMessage = "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end);
			    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
			    remarks1.setValue(planningLog);
			    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			} else if (resource.getActivitiesInInterval(start, end).size() > 0) {
			    logger.debug("17 : All feasible trucks for this order are occupied between " + new Date(start) + " and " + new Date(end));
			    String planningLog = remarks1.getValueAsString();
			    logMessage = "No truck is feasible to fulfill this order between " + new Date(start) + " and " + new Date(end);
			    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
			    remarks1.setValue(planningLog);
			    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "No truck is feasible to fulfill this order between " + new Date(start) + " and " + new Date(end));
			} else if ((previousActs.size() > 0) && (localDeliveryCount < 1)) {
			    logger.debug("18 : Truck has not completed any local orders ");
			    String planningLog = remarks1.getValueAsString();
			    logMessage = "System is not able to find the truck which had done atleast a local order to fulfill this outstation trip.";
			    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
			    remarks1.setValue(planningLog);
			    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "System is not able to find the truck which had done atleast a local order to fulfill this outstation trip .");
			} else if (start < System.currentTimeMillis()) {
			    logger.debug("19 : Start time to fulfill this order is less than the current time");
			    String planningLog = remarks1.getValueAsString();
			    logMessage = "The Expected pickup time can not be fulfilled since the current time crossed over.";
			    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
			    remarks1.setValue(planningLog);
			    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The Expected pickup time can not be fulfilled since the current time crossed over.");
			} else if (estPickupTime > latestPickupTimeOfTheDay) {
			    logger.debug("20 : The Expected pickup time can not be fulfilled since the latest pickup time will cross over.");
			    String planningLog = remarks1.getValueAsString();
			    logMessage = "The Expected pickup time can not be fulfilled since the latest pickup time will cross over.";
			    planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
			    remarks1.setValue(planningLog);
			    placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The Expected pickup time can not be fulfilled since the latest pickup time will cross over.");
			} else if (end > latestDeliveryTimeOfTheDay) {
			    latestDeliveryTimeOfTheDay = latestDeliveryTimeOfTheDay + endTimeBuffer;
			    if (end > latestDeliveryTimeOfTheDay) {
				logger.debug("21 : Derived End time is greater than the required delivery time");
				String planningLog = remarks1.getValueAsString();
				logMessage = "The feasible trucks to fulfill this order cannot complete the trip within End time is greater than the required delivery time.";
				planningLog = GigaUtils.getPlanningLog(planningLog, resource, CurrLoc, earliestAvailTime, currToPickupDur, logMessage);
				remarks1.setValue(planningLog);
				placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The feasible trucks to fulfill this order cannot complete the trip within End time is greater than the required delivery time.");
			    } else {
				logger.debug("start->" + new Date(start));
				logger.debug("end->" + new Date(end));
				placementResult = new DefaultPlacementResult(PlacementResult.SUCCESS);
				placementResult.setStart(start);
				placementResult.setEnd(end);
				placementResult.setQuantity(this.quantity);
				placementResult.setResource(resource);
				remarks.setValue("");
				logger.debug("PlacementResult: " + placementResult);
			    }
			} else {
			    logger.debug("start->" + new Date(start));
			    logger.debug("end->" + new Date(end));
			    placementResult = new DefaultPlacementResult(PlacementResult.SUCCESS);
			    placementResult.setStart(start);
			    placementResult.setEnd(end);
			    placementResult.setQuantity(this.quantity);
			    placementResult.setResource(resource);
			    remarks.setValue("");
			    logger.debug("PlacementResult: " + placementResult);
			}
		    }
		}
		//---------------------------------
	    } catch (ParseException e1) {
		e1.printStackTrace();
	    }
	    logger.debug(placementResult);
	    if (placementResult == null || placementResult.getStatus() == PlacementResult.FAILURE) {
		String statusMsg = placementResult.getMessage();
		//logger.debug("remarks :" + remarks1.getValueAsString());
		remarks.setValue(statusMsg);
	    }
	    orderDetails.append("~Truck Id,Truck Reg No,Driver Id,Driver Name,Base Location,Base Location LAT&LONG,Make,Current Location,Current Location LAT&LONG,Earliest Avl Time,Travel Duration,Reason");
	    // orderDetails.append("~List of failed trucks and its reason: \n");
	    orderDetails.append(remarks1.getValueAsString());
	    orderDetailsP.setValue(orderDetails.toString());
	}
	return placementResult;
    }

    public void updateStateVariables(Task task, Resource resource, PlacementResult placementResult) {
	this.Capacity = getFloatState(resource, CAPACITYSTATE_KEY);
	this.Location = getStringState(resource, LOCATION_KEY);
	ParameterizedElement tmp = task;
	String orderNetId = null;
	while (tmp != null) {
	    orderNetId = tmp.getId();
	    tmp = tmp.getParent();
	}
	ElementMap<Resource> resourceMap = RSPContainerHelper.getResourceMap(true);
	Order order = (Order) RSPContainerHelper.getOrderMap(true).getEntryBy(orderNetId);
	Resource currLatLong = RSPContainerHelper.getResourceMap(true).getEntryBy("truckLatitudeLongitude");
	logger.debug("ordupdateStateVar: " + orderNetId);
	double quantity = task.getQuantity();
	long start = placementResult.getStart();
	logger.debug("start:" + new Date(start));
	long end = placementResult.getEnd();
	logger.debug("end:" + new Date(end));
	logger.debug("Start: " + new Date(start) + " End: " + new Date(end));
	double currLat = 0;
	double currLong = 0;
	Resource giga_parameters = RSPContainerHelper.getResourceMap(true).getEntryBy("giga_parameters");
	double loadingBufferValue = (Double) giga_parameters.getParameterBy("loadingBuffer").getValue();
	Double durationCalculator = ((FloatParameter) giga_parameters.getParameterBy("durationCalculation")).getValue();
	long loadingBuffer = (long) (loadingBufferValue * 60000L);
	if (order.hasParameter(GigaConstants.TRUCK_EVENT_STATUS)) {
	    String eventStatus = order.getParameterBy(GigaConstants.TRUCK_EVENT_STATUS).getValueAsString();
	    if (eventStatus.equalsIgnoreCase(GigaConstants.TRUCK_BD_STATUS_RESUME)) {
		if (order.hasParameter(GigaConstants.TRUCK_LOAD_PICKUP_STATUS)) {
		    String loadStatus = order.getParameterBy(GigaConstants.TRUCK_LOAD_PICKUP_STATUS).getValueAsString();
		    if (loadStatus.equalsIgnoreCase(GigaConstants.TRUCK_LOAD_PICKUP_COMPLETED)) {
			loadingBuffer = 60000;
		    }
		}
	    }
	}
	double unloadingBufferValue = (Double) giga_parameters.getParameterBy("unloadingBuffer").getValue();
	long unloadingBuffer = (long) (unloadingBufferValue * 60000L);
	double restHrs4value = (Double) giga_parameters.getParameterBy("restHrs4").getValue();
	long restHrs4 = (long) (restHrs4value * 60000L);
	double restHrs8value = (Double) giga_parameters.getParameterBy("restHrs8").getValue();
	long restHrs8 = (long) (restHrs8value * 60000L);
	double restHrs111value = (Double) giga_parameters.getParameterBy("restHrs111").getValue();
	long restHrs111 = (long) (restHrs111value * 60000L);
	double restHrs211value = (Double) giga_parameters.getParameterBy("restHrs211").getValue();
	long restHrs211 = (long) (restHrs211value * 60000L);
	if ((order.getType().equalsIgnoreCase("Maintenance")) || (order.getType().equalsIgnoreCase("PlannedLeave"))) {
	    start = task.getStart();
	    end = start + this.duration;
	    createAndAddFloatChange(task, start, this.Capacity, quantity);
	    createAndAddFloatChange(task, end, this.Capacity, -quantity);
	} else {
	    String customerType = order.getParameterBy("orderType").getValueAsString();
	    double noOfDrops = (Double) order.getParameterBy("noOfDrops").getValue();
	    String pickupLocation = order.getParameterBy("pickupLocation").getValueAsString();
	    String deliveryLocation = order.getParameterBy("deliveryLocation").getValueAsString();
	    String BaseLocation = resource.getParameterBy("location").getValueAsString();
	    Resource baselocationDynamic = RSPContainerHelper.getResourceMap(true).getEntryBy(BaseLocation);
	    Resource delResourcDeynamic = RSPContainerHelper.getResourceMap(true).getEntryBy(deliveryLocation);
	    double deliveryToBasekm = 0;
	    if (durationCalculator < 3) {
		if (delResourcDeynamic.getParameterBy(baselocationDynamic.getId()) == null) {
		    String[] deliveryToBasekmDistance = GigaUtils.getDynamicDuration(delResourcDeynamic, baselocationDynamic).split(",");
		    deliveryToBasekm = Double.parseDouble(deliveryToBasekmDistance[0]);
		    List<Double> listDistDur = new ArrayList<Double>();
		    listDistDur.add(Double.parseDouble(deliveryToBasekmDistance[0]));
		    listDistDur.add(Double.parseDouble(deliveryToBasekmDistance[1]));
		    FloatListParameter newDest = new FloatListParameter(BaseLocation, BaseLocation, listDistDur);
		    delResourcDeynamic.addParameter(newDest);
		} else {
		    deliveryToBasekm = GigaUtils.getDistance(deliveryLocation, BaseLocation);
		}
	    } else {
		deliveryToBasekm = GigaUtils.getDistance(deliveryLocation, BaseLocation);
	    }
	    long tripDuration = GigaUtils.getDuration(pickupLocation, deliveryLocation);
	    if (order.getParameterBy(GigaConstants.ORD_PARAM_BUSINESS_DIVISION).getValueAsString().equalsIgnoreCase(GigaConstants.BUSINESS_DIVISION_KUCHING)) {
		Double truckDrivingFactor = ((FloatParameter) giga_parameters.getParameterBy(GigaConstants.GIGA_PARAM_TRUCK_DRIVING_FACTOR)).getValue();
		tripDuration = (long) (tripDuration / truckDrivingFactor);
	    }
	    long restHr = 0L;
	    long pickupDate = ((DateParameter) task.getParameterBy("start")).getValue().longValue();
	    logger.debug(resource);
	    String CurrLoc = null;
	    //List<Activity> prevTasks = resource.getActivitiesInInterval(Long.MIN_VALUE, Long.MAX_VALUE);
	    List<Activity> prevTasks = resource.getActivitiesInInterval(System.currentTimeMillis(), Long.MAX_VALUE);
	    if (prevTasks.size() < 1) {
		StateValue<?> currentLocValue = resource.getStateVariableBy("Location").getValueAt(end);
		CurrLoc = currentLocValue.getValueAsString();
		logger.debug("currentLocValue " + CurrLoc);
		String[] latLongValues = currLatLong.getParameterBy(resource.getId()).getValueAsString().split(",");
		currLat = Double.valueOf(latLongValues[0]);
		currLong = Double.valueOf(latLongValues[1]);
	    } else {
		for (Activity act : prevTasks) {
		    String orderId = act.getOrderNetId();
		    Order prevOrder = RSPContainerHelper.getOrderMap(true).getEntryBy(orderId);
		    String OrderType = prevOrder.getType();
		    logger.debug("prevOrder->" + orderId);
		    if (OrderType.equalsIgnoreCase("Maintenance")) {
			logger.debug("It is a maintenance order");
			continue;
		    }
		    logger.debug("prevOrder->" + orderId);
		    CurrLoc = prevOrder.getParameterBy("deliveryLocation").getValueAsString();
		    if (prevOrder.getParameterBy("orderType").getValueAsString().contains("Outstation")) {
			long estimatedAvailableTime = act.getStart() - 2 * 60000L;
			CurrLoc = resource.getStateVariableBy("Location").getValueAt(estimatedAvailableTime).getValueAsString();
			break;
		    }
		}
		logger.debug("currentLocValue " + CurrLoc);
	    }
	    logger.debug("currentLoc: " + CurrLoc);
	    Resource pickupDynamic = RSPContainerHelper.getResourceMap(true).getEntryBy(pickupLocation);
	    long currToPickupDur = 0;
	    double currToPickupDist = 0;
	    boolean canHaveCurrLatLong = GigaUtils.canHaveCurrLatLong(resource);
	    String eventStatus = null;
	    if (order.hasParameter(GigaConstants.TRUCK_EVENT_STATUS)) {
		eventStatus = order.getParameterBy(GigaConstants.TRUCK_EVENT_STATUS).getValueAsString();
	    }
	    if (eventStatus == null) {
		if (durationCalculator < 3) {
		    if (currLat != 0) {
			if (canHaveCurrLatLong) {
			    double pickUpLat = (Double) pickupDynamic.getParameterBy("lat").getValue();
			    double pickUpLong = (Double) pickupDynamic.getParameterBy("long").getValue();
			    String[] currToPickupDuration = FindDistanceDuration.getDistanceDuration(currLat, currLong, pickUpLat, pickUpLong).split(",");
			    currToPickupDur = Long.parseLong(currToPickupDuration[1]);
			    currToPickupDist = Long.parseLong(currToPickupDuration[0]);
			} else {
			    currToPickupDur = GigaUtils.getDuration(CurrLoc, pickupLocation);
			    currToPickupDist = GigaUtils.getDistance(CurrLoc, pickupLocation);
			}
		    } else {
			currToPickupDur = GigaUtils.getDuration(CurrLoc, pickupLocation);
			currToPickupDist = GigaUtils.getDistance(CurrLoc, pickupLocation);
		    }
		} else {
		    currToPickupDist = GigaUtils.getDistance(CurrLoc, pickupLocation);
		    currToPickupDur = GigaUtils.getDuration(CurrLoc, pickupLocation);
		}
	    } else {
		if (eventStatus.equalsIgnoreCase(GigaConstants.TRUCK_BD_STATUS_SHORT_CLOSE)) {
		    if (order.hasParameter(GigaConstants.TRUCK_LOAD_PICKUP_STATUS)) {
			String loadStatus = order.getParameterBy(GigaConstants.TRUCK_LOAD_PICKUP_STATUS).getValueAsString();
			if (loadStatus.equalsIgnoreCase(GigaConstants.TRUCK_LOAD_PICKUP_COMPLETED)) {
			    Resource truckLatLongRes = resourceMap.getEntryBy(GigaConstants.RES_TRUCK_LAT_LONG);
			    String[] truckLatLong = truckLatLongRes.getParameterBy(order.getParameterBy(GigaConstants.ORDER_PARAM_PREV_TRUCK_ID).getValueAsString()).getValueAsString().split(",");
			    String[] currToPickupDuration = FindDistanceDuration.getDistanceDuration(currLat, currLong, Double.valueOf(truckLatLong[0]), Double.valueOf(truckLatLong[1])).split(",");
			    currToPickupDur = Long.parseLong(currToPickupDuration[1]);
			    CurrLoc = String.valueOf(currLat).concat(",").concat(String.valueOf(currLong));
			    pickupLocation = String.valueOf(currLat).concat(",").concat(String.valueOf(currLong));
			}
		    }
		} else if (eventStatus.equalsIgnoreCase(GigaConstants.TRUCK_BD_STATUS_RESUME)) {
		    if (order.hasParameter(GigaConstants.TRUCK_LOAD_PICKUP_STATUS)) {
			String loadStatus = order.getParameterBy(GigaConstants.TRUCK_LOAD_PICKUP_STATUS).getValueAsString();
			if (loadStatus.equalsIgnoreCase(GigaConstants.TRUCK_LOAD_PICKUP_COMPLETED)) {
			    currToPickupDur = 60000;
			    loadingBuffer = 60000;
			    CurrLoc = String.valueOf(currLat).concat(",").concat(String.valueOf(currLong));
			    pickupLocation = String.valueOf(currLat).concat(",").concat(String.valueOf(currLong));
			}
		    }
		}
	    }
	    StringParameter selectedTruck = (StringParameter) order.getParameterBy("truckId");
	    selectedTruck.setValue(resource.getId());
	    String driverId = resource.getParameterBy("driverId").getValueAsString();
	    Resource DriverRes = RSPContainerHelper.getResourceMap(true).getEntryBy(driverId);
	    StateValue<Double> driverCapacity = null;
	    driverCapacity = ((FloatState) DriverRes.getStateVariableBy("Capacity")).getValueAt(pickupDate);
	    driverCapacity.setValue(1.0);
	    logger.debug("OrderiD choosen :" + orderNetId + "  truckid chosen " + selectedTruck + " driverid" + driverId + "  /capcaity  : " + driverCapacity);
	    boolean isOSDoneLocally = ((BooleanMapParameter) order.getParameterBy(GigaConstants.ORDER_PARAM_ISOSDONELOCALLY)).getValue().get(resource.getId());
	    if (isOSDoneLocally) {
		long estimatedPickupTime = 0L;
		String endTaskLoc = task.getParameterBy("to").getValueAsString();
		long buffer1 = loadingBuffer;
		long buffer2 = unloadingBuffer;
		long totalTripDur = end - start;
		long twoHrs = 7200000L;
		long fourHrs = 14400000L;
		long eightHrs = 28800000L;
		long elevenHrs = 39600000L;
		if (totalTripDur <= twoHrs + buffer1 + buffer2) {
		    estimatedPickupTime = start + currToPickupDur;
		    restHr = 0L;
		    createAndAddStringChange(task, start, this.Location, CurrLoc);
		    createAndAddStringChange(task, estimatedPickupTime, this.Location, pickupLocation);
		    createAndAddStringChange(task, estimatedPickupTime + buffer1, this.Location, pickupLocation);
		    createAndAddStringChange(task, end - buffer2, this.Location, endTaskLoc);
		    createAndAddStringChange(task, end, this.Location, endTaskLoc);
		    createAndAddFloatChange(task, start, this.Capacity, quantity);
		    createAndAddFloatChange(task, end, this.Capacity, -quantity);
		}
		if ((totalTripDur > twoHrs + buffer1 + buffer2) && (totalTripDur <= fourHrs + buffer1 + buffer2 + restHrs4)) {
		    restHr = restHrs4;
		    if (start + currToPickupDur < twoHrs) {
			estimatedPickupTime = start + currToPickupDur;
			createAndAddStringChange(task, start, this.Location, CurrLoc);
			createAndAddStringChange(task, estimatedPickupTime, this.Location, pickupLocation);
			createAndAddStringChange(task, estimatedPickupTime + buffer1, this.Location, pickupLocation);
			createAndAddStringChange(task, end - buffer2, this.Location, endTaskLoc);
			createAndAddStringChange(task, end, this.Location, endTaskLoc);
			createAndAddFloatChange(task, start, this.Capacity, quantity);
			createAndAddFloatChange(task, end, this.Capacity, -quantity);
		    } else if (start + currToPickupDur > twoHrs) {
			estimatedPickupTime = start + currToPickupDur + restHrs4;
			createAndAddStringChange(task, start, this.Location, CurrLoc);
			createAndAddStringChange(task, estimatedPickupTime, this.Location, pickupLocation);
			createAndAddStringChange(task, estimatedPickupTime + buffer1, this.Location, pickupLocation);
			createAndAddStringChange(task, end - buffer2, this.Location, endTaskLoc);
			createAndAddStringChange(task, end, this.Location, endTaskLoc);
			createAndAddFloatChange(task, start, this.Capacity, quantity);
			createAndAddFloatChange(task, end, this.Capacity, -quantity);
		    }
		}
		if ((totalTripDur > fourHrs + buffer1 + buffer2 + restHrs4) && (totalTripDur <= eightHrs + buffer1 + buffer2 + restHrs8)) {
		    restHr = restHrs8;
		    if (start + currToPickupDur < fourHrs) {
			estimatedPickupTime = start + currToPickupDur;
			createAndAddStringChange(task, start, this.Location, CurrLoc);
			createAndAddStringChange(task, estimatedPickupTime, this.Location, pickupLocation);
			createAndAddStringChange(task, estimatedPickupTime + buffer1, this.Location, pickupLocation);
			createAndAddStringChange(task, end - buffer2, this.Location, endTaskLoc);
			createAndAddStringChange(task, end, this.Location, endTaskLoc);
			createAndAddFloatChange(task, start, this.Capacity, quantity);
			createAndAddFloatChange(task, end, this.Capacity, -quantity);
		    } else if (start + currToPickupDur > fourHrs) {
			estimatedPickupTime = start + currToPickupDur + restHrs8;
			createAndAddStringChange(task, start, this.Location, CurrLoc);
			createAndAddStringChange(task, estimatedPickupTime, this.Location, pickupLocation);
			createAndAddStringChange(task, estimatedPickupTime + buffer1, this.Location, pickupLocation);
			createAndAddStringChange(task, end - buffer2, this.Location, endTaskLoc);
			createAndAddStringChange(task, end, this.Location, endTaskLoc);
			createAndAddFloatChange(task, start, this.Capacity, quantity);
			createAndAddFloatChange(task, end, this.Capacity, -quantity);
		    }
		}
		//if ((totalTripDur > eightHrs + buffer1 + buffer2 + restHrs8) && (totalTripDur <= elevenHrs + buffer1 + buffer2 + restHrs111 + restHrs211)) {
		if ((totalTripDur > eightHrs + buffer1 + buffer2 + restHrs8)) {
		    restHr = restHrs111 + restHrs211;
		    if (start + currToPickupDur < fourHrs) {
			estimatedPickupTime = start + currToPickupDur;
			createAndAddStringChange(task, start, this.Location, CurrLoc);
			createAndAddStringChange(task, estimatedPickupTime, this.Location, pickupLocation);
			createAndAddStringChange(task, estimatedPickupTime + buffer1, this.Location, pickupLocation);
			createAndAddStringChange(task, end - buffer2, this.Location, endTaskLoc);
			createAndAddStringChange(task, end, this.Location, endTaskLoc);
			createAndAddFloatChange(task, start, this.Capacity, quantity);
			createAndAddFloatChange(task, end, this.Capacity, -quantity);
		    } else if (start + currToPickupDur > fourHrs) {
			estimatedPickupTime = start + currToPickupDur + restHrs111;
			createAndAddStringChange(task, start, this.Location, CurrLoc);
			createAndAddStringChange(task, estimatedPickupTime, this.Location, pickupLocation);
			createAndAddStringChange(task, estimatedPickupTime + buffer1, this.Location, pickupLocation);
			createAndAddStringChange(task, end - buffer2, this.Location, endTaskLoc);
			createAndAddStringChange(task, end, this.Location, endTaskLoc);
			createAndAddFloatChange(task, start, this.Capacity, quantity);
			createAndAddFloatChange(task, end, this.Capacity, -quantity);
		    } else if (start + currToPickupDur > eightHrs) {
			estimatedPickupTime = start + currToPickupDur + restHrs111 + restHrs211;
			createAndAddStringChange(task, start, this.Location, CurrLoc);
			createAndAddStringChange(task, estimatedPickupTime, this.Location, pickupLocation);
			createAndAddStringChange(task, estimatedPickupTime + buffer1, this.Location, pickupLocation);
			createAndAddStringChange(task, end - buffer2, this.Location, endTaskLoc);
			createAndAddStringChange(task, end, this.Location, endTaskLoc);
			createAndAddFloatChange(task, start, this.Capacity, quantity);
			createAndAddFloatChange(task, end, this.Capacity, -quantity);
		    }
		}
		StringParameter driver = (StringParameter) resource.getParameterBy("driverId");
		String driverRes = driver.getValueAsString();
		logger.debug("truckDriver: " + driverRes);
		StringParameter preferDriver = (StringParameter) order.getParameterBy("driverId");
		preferDriver.setValue(driverRes);
		logger.debug("orderDriver :" + preferDriver.getValueAsString());
		Date pickdate = new Date(estimatedPickupTime);
		SimpleDateFormat df2 = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
		String pickupDateText = df2.format(pickdate);
		StringParameter estPickupTime = (StringParameter) order.getParameterBy("estPickupTime");
		estPickupTime.setValue(pickupDateText);
		logger.debug("estPickupTime :" + pickupDateText);
		Date Enddate = new Date(end);
		String endtdateText = df2.format(Enddate);
		StringParameter estDeliveryTime = (StringParameter) order.getParameterBy("estDeliveryTime");
		estDeliveryTime.setValue(endtdateText);
		logger.debug("estDeliveryTime :" + endtdateText);
		long travelTime = end - start;
		Integer travelTimeT = (int) (travelTime / 60000);
		String travelTimeText = travelTimeT.toString();
		logger.debug("TravelTime: " + travelTimeT);
		String remarks2 = order.getParameterBy("remarks").getValueAsString();
		logger.debug("remarks2 :" + remarks2);
		StringParameter estTravelTime = (StringParameter) order.getParameterBy("estTravelTime");
		estTravelTime.setValue(travelTimeText);
		double precedingDm = currToPickupDist;
		StringParameter preceding_DM = (StringParameter) order.getParameterBy("preceding_DM");
		preceding_DM.setValue(Double.toString(precedingDm));
		StringParameter succeeding_DM = (StringParameter) order.getParameterBy("succeeding_DM");
		succeeding_DM.setValue(Double.toString(deliveryToBasekm));
		Integer travelDur = (int) (tripDuration / 60000);
		StringParameter travel_duration = (StringParameter) order.getParameterBy("travel_Duration");
		travel_duration.setValue(Integer.toString(travelDur));
		StringParameter loadBuffer = (StringParameter) order.getParameterBy("loading_unloading_timeBuffer");
		loadBuffer.setValue(Double.toString(90 + (noOfDrops - 1) * (loadingBuffer / 60000L)));
		Integer restDur = (int) (restHr / 60000);
		StringParameter restWaitBuffer = (StringParameter) order.getParameterBy("rest_Waiting_timeBuffer");
		restWaitBuffer.setValue(Integer.toString(restDur));
		if (CurrLoc.equalsIgnoreCase(BaseLocation)) {
		    String baseStartDateText = df2.format(new Date(start));
		    StringParameter baseLocStartTime = (StringParameter) order.getParameterBy("base_location_StartTime");
		    baseLocStartTime.setValue(baseStartDateText);
		}
	    }
	    if (customerType.contains("Outstation") && !isOSDoneLocally) {
		if (order.getParameterBy(GigaConstants.ORD_PARAM_BUSINESS_DIVISION).getValueAsString().equalsIgnoreCase(GigaConstants.BUSINESS_DIVISION_EAST_MALAYSIA)) {
		    Date Startdate = new Date(start);
		    logger.debug("startTime:" + Startdate);
		    long estimatedPickupTime = start + currToPickupDur;
		    long estimatedDeliveryTime = end;
		    logger.debug("CurrLoc:" + CurrLoc);
		    createAndAddStringChange(task, start, this.Location, CurrLoc);
		    createAndAddStringChange(task, estimatedPickupTime, this.Location, pickupLocation);
		    createAndAddStringChange(task, estimatedPickupTime + loadingBuffer, this.Location, pickupLocation);
		    createAndAddStringChange(task, estimatedDeliveryTime - unloadingBuffer, this.Location, deliveryLocation);
		    createAndAddStringChange(task, estimatedDeliveryTime, this.Location, deliveryLocation);
		    createAndAddStringChange(task, end, this.Location, deliveryLocation);
		    createAndAddFloatChange(task, start, this.Capacity, quantity);
		    createAndAddFloatChange(task, end, this.Capacity, -quantity);
		    restHr = (end - unloadingBuffer) - (estimatedPickupTime + loadingBuffer);
		    StringParameter driver = (StringParameter) resource.getParameterBy("driverId");
		    String driverRes = driver.getValueAsString();
		    logger.debug("truckDriver: " + driverRes);
		    StringParameter preferDriver = (StringParameter) order.getParameterBy("driverId");
		    preferDriver.setValue(driverRes);
		    logger.debug("orderDriver :" + preferDriver.getValueAsString());
		    Date pickdate = new Date(estimatedPickupTime);
		    SimpleDateFormat df2 = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
		    String pickupDateText = df2.format(pickdate);
		    StringParameter estPickupTime = (StringParameter) order.getParameterBy("estPickupTime");
		    estPickupTime.setValue(pickupDateText);
		    logger.debug("estPickupTime :" + estPickupTime.getValueAsString());
		    Date Enddate = new Date(end);
		    String endtdateText = df2.format(Enddate);
		    StringParameter estDeliveryTime = (StringParameter) order.getParameterBy("estDeliveryTime");
		    estDeliveryTime.setValue(endtdateText);
		    logger.debug("estDeliveryTime :" + estDeliveryTime.getValueAsString());
		    long travelTime = end - start;
		    Integer travelTimeT = (int) (travelTime / 60000);
		    String travelTimeText = Integer.toString(travelTimeT);
		    StringParameter estTravelTime = (StringParameter) order.getParameterBy("estTravelTime");
		    estTravelTime.setValue(travelTimeText);
		    double precedingDm = currToPickupDist;
		    StringParameter preceding_DM = (StringParameter) order.getParameterBy("preceding_DM");
		    preceding_DM.setValue(Double.toString(precedingDm));
		    StringParameter succeeding_DM = (StringParameter) order.getParameterBy("succeeding_DM");
		    succeeding_DM.setValue(Integer.toString(0));
		    Integer travelDur = (int) (tripDuration / 60000);
		    StringParameter travel_duration = (StringParameter) order.getParameterBy("travel_Duration");
		    travel_duration.setValue(Integer.toString(travelDur));
		    StringParameter loadBuffer = (StringParameter) order.getParameterBy("loading_unloading_timeBuffer");
		    loadBuffer.setValue(Double.toString(90 + (noOfDrops - 1) * (loadingBuffer / 60000L)));
		    Integer restDur = (int) (restHr / 60000);
		    StringParameter restWaitBuffer = (StringParameter) order.getParameterBy("rest_Waiting_timeBuffer");
		    restWaitBuffer.setValue(Integer.toString(restDur));
		    String baseStartDateText = df2.format(new Date(end - unloadingBuffer));
		    StringParameter baseLocStartTime = (StringParameter) order.getParameterBy("base_location_StartTime");
		    baseLocStartTime.setValue(baseStartDateText);
		} else {
		    String IntermediateBase;
		    long pickupToBase;
		    long BaseToDelivery;
		    String base1 = RSPContainerHelper.getResourceMap(true).getEntryBy("base_west").getParameterBy("base1").getValueAsString();
		    String base2 = RSPContainerHelper.getResourceMap(true).getEntryBy("base_west").getParameterBy("base2").getValueAsString();
		    if (order.getParameterBy(GigaConstants.ORD_PARAM_BUSINESS_DIVISION).getValueAsString().equalsIgnoreCase(GigaConstants.BUSINESS_DIVISION_KUCHING)) {
			base1 = GigaConstants.BASE_LOCATION_KUCHING;
			base2 = GigaConstants.BASE_LOCATION_KUCHING;
		    }
		    Resource base1Dynamic = RSPContainerHelper.getResourceMap(true).getEntryBy(base1);
		    Resource delDynamic = RSPContainerHelper.getResourceMap(true).getEntryBy(deliveryLocation);
		    long pickupToBase1Dur;
		    long base1ToDelDur;
		    Resource base2Dynamic = RSPContainerHelper.getResourceMap(true).getEntryBy(base2);
		    long pickupToBase2Dur;
		    long base2ToDelDur;
		    if (durationCalculator < 3) {
			if (pickupDynamic.getParameterBy(base1Dynamic.getId()) == null) {
			    String[] pickupToBase1Duration = GigaUtils.getDynamicDuration(pickupDynamic, base1Dynamic).split(",");
			    pickupToBase1Dur = Long.parseLong(pickupToBase1Duration[1]);
			    List<Double> listDistDur = new ArrayList<Double>();
			    listDistDur.add(Double.parseDouble(pickupToBase1Duration[0]));
			    listDistDur.add(Double.parseDouble(pickupToBase1Duration[1]));
			    FloatListParameter newDest = new FloatListParameter(base1Dynamic.getId(), base1Dynamic.getId(), listDistDur);
			    pickupDynamic.addParameter(newDest);
			} else {
			    pickupToBase1Dur = GigaUtils.getDuration(pickupDynamic.getId(), base1Dynamic.getId());
			}
			if (base1Dynamic.getParameterBy(delDynamic.getId()) == null) {
			    String[] base1ToDelDurDuration = GigaUtils.getDynamicDuration(base1Dynamic, delDynamic).split(",");
			    base1ToDelDur = Long.parseLong(base1ToDelDurDuration[1]);
			    List<Double> listDistDur = new ArrayList<Double>();
			    listDistDur.add(Double.parseDouble(base1ToDelDurDuration[0]));
			    listDistDur.add(Double.parseDouble(base1ToDelDurDuration[1]));
			    FloatListParameter newDest = new FloatListParameter(delDynamic.getId(), delDynamic.getId(), listDistDur);
			    base1Dynamic.addParameter(newDest);
			} else {
			    base1ToDelDur = GigaUtils.getDuration(base1Dynamic.getId(), delDynamic.getId());
			}
			if (pickupDynamic.getParameterBy(base2Dynamic.getId()) == null) {
			    String[] pickupToBase2Duration = GigaUtils.getDynamicDuration(pickupDynamic, base2Dynamic).split(",");
			    pickupToBase2Dur = Long.parseLong(pickupToBase2Duration[1]);
			    List<Double> listDistDur = new ArrayList<Double>();
			    listDistDur.add(Double.parseDouble(pickupToBase2Duration[0]));
			    listDistDur.add(Double.parseDouble(pickupToBase2Duration[1]));
			    FloatListParameter newDest = new FloatListParameter(base2Dynamic.getId(), base2Dynamic.getId(), listDistDur);
			    pickupDynamic.addParameter(newDest);
			} else {
			    pickupToBase2Dur = GigaUtils.getDuration(pickupDynamic.getId(), base2Dynamic.getId());
			}
			if (base2Dynamic.getParameterBy(delDynamic.getId()) == null) {
			    String[] base2ToDelDuration = GigaUtils.getDynamicDuration(base2Dynamic, delDynamic).split(",");
			    base2ToDelDur = Long.parseLong(base2ToDelDuration[1]);
			    List<Double> listDistDur = new ArrayList<Double>();
			    listDistDur.add(Double.parseDouble(base2ToDelDuration[0]));
			    listDistDur.add(Double.parseDouble(base2ToDelDuration[1]));
			    FloatListParameter newDest = new FloatListParameter(delDynamic.getId(), delDynamic.getId(), listDistDur);
			    base2Dynamic.addParameter(newDest);
			} else {
			    base2ToDelDur = GigaUtils.getDuration(base2Dynamic.getId(), delDynamic.getId());
			}
		    } else {
			base1 = ((Resource) RSPContainerHelper.getResourceMap(true).getEntryBy("base_west")).getParameterBy("base1").getValueAsString();
			pickupToBase1Dur = GigaUtils.getDuration(pickupLocation, base1);
			base1ToDelDur = GigaUtils.getDuration(base1, deliveryLocation);
			base2 = ((Resource) RSPContainerHelper.getResourceMap(true).getEntryBy("base_west")).getParameterBy("base2").getValueAsString();
			pickupToBase2Dur = GigaUtils.getDuration(pickupLocation, base2);
			base2ToDelDur = GigaUtils.getDuration(base2, deliveryLocation);
		    }
		    long dif = Math.min(base1ToDelDur, base2ToDelDur);
		    if (dif == base1ToDelDur) {
			IntermediateBase = base1;
			pickupToBase = pickupToBase1Dur;
			BaseToDelivery = base1ToDelDur;
		    } else {
			IntermediateBase = base2;
			pickupToBase = pickupToBase2Dur;
			BaseToDelivery = base2ToDelDur;
		    }
		    Date Startdate = new Date(start);
		    logger.debug("startTime:" + Startdate);
		    long estimatedPickupTime = start + currToPickupDur;
		    long estimatedDeliveryTime = end;
		    logger.debug("CurrLoc:" + CurrLoc);
		    if (order.getParameterBy(GigaConstants.ORD_PARAM_BUSINESS_DIVISION).getValueAsString().equalsIgnoreCase(GigaConstants.BUSINESS_DIVISION_KUCHING)) {
			Double truckDrivingFactor = ((FloatParameter) giga_parameters.getParameterBy(GigaConstants.GIGA_PARAM_TRUCK_DRIVING_FACTOR)).getValue();
			pickupToBase = (long) (pickupToBase / truckDrivingFactor);
			BaseToDelivery = (long) (BaseToDelivery / truckDrivingFactor);
		    }
		    createAndAddStringChange(task, start, this.Location, CurrLoc);
		    createAndAddStringChange(task, estimatedPickupTime, this.Location, pickupLocation);
		    createAndAddStringChange(task, estimatedPickupTime + loadingBuffer, this.Location, pickupLocation);
		    createAndAddStringChange(task, estimatedPickupTime + loadingBuffer + pickupToBase, this.Location, IntermediateBase);
		    createAndAddStringChange(task, estimatedDeliveryTime - unloadingBuffer - BaseToDelivery, this.Location, IntermediateBase);
		    createAndAddStringChange(task, estimatedDeliveryTime - unloadingBuffer, this.Location, deliveryLocation);
		    createAndAddStringChange(task, estimatedDeliveryTime, this.Location, deliveryLocation);
		    createAndAddStringChange(task, end, this.Location, deliveryLocation);
		    createAndAddFloatChange(task, start, this.Capacity, quantity);
		    createAndAddFloatChange(task, end, this.Capacity, -quantity);
		    restHr = (end - unloadingBuffer - BaseToDelivery) - (estimatedPickupTime + loadingBuffer + pickupToBase);
		    StringParameter driver = (StringParameter) resource.getParameterBy("driverId");
		    String driverRes = driver.getValueAsString();
		    logger.debug("truckDriver: " + driverRes);
		    StringParameter preferDriver = (StringParameter) order.getParameterBy("driverId");
		    preferDriver.setValue(driverRes);
		    logger.debug("orderDriver :" + preferDriver.getValueAsString());
		    Date pickdate = new Date(estimatedPickupTime);
		    SimpleDateFormat df2 = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
		    String pickupDateText = df2.format(pickdate);
		    StringParameter estPickupTime = (StringParameter) order.getParameterBy("estPickupTime");
		    estPickupTime.setValue(pickupDateText);
		    logger.debug("estPickupTime :" + estPickupTime.getValueAsString());
		    Date Enddate = new Date(end);
		    String endtdateText = df2.format(Enddate);
		    StringParameter estDeliveryTime = (StringParameter) order.getParameterBy("estDeliveryTime");
		    estDeliveryTime.setValue(endtdateText);
		    logger.debug("estDeliveryTime :" + estDeliveryTime.getValueAsString());
		    long travelTime = end - start;
		    Integer travelTimeT = (int) (travelTime / 60000);
		    String travelTimeText = Integer.toString(travelTimeT);
		    StringParameter estTravelTime = (StringParameter) order.getParameterBy("estTravelTime");
		    estTravelTime.setValue(travelTimeText);
		    double precedingDm = currToPickupDist;
		    StringParameter preceding_DM = (StringParameter) order.getParameterBy("preceding_DM");
		    preceding_DM.setValue(Double.toString(precedingDm));
		    StringParameter succeeding_DM = (StringParameter) order.getParameterBy("succeeding_DM");
		    succeeding_DM.setValue(Integer.toString(0));
		    Integer travelDur = (int) (tripDuration / 60000);
		    StringParameter travel_duration = (StringParameter) order.getParameterBy("travel_Duration");
		    travel_duration.setValue(Integer.toString(travelDur));
		    StringParameter loadBuffer = (StringParameter) order.getParameterBy("loading_unloading_timeBuffer");
		    loadBuffer.setValue(Double.toString(90 + (noOfDrops - 1) * (loadingBuffer / 60000L)));
		    Integer restDur = (int) (restHr / 60000);
		    StringParameter restWaitBuffer = (StringParameter) order.getParameterBy("rest_Waiting_timeBuffer");
		    restWaitBuffer.setValue(Integer.toString(restDur));
		    String baseStartDateText = df2.format(new Date(end - unloadingBuffer - BaseToDelivery));
		    StringParameter baseLocStartTime = (StringParameter) order.getParameterBy("base_location_StartTime");
		    baseLocStartTime.setValue(baseStartDateText);
		}
	    }
	}
    }

    private long getTime(long date, String time) {
	if (time == null || time.equalsIgnoreCase("?"))
	    time = "00:00:00";
	long result = 0;
	Date Date = new Date(date);
	SimpleDateFormat df = new SimpleDateFormat("MM/dd/yyyy");
	SimpleDateFormat df1 = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
	String DateText = df.format(Date);
	StringWriter s = new StringWriter();
	s.append(DateText);
	s.append(" " + time);
	try {
	    Date dateTime = df1.parse(s.toString());
	    result = dateTime.getTime();
	} catch (ParseException e) {
	    e.printStackTrace();
	}
	return result;
    }

    protected static String getCurrentLocation(Resource truckRes) {
	String currentLoc = null;
	List<Activity> prevTasks = truckRes.getActivitiesInInterval(Long.MIN_VALUE, Long.MAX_VALUE);
	if (prevTasks.size() < 1) {
	    StateValue<?> currentLocValue = truckRes.getStateVariableBy("Location").getValueAt(Long.MAX_VALUE);
	    currentLoc = currentLocValue.getValueAsString();
	} else {
	    Activity lastAct = prevTasks.get(prevTasks.size() - 1);
	    String orderId = lastAct.getOrderNetId();
	    Order prevOrder = RSPContainerHelper.getOrderMap(true).getEntryBy(orderId);
	    String OrderType = prevOrder.getType();
	    if (OrderType.equalsIgnoreCase("Maintenance")) {
		currentLoc = truckRes.getStateVariableBy("Location").getValueAt(lastAct.getEnd()).getValueAsString();
	    } else {
		currentLoc = prevOrder.getParameterBy("deliveryLocation").getValueAsString();
		if (prevOrder.getParameterBy(GigaConstants.ORD_PARAM_JOB_TYPE).getValueAsString().equalsIgnoreCase("Outstation")) {
		    long prevOrderStartTime = lastAct.getStart() - 2 * 60000L;
		    currentLoc = truckRes.getStateVariableBy("Location").getValueAt(prevOrderStartTime).getValueAsString();
		}
	    }
	}
	return currentLoc;
    }

    public String convertLongToTime(long dateToConvert) {
	String result = null;
	SimpleDateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
	result = df.format(dateToConvert);
	return result;
    }

    public String convertDurationToTime(String durationToConvert) {
	String result = durationToConvert;
	result = (((result.replace("PT", "")).replace("H", " Hours ")).replace("M", " Minutes ")).replace("S", " Seconds ");
	return result;
    }

    public void validate(IPolicyContainer policyContainer) {
	super.validate(policyContainer);
	Resource re = (Resource) policyContainer;
	evaluateBounds(re);
    }
}