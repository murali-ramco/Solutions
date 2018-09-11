package com.ramco.giga.formula;

import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

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
import com.rsp.core.base.model.parameter.DateParameter;
import com.rsp.core.base.model.parameter.FloatListParameter;
import com.rsp.core.base.model.parameter.FloatParameter;
import com.rsp.core.base.model.parameter.Parameter;
import com.rsp.core.base.model.parameter.StringParameter;
import com.rsp.core.base.model.stateVariable.FloatState;
import com.rsp.core.base.model.stateVariable.StateValue;
import com.rsp.core.i18n.RSPMessages;

public class testFormula extends LookupFormula {
    private static final long serialVersionUID = 1L;
    public static List<Resource> truckWithoutDrivers = new ArrayList<Resource>();// trucks

    // with
    // no
    // primary
    // drivers
    // or
    // absent
    // drivers.
    @SuppressWarnings({ "unused", "deprecation" })
    public Parameter<?> evaluate() throws RSPException {
	StringParameter resultParam = null;
	String orderedType = null;
	String truckType = null;
	String resMake = null;
	String[] args = this.argument.split(",");
	String resType = null;
	String orderParam = null;
	String resParam = null;
	if (args.length >= 3) {
	    resType = args[0].trim();
	    orderParam = args[1].trim();
	    resParam = args[2].trim();
	} else {
	    DataModelException e = new DataModelException("Malformed argument $argument for formula $className", "datamodel.formulaArgument");
	    e.addProperty("argument", this.argument);
	    e.addProperty("className", getClass().getSimpleName());
	    throw e;
	}
	if (orderParam.startsWith("$")) {
	    orderParam = getKeyFromParameter(orderParam);
	}
	ElementMap<Resource> resourceMap = RSPContainerHelper.getResourceMap(true);
	Collection<Resource> resourcesByType = resourceMap.getByType(resType);
	List<Resource> trucksResIds = new ArrayList();
	ParameterizedElement tmp = getElement();
	String orderNetId = null;
	while (tmp != null) {
	    orderNetId = tmp.getId();
	    tmp = tmp.getParent();
	}
	Order order = (Order) RSPContainerHelper.getOrderMap(true).getEntryBy(orderNetId);
	Resource gigaParamRes = resourceMap.getEntryBy("giga_parameters");
	Double durationCalculator = ((FloatParameter) gigaParamRes.getParameterBy("durationCalculation")).getValue();
	// logger.info("orderNetId: " + orderNetId);
	StringParameter remarksLog = new StringParameter("remarksLog", "planningLog", "");
	order.addParameter(remarksLog);
	StringParameter remarks = (StringParameter) order.getParameterBy("remarks");
	Resource gigaParameters = RSPContainerHelper.getResourceMap(true).getEntryBy("giga_parameters");
	double allowToyotaForOthers = (Double) gigaParameters.getParameterBy("allowToyotaForOthers").getValue();
	double allowIsuzuForOthers = (Double) gigaParameters.getParameterBy("allowToyotaForOthers").getValue();
	long pickupTime = ((DateParameter) order.getParameterBy("pickupDate")).getValue().longValue();
	long deliveryTime = ((DateParameter) order.getParameterBy("deliveryDate")).getValue().longValue();
	boolean isArticulatedAllowed = isEntryAllowed(order, "articulated");
	boolean isRigidAllowed = isEntryAllowed(order, "rigid");
	boolean isSingleAllowed = isEntryAllowed(order, "single");
	// logger.info("pickupTime "+pickupTime);
	orderedType = order.getParameterBy("truckType").getValueAsString();
	if (orderedType == null) {
	    remarks.setValue("Parameter with id " + orderedType + " is not found Order with id " + orderNetId);
	    throw RSPMessages.newRspMsgEx("Parameter with id " + orderedType + " is not found Order with id " + orderNetId);
	}
	//ElementMap<Resource> resourceMap = RSPContainerHelper.getResourceMap(true);
	Resource currLatLong = resourceMap.getEntryBy("truckLatitudeLongitude");
	String pickup = order.getParameterBy("pickupLocation").getValueAsString();
	// logger.info("pickup: " +pickup);
	StringParameter pickupParam = (StringParameter) order.getParameterBy("pickupLocation"); // parameter
	// to
	// find
	// pickup
	// location from the order.
	/* @ check for the pickup location value.
	 * 
	 * @ modify the parameter value as mentioned in route matrix. */
	if (pickup.equalsIgnoreCase("Sitiawan"))
	    pickup = "SETIAWAN";
	if (pickup.equalsIgnoreCase("Alor_Star"))
	    pickup = "ALOR_SETAR";
	if (pickup.equalsIgnoreCase("JALAN_SKUDAI_LAMA_JOHOR_BAHRU"))
	    pickup = "JALAN_SKUDAI_LAMA";
	if (pickup.equalsIgnoreCase("JALAN_KLANG_LAMA_KUALA_LUMPUR"))
	    pickup = "JALAN_KELANG_LAMA";
	if (pickup.equalsIgnoreCase("WESTPORTS"))
	    pickup = "WEST_PORT";
	pickupParam.setValue(pickup);
	Resource pickupLocationRes = resourceMap.getEntryBy(pickup);// Resource
	// with Id
	// pickup
	// Location.
	String delivery = order.getParameterBy("deliveryLocation").getValueAsString();
	StringParameter deliveryParam = (StringParameter) order.getParameterBy("deliveryLocation");// parameter
	// to
	// find
	// delivery location from
	// the order.
	/* @ check for the delivery location value.
	 * 
	 * @ modify the parameter value as mentioned in route matrix. */
	if (delivery.equalsIgnoreCase("Sitiawan"))
	    delivery = "SETIAWAN";
	if (delivery.equalsIgnoreCase("Alor_Star"))
	    delivery = "ALOR_SETAR";
	if (delivery.equalsIgnoreCase("JALAN_SKUDAI_LAMA_JOHOR_BAHRU"))
	    delivery = "JALAN_SKUDAI_LAMA";
	if (delivery.equalsIgnoreCase("JALAN_KLANG_LAMA_KUALA_LUMPUR"))
	    delivery = "JALAN_KELANG_LAMA";
	if (delivery.equalsIgnoreCase("WESTPORTS"))
	    delivery = "WEST_PORT";
	deliveryParam.setValue(delivery);
	// logger.info("delivery: " +delivery);
	Resource deliveryLocationRes = resourceMap.getEntryBy(delivery);// Resource
	// with
	// Id
	// delivery
	// Location.
	// logger.info("pickupLocation is not null");
	if (pickupLocationRes == null) {
	    remarks.setValue("Pickup Location with id " + pickup + " is not available in Routes");
	    throw RSPMessages.newRspMsgEx("Pickup Location with id " + pickup + " is not available in Routes");
	}
	FloatListParameter deliveryLoc = (FloatListParameter) pickupLocationRes.getParameterBy(delivery);
	// logger.info("deliveryLoc is not null");
	if (deliveryLocationRes == null) {
	    remarks.setValue("Delivery Location with id " + delivery + " is not available in Routes");
	    throw RSPMessages.newRspMsgEx("Delivery Location with id " + delivery + " is not available in Routes");
	}
	if (durationCalculator < 3.0) {
	    if (deliveryLoc == null) {
		String distDur = getDynamicDuration(pickupLocationRes, deliveryLocationRes);
		List<Double> listDistDur = new ArrayList<Double>();
		String[] listDistDurResult = distDur.split(",");
		listDistDur.add(Double.parseDouble(listDistDurResult[0]));
		listDistDur.add(Double.parseDouble(listDistDurResult[1]));
		FloatListParameter newDest = new FloatListParameter(delivery, delivery, listDistDur);
		pickupLocationRes.addParameter(newDest);
		deliveryLoc = (FloatListParameter) pickupLocationRes.getParameterBy(delivery);
	    }
	}
	String[] pickupToDelivery = deliveryLoc.getValueAsString().split(",");
	// logger.info("pickupToDelivery is not null");
	double tripDur = Double.parseDouble(pickupToDelivery[1]);
	// logger.info("tripDur: "+tripDur );
	long PickupToDelivery = (long) tripDur * 1000L;
	List<Resource> availArticulatedTrucks = new ArrayList<Resource>();
	List<Resource> availRigidTrucks = new ArrayList<Resource>();
	List<Resource> availSingleTrucks = new ArrayList<Resource>();
	List<Resource> availToyotaTrucks = new ArrayList<Resource>();
	List<Resource> pekanTrucks = new ArrayList<Resource>();
	List<Resource> availIsuzuTrucks = new ArrayList<Resource>();
	for (Resource resource : resourcesByType) {
	    truckType = resource.getParameterBy("truckType").getValueAsString();
	    resMake = resource.getParameterBy(resParam).getValueAsString();
	    String base = resource.getParameterBy("location").getValueAsString();
	    StateValue<?> currentLocValue = null;
	    String CurrentLoc = null;
	    if (resource.getParameterBy("driverId").getValueAsString().equalsIgnoreCase("TBA"))
		truckWithoutDrivers.add(resource);
	    //logger.info(resource);
	    List<Activity> prevTasks = resource.getActivitiesInInterval(Long.MIN_VALUE, Long.MAX_VALUE);
	    if (prevTasks.size() < 1) {
		currentLocValue = resource.getStateVariableBy("Location").getValueAt(deliveryTime);
		if (currentLocValue == null)
		    continue;
		CurrentLoc = currentLocValue.getValueAsString();
		//logger.info("currentLocValue " + CurrentLoc);
	    } else {
		for (Activity act : prevTasks) {
		    String orderId = act.getOrderNetId();
		    Order prevOrder = RSPContainerHelper.getOrderMap(true).getEntryBy(orderId);
		    String OrderType = prevOrder.getType();
		    logger.info("prevOrder->" + orderId);
		    if (OrderType.equalsIgnoreCase("Maintenance")) {
			//logger.info("It is a maintenance order");
			continue;
		    }
		    CurrentLoc = prevOrder.getParameterBy("deliveryLocation").getValueAsString();
		    if (prevOrder.getParameterBy("orderType").getValueAsString().contains("Outstation")) {
			long prevOrderStartTime = act.getStart() - 2 * 60000L;
			//logger.info("prevOrderStartTime->" + new Date(prevOrderStartTime));
			currentLocValue = resource.getStateVariableBy("Location").getValueAt(prevOrderStartTime);
			if (currentLocValue != null)
			    CurrentLoc = currentLocValue.getValueAsString();
			break;
		    }
		}
		//logger.info("currentLocValue " + currentLocValue);
		if (CurrentLoc == null)
		    continue;
		//logger.info("currentLoc: " + CurrentLoc);
	    }
	    /*FloatListParameter deliveryToBase = (FloatListParameter) deliveryLocation.getParameterBy(base); 
	    if(deliveryToBase == null)
	    pekan continue;*/
	    Resource currentLocRes = (Resource) resourceMap.getEntryBy(CurrentLoc);
	    //logger.info(resource.getId() + " CurrentLoc : " + currentLocRes);
	    if (currentLocRes == null)
		continue;
	    /*FloatListParameter currPickParam = (FloatListParameter) currentLocRes.getParameterBy(pickup);
	    logger.info("currPickParam " + currPickParam);

	    if (currPickParam == null)
	    continue;*/
	    String Base = resource.getParameterBy("location").getValueAsString();
	    if (Base.equalsIgnoreCase("PEKAN") && !resMake.equalsIgnoreCase("ISUZU") && !resMake.equalsIgnoreCase("TOYOTA") && truckType.equalsIgnoreCase(orderedType))
		pekanTrucks.add(resource);
	    /*if(currentLocRes != null){ 
	    String[] CurrLocMatrixValues = currentLocRes .getParameterBy(pickup).getValueAsString().split(","); 
	    double currToPickup = Double.parseDouble(CurrLocMatrixValues[1]); 
	    long currToPickupDur = (long)currToPickup * 1000L;

	    long lowerD = deliveryTime - PickupToDelivery - currToPickupDur; 
	    long upperD = deliveryTime;*/
	    FloatState CapacityState = (FloatState) resource.getStateVariableBy("Capacity");
	    List<Activity> Activities = resource.getActivitiesInInterval(Long.MIN_VALUE, Long.MAX_VALUE);
	    if (!resMake.equalsIgnoreCase("ISUZU") && !resMake.equalsIgnoreCase("TOYOTA")) {
		if (truckType.equalsIgnoreCase("articulated"))
		    availArticulatedTrucks.add(resource);
		else if (truckType.equalsIgnoreCase("rigid"))
		    availRigidTrucks.add(resource);
		else if (truckType.equalsIgnoreCase("single"))
		    availSingleTrucks.add(resource);
	    }
	    if (resMake.equalsIgnoreCase("TOYOTA"))
		availToyotaTrucks.add(resource);
	    if (resMake.equalsIgnoreCase("ISUZU"))
		availIsuzuTrucks.add(resource);
	    logger.info("availToyotaTrucks ->" + availToyotaTrucks);
	    double CapacityVal = ((Double) CapacityState.getValueAt(deliveryTime).getValue()).doubleValue();
	    if (CapacityState == null) {
		throw RSPMessages.newRspMsgEx("SateVariable with id " + CapacityState + " is not found Resource with id " + resource.getId());
	    }
	    /* if ((CapacityVal < ((Double)CapacityState.getUpperBound()).doubleValue()) && (truckType.contains(orderedType)) && (resMake.contains(orderParam)) && (!orderParam.equalsIgnoreCase("others"))) { trucksResIds.add(resource); } */
	    if (truckType.equalsIgnoreCase(orderedType) && resMake.equalsIgnoreCase(orderParam) && !orderParam.equalsIgnoreCase("others")) {
		trucksResIds.add(resource);
	    } else if (truckType.equalsIgnoreCase(orderedType) && orderParam.equalsIgnoreCase("others") && !resMake.equalsIgnoreCase("ISUZU") && !resMake.equalsIgnoreCase("TOYOTA")) {
		trucksResIds.add(resource);
	    }
	}
	// }
	logger.info("trucksResIds " + trucksResIds);
	if (pekanTrucks.size() > 0 && !orderParam.equalsIgnoreCase("ISUZU") && !orderParam.equalsIgnoreCase("TOYOTA") && (pickupLocationRes.getName().equalsIgnoreCase("EAST_COAST") || deliveryLocationRes.getName().equalsIgnoreCase("EAST_COAST"))) {
	    trucksResIds = pekanTrucks;
	    logger.info("pekan1");
	}
	// else if(orderParam.equalsIgnoreCase("ISUZU") &&
	// pickupLocation.getName().equalsIgnoreCase("EAST_COAST")||deliveryLocation.getName().equalsIgnoreCase("EAST_COAST")){
	Collection<Order> Orders_CR = (Collection<Order>) RSPContainerHelper.getOrderMap(true).getByType("CR");
	List<Order> unplannedIsuzuOrders = new ArrayList<Order>();
	List<Resource> idleTrucks = new ArrayList<Resource>();
	// logger.info("IsuzuTrucks-> "+availIsuzuTrucks.size()+
	// "->"+availIsuzuTrucks);
	logger.info("idleTrucks-> " + idleTrucks + "->" + idleTrucks);
	if (checkIfIdle(trucksResIds) < 1 && !orderParam.equalsIgnoreCase("ISUZU") && !orderParam.equalsIgnoreCase("TOYOTA") && ((pickupLocationRes.getName().equalsIgnoreCase("EAST_COAST") || deliveryLocationRes.getName().equalsIgnoreCase("EAST_COAST")))) {
	    trucksResIds = availArticulatedTrucks;
	    logger.info("pekan2");
	}
	for (Order o : Orders_CR) {
	    if (o.getParameterBy("make").getValueAsString().equalsIgnoreCase("ISUZU") && o.getState().equals(PlanningState.CREATED))
		unplannedIsuzuOrders.add(o);
	}
	logger.info("unplannedIsuzuOrders-> " + unplannedIsuzuOrders.size() + "->" + unplannedIsuzuOrders);
	if (checkIfIdle(trucksResIds) < 1 && orderedType.equalsIgnoreCase("articulated") && orderParam.equalsIgnoreCase("others")) {
	    if (allowToyotaForOthers == 1) {
		for (Resource r1 : availToyotaTrucks) {
		    if (r1.getParameterBy("truckType").getValueAsString().equalsIgnoreCase("Articulated"))
			trucksResIds.add(r1);
		}
	    }
	    if (checkIfIdle(trucksResIds) < 1 && allowIsuzuForOthers == 1) {
		for (Resource r1 : availIsuzuTrucks) {
		    if (r1.getParameterBy("truckType").getValueAsString().equalsIgnoreCase("Articulated"))
			trucksResIds.add(r1);
		}
	    }
	} else if (checkIfIdle(trucksResIds) < 1 && orderedType.equalsIgnoreCase("rigid") && !orderParam.equalsIgnoreCase("ISUZU") && !orderParam.equalsIgnoreCase("TOYOTA")) {
	    if (allowToyotaForOthers == 1) {
		for (Resource r1 : availToyotaTrucks) {
		    if (r1.getParameterBy("truckType").getValueAsString().equalsIgnoreCase("rigid"))
			trucksResIds.add(r1);
		}
	    }
	    if (checkIfIdle(trucksResIds) < 1 && availArticulatedTrucks.size() > 0 && isArticulatedAllowed)
		trucksResIds.addAll(availArticulatedTrucks);
	    if (checkIfIdle(trucksResIds) < 1 && allowToyotaForOthers == 1 && isArticulatedAllowed) {
		for (Resource r1 : availToyotaTrucks) {
		    if (r1.getParameterBy("truckType").getValueAsString().equalsIgnoreCase("articulated"))
			trucksResIds.add(r1);
		}
	    }
	    if (checkIfIdle(trucksResIds) < 1 && allowIsuzuForOthers == 1 && isRigidAllowed) {
		for (Resource r1 : availIsuzuTrucks) {
		    if (r1.getParameterBy("truckType").getValueAsString().equalsIgnoreCase("rigid"))
			trucksResIds.add(r1);
		}
	    }
	    if (allowIsuzuForOthers == 1 && checkIfIdle(trucksResIds) < 1 && isArticulatedAllowed) {
		for (Resource r1 : availIsuzuTrucks) {
		    if (r1.getParameterBy("truckType").getValueAsString().equalsIgnoreCase("articulated"))
			trucksResIds.add(r1);
		}
	    }
	}
	// logger.info("idleTrucks-size: "+idleTrucks.size()+"trucksResIds-size: "+trucksResIds.size());
	else if (checkIfIdle(trucksResIds) < 1 && orderedType.equalsIgnoreCase("single") && !orderParam.equalsIgnoreCase("ISUZU") && !orderParam.equalsIgnoreCase("TOYOTA")) {
	    if (isRigidAllowed)
		trucksResIds.addAll(availRigidTrucks);
	    if (checkIfIdle(trucksResIds) < 1 && allowToyotaForOthers == 1 && isRigidAllowed) {
		for (Resource r1 : availToyotaTrucks) {
		    if (r1.getParameterBy("truckType").getValueAsString().equalsIgnoreCase("rigid"))
			trucksResIds.add(r1);
		}
	    }
	    if (checkIfIdle(trucksResIds) < 1 && isArticulatedAllowed)
		trucksResIds.addAll(availArticulatedTrucks);
	    if (checkIfIdle(trucksResIds) < 1 && allowToyotaForOthers == 1 && isArticulatedAllowed) {
		for (Resource r1 : availToyotaTrucks) {
		    if (r1.getParameterBy("truckType").getValueAsString().equalsIgnoreCase("articulated"))
			trucksResIds.add(r1);
		}
	    }
	    if (checkIfIdle(trucksResIds) < 1 && allowIsuzuForOthers == 1 && isRigidAllowed) {
		for (Resource r1 : availIsuzuTrucks) {
		    if (r1.getParameterBy("truckType").getValueAsString().equalsIgnoreCase("rigid"))
			trucksResIds.add(r1);
		}
	    }
	    if (checkIfIdle(trucksResIds) < 1 && allowIsuzuForOthers == 1 && isArticulatedAllowed) {
		for (Resource r1 : availIsuzuTrucks) {
		    if (r1.getParameterBy("truckType").getValueAsString().equalsIgnoreCase("articulated"))
			trucksResIds.add(r1);
		}
	    }
	}
	//logger.info("trucksResIds.size: " + trucksResIds);
	//logger.info("trucksResIds.size" + trucksResIds.size());
	if (trucksResIds.size() < 1) {
	    try {
		remarks.setValue("Trucks with make ID: " + orderParam + " and type: " + orderedType + " are not available for the day. ");
		throw RSPMessages.newRspMsgEx("Trucks with make ID: " + orderParam + " and type: " + orderedType + " are not available. ");
	    } catch (Exception e) {
		e.printStackTrace();
	    }
	}
	if (trucksResIds.size() >= 1) {
	    ListIterator itr = trucksResIds.listIterator();
	    List<Resource> driversFromMaintenance = new ArrayList<Resource>();
	    List<Resource> assignedDriversFromMaint = new ArrayList<Resource>();
	    while (itr.hasNext()) {
		Resource truck = (Resource) itr.next();
		//logger.info(truck.getId());
		StringParameter driverId = (StringParameter) truck.getParameterBy("driverId");
		String driver = driverId.getValueAsString();
		Resource assignedDriver = null;
		List<Activity> truckActlist = truck.getActivitiesInInterval(0, java.lang.Long.MAX_VALUE);
		if (truckActlist.size() == 0) {
		    if (driver.equalsIgnoreCase("TBA")) {
			driversFromMaintenance = getPreferredDriverFromMaintenance(order, truck);
			if (driversFromMaintenance.size() > 0)
			    assignedDriver = driversFromMaintenance.get(0);
			//logger.info("assignedDriver: " + assignedDriver);
			while (true) {
			    if (assignedDriver == null) {
				itr.remove();
				break;
			    } else {
				if (!assignedDriversFromMaint.contains(assignedDriver)) {
				    driverId.setValue(assignedDriver.getId());
				    assignedDriversFromMaint.add(assignedDriver);
				    break;
				} else {
				    driversFromMaintenance.remove(0);
				    if (driversFromMaintenance.size() > 0)
					assignedDriver = driversFromMaintenance.get(0);
				    else
					assignedDriver = null;
				}
			    }
			}
		    }
		} else {
		    Order prevOrder = RSPContainerHelper.getOrderMap(true).getEntryBy(truckActlist.get(0).getOrderNetId());
		    ((StringParameter) truck.getParameterBy("driverId")).setValue(prevOrder.getParameterBy("driverId").getValueAsString());
		}
	    }
	    //logger.info("trucksResIds : " + trucksResIds.size());
	    if (trucksResIds.size() < 1) {
		try {
		    remarks.setValue("No drivers are present to drive the truck of ordered type : " + orderedType + " for the day.");
		    throw RSPMessages.newRspMsgEx("No drivers are present to drive the truck of ordered type : " + orderedType + " for the day.");
		} catch (Exception e) {
		    e.printStackTrace();
		}
	    }
	}
	logger.info("trucks feasible->" + trucksResIds);
	HashMap resDur = new HashMap();
	Date date = new Date(pickupTime);
	SimpleDateFormat df2 = new SimpleDateFormat("MM/dd/yyyy");
	String dateText = df2.format(date);
	StringWriter Writer = new StringWriter();
	Writer.append(dateText);
	Writer.append(" 07:00:00");
	SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
	try {
	    Date date1 = simpleDateFormat.parse(Writer.toString());
	    long lower = date1.getTime();
	    long upper = deliveryTime;
	    Resource CurrentLocRes;
	    Collection<Order> MaintenanceOrders = RSPContainerHelper.getOrderMap(true).getByType("Maintenance");
	    List<String> mntTrucks = new ArrayList<String>();
	    for (Order mntOrder : MaintenanceOrders) {
		if (mntOrder.getState().equals(PlanningState.PLANNED))
		    mntTrucks.add(mntOrder.getParameterBy("truckNo").getValueAsString());
	    }
	    for (Resource res : trucksResIds) {
		/*Double currentLocLat = (Double) res.getParameterBy("lat").getValue();
		Double currentLocLong = (Double) res.getParameterBy("long").getValue();*/
		String[] latLongValues = currLatLong.getParameterBy(res.getId()).getValueAsString().split(",");
		Double currentLocLat = Double.valueOf(latLongValues[0]);
		Double currentLocLong = Double.valueOf(latLongValues[1]);
		String CurrentLoc = res.getStateVariableBy("Location").getValueAt(deliveryTime).getValueAsString();
		String[] LocMatrixValues = {};
		if (durationCalculator < 3.0) {
		    Double pickupLocLat = (Double) pickupLocationRes.getParameterBy("lat").getValue();
		    Double pickupLocLong = (Double) pickupLocationRes.getParameterBy("long").getValue();
		    LocMatrixValues = FindDistanceDuration.getDistanceDuration(currentLocLat, currentLocLong, pickupLocLat, pickupLocLong).split(",");
		} else {
		    CurrentLocRes = (Resource) resourceMap.getEntryBy(CurrentLoc);
		    LocMatrixValues = CurrentLocRes.getParameterBy(pickup).getValueAsString().split(",");
		}
		double nextPickupDur = Double.parseDouble(LocMatrixValues[1]);
		resDur.put(res, Double.valueOf(nextPickupDur));
	    }
	    HashMap<Resource, Double> sortedResDur = sortByValues(resDur);
	    Set set = sortedResDur.entrySet();
	    Iterator iterator = set.iterator();
	    while (iterator.hasNext()) {
		Map.Entry me = (Map.Entry) iterator.next();
	    }
	    List<Resource> ResIds = new ArrayList(sortedResDur.keySet());
	    // logger.info("sortedRes :" + ResIds);
	    StringWriter resultWriter = new StringWriter();
	    for (Resource re : ResIds) {
		if (!mntTrucks.contains(re.getId())) {
		    resultWriter.append(re.getId());
		    resultWriter.append(",");
		}
	    }
	    String resultS = resultWriter.toString();
	    int latestKommaPosition = resultS.lastIndexOf(",");
	    if (latestKommaPosition >= 0) {
		resultS = resultS.substring(0, latestKommaPosition);
		StringParameter evaluatedTrucks = (StringParameter) order.getParameterBy("evaluatedTrucks");
		evaluatedTrucks.setValue(resultS);
		// logger.info("evaluatedTrucks"+evaluatedTrucks);
	    } else {
		String log = remarks.getValueAsString();
		//logger.info("log: " + log);
		remarks.setValue(log);
		throw RSPMessages.newRspMsgEx(log);
	    }
	    StringParameter resultParameter = new StringParameter(this.descriptiveParameter.getId(), this.descriptiveParameter.getName(), resultS.toString());
	    resultParameter.setHidden(this.descriptiveParameter.isHidden());
	    resultParameter.setUom(this.descriptiveParameter.getUom());
	    resultParameter.setInterpretation(this.descriptiveParameter.getInterpretation());
	    resultParameter.setSortIndex(this.descriptiveParameter.getSortIndex());
	    resultParameter.setParent(getElement());
	    resultParam = resultParameter;
	} catch (ParseException e) {
	    e.printStackTrace();
	}
	return resultParam;
    }

