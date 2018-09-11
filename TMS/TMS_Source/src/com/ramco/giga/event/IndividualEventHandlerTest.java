package com.ramco.giga.event;

import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.ramco.giga.formula.FindDistanceDuration;
import com.ramco.giga.utils.GigaUtils;
import com.rsp.core.base.FeatherliteAgent;
import com.rsp.core.base.RSPContainerHelper;
import com.rsp.core.base.command.AddOrderCommand;
import com.rsp.core.base.exception.RSPException;
import com.rsp.core.base.model.Activity;
import com.rsp.core.base.model.ElementMap;
import com.rsp.core.base.model.Order;
import com.rsp.core.base.model.Resource;
import com.rsp.core.base.model.constants.PlanningState;
import com.rsp.core.base.model.parameter.DurationParameter;
import com.rsp.core.base.model.parameter.StringParameter;
import com.rsp.core.base.transaction.RSPTransaction;
import com.rsp.core.i18n.RSPMessages;
import com.rsp.core.planning.command.PlanOrderCommand;

public class IndividualEventHandlerTest {
    private final static Logger logger = Logger.getLogger(IndividualEventHandlerTest.class);
    private final String TRUE = "true";
    private final String FALSE = "false";

    public String doService(com.rsp.core.planning.service.EventService.Argument argument) {
	String result = TRUE;
	try {
	    Map<String, String> simplePlacement = new HashMap<String, String>();
	    simplePlacement.put("PlacementPolicy", "simplePlacementCR");
	    Map<String, String> truckPlacement = new HashMap<String, String>();
	    truckPlacement.put("PlacementPolicy", "truckPlacementCR");
	    List<Order> bulkOrders = argument.orders;
	    logger.info("size: " + bulkOrders.size());
	    if (bulkOrders == null || bulkOrders.size() == 0)
		return "The argument orders is null.";
	    long pickDate = (Long) bulkOrders.get(0).getParameterBy("pickupDate").getValue();
	    Date date = new Date(pickDate);
	    logger.info("indDate" + date);
	    SimpleDateFormat df = new SimpleDateFormat("MM/dd/yyyy");
	    SimpleDateFormat DateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
	    String dateText = df.format(date);
	    StringWriter W = new StringWriter();
	    StringWriter W1 = new StringWriter();
	    W.append(dateText);
	    W.append(" 08:00:00");
	    W1.append(dateText);
	    W1.append(" 22:00:00");
	    Date indStartDate = DateFormat.parse(W.toString());
	    long indStartTime = indStartDate.getTime();
	    logger.info("indStartTime " + indStartDate);
	    Date indEndDate = DateFormat.parse(W1.toString());
	    long indEndTime = indEndDate.getTime();
	    logger.info("indStartTime " + indEndDate);
	    long currentTime = System.currentTimeMillis();
	    logger.info("currentTime " + currentTime);
	    long buffer = 2700000L;
	    List<Resource> avlTrucks = new ArrayList<Resource>();
	    List<Resource> avlSingle = new ArrayList<Resource>();
	    List<Resource> avlRigid = new ArrayList<Resource>();
	    List<Resource> avlArticulated = new ArrayList<Resource>();
	    List<Resource> avlSingleOthers = new ArrayList<Resource>();
	    List<Resource> avlRigidOthers = new ArrayList<Resource>();
	    List<Resource> avlArticulatedOthers = new ArrayList<Resource>();
	    List<Resource> avlSingleToyota = new ArrayList<Resource>();
	    List<Resource> avlRigidToyota = new ArrayList<Resource>();
	    List<Resource> avlArticulatedToyota = new ArrayList<Resource>();
	    List<Resource> avlSingleIsuzu = new ArrayList<Resource>();
	    List<Resource> avlRigidIsuzu = new ArrayList<Resource>();
	    List<Resource> avlArticulatedIsuzu = new ArrayList<Resource>();
	    Collection<Order> MaintenanceOrders = RSPContainerHelper.getOrderMap(true).getByType("Maintenance");
	    List<String> mntTrucks = new ArrayList<String>();
	    for (Order mntOrder : MaintenanceOrders) {
		if (mntOrder.getState().equals(PlanningState.PLANNED))
		    mntTrucks.add(mntOrder.getParameterBy("truckNo").getValueAsString());
	    }
	    ElementMap<Resource> resourceMap = RSPContainerHelper.getResourceMap(true);
	    if (resourceMap == null || resourceMap.getSize() == 0)
		return "The resourceMap is null.";
	    logger.info("resourceMap: " + resourceMap.getSize());
	    List<Resource> Trucks = (List<Resource>) resourceMap.getByType("truck_CR");
	    logger.info("availTrucks: " + Trucks.size());
	    for (Resource res : Trucks) {
		logger.info("res: " + res.getId());
		if (!mntTrucks.contains(res.getId())) {
		    long EarliestAvailableTime = res.getStateVariableBy("Location").getValueAt(indEndTime).getTime();
		    if (EarliestAvailableTime < indStartTime) {
			EarliestAvailableTime = indStartTime;
		    }
		    logger.info("EarliestAvailableTime->" + new Date(EarliestAvailableTime));
		    List<Activity> activities = res.getActivitiesInInterval(System.currentTimeMillis(), Long.MAX_VALUE);
		    String Driver = res.getParameterBy("driverId").getValueAsString();
		    if (activities.size() < 1 && !Driver.equalsIgnoreCase("TBA")) {
			avlTrucks.add(res);
			if (res.getParameterBy("truckType").getValueAsString().equalsIgnoreCase("single") && res.getParameterBy("make").getValueAsString().equalsIgnoreCase("OTHERS"))
			    avlSingleOthers.add(res);
			else if (res.getParameterBy("truckType").getValueAsString().equalsIgnoreCase("rigid") && res.getParameterBy("make").getValueAsString().equalsIgnoreCase("OTHERS"))
			    avlRigidOthers.add(res);
			else if (res.getParameterBy("truckType").getValueAsString().equalsIgnoreCase("articulated") && res.getParameterBy("make").getValueAsString().equalsIgnoreCase("OTHERS"))
			    avlArticulatedOthers.add(res);
			if (res.getParameterBy("truckType").getValueAsString().equalsIgnoreCase("single") && res.getParameterBy("make").getValueAsString().equalsIgnoreCase("TOYOTA"))
			    avlSingleToyota.add(res);
			else if (res.getParameterBy("truckType").getValueAsString().equalsIgnoreCase("rigid") && res.getParameterBy("make").getValueAsString().equalsIgnoreCase("TOYOTA"))
			    avlRigidToyota.add(res);
			else if (res.getParameterBy("truckType").getValueAsString().equalsIgnoreCase("articulated") && res.getParameterBy("make").getValueAsString().equalsIgnoreCase("TOYOTA"))
			    avlArticulatedToyota.add(res);
			if (res.getParameterBy("truckType").getValueAsString().equalsIgnoreCase("single") && res.getParameterBy("make").getValueAsString().equalsIgnoreCase("ISUZU"))
			    avlSingleIsuzu.add(res);
			else if (res.getParameterBy("truckType").getValueAsString().equalsIgnoreCase("rigid") && res.getParameterBy("make").getValueAsString().equalsIgnoreCase("ISUZU"))
			    avlRigidIsuzu.add(res);
			else if (res.getParameterBy("truckType").getValueAsString().equalsIgnoreCase("articulated") && res.getParameterBy("make").getValueAsString().equalsIgnoreCase("ISUZU"))
			    avlArticulatedIsuzu.add(res);
		    }
		}
	    }
	    avlSingle.addAll(avlSingleOthers);
	    avlRigid.addAll(avlRigidOthers);
	    avlArticulated.addAll(avlArticulatedOthers);
	    logger.info("avlTrucks: " + avlTrucks.size() + "  " + avlTrucks);
	    List<Order> indLocalImportOrders = new ArrayList<Order>();
	    List<Order> indLocalExportOrders = new ArrayList<Order>();
	    List<Order> indLocalDLTOrders = new ArrayList<Order>();
	    List<Order> indOutstationImportOrders = new ArrayList<Order>();
	    List<Order> indOutstationExportOrders = new ArrayList<Order>();
	    List<Order> indOutstationDLTOrders = new ArrayList<Order>();
	    Resource currLatLong = RSPContainerHelper.getResourceMap(true).getEntryBy("truckLatitudeLongitude");
	    double currLat = 0;
	    double currLong = 0;
	    for (Order o : bulkOrders) {
		RSPTransaction tx = FeatherliteAgent.getTransaction(this);
		AddOrderCommand aoc = new AddOrderCommand();
		aoc.setOrder(o);
		tx.addCommand(aoc);
		tx.commit();
		String OrderType = o.getParameterBy("orderType").getValueAsString();
		if (OrderType.equalsIgnoreCase("Individual_Local_Import"))
		    indLocalImportOrders.add(o);
		else if (OrderType.equalsIgnoreCase("Individual_Local_Export"))
		    indLocalExportOrders.add(o);
		else if (OrderType.equalsIgnoreCase("Individual_Local_DLT"))
		    indLocalDLTOrders.add(o);
		else if (OrderType.equalsIgnoreCase("Individual_Outstation_Import"))
		    indOutstationImportOrders.add(o);
		else if (OrderType.equalsIgnoreCase("Individual_Outstation_Export"))
		    indOutstationExportOrders.add(o);
		else if (OrderType.equalsIgnoreCase("Individual_Outstation_DLT"))
		    indOutstationDLTOrders.add(o);
		else
		    throw RSPMessages.newRspMsgEx("No Individual Orders are there for orderType: " + OrderType);
	    }
	    logger.info("indLocalImportOrders: " + indLocalImportOrders.size());
	    try {
		int a = 0;
		while (indLocalImportOrders.size() > 0) {
		    logger.info("newBatchStarted");
		    List<Order> imp = new ArrayList<Order>();
		    String pickupLoc = indLocalImportOrders.get(0).getParameterBy("pickupLocation").getValueAsString();
		    logger.info("pickup: " + pickupLoc);
		    String lastDelivery = null;
		    long truckToPickupDur = 0L;
		    long min = Long.MAX_VALUE;
		    Resource selectedTruck = null;
		    String selectedDriver = null;
		    StringWriter w = new StringWriter();
		    if (avlRigid.size() < 0 && avlRigidToyota.size() > 0)
			avlRigid.addAll(avlRigidToyota);
		    else if (avlRigid.size() < 0 && avlRigidToyota.size() < 0 && avlRigidIsuzu.size() > 0)
			avlRigid.addAll(avlRigidIsuzu);
		    else if (avlArticulated.size() < 0 && avlArticulatedToyota.size() > 0)
			avlArticulated.addAll(avlArticulatedToyota);
		    else if (avlArticulated.size() < 0 && avlArticulatedToyota.size() < 0 && avlArticulatedIsuzu.size() > 0)
			avlArticulated.addAll(avlArticulatedIsuzu);
		    logger.info("avlRigid: " + avlRigid.size());
		    if (avlRigid.size() > 0) {
			for (Resource r : avlRigid) {
			    w.append(r.getId() + ", ");
			    /*	StateValue<?> currentTruckLoc = r.getStateVariableBy("Location").getValueAt(indEndTime);
			    	logger.info("currentLoc:" + currentTruckLoc);
			    	if (currentTruckLoc == null)
			    		continue;
			    	String currentTruckLocation = currentTruckLoc.getValueAsString();
			    	Resource currentTruckLocRes = RSPContainerHelper.getResourceMap(true).getEntryBy(currentTruckLocation);

			    	if (currentTruckLocRes == null) {

			    		continue;
			    	}
			    	*/
			    Resource pickUpDynamic = RSPContainerHelper.getResourceMap(true).getEntryBy(pickupLoc);
			    Double pickUpLat = (Double) pickUpDynamic.getParameterBy("lat").getValue();
			    Double pickUpLong = (Double) pickUpDynamic.getParameterBy("long").getValue();
			    String[] latLongValues = currLatLong.getParameterBy(r.getId()).getValueAsString().split(",");
			    currLat = Double.parseDouble(latLongValues[0]);
			    currLong = Double.parseDouble(latLongValues[0]);
			    String[] truckToPickupDuration = FindDistanceDuration.getDistanceDuration(currLat, currLong, pickUpLat, pickUpLong).split(",");
			    truckToPickupDur = Long.parseLong(truckToPickupDuration[1]);
			    if (truckToPickupDur < min) {
				min = truckToPickupDur;
				selectedTruck = r;
				logger.info(r.getId());
				selectedDriver = r.getParameterBy("driverId").getValueAsString();
			    }
			}
		    } else if (avlRigid.size() < 1 && avlArticulated.size() > 0) {
			for (Resource r : avlArticulated) {
			    w.append(r.getId() + ", ");
			    /*	StateValue<?> currentTruckLoc = r.getStateVariableBy("Location").getValueAt(indEndTime);
			    	logger.info("currentLoc:" + currentTruckLoc);
			    	if (currentTruckLoc == null)
			    		continue;
			    	String currentTruckLocation = currentTruckLoc.getValueAsString();
			    	Resource currentTruckLocRes = RSPContainerHelper.getResourceMap(true).getEntryBy(currentTruckLocation);

			    	if (currentTruckLocRes == null) {

			    		continue;
			    	}
			    	*/
			    Resource pickUpDynamic = RSPContainerHelper.getResourceMap(true).getEntryBy(pickupLoc);
			    Double pickUpLat = (Double) pickUpDynamic.getParameterBy("lat").getValue();
			    Double pickUpLong = (Double) pickUpDynamic.getParameterBy("long").getValue();
			    String[] latLongValues = currLatLong.getParameterBy(r.getId()).getValueAsString().split(",");
			    currLat = Double.parseDouble(latLongValues[0]);
			    currLong = Double.parseDouble(latLongValues[0]);
			    String[] truckToPickupDuration = FindDistanceDuration.getDistanceDuration(currLat, currLong, pickUpLat, pickUpLong).split(",");
			    truckToPickupDur = Long.parseLong(truckToPickupDuration[1]);
			    if (truckToPickupDur < min) {
				min = truckToPickupDur;
				selectedTruck = r;
				selectedDriver = r.getParameterBy("driverId").getValueAsString();
			    }
			}
		    } else if (avlRigid.size() < 1 && avlArticulated.size() < 1)
			break;
		    logger.info("selectedTruck->" + selectedTruck);
		    logger.info("selectedDriver->" + selectedDriver);
		    Long earliestAvailTime = selectedTruck.getStateVariableBy("Location").getValueAt(indEndTime).getTime();
		    if (earliestAvailTime < indStartTime) {
			earliestAvailTime = indStartTime;
		    }
		    if (earliestAvailTime < currentTime)
			earliestAvailTime = currentTime + 2 * 60000L;
		    logger.info("earliestAvailTimeofSelectedTruck->" + new Date(earliestAvailTime));
		    Long estimatedPickupTime = earliestAvailTime + min;
		    logger.info("estimatedPickupTime->" + new Date(estimatedPickupTime));
		    String pickupDateText = DateFormat.format(new Date(estimatedPickupTime));
		    Long estimatedDeliveryTime = estimatedPickupTime;
		    for (int i = 0; i < 4; a++) {
			Order order = indLocalImportOrders.get(i);
			StringParameter evaluatedTrucks = (StringParameter) order.getParameterBy("evaluatedTrucks");
			evaluatedTrucks.setValue(w.toString());
			logger.info("oder: " + order.getId());
			String prevLoc;
			String nextDeliveryLoc;
			if (order.equals(indLocalImportOrders.get(0))) {
			    logger.info("It is the first order");
			    prevLoc = pickupLoc;
			} else {
			    prevLoc = indLocalImportOrders.get(i - 1).getParameterBy("deliveryLocation").getValueAsString();
			    logger.info("Not the first order");
			}
			;
			nextDeliveryLoc = order.getParameterBy("deliveryLocation").getValueAsString();
			logger.info("Before Planning: from " + prevLoc + " To " + nextDeliveryLoc);
			long prevLocToNextdeliveryDur = GigaUtils.getDuration(prevLoc, nextDeliveryLoc);
			if (!nextDeliveryLoc.equalsIgnoreCase(prevLoc))
			    estimatedDeliveryTime += prevLocToNextdeliveryDur + buffer;
			else {
			    i++;
			    imp.add(order);
			    lastDelivery = nextDeliveryLoc;
			    logger.info("After Planning: from " + prevLoc + " To " + nextDeliveryLoc);
			    StringParameter script = (StringParameter) order.getParameterBy("script");
			    logger.info(script);
			    StringParameter truckId = (StringParameter) order.getParameterBy("truckId");
			    logger.info(truckId.getValueAsString());
			    StringParameter driverId = (StringParameter) order.getParameterBy("driverId");
			    script.setValue("planIndividualOrders");
			    truckId.setValue(selectedTruck.getId());
			    selectedTruck.setPolicyMap(simplePlacement);
			    driverId.setValue(selectedDriver);
			    logger.info(driverId);
			    DurationParameter taskStartDate = new DurationParameter("TaskStart", "TaskStart", estimatedPickupTime);
			    order.addParameter(taskStartDate);
			    DurationParameter taskEndDate = new DurationParameter("TaskEnd", "TaskEnd", estimatedDeliveryTime);
			    order.addParameter(taskEndDate);
			    RSPTransaction tx1 = FeatherliteAgent.getTransaction(this);
			    PlanOrderCommand poc = new PlanOrderCommand();
			    poc.setOrder(order);
			    tx1.addCommand(poc);
			    tx1.commit();
			    selectedTruck.setPolicyMap(truckPlacement);
			    StringParameter estPickupTime = (StringParameter) order.getParameterBy("estPickupTime");
			    estPickupTime.setValue(pickupDateText);
			    logger.info("estPickupTime :" + estPickupTime.getValueAsString());
			    String estDeliveryDate = DateFormat.format(new Date(estimatedDeliveryTime));
			    StringParameter estDeliveryTime = (StringParameter) order.getParameterBy("estDeliveryTime");
			    estDeliveryTime.setValue(estDeliveryDate);
			    logger.info("estDeliveryTime :" + estDeliveryTime.getValueAsString());
			    Integer estTravelTym = (int) ((estimatedDeliveryTime - earliestAvailTime) / 60000);
			    StringParameter estTravelTime = (StringParameter) order.getParameterBy("estTravelTime");
			    estTravelTime.setValue(Integer.toString(estTravelTym));
			    logger.info("estTravelTime :" + estTravelTime);
			    String CurrTruckLoc = selectedTruck.getStateVariableBy("Location").getValueAt(indEndTime).getValueAsString();
			    StringParameter preceding_DM = (StringParameter) order.getParameterBy("preceding_DM");
			    preceding_DM.setValue("0");
			    logger.info("preceding_DM :" + preceding_DM);
			    String Base = selectedTruck.getParameterBy("location").getValueAsString();
			    StringParameter succeeding_DM = (StringParameter) order.getParameterBy("succeeding_DM");
			    succeeding_DM.setValue("0");
			    logger.info("succeeding_DM :" + succeeding_DM);
			    Integer travelDur = (int) (GigaUtils.getDuration(pickupLoc, nextDeliveryLoc) / 60000);
			    StringParameter travel_duration = (StringParameter) order.getParameterBy("travel_Duration");
			    travel_duration.setValue(Integer.toString(travelDur));
			    logger.info("travel_duration :" + travel_duration);
			    StringParameter loadBuffer = (StringParameter) order.getParameterBy("loading_unloading_timeBuffer");
			    loadBuffer.setValue(Integer.toString(90));
			    logger.info("loadBuffer :" + loadBuffer);
			    StringParameter restWaitBuffer = (StringParameter) order.getParameterBy("rest_Waiting_timeBuffer");
			    restWaitBuffer.setValue(Integer.toString(0));
			    logger.info("restWaitBuffer :" + restWaitBuffer);
			    StringParameter baseLocStartTime = (StringParameter) order.getParameterBy("base_location_StartTime");
			    if (order.equals(imp.get(0)) && CurrTruckLoc.equalsIgnoreCase(Base)) {
				String baseStartDateText = DateFormat.format(new Date(earliestAvailTime));
				baseLocStartTime.setValue(baseStartDateText);
			    } else
				baseLocStartTime.setValue("");
			    logger.info("baseLocStartTime :" + baseLocStartTime);
			}
			if (estimatedDeliveryTime > (indEndTime - buffer)) {
			    logger.info("order:" + order + " cannot be planned in this batch");
			    break;
			} else {
			    if (order.getParameterBy("pickupLocation").getValueAsString().equalsIgnoreCase(pickupLoc) && selectedTruck.getActivitiesInInterval(estimatedPickupTime, estimatedDeliveryTime).size() < 1) {
				logger.info("inside inner loop");
				i++;
				imp.add(order);
				lastDelivery = nextDeliveryLoc;
				logger.info("After Planning: from " + prevLoc + " To " + nextDeliveryLoc);
				StringParameter script = (StringParameter) order.getParameterBy("script");
				logger.info(script);
				StringParameter truckId = (StringParameter) order.getParameterBy("truckId");
				logger.info(truckId.getValueAsString());
				StringParameter driverId = (StringParameter) order.getParameterBy("driverId");
				script.setValue("planIndividualOrders");
				truckId.setValue(selectedTruck.getId());
				selectedTruck.setPolicyMap(simplePlacement);
				driverId.setValue(selectedDriver);
				logger.info(driverId);
				DurationParameter taskStartDate = new DurationParameter("TaskStart", "TaskStart", estimatedPickupTime);
				order.addParameter(taskStartDate);
				DurationParameter taskEndDate = new DurationParameter("TaskEnd", "TaskEnd", estimatedDeliveryTime);
				order.addParameter(taskEndDate);
				RSPTransaction tx1 = FeatherliteAgent.getTransaction(this);
				PlanOrderCommand poc = new PlanOrderCommand();
				poc.setOrder(order);
				tx1.addCommand(poc);
				tx1.commit();
				selectedTruck.setPolicyMap(truckPlacement);
				StringParameter estPickupTime = (StringParameter) order.getParameterBy("estPickupTime");
				estPickupTime.setValue(pickupDateText);
				logger.info("estPickupTime :" + estPickupTime.getValueAsString());
				String estDeliveryDate = DateFormat.format(new Date(estimatedDeliveryTime));
				StringParameter estDeliveryTime = (StringParameter) order.getParameterBy("estDeliveryTime");
				estDeliveryTime.setValue(estDeliveryDate);
				logger.info("estDeliveryTime :" + estDeliveryTime.getValueAsString());
				Integer estTravelTym = (int) ((estimatedDeliveryTime - earliestAvailTime) / 60000);
				StringParameter estTravelTime = (StringParameter) order.getParameterBy("estTravelTime");
				estTravelTime.setValue(Integer.toString(estTravelTym));
				logger.info("estTravelTime :" + estTravelTime);
				String CurrTruckLoc = selectedTruck.getStateVariableBy("Location").getValueAt(indEndTime).getValueAsString();
				StringParameter preceding_DM = (StringParameter) order.getParameterBy("preceding_DM");
				preceding_DM.setValue("0");
				logger.info("preceding_DM :" + preceding_DM);
				String Base = selectedTruck.getParameterBy("location").getValueAsString();
				StringParameter succeeding_DM = (StringParameter) order.getParameterBy("succeeding_DM");
				succeeding_DM.setValue("0");
				logger.info("succeeding_DM :" + succeeding_DM);
				Integer travelDur = (int) (GigaUtils.getDuration(pickupLoc, nextDeliveryLoc) / 60000);
				StringParameter travel_duration = (StringParameter) order.getParameterBy("travel_Duration");
				travel_duration.setValue(Integer.toString(travelDur));
				logger.info("travel_duration :" + travel_duration);
				StringParameter loadBuffer = (StringParameter) order.getParameterBy("loading_unloading_timeBuffer");
				loadBuffer.setValue(Integer.toString(90));
				logger.info("loadBuffer :" + loadBuffer);
				StringParameter restWaitBuffer = (StringParameter) order.getParameterBy("rest_Waiting_timeBuffer");
				restWaitBuffer.setValue(Integer.toString(0));
				logger.info("restWaitBuffer :" + restWaitBuffer);
				StringParameter baseLocStartTime = (StringParameter) order.getParameterBy("base_location_StartTime");
				if (order.equals(imp.get(0)) && CurrTruckLoc.equalsIgnoreCase(Base)) {
				    String baseStartDateText = DateFormat.format(new Date(earliestAvailTime));
				    baseLocStartTime.setValue(baseStartDateText);
				} else
				    baseLocStartTime.setValue("");
				logger.info("baseLocStartTime :" + baseLocStartTime);
			    }
			    logger.info("Direct Here");
			}
			logger.info("i & indLocalImportOrders.size() " + i + "," + indLocalImportOrders.size());
			if (i == indLocalImportOrders.size())
			    break;
		    }
		    logger.info("imp: " + imp);
		    if (imp.size() < 1)
			indLocalImportOrders.remove(0);
		    else {
			logger.info("firstOrder: " + imp.get(0));
			StringParameter preceding_DM = (StringParameter) imp.get(0).getParameterBy("preceding_DM");
			//preceding_DM.setValue(Double.toString(GigaUtils.getDistance(CurrTruckLoc, pickupLoc)));
			Double currToPickDist;
			Resource pickUpLocRes = RSPContainerHelper.getResourceMap(true).getEntryBy(pickupLoc);
			Double pickupLat = (Double) pickUpLocRes.getParameterBy("lat").getValue();
			Double pickupLong = (Double) pickUpLocRes.getParameterBy("long").getValue();
			String[] latLongValues = currLatLong.getParameterBy(selectedTruck.getId()).getValueAsString().split(",");
			Double currLati = Double.parseDouble(latLongValues[0]);
			Double currLongi = Double.parseDouble(latLongValues[1]);
			String[] currToPickDistance = FindDistanceDuration.getDistanceDuration(currLati, currLongi, pickupLat, pickupLong).split(",");
			currToPickDist = Double.parseDouble(currToPickDistance[0]);
			preceding_DM.setValue(Double.toString(currToPickDist));
			logger.info("preceding_DM :" + preceding_DM);
			logger.info("imp.get " + (imp.size() - 1));
			String Base = selectedTruck.getParameterBy("location").getValueAsString();
			StringParameter succeeding_DM = (StringParameter) imp.get(imp.size() - 1).getParameterBy("succeeding_DM");
			succeeding_DM.setValue(Double.toString(GigaUtils.getDistance(lastDelivery, Base)));
			logger.info("succeeding_DM :" + succeeding_DM);
			logger.info("imp: " + imp.size() + "Orders: " + imp);
			if (avlRigid.contains(selectedTruck))
			    avlRigid.remove(selectedTruck);
			else if (avlArticulated.contains(selectedTruck))
			    avlArticulated.remove(selectedTruck);
			logger.info(selectedTruck + "truckRemoved");
			logger.info("indLocalImportOrders: " + indLocalImportOrders);
			for (Order o : imp) {
			    if (indLocalImportOrders.contains(o)) {
				logger.info("o_imp: " + o);
				indLocalImportOrders.remove(o);
				logger.info(o + "is removed ");
			    }
			}
			logger.info("a->:" + a);
			logger.info("BatchCompleted");
			logger.info("ordersLeft: " + indLocalImportOrders);
		    }
		}
	    } catch (RSPException e) {
		e.printStackTrace();
	    }
	    logger.info("indLocalImport plan completed ");
	    logger.info("indLocalExportOrders: " + indLocalExportOrders.size());
	    try {
		int a1 = 0;
		while (indLocalExportOrders.size() > 0) {
		    logger.info("newBatchStarted");
		    List<Order> exp = new ArrayList<Order>();
		    String delivery = indLocalExportOrders.get(0).getParameterBy("deliveryLocation").getValueAsString();
		    logger.info("delivery: " + delivery);
		    long truckToPickup1Dur = 0L;
		    long min = Long.MAX_VALUE;
		    Resource selectedTruck = null;
		    String selectedDriver = null;
		    StringWriter w = new StringWriter();
		    String pickup1 = indLocalExportOrders.get(0).getParameterBy("pickupLocation").getValueAsString();
		    if (avlRigid.size() < 0 && avlRigidToyota.size() > 0)
			avlRigid.addAll(avlRigidToyota);
		    else if (avlRigid.size() < 0 && avlRigidToyota.size() < 0 && avlRigidIsuzu.size() > 0)
			avlRigid.addAll(avlRigidIsuzu);
		    else if (avlArticulated.size() < 0 && avlArticulatedToyota.size() > 0)
			avlArticulated.addAll(avlArticulatedToyota);
		    else if (avlArticulated.size() < 0 && avlArticulatedToyota.size() < 0 && avlArticulatedIsuzu.size() > 0)
			avlArticulated.addAll(avlArticulatedIsuzu);
		    logger.info("avlRigid: " + avlRigid.size());
		    if (avlRigid.size() > 0) {
			for (Resource r : avlRigid) {
			    w.append(r.getId() + ", ");
			    /*
			    StateValue<?> currentTruckLoc = r.getStateVariableBy("Location").getValueAt(indEndTime);
			    logger.info("currentLoc:" + currentTruckLoc);
			    if (currentTruckLoc == null)
			    	continue;
			    String currentTruckLocation = currentTruckLoc.getValueAsString();
			    Resource currentTruckLocRes = RSPContainerHelper.getResourceMap(true).getEntryBy(currentTruckLocation);

			    if (currentTruckLocRes == null) {

			    	continue;
			    }
			    */
			    Resource pickUpDynamic = RSPContainerHelper.getResourceMap(true).getEntryBy(pickup1);
			    Double pickUpLat = (Double) pickUpDynamic.getParameterBy("lat").getValue();
			    Double pickUpLong = (Double) pickUpDynamic.getParameterBy("long").getValue();
			    String[] latLongValues = currLatLong.getParameterBy(r.getId()).getValueAsString().split(",");
			    currLat = Double.parseDouble(latLongValues[0]);
			    currLong = Double.parseDouble(latLongValues[0]);
			    String[] truckToPickupDuration = FindDistanceDuration.getDistanceDuration(currLat, currLong, pickUpLat, pickUpLong).split(",");
			    truckToPickup1Dur = Long.parseLong(truckToPickupDuration[1]);
			    if (truckToPickup1Dur < min) {
				min = truckToPickup1Dur;
				selectedTruck = r;
				selectedDriver = r.getParameterBy("driverId").getValueAsString();
			    }
			}
		    } else if (avlRigid.size() < 1 && avlArticulated.size() > 0) {
			for (Resource r : avlArticulated) {
			    w.append(r.getId() + ", ");
			    /*
			    StateValue<?> currentTruckLoc = r.getStateVariableBy("Location").getValueAt(indEndTime);
			    logger.info("currentLoc:" + currentTruckLoc);
			    if (currentTruckLoc == null)
			    	continue;
			    String currentTruckLocation = currentTruckLoc.getValueAsString();
			    Resource currentTruckLocRes = RSPContainerHelper.getResourceMap(true).getEntryBy(currentTruckLocation);

			    if (currentTruckLocRes == null) {

			    	continue;
			    }
			    */
			    Resource pickUpDynamic = RSPContainerHelper.getResourceMap(true).getEntryBy(pickup1);
			    Double pickUpLat = (Double) pickUpDynamic.getParameterBy("lat").getValue();
			    Double pickUpLong = (Double) pickUpDynamic.getParameterBy("long").getValue();
			    String[] latLongValues = currLatLong.getParameterBy(r.getId()).getValueAsString().split(",");
			    currLat = Double.parseDouble(latLongValues[0]);
			    currLong = Double.parseDouble(latLongValues[0]);
			    String[] truckToPickupDuration = FindDistanceDuration.getDistanceDuration(currLat, currLong, pickUpLat, pickUpLong).split(",");
			    truckToPickup1Dur = Long.parseLong(truckToPickupDuration[1]);
			    if (truckToPickup1Dur < min) {
				min = truckToPickup1Dur;
				selectedTruck = r;
				selectedDriver = r.getParameterBy("driverId").getValueAsString();
			    }
			}
		    } else if (avlRigid.size() < 1 && avlArticulated.size() < 1)
			break;
		    logger.info("selectedTruck->" + selectedTruck);
		    logger.info("selectedDriver->" + selectedDriver);
		    Long earliestAvailTime = selectedTruck.getStateVariableBy("Location").getValueAt(indEndTime).getTime();
		    if (earliestAvailTime < indStartTime) {
			earliestAvailTime = indStartTime;
		    }
		    if (earliestAvailTime < currentTime)
			earliestAvailTime = currentTime + 2 * 60000L;
		    logger.info("earliestAvailTimeofSelectedTruck->" + new Date(earliestAvailTime));
		    Long estimatedPickup1Time = earliestAvailTime + min;
		    logger.info("estimatedPickup1Time->" + new Date(estimatedPickup1Time));
		    Long estimatedPickupTime = estimatedPickup1Time;
		    Long estimatedDeliveryTime = null;
		    for (int i = 0; i < 4; a1++) {
			Order order = indLocalExportOrders.get(i);
			StringParameter evaluatedTrucks = (StringParameter) order.getParameterBy("evaluatedTrucks");
			evaluatedTrucks.setValue(w.toString());
			logger.info("oder: " + order.getId());
			String prevLoc;
			String nextPickupLoc;
			if (order.equals(indLocalExportOrders.get(0))) {
			    logger.info("It is the first order");
			    prevLoc = selectedTruck.getStateVariableBy("Location").getValueAt(indEndTime).getValueAsString();
			    nextPickupLoc = order.getParameterBy("pickupLocation").getValueAsString();
			    logger.info("Before Planning: from " + prevLoc + " To " + nextPickupLoc);
			    estimatedPickupTime = estimatedPickup1Time;
			    logger.info("first estimatedPickupTime " + new Date(estimatedPickupTime));
			    if (estimatedPickup1Time + buffer + GigaUtils.getDuration(pickup1, delivery) < (indEndTime - buffer)) {
				estimatedDeliveryTime = estimatedPickup1Time + buffer + GigaUtils.getDuration(pickup1, delivery);
			    } else
				break;
			    logger.info("first estimatedDeliveryTime " + new Date(estimatedDeliveryTime));
			} else {
			    prevLoc = indLocalExportOrders.get(i - 1).getParameterBy("pickupLocation").getValueAsString();
			    nextPickupLoc = order.getParameterBy("pickupLocation").getValueAsString();
			    logger.info("Before Planning: from " + prevLoc + " To " + nextPickupLoc);
			    long prevLocToNextPickupDur = GigaUtils.getDuration(prevLoc, nextPickupLoc);
			    if (!nextPickupLoc.equalsIgnoreCase(prevLoc)) {
				estimatedPickupTime = estimatedPickupTime + prevLocToNextPickupDur + buffer;
				logger.info("second estimatedPickupTime " + new Date(estimatedPickupTime));
				if (estimatedPickupTime + GigaUtils.getDuration(nextPickupLoc, delivery) < (indEndTime - buffer)) {
				    estimatedDeliveryTime = estimatedPickupTime + GigaUtils.getDuration(nextPickupLoc, delivery);
				} else
				    break;
				logger.info("second estimatedDeliveryTime " + new Date(estimatedDeliveryTime));
			    } else {
				logger.info("third estimatedPickupTime " + new Date(estimatedPickupTime));
				logger.info("third estimatedDeliveryTime " + new Date(estimatedDeliveryTime));
				i++;
				exp.add(order);
				logger.info("After Planning: from " + prevLoc + " To " + nextPickupLoc);
				StringParameter script = (StringParameter) order.getParameterBy("script");
				logger.info(script);
				StringParameter truckId = (StringParameter) order.getParameterBy("truckId");
				logger.info(truckId.getValueAsString());
				StringParameter driverId = (StringParameter) order.getParameterBy("driverId");
				script.setValue("planIndividualOrders");
				truckId.setValue(selectedTruck.getId());
				selectedTruck.setPolicyMap(simplePlacement);
				driverId.setValue(selectedDriver);
				logger.info(driverId);
				DurationParameter taskStartDate = new DurationParameter("TaskStart", "TaskStart", estimatedPickupTime);
				order.addParameter(taskStartDate);
				DurationParameter taskEndDate = new DurationParameter("TaskEnd", "TaskEnd", estimatedDeliveryTime);
				order.addParameter(taskEndDate);
				RSPTransaction tx1 = FeatherliteAgent.getTransaction(this);
				PlanOrderCommand poc = new PlanOrderCommand();
				poc.setOrder(order);
				tx1.addCommand(poc);
				tx1.commit();
				selectedTruck.setPolicyMap(truckPlacement);
				String pickupDateText = DateFormat.format(new Date(estimatedPickupTime));
				StringParameter estPickupTime = (StringParameter) order.getParameterBy("estPickupTime");
				estPickupTime.setValue(pickupDateText);
				logger.info("estPickupTime 1:" + estPickupTime.getValueAsString());
				StringParameter preceding_DM = (StringParameter) order.getParameterBy("preceding_DM");
				preceding_DM.setValue("0");
				String Base = selectedTruck.getParameterBy("location").getValueAsString();
				StringParameter succeeding_DM = (StringParameter) order.getParameterBy("succeeding_DM");
				succeeding_DM.setValue("0");
				Integer travelDur = (int) (GigaUtils.getDuration(nextPickupLoc, delivery) / 60000);
				StringParameter travel_duration = (StringParameter) order.getParameterBy("travel_Duration");
				travel_duration.setValue(Integer.toString(travelDur));
				StringParameter loadBuffer = (StringParameter) order.getParameterBy("loading_unloading_timeBuffer");
				loadBuffer.setValue(Integer.toString(90));
				StringParameter restWaitBuffer = (StringParameter) order.getParameterBy("rest_Waiting_timeBuffer");
				restWaitBuffer.setValue(Integer.toString(0));
				String currTruckLoc = selectedTruck.getStateVariableBy("Location").getValueAt(indEndTime).getValueAsString();
				StringParameter baseLocStartTime = (StringParameter) order.getParameterBy("base_location_StartTime");
				if (order.equals(exp.get(0)) && currTruckLoc.equalsIgnoreCase(Base)) {
				    String baseStartDateText = DateFormat.format(new Date(earliestAvailTime));
				    baseLocStartTime.setValue(baseStartDateText);
				} else
				    baseLocStartTime.setValue("");
				/*
				 * Activity activity = new Activity();
				 * activity.setOrderNetId(order.getId());
				 * activity.setId(order.getId());
				 * activity.setName(order.getName());
				 * activity.setStart(earliestAvailTime);
				 * activity.setDuration(estimatedDeliveryTime-
				 * earliestAvailTime);
				 * selectedTruck.addActivity(activity);
				 */
			    }
			}
			if (estimatedDeliveryTime > (indEndTime - buffer)) {
			    logger.info("order:" + order + " cannot be planned in this batch");
			    break;
			} else {
			    if (order.getParameterBy("deliveryLocation").getValueAsString().equalsIgnoreCase(delivery) && selectedTruck.getActivitiesInInterval(estimatedPickupTime, estimatedDeliveryTime).size() < 1) {
				i++;
				exp.add(order);
				logger.info("After Planning: from " + prevLoc + " To " + nextPickupLoc);
				StringParameter script = (StringParameter) order.getParameterBy("script");
				logger.info(script);
				StringParameter truckId = (StringParameter) order.getParameterBy("truckId");
				logger.info(truckId.getValueAsString());
				StringParameter driverId = (StringParameter) order.getParameterBy("driverId");
				script.setValue("planIndividualOrders");
				truckId.setValue(selectedTruck.getId());
				selectedTruck.setPolicyMap(simplePlacement);
				driverId.setValue(selectedDriver);
				logger.info(driverId);
				DurationParameter taskStartDate = new DurationParameter("TaskStart", "TaskStart", estimatedPickupTime);
				order.addParameter(taskStartDate);
				DurationParameter taskEndDate = new DurationParameter("TaskEnd", "TaskEnd", estimatedDeliveryTime);
				order.addParameter(taskEndDate);
				RSPTransaction tx1 = FeatherliteAgent.getTransaction(this);
				PlanOrderCommand poc = new PlanOrderCommand();
				poc.setOrder(order);
				tx1.addCommand(poc);
				tx1.commit();
				selectedTruck.setPolicyMap(truckPlacement);
				String pickupDateText = DateFormat.format(new Date(estimatedPickupTime));
				StringParameter estPickupTime = (StringParameter) order.getParameterBy("estPickupTime");
				estPickupTime.setValue(pickupDateText);
				logger.info("estPickupTime 2:" + estPickupTime.getValueAsString());
				StringParameter preceding_DM = (StringParameter) order.getParameterBy("preceding_DM");
				preceding_DM.setValue("0");
				String Base = selectedTruck.getParameterBy("location").getValueAsString();
				StringParameter succeeding_DM = (StringParameter) order.getParameterBy("succeeding_DM");
				succeeding_DM.setValue("0");
				Integer travelDur = (int) (GigaUtils.getDuration(nextPickupLoc, delivery) / 60000);
				StringParameter travel_duration = (StringParameter) order.getParameterBy("travel_Duration");
				travel_duration.setValue(Integer.toString(travelDur));
				StringParameter loadBuffer = (StringParameter) order.getParameterBy("loading_unloading_timeBuffer");
				loadBuffer.setValue(Integer.toString(90));
				StringParameter restWaitBuffer = (StringParameter) order.getParameterBy("rest_Waiting_timeBuffer");
				restWaitBuffer.setValue(Integer.toString(0));
				String currTruckLoc = selectedTruck.getStateVariableBy("Location").getValueAt(indEndTime).getValueAsString();
				StringParameter baseLocStartTime = (StringParameter) order.getParameterBy("base_location_StartTime");
				if (order.equals(exp.get(0)) && currTruckLoc.equalsIgnoreCase(Base)) {
				    String baseStartDateText = DateFormat.format(new Date(earliestAvailTime));
				    baseLocStartTime.setValue(baseStartDateText);
				} else
				    baseLocStartTime.setValue("");
				/*
				 * Activity activity = new Activity();
				 * activity.setOrderNetId(order.getId());
				 * activity.setId(order.getId());
				 * activity.setName(order.getName());
				 * activity.setStart(earliestAvailTime);
				 * activity.setDuration(estimatedDeliveryTime-
				 * earliestAvailTime);
				 * selectedTruck.addActivity(activity);
				 */
			    }
			}
			if (i == indLocalExportOrders.size())
			    break;
		    }
		    if (exp.size() < 1)
			indLocalExportOrders.remove(0);
		    else {
			StringParameter preceding_DM = (StringParameter) exp.get(0).getParameterBy("preceding_DM");
			String feasiblePickup1 = exp.get(0).getParameterBy("pickupLocation").getValueAsString();
			Double currToPickDist;
			Resource pickUpLocRes = RSPContainerHelper.getResourceMap(true).getEntryBy(feasiblePickup1);
			Double pickupLat = (Double) pickUpLocRes.getParameterBy("lat").getValue();
			Double pickupLong = (Double) pickUpLocRes.getParameterBy("long").getValue();
			String[] latLongValues = currLatLong.getParameterBy(selectedTruck.getId()).getValueAsString().split(",");
			Double currLati = Double.parseDouble(latLongValues[0]);
			Double currLongi = Double.parseDouble(latLongValues[1]);
			String[] currToPickDistance = FindDistanceDuration.getDistanceDuration(currLati, currLongi, pickupLat, pickupLong).split(",");
			currToPickDist = Double.parseDouble(currToPickDistance[0]);
			preceding_DM.setValue(Double.toString(currToPickDist));
			//preceding_DM.setValue(Double.toString(GigaUtils.getDistance(CurrTruckLoc, feasiblePickup1)));
			String Base = selectedTruck.getParameterBy("location").getValueAsString();
			StringParameter succeeding_DM = (StringParameter) exp.get(exp.size() - 1).getParameterBy("succeeding_DM");
			succeeding_DM.setValue(Double.toString(GigaUtils.getDistance(delivery, Base)));
			logger.info("exp: " + exp.size() + "Orders: " + exp);
			if (avlRigid.contains(selectedTruck))
			    avlRigid.remove(selectedTruck);
			else if (avlArticulated.contains(selectedTruck))
			    avlArticulated.remove(selectedTruck);
			logger.info(selectedTruck + "truckRemoved");
			logger.info("indLocalExportOrders: " + indLocalExportOrders);
			for (Order o : exp) {
			    StringParameter estDeliveryTime = (StringParameter) o.getParameterBy("estDeliveryTime");
			    estDeliveryTime.setValue(DateFormat.format(new Date(estimatedDeliveryTime)));
			    logger.info("order :" + o.getId() + " deltime : " + estDeliveryTime.getValueAsString());
			    logger.info("estDeliveryTime :" + estDeliveryTime.getValueAsString());
			    Integer estTravelTym = (int) ((estimatedDeliveryTime - earliestAvailTime) / 60000);
			    StringParameter estTravelTime = (StringParameter) o.getParameterBy("estTravelTime");
			    estTravelTime.setValue(Integer.toString(estTravelTym));
			    logger.info("estTravelTime :" + estTravelTime);
			    if (indLocalExportOrders.contains(o)) {
				logger.info("o_exp: " + o);
				indLocalExportOrders.remove(o);
				logger.info(o + "is removed ");
			    }
			}
			logger.info("a1->:" + a1);
			logger.info("BatchCompleted");
			logger.info("ordersLeft: " + indLocalExportOrders);
		    }
		}
	    } catch (RSPException e) {
		e.printStackTrace();
	    }
	    logger.info("indLocalExport plan completed ");
	    logger.info("indOutstationImportOrders: " + indOutstationImportOrders.size());
	    try {
		int a2 = 0;
		while (indOutstationImportOrders.size() > 0) {
		    logger.info("newBatchStarted");
		    List<Order> imp = new ArrayList<Order>();
		    String pickupLoc = indOutstationImportOrders.get(0).getParameterBy("pickupLocation").getValueAsString();
		    logger.info("pickup: " + pickupLoc);
		    String lastDelivery = null;
		    long truckToPickupDur = 0L;
		    long min = Long.MAX_VALUE;
		    Resource selectedTruck = null;
		    String selectedDriver = null;
		    StringWriter w = new StringWriter();
		    if (avlRigid.size() < 0 && avlRigidToyota.size() > 0)
			avlRigid.addAll(avlRigidToyota);
		    else if (avlRigid.size() < 0 && avlRigidToyota.size() < 0 && avlRigidIsuzu.size() > 0)
			avlRigid.addAll(avlRigidIsuzu);
		    else if (avlArticulated.size() < 0 && avlArticulatedToyota.size() > 0)
			avlArticulated.addAll(avlArticulatedToyota);
		    else if (avlArticulated.size() < 0 && avlArticulatedToyota.size() < 0 && avlArticulatedIsuzu.size() > 0)
			avlArticulated.addAll(avlArticulatedIsuzu);
		    logger.info("avlRigid: " + avlRigid.size());
		    if (avlRigid.size() > 0) {
			for (Resource r : avlRigid) {
			    w.append(r.getId() + ", ");
			    /*
			    		StateValue<?> currentTruckLoc = r.getStateVariableBy("Location").getValueAt(indEndTime);
			    		logger.info("currentLoc:" + currentTruckLoc);
			    		if (currentTruckLoc == null)
			    			continue;
			    		String currentTruckLocation = currentTruckLoc.getValueAsString();
			    		Resource currentTruckLocRes = RSPContainerHelper.getResourceMap(true).getEntryBy(currentTruckLocation);

			    		if (currentTruckLocRes == null) {

			    			continue;
			    		}
			    		*/
			    Resource pickUpDynamic = RSPContainerHelper.getResourceMap(true).getEntryBy(pickupLoc);
			    Double pickUpLat = (Double) pickUpDynamic.getParameterBy("lat").getValue();
			    Double pickUpLong = (Double) pickUpDynamic.getParameterBy("long").getValue();
			    String[] latLongValues = currLatLong.getParameterBy(r.getId()).getValueAsString().split(",");
			    currLat = Double.parseDouble(latLongValues[0]);
			    currLong = Double.parseDouble(latLongValues[0]);
			    String[] truckToPickupDuration = FindDistanceDuration.getDistanceDuration(currLat, currLong, pickUpLat, pickUpLong).split(",");
			    truckToPickupDur = Long.parseLong(truckToPickupDuration[1]);
			    if (truckToPickupDur < min) {
				min = truckToPickupDur;
				selectedTruck = r;
				selectedDriver = r.getParameterBy("driverId").getValueAsString();
			    }
			}
		    } else if (avlRigid.size() < 1 && avlArticulated.size() > 0) {
			for (Resource r : avlArticulated) {
			    w.append(r.getId() + ", ");
			    /*

			    StateValue<?> currentTruckLoc = r.getStateVariableBy("Location").getValueAt(indEndTime);
			    logger.info("currentLoc:" + currentTruckLoc);
			    if (currentTruckLoc == null)
			    	continue;
			    String currentTruckLocation = currentTruckLoc.getValueAsString();
			    Resource currentTruckLocRes = RSPContainerHelper.getResourceMap(true).getEntryBy(currentTruckLocation);

			    if (currentTruckLocRes == null) {

			    	continue;
			    }
			    */
			    Resource pickUpDynamic = RSPContainerHelper.getResourceMap(true).getEntryBy(pickupLoc);
			    Double pickUpLat = (Double) pickUpDynamic.getParameterBy("lat").getValue();
			    Double pickUpLong = (Double) pickUpDynamic.getParameterBy("long").getValue();
			    String[] latLongValues = currLatLong.getParameterBy(r.getId()).getValueAsString().split(",");
			    currLat = Double.parseDouble(latLongValues[0]);
			    currLong = Double.parseDouble(latLongValues[0]);
			    String[] truckToPickupDuration = FindDistanceDuration.getDistanceDuration(currLat, currLong, pickUpLat, pickUpLong).split(",");
			    truckToPickupDur = Long.parseLong(truckToPickupDuration[1]);
			    if (truckToPickupDur < min) {
				min = truckToPickupDur;
				selectedTruck = r;
				selectedDriver = r.getParameterBy("driverId").getValueAsString();
			    }
			}
		    } else if (avlRigid.size() < 1 && avlArticulated.size() < 1)
			break;
		    logger.info("selectedTruck->" + selectedTruck);
		    logger.info("selectedDriver->" + selectedDriver);
		    Long earliestAvailTime = selectedTruck.getStateVariableBy("Location").getValueAt(indEndTime).getTime();
		    logger.info("indStartTime->" + new Date(indStartTime));
		    logger.info("earliestAvailTimeofSelectedTruck1->" + new Date(earliestAvailTime));
		    if (earliestAvailTime < indStartTime) {
			earliestAvailTime = indStartTime;
		    }
		    if (earliestAvailTime < currentTime)
			earliestAvailTime = currentTime + 2 * 60000L;
		    logger.info("earliestAvailTimeofSelectedTruck->" + new Date(earliestAvailTime));
		    Long estimatedPickupTime = earliestAvailTime + min;
		    logger.info("estimatedPickupTime->" + new Date(estimatedPickupTime));
		    String pickupDateText = DateFormat.format(new Date(estimatedPickupTime));
		    Long estimatedDeliveryTime = estimatedPickupTime;
		    for (int i = 0; i < 4; a2++) {
			Order order = indOutstationImportOrders.get(i);
			StringParameter evaluatedTrucks = (StringParameter) order.getParameterBy("evaluatedTrucks");
			evaluatedTrucks.setValue(w.toString());
			/*
			 * RSPTransaction tx =
			 * FeatherliteAgent.getTransaction(this);
			 * AddOrderCommand aoc = new AddOrderCommand();
			 * aoc.setOrder(order); tx.addCommand(aoc); tx.commit();
			 */
			logger.info("oder: " + order.getId());
			String prevLoc;
			String nextDeliveryLoc;
			if (order.equals(indOutstationImportOrders.get(0))) {
			    logger.info("It is the first order");
			    prevLoc = pickupLoc;
			} else {
			    prevLoc = indOutstationImportOrders.get(i - 1).getParameterBy("deliveryLocation").getValueAsString();
			}
			;
			nextDeliveryLoc = order.getParameterBy("deliveryLocation").getValueAsString();
			logger.info("Before Planning: from " + prevLoc + " To " + nextDeliveryLoc);
			long prevLocToNextdeliveryDur = GigaUtils.getDuration(prevLoc, nextDeliveryLoc);
			if (!nextDeliveryLoc.equalsIgnoreCase(prevLoc))
			    estimatedDeliveryTime += prevLocToNextdeliveryDur + buffer;
			else {
			    i++;
			    imp.add(order);
			    lastDelivery = nextDeliveryLoc;
			    logger.info("After Planning: from " + prevLoc + " To " + nextDeliveryLoc);
			    StringParameter script = (StringParameter) order.getParameterBy("script");
			    logger.info(script);
			    StringParameter truckId = (StringParameter) order.getParameterBy("truckId");
			    logger.info(truckId.getValueAsString());
			    StringParameter driverId = (StringParameter) order.getParameterBy("driverId");
			    script.setValue("planIndividualOrders");
			    truckId.setValue(selectedTruck.getId());
			    selectedTruck.setPolicyMap(simplePlacement);
			    driverId.setValue(selectedDriver);
			    logger.info(driverId);
			    DurationParameter taskStartDate = new DurationParameter("TaskStart", "TaskStart", estimatedPickupTime);
			    order.addParameter(taskStartDate);
			    DurationParameter taskEndDate = new DurationParameter("TaskEnd", "TaskEnd", estimatedDeliveryTime);
			    order.addParameter(taskEndDate);
			    RSPTransaction tx1 = FeatherliteAgent.getTransaction(this);
			    PlanOrderCommand poc = new PlanOrderCommand();
			    poc.setOrder(order);
			    tx1.addCommand(poc);
			    tx1.commit();
			    selectedTruck.setPolicyMap(truckPlacement);
			    StringParameter estPickupTime = (StringParameter) order.getParameterBy("estPickupTime");
			    estPickupTime.setValue(pickupDateText);
			    logger.info("estPickupTime :" + estPickupTime.getValueAsString());
			    String estDeliveryDate = DateFormat.format(new Date(estimatedDeliveryTime));
			    StringParameter estDeliveryTime = (StringParameter) order.getParameterBy("estDeliveryTime");
			    estDeliveryTime.setValue(estDeliveryDate);
			    logger.info("estDeliveryTime :" + estDeliveryTime.getValueAsString());
			    Integer estTravelTym = (int) ((estimatedDeliveryTime - earliestAvailTime) / 60000);
			    StringParameter estTravelTime = (StringParameter) order.getParameterBy("estTravelTime");
			    estTravelTime.setValue(Integer.toString(estTravelTym));
			    logger.info("estTravelTime :" + estTravelTime);
			    String CurrTruckLoc = selectedTruck.getStateVariableBy("Location").getValueAt(indEndTime).getValueAsString();
			    StringParameter preceding_DM = (StringParameter) order.getParameterBy("preceding_DM");
			    preceding_DM.setValue("0");
			    String Base = selectedTruck.getParameterBy("location").getValueAsString();
			    StringParameter succeeding_DM = (StringParameter) order.getParameterBy("succeeding_DM");
			    succeeding_DM.setValue("0");
			    Integer travelDur = (int) (GigaUtils.getDuration(pickupLoc, nextDeliveryLoc) / 60000);
			    StringParameter travel_duration = (StringParameter) order.getParameterBy("travel_Duration");
			    travel_duration.setValue(Integer.toString(travelDur));
			    StringParameter loadBuffer = (StringParameter) order.getParameterBy("loading_unloading_timeBuffer");
			    loadBuffer.setValue(Integer.toString(90));
			    StringParameter restWaitBuffer = (StringParameter) order.getParameterBy("rest_Waiting_timeBuffer");
			    restWaitBuffer.setValue(Integer.toString(0));
			    StringParameter baseLocStartTime = (StringParameter) order.getParameterBy("base_location_StartTime");
			    if (order.equals(indOutstationImportOrders.get(0)) && CurrTruckLoc.equalsIgnoreCase(Base)) {
				String baseStartDateText = DateFormat.format(new Date(earliestAvailTime));
				baseLocStartTime.setValue(baseStartDateText);
			    } else
				baseLocStartTime.setValue("");
			    /*
			     * Activityactivity =newActivity();
			     * activity.setOrderNetId(order.getId());
			     * activity.setId(order.getId());
			     * activity.setName(order.getName());
			     * activity.setStart(earliestAvailTime);
			     * activity.setDuration(estimatedDeliveryTime-
			     * earliestAvailTime);
			     * selectedTruck.addActivity(activity);
			     */
			}
			if (estimatedDeliveryTime > (indEndTime - buffer)) {
			    logger.info("order:" + order + " cannot be planned in this batch");
			    break;
			} else {
			    if (order.getParameterBy("pickupLocation").getValueAsString().equalsIgnoreCase(pickupLoc) && selectedTruck.getActivitiesInInterval(estimatedPickupTime, estimatedDeliveryTime).size() < 1) {
				i++;
				imp.add(order);
				lastDelivery = nextDeliveryLoc;
				logger.info("After Planning: from " + prevLoc + " To " + nextDeliveryLoc);
				StringParameter script = (StringParameter) order.getParameterBy("script");
				logger.info(script);
				StringParameter truckId = (StringParameter) order.getParameterBy("truckId");
				logger.info(truckId.getValueAsString());
				StringParameter driverId = (StringParameter) order.getParameterBy("driverId");
				script.setValue("planIndividualOrders");
				truckId.setValue(selectedTruck.getId());
				selectedTruck.setPolicyMap(simplePlacement);
				driverId.setValue(selectedDriver);
				logger.info(driverId);
				DurationParameter taskStartDate = new DurationParameter("TaskStart", "TaskStart", estimatedPickupTime);
				order.addParameter(taskStartDate);
				DurationParameter taskEndDate = new DurationParameter("TaskEnd", "TaskEnd", estimatedDeliveryTime);
				order.addParameter(taskEndDate);
				RSPTransaction tx1 = FeatherliteAgent.getTransaction(this);
				PlanOrderCommand poc = new PlanOrderCommand();
				poc.setOrder(order);
				tx1.addCommand(poc);
				tx1.commit();
				selectedTruck.setPolicyMap(truckPlacement);
				StringParameter estPickupTime = (StringParameter) order.getParameterBy("estPickupTime");
				estPickupTime.setValue(pickupDateText);
				logger.info("estPickupTime :" + estPickupTime.getValueAsString());
				String estDeliveryDate = DateFormat.format(new Date(estimatedDeliveryTime));
				StringParameter estDeliveryTime = (StringParameter) order.getParameterBy("estDeliveryTime");
				estDeliveryTime.setValue(estDeliveryDate);
				logger.info("estDeliveryTime :" + estDeliveryTime.getValueAsString());
				Integer estTravelTym = (int) ((estimatedDeliveryTime - earliestAvailTime) / 60000);
				StringParameter estTravelTime = (StringParameter) order.getParameterBy("estTravelTime");
				estTravelTime.setValue(Integer.toString(estTravelTym));
				logger.info("estTravelTime :" + estTravelTime);
				String CurrTruckLoc = selectedTruck.getStateVariableBy("Location").getValueAt(indEndTime).getValueAsString();
				StringParameter preceding_DM = (StringParameter) order.getParameterBy("preceding_DM");
				preceding_DM.setValue("0");
				String Base = selectedTruck.getParameterBy("location").getValueAsString();
				StringParameter succeeding_DM = (StringParameter) order.getParameterBy("succeeding_DM");
				succeeding_DM.setValue("0");
				Integer travelDur = (int) (GigaUtils.getDuration(pickupLoc, nextDeliveryLoc) / 60000);
				StringParameter travel_duration = (StringParameter) order.getParameterBy("travel_Duration");
				travel_duration.setValue(Integer.toString(travelDur));
				StringParameter loadBuffer = (StringParameter) order.getParameterBy("loading_unloading_timeBuffer");
				loadBuffer.setValue(Integer.toString(90));
				StringParameter restWaitBuffer = (StringParameter) order.getParameterBy("rest_Waiting_timeBuffer");
				restWaitBuffer.setValue(Integer.toString(0));
				StringParameter baseLocStartTime = (StringParameter) order.getParameterBy("base_location_StartTime");
				if (order.equals(indOutstationImportOrders.get(0)) && CurrTruckLoc.equalsIgnoreCase(Base)) {
				    String baseStartDateText = DateFormat.format(new Date(earliestAvailTime));
				    baseLocStartTime.setValue(baseStartDateText);
				} else
				    baseLocStartTime.setValue("");
				/*
				 * Activityactivity =newActivity();
				 * activity.setOrderNetId(order.getId());
				 * activity.setId(order.getId());
				 * activity.setName(order.getName());
				 * activity.setStart(earliestAvailTime);
				 * activity.setDuration(estimatedDeliveryTime-
				 * earliestAvailTime);
				 * selectedTruck.addActivity(activity);
				 */
			    }
			}
			if (i == indOutstationImportOrders.size())
			    break;
		    }
		    if (imp.size() < 1)
			indOutstationImportOrders.remove(0);
		    else {
			StringParameter preceding_DM = (StringParameter) imp.get(0).getParameterBy("preceding_DM");
			Double currToPickDist;
			Resource pickUpLocRes = RSPContainerHelper.getResourceMap(true).getEntryBy(pickupLoc);
			Double pickupLat = (Double) pickUpLocRes.getParameterBy("lat").getValue();
			Double pickupLong = (Double) pickUpLocRes.getParameterBy("long").getValue();
			String[] latLongValues = currLatLong.getParameterBy(selectedTruck.getId()).getValueAsString().split(",");
			Double currLati = Double.parseDouble(latLongValues[0]);
			Double currLongi = Double.parseDouble(latLongValues[1]);
			String[] currToPickDistance = FindDistanceDuration.getDistanceDuration(currLati, currLongi, pickupLat, pickupLong).split(",");
			currToPickDist = Double.parseDouble(currToPickDistance[0]);
			preceding_DM.setValue(Double.toString(currToPickDist));
			//preceding_DM.setValue(Double.toString(GigaUtils.getDistance(CurrTruckLoc, pickupLoc)));
			String Base = selectedTruck.getParameterBy("location").getValueAsString();
			StringParameter succeeding_DM = (StringParameter) imp.get(imp.size() - 1).getParameterBy("succeeding_DM");
			succeeding_DM.setValue(Double.toString(GigaUtils.getDistance(lastDelivery, Base)));
			logger.info("imp: " + imp.size() + "Orders: " + imp);
			if (avlRigid.contains(selectedTruck))
			    avlRigid.remove(selectedTruck);
			else if (avlArticulated.contains(selectedTruck))
			    avlArticulated.remove(selectedTruck);
			logger.info(selectedTruck + "truckRemoved");
			logger.info("indLocalImportOrders: " + indOutstationImportOrders);
			for (Order o : imp) {
			    if (indOutstationImportOrders.contains(o)) {
				logger.info("o_imp: " + o);
				indOutstationImportOrders.remove(o);
				logger.info(o + "is removed ");
			    }
			}
			logger.info("a2->:" + a2);
			logger.info("BatchCompleted");
			logger.info("ordersLeft: " + indOutstationImportOrders);
		    }
		}
	    } catch (RSPException e) {
		e.printStackTrace();
	    }
	    logger.info("indOutstationImportOrders plan completed ");
	    logger.info("indOutstationExportOrders: " + indOutstationExportOrders.size());
	    try {
		int a3 = 0;
		while (indOutstationExportOrders.size() > 0) {
		    logger.info("newBatchStarted");
		    List<Order> exp = new ArrayList<Order>();
		    String delivery = indOutstationExportOrders.get(0).getParameterBy("deliveryLocation").getValueAsString();
		    logger.info("delivery: " + delivery);
		    long truckToPickup1Dur = 0L;
		    long min = Long.MAX_VALUE;
		    Resource selectedTruck = null;
		    String selectedDriver = null;
		    StringWriter w = new StringWriter();
		    String pickup1 = indOutstationExportOrders.get(0).getParameterBy("pickupLocation").getValueAsString();
		    if (avlRigid.size() < 0 && avlRigidToyota.size() > 0)
			avlRigid.addAll(avlRigidToyota);
		    else if (avlRigid.size() < 0 && avlRigidToyota.size() < 0 && avlRigidIsuzu.size() > 0)
			avlRigid.addAll(avlRigidIsuzu);
		    else if (avlArticulated.size() < 0 && avlArticulatedToyota.size() > 0)
			avlArticulated.addAll(avlArticulatedToyota);
		    else if (avlArticulated.size() < 0 && avlArticulatedToyota.size() < 0 && avlArticulatedIsuzu.size() > 0)
			avlArticulated.addAll(avlArticulatedIsuzu);
		    logger.info("avlRigid: " + avlRigid.size());
		    if (avlRigid.size() > 0) {
			for (Resource r : avlRigid) {
			    w.append(r.getId() + ", ");
			    /*
			    StateValue<?> currentTruckLoc = r.getStateVariableBy("Location").getValueAt(indEndTime);
			    logger.info("currentLoc:" + currentTruckLoc);
			    if (currentTruckLoc == null)
			    	continue;
			    String currentTruckLocation = currentTruckLoc.getValueAsString();
			    Resource currentTruckLocRes = RSPContainerHelper.getResourceMap(true).getEntryBy(currentTruckLocation);

			    if (currentTruckLocRes == null) {

			    	continue;
			    }
			    */
			    Resource pickUpDynamic = RSPContainerHelper.getResourceMap(true).getEntryBy(pickup1);
			    Double pickUpLat = (Double) pickUpDynamic.getParameterBy("lat").getValue();
			    Double pickUpLong = (Double) pickUpDynamic.getParameterBy("long").getValue();
			    String[] latLongValues = currLatLong.getParameterBy(r.getId()).getValueAsString().split(",");
			    currLat = Double.parseDouble(latLongValues[0]);
			    currLong = Double.parseDouble(latLongValues[0]);
			    String[] truckToPickupDuration = FindDistanceDuration.getDistanceDuration(currLat, currLong, pickUpLat, pickUpLong).split(",");
			    truckToPickup1Dur = Long.parseLong(truckToPickupDuration[1]);
			    if (truckToPickup1Dur < min) {
				min = truckToPickup1Dur;
				selectedTruck = r;
				selectedDriver = r.getParameterBy("driverId").getValueAsString();
			    }
			}
		    } else if (avlRigid.size() < 1 && avlArticulated.size() > 0) {
			for (Resource r : avlArticulated) {
			    w.append(r.getId() + ", ");
			    /*
			    StateValue<?> currentTruckLoc = r.getStateVariableBy("Location").getValueAt(indEndTime);
			    logger.info("currentLoc:" + currentTruckLoc);
			    if (currentTruckLoc == null)
			    	continue;
			    String currentTruckLocation = currentTruckLoc.getValueAsString();
			    Resource currentTruckLocRes = RSPContainerHelper.getResourceMap(true).getEntryBy(currentTruckLocation);

			    if (currentTruckLocRes == null) {

			    	continue;
			    }
			    */
			    Resource pickUpDynamic = RSPContainerHelper.getResourceMap(true).getEntryBy(pickup1);
			    Double pickUpLat = (Double) pickUpDynamic.getParameterBy("lat").getValue();
			    Double pickUpLong = (Double) pickUpDynamic.getParameterBy("long").getValue();
			    String[] latLongValues = currLatLong.getParameterBy(r.getId()).getValueAsString().split(",");
			    currLat = Double.parseDouble(latLongValues[0]);
			    currLong = Double.parseDouble(latLongValues[0]);
			    String[] truckToPickupDuration = FindDistanceDuration.getDistanceDuration(currLat, currLong, pickUpLat, pickUpLong).split(",");
			    truckToPickup1Dur = Long.parseLong(truckToPickupDuration[1]);
			    if (truckToPickup1Dur < min) {
				min = truckToPickup1Dur;
				selectedTruck = r;
				selectedDriver = r.getParameterBy("driverId").getValueAsString();
			    }
			}
		    } else if (avlRigid.size() < 1 && avlArticulated.size() < 1)
			break;
		    logger.info("selectedTruck->" + selectedTruck);
		    logger.info("selectedDriver->" + selectedDriver);
		    Long earliestAvailTime = selectedTruck.getStateVariableBy("Location").getValueAt(indEndTime).getTime();
		    if (earliestAvailTime < indStartTime) {
			earliestAvailTime = indStartTime;
		    }
		    if (earliestAvailTime < currentTime)
			earliestAvailTime = currentTime + 2 * 60000L;
		    logger.info("earliestAvailTimeofSelectedTruck->" + new Date(earliestAvailTime));
		    Long estimatedPickup1Time = earliestAvailTime + min;
		    logger.info("estimatedPickup1Time->" + new Date(estimatedPickup1Time));
		    Long estimatedPickupTime = estimatedPickup1Time;
		    Long estimatedDeliveryTime = null;
		    for (int i = 0; i < 4; a3++) {
			Order order = indLocalExportOrders.get(i);
			StringParameter evaluatedTrucks = (StringParameter) order.getParameterBy("evaluatedTrucks");
			evaluatedTrucks.setValue(w.toString());
			logger.info("oder: " + order.getId());
			String prevLoc;
			String nextPickupLoc;
			if (order.equals(indOutstationExportOrders.get(0))) {
			    logger.info("It is the first order");
			    prevLoc = selectedTruck.getStateVariableBy("Location").getValueAt(indEndTime).getValueAsString();
			    nextPickupLoc = order.getParameterBy("pickupLocation").getValueAsString();
			    logger.info("Before Planning: from " + prevLoc + " To " + nextPickupLoc);
			    estimatedPickupTime = estimatedPickup1Time;
			    estimatedDeliveryTime = estimatedPickup1Time + buffer + GigaUtils.getDuration(pickup1, delivery);
			} else {
			    prevLoc = indOutstationExportOrders.get(i - 1).getParameterBy("pickupLocation").getValueAsString();
			    nextPickupLoc = order.getParameterBy("pickupLocation").getValueAsString();
			    logger.info("Before Planning: from " + prevLoc + " To " + nextPickupLoc);
			    long prevLocToNextPickupDur = GigaUtils.getDuration(prevLoc, nextPickupLoc);
			    if (!nextPickupLoc.equalsIgnoreCase(prevLoc)) {
				estimatedPickupTime = estimatedPickupTime + prevLocToNextPickupDur + buffer;
				estimatedDeliveryTime = estimatedPickupTime + GigaUtils.getDuration(nextPickupLoc, delivery);
			    } else {
				i++;
				exp.add(order);
				logger.info("After Planning: from " + prevLoc + " To " + nextPickupLoc);
				StringParameter script = (StringParameter) order.getParameterBy("script");
				logger.info(script);
				StringParameter truckId = (StringParameter) order.getParameterBy("truckId");
				logger.info(truckId.getValueAsString());
				StringParameter driverId = (StringParameter) order.getParameterBy("driverId");
				script.setValue("planIndividualOrders");
				truckId.setValue(selectedTruck.getId());
				selectedTruck.setPolicyMap(simplePlacement);
				driverId.setValue(selectedDriver);
				logger.info(driverId);
				DurationParameter taskStartDate = new DurationParameter("TaskStart", "TaskStart", estimatedPickupTime);
				order.addParameter(taskStartDate);
				DurationParameter taskEndDate = new DurationParameter("TaskEnd", "TaskEnd", estimatedDeliveryTime);
				order.addParameter(taskEndDate);
				RSPTransaction tx1 = FeatherliteAgent.getTransaction(this);
				PlanOrderCommand poc = new PlanOrderCommand();
				poc.setOrder(order);
				tx1.addCommand(poc);
				tx1.commit();
				selectedTruck.setPolicyMap(truckPlacement);
				String pickupDateText = DateFormat.format(new Date(estimatedPickupTime));
				StringParameter estPickupTime = (StringParameter) order.getParameterBy("estPickupTime");
				estPickupTime.setValue(pickupDateText);
				logger.info("estPickupTime :" + estPickupTime.getValueAsString());
				StringParameter preceding_DM = (StringParameter) order.getParameterBy("preceding_DM");
				preceding_DM.setValue("0");
				String Base = selectedTruck.getParameterBy("location").getValueAsString();
				StringParameter succeeding_DM = (StringParameter) order.getParameterBy("succeeding_DM");
				succeeding_DM.setValue("0");
				Integer travelDur = (int) (GigaUtils.getDuration(nextPickupLoc, delivery) / 60000);
				StringParameter travel_duration = (StringParameter) order.getParameterBy("travel_Duration");
				travel_duration.setValue(Integer.toString(travelDur));
				StringParameter loadBuffer = (StringParameter) order.getParameterBy("loading_unloading_timeBuffer");
				loadBuffer.setValue(Integer.toString(90));
				StringParameter restWaitBuffer = (StringParameter) order.getParameterBy("rest_Waiting_timeBuffer");
				restWaitBuffer.setValue(Integer.toString(0));
				String currTruckLoc = selectedTruck.getStateVariableBy("Location").getValueAt(indEndTime).getValueAsString();
				StringParameter baseLocStartTime = (StringParameter) order.getParameterBy("base_location_StartTime");
				if (order.equals(exp.get(0)) && currTruckLoc.equalsIgnoreCase(Base)) {
				    String baseStartDateText = DateFormat.format(new Date(earliestAvailTime));
				    baseLocStartTime.setValue(baseStartDateText);
				} else
				    baseLocStartTime.setValue("");
			    }
			}
			if (estimatedDeliveryTime > (indEndTime - buffer)) {
			    logger.info("order:" + order + " cannot be planned in this batch");
			    break;
			} else {
			    if (order.getParameterBy("deliveryLocation").getValueAsString().equalsIgnoreCase(delivery) && selectedTruck.getActivitiesInInterval(estimatedPickupTime, estimatedDeliveryTime).size() < 1) {
				i++;
				exp.add(order);
				logger.info("After Planning: from " + prevLoc + " To " + nextPickupLoc);
				StringParameter script = (StringParameter) order.getParameterBy("script");
				logger.info(script);
				StringParameter truckId = (StringParameter) order.getParameterBy("truckId");
				logger.info(truckId.getValueAsString());
				StringParameter driverId = (StringParameter) order.getParameterBy("driverId");
				script.setValue("planIndividualOrders");
				truckId.setValue(selectedTruck.getId());
				selectedTruck.setPolicyMap(simplePlacement);
				driverId.setValue(selectedDriver);
				logger.info(driverId);
				DurationParameter taskStartDate = new DurationParameter("TaskStart", "TaskStart", estimatedPickupTime);
				order.addParameter(taskStartDate);
				DurationParameter taskEndDate = new DurationParameter("TaskEnd", "TaskEnd", estimatedDeliveryTime);
				order.addParameter(taskEndDate);
				RSPTransaction tx1 = FeatherliteAgent.getTransaction(this);
				PlanOrderCommand poc = new PlanOrderCommand();
				poc.setOrder(order);
				tx1.addCommand(poc);
				tx1.commit();
				selectedTruck.setPolicyMap(truckPlacement);
				String pickupDateText = DateFormat.format(new Date(estimatedPickupTime));
				StringParameter estPickupTime = (StringParameter) order.getParameterBy("estPickupTime");
				estPickupTime.setValue(pickupDateText);
				logger.info("estPickupTime :" + estPickupTime.getValueAsString());
				StringParameter preceding_DM = (StringParameter) order.getParameterBy("preceding_DM");
				preceding_DM.setValue("0");
				String Base = selectedTruck.getParameterBy("location").getValueAsString();
				StringParameter succeeding_DM = (StringParameter) order.getParameterBy("succeeding_DM");
				succeeding_DM.setValue("0");
				Integer travelDur = (int) (GigaUtils.getDuration(nextPickupLoc, delivery) / 60000);
				StringParameter travel_duration = (StringParameter) order.getParameterBy("travel_Duration");
				travel_duration.setValue(Integer.toString(travelDur));
				StringParameter loadBuffer = (StringParameter) order.getParameterBy("loading_unloading_timeBuffer");
				loadBuffer.setValue(Integer.toString(90));
				StringParameter restWaitBuffer = (StringParameter) order.getParameterBy("rest_Waiting_timeBuffer");
				restWaitBuffer.setValue(Integer.toString(0));
				String currTruckLoc = selectedTruck.getStateVariableBy("Location").getValueAt(indEndTime).getValueAsString();
				StringParameter baseLocStartTime = (StringParameter) order.getParameterBy("base_location_StartTime");
				if (order.equals(exp.get(0)) && currTruckLoc.equalsIgnoreCase(Base)) {
				    String baseStartDateText = DateFormat.format(new Date(earliestAvailTime));
				    baseLocStartTime.setValue(baseStartDateText);
				} else
				    baseLocStartTime.setValue("");
			    }
			}
			if (i == indOutstationExportOrders.size())
			    break;
		    }
		    if (exp.size() < 1)
			indOutstationExportOrders.remove(0);
		    else {
			StringParameter preceding_DM = (StringParameter) exp.get(0).getParameterBy("preceding_DM");
			String feasiblePickup1 = exp.get(0).getParameterBy("pickupLocation").getValueAsString();
			Double currToPickDist;
			Resource pickUpLocRes = RSPContainerHelper.getResourceMap(true).getEntryBy(feasiblePickup1);
			Double pickupLat = (Double) pickUpLocRes.getParameterBy("lat").getValue();
			Double pickupLong = (Double) pickUpLocRes.getParameterBy("long").getValue();
			String[] latLongValues = currLatLong.getParameterBy(selectedTruck.getId()).getValueAsString().split(",");
			Double currLati = Double.parseDouble(latLongValues[0]);
			Double currLongi = Double.parseDouble(latLongValues[1]);
			String[] currToPickDistance = FindDistanceDuration.getDistanceDuration(currLati, currLongi, pickupLat, pickupLong).split(",");
			currToPickDist = Double.parseDouble(currToPickDistance[0]);
			preceding_DM.setValue(Double.toString(currToPickDist));
			//	preceding_DM.setValue(Double.toString(GigaUtils.getDistance(CurrTruckLoc, feasiblePickup1)));
			String Base = selectedTruck.getParameterBy("location").getValueAsString();
			StringParameter succeeding_DM = (StringParameter) exp.get(exp.size() - 1).getParameterBy("succeeding_DM");
			succeeding_DM.setValue(Double.toString(GigaUtils.getDistance(delivery, Base)));
			logger.info("exp: " + exp.size() + "Orders: " + exp);
			if (avlRigid.contains(selectedTruck))
			    avlRigid.remove(selectedTruck);
			else if (avlArticulated.contains(selectedTruck))
			    avlArticulated.remove(selectedTruck);
			logger.info(selectedTruck + "truckRemoved");
			logger.info("indOutstationExportOrders: " + indOutstationExportOrders);
			for (Order o : exp) {
			    StringParameter estDeliveryTime = (StringParameter) o.getParameterBy("estDeliveryTime");
			    estDeliveryTime.setValue(DateFormat.format(new Date(estimatedDeliveryTime)));
			    logger.info("estDeliveryTime :" + estDeliveryTime.getValueAsString());
			    Integer estTravelTym = (int) ((estimatedDeliveryTime - earliestAvailTime) / 60000);
			    StringParameter estTravelTime = (StringParameter) o.getParameterBy("estTravelTime");
			    estTravelTime.setValue(Integer.toString(estTravelTym));
			    logger.info("estTravelTime :" + estTravelTime);
			    if (indOutstationExportOrders.contains(o)) {
				logger.info("o_exp: " + o);
				indOutstationExportOrders.remove(o);
				logger.info(o + "is removed ");
			    }
			}
			logger.info("a3->:" + a3);
			logger.info("BatchCompleted");
			logger.info("ordersLeft: " + indOutstationExportOrders);
		    }
		}
	    } catch (RSPException e) {
		e.printStackTrace();
	    }
	    logger.info("indOutstationExportOrders plan completed ");
	    logger.info("indLocalDLTOrders: " + indLocalDLTOrders.size());
	    try {
		Resource selectedTruck = null;
		String selectedDriver = null;
		long truckToPickupDur = 0L;
		for (Order order : indLocalDLTOrders) {
		    long min = Long.MAX_VALUE;
		    String pickupLoc = order.getParameterBy("pickupLocation").getValueAsString();
		    String deliveryLoc = order.getParameterBy("deliveryLocation").getValueAsString();
		    long EarliestAvailableTime = 0;
		    StringParameter script = (StringParameter) order.getParameterBy("script");
		    logger.info(script);
		    StringParameter truckId = (StringParameter) order.getParameterBy("truckId");
		    logger.info(truckId.getValueAsString());
		    StringParameter driverId = (StringParameter) order.getParameterBy("driverId");
		    long estimatedPickupTime = 0;
		    long estimatedDeliveryTime = 0;
		    StringWriter w = new StringWriter();
		    if (avlSingle.size() < 0 && avlSingleToyota.size() > 0)
			avlSingle.addAll(avlSingleToyota);
		    else if (avlSingle.size() < 0 && avlSingleToyota.size() < 0 && avlSingleIsuzu.size() > 0)
			avlSingle.addAll(avlSingleIsuzu);
		    else if (avlRigid.size() < 0 && avlRigidToyota.size() > 0)
			avlRigid.addAll(avlRigidToyota);
		    else if (avlRigid.size() < 0 && avlRigidToyota.size() < 0 && avlRigidIsuzu.size() > 0)
			avlRigid.addAll(avlRigidIsuzu);
		    else if (avlArticulated.size() < 0 && avlArticulatedToyota.size() > 0)
			avlArticulated.addAll(avlArticulatedToyota);
		    else if (avlArticulated.size() < 0 && avlArticulatedToyota.size() < 0 && avlArticulatedIsuzu.size() > 0)
			avlArticulated.addAll(avlArticulatedIsuzu);
		    logger.info("avlSingle " + avlSingle);
		    if (avlSingle.size() > 0) {
			for (Resource r : avlSingle) {
			    logger.info("res: " + r.getId());
			    w.append(r.getId() + ", ");
			    /*

			    StateValue<?> currentTruckLoc = r.getStateVariableBy("Location").getValueAt(indEndTime);
			    logger.info("currentLoc:" + currentTruckLoc);
			    if (currentTruckLoc == null)
			    	continue;
			    String currentTruckLocation = currentTruckLoc.getValueAsString();
			    Resource currentTruckLocRes = RSPContainerHelper.getResourceMap(true).getEntryBy(currentTruckLocation);

			    if (currentTruckLocRes == null) {

			    	continue;
			    }
			    */
			    Resource pickUpDynamic = RSPContainerHelper.getResourceMap(true).getEntryBy(pickupLoc);
			    Double pickUpLat = (Double) pickUpDynamic.getParameterBy("lat").getValue();
			    Double pickUpLong = (Double) pickUpDynamic.getParameterBy("long").getValue();
			    String[] latLongValues = currLatLong.getParameterBy(r.getId()).getValueAsString().split(",");
			    currLat = Double.parseDouble(latLongValues[0]);
			    currLong = Double.parseDouble(latLongValues[0]);
			    String[] truckToPickupDuration = FindDistanceDuration.getDistanceDuration(currLat, currLong, pickUpLat, pickUpLong).split(",");
			    truckToPickupDur = Long.parseLong(truckToPickupDuration[1]);
			    if (truckToPickupDur < min) {
				min = truckToPickupDur;
				selectedTruck = r;
				logger.info(r);
				selectedDriver = r.getParameterBy("driverId").getValueAsString();
				logger.info(selectedDriver);
			    }
			}
			logger.info("selected truck: " + selectedTruck.getId());
		    } else if (avlSingle.size() < 1 && avlRigid.size() > 0) {
			for (Resource r : avlRigid) {
			    logger.info("res: " + r.getId());
			    w.append(r.getId() + ", ");
			    /*

			    StateValue<?> currentTruckLoc = r.getStateVariableBy("Location").getValueAt(indEndTime);
			    logger.info("currentLoc:" + currentTruckLoc);
			    if (currentTruckLoc == null)
			    	continue;
			    String currentTruckLocation = currentTruckLoc.getValueAsString();
			    Resource currentTruckLocRes = RSPContainerHelper.getResourceMap(true).getEntryBy(currentTruckLocation);

			    if (currentTruckLocRes == null) {

			    	continue;
			    }
			    */
			    Resource pickUpDynamic = RSPContainerHelper.getResourceMap(true).getEntryBy(pickupLoc);
			    Double pickUpLat = (Double) pickUpDynamic.getParameterBy("lat").getValue();
			    Double pickUpLong = (Double) pickUpDynamic.getParameterBy("long").getValue();
			    String[] latLongValues = currLatLong.getParameterBy(r.getId()).getValueAsString().split(",");
			    currLat = Double.parseDouble(latLongValues[0]);
			    currLong = Double.parseDouble(latLongValues[0]);
			    String[] truckToPickupDuration = FindDistanceDuration.getDistanceDuration(currLat, currLong, pickUpLat, pickUpLong).split(",");
			    truckToPickupDur = Long.parseLong(truckToPickupDuration[1]);
			    if (truckToPickupDur < min) {
				min = truckToPickupDur;
				selectedTruck = r;
				logger.info(r);
				selectedDriver = r.getParameterBy("driverId").getValueAsString();
				logger.info(selectedDriver);
			    }
			}
			logger.info("selected truck: " + selectedTruck.getId());
		    } else if (avlSingle.size() < 1 && avlRigid.size() < 1 && avlArticulated.size() > 0) {
			for (Resource r : avlArticulated) {
			    logger.info("res: " + r.getId());
			    w.append(r.getId() + ", ");
			    /*  StateValue<?> currentTruckLoc = r.getStateVariableBy("Location").getValueAt(indEndTime);
			      logger.info("currentLoc:" + currentTruckLoc);
			      if (currentTruckLoc == null)
			    continue;
			      String currentTruckLocation = currentTruckLoc.getValueAsString();
			      Resource currentTruckLocRes = RSPContainerHelper.getResourceMap(true).getEntryBy(currentTruckLocation);
			      if (currentTruckLocRes == null) {
			    continue;
			      }
			      truckToPickupDur = GigaUtils.getDuration(currentTruckLocation, pickupLoc);*/
			    Resource pickUpDynamic = RSPContainerHelper.getResourceMap(true).getEntryBy(pickupLoc);
			    Double pickUpLat = (Double) pickUpDynamic.getParameterBy("lat").getValue();
			    Double pickUpLong = (Double) pickUpDynamic.getParameterBy("long").getValue();
			    String[] latLongValues = currLatLong.getParameterBy(r.getId()).getValueAsString().split(",");
			    currLat = Double.parseDouble(latLongValues[0]);
			    currLong = Double.parseDouble(latLongValues[0]);
			    String[] truckToPickupDuration = FindDistanceDuration.getDistanceDuration(currLat, currLong, pickUpLat, pickUpLong).split(",");
			    truckToPickupDur = Long.parseLong(truckToPickupDuration[1]);
			    if (truckToPickupDur < min) {
				min = truckToPickupDur;
				selectedTruck = r;
				logger.info(r);
				selectedDriver = r.getParameterBy("driverId").getValueAsString();
				logger.info(selectedDriver);
			    }
			}
			logger.info("selected truck: " + selectedTruck.getId());
		    } else if (avlSingle.size() < 1 && avlRigid.size() < 1 && avlArticulated.size() < 1)
			break;
		    StringParameter evaluatedTrucks = (StringParameter) order.getParameterBy("evaluatedTrucks");
		    evaluatedTrucks.setValue(w.toString());
		    EarliestAvailableTime = selectedTruck.getStateVariableBy("Location").getValueAt(indEndTime).getTime();
		    if (EarliestAvailableTime < indStartTime) {
			EarliestAvailableTime = indStartTime;
		    }
		    if (EarliestAvailableTime < System.currentTimeMillis()) {
			EarliestAvailableTime = System.currentTimeMillis() + 2 * 60000L;
		    }
		    logger.info("EarliestAvailableTime_DLT:" + new Date(EarliestAvailableTime));
		    estimatedPickupTime = EarliestAvailableTime + truckToPickupDur;
		    logger.info("estimatedPickupTime->" + estimatedPickupTime);
		    estimatedDeliveryTime = estimatedPickupTime + GigaUtils.getDuration(pickupLoc, deliveryLoc) + 2 * buffer;
		    logger.info("estimatedDeliveryTime->" + estimatedDeliveryTime);
		    if (estimatedDeliveryTime > indEndTime || selectedTruck.getActivitiesInInterval(estimatedPickupTime, estimatedDeliveryTime).size() > 0) {
			continue;
		    }
		    if (avlSingle.contains(selectedTruck))
			avlSingle.remove(selectedTruck);
		    else if (avlRigid.contains(selectedTruck))
			avlRigid.remove(selectedTruck);
		    else if (avlArticulated.contains(selectedTruck))
			avlArticulated.remove(selectedTruck);
		    logger.info(selectedTruck + " removed");
		    logger.info("avlSingle " + avlSingle);
		    script.setValue("planIndividualOrders");
		    truckId.setValue(selectedTruck.getId());
		    selectedTruck.setPolicyMap(simplePlacement);
		    driverId.setValue(selectedDriver);
		    logger.info(driverId);
		    DurationParameter taskStartDate = new DurationParameter("TaskStart", "TaskStart", estimatedPickupTime);
		    order.addParameter(taskStartDate);
		    DurationParameter taskEndDate = new DurationParameter("TaskEnd", "TaskEnd", estimatedDeliveryTime);
		    order.addParameter(taskEndDate);
		    RSPTransaction tx1 = FeatherliteAgent.getTransaction(this);
		    PlanOrderCommand poc = new PlanOrderCommand();
		    poc.setOrder(order);
		    tx1.addCommand(poc);
		    tx1.commit();
		    selectedTruck.setPolicyMap(truckPlacement);
		    String pickupDate = DateFormat.format(new Date(estimatedPickupTime));
		    StringParameter estPickupTime = (StringParameter) order.getParameterBy("estPickupTime");
		    estPickupTime.setValue(pickupDate);
		    logger.info("estPickupTime :" + estPickupTime.getValueAsString());
		    String deliveryDate = DateFormat.format(new Date(estimatedDeliveryTime));
		    StringParameter estdeliveryTime = (StringParameter) order.getParameterBy("estDeliveryTime");
		    estdeliveryTime.setValue(deliveryDate);
		    logger.info("estDeliveryTime :" + estdeliveryTime.getValueAsString());
		    Integer estTravelTym = (int) ((estimatedDeliveryTime - EarliestAvailableTime) / 60000);
		    StringParameter estTravelTime = (StringParameter) order.getParameterBy("estTravelTime");
		    estTravelTime.setValue(Integer.toString(estTravelTym));
		    logger.info("estTravelTime :" + estTravelTime.getValueAsString());
		    String CurrTruckLoc = selectedTruck.getStateVariableBy("Location").getValueAt(indEndTime).getValueAsString();
		    StringParameter preceding_DM = (StringParameter) order.getParameterBy("preceding_DM");
		    Double currToPickDist;
		    Resource pickUpLocRes = RSPContainerHelper.getResourceMap(true).getEntryBy(pickupLoc);
		    Double pickupLat = (Double) pickUpLocRes.getParameterBy("lat").getValue();
		    Double pickupLong = (Double) pickUpLocRes.getParameterBy("long").getValue();
		    String[] latLongValues = currLatLong.getParameterBy(selectedTruck.getId()).getValueAsString().split(",");
		    Double currLati = Double.parseDouble(latLongValues[0]);
		    Double currLongi = Double.parseDouble(latLongValues[1]);
		    String[] currToPickDistance = FindDistanceDuration.getDistanceDuration(currLati, currLongi, pickupLat, pickupLong).split(",");
		    currToPickDist = Double.parseDouble(currToPickDistance[0]);
		    preceding_DM.setValue(Double.toString(currToPickDist));
		    //  preceding_DM.setValue(Double.toString(GigaUtils.getDistance(CurrTruckLoc, pickupLoc)));
		    String Base = selectedTruck.getParameterBy("location").getValueAsString();
		    StringParameter succeeding_DM = (StringParameter) order.getParameterBy("succeeding_DM");
		    succeeding_DM.setValue(Double.toString(GigaUtils.getDistance(deliveryLoc, Base)));
		    Integer travelDur = (int) (GigaUtils.getDuration(pickupLoc, deliveryLoc) / 60000);
		    StringParameter travel_duration = (StringParameter) order.getParameterBy("travel_Duration");
		    travel_duration.setValue(Integer.toString(travelDur));
		    StringParameter loadBuffer = (StringParameter) order.getParameterBy("loading_unloading_timeBuffer");
		    loadBuffer.setValue(Integer.toString(90));
		    StringParameter restWaitBuffer = (StringParameter) order.getParameterBy("rest_Waiting_timeBuffer");
		    restWaitBuffer.setValue(Integer.toString(0));
		    StringParameter baseLocStartTime = (StringParameter) order.getParameterBy("base_location_StartTime");
		    if (CurrTruckLoc.equalsIgnoreCase(Base)) {
			String baseStartDateText = DateFormat.format(new Date(EarliestAvailableTime));
			baseLocStartTime.setValue(baseStartDateText);
		    } else
			baseLocStartTime.setValue("");
		}
	    } catch (RSPException e) {
		e.printStackTrace();
	    }
	    logger.info("indLocalDlt PlanCompleted");
	    logger.info("indOutstationDLTOrders: " + indOutstationDLTOrders.size());
	    try {
		Resource selectedTruck_o = null;
		String selectedDriver_o = null;
		long truckToPickupDur_o = 0L;
		for (Order order : indOutstationDLTOrders) {
		    long min_o = Long.MAX_VALUE;
		    String pickupLoc = order.getParameterBy("pickupLocation").getValueAsString();
		    String deliveryLoc = order.getParameterBy("deliveryLocation").getValueAsString();
		    long EarliestAvailableTime = 0;
		    StringParameter script = (StringParameter) order.getParameterBy("script");
		    logger.info(script);
		    StringParameter truckId = (StringParameter) order.getParameterBy("truckId");
		    logger.info(truckId.getValueAsString());
		    StringParameter driverId = (StringParameter) order.getParameterBy("driverId");
		    long estimatedPickupTime = 0;
		    long estimatedDeliveryTime = 0;
		    StringWriter w = new StringWriter();
		    if (avlSingle.size() < 0 && avlSingleToyota.size() > 0)
			avlSingle.addAll(avlSingleToyota);
		    else if (avlSingle.size() < 0 && avlSingleToyota.size() < 0 && avlSingleIsuzu.size() > 0)
			avlSingle.addAll(avlSingleIsuzu);
		    else if (avlRigid.size() < 0 && avlRigidToyota.size() > 0)
			avlRigid.addAll(avlRigidToyota);
		    else if (avlRigid.size() < 0 && avlRigidToyota.size() < 0 && avlRigidIsuzu.size() > 0)
			avlRigid.addAll(avlRigidIsuzu);
		    else if (avlArticulated.size() < 0 && avlArticulatedToyota.size() > 0)
			avlArticulated.addAll(avlArticulatedToyota);
		    else if (avlArticulated.size() < 0 && avlArticulatedToyota.size() < 0 && avlArticulatedIsuzu.size() > 0)
			avlArticulated.addAll(avlArticulatedIsuzu);
		    logger.info("avlSingle " + avlSingle);
		    if (avlSingle.size() > 0) {
			for (Resource r : avlSingle) {
			    logger.info("res: " + r.getId());
			    w.append(r.getId() + ", ");
			    /* StateValue<?> currentTruckLocValue = r.getStateVariableBy("Location").getValueAt(indEndTime);
			     if (currentTruckLocValue == null)
			    continue;
			     String currentTruckLocation = currentTruckLocValue.getValueAsString();
			     Resource currentLocRes = RSPContainerHelper.getResourceMap(true).getEntryBy(currentTruckLocation);
			     if (currentLocRes == null)
			    continue;
			     
			     truckToPickupDur_o = GigaUtils.getDuration(currentTruckLocation, pickupLoc);
			     */
			    Resource pickUpDynamic = RSPContainerHelper.getResourceMap(true).getEntryBy(pickupLoc);
			    Double pickUpLat = (Double) pickUpDynamic.getParameterBy("lat").getValue();
			    Double pickUpLong = (Double) pickUpDynamic.getParameterBy("long").getValue();
			    String[] latLongValues = currLatLong.getParameterBy(r.getId()).getValueAsString().split(",");
			    currLat = Double.parseDouble(latLongValues[0]);
			    currLong = Double.parseDouble(latLongValues[0]);
			    String[] truckToPickupDuration = FindDistanceDuration.getDistanceDuration(currLat, currLong, pickUpLat, pickUpLong).split(",");
			    truckToPickupDur_o = Long.parseLong(truckToPickupDuration[1]);
			    if (truckToPickupDur_o < min_o) {
				min_o = truckToPickupDur_o;
				selectedTruck_o = r;
				logger.info(r);
				selectedDriver_o = r.getParameterBy("driverId").getValueAsString();
				logger.info(selectedDriver_o);
			    }
			}
			logger.info("selected truck: " + selectedTruck_o.getId());
		    } else if (avlSingle.size() < 1 && avlRigid.size() > 0) {
			for (Resource r : avlRigid) {
			    logger.info("res: " + r.getId());
			    w.append(r.getId() + ", ");
			    /*    StateValue<?> currentTruckLocValue = r.getStateVariableBy("Location").getValueAt(indEndTime);
			        if (currentTruckLocValue == null)
			    	continue;
			        String currentTruckLocation = currentTruckLocValue.getValueAsString();
			        Resource currentLocRes = RSPContainerHelper.getResourceMap(true).getEntryBy(currentTruckLocation);
			        if (currentLocRes == null)
			    	continue;
			        truckToPickupDur_o = GigaUtils.getDuration(currentTruckLocation, pickupLoc);
			        */
			    Resource pickUpDynamic = RSPContainerHelper.getResourceMap(true).getEntryBy(pickupLoc);
			    Double pickUpLat = (Double) pickUpDynamic.getParameterBy("lat").getValue();
			    Double pickUpLong = (Double) pickUpDynamic.getParameterBy("long").getValue();
			    String[] latLongValues = currLatLong.getParameterBy(r.getId()).getValueAsString().split(",");
			    currLat = Double.parseDouble(latLongValues[0]);
			    currLong = Double.parseDouble(latLongValues[0]);
			    String[] truckToPickupDuration = FindDistanceDuration.getDistanceDuration(currLat, currLong, pickUpLat, pickUpLong).split(",");
			    truckToPickupDur_o = Long.parseLong(truckToPickupDuration[1]);
			    if (truckToPickupDur_o < min_o) {
				min_o = truckToPickupDur_o;
				selectedTruck_o = r;
				logger.info(r);
				selectedDriver_o = r.getParameterBy("driverId").getValueAsString();
				logger.info(selectedDriver_o);
			    }
			}
			logger.info("selected truck: " + selectedTruck_o.getId());
		    } else if (avlSingle.size() < 1 && avlRigid.size() < 1 && avlArticulated.size() > 0) {
			for (Resource r : avlArticulated) {
			    logger.info("res: " + r.getId());
			    w.append(r.getId() + ", ");
			    /*    StateValue<?> currentTruckLocValue = r.getStateVariableBy("Location").getValueAt(indEndTime);
			        if (currentTruckLocValue == null)
			    	continue;
			        String currentTruckLocation = currentTruckLocValue.getValueAsString();
			        Resource currentLocRes = RSPContainerHelper.getResourceMap(true).getEntryBy(currentTruckLocation);
			        if (currentLocRes == null)
			    	continue;
			        truckToPickupDur_o = GigaUtils.getDuration(currentTruckLocation, pickupLoc);
			        */
			    Resource pickUpDynamic = RSPContainerHelper.getResourceMap(true).getEntryBy(pickupLoc);
			    Double pickUpLat = (Double) pickUpDynamic.getParameterBy("lat").getValue();
			    Double pickUpLong = (Double) pickUpDynamic.getParameterBy("long").getValue();
			    String[] latLongValues = currLatLong.getParameterBy(r.getId()).getValueAsString().split(",");
			    currLat = Double.parseDouble(latLongValues[0]);
			    currLong = Double.parseDouble(latLongValues[0]);
			    String[] truckToPickupDuration = FindDistanceDuration.getDistanceDuration(currLat, currLong, pickUpLat, pickUpLong).split(",");
			    truckToPickupDur_o = Long.parseLong(truckToPickupDuration[1]);
			    if (truckToPickupDur_o < min_o) {
				min_o = truckToPickupDur_o;
				selectedTruck_o = r;
				logger.info(r);
				selectedDriver_o = r.getParameterBy("driverId").getValueAsString();
				logger.info(selectedDriver_o);
			    }
			}
			logger.info("selected truck: " + selectedTruck_o.getId());
		    } else if (avlSingle.size() < 1 && avlRigid.size() < 1)
			break;
		    StringParameter evaluatedTrucks = (StringParameter) order.getParameterBy("evaluatedTrucks");
		    logger.info("evaluatedTrucksBef: " + evaluatedTrucks);
		    evaluatedTrucks.setValue(w.toString());
		    logger.info("evaluatedTrucksAft: " + evaluatedTrucks);
		    EarliestAvailableTime = selectedTruck_o.getStateVariableBy("Location").getValueAt(indEndTime).getTime();
		    if (EarliestAvailableTime < indStartTime) {
			EarliestAvailableTime = indStartTime;
		    }
		    if (EarliestAvailableTime < currentTime)
			EarliestAvailableTime = currentTime + 2 * 60000L;
		    logger.info("EarliestAvailableTime_DLT:" + new Date(EarliestAvailableTime));
		    estimatedPickupTime = EarliestAvailableTime + truckToPickupDur_o;
		    logger.info("estimatedPickupTime->" + estimatedPickupTime);
		    estimatedDeliveryTime = estimatedPickupTime + GigaUtils.getDuration(pickupLoc, deliveryLoc) + 2 * buffer;
		    logger.info("estimatedDeliveryTime->" + estimatedDeliveryTime);
		    if (estimatedDeliveryTime > indEndTime || selectedTruck_o.getActivitiesInInterval(estimatedPickupTime, estimatedDeliveryTime).size() > 0) {
			continue;
		    }
		    if (avlSingle.contains(selectedTruck_o))
			avlSingle.remove(selectedTruck_o);
		    else if (avlRigid.contains(selectedTruck_o))
			avlRigid.remove(selectedTruck_o);
		    else if (avlArticulated.contains(selectedTruck_o))
			avlArticulated.remove(selectedTruck_o);
		    logger.info(selectedTruck_o + " removed");
		    logger.info("avlSingle " + avlSingle);
		    script.setValue("planIndividualOrders");
		    truckId.setValue(selectedTruck_o.getId());
		    selectedTruck_o.setPolicyMap(simplePlacement);
		    driverId.setValue(selectedDriver_o);
		    logger.info(driverId);
		    DurationParameter taskStartDate = new DurationParameter("TaskStart", "TaskStart", estimatedPickupTime);
		    order.addParameter(taskStartDate);
		    DurationParameter taskEndDate = new DurationParameter("TaskEnd", "TaskEnd", estimatedDeliveryTime);
		    order.addParameter(taskEndDate);
		    RSPTransaction tx1 = FeatherliteAgent.getTransaction(this);
		    PlanOrderCommand poc = new PlanOrderCommand();
		    poc.setOrder(order);
		    tx1.addCommand(poc);
		    tx1.commit();
		    String pickupDate = DateFormat.format(new Date(estimatedPickupTime));
		    StringParameter estPickupTime = (StringParameter) order.getParameterBy("estPickupTime");
		    estPickupTime.setValue(pickupDate);
		    logger.info("estPickupTime :" + estPickupTime.getValueAsString());
		    String deliveryDate = DateFormat.format(new Date(estimatedDeliveryTime));
		    StringParameter estdeliveryTime = (StringParameter) order.getParameterBy("estDeliveryTime");
		    estdeliveryTime.setValue(deliveryDate);
		    logger.info("estDeliveryTime :" + estdeliveryTime.getValueAsString());
		    Integer estTravelTym = (int) ((estimatedDeliveryTime - EarliestAvailableTime) / 60000);
		    StringParameter estTravelTime = (StringParameter) order.getParameterBy("estTravelTime");
		    estTravelTime.setValue(Integer.toString(estTravelTym));
		    logger.info("estTravelTime :" + estTravelTime.getValueAsString());
		    String CurrTruckLoc = selectedTruck_o.getStateVariableBy("Location").getValueAt(indEndTime).getValueAsString();
		    StringParameter preceding_DM = (StringParameter) order.getParameterBy("preceding_DM");
		    Double currToPickDist;
		    Resource pickUpLocRes = RSPContainerHelper.getResourceMap(true).getEntryBy(pickupLoc);
		    Double pickupLat = (Double) pickUpLocRes.getParameterBy("lat").getValue();
		    Double pickupLong = (Double) pickUpLocRes.getParameterBy("long").getValue();
		    String[] latLongValues = currLatLong.getParameterBy(selectedTruck_o.getId()).getValueAsString().split(",");
		    Double currLati = Double.parseDouble(latLongValues[0]);
		    Double currLongi = Double.parseDouble(latLongValues[1]);
		    String[] currToPickDistance = FindDistanceDuration.getDistanceDuration(currLati, currLongi, pickupLat, pickupLong).split(",");
		    currToPickDist = Double.parseDouble(currToPickDistance[0]);
		    preceding_DM.setValue(Double.toString(currToPickDist));
		    // preceding_DM.setValue(Double.toString(GigaUtils.getDistance(CurrTruckLoc, pickupLoc)));
		    String Base = selectedTruck_o.getParameterBy("location").getValueAsString();
		    StringParameter succeeding_DM = (StringParameter) order.getParameterBy("succeeding_DM");
		    succeeding_DM.setValue(Double.toString(GigaUtils.getDistance(deliveryLoc, Base)));
		    Integer travelDur = (int) (GigaUtils.getDuration(pickupLoc, deliveryLoc) / 60000);
		    StringParameter travel_duration = (StringParameter) order.getParameterBy("travel_Duration");
		    travel_duration.setValue(Integer.toString(travelDur));
		    StringParameter loadBuffer = (StringParameter) order.getParameterBy("loading_unloading_timeBuffer");
		    loadBuffer.setValue(Integer.toString(90));
		    StringParameter restWaitBuffer = (StringParameter) order.getParameterBy("rest_Waiting_timeBuffer");
		    restWaitBuffer.setValue(Integer.toString(0));
		    StringParameter baseLocStartTime = (StringParameter) order.getParameterBy("base_location_StartTime");
		    if (CurrTruckLoc.equalsIgnoreCase(Base)) {
			String baseStartDateText = DateFormat.format(new Date(EarliestAvailableTime));
			baseLocStartTime.setValue(baseStartDateText);
		    } else
			baseLocStartTime.setValue("");
		}
		logger.info("indOutstationDlt PlanCompleted");
	    } catch (RSPException e) {
		e.printStackTrace();
	    }
	} catch (Exception e) {
	    result = FALSE;
	    e.printStackTrace();
	    result = e.getMessage();
	}
	return result;
    }
}
