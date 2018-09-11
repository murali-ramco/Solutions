package com.ramco.giga.formula;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.ramco.giga.constant.GigaConstants;
import com.ramco.giga.utils.GigaUtils;
import com.rsp.core.base.RSPContainerHelper;
import com.rsp.core.base.exception.DataModelException;
import com.rsp.core.base.exception.RSPException;
import com.rsp.core.base.formula.LookupFormula;
import com.rsp.core.base.model.Activity;
import com.rsp.core.base.model.ElementMap;
import com.rsp.core.base.model.Order;
import com.rsp.core.base.model.ParameterizedElement;
import com.rsp.core.base.model.Resource;
import com.rsp.core.base.model.constants.PlanningState;
import com.rsp.core.base.model.parameter.BooleanParameter;
import com.rsp.core.base.model.parameter.DateParameter;
import com.rsp.core.base.model.parameter.IntegerParameter;
import com.rsp.core.base.model.parameter.Parameter;
import com.rsp.core.base.model.parameter.StringListParameter;
import com.rsp.core.base.model.parameter.StringParameter;
import com.rsp.core.base.model.stateVariable.StateValue;
import com.rsp.core.i18n.RSPMessages;

public class TruckSelectionFormula extends LookupFormula {
    private static final long serialVersionUID = 1L;
    public static List<Resource> truckWithoutDrivers = new ArrayList<Resource>();

    public Parameter<?> evaluate() throws RSPException {
	StringListParameter resultParam = null;
	String orderedTruckType = null;
	String truckType = null;
	String truckMake = null;
	String orderJobType;
	String[] args = this.argument.split(",");
	String resType = null;
	boolean isForceLocal = false;
	boolean isForceLocalDriver = false;
	String truckMakeFromOrder = null;
	String resParam = null;
	String TRUCK = "truck_";
	if (args.length >= 3) {
	    resType = args[0].trim();
	    truckMakeFromOrder = args[1].trim();
	    resParam = args[2].trim();
	} else {
	    DataModelException e = new DataModelException("Malformed argument $argument for formula $className", "datamodel.formulaArgument");
	    e.addProperty("argument", this.argument);
	    e.addProperty("className", getClass().getSimpleName());
	    throw e;
	}
	if (truckMakeFromOrder.startsWith("$")) {
	    truckMakeFromOrder = getKeyFromParameter(truckMakeFromOrder);
	}
	ElementMap<Resource> resourceMap = RSPContainerHelper.getResourceMap(true);
	List<Resource> trucksResList = new ArrayList<Resource>();
	ParameterizedElement tmp = getElement();
	String orderNetId = null;
	while (tmp != null) {
	    orderNetId = tmp.getId();
	    tmp = tmp.getParent();
	}
	Order order = (Order) RSPContainerHelper.getOrderMap(true).getEntryBy(orderNetId);
	logger.info("Planning started for order: " + order.getId());
	orderJobType = order.getParameterBy("jobType").getValueAsString();
	resType = TRUCK.concat(order.getParameterBy(GigaConstants.ORD_PARAM_BUSINESS_DIVISION).getValueAsString());
	Collection<Resource> truckResList = resourceMap.getByType(resType);
	StringParameter remarksLog = new StringParameter("remarksLog", "planningLog", "");
	order.addParameter(remarksLog);
	StringParameter remarks = (StringParameter) order.getParameterBy("remarks");
	Resource gigaParameters = RSPContainerHelper.getResourceMap(true).getEntryBy(GigaConstants.RES_GIGA_PARAM);
	double allowToyotaForOthers = (Double) gigaParameters.getParameterBy(GigaConstants.ALLOW_TOYOTA_OTHERS).getValue();
	double allowIsuzuForOthers = (Double) gigaParameters.getParameterBy(GigaConstants.ALLOW_ISUZU_OTHERS).getValue();
	//long pickupTime = ((DateParameter) order.getParameterBy(GigaConstants.ORD_PARAM_PICKUP_DATE)).getValue().longValue();
	long deliveryTime = ((DateParameter) order.getParameterBy(GigaConstants.ORD_PARAM_DELIVERY_DATE)).getValue().longValue();
	boolean isArticulatedAllowed = GigaUtils.isEntryAllowed(order, GigaConstants.TRUCK_TYPE_ARTICULATED);
	boolean isRigidAllowed = GigaUtils.isEntryAllowed(order, GigaConstants.TRUCK_TYPE_RIGID);
	//boolean isSingleAllowed = isEntryAllowed(order, TRUCK_TYPE_SINGLE);
	orderedTruckType = order.getParameterBy(GigaConstants.ORD_PARAM_TRUCK_TYPE).getValueAsString();
	if (orderedTruckType == null) {
	    remarks.setValue("Parameter with id " + orderedTruckType + " is not found Order with id " + orderNetId);
	    throw RSPMessages.newRspMsgEx("Parameter with id " + orderedTruckType + " is not found Order with id " + orderNetId);
	}
	//Resource currLatLong = resourceMap.getEntryBy(GigaConstants.RES_TRUCK_LAT_LONG);
	String pickup = order.getParameterBy(GigaConstants.ORD_PARAM_PICKUP_LOCATION).getValueAsString();
	Resource pickupLocationRes = resourceMap.getEntryBy(pickup);// Resource
	String delivery = order.getParameterBy(GigaConstants.ORD_PARAM_DELIVERY_LOATION).getValueAsString();
	Resource deliveryLocationRes = resourceMap.getEntryBy(delivery);// Resource
	if (pickupLocationRes == null) {
	    remarks.setValue("Pickup Location with id " + pickup + " is not available in Routes");
	    throw RSPMessages.newRspMsgEx("Pickup Location with id " + pickup + " is not available in Routes");
	}
	//FloatListParameter deliveryLoc = (FloatListParameter) pickupLocationRes.getParameterBy(delivery);
	if (deliveryLocationRes == null) {
	    remarks.setValue("Delivery Location with id " + delivery + " is not available in Routes");
	    throw RSPMessages.newRspMsgEx("Delivery Location with id " + delivery + " is not available in Routes");
	}
	List<String> toyotaDriverList = new ArrayList<String>();
	toyotaDriverList = GigaUtils.getToyotaDriversList();
	List<Resource> othersArticulatedTrucks = new ArrayList<Resource>();
	List<Resource> othersRigidTrucks = new ArrayList<Resource>();
	List<Resource> othersArticulatedWTTD = new ArrayList<Resource>();
	List<Resource> othersRigidWTTD = new ArrayList<Resource>();
	List<Resource> othersSingleTrucks = new ArrayList<Resource>();
	List<Resource> toyotaArticulatedTrucks = new ArrayList<Resource>();
	List<Resource> toyotaRigidTrucks = new ArrayList<Resource>();
	List<Resource> toyotaSingleTrucks = new ArrayList<Resource>();
	List<Resource> pekanTrucks = new ArrayList<Resource>();
	List<Resource> isuzuArticulatedTrucks = new ArrayList<Resource>();
	List<Resource> isuzuRigidTrucks = new ArrayList<Resource>();
	List<Resource> isuzuSingleTrucks = new ArrayList<Resource>();
	String driverId = null;
	String prevOSOrderId = null;
	int prevOSorderNoOfDrops = 0;
	int prevOSorderNoOfDropsCompleted = 0;
	boolean isDelayedOutstationDelivery = false;
	for (Resource truckResource : truckResList) {
	    truckType = truckResource.getParameterBy(GigaConstants.ORD_PARAM_TRUCK_TYPE).getValueAsString();
	    truckMake = truckResource.getParameterBy(resParam).getValueAsString();
	    if (gigaParameters.hasParameter(GigaConstants.GIGA_PARAM_DELAYED_OS_DELIVERY))
		isDelayedOutstationDelivery = ((BooleanParameter) gigaParameters.getParameterBy(GigaConstants.GIGA_PARAM_DELAYED_OS_DELIVERY)).getValue();
	    if (isDelayedOutstationDelivery) {
		if (truckResource.hasParameter(GigaConstants.PARAM_PREV_OS_ORDER_ID)) {
		    prevOSOrderId = truckResource.getParameterBy(GigaConstants.PARAM_PREV_OS_ORDER_ID).getValueAsString();
		    if (truckResource.hasParameter(GigaConstants.PARAM_PREV_OS_ORDER_NO_OF_DROPS)) {
			prevOSorderNoOfDrops = ((IntegerParameter) truckResource.getParameterBy(GigaConstants.PARAM_PREV_OS_ORDER_NO_OF_DROPS)).getValue();
		    }
		    if (prevOSorderNoOfDrops > 1) {
			Resource prevOSLoadStatusRes = resourceMap.getEntryBy(GigaConstants.RES_PREV_OS_LOAD_STATUS);
			if (prevOSLoadStatusRes != null) {
			    if (prevOSLoadStatusRes.hasParameter(prevOSOrderId)) {
				prevOSorderNoOfDropsCompleted = ((IntegerParameter) prevOSLoadStatusRes.getParameterBy(prevOSOrderId)).getValue();
			    }
			    if (prevOSorderNoOfDropsCompleted < (prevOSorderNoOfDrops - 1)) {
				continue;
			    }
			} else {
			    continue;
			}
		    }
		}
	    }
	    if (truckMakeFromOrder.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_TOYOTA) && (orderJobType.equals(GigaConstants.ORDER_TYPE_OUTSTATION))) {
		if (truckResource.getActivitiesInInterval(System.currentTimeMillis(), java.lang.Long.MAX_VALUE).size() > 0) {
		    continue;
		}
	    }
	    //  isForceLocal = Boolean.valueOf(truckResource.getParameterBy(GigaConstants.FORCELOCAL).getValueAsString());
	    if (truckResource.hasParameter(GigaConstants.FORCELOCAL)) {
		isForceLocal = ((BooleanParameter) truckResource.getParameterBy(GigaConstants.FORCELOCAL)).getValue();
	    }
	    String truckDriverId = truckResource.getParameterBy("driverId").getValueAsString();
	    logger.debug("truck ID: " + truckResource.getId());
	    logger.debug("driver ID: " + truckDriverId);
	    Resource driverRes = (Resource) RSPContainerHelper.getResourceMap(true).getEntryBy(truckDriverId);
	    logger.debug("driver ID: " + driverRes.getId());
	    if (driverRes.hasParameter(GigaConstants.FORCELOCAL)) {
		//	isForceLocalDriver = Boolean.valueOf(driverRes.getParameterBy(GigaConstants.FORCELOCAL).getValueAsString());
		isForceLocalDriver = ((BooleanParameter) driverRes.getParameterBy(GigaConstants.FORCELOCAL)).getValue();
	    }
	    logger.debug("isForceLocalDriver for driver " + driverRes.getId() + "is :" + isForceLocalDriver);
	    if ((orderJobType.equals(GigaConstants.ORDER_TYPE_OUTSTATION) && isForceLocal) || (orderJobType.equals(GigaConstants.ORDER_TYPE_OUTSTATION) && isForceLocalDriver)) {
		continue;
	    }
	    driverId = truckResource.getParameterBy(GigaConstants.RES_PARAM_DRIVERID).getValueAsString();
	    //String base = resource.getParameterBy(RES_PARAM_LOCATION).getValueAsString();
	    StateValue<?> currentLocValue = null;
	    String CurrentLoc = null;
	    /* if (truckResource.getParameterBy(GigaConstants.RES_PARAM_DRIVERID).getValueAsString().equalsIgnoreCase("TBA")) {
	    truckWithoutDrivers.add(truckResource);
	    continue;
	     }*/
	    //logger.debug(resource);
	    List<Activity> prevTasks = truckResource.getActivitiesInInterval(Long.MIN_VALUE, Long.MAX_VALUE);
	    if (prevTasks.size() < 1) {
		currentLocValue = truckResource.getStateVariableBy(GigaConstants.STATE_LOCATION).getValueAt(deliveryTime);
		if (currentLocValue == null)
		    continue;
		CurrentLoc = currentLocValue.getValueAsString();
		//logger.debug("currentLocValue " + CurrentLoc);
	    } else {
		for (Activity act : prevTasks) {
		    String orderId = act.getOrderNetId();
		    Order prevOrder = RSPContainerHelper.getOrderMap(true).getEntryBy(orderId);
		    String OrderType = prevOrder.getType();
		    logger.debug("prevOrder->" + orderId);
		    if (OrderType.equalsIgnoreCase(GigaConstants.ORD_TYPE_MAINTENANCE)) {
			logger.debug("It is a maintenance order");
			continue;
		    }
		    CurrentLoc = prevOrder.getParameterBy(GigaConstants.ORD_PARAM_DELIVERY_LOATION).getValueAsString();
		    if (prevOrder.getParameterBy(GigaConstants.ORD_PARAM_ORDER_TYPE).getValueAsString().contains(GigaConstants.ORDER_TYPE_OUTSTATION)) {
			long prevOrderStartTime = act.getStart() - 2 * 60000L;
			//logger.debug("prevOrderStartTime->" + new Date(prevOrderStartTime));
			currentLocValue = truckResource.getStateVariableBy(GigaConstants.STATE_LOCATION).getValueAt(prevOrderStartTime);
			if (currentLocValue != null)
			    CurrentLoc = currentLocValue.getValueAsString();
			break;
		    }
		}
		if (CurrentLoc == null)
		    continue;
		//logger.debug("currentLoc: " + CurrentLoc);
	    }
	    Resource currentLocRes = (Resource) resourceMap.getEntryBy(CurrentLoc);
	    logger.debug(truckResource.getId() + " CurrentLoc : " + currentLocRes);
	    if (currentLocRes == null)
		continue;
	    String Base = truckResource.getParameterBy(GigaConstants.RES_PARAM_LOCATION).getValueAsString();
	    if (Base.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_PEKAN) && !truckMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_ISUZU) && !truckMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_TOYOTA) && truckType.equalsIgnoreCase(orderedTruckType))
		pekanTrucks.add(truckResource);
	    if (!truckMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_ISUZU) && !truckMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_TOYOTA)) {
		if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_ARTICULATED)) {
		    if (toyotaDriverList.contains(driverId))
			othersArticulatedWTTD.add(truckResource);
		    else
			othersArticulatedTrucks.add(truckResource);
		} else if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_RIGID)) {
		    logger.debug(othersRigidWTTD);
		    if (toyotaDriverList.contains(driverId))
			othersRigidWTTD.add(truckResource);
		    else
			othersRigidTrucks.add(truckResource);
		} else if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_SINGLE))
		    othersSingleTrucks.add(truckResource);
	    }
	    if (truckMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_TOYOTA)) {
		if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_ARTICULATED))
		    toyotaArticulatedTrucks.add(truckResource);
		else if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_RIGID))
		    toyotaRigidTrucks.add(truckResource);
		else if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_SINGLE))
		    toyotaSingleTrucks.add(truckResource);
	    }
	    if (truckMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_ISUZU)) {
		if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_ARTICULATED)) {
		    /*if (toyotaDriverList.contains(driverId))
		    isuzuArticulatedWTTD.add(truckResource);
		    else*/
		    isuzuArticulatedTrucks.add(truckResource);
		} else if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_RIGID)) {
		    /*if (toyotaDriverList.contains(driverId))
		    isuzuRigidWTTD.add(truckResource);
		    else*/
		    isuzuRigidTrucks.add(truckResource);
		} else if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_SINGLE))
		    isuzuSingleTrucks.add(truckResource);
	    }
	    /*if (truckType.equalsIgnoreCase(orderedTruckType) && truckMake.equalsIgnoreCase(truckMakeFromOrder) && !truckMakeFromOrder.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_OTHERS)) {
	    trucksResList.add(truckResource);
	    } else if (truckType.equalsIgnoreCase(orderedTruckType) && truckMakeFromOrder.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_OTHERS) && !truckMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_ISUZU) && !truckMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_TOYOTA)) {
	    trucksResList.add(truckResource);
	    }*/
	}
	/*logger.debug("trucksResIds " + trucksResList.size());
	logger.debug("trucksResIds " + trucksResList);
	if (pekanTrucks.size() > 0 && !truckMakeFromOrder.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_ISUZU) && !truckMakeFromOrder.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_TOYOTA) && (pickupLocationRes.getName().equalsIgnoreCase(GigaConstants.EASTERN_REGION) || deliveryLocationRes.getName().equalsIgnoreCase(GigaConstants.EASTERN_REGION))) {
	    trucksResList = pekanTrucks;
	    logger.debug("pekan1");
	}
	if (!truckMakeFromOrder.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_ISUZU) && !truckMakeFromOrder.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_TOYOTA) && ((pickupLocationRes.getName().equalsIgnoreCase(GigaConstants.EASTERN_REGION) || deliveryLocationRes.getName().equalsIgnoreCase(GigaConstants.EASTERN_REGION)))) {
	    trucksResList = othersArticulatedTrucks;
	    logger.debug("pekan2");
	}*/
	if (orderedTruckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_ARTICULATED)) {// && truckMakeFromOrder.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_OTHERS)) {
	    trucksResList.addAll(othersArticulatedTrucks);
	    trucksResList.addAll(othersArticulatedWTTD);
	    if (allowToyotaForOthers == 1) {
		trucksResList.addAll(toyotaArticulatedTrucks);
	    }
	    if (allowIsuzuForOthers == 1) {
		trucksResList.addAll(isuzuArticulatedTrucks);
	    }
	} else if (orderedTruckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_RIGID)) {// && !truckMakeFromOrder.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_ISUZU) && !truckMakeFromOrder.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_TOYOTA)) {
	    trucksResList.addAll(othersRigidTrucks);
	    trucksResList.addAll(othersRigidWTTD);
	    if (allowToyotaForOthers == 1) {
		trucksResList.addAll(toyotaRigidTrucks);
	    }
	    if (othersArticulatedTrucks.size() > 0 && isArticulatedAllowed && GigaUtils.upgradeTruck())
		trucksResList.addAll(othersArticulatedTrucks);
	    if (allowToyotaForOthers == 1 && isArticulatedAllowed && GigaUtils.upgradeTruck()) {
		trucksResList.addAll(toyotaArticulatedTrucks);
	    }
	    if (allowIsuzuForOthers == 1 && isRigidAllowed) {
		trucksResList.addAll(isuzuRigidTrucks);
	    }
	    if (allowIsuzuForOthers == 1 && isArticulatedAllowed && GigaUtils.upgradeTruck()) {
		trucksResList.addAll(isuzuArticulatedTrucks);
	    }
	}
	// logger.debug("idleTrucks-size: "+idleTrucks.size()+"trucksResIds-size: "+trucksResIds.size());
	else if (orderedTruckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_SINGLE)) {// && !truckMakeFromOrder.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_ISUZU) && !truckMakeFromOrder.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_TOYOTA)) {
	    trucksResList.addAll(othersSingleTrucks);
	    if (GigaUtils.upgradeTruck()) {
		if (isRigidAllowed && GigaUtils.upgradeTruck())
		    trucksResList.addAll(othersRigidTrucks);
		if (allowToyotaForOthers == 1 && isRigidAllowed && GigaUtils.upgradeTruck()) {
		    trucksResList.addAll(toyotaRigidTrucks);
		}
		if (isArticulatedAllowed && GigaUtils.upgradeTruck())
		    trucksResList.addAll(othersArticulatedTrucks);
		if (allowToyotaForOthers == 1 && isArticulatedAllowed) {
		    trucksResList.addAll(toyotaArticulatedTrucks);
		}
		if (allowIsuzuForOthers == 1 && isRigidAllowed && GigaUtils.upgradeTruck()) {
		    trucksResList.addAll(isuzuRigidTrucks);
		}
		if (allowIsuzuForOthers == 1 && isArticulatedAllowed && GigaUtils.upgradeTruck()) {
		    trucksResList.addAll(isuzuArticulatedTrucks);
		}
	    }
	}
	logger.debug("trucksResIds : " + trucksResList);
	logger.debug("trucksResIds.size" + trucksResList.size());
	if (trucksResList.size() < 1) {
	    if (order.hasParameter(GigaConstants.ORD_PARAM_ORDER_DETAILS)) {
		((StringParameter) order.getParameterBy(GigaConstants.ORD_PARAM_ORDER_DETAILS)).setValue(order.getParameterBy(GigaConstants.ORD_PARAM_ORDER_DETAILS).getValueAsString().concat("Trucks with make ID: " + truckMakeFromOrder + " and type: " + orderedTruckType + " are not available for the day. "));
	    } else {
		StringParameter orderDetails = new StringParameter(GigaConstants.ORD_PARAM_ORDER_DETAILS, GigaConstants.ORD_PARAM_ORDER_DETAILS, "Trucks with make ID: " + truckMakeFromOrder + " and type: " + orderedTruckType + " are not available for the day. ");
		order.addParameter(orderDetails);
	    }
	    try {
		remarks.setValue("Trucks with make ID: " + truckMakeFromOrder + " and type: " + orderedTruckType + " are not available for the day. ");
		throw RSPMessages.newRspMsgEx("Trucks with make ID: " + truckMakeFromOrder + " and type: " + orderedTruckType + " are not available. ");
	    } catch (Exception e) {
		e.printStackTrace();
	    }
	}
	try {
	    Collection<Order> MaintenanceOrders = RSPContainerHelper.getOrderMap(true).getByType(GigaConstants.ORD_TYPE_MAINTENANCE);
	    List<String> mntTrucks = new ArrayList<String>();
	    for (Order mntOrder : MaintenanceOrders) {
		if (mntOrder.getState().equals(PlanningState.PLANNED))
		    mntTrucks.add(mntOrder.getParameterBy(GigaConstants.MNT_PARAM_TRUCK_NO).getValueAsString());
	    }
	    List<String> truckResIds = new ArrayList<String>();
	    for (Resource res : trucksResList) {
		if (!mntTrucks.contains(res.getId())) {
		    truckResIds.add(res.getId());
		}
	    }
	    logger.info(truckResIds);
	    StringListParameter resultParameter = new StringListParameter(this.descriptiveParameter.getId(), this.descriptiveParameter.getName(), truckResIds);
	    resultParameter.setHidden(this.descriptiveParameter.isHidden());
	    resultParameter.setUom(this.descriptiveParameter.getUom());
	    resultParameter.setInterpretation(this.descriptiveParameter.getInterpretation());
	    resultParameter.setSortIndex(this.descriptiveParameter.getSortIndex());
	    resultParameter.setParent(getElement());
	    resultParam = resultParameter;
	} catch (Exception e) {
	    e.printStackTrace();
	}
	logger.debug("END of truck formula");
	return resultParam;
    }
}