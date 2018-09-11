package com.ramco.giga.utils;

import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.log4j.Logger;

import com.ramco.giga.constant.GigaConstants;
import com.ramco.giga.dbupload.GigaDBUpload;
import com.ramco.giga.formula.FindDistanceDuration;
import com.rsp.core.base.FeatherliteAgent;
import com.rsp.core.base.RSPContainerHelper;
import com.rsp.core.base.command.AddOrderCommand;
import com.rsp.core.base.command.RemoveOrderCommand;
import com.rsp.core.base.model.Activity;
import com.rsp.core.base.model.ElementMap;
import com.rsp.core.base.model.Order;
import com.rsp.core.base.model.Resource;
import com.rsp.core.base.model.Workflow;
import com.rsp.core.base.model.constants.PlanningState;
import com.rsp.core.base.model.constants.ProcessingState;
import com.rsp.core.base.model.parameter.BooleanMapParameter;
import com.rsp.core.base.model.parameter.BooleanParameter;
import com.rsp.core.base.model.parameter.FloatListParameter;
import com.rsp.core.base.model.parameter.FloatParameter;
import com.rsp.core.base.model.parameter.IntegerParameter;
import com.rsp.core.base.model.parameter.StringListParameter;
import com.rsp.core.base.model.parameter.StringParameter;
import com.rsp.core.base.model.stateVariable.StateValue;
import com.rsp.core.base.model.stateVariable.StringState;
import com.rsp.core.base.query.comparator.ActivityByEndComparator;
import com.rsp.core.base.transaction.RSPTransaction;
import com.rsp.core.control.command.EvictOrderToCreatedCommand;
import com.rsp.core.helper.ISO8601FormatFactory;
import com.rsp.core.planning.command.ClearWorkflowCommand;
import com.rsp.core.planning.command.PlanOrderCommand;
import com.rsp.core.planning.policy.PlacementResult;

public class GigaUtils {
    protected static Logger logger = Logger.getLogger(FindDistanceDuration.class);