    public static HashMap sortByValues(HashMap map) {
	List list = new LinkedList(map.entrySet());
	List<Object> keySet = new ArrayList<Object>();
	List<Object> valueSet = new ArrayList<Object>();
	// Defined Custom Comparator here
	Collections.sort(list, new Comparator() {
	    public int compare(Object o1, Object o2) {
		return ((Comparable) ((Map.Entry) (o1)).getValue()).compareTo(((Map.Entry) (o2)).getValue());
	    }
	});
	// Here I am copying the sorted list in HashMap
	// using LinkedHashMap to preserve the insertion order
	HashMap sortedHashMap = new LinkedHashMap();
	for (Iterator it = list.iterator(); it.hasNext();) {
	    Map.Entry entry = (Map.Entry) it.next();
	    sortedHashMap.put(entry.getKey(), entry.getValue());
	    keySet.add(entry.getKey());
	    valueSet.add(entry.getValue());
	}
	return sortedHashMap;
    }

    private List<Resource> getPreferredDriverFromMaintenance(Order order, Resource truck) {
	//logger.info("DriverUnavailable");
	List<Resource> result = new ArrayList<Resource>();
	long pickupTime = (Long) order.getParameterBy("pickupDate").getValue();
	String truckMake = truck.getParameterBy("make").getValueAsString();
	List<Resource> maintenanceTrucks = new ArrayList<Resource>();
	Collection<Order> MaintenanceOrders = RSPContainerHelper.getOrderMap(true).getByType("Maintenance");
	for (Order o : MaintenanceOrders) {
	    String mntType = "";
	    if (o.hasParameter("maintenanceType"))
		mntType = o.getParameterBy("maintenanceType").getValueAsString();
	    if (!mntType.contains("Inspection")) {
		String truckNo = o.getParameterBy("truckNo").getValueAsString();
		//logger.info("truckUnderMaintenance:" + truckNo);
		Resource truckRes = RSPContainerHelper.getResourceMap(true).getEntryBy(truckNo);
		if (truckRes == null)
		    continue;
		//logger.info("truckno in maintenance" + truckRes.getId());
		String maintenanceTruckMake = truckRes.getParameterBy("make").getValueAsString();
		if (truckMake.equalsIgnoreCase("OTHERS") && o.getState().equals(PlanningState.PLANNED))
		    maintenanceTrucks.add(truckRes);
		else {
		    if (maintenanceTruckMake.equalsIgnoreCase(truckMake) && o.getState().equals(PlanningState.PLANNED))
			maintenanceTrucks.add(truckRes);
		}
	    }
	}
	String truckType = truck.getParameterBy("truckType").getValueAsString();
	//logger.info("truckType:" + truckType);
	List<Resource> idleRigidDrivers = new ArrayList<Resource>();
	List<Resource> idleArticulatedDrivers = new ArrayList<Resource>();
	List<Resource> idleSingleDrivers = new ArrayList<Resource>();
	//logger.info("List of maintenance trucks: " + maintenanceTrucks);
	for (Resource t : maintenanceTrucks) {
	    StringParameter driver = (StringParameter) t.getParameterBy("driverId");
	    String driverId = driver.getValueAsString();
	    if (driverId.equalsIgnoreCase("TBA"))
		continue;
	    Resource driverRes = RSPContainerHelper.getResourceMap(true).getEntryBy(driverId);
	    // logger.info(driverRes);
	    if (driverRes == null)
		continue;
	    String skill = t.getParameterBy("truckType").getValueAsString();
	    StateValue<?> driverCapacityState = driverRes.getStateVariableBy("Capacity").getValueAt(pickupTime);
	    //logger.info("driverCapacityState: " + driverCapacityState);
	    double driverCapacity = (Double) driverCapacityState.getValue();
	    //logger.info("driverCapacity: " + driverCapacity);
	    boolean isAlreadyAllocated = false;
	    if (driverRes.hasParameter("isAlreadyAllocated")) {
		//logger.info("isAlreadyAllocated: " + driverRes.getId());
		isAlreadyAllocated = true;
	    }
	    if (!isAlreadyAllocated) {
		if (driverCapacity == 0.0) {
		    if (skill.equals("rigid"))
			idleRigidDrivers.add(driverRes);
		    else if (skill.equals("articulated"))
			idleArticulatedDrivers.add(driverRes);
		    else if (skill.equals("single"))
			idleSingleDrivers.add(driverRes);
		}
	    }
	}
	logger.info("idleRigidDrivers: " + idleRigidDrivers);
	logger.info("idleArticulatedDrivers: " + idleArticulatedDrivers);
	logger.info("idleSingleDrivers: " + idleSingleDrivers);
	if (truckType.equalsIgnoreCase("single")) {
	    result.addAll(idleSingleDrivers);
	    result.addAll(idleRigidDrivers);
	    result.addAll(idleArticulatedDrivers);
	} else if (truckType.equalsIgnoreCase("rigid")) {
	    result.addAll(idleRigidDrivers);
	    result.addAll(idleArticulatedDrivers);
	} else if (truckType.equalsIgnoreCase("articulated")) {
	    result.addAll(idleArticulatedDrivers);
	}
	logger.info("result :" + result);
	return result;
    }

    public int checkIfIdle(List<Resource> trucksResIds) {
	List<Resource> idleTrucks = new ArrayList<Resource>();
	for (Resource r : trucksResIds) {
	    if (r.getActivityMap().size() < 1 && !r.getParameterBy("driverId").getValueAsString().equalsIgnoreCase("TBA"))
		idleTrucks.add(r);
	}
	return idleTrucks.size();
    }

    public static String getDynamicDuration(Resource pickupLocationRes, Resource deliveryLocationRes) {
	double pickLat = (Double) pickupLocationRes.getParameterBy("lat").getValue();
	double pickLong = (Double) pickupLocationRes.getParameterBy("long").getValue();
	double delLat = (Double) deliveryLocationRes.getParameterBy("lat").getValue();
	double delLong = (Double) deliveryLocationRes.getParameterBy("long").getValue();
	String distDur = FindDistanceDuration.getDistanceDuration(pickLat, pickLong, delLat, delLong);
	return distDur;
    }

    public static boolean isEntryAllowed(Order order, String truckType) {
	String pickupLocation = order.getParameterBy("pickupLocation").getValueAsString();
	String deliveryLocation = order.getParameterBy("deliveryLocation").getValueAsString();
	String custId = order.getParameterBy("customerID").getValueAsString();
	String customerPickupId = custId + "_" + pickupLocation;
	String customerDeliveryId = custId + "_" + deliveryLocation;
	Collection<Resource> customerLocMap = RSPContainerHelper.getResourceMap(true).getByType("Customer_Location_Mapping");
	int pickupMatch = 0;
	int deliveryMatch = 0;
	for (Resource r : customerLocMap) {
	    String custLocId = r.getId();
	    String custCode = r.getParameterBy("customerCode").getValueAsString();
	    if (!custCode.equalsIgnoreCase(custId))
		continue;
	    else {
		if (pickupMatch == 2)
		    ;
		else
		    pickupMatch = 1;
		if (deliveryMatch == 2)
		    ;
		else
		    deliveryMatch = 1;
		if (customerPickupId.equalsIgnoreCase(r.getId()))
		    pickupMatch = 2;
		if (customerDeliveryId.equalsIgnoreCase(r.getId()))
		    deliveryMatch = 2;
	    }
	}
	if (pickupMatch == 0)
	    customerPickupId = "OTHERS_OTHERS";
	else if (pickupMatch == 1)
	    customerPickupId = custId + "_OTHERS";
	if (deliveryMatch == 0)
	    customerDeliveryId = "OTHERS_OTHERS";
	else if (deliveryMatch == 1)
	    customerDeliveryId = custId + "_OTHERS";
	Resource custPickLocationRes = RSPContainerHelper.getResourceMap(true).getEntryBy(customerPickupId);
	Resource custDelLocationRes = RSPContainerHelper.getResourceMap(true).getEntryBy(customerDeliveryId);
	if ((Double) custPickLocationRes.getParameterBy(truckType).getValue() == 1.0 && (Double) custDelLocationRes.getParameterBy(truckType).getValue() == 1.0)
	    return true;
	else
	    return false;
    }
}