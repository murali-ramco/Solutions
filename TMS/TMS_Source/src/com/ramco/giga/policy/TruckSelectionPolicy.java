package com.ramco.giga.policy;

import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.ramco.giga.constant.GigaConstants;
import com.ramco.giga.formula.FindDistanceDuration;
import com.ramco.giga.utils.GigaUtils;
import com.rsp.core.base.RSPContainerHelper;
import com.rsp.core.base.model.Activity;
import com.rsp.core.base.model.ElementMap;
import com.rsp.core.base.model.Order;
import com.rsp.core.base.model.ParameterizedElement;
import com.rsp.core.base.model.Resource;
import com.rsp.core.base.model.WorkflowElement;
import com.rsp.core.base.model.constants.TimeDirection;
import com.rsp.core.base.model.parameter.BooleanParameter;
import com.rsp.core.base.model.parameter.FloatListParameter;
import com.rsp.core.base.model.parameter.FloatParameter;
import com.rsp.core.base.model.parameter.IntegerParameter;
import com.rsp.core.base.model.parameter.StringParameter;
import com.rsp.core.base.model.stateVariable.StateValue;
import com.rsp.core.base.policy.TimeRelation;
import com.rsp.core.planning.policy.PlacementResult;
import com.rsp.core.planning.policy.placement.DefaultPlacementResult;
import com.rsp.core.planning.policy.resourceSelection.JITResourceSelection;

public class TruckSelectionPolicy extends JITResourceSelection {
    private static final long serialVersionUID = 1L;
    String pickup = "";
    String delivery = "";
    boolean isForceLocal = false;
    long pickupTime;
    long deliveryTime;
    double ODM_km;
    double ODM_hrs;
    String pickupRegion = null;

    public PlacementResult selectPlacementResult(WorkflowElement workflowElement, List<PlacementResult> placements) {
	logger.debug("start of truck selection");
	TreeMap<Double, List<Resource>> driverLoadSortedMap = new TreeMap<Double, List<Resource>>();
	TreeMap<Double, List<Resource>> minDistResList = new TreeMap<Double, List<Resource>>();
	TreeMap<Double, List<Resource>> ODMSortedResList = new TreeMap<Double, List<Resource>>();
	TreeMap<Double, List<Resource>> fleetUtilResList = new TreeMap<Double, List<Resource>>();
	ElementMap<Resource> resourceMap = RSPContainerHelper.getResourceMap(true);
	Resource currLatLong = resourceMap.getEntryBy(GigaConstants.RES_TRUCK_LAT_LONG);
	Resource gigaParamRes = resourceMap.getEntryBy(GigaConstants.RES_GIGA_PARAM);
	Double durationCalculator = ((FloatParameter) gigaParamRes.getParameterBy(GigaConstants.GIGA_PARAM_DUR_CALCULATION)).getValue();
	double driverLoadFactor = (Double) gigaParamRes.getParameterBy(GigaConstants.GIGA_PARAM_DRIVER_LOAD).getValue();
	double minDistWeightageFactor = (Double) gigaParamRes.getParameterBy(GigaConstants.GIGA_PARAM_PROXIMITY).getValue();
	double ODMWeightageFactor = (Double) gigaParamRes.getParameterBy(GigaConstants.GIGA_PARAM_DEAD_MILEAGE).getValue();
	double fleetUtilWeightageFactor = (Double) gigaParamRes.getParameterBy(GigaConstants.GIGA_PARAM_FLEET_UTILIZATION).getValue();
	if (placements.size() == 0)
	    return null;
	PlacementResult result = null;
	HashMap<Resource, Double> resDeadMile_hrs = new HashMap<Resource, Double>();
	HashMap<Resource, Double> resDeadMile_km = new HashMap<Resource, Double>();
	HashMap<Resource, Double> durationODM = new HashMap<Resource, Double>();
	HashMap<Resource, Double> distanceODM = new HashMap<Resource, Double>();
	HashMap<Double, List<Resource>> driverLoadMap = new HashMap<Double, List<Resource>>();
	HashMap<Double, List<Resource>> currToPickupMap = new HashMap<Double, List<Resource>>();
	HashMap<Double, List<Resource>> resOverAllDeadMile_Kms = new HashMap<Double, List<Resource>>();
	HashMap<Double, List<Resource>> fleetUtil = new HashMap<Double, List<Resource>>();
	ParameterizedElement tmp = workflowElement;
	String orderNetId = null;
	try {
	    while (tmp != null) {
		orderNetId = tmp.getId();
		tmp = tmp.getParent();
	    }
	    Order order = RSPContainerHelper.getOrderMap(true).getEntryBy(orderNetId);
	    String orderMake = order.getParameterBy(GigaConstants.ORD_PARAM_MAKE).getValueAsString();
	    String orderTruckType = order.getParameterBy(GigaConstants.ORD_PARAM_TRUCK_TYPE).getValueAsString();
	    String orderJobType = order.getParameterBy(GigaConstants.ORD_PARAM_JOB_TYPE).getValueAsString();
	    List<PlacementResult> plannedLocalList = new ArrayList<PlacementResult>();
	    List<PlacementResult> plannedOSList = new ArrayList<PlacementResult>();
	    boolean isSelectionCompleted = false;
	    boolean isBothDone = false;
	    if (orderJobType.equalsIgnoreCase(GigaConstants.ORDER_TYPE_OUTSTATION)) {
		Map<String, List<PlacementResult>> placementsMap = GigaUtils.segregateOSOrderPlanType(placements, order);
		plannedLocalList = placementsMap.get(GigaConstants.ORDER_TYPE_LOCAL);
		plannedOSList = placementsMap.get(GigaConstants.ORDER_TYPE_OUTSTATION);
		logger.debug("local placement size" + plannedLocalList.size());
		logger.debug("OS placement size" + plannedOSList.size());
		if (plannedLocalList.size() > 0)
		    placements = plannedLocalList;
		else
		    placements = plannedOSList;
	    }
	    pickup = order.getParameterBy(GigaConstants.ORD_PARAM_PICKUP_LOCATION).getValueAsString();
	    delivery = order.getParameterBy(GigaConstants.ORD_PARAM_DELIVERY_LOATION).getValueAsString();
	    pickupTime = (Long) order.getParameterBy(GigaConstants.ORD_PARAM_PICKUP_DATE).getValue();
	    deliveryTime = (Long) order.getParameterBy(GigaConstants.ORD_PARAM_DELIVERY_DATE).getValue();
	    Resource pickupRes = resourceMap.getEntryBy(pickup);
	    Resource deliveryRes = resourceMap.getEntryBy(delivery);
	    do {
		isSelectionCompleted = false;
		List<PlacementResult> othersArticulatedTrucks = new ArrayList<PlacementResult>();
		List<PlacementResult> othersRigidTrucks = new ArrayList<PlacementResult>();
		List<PlacementResult> othersSingleTrucks = new ArrayList<PlacementResult>();
		List<PlacementResult> toyotaArticulatedTrucks = new ArrayList<PlacementResult>();
		List<PlacementResult> toyotaRigidTrucks = new ArrayList<PlacementResult>();
		List<PlacementResult> toyotaSingleTrucks = new ArrayList<PlacementResult>();
		List<PlacementResult> isuzuArticulatedTrucks = new ArrayList<PlacementResult>();
		List<PlacementResult> tmpIsuzuArticulatedTrucks = new ArrayList<PlacementResult>();
		List<PlacementResult> isuzuRigidTrucks = new ArrayList<PlacementResult>();
		List<PlacementResult> isuzuSingleTrucks = new ArrayList<PlacementResult>();
		List<PlacementResult> othersArticulatedTWD = new ArrayList<PlacementResult>();
		List<PlacementResult> othersRigidTWD = new ArrayList<PlacementResult>();
		List<PlacementResult> othersSingleTWD = new ArrayList<PlacementResult>();
		List<PlacementResult> toyotaArticulatedTWD = new ArrayList<PlacementResult>();
		List<PlacementResult> toyotaRigidTWD = new ArrayList<PlacementResult>();
		List<PlacementResult> toyotaSingleTWD = new ArrayList<PlacementResult>();
		List<PlacementResult> isuzuArticulatedTWD = new ArrayList<PlacementResult>();
		List<PlacementResult> isuzuRigidTWD = new ArrayList<PlacementResult>();
		List<PlacementResult> isuzuSingleTWD = new ArrayList<PlacementResult>();
		List<PlacementResult> isuzuArtWithToyotaDriver = new ArrayList<PlacementResult>();
		List<PlacementResult> isuzuRigidWithToyotaDriver = new ArrayList<PlacementResult>();
		List<PlacementResult> othersArtWithToyotaDriver = new ArrayList<PlacementResult>();
		List<PlacementResult> othersRigidWithToyotaDriver = new ArrayList<PlacementResult>();
		List<PlacementResult> truckListDoneOutstation = new ArrayList<PlacementResult>();
		List<PlacementResult> truckDoneLocalNoPrevOutStation = new ArrayList<PlacementResult>();
		List<String> toyotaDriverList = new ArrayList<String>();
		toyotaDriverList = GigaUtils.getToyotaDriversList();
		String truckMake;
		String truckType;
		List<PlacementResult> placementResultTWD = new ArrayList<PlacementResult>();
		logger.info("Initial placements size: " + placements.size() + "; and Placements: " + placements.toString());
		for (PlacementResult placementResult : placements) {
		    Resource truckResource = placementResult.getResource();
		    String driverId = truckResource.getParameterBy(GigaConstants.RES_PARAM_DRIVERID).getValueAsString();
		    truckType = truckResource.getParameterBy(GigaConstants.ORD_PARAM_TRUCK_TYPE).getValueAsString();
		    truckMake = truckResource.getParameterBy(GigaConstants.RES_PARAM_MAKE).getValueAsString();
		    logger.debug("truckResource ID: " + truckResource.getId());
		    if (!driverId.equalsIgnoreCase("TBA")) {
			if (!truckMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_ISUZU) && !truckMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_TOYOTA)) {
			    if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_ARTICULATED))
				othersArticulatedTrucks.add(placementResult);
			    else if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_RIGID))
				othersRigidTrucks.add(placementResult);
			    else if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_SINGLE))
				othersSingleTrucks.add(placementResult);
			}
			if (truckMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_TOYOTA)) {
			    if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_ARTICULATED))
				toyotaArticulatedTrucks.add(placementResult);
			    else if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_RIGID))
				toyotaRigidTrucks.add(placementResult);
			    else if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_SINGLE))
				toyotaSingleTrucks.add(placementResult);
			}
			if (truckMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_ISUZU)) {
			    if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_ARTICULATED))
				isuzuArticulatedTrucks.add(placementResult);
			    else if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_RIGID))
				isuzuRigidTrucks.add(placementResult);
			    else if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_SINGLE))
				isuzuSingleTrucks.add(placementResult);
			}
		    }
		    if (driverId.equalsIgnoreCase("TBA")) {
			placementResultTWD.add(placementResult);
			Resource spareDriver = GigaUtils.getSpareDriver(truckResource, placementResult.getStart(), orderJobType);
			if (spareDriver != null) {
			    if (!truckMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_ISUZU) && !truckMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_TOYOTA)) {
				if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_ARTICULATED))
				    othersArticulatedTWD.add(placementResult);
				else if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_RIGID))
				    othersRigidTWD.add(placementResult);
				else if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_SINGLE))
				    othersSingleTWD.add(placementResult);
			    }
			    if (truckMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_TOYOTA)) {
				if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_ARTICULATED))
				    toyotaArticulatedTWD.add(placementResult);
				else if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_RIGID))
				    toyotaRigidTWD.add(placementResult);
				else if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_SINGLE))
				    toyotaSingleTWD.add(placementResult);
			    }
			    if (truckMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_ISUZU)) {
				if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_ARTICULATED))
				    isuzuArticulatedTWD.add(placementResult);
				else if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_RIGID))
				    isuzuRigidTWD.add(placementResult);
				else if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_SINGLE))
				    isuzuSingleTWD.add(placementResult);
			    }
			}
		    } else {
			Resource driverRes = resourceMap.getEntryBy(driverId);
			if (truckMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_ISUZU)) {
			    if (toyotaDriverList.contains(driverId)) {
				if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_ARTICULATED))
				    isuzuArtWithToyotaDriver.add(placementResult);
				else if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_RIGID))
				    isuzuRigidWithToyotaDriver.add(placementResult);
			    }
			}
			if (truckMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_OTHERS)) {
			    if (toyotaDriverList.contains(driverId)) {
				if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_ARTICULATED))
				    othersArtWithToyotaDriver.add(placementResult);
				else if (truckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_RIGID))
				    othersRigidWithToyotaDriver.add(placementResult);
			    }
			}
			boolean isPrevDayOutstation = false;
			if (driverRes.hasParameter(GigaConstants.DRIVER_RES_PARAM_PREV_DAY_OUTSTATION))
			    isPrevDayOutstation = ((BooleanParameter) driverRes.getParameterBy(GigaConstants.DRIVER_RES_PARAM_PREV_DAY_OUTSTATION)).getValue();
			if (isPrevDayOutstation) {
			    truckListDoneOutstation.add(placementResult);
			} else {
			    if (GigaUtils.isDoneAnylocal(truckResource))
				truckDoneLocalNoPrevOutStation.add(placementResult);
			}
		    }
		}
		placements.removeAll(placementResultTWD);
		pickupRegion = resourceMap.getEntryBy(pickup).getName();
		boolean prefSameRegionTrucks = false;
		prefSameRegionTrucks = ((BooleanParameter) gigaParamRes.getParameterBy(GigaConstants.GIGA_PARAM_SAME_REGION_TRUCK)).getValue();
		if (prefSameRegionTrucks) {
		    List<PlacementResult> placementResultSameRegion = new ArrayList<PlacementResult>();
		    for (PlacementResult pr : placements) {
			Resource res = pr.getResource();
			String CurrentLoc = res.getStateVariableBy(GigaConstants.STATE_LOCATION).getValueAt(java.lang.Long.MAX_VALUE).getValueAsString();
			logger.debug("===CurrentLoc==" + res.getId());
			Resource CurrentLocRes = RSPContainerHelper.getResourceMap(true).getEntryBy(CurrentLoc);
			String truckRegion = CurrentLocRes.getName();
			if (truckRegion.equalsIgnoreCase(pickupRegion)) {
			    placementResultSameRegion.add(pr);
			}
		    }
		    if (placementResultSameRegion.size() > 0) {
			placements.clear();
			placements.addAll(placementResultSameRegion);
		    }
		}
		double allowOthersForIsuzu = (Double) gigaParamRes.getParameterBy(GigaConstants.ALLOW_OTHERS_ISUZU).getValue();
		if (orderMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_OTHERS)) {
		    if (placements.size() < 1)
			placements.addAll(placementResultTWD);
		} else if ((!pickupRegion.equalsIgnoreCase(GigaConstants.CENTRAL_REGION) && orderJobType.equalsIgnoreCase(GigaConstants.ORDER_TYPE_OUTSTATION))) {
		    if (placements.size() < 1)
			placements.addAll(placementResultTWD);
		} else if (orderMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_ISUZU) && allowOthersForIsuzu == 1.0) {
		    if (placements.size() < 1)
			placements.addAll(placementResultTWD);
		} else {
		    if (orderMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_OTHERS) && orderTruckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_RIGID)) {
			placements.removeAll(othersRigidTWD);
			placements.removeAll(isuzuRigidTrucks);
			placements.removeAll(toyotaRigidTrucks);
			if (placements.size() < 1)
			    placements.addAll(othersRigidTWD);
			if (placements.size() < 1) {
			    isuzuRigidTrucks.removeAll(isuzuRigidTWD);
			    placements.addAll(isuzuRigidTrucks);
			}
			if (placements.size() < 1)
			    placements.addAll(isuzuRigidTWD);
			if (placements.size() < 1) {
			    toyotaRigidTrucks.removeAll(toyotaRigidTWD);
			    placements.addAll(toyotaRigidTrucks);
			}
			if (placements.size() < 1)
			    placements.addAll(toyotaRigidTWD);
		    }
		    if (orderMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_ISUZU) && orderTruckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_RIGID)) {
			placements.removeAll(othersRigidTrucks);
			placements.removeAll(isuzuRigidTWD);
			placements.removeAll(toyotaRigidTrucks);
			if (placements.size() < 1)
			    placements.addAll(isuzuRigidTWD);
			if (placements.size() < 1) {
			    othersRigidTrucks.removeAll(othersRigidTWD);
			    placements.addAll(othersRigidTrucks);
			}
			if (placements.size() < 1)
			    placements.addAll(othersRigidTWD);
			if (placements.size() < 1) {
			    toyotaRigidTrucks.removeAll(toyotaRigidTWD);
			    placements.addAll(toyotaRigidTrucks);
			}
			if (placements.size() < 1)
			    placements.addAll(toyotaRigidTWD);
		    }
		    if (orderMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_TOYOTA) && orderTruckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_RIGID)) {
			placements.removeAll(othersRigidWithToyotaDriver);
			placements.removeAll(isuzuRigidWithToyotaDriver);
			placements.removeAll(othersRigidTrucks);
			placements.removeAll(isuzuRigidTrucks);
			truckListDoneOutstation.removeAll(othersRigidTrucks);
			truckListDoneOutstation.removeAll(isuzuRigidTrucks);
			if (placements.size() < 1)
			    placements.addAll(isuzuRigidWithToyotaDriver);
			if (placements.size() < 1)
			    placements.addAll(othersRigidWithToyotaDriver);
		    }
		    if (orderMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_OTHERS) && orderTruckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_ARTICULATED)) {
			placements.removeAll(othersArticulatedTWD);
			placements.removeAll(isuzuArticulatedTrucks);
			placements.removeAll(toyotaArticulatedTrucks);
			if (placements.size() < 1)
			    placements.addAll(othersArticulatedTWD);
			if (placements.size() < 1) {
			    toyotaArticulatedTrucks.removeAll(toyotaArticulatedTWD);
			    placements.addAll(toyotaArticulatedTrucks);
			}
			if (placements.size() < 1)
			    placements.addAll(toyotaArticulatedTWD);
			if (placements.size() < 1) {
			    isuzuArticulatedTrucks.removeAll(isuzuArticulatedTWD);
			    placements.addAll(isuzuArticulatedTrucks);
			}
			if (placements.size() < 1)
			    placements.addAll(isuzuArticulatedTWD);
		    }
		    if (orderMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_ISUZU) && orderTruckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_ARTICULATED)) {
			placements.removeAll(othersArticulatedTrucks);
			placements.removeAll(isuzuArticulatedTWD);
			placements.removeAll(toyotaArticulatedTrucks);
			if (placements.size() < 1)
			    placements.addAll(isuzuArticulatedTWD);
			if (placements.size() < 1) {
			    othersArticulatedTrucks.removeAll(othersArticulatedTWD);
			    placements.addAll(othersArticulatedTrucks);
			}
			if (placements.size() < 1)
			    placements.addAll(othersArticulatedTWD);
			if (placements.size() < 1) {
			    toyotaArticulatedTrucks.removeAll(toyotaArticulatedTWD);
			    placements.addAll(toyotaArticulatedTrucks);
			}
			if (placements.size() < 1)
			    placements.addAll(toyotaArticulatedTWD);
		    }
		    if (orderMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_TOYOTA) && orderTruckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_ARTICULATED)) {
			placements.removeAll(isuzuArtWithToyotaDriver);
			placements.removeAll(othersArtWithToyotaDriver);
			placements.removeAll(isuzuArticulatedTrucks);
			placements.removeAll(othersArticulatedTrucks);
			truckListDoneOutstation.removeAll(isuzuArticulatedTrucks);
			truckListDoneOutstation.removeAll(othersArticulatedTrucks);
			if (placements.size() < 1)
			    placements.addAll(isuzuArtWithToyotaDriver);
			if (placements.size() < 1)
			    placements.addAll(othersArtWithToyotaDriver);
		    }
		    List<PlacementResult> actualPlacements = new ArrayList<PlacementResult>();
		    actualPlacements.addAll(placements);
		    if (orderJobType.equalsIgnoreCase(GigaConstants.ORDER_TYPE_OUTSTATION)) {
			placements.removeAll(truckListDoneOutstation);
			if (placements.size() < 1)
			    placements.addAll(actualPlacements);
		    }
		    /*if (orderMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_ISUZU)) {
		    placements.removeAll(isuzuArtWithToyotaDriver);
		    if (placements.size() < 1)
		    placements.addAll(isuzuArtWithToyotaDriver);
		    }*/
		    placements.removeAll(truckDoneLocalNoPrevOutStation);
		    if (placements.size() < 1)
			placements.addAll(actualPlacements);
		    if (orderJobType.equalsIgnoreCase(GigaConstants.ORDER_TYPE_LOCAL)) {
			if (truckListDoneOutstation.size() > 1) {
			    /*placements.removeAll(truckListDoneOutstation);
			    truckListDoneOutstation.addAll(placements);
			    placements.clear();
			    placements.addAll(truckListDoneOutstation);*/
			    placements.clear();
			    placements.addAll(truckListDoneOutstation);
			} else {
			    //placements.removeAll(truckListDoneOutstation);
			    if (placements.size() < 1)
				placements.addAll(truckListDoneOutstation);
			}
		    }
		    if (orderMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_ISUZU)) {
			for (PlacementResult pr : placements) {
			    if (isuzuArticulatedTrucks.contains(pr))
				tmpIsuzuArticulatedTrucks.add(pr);
			}
			if (tmpIsuzuArticulatedTrucks.size() > 0) {
			    placements = tmpIsuzuArticulatedTrucks;
			}
		    }
		    logger.info("Final placements size: " + placements.size() + "; and Placements: " + placements.toString());
		    if (placements.size() < 1) {
			if (orderMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_OTHERS) && orderTruckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_RIGID)) {
			    placements.addAll(othersRigidTWD);
			    if (placements.size() < 1) {
				placements.addAll(toyotaRigidTWD);
			    }
			}
			if (orderMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_OTHERS) && orderTruckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_ARTICULATED)) {
			    placements.addAll(othersArticulatedTWD);
			    if (placements.size() < 1) {
				placements.addAll(toyotaArticulatedTWD);
			    }
			}
			if (orderMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_TOYOTA) && orderTruckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_RIGID)) {
			    placements.addAll(toyotaRigidTWD);
			}
			if (orderMake.equalsIgnoreCase(GigaConstants.TRUCK_MAKE_TOYOTA) && orderTruckType.equalsIgnoreCase(GigaConstants.TRUCK_TYPE_ARTICULATED)) {
			    placements.addAll(toyotaRigidTWD);
			}
		    }
		}
		if (orderJobType.equalsIgnoreCase(GigaConstants.ORDER_TYPE_OUTSTATION) && placements.size() <= 0 && !isBothDone) {
		    placements = plannedOSList;
		    isSelectionCompleted = true;
		    isBothDone = true;
		}
	    } while (isSelectionCompleted);
	    if (!pickupRegion.equalsIgnoreCase(GigaConstants.CENTRAL_REGION) && orderJobType.equalsIgnoreCase(GigaConstants.ORDER_TYPE_OUTSTATION)) {
		driverLoadFactor = 0.0;
		minDistWeightageFactor = 0.0;
		fleetUtilWeightageFactor = 0.0;
		ODMWeightageFactor = 1.0;
	    }
	    for (PlacementResult placementResult : placements) {
		Resource resource = placementResult.getResource();
		Resource driverRes = RSPContainerHelper.getResourceMap(true).getEntryBy(placementResult.getResource().getParameterBy(GigaConstants.RES_PARAM_DRIVERID).getValueAsString());
		Double driverLoadCount = 0.0;
		if (!driverRes.getId().equalsIgnoreCase(GigaConstants.TRUCK_WITHOUT_DRIVER)) {
		    if (order.hasParameter(GigaConstants.ORD_PARAM_JOB_TYPE)) {
			if (order.getParameterBy(GigaConstants.ORD_PARAM_JOB_TYPE).getValueAsString().equalsIgnoreCase(GigaConstants.ORDER_TYPE_LOCAL)) {
			    driverLoadCount = ((IntegerParameter) driverRes.getParameterBy(GigaConstants.NO_OF_LOCAL_ORDERS)).getValue().doubleValue();
			    driverLoadMap = GigaUtils.populateMap(driverLoadCount, resource, driverLoadMap);
			}
			if (order.getParameterBy(GigaConstants.ORD_PARAM_JOB_TYPE).getValueAsString().equalsIgnoreCase(GigaConstants.ORDER_TYPE_OUTSTATION)) {
			    driverLoadCount = ((IntegerParameter) driverRes.getParameterBy(GigaConstants.NO_OF_OUTSTATION_ORDERS)).getValue().doubleValue();
			    driverLoadMap = GigaUtils.populateMap(driverLoadCount, resource, driverLoadMap);
			}
		    }
		} else {
		    driverLoadMap = GigaUtils.populateMap(driverLoadCount, resource, driverLoadMap);
		}
		//String CurrentLoc = resource.getStateVariableBy(GigaConstants.STATE_LOCATION).getValueAt(deliveryTime).getValueAsString();
		String CurrentLoc = resource.getStateVariableBy(GigaConstants.STATE_LOCATION).getValueAt(java.lang.Long.MAX_VALUE).getValueAsString();
		Resource CurrentLocRes = RSPContainerHelper.getResourceMap(true).getEntryBy(CurrentLoc);
		String[] latLongValues = currLatLong.getParameterBy(resource.getId()).getValueAsString().split(",");
		double currLattitude = Double.valueOf(latLongValues[0]);
		double currLongitude = Double.valueOf(latLongValues[1]);
		double currToPickupDist = 0;
		double currToPickupDur = 0;
		boolean canHaveCurrLatLong = GigaUtils.canHaveCurrLatLong(resource);
		String eventStatus = null;
		if (order.hasParameter(GigaConstants.TRUCK_EVENT_STATUS)) {
		    eventStatus = order.getParameterBy(GigaConstants.TRUCK_EVENT_STATUS).getValueAsString();
		}
		if (eventStatus == null) {
		    if (durationCalculator < 3.0) {
			if (CurrentLocRes.getParameterBy(pickup) == null) {
			    if (canHaveCurrLatLong) {
				double pickLattitude = (Double) pickupRes.getParameterBy(GigaConstants.ROUTE_PARAM_LATITUDE).getValue();
				double pickLongitude = (Double) pickupRes.getParameterBy(GigaConstants.ROUTE_PARAM_LONGITUDE).getValue();
				String[] CurrToPickupValues = FindDistanceDuration.getDistanceDuration(currLattitude, currLongitude, pickLattitude, pickLongitude).split(",");
				currToPickupDist = Double.parseDouble(CurrToPickupValues[0]);
				currToPickupDur = Double.parseDouble(CurrToPickupValues[1]);
			    } else {
				currToPickupDur = GigaUtils.getDuration(CurrentLoc, pickup);
				currToPickupDist = GigaUtils.getDistance(CurrentLoc, pickup);
			    }
			} else {
			    String[] CurrToPickupValues = CurrentLocRes.getParameterBy(pickup).getValueAsString().split(",");
			    currToPickupDist = Double.parseDouble(CurrToPickupValues[0]);
			    currToPickupDur = Double.parseDouble(CurrToPickupValues[1]);
			}
		    } else {
			String[] CurrToPickupValues = CurrentLocRes.getParameterBy(pickup).getValueAsString().split(",");
			currToPickupDist = Double.parseDouble(CurrToPickupValues[0]);
			currToPickupDur = Double.parseDouble(CurrToPickupValues[1]);
		    }
		} else {
		    if (eventStatus.equalsIgnoreCase(GigaConstants.TRUCK_BD_STATUS_SHORT_CLOSE)) {
			if (order.hasParameter(GigaConstants.TRUCK_LOAD_PICKUP_STATUS)) {
			    String loadStatus = order.getParameterBy(GigaConstants.TRUCK_LOAD_PICKUP_STATUS).getValueAsString();
			    if (loadStatus.equalsIgnoreCase(GigaConstants.TRUCK_LOAD_PICKUP_COMPLETED)) {
				Resource truckLatLongRes = resourceMap.getEntryBy(GigaConstants.RES_TRUCK_LAT_LONG);
				String[] currTrucklatLong = truckLatLongRes.getParameterBy(resource.getId()).getValueAsString().split(",");
				double currLat = Double.valueOf(currTrucklatLong[0]);
				double currLong = Double.valueOf(currTrucklatLong[1]);
				String[] truckLatLong = truckLatLongRes.getParameterBy(order.getParameterBy(GigaConstants.ORDER_PARAM_PREV_TRUCK_ID).getValueAsString()).getValueAsString().split(",");
				String[] currToPickupDuration = FindDistanceDuration.getDistanceDuration(currLat, currLong, Double.valueOf(truckLatLong[0]), Double.valueOf(truckLatLong[1])).split(",");
				currToPickupDist = Long.parseLong(currToPickupDuration[0]);
				currToPickupDur = Long.parseLong(currToPickupDuration[1]);
			    }
			}
		    } else if (eventStatus.equalsIgnoreCase(GigaConstants.TRUCK_BD_STATUS_RESUME)) {
			if (order.hasParameter(GigaConstants.TRUCK_LOAD_PICKUP_STATUS)) {
			    String loadStatus = order.getParameterBy(GigaConstants.TRUCK_LOAD_PICKUP_STATUS).getValueAsString();
			    if (loadStatus.equalsIgnoreCase(GigaConstants.TRUCK_LOAD_PICKUP_COMPLETED)) {
				currToPickupDur = 60000;
			    }
			}
		    }
		}
		currToPickupMap = GigaUtils.populateMap(currToPickupDist, resource, currToPickupMap);
		//logger.debug("CurrToPickupDur " +CurrToPickupDur);
		String[] pickupToDelValues = pickupRes.getParameterBy(delivery).getValueAsString().split(",");
		//logger.debug("pickupToDelValues " +pickupToDelValues);
		double pickupToDelDist = Double.parseDouble(pickupToDelValues[0]);
		//logger.debug("pickupToDelDist " +pickupToDelDist);
		double pickupToDelDur = Double.parseDouble(pickupToDelValues[1]);
		//logger.debug("pickupToDelDur " +pickupToDelDur);
		String Base = resource.getParameterBy(GigaConstants.RES_PARAM_LOCATION).getValueAsString(); //fetching the base location of the truck
		Resource baseLocation = RSPContainerHelper.getResourceMap(true).getEntryBy(Base);
		double deliveryToBaseDist;
		double deliveryToBaseDur;
		if (durationCalculator < 3) {
		    if (deliveryRes.getParameterBy(Base) == null) {
			double delLattitude = (Double) deliveryRes.getParameterBy(GigaConstants.ROUTE_PARAM_LATITUDE).getValue();
			double delLongitude = (Double) deliveryRes.getParameterBy(GigaConstants.ROUTE_PARAM_LONGITUDE).getValue();
			double baseLattitude = (Double) baseLocation.getParameterBy(GigaConstants.ROUTE_PARAM_LATITUDE).getValue();
			double baseLongitude = (Double) baseLocation.getParameterBy(GigaConstants.ROUTE_PARAM_LONGITUDE).getValue();
			String[] BaseMatrixValues = FindDistanceDuration.getDistanceDuration(delLattitude, delLongitude, baseLattitude, baseLongitude).split(",");
			deliveryToBaseDist = Double.parseDouble(BaseMatrixValues[0]);
			deliveryToBaseDur = Double.parseDouble(BaseMatrixValues[1]);
			ArrayList<Double> arrayList = new ArrayList<Double>();
			arrayList.add(Double.parseDouble(BaseMatrixValues[0]));
			arrayList.add(Double.parseDouble(BaseMatrixValues[1]));
			FloatListParameter newDest = new FloatListParameter(Base, Base, arrayList);
			deliveryRes.addParameter(newDest);
		    } else {
			String[] BaseMatrixValues = deliveryRes.getParameterBy(Base).getValueAsString().split(",");
			deliveryToBaseDist = Double.parseDouble(BaseMatrixValues[0]);
			deliveryToBaseDur = Double.parseDouble(BaseMatrixValues[1]);
		    }
		} else {
		    String[] BaseMatrixValues = deliveryRes.getParameterBy(Base).getValueAsString().split(",");
		    deliveryToBaseDist = Double.parseDouble(BaseMatrixValues[0]);
		    deliveryToBaseDur = Double.parseDouble(BaseMatrixValues[1]);
		}
		Date date = new Date(pickupTime);
		SimpleDateFormat df2 = new SimpleDateFormat("MM/dd/yyyy");
		String dateText = df2.format(date);
		StringWriter Writer = new StringWriter();
		Writer.append(dateText);
		Writer.append(" 04:00:00");
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
		try {
		    Date date1 = (Date) simpleDateFormat.parse(Writer.toString());
		    long lower = date1.getTime();
		    long upper = deliveryTime;
		    List<Activity> activities = resource.getActivitiesInInterval(lower, upper);
		    long prevDur = 0;
		    for (Activity activity : activities) {
			long di = activity.getDuration();
			prevDur = prevDur + di;
		    }
		    fleetUtil = GigaUtils.populateMap(Long.valueOf(prevDur).doubleValue(), resource, fleetUtil);
		    double odm_km1 = currToPickupDist + deliveryToBaseDist;
		    double odm1 = currToPickupDur + deliveryToBaseDur;
		    double odm2 = 0;
		    double odm_km2 = 0;
		    for (PlacementResult placmntResult : placements) {
			Resource otherRes = placmntResult.getResource();
			if (otherRes != resource) {
			    String lastDeliveredLoc = null;
			    List<Activity> prevActivities = otherRes.getActivitiesInInterval(Long.MIN_VALUE, Long.MAX_VALUE);
			    if (prevActivities.size() < 1)
				// if the truck is idle
				lastDeliveredLoc = getCurrentLocation(otherRes);
			    else {
				// if truck is fulfilling some orders
				Activity lastAct = prevActivities.get(prevActivities.size() - 1);
				Order prevOrder = RSPContainerHelper.getOrderMap(true).getEntryBy(lastAct.getOrderNetId());
				if (!prevOrder.getType().equalsIgnoreCase(GigaConstants.ORD_TYPE_MAINTENANCE))
				    // if it is not a maintenance order
				    lastDeliveredLoc = prevOrder.getParameterBy(GigaConstants.ORD_PARAM_DELIVERY_LOATION).getValueAsString();
			    }
			    String Base1 = otherRes.getParameterBy(GigaConstants.RES_PARAM_LOCATION).getValueAsString(); //fetching the base location of the truck
			    Resource lastDeliveredLocRes = resourceMap.getEntryBy(lastDeliveredLoc);
			    logger.info("Last delivered location: " + lastDeliveredLoc);
			    Resource Base1Res = resourceMap.getEntryBy(Base1);
			    double lastDeliveryToBaseDist = 0;
			    double lastDeliveryToBaseDur = 0;
			    if (durationCalculator < 3) {
				if (lastDeliveredLocRes.getParameterBy(Base1) == null) {
				    double lastDeliveredLocLat = (Double) lastDeliveredLocRes.getParameterBy(GigaConstants.ROUTE_PARAM_LATITUDE).getValue();
				    double lastDeliveredLocLong = (Double) lastDeliveredLocRes.getParameterBy(GigaConstants.ROUTE_PARAM_LONGITUDE).getValue();
				    double base1Lat = (Double) Base1Res.getParameterBy(GigaConstants.ROUTE_PARAM_LATITUDE).getValue();
				    double base1Long = (Double) Base1Res.getParameterBy(GigaConstants.ROUTE_PARAM_LONGITUDE).getValue();
				    logger.debug("3rd" + Base1);
				    String[] LocMatrixValues1 = FindDistanceDuration.getDistanceDuration(lastDeliveredLocLat, lastDeliveredLocLong, base1Lat, base1Long).split(",");
				    lastDeliveryToBaseDist = Double.parseDouble(LocMatrixValues1[0]);
				    lastDeliveryToBaseDur = Double.parseDouble(LocMatrixValues1[1]);
				    ArrayList<Double> arrayList = new ArrayList<Double>();
				    arrayList.add(Double.parseDouble(LocMatrixValues1[0]));
				    arrayList.add(Double.parseDouble(LocMatrixValues1[1]));
				    FloatListParameter newDest = new FloatListParameter(Base1, Base1, arrayList);
				    lastDeliveredLocRes.addParameter(newDest);
				} else {
				    String[] LocMatrixValues1 = lastDeliveredLocRes.getParameterBy(Base1).getValueAsString().split(",");
				    lastDeliveryToBaseDist = Double.parseDouble(LocMatrixValues1[0]);
				    lastDeliveryToBaseDur = Double.parseDouble(LocMatrixValues1[1]);
				}
			    } else {
				String[] LocMatrixValues1 = lastDeliveredLocRes.getParameterBy(Base1).getValueAsString().split(",");
				lastDeliveryToBaseDist = Double.parseDouble(LocMatrixValues1[0]);
				lastDeliveryToBaseDur = Double.parseDouble(LocMatrixValues1[1]);
			    }
			    odm_km2 = odm_km2 + lastDeliveryToBaseDist;
			    odm2 = odm2 + lastDeliveryToBaseDur;
			}
		    }
		    ODM_hrs = odm1 + odm2;
		    ODM_km = odm_km1 + odm_km2;
		    double estimatedTravelDist = currToPickupDist + pickupToDelDist;
		    double estimatedTravelDur = currToPickupDur + pickupToDelDur;
		    resDeadMile_hrs.put(resource, ODM_hrs);
		    resOverAllDeadMile_Kms = GigaUtils.populateMap(ODM_km, resource, resOverAllDeadMile_Kms);
		    resDeadMile_km.put(resource, ODM_km);
		    distanceODM.put(resource, estimatedTravelDist);
		    durationODM.put(resource, estimatedTravelDur);
		} catch (ParseException e) {
		    e.printStackTrace();
		}
	    }
	    driverLoadSortedMap = new TreeMap<Double, List<Resource>>(driverLoadMap);//driverLoadMap sorted Resource List
	    logger.info("driverLoadSortedMap :" + driverLoadSortedMap);
	    minDistResList = new TreeMap<Double, List<Resource>>(currToPickupMap);
	    logger.info("minDistResList: " + minDistResList);//Resource List based on minimum distance sorting
	    ODMSortedResList = new TreeMap<Double, List<Resource>>(resOverAllDeadMile_Kms);//ODM sorted Resource List
	    logger.debug("testing the milege" + resOverAllDeadMile_Kms);
	    logger.info("ODMSortedResList :" + ODMSortedResList);
	    fleetUtilResList = new TreeMap<Double, List<Resource>>(fleetUtil);//FleetUtilization sorted Resource List
	    logger.info("fleetUtilResList :" + fleetUtilResList);
	    Map<Resource, Double> weightageSortedMap = GigaUtils.sortByPriority(driverLoadSortedMap, minDistResList, ODMSortedResList, fleetUtilResList, driverLoadFactor, minDistWeightageFactor, ODMWeightageFactor, fleetUtilWeightageFactor);
	    logger.info("weightageSortedMap : " + weightageSortedMap);
	    List<Resource> ResIds = new ArrayList<Resource>(weightageSortedMap.keySet());//Final Sorted List of Resources
	    List<Double> ODM_inHrs = new ArrayList<Double>(GigaUtils.getSortedMap(weightageSortedMap, resDeadMile_hrs).values());
	    List<Double> ODM_inKm = new ArrayList<Double>(GigaUtils.getSortedMap(weightageSortedMap, resDeadMile_km).values());
	    List<Double> Distancies = new ArrayList<Double>(GigaUtils.getSortedMap(weightageSortedMap, distanceODM).values());
	    List<Double> Durations = new ArrayList<Double>(GigaUtils.getSortedMap(weightageSortedMap, durationODM).values());
	    if (ResIds.size() > 0) {
		StringWriter resultWriter = new StringWriter();
		for (Resource re : ResIds) {
		    resultWriter.append(re.getId());
		    resultWriter.append(",");
		}
		String selectableTrucks = resultWriter.toString();
		int latestKommaPosition = selectableTrucks.lastIndexOf(",");
		selectableTrucks = selectableTrucks.substring(0, latestKommaPosition);
		StringParameter evaluatedTrucks = (StringParameter) order.getParameterBy(GigaConstants.ORD_PARAM_EVAL_TRUCKS);
		evaluatedTrucks.setValue(selectableTrucks);//set the evaluatedTrucks parameter in order
		StringParameter evaluatedODM_hrs = (StringParameter) order.getParameterBy(GigaConstants.ORD_PARAM_EVAL_ODM_HOURS);
		evaluatedODM_hrs.setValue(getString(ODM_inHrs));//set the evaluatedODM_hrs parameter in order
		StringParameter evaluatedODM_km = (StringParameter) order.getParameterBy(GigaConstants.ORD_PARAM_EVAL_ODM_KM);
		evaluatedODM_km.setValue(getString(ODM_inKm));//set the evaluatedODM_km parameter in order
		StringParameter estimatedTravelDistance = (StringParameter) order.getParameterBy(GigaConstants.ORD_PARAM_EVAL_TRAVEL_DISTANCE);
		estimatedTravelDistance.setValue(getString(Distancies));//set the estimatedTravelDistance parameter in order
		StringParameter estimatedTravelDuration = (StringParameter) order.getParameterBy(GigaConstants.ORD_PARAM_EVAL_TRAVEL_DURATION);
		estimatedTravelDuration.setValue(getString(Durations));//set the estimatedTravelDuration parameter in order
		List<PlacementResult> sortedPlacements = new ArrayList<PlacementResult>();
		for (Resource r : ResIds) {
		    for (PlacementResult p : placements) {
			if (p.getResource() == r)
			    sortedPlacements.add(p);
		    }
		}
		for (PlacementResult Result : placements) {
		    if (Result.getResource() == ResIds.get(0))
			result = Result;
		}
		if (result.getResource().getParameterBy(GigaConstants.RES_PARAM_DRIVERID).getValueAsString().equalsIgnoreCase(GigaConstants.TRUCK_WITHOUT_DRIVER)) {
		    Resource mntTruckRes = GigaUtils.getSpareDriver(result.getResource(), result.getStart(), orderJobType);
		    if (mntTruckRes != null) {
			String spareDriver = mntTruckRes.getParameterBy(GigaConstants.RES_PARAM_DRIVERID).getValueAsString();
			if (!spareDriver.equalsIgnoreCase(GigaConstants.TRUCK_WITHOUT_DRIVER)) {
			    ((StringParameter) result.getResource().getParameterBy(GigaConstants.RES_PARAM_DRIVERID)).setValue(spareDriver);
			    ((StringParameter) mntTruckRes.getParameterBy(GigaConstants.RES_PARAM_DRIVERID)).setValue(GigaConstants.TRUCK_WITHOUT_DRIVER);
			    if (order.hasParameter(GigaConstants.ORD_PARAM_ORDER_DETAILS)) {
				((StringParameter) order.getParameterBy(GigaConstants.ORD_PARAM_ORDER_DETAILS)).setValue(order.getParameterBy(GigaConstants.ORD_PARAM_ORDER_DETAILS).getValueAsString().concat("Driver '" + spareDriver + "' taken from Maintenance truck" + mntTruckRes.getId()));
			    } else {
				StringParameter orderDetails = new StringParameter(GigaConstants.ORD_PARAM_ORDER_DETAILS, GigaConstants.ORD_PARAM_ORDER_DETAILS, "Driver '" + spareDriver + "' taken from Maintenance truck" + mntTruckRes.getId());
				order.addParameter(orderDetails);
			    }
			} else {
			    result = new DefaultPlacementResult(PlacementResult.FAILURE, "The truck" + result.getResource().getId() + "  doesnt have matching driver from Maintenance.");
			    ((StringParameter) order.getParameterBy(GigaConstants.ORD_PARAM_REMARKS)).setValue("There are no feasible trucks to fulfill this order.");
			    return null;
			}
		    } else {
			result = new DefaultPlacementResult(PlacementResult.FAILURE, "The truck" + result.getResource().getId() + "  doesnt have matching driver from Maintenance.");
			((StringParameter) order.getParameterBy(GigaConstants.ORD_PARAM_REMARKS)).setValue("There are no feasible trucks to fulfill this order.");
			return null;
		    }
		}
	    } else {
		((StringParameter) order.getParameterBy(GigaConstants.ORD_PARAM_REMARKS)).setValue("There are no feasible trucks to fulfill this order.");
		return null;
	    }
	    logger.debug("end of truck selection resource id>>>>" + result.getResource().getId());
	    Resource driverRes = RSPContainerHelper.getResourceMap(true).getEntryBy(result.getResource().getParameterBy(GigaConstants.RES_PARAM_DRIVERID).getValueAsString());
	    if (order.hasParameter(GigaConstants.ORD_PARAM_JOB_TYPE)) {
		if (order.getParameterBy(GigaConstants.ORD_PARAM_JOB_TYPE).getValueAsString().equalsIgnoreCase(GigaConstants.ORDER_TYPE_LOCAL))
		    ((IntegerParameter) driverRes.getParameterBy(GigaConstants.NO_OF_LOCAL_ORDERS)).setValue(((IntegerParameter) driverRes.getParameterBy(GigaConstants.NO_OF_LOCAL_ORDERS)).getValue() + 1);
		if (order.getParameterBy(GigaConstants.ORD_PARAM_JOB_TYPE).getValueAsString().equalsIgnoreCase(GigaConstants.ORDER_TYPE_OUTSTATION))
		    ((IntegerParameter) driverRes.getParameterBy(GigaConstants.NO_OF_OUTSTATION_ORDERS)).setValue(((IntegerParameter) driverRes.getParameterBy(GigaConstants.NO_OF_OUTSTATION_ORDERS)).getValue() + 1);
		if (order.getParameterBy(GigaConstants.ORD_PARAM_JOB_TYPE).getValueAsString().equalsIgnoreCase(GigaConstants.ORDER_TYPE_LOCAL)) {
		    if (driverRes.hasParameter(GigaConstants.NO_OF_LOCAL_ORDERS_CD))
			((IntegerParameter) driverRes.getParameterBy(GigaConstants.NO_OF_LOCAL_ORDERS_CD)).setValue(((IntegerParameter) driverRes.getParameterBy(GigaConstants.NO_OF_LOCAL_ORDERS_CD)).getValue() + 1);
		    else {
			IntegerParameter noOfCurrDayLocalOrdersP = new IntegerParameter(GigaConstants.NO_OF_LOCAL_ORDERS_CD, GigaConstants.NO_OF_LOCAL_ORDERS_CD, 1);
			driverRes.addParameter(noOfCurrDayLocalOrdersP);
		    }
		}
		if (order.getParameterBy(GigaConstants.ORD_PARAM_JOB_TYPE).getValueAsString().equalsIgnoreCase(GigaConstants.ORDER_TYPE_OUTSTATION)) {
		    if (driverRes.hasParameter(GigaConstants.NO_OF_OUTSTATION_ORDERS_CD))
			((IntegerParameter) driverRes.getParameterBy(GigaConstants.NO_OF_OUTSTATION_ORDERS_CD)).setValue(((IntegerParameter) driverRes.getParameterBy(GigaConstants.NO_OF_OUTSTATION_ORDERS_CD)).getValue() + 1);
		    else {
			IntegerParameter noOfCurrDayOutstationOrdersP = new IntegerParameter(GigaConstants.NO_OF_OUTSTATION_ORDERS_CD, GigaConstants.NO_OF_OUTSTATION_ORDERS_CD, 1);
			driverRes.addParameter(noOfCurrDayOutstationOrdersP);
		    }
		}
	    }
	} catch (Exception e) {
	    e.printStackTrace();
	}
	return result;
    }

    public PlacementResult evaluatePlacements(WorkflowElement workflowElement, List<PlacementResult> successfulPlacements) {
	if (successfulPlacements.isEmpty())
	    return null;
	PlacementResult latestResult = successfulPlacements.get(successfulPlacements.size() - 1);
	TimeRelation timeRelation = workflowElement.getTimeRelation();
	long LatestEnd = timeRelation.getLatest(workflowElement, TimeDirection.TIMEDIRECTION_FORWARD);
	if (LatestEnd == latestResult.getEnd()) {
	    return latestResult;
	}
	return null;
    }

    public static String getString(List<Double> obj) {
	StringWriter resultWriter = new StringWriter();
	for (Object o1 : obj) {
	    resultWriter.append(o1.toString());
	    resultWriter.append(",");
	}
	String resultS = resultWriter.toString();
	int latestKommaPosition = resultS.lastIndexOf(",");
	if (latestKommaPosition > 0)
	    resultS = resultS.substring(0, latestKommaPosition);
	return resultS;
    }

    public static HashMap<Resource, Double> sortByPriority(List<Resource> L1, List<Resource> L2, List<Resource> L3, double minDistWeightageFactor, double odmWeightageFactor, double fleetUtilWeightageFactor) {
	HashMap<Resource, Double> sum = new HashMap<Resource, Double>();
	for (Resource res : L1) {
	    double overallWeightage = 0;
	    List<Integer> posT1 = new ArrayList<Integer>();
	    for (int i = 1; i <= L1.size(); i++) {
		if (L1.get(i - 1) == res) {
		    posT1.add(i);
		    overallWeightage += i * minDistWeightageFactor;
		}
	    }
	    for (int i = 1; i <= L2.size(); i++) {
		if (L2.get(i - 1) == res) {
		    posT1.add(i);
		    overallWeightage += i * odmWeightageFactor;
		}
	    }
	    for (int i = 1; i <= L3.size(); i++) {
		if (L3.get(i - 1) == res) {
		    posT1.add(i);
		    overallWeightage += i * fleetUtilWeightageFactor;
		}
	    }
	    sum.put(res, overallWeightage);
	}
	HashMap<Resource, Double> sortedMap = GigaUtils.sortByValues(sum);
	return sortedMap;
    }

    protected static String getCurrentLocation(Resource truckRes) {
	String currentLoc = null;
	List<Activity> prevTasks = truckRes.getActivitiesInInterval(Long.MIN_VALUE, Long.MAX_VALUE);
	if (prevTasks.size() < 1) {
	    //if the truck is idle
	    StateValue<?> currentLocValue = truckRes.getStateVariableBy(GigaConstants.STATE_LOCATION).getValueAt(Long.MAX_VALUE);
	    currentLoc = currentLocValue.getValueAsString();
	} else {
	    // if truck is fulfilling another order.
	    Activity lastAct = prevTasks.get(prevTasks.size() - 1);
	    String orderId = lastAct.getOrderNetId();
	    Order prevOrder = RSPContainerHelper.getOrderMap(true).getEntryBy(orderId);
	    String OrderType = prevOrder.getType();
	    if (OrderType.equalsIgnoreCase(GigaConstants.ORD_TYPE_MAINTENANCE)) {
		// if truck is under maintenance.
		currentLoc = truckRes.getStateVariableBy(GigaConstants.STATE_LOCATION).getValueAt(lastAct.getEnd()).getValueAsString();
	    } else {
		// if truck is not under maintenance and fulfilling another order.
		currentLoc = prevOrder.getParameterBy(GigaConstants.ORD_PARAM_DELIVERY_LOATION).getValueAsString();
		if (prevOrder.hasParameter(GigaConstants.ORD_PARAM_JOB_TYPE)) {
		    if (prevOrder.getParameterBy(GigaConstants.ORD_PARAM_JOB_TYPE).getValueAsString().equalsIgnoreCase(GigaConstants.ORDER_TYPE_OUTSTATION)) {
			//if truck has fulfilled an out station order.
			long prevOrderStartTime = lastAct.getStart() - 2 * 60000L;
			currentLoc = truckRes.getStateVariableBy(GigaConstants.STATE_LOCATION).getValueAt(prevOrderStartTime).getValueAsString();
		    }
		}
	    }
	}
	return currentLoc;
    }
}