    public static List<Resource> getPreferredDriverFromMaintenance(Order order, Resource truck) {
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
		Resource truckRes = RSPContainerHelper.getResourceMap(true).getEntryBy(truckNo);
		if (truckRes == null)
		    continue;
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
	List<Resource> idleRigidDrivers = new ArrayList<Resource>();
	List<Resource> idleArticulatedDrivers = new ArrayList<Resource>();
	List<Resource> idleSingleDrivers = new ArrayList<Resource>();
	for (Resource t : maintenanceTrucks) {
	    StringParameter driver = (StringParameter) t.getParameterBy("driverId");
	    String driverId = driver.getValueAsString();
	    if (driverId.equalsIgnoreCase("TBA"))
		continue;
	    Resource driverRes = RSPContainerHelper.getResourceMap(true).getEntryBy(driverId);
	    if (driverRes == null)
		continue;
	    String skill = t.getParameterBy("truckType").getValueAsString();
	    StateValue<?> driverCapacityState = driverRes.getStateVariableBy("Capacity").getValueAt(pickupTime);
	    double driverCapacity = (Double) driverCapacityState.getValue();
	    boolean isAlreadyAllocated = false;
	    if (driverRes.hasParameter("isAlreadyAllocated")) {
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
	logger.debug("idleRigidDrivers: " + idleRigidDrivers);
	logger.debug("idleArticulatedDrivers: " + idleArticulatedDrivers);
	logger.debug("idleSingleDrivers: " + idleSingleDrivers);
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
	logger.debug("result :" + result);
	return result;
    }

    public static Resource getSpareDriver(Resource truckRes, long activityStart, String orderJobType) {
	String truckMake = truckRes.getParameterBy(GigaConstants.RES_PARAM_MAKE).getValueAsString();
	String truckType = truckRes.getParameterBy(GigaConstants.RES_PARAM_TRUCK_TYPE).getValueAsString();
	Collection<Order> MaintenanceOrders = RSPContainerHelper.getOrderMap(true).getByType(GigaConstants.ORD_TYPE_MAINTENANCE);
	String selectedDriverId = null;
	Resource mntTruckRes = null;
	List<String> toyotaDrivers = getToyotaDriversList();
	HashMap<Double, List<Resource>> driverLoadMap = new HashMap<Double, List<Resource>>();
	for (Order mntOrder : MaintenanceOrders) {
	    Boolean isForceLocal = false;
	    if (mntOrder.getState().equals(PlanningState.PLANNED)) {
		String mntType = "";
		if (mntOrder.hasParameter(GigaConstants.MNT_ORD_PARAM_MAINTENANCE_TYPE))
		    mntType = mntOrder.getParameterBy(GigaConstants.MNT_ORD_PARAM_MAINTENANCE_TYPE).getValueAsString();
		if (!mntType.contains(GigaConstants.MNT_ORD_TYPE_INSPECTION)) {
		    String truckNo = mntOrder.getParameterBy(GigaConstants.MNT_ORD_PARAM_TRUCK_NO).getValueAsString();
		    mntTruckRes = RSPContainerHelper.getResourceMap(true).getEntryBy(truckNo);
		    if (mntTruckRes == null)
			continue;
		    String maintenanceTruckMake = mntTruckRes.getParameterBy(GigaConstants.RES_PARAM_MAKE).getValueAsString();
		    StringState truckLocattionState = (StringState) mntTruckRes.getStateVariableBy(GigaConstants.STATE_LOCATION);
		    String maintenanceTruckLocation = truckLocattionState.getValueAt(activityStart).getValueAsString();
		    String truckResLocation = truckRes.getStateVariableBy(GigaConstants.STATE_LOCATION).getValueAt(Long.MAX_VALUE).getValueAsString();
		    String mntTruckType = mntTruckRes.getParameterBy(GigaConstants.RES_PARAM_TRUCK_TYPE).getValueAsString();
		    selectedDriverId = mntTruckRes.getParameterBy(GigaConstants.RES_PARAM_DRIVERID).getValueAsString();
		    Resource driverRes = RSPContainerHelper.getResourceMap(true).getEntryBy(selectedDriverId);
		    if (driverRes.hasParameter(GigaConstants.FORCELOCAL)) {
			isForceLocal = ((BooleanParameter) driverRes.getParameterBy(GigaConstants.FORCELOCAL)).getValue();
		    }
		    if (orderJobType.equals(GigaConstants.ORDER_TYPE_OUTSTATION) && isForceLocal) {
			continue;
		    }
		    if (truckType.equalsIgnoreCase(mntTruckType)) {
			if (truckMake.equalsIgnoreCase(maintenanceTruckMake) && maintenanceTruckLocation.equalsIgnoreCase(truckResLocation)) {
			    if (truckMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_TOYOTA)) {
				if (!selectedDriverId.equalsIgnoreCase(GigaConstants.TRUCK_WITHOUT_DRIVER)) {
				    if (toyotaDrivers.contains(selectedDriverId)) {
					Double driverLoadCount = 0.0;
					if (orderJobType.equalsIgnoreCase(GigaConstants.ORDER_TYPE_LOCAL)) {
					    driverLoadCount = ((IntegerParameter) driverRes.getParameterBy(GigaConstants.NO_OF_LOCAL_ORDERS)).getValue().doubleValue();
					    driverLoadMap = populateMap(driverLoadCount, mntTruckRes, driverLoadMap);
					}
					if (orderJobType.equalsIgnoreCase(GigaConstants.ORDER_TYPE_OUTSTATION)) {
					    driverLoadCount = ((IntegerParameter) driverRes.getParameterBy(GigaConstants.NO_OF_OUTSTATION_ORDERS)).getValue().doubleValue();
					    driverLoadMap = populateMap(driverLoadCount, mntTruckRes, driverLoadMap);
					}
					continue;
				    } else {
					mntTruckRes = null;
					continue;
				    }
				} else {
				    mntTruckRes = null;
				    continue;
				}
			    } else {
				if (!selectedDriverId.equalsIgnoreCase(GigaConstants.TRUCK_WITHOUT_DRIVER)) {
				    Double driverLoadCount = 0.0;
				    if (orderJobType.equalsIgnoreCase(GigaConstants.ORDER_TYPE_LOCAL)) {
					driverLoadCount = ((IntegerParameter) driverRes.getParameterBy(GigaConstants.NO_OF_LOCAL_ORDERS)).getValue().doubleValue();
					driverLoadMap = populateMap(driverLoadCount, mntTruckRes, driverLoadMap);
				    }
				    if (orderJobType.equalsIgnoreCase(GigaConstants.ORDER_TYPE_OUTSTATION)) {
					driverLoadCount = ((IntegerParameter) driverRes.getParameterBy(GigaConstants.NO_OF_OUTSTATION_ORDERS)).getValue().doubleValue();
					driverLoadMap = populateMap(driverLoadCount, mntTruckRes, driverLoadMap);
				    }
				    continue;
				} else {
				    mntTruckRes = null;
				    continue;
				}
			    }
			} else if (truckMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_OTHERS) && maintenanceTruckMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_TOYOTA) && maintenanceTruckLocation.equalsIgnoreCase(truckResLocation)) {
			    if (!selectedDriverId.equalsIgnoreCase(GigaConstants.TRUCK_WITHOUT_DRIVER)) {
				selectedDriverId = mntTruckRes.getParameterBy(GigaConstants.RES_PARAM_DRIVERID).getValueAsString();
				Double driverLoadCount = 0.0;
				if (orderJobType.equalsIgnoreCase(GigaConstants.ORDER_TYPE_LOCAL)) {
				    driverLoadCount = ((IntegerParameter) driverRes.getParameterBy(GigaConstants.NO_OF_LOCAL_ORDERS)).getValue().doubleValue();
				    driverLoadMap = populateMap(driverLoadCount, mntTruckRes, driverLoadMap);
				}
				if (orderJobType.equalsIgnoreCase(GigaConstants.ORDER_TYPE_OUTSTATION)) {
				    driverLoadCount = ((IntegerParameter) driverRes.getParameterBy(GigaConstants.NO_OF_OUTSTATION_ORDERS)).getValue().doubleValue();
				    driverLoadMap = populateMap(driverLoadCount, mntTruckRes, driverLoadMap);
				}
				continue;
			    } else {
				mntTruckRes = null;
				continue;
			    }
			} else {
			    mntTruckRes = null;
			    continue;
			}
		    } else {
			mntTruckRes = null;
			continue;
		    }
		}
	    }
	}
	TreeMap<Double, List<Resource>> driverLoadSortedMap = new TreeMap<Double, List<Resource>>(driverLoadMap);
	if (driverLoadSortedMap.size() > 0) {
	    mntTruckRes = driverLoadSortedMap.entrySet().iterator().next().getValue().get(0);
	    return mntTruckRes;
	} else {
	    return null;
	}
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
	ElementMap<Resource> resourceMap = RSPContainerHelper.getResourceMap(true);
	if (order.getParameterBy(GigaConstants.ORD_PARAM_BUSINESS_DIVISION).getValueAsString().equalsIgnoreCase(GigaConstants.BUSINESS_DIVISION_EAST_MALAYSIA)) {
	    return true;
	} else {
	    String pickupLocation = order.getParameterBy("pickupLocation").getValueAsString();
	    String deliveryLocation = order.getParameterBy("deliveryLocation").getValueAsString();
	    String custId = order.getParameterBy("customerID").getValueAsString();
	    String customerPickupId = custId + "_" + pickupLocation;
	    String customerDeliveryId = custId + "_" + deliveryLocation;
	    Resource custPickLocationRes = null;
	    Resource custDelLocationRes = null;
	    custPickLocationRes = resourceMap.getEntryBy(customerPickupId);
	    custDelLocationRes = resourceMap.getEntryBy(customerDeliveryId);
	    if (custPickLocationRes == null) {
		customerPickupId = custId + "_OTHERS";
		custPickLocationRes = resourceMap.getEntryBy(customerPickupId);
		if (custPickLocationRes == null) {
		    customerPickupId = "OTHERS_OTHERS";
		    custPickLocationRes = resourceMap.getEntryBy(customerPickupId);
		}
	    }
	    if (custDelLocationRes == null) {
		customerDeliveryId = custId + "_OTHERS";
		custDelLocationRes = resourceMap.getEntryBy(customerDeliveryId);
		if (custDelLocationRes == null) {
		    customerDeliveryId = "OTHERS_OTHERS";
		    custDelLocationRes = resourceMap.getEntryBy(customerDeliveryId);
		}
	    }
	    if ((Double) custPickLocationRes.getParameterBy(truckType).getValue() == 1.0 && (Double) custDelLocationRes.getParameterBy(truckType).getValue() == 1.0)
		return true;
	    else
		return false;
	}
    }

    public static boolean canHaveCurrLatLong(Resource truckResource) {
	List<Activity> truckkActivityList = truckResource.getActivitiesAt(System.currentTimeMillis());
	Collections.sort(truckkActivityList, new ActivityByEndComparator());
	if (truckkActivityList.size() > 0) {
	    return false;
	} else {
	    truckkActivityList = truckResource.getActivitiesInInterval(0, System.currentTimeMillis());
	    if (truckkActivityList.size() > 0)
		return true;
	    else
		return false;
	}
    }

    public static String getCurrentLocation(Resource truckResource) {
	List<Activity> truckkActivityList = truckResource.getActivitiesAt(System.currentTimeMillis());
	Collections.sort(truckkActivityList, new ActivityByEndComparator());
	String currOrLastLocation = "";
	StringState truckLocattionState = (StringState) truckResource.getStateVariableBy(GigaConstants.STATE_LOCATION);
	List<StateValue<String>> locationStateValues = new ArrayList<StateValue<String>>(truckLocattionState.getValues());
	long currBaseLocationTime = 0;
	if (truckkActivityList.size() > 0) {
	    currBaseLocationTime = truckkActivityList.get(0).getEnd();
	    currOrLastLocation = truckLocattionState.getValueAt(currBaseLocationTime).getValueAsString();
	} else {
	    truckkActivityList = truckResource.getActivitiesInInterval(0, System.currentTimeMillis());
	    if (truckkActivityList.size() > 0)
		currOrLastLocation = truckLocattionState.getValueAt(System.currentTimeMillis()).getValueAsString();
	    else
		currOrLastLocation = locationStateValues.get(1).getValueAsString();
	}
	return currOrLastLocation;
    }

    public static long getDuration(String placeFrom, String placeTo) {
	logger.debug("placeFrom::" + placeFrom + "::placeTo-->" + placeTo);
	long result = 0L;
	double duration = 0;
	Resource placeFromRes = RSPContainerHelper.getResourceMap(true).getEntryBy(placeFrom);
	Resource gigaParameters = RSPContainerHelper.getResourceMap(true).getEntryBy(GigaConstants.RES_GIGA_PARAM);
	Double durationCalculator = ((FloatParameter) gigaParameters.getParameterBy(GigaConstants.GIGA_PARAM_DUR_CALCULATION)).getValue();
	//Double truckDrivingFactor = ((FloatParameter) gigaParameters.getParameterBy(GigaConstants.GIGA_PARAM_TRUCK_DRIVING_FACTOR)).getValue();
	if (durationCalculator < 3) {
	    if (placeFromRes.getParameterBy(placeTo) == null) {
		Resource placeToRes = RSPContainerHelper.getResourceMap(true).getEntryBy(placeTo);
		String[] currToPickupDuration = GigaUtils.getDynamicDuration(placeFromRes, placeToRes).split(",");
		duration = Long.parseLong(currToPickupDuration[1]);
		List<Double> listDistDur = new ArrayList<Double>();
		listDistDur.add(Double.parseDouble(currToPickupDuration[0]));
		listDistDur.add(Double.parseDouble(currToPickupDuration[1]));
		FloatListParameter newDest = new FloatListParameter(placeToRes.getId(), placeToRes.getId(), listDistDur);
		placeFromRes.addParameter(newDest);
	    } else {
		String[] MatrixValues = placeFromRes.getParameterBy(placeTo).getValueAsString().split(",");
		logger.debug("MatrixValues[1]-->" + MatrixValues[1] + "::placeFrom::" + placeFrom + "::placeTo-->" + placeTo);
		duration = Double.parseDouble(MatrixValues[1]);
		logger.debug("result: " + duration);
	    }
	}
	//result = (long) (duration * truckDrivingFactor);
	result = (long) duration;
	if (result <= 0) {
	    Double durPickupAndDropSame = 0.0;
	    if (gigaParameters.hasParameter(GigaConstants.GIGA_PARAM_SAME_PICK_DROP)) {
		durPickupAndDropSame = ((FloatParameter) gigaParameters.getParameterBy(GigaConstants.GIGA_PARAM_SAME_PICK_DROP)).getValue();
		result = (long) (durPickupAndDropSame * 60000);
	    } else {
		durPickupAndDropSame = 1.0;
		result = (long) (durPickupAndDropSame * 60000);
	    }
	}
	return result;
    }

    public static double getDistance(String placeFrom, String placeTo) {
	logger.debug("placeFrom::" + placeFrom + "::placeTo-->" + placeTo);
	double result = 0L;
	double distance = 0;
	Resource placeFromRes = RSPContainerHelper.getResourceMap(true).getEntryBy(placeFrom);
	Resource gigaParameters = RSPContainerHelper.getResourceMap(true).getEntryBy(GigaConstants.RES_GIGA_PARAM);
	Double durationCalculator = ((FloatParameter) gigaParameters.getParameterBy(GigaConstants.GIGA_PARAM_DUR_CALCULATION)).getValue();
	if (durationCalculator < 3) {
	    if (placeFromRes.getParameterBy(placeTo) == null) {
		Resource placeToRes = RSPContainerHelper.getResourceMap(true).getEntryBy(placeTo);
		String[] currToPickupDuration = GigaUtils.getDynamicDuration(placeFromRes, placeToRes).split(",");
		distance = Long.parseLong(currToPickupDuration[0]);
		List<Double> listDistDur = new ArrayList<Double>();
		listDistDur.add(Double.parseDouble(currToPickupDuration[0]));
		listDistDur.add(Double.parseDouble(currToPickupDuration[1]));
		FloatListParameter newDest = new FloatListParameter(placeToRes.getId(), placeToRes.getId(), listDistDur);
		placeFromRes.addParameter(newDest);
	    } else {
		String[] MatrixValues = placeFromRes.getParameterBy(placeTo).getValueAsString().split(",");
		logger.debug("MatrixValues[1]-->" + MatrixValues[1] + "::placeFrom::" + placeFrom + "::placeTo-->" + placeTo);
		distance = Double.parseDouble(MatrixValues[0]);
		logger.debug("result: " + distance);
	    }
	}
	result = distance;
	logger.debug("result: " + distance);
	return result;
    }

    public static List<String> getToyotaDriversList() {
	List<String> toyotaDrivers = new ArrayList<String>();
	if (RSPContainerHelper.getResourceMap(true).getEntryBy(GigaConstants.RES_TOYOTA_DRIVERS_LIST) != null)
	    toyotaDrivers = ((StringListParameter) RSPContainerHelper.getResourceMap(true).getEntryBy(GigaConstants.RES_TOYOTA_DRIVERS_LIST).getParameterBy(GigaConstants.RES_PARAM_TOYOTA_DRIVERS_LIST)).getValue();
	return toyotaDrivers;
    }

    public static boolean isDoneAnylocal(Resource truckResource) {
	ElementMap<Order> orderMap = RSPContainerHelper.getOrderMap(true);
	Calendar currentDate = Calendar.getInstance();
	currentDate.set(Calendar.HOUR, 0);
	currentDate.set(Calendar.MINUTE, 0);
	currentDate.set(Calendar.SECOND, 0);
	currentDate.set(Calendar.HOUR_OF_DAY, 0);
	Long planDate = currentDate.getTime().getTime();
	List<Activity> truckActivities = truckResource.getActivitiesInInterval(planDate, Long.MAX_VALUE);
	for (Activity activity : truckActivities) {
	    Order order = orderMap.getEntryBy(activity.getOrderNetId());
	    if (!order.getType().equals(GigaConstants.ORD_TYPE_MAINTENANCE)) {
		String orderJobType = order.getParameterBy(GigaConstants.ORD_PARAM_JOB_TYPE).getValueAsString();
		if (orderJobType.equalsIgnoreCase(GigaConstants.ORDER_TYPE_LOCAL)) {
		    return true;
		}
	    }
	}
	return false;
    }

    public static HashMap<Resource, Double> sortByValues(HashMap<Resource, Double> map) {
	List<Entry<Resource, Double>> list = new LinkedList<Entry<Resource, Double>>(map.entrySet());
	List<Object> keySet = new ArrayList<Object>();
	List<Object> valueSet = new ArrayList<Object>();
	// Defined Custom Comparator here
	Collections.sort(list, new Comparator<Map.Entry<Resource, Double>>() {
	    public int compare(Map.Entry<Resource, Double> o1, Map.Entry<Resource, Double> o2) {
		return (o1.getValue()).compareTo((o2.getValue()));
	    }
	});
	// Here I am copying the sorted list in HashMap
	// using LinkedHashMap to preserve the insertion order
	HashMap<Resource, Double> sortedHashMap = new LinkedHashMap<Resource, Double>();
	for (Iterator<Entry<Resource, Double>> it = list.iterator(); it.hasNext();) {
	    Map.Entry<Resource, Double> entry = it.next();
	    sortedHashMap.put(entry.getKey(), entry.getValue());
	    keySet.add(entry.getKey());
	    valueSet.add(entry.getValue());
	}
	return sortedHashMap;
    }

    public static HashMap<Double, List<Resource>> populateMap(Double key, Resource resource, HashMap<Double, List<Resource>> existingMap) {
	//HashMap<Double, List<Resource>> finalMap = new HashMap<Double, List<Resource>>();
	if (existingMap.containsKey(key)) {
	    List<Resource> tmpList = existingMap.get(key);
	    tmpList.add(resource);
	    existingMap.put(key, tmpList);
	} else {
	    existingMap.put(key, new ArrayList<Resource>(Arrays.asList(resource)));
	}
	return existingMap;
    }

    public static Map<Resource, Double> sortByPriority(TreeMap<Double, List<Resource>> driverLoadMap, TreeMap<Double, List<Resource>> distanceMap, TreeMap<Double, List<Resource>> overAllDeadMilMap, TreeMap<Double, List<Resource>> utilizationMap, double driverLoadFactor, double minDistWeightageFactor, double odmWeightageFactor, double fleetUtilWeightageFactor) {
	HashMap<Resource, Double> sum = new HashMap<Resource, Double>();
	List<Resource> tempResourceList = new ArrayList<Resource>();
	for (Map.Entry<Double, List<Resource>> me : distanceMap.entrySet()) {
	    tempResourceList.addAll(me.getValue());
	}
	for (Resource res : tempResourceList) {
	    double overallWeightage = 0;
	    int rank = 0;
	    for (Map.Entry<Double, List<Resource>> me : driverLoadMap.entrySet()) {
		rank++;
		if (me.getValue().contains(res)) {
		    overallWeightage += rank * driverLoadFactor;
		    break;
		}
	    }
	    rank = 0;
	    for (Map.Entry<Double, List<Resource>> me : distanceMap.entrySet()) {
		rank++;
		if (me.getValue().contains(res)) {
		    overallWeightage += rank * minDistWeightageFactor;
		    break;
		}
	    }
	    rank = 0;
	    for (Map.Entry<Double, List<Resource>> me : overAllDeadMilMap.entrySet()) {
		rank++;
		if (me.getValue().contains(res)) {
		    overallWeightage += rank * odmWeightageFactor;
		    break;
		}
	    }
	    rank = 0;
	    for (Map.Entry<Double, List<Resource>> me : utilizationMap.entrySet()) {
		rank++;
		if (me.getValue().contains(res)) {
		    overallWeightage += rank * fleetUtilWeightageFactor;
		    break;
		}
	    }
	    rank = 0;
	    sum.put(res, overallWeightage);
	}
	Map<Resource, Double> sortedMap = GigaUtils.sortByValues(sum);
	return sortedMap;
    }

    public static HashMap<Resource, Double> getSortedMap(Map<Resource, Double> map1, HashMap<Resource, Double> map2) {
	HashMap<Resource, Double> sortedMap = new HashMap<Resource, Double>();
	for (Resource res : map1.keySet()) {
	    Iterator<Entry<Resource, Double>> iterator = map2.entrySet().iterator();
	    while (iterator.hasNext()) {
		Map.Entry<Resource, Double> me = iterator.next();
		if (me.getKey().equals(res)) {
		    sortedMap.put(me.getKey(), me.getValue());
		}
	    }
	}
	return sortedMap;
    }

    public static List<Order> getOrderSequence(String pickup, List<Order> Orders) {
	logger.debug("start");
	List<Order> sortOrders = new ArrayList<Order>();
	Map<Order, String> orderLocations = new HashMap<Order, String>();
	List<String> route = new ArrayList<String>();
	List<String> Hubs = new ArrayList<String>();
	for (Order o : Orders) {
	    String delivery = o.getParameterBy("deliveryLocation").getValueAsString();
	    if (!Hubs.contains(delivery))
		Hubs.add(delivery);
	    logger.debug("delivery-->" + delivery);
	    orderLocations.put(o, delivery);
	}
	logger.debug("deliveryHubs: " + Hubs.size() + Hubs);
	String newDrop = null;
	while (Hubs.size() > 1) {
	    long min = Long.MAX_VALUE;
	    for (int i = 0; i < Hubs.size(); i++) {
		String s = Hubs.get(i);
		long dur = GigaUtils.getDuration(pickup, s);
		if (min > dur) {
		    min = dur;
		    newDrop = s;
		}
	    }
	    logger.debug("newDrop" + newDrop);
	    route.add(newDrop);
	    Hubs.remove(newDrop);
	    logger.debug("left->" + Hubs);
	    pickup = newDrop;
	}
	route.add(Hubs.get(0));
	logger.debug("route->" + route);
	for (String p : route) {
	    for (Order o : Orders) {
		if (o.getParameterBy("deliveryLocation").getValueAsString().equalsIgnoreCase(p))
		    sortOrders.add(o);
	    }
	}
	logger.debug("sortOrders" + sortOrders);
	return sortOrders;
    }

    public static boolean upgradeTruck() {
	Resource gigaParamRes = RSPContainerHelper.getResourceMap(true).getEntryBy(GigaConstants.RES_GIGA_PARAM);
	if (gigaParamRes.hasParameter(GigaConstants.GIGA_PARAM_TRUCK_UPGRADE)) {
	    Double allowTruckUpgrade = ((FloatParameter) gigaParamRes.getParameterBy(GigaConstants.GIGA_PARAM_TRUCK_UPGRADE)).getValue();
	    if (allowTruckUpgrade == 1)
		return true;
	    else
		return false;
	}
	return false;
    }

    public static void unplanOrders(Order existOrder) {
	logger.debug("Unplanned order started");
	PlanningState state = existOrder.getState();
	if (state.toString().equalsIgnoreCase(GigaConstants.PLANNED)) {
	    ElementMap<Workflow> workflowMap = RSPContainerHelper.getWorkflowMap(true);
	    // call the delete workflow command
	    Workflow workflow = workflowMap.getEntryBy(existOrder.getId());
	    // check if the order is closed
	    if (existOrder.getProcessingState() == ProcessingState.OPEN) {
		// clear the planned order
		RSPTransaction tx = FeatherliteAgent.getTransaction(GigaUtils.class);
		ClearWorkflowCommand command = new ClearWorkflowCommand();
		command.setWorkflow(workflow);
		tx.addCommand(command);
		tx.commit();
		evictOrder(existOrder);
	    }
	}
	logger.debug("Unplanned order completed");
    }

    public static void planOrder(Order existOrder) {
	logger.debug("planned order started");
	// plan order and the change the status
	try {
	    RSPTransaction tx = FeatherliteAgent.getTransaction(GigaUtils.class);
	    PlanOrderCommand planOrder1 = new PlanOrderCommand();
	    planOrder1.setOrder(existOrder);
	    tx.addCommand(planOrder1);
	    tx.commit();
	} catch (Exception e) {
	    e.printStackTrace();
	    logger.debug("Exception in planned order command" + e.getMessage());
	}
	logger.debug("planned order completed");
    }

    public static void addOrder(Order order) {
	logger.debug("Add order started");
	RSPTransaction tx = FeatherliteAgent.getTransaction(GigaUtils.class);
	AddOrderCommand addOrderCmd = new AddOrderCommand();
	addOrderCmd.setOrder(order);
	tx.addCommand(addOrderCmd);
	tx.commit();
	logger.debug("Add order completed");
    }

    public static void changeDbStatus(Order existOrder) {
	if (existOrder.hasParameter("uploadstatus")) {
	    logger.debug("inside changeDbStatus");
	    IntegerParameter uploadStatus = (IntegerParameter) existOrder.getParameterBy("uploadstatus");
	    uploadStatus.setValue(0);
	    logger.debug("changeDbStatus completed");
	}
    }

    public static void evictOrder(Order existOrder) {
	logger.debug("inside evictOrder");
	RSPTransaction tx = FeatherliteAgent.getTransaction(GigaUtils.class);
	EvictOrderToCreatedCommand evictOrderCommand = new EvictOrderToCreatedCommand();
	evictOrderCommand.setOrder(existOrder);
	tx.addCommand(evictOrderCommand);
	tx.commit();
	logger.debug("evictOrder completed");
    }

    public static void removeOrder(Order order) {
	logger.debug("inside removeOrder");
	RSPTransaction tx = FeatherliteAgent.getTransaction(GigaUtils.class);
	RemoveOrderCommand removeOrderCommand = new RemoveOrderCommand();
	removeOrderCommand.setOrder(order);
	tx.addCommand(removeOrderCommand);
	tx.commit();
	logger.debug("removeOrder completed");
    }

    public static long getAverageDurationForCoLocatedBaseLocation(String CurrLoc, String pickupLocation) {
	long totalDuration = 0;
	long averageDuration = 0;
	Resource coLocatedBaseRes = RSPContainerHelper.getResourceMap(true).getEntryBy(GigaConstants.RES_COLOCATED_BASE_LOCATION);
	if (coLocatedBaseRes.hasParameter(CurrLoc)) {
	    List<String> coLocatedBaseList = ((StringListParameter) coLocatedBaseRes.getParameterBy(CurrLoc)).getValue();
	    for (String coLocatedBase : coLocatedBaseList) {
		totalDuration = totalDuration + getDuration(coLocatedBase, pickupLocation);
	    }
	    averageDuration = totalDuration / coLocatedBaseList.size();
	} else {
	    averageDuration = getDuration(CurrLoc, pickupLocation);
	}
	return averageDuration;
    }

    public static long getDurationByDrivingFactor(long actualDuration) {
	Resource gigaParameters = RSPContainerHelper.getResourceMap(true).getEntryBy(GigaConstants.RES_GIGA_PARAM);
	Double truckDrivingFactor = ((FloatParameter) gigaParameters.getParameterBy(GigaConstants.GIGA_PARAM_TRUCK_DRIVING_FACTOR)).getValue();
	long result = (long) (actualDuration * 1000L * truckDrivingFactor);
	if (result <= 0) {
	    Double durPickupAndDropSame = ((FloatParameter) gigaParameters.getParameterBy(GigaConstants.GIGA_PARAM_SAME_PICK_DROP)).getValue();
	    result = (long) (durPickupAndDropSame * 60000);
	}
	return result;
    }

    public static void addPreviousTruckDriver(Order existOrder, String truck, String driver) {
	StringParameter prevTruck = new StringParameter(GigaConstants.ORDER_PARAM_PREV_TRUCK_ID, "Previous Truck", truck);
	StringParameter prevDriver = new StringParameter(GigaConstants.ORDER_PARAM_PREV_DRIVER_ID, "Previous Driver", driver);
	existOrder.addParameter(prevTruck);
	existOrder.addParameter(prevDriver);
    }

    public static void clearDriverLoadStatus(Order order) {
	if (order != null) {
	    if (order.hasParameter("driverId")) {
		String driverId = order.getParameterBy("driverId").getValueAsString();
		Resource driverRes = RSPContainerHelper.getResourceMap(true).getEntryBy(driverId);
		if (driverRes != null) {
		    if (order.getParameterBy(GigaConstants.ORD_PARAM_JOB_TYPE).getValueAsString().equalsIgnoreCase(GigaConstants.ORDER_TYPE_LOCAL)) {
			if (driverRes.hasParameter(GigaConstants.NO_OF_LOCAL_ORDERS))
			    ((IntegerParameter) driverRes.getParameterBy(GigaConstants.NO_OF_LOCAL_ORDERS)).setValue(((IntegerParameter) driverRes.getParameterBy(GigaConstants.NO_OF_LOCAL_ORDERS)).getValue() - 1);
			if (driverRes.hasParameter(GigaConstants.NO_OF_LOCAL_ORDERS_CD))
			    ((IntegerParameter) driverRes.getParameterBy(GigaConstants.NO_OF_LOCAL_ORDERS_CD)).setValue(((IntegerParameter) driverRes.getParameterBy(GigaConstants.NO_OF_LOCAL_ORDERS_CD)).getValue() - 1);
		    }
		    if (order.getParameterBy(GigaConstants.ORD_PARAM_JOB_TYPE).getValueAsString().equalsIgnoreCase(GigaConstants.ORDER_TYPE_OUTSTATION)) {
			if (driverRes.hasParameter(GigaConstants.NO_OF_OUTSTATION_ORDERS))
			    ((IntegerParameter) driverRes.getParameterBy(GigaConstants.NO_OF_OUTSTATION_ORDERS)).setValue(((IntegerParameter) driverRes.getParameterBy(GigaConstants.NO_OF_OUTSTATION_ORDERS)).getValue() - 1);
			if (driverRes.hasParameter(GigaConstants.NO_OF_OUTSTATION_ORDERS_CD))
			    ((IntegerParameter) driverRes.getParameterBy(GigaConstants.NO_OF_OUTSTATION_ORDERS_CD)).setValue(((IntegerParameter) driverRes.getParameterBy(GigaConstants.NO_OF_OUTSTATION_ORDERS_CD)).getValue() - 1);
		    }
		}
	    }
	}
    }

    public static void addParamLocalOS(Order order, String truckId, boolean isOSDoneLocally) {
	if (order.hasParameter(GigaConstants.ORDER_PARAM_ISOSDONELOCALLY)) {
	    ((BooleanMapParameter) order.getParameterBy(GigaConstants.ORDER_PARAM_ISOSDONELOCALLY)).getValue().put(truckId, isOSDoneLocally);
	} else {
	    Map<String, Boolean> booleanMapValue = new HashMap<String, Boolean>();
	    booleanMapValue.put(truckId, isOSDoneLocally);
	    BooleanMapParameter isOSDoneLocallyP = new BooleanMapParameter(GigaConstants.ORDER_PARAM_ISOSDONELOCALLY, "Is outstation done locally or not for each truck level", booleanMapValue);
	    order.addParameter(isOSDoneLocallyP);
	}
    }

    public static Map<String, List<PlacementResult>> segregateOSOrderPlanType(List<PlacementResult> placements, Order order) {
	Map<String, List<PlacementResult>> placementsMap = new HashMap<String, List<PlacementResult>>();
	Map<String, Boolean> osOrderPlanTypeTruckWise = ((BooleanMapParameter) order.getParameterBy(GigaConstants.ORDER_PARAM_ISOSDONELOCALLY)).getValue();
	List<PlacementResult> plannedLocalList = new ArrayList<PlacementResult>();
	List<PlacementResult> plannedOSList = new ArrayList<PlacementResult>();
	for (PlacementResult placementResult : placements) {
	    String TruckId = placementResult.getResource().getId();
	    boolean isOSDoneLocally = osOrderPlanTypeTruckWise.get(TruckId);
	    if (isOSDoneLocally)
		plannedLocalList.add(placementResult);
	    else
		plannedOSList.add(placementResult);
	}
	placementsMap.put(GigaConstants.ORDER_TYPE_LOCAL, plannedLocalList);
	placementsMap.put(GigaConstants.ORDER_TYPE_OUTSTATION, plannedOSList);
	return placementsMap;
    }

    public static String getLocationLatLong(String location) {
	String loationLatLong = "";
	Resource latLongRes = RSPContainerHelper.getResourceMap(true).getEntryBy(location);
	if (latLongRes != null) {
	    loationLatLong = latLongRes.getParameterBy(GigaConstants.ROUTE_PARAM_LATITUDE).getValueAsString();
	    loationLatLong = loationLatLong.concat(" | ");
	    loationLatLong = loationLatLong.concat(latLongRes.getParameterBy(GigaConstants.ROUTE_PARAM_LONGITUDE).getValueAsString());
	}
	return loationLatLong;
    }

    public static String getPlanningLog(String planningLog, Resource truckResource, String currLoc, long earliestAvailTime, long currToPickupDur, String logMessage) {
	StringBuilder planningLogSB = new StringBuilder();
	final String SEPERATOR_TILDA = "~";
	final String SEPERATOR_COMMA = ",";
	planningLogSB.append(planningLog);
	planningLogSB.append(SEPERATOR_TILDA);
	planningLogSB.append(truckResource.getId());
	planningLogSB.append(SEPERATOR_COMMA);
	planningLogSB.append(truckResource.getParameterBy(GigaConstants.RES_PARAM_TRUCK_REG_NO).getValueAsString());
	planningLogSB.append(SEPERATOR_COMMA);
	planningLogSB.append(truckResource.getParameterBy(GigaConstants.RES_PARAM_DRIVERID).getValueAsString());
	planningLogSB.append(SEPERATOR_COMMA);
	planningLogSB.append(RSPContainerHelper.getResourceMap(true).getEntryBy(truckResource.getParameterBy(GigaConstants.RES_PARAM_DRIVERID).getValueAsString()).getName());
	planningLogSB.append(SEPERATOR_COMMA);
	planningLogSB.append(truckResource.getParameterBy(GigaConstants.RES_PARAM_LOCATION).getValueAsString());
	planningLogSB.append(SEPERATOR_COMMA);
	planningLogSB.append(getLocationLatLong(truckResource.getParameterBy(GigaConstants.RES_PARAM_LOCATION).getValueAsString()));
	planningLogSB.append(SEPERATOR_COMMA);
	planningLogSB.append(truckResource.getParameterBy(GigaConstants.RES_PARAM_MAKE).getValueAsString());
	planningLogSB.append(SEPERATOR_COMMA);
	planningLogSB.append(currLoc);
	planningLogSB.append(SEPERATOR_COMMA);
	planningLogSB.append(getLocationLatLong(currLoc));
	planningLogSB.append(SEPERATOR_COMMA);
	planningLogSB.append(convertLongToTime(earliestAvailTime));
	planningLogSB.append(SEPERATOR_COMMA);
	planningLogSB.append(convertDurationToTime(ISO8601FormatFactory.getInstance().getDurationFormat().format(currToPickupDur).toString()));
	planningLogSB.append(SEPERATOR_COMMA);
	planningLogSB.append(logMessage);
	planningLogSB.append(SEPERATOR_COMMA);
	return planningLogSB.toString();
    }

    public static String convertLongToTime(long dateToConvert) {
	String result = null;
	SimpleDateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
	result = df.format(dateToConvert);
	return result;
    }

    public static String convertDurationToTime(String durationToConvert) {
	String result = durationToConvert;
	result = (((result.replace("PT", "")).replace("H", " Hours ")).replace("M", " Minutes ")).replace("S", " Seconds ");
	return result;
    }

    public static void addParamLocalOS(Order order, boolean isOSDoneLocally) {
	BooleanParameter isOSDoneLocallyP = new BooleanParameter(GigaConstants.ORDER_PARAM_ISOSDONELOCALLY, "Is outstation done locally or not", isOSDoneLocally);
	order.addParameter(isOSDoneLocallyP);
    }

    public static void unplanOrders(List<Activity> prevTasks, Connection conn) {
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
    /*public static boolean orderPushPossible(Resource truckRes, String placeTo) {
    List<Activity> activityList = truckRes.getActivitiesInInterval(System.currentTimeMillis(), java.lang.Long.MAX_VALUE);
    Map<Double, String> distanceMap = new TreeMap<Double, String>();
    for (Activity activity : activityList) {
        Long activityEndTime = activity.getEnd();
        String activityDelLocation = ((StringState) truckRes.getStateVariableBy(GigaConstants.STATE_LOCATION)).getValueAt(activityEndTime).getValueAsString();
        Resource placeFromRes = RSPContainerHelper.getResourceMap(true).getEntryBy(activityDelLocation);
        Resource gigaParameters = RSPContainerHelper.getResourceMap(true).getEntryBy(GigaConstants.RES_GIGA_PARAM);
        Double durationCalculator = ((FloatParameter) gigaParameters.getParameterBy(GigaConstants.GIGA_PARAM_DUR_CALCULATION)).getValue();
        Double distance = 0.0;
        if (durationCalculator < 3) {
    	if (placeFromRes.getParameterBy(placeTo) == null) {
    	    Resource placeToRes = RSPContainerHelper.getResourceMap(true).getEntryBy(placeTo);
    	    String[] currToPickupDuration = GigaUtils.getDynamicDuration(placeFromRes, placeToRes).split(",");
    	    distance = Double.parseDouble(currToPickupDuration[0]);
    	} else {
    	    String[] MatrixValues = placeFromRes.getParameterBy(placeTo).getValueAsString().split(",");
    	    logger.info("MatrixValues[1]-->" + MatrixValues[1] + "::placeFrom::" + activityDelLocation + "::placeTo-->" + placeTo);
    	    distance = Double.parseDouble(MatrixValues[0]);
    	    logger.info("result: " + distance);
    	}
        }
        distanceMap.put(distance, activity.getOrderNetId());
    }
    String nearestLocation = distanceMap.entrySet().iterator().next().getValue();
    Double nearestLocationDistance = distanceMap.entrySet().iterator().next().getKey();
    if (nearestLocationDistance < 20000) {
        return true;
    } else {
        return false;
    }
    }

    public static void pushOrderInBetween(Resource truckRes, String placeTo) {
    if (orderPushPossible(truckRes, placeTo)) {
        List<Activity> activityList = truckRes.getActivitiesInInterval(System.currentTimeMillis(), java.lang.Long.MAX_VALUE);
        activityList.removeAll(truckRes.getActivitiesAt(System.currentTimeMillis()));
        Connection conn = null;
        unplanOrders(activityList, conn);
    }
    }

    public static boolean orderPushPossiblee(Resource truckRes, String placeTo, Order currOrder) {
    List<Activity> tailActivityList = truckRes.getActivitiesInInterval(System.currentTimeMillis(), java.lang.Long.MAX_VALUE);
    Activity curActivity = truckRes.getActivitiesAt(System.currentTimeMillis()).get(0);
    Order firstOrder = RSPContainerHelper.getOrderMap(true).getEntryBy(curActivity.getOrderNetId());
    String currentLocation = firstOrder.getParameterBy(GigaConstants.ORD_PARAM_DELIVERY_LOATION).getValueAsString();
    String pickupLocation = currOrder.getParameterBy(GigaConstants.ORD_PARAM_PICKUP_LOCATION).getValueAsString();
    String deliveryLocation = currOrder.getParameterBy(GigaConstants.ORD_PARAM_DELIVERY_LOATION).getValueAsString();
    Map<Double, String> distanceMap = new TreeMap<Double, String>();
    for (Activity activity : tailActivityList) {
        Long activityEndTime = activity.getEnd();
        String activityDelLocation = ((StringState) truckRes.getStateVariableBy(GigaConstants.STATE_LOCATION)).getValueAt(activityEndTime).getValueAsString();
        Resource placeFromRes = RSPContainerHelper.getResourceMap(true).getEntryBy(activityDelLocation);
        Resource gigaParameters = RSPContainerHelper.getResourceMap(true).getEntryBy(GigaConstants.RES_GIGA_PARAM);
        Double durationCalculator = ((FloatParameter) gigaParameters.getParameterBy(GigaConstants.GIGA_PARAM_DUR_CALCULATION)).getValue();
        Double distance = 0.0;
        if (durationCalculator < 3) {
    	if (placeFromRes.getParameterBy(placeTo) == null) {
    	    Resource placeToRes = RSPContainerHelper.getResourceMap(true).getEntryBy(placeTo);
    	    String[] currToPickupDuration = GigaUtils.getDynamicDuration(placeFromRes, placeToRes).split(",");
    	    distance = Double.parseDouble(currToPickupDuration[0]);
    	} else {
    	    String[] MatrixValues = placeFromRes.getParameterBy(placeTo).getValueAsString().split(",");
    	    logger.info("MatrixValues[1]-->" + MatrixValues[1] + "::placeFrom::" + activityDelLocation + "::placeTo-->" + placeTo);
    	    distance = Double.parseDouble(MatrixValues[0]);
    	    logger.info("result: " + distance);
    	}
        }
        distanceMap.put(distance, activity.getOrderNetId());
    }
    String nearestLocation = distanceMap.entrySet().iterator().next().getValue();
    Double nearestLocationDistance = distanceMap.entrySet().iterator().next().getKey();
    if (nearestLocationDistance < 20000) {
        return true;
    } else {
        return false;
    }
    }

    public static Map<String, Double> getDeadMilage(List<PlacementResult> placements) {
    Map<String, Double> distancetobase = new HashMap<String, Double>();
    Double finaldistance = 0.0;
    Double distance = 0.0;
    Map<String, Double> truckDetails = new HashMap<String, Double>();
    for (PlacementResult placementResult1 : placements) {
        String masterTruckId = placementResult1.getResource().getId();
        for (PlacementResult placementResult2 : placements) {
    	Resource truckRes = placementResult2.getResource();
    	if (!masterTruckId.equalsIgnoreCase(truckRes.getId())) {
    	    String currentlocation = ((StringState) truckRes.getStateVariableBy(GigaConstants.STATE_LOCATION)).getValueAt(java.lang.Long.MAX_VALUE).getValueAsString();
    	    Resource placeFromRes = RSPContainerHelper.getResourceMap(true).getEntryBy(currentlocation);
    	    String baselocation = (String) truckRes.getParameterBy(GigaConstants.RES_PARAM_LOCATION).getValue();
    	    Resource gigaParameters = RSPContainerHelper.getResourceMap(true).getEntryBy(GigaConstants.RES_GIGA_PARAM);
    	    Double durationCalculator = ((FloatParameter) gigaParameters.getParameterBy(GigaConstants.GIGA_PARAM_DUR_CALCULATION)).getValue();
    	    if (durationCalculator < 3) {
    		if (placeFromRes.getParameterBy(baselocation) == null) {
    		    Resource placeToRes = RSPContainerHelper.getResourceMap(true).getEntryBy(baselocation);
    		    String[] currToPickupDuration = GigaUtils.getDynamicDuration(placeFromRes, placeToRes).split(",");
    		    distance = Double.parseDouble(currToPickupDuration[0]);
    		    logger.info("deadmilage: " + distance);
    		} else {
    		    String[] MatrixValues = placeFromRes.getParameterBy(currentlocation).getValueAsString().split(",");
    		    logger.info("MatrixValues[1]deadmilege-->" + MatrixValues[1] + "::placeFrom::" + placeFromRes + "::placeTo-->" + baselocation);
    		    distance = Double.parseDouble(MatrixValues[0]);
    		    logger.info("resultdeadmilage: " + distance);
    		}
    		finaldistance += distance;
    		logger.info("finaldistance: " + finaldistance);
    	    }
    	}
        }
        truckDetails.put(masterTruckId, finaldistance);
        finaldistance = 0.0;
    }
    for (Entry<String, Double> entry : truckDetails.entrySet()) {
        logger.info("Key = " + entry.getKey() + ", Value = " + entry.getValue());
        String key = entry.getKey();
        Double value = entry.getValue();
        distancetobase.put(key, value);
    }
    return distancetobase;
    }*/
}
