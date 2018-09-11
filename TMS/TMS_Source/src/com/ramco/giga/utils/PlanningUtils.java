package com.ramco.giga.utils;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;

import com.ramco.giga.constant.GigaConstants;
import com.rsp.core.base.RSPContainerHelper;
import com.rsp.core.base.model.Activity;
import com.rsp.core.base.model.ElementMap;
import com.rsp.core.base.model.Order;
import com.rsp.core.base.model.Resource;
import com.rsp.core.base.model.constants.PlanningState;
import com.rsp.core.base.model.parameter.FloatParameter;
import com.rsp.core.base.model.parameter.StringListParameter;
import com.rsp.core.base.model.stateVariable.StringState;

public class PlanningUtils {
    private static Logger logger = Logger.getLogger(PlanningUtils.class);

    public static void planOrder(Connection conn) {
	String resType = null;
	String TRUCK = "truck_";
	ElementMap<Order> orderMap = RSPContainerHelper.getOrderMap(true);
	ElementMap<Resource> resourceMap = RSPContainerHelper.getResourceMap(true);
	Resource zoneWiseRes = resourceMap.getEntryBy(GigaConstants.RES_ZONE_WISE_ORDERS);
	List<String> zoneWiseOrdersParamList = new ArrayList<String>();
	zoneWiseOrdersParamList.addAll(zoneWiseRes.getParameterKeySet());
	Collections.sort(zoneWiseOrdersParamList);
	System.out.println("===parameter seq===" + zoneWiseOrdersParamList);
	for (String zoneWiseOrdersParamId : zoneWiseOrdersParamList) {
	    StringListParameter zoneWiseOrdersParam = (StringListParameter) zoneWiseRes.getParameterBy(zoneWiseOrdersParamId);
	    System.out.println("===parameter name===" + zoneWiseOrdersParam);
	    List<String> ordersIds = ((StringListParameter) zoneWiseOrdersParam).getValue();
	    boolean continuePlan = true;
	    if (ordersIds.size() > 0) {
		while (continuePlan) {
		    List<String> subOrderList = new ArrayList<String>();
		    subOrderList.addAll(ordersIds);
		    System.out.println("===ordersIds===" + ordersIds);
		    Map<Double, List<String>> distanceMap = new TreeMap<Double, List<String>>();
		    for (String orderId : subOrderList) {
			Order order = orderMap.getEntryBy(orderId);
			if (order != null) {
			    if (order.getState().compareTo(PlanningState.CREATED) == 0) {
				resType = TRUCK.concat(order.getParameterBy(GigaConstants.ORD_PARAM_BUSINESS_DIVISION).getValueAsString());
				Collection<Resource> truckResList = resourceMap.getByType(resType);
				for (Resource truckRes : truckResList) {
				    Activity activity = null;
				    if (truckRes.getActivitiesAt(System.currentTimeMillis()).size() > 0) {
					activity = truckRes.getActivitiesAt(System.currentTimeMillis()).get(0);
				    }
				    Long activityEndTime = System.currentTimeMillis();
				    if (activity != null)
					activityEndTime = activity.getEnd();
				    String currLocation = ((StringState) truckRes.getStateVariableBy(GigaConstants.STATE_LOCATION)).getValueAt(activityEndTime).getValueAsString();
				    String pickupLocation = order.getParameterBy(GigaConstants.ORD_PARAM_PICKUP_LOCATION).getValueAsString();
				    Resource placeFromRes = RSPContainerHelper.getResourceMap(true).getEntryBy(currLocation);
				    Resource gigaParameters = RSPContainerHelper.getResourceMap(true).getEntryBy(GigaConstants.RES_GIGA_PARAM);
				    Double durationCalculator = ((FloatParameter) gigaParameters.getParameterBy(GigaConstants.GIGA_PARAM_DUR_CALCULATION)).getValue();
				    Double distance = 0.0;
				    if (durationCalculator < 3) {
					if (placeFromRes.getParameterBy(pickupLocation) == null) {
					    Resource placeToRes = RSPContainerHelper.getResourceMap(true).getEntryBy(pickupLocation);
					    String[] currToPickupDuration = GigaUtils.getDynamicDuration(placeFromRes, placeToRes).split(",");
					    distance = Double.parseDouble(currToPickupDuration[0]);
					} else {
					    String[] MatrixValues = placeFromRes.getParameterBy(pickupLocation).getValueAsString().split(",");
					    logger.info("MatrixValues[1]-->" + MatrixValues[1] + "::placeFrom::" + currLocation + "::placeTo-->" + pickupLocation);
					    distance = Double.parseDouble(MatrixValues[0]);
					    logger.info("result: " + distance);
					}
				    }
				    if (distanceMap.containsKey(distance)) {
					distanceMap.get(distance).add(order.getId().concat("~").concat(truckRes.getId()));
				    } else {
					List<String> orderIdList = new ArrayList<String>();
					orderIdList.add(order.getId().concat("~").concat(truckRes.getId()));
					distanceMap.put(distance, orderIdList);
				    }
				}
			    } else {
				logger.info("==order already planned==>" + orderId);
				ordersIds.remove(orderId);
				if (ordersIds.size() < 1) {
				    continuePlan = false;
				}
			    }
			} else {
			    logger.info("==order doesnt exists==>" + orderId);
			    ordersIds.remove(orderId);
			    if (ordersIds.size() < 1) {
				continuePlan = false;
			    }
			}
		    }
		    logger.info("==distanceMap==>" + distanceMap);
		    if (distanceMap.size() > 0) {
			String nearestOrderId = distanceMap.entrySet().iterator().next().getValue().get(0).split("~")[0];
			GigaUtils.planOrder(orderMap.getEntryBy(nearestOrderId));
			ordersIds.remove(nearestOrderId);
			if (ordersIds.size() < 1) {
			    continuePlan = false;
			}
		    } else {
			continuePlan = false;
		    }
		}
	    }
	}
    }

    public static void planInbetweenOrders(Connection conn) {
	try {
	    ElementMap<Resource> resourceMap = RSPContainerHelper.getResourceMap(true);
	    Collection<Resource> truckResList = resourceMap.getByType("truck_CR");
	    List<Activity> unplanActivityList = new ArrayList<Activity>();
	    for (Resource truckRes : truckResList) {
		List<Activity> activityList = truckRes.getActivitiesInInterval(System.currentTimeMillis(), java.lang.Long.MAX_VALUE);
		activityList.removeAll(truckRes.getActivitiesAt(System.currentTimeMillis()));
		unplanActivityList.addAll(activityList);
	    }
	    GigaUtils.unplanOrders(unplanActivityList, conn);
	} catch (Exception e) {
	}
    }
}
