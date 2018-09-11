package com.ramco.giga.policy;

import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import com.rsp.core.base.RSPContainerHelper;
import com.rsp.core.base.exception.DataModelException;
import com.rsp.core.base.exception.RSPException;
import com.rsp.core.base.model.Activity;
import com.rsp.core.base.model.IPolicyContainer;
import com.rsp.core.base.model.Objectives;
import com.rsp.core.base.model.Order;
import com.rsp.core.base.model.ParameterizedElement;
import com.rsp.core.base.model.Resource;
import com.rsp.core.base.model.Task;
import com.rsp.core.base.model.TimeInterval;
import com.rsp.core.base.model.WorkCalendar;
import com.rsp.core.base.model.constants.TimeDirection;
import com.rsp.core.base.model.parameter.DateParameter;
import com.rsp.core.base.model.parameter.FloatParameter;
import com.rsp.core.base.model.parameter.StringParameter;
import com.rsp.core.base.model.stateVariable.FloatState;
import com.rsp.core.base.model.stateVariable.StringState;
import com.rsp.core.i18n.RSPMessages;
import com.rsp.core.planning.policy.CalendarPolicy;
import com.rsp.core.planning.policy.PlacementResult;
import com.rsp.core.planning.policy.placement.CapacityPlacement;
import com.rsp.core.planning.policy.placement.DefaultPlacementResult;

public class MotorBikesTrucksPlacement extends CapacityPlacement{
	
	private static final long serialVersionUID = 1L;
	
    private static String CAPACITYSTATE_KEY = "Capacity";
	
	private static String LOCATION_KEY = "Location";

	private static String QUANTITY_KEY = "quantity";

	private static String DURATION_KEY = "duration";

	protected FloatState Capacity;
	protected StringState Location;
	protected StringState Make;

	protected double quantity;
	protected long duration;
	protected String locationVal;
	//protected String makeID;
	
	
	protected double capacityLowerBound;
	protected double capacityUpperBound;

	
	public void evaluateObjectives(Task task) {

		try {

			quantity = getFloatObjective(task, QUANTITY_KEY);
			duration = getDurationObjective(task, DURATION_KEY);
			
		} catch (DataModelException e) {

			DataModelException dme = new DataModelException(
					"Error in placement policy $policyName while reading objectives of task $taskId",
					"datamodel.objectivesRead", e);
			dme.addProperty("policyName", this.getName());
			dme.addProperty("taskId", task == null ? "null" : task.getId());
			throw dme;
		}
	}

	public void evaluateBounds(Resource resource) {
		
		capacityLowerBound = getFloatState(resource, CAPACITYSTATE_KEY).getLowerBound();
		capacityUpperBound = getFloatState(resource, CAPACITYSTATE_KEY).getUpperBound();
		// nothing to do
	}

	@Override
	public PlacementResult place(Task task, Resource resource, TimeInterval horizon, CalendarPolicy calendarPolicy,
			WorkCalendar workCalendar) {
		
		String direction = getStringObjective(task, TimeDirection.TIMEDIRECTION_KEY);
		return place(task, resource, horizon, direction, calendarPolicy, workCalendar);
	}

	public PlacementResult place(Task task, Resource resource, TimeInterval horizon, String direction,
			CalendarPolicy calendarPolicy, WorkCalendar workCalendar) {

		evaluateObjectives(task);
		evaluateBounds(resource);

		/*String driverRes = resource.getParameterBy("driver").getValueAsString();
		logger.info("truckDriver: "+driverRes);*/
		
		PlacementResult placementResult = null;
		long start;
		long end;
		long estimatedPickupTime;
		long estimatedDeliveryTime;
		
		
		ParameterizedElement tmp = task;
		String orderNetId = null;
		while (tmp != null) {
			orderNetId = tmp.getId();
			tmp = tmp.getParent();
		}
		
	   Order order = RSPContainerHelper.getOrderMap(true).getEntryBy(orderNetId);
	   StringParameter remarks = (StringParameter) order.getParameterBy("remarks");
 

	  if (order.getType().equalsIgnoreCase("Maintenance") || order.getType().equalsIgnoreCase("PlannedLeave")){
		   
		  start = horizon.getLower();
		  logger.info("maintenance order upload");
		   placementResult = new DefaultPlacementResult(PlacementResult.SUCCESS);
			placementResult.setStart(start);
			placementResult.setEnd(start+duration);
			placementResult.setQuantity(quantity);
			placementResult.setResource(resource);
			
			logger.info("maintenance order planned");
		   
			return placementResult;
		   
	   }
	  
	   
	   else{ 
		   
	   String pickupLocation = order.getParameterBy("pickupLocation").getValueAsString();

	   String deliveryLocation = order.getParameterBy("deliveryLocation").getValueAsString();
	
	   long tripDuration = getDuration(pickupLocation,deliveryLocation);
	   
	   double Drops =  (Double) (order.getParameterBy("noOfDrops").getValue());
	   
	   double noOfDrops = Drops - 1;
	   
	   long buffer = (long) 2.7e6;
	   
	   long inTransitTravelTime = (long) 9e5;

	   long OrderDate = (Long) order.getParameterBy("pickupDate").getValue();;
        /*if(task.hasParameter("start")){*/
	
		
		Date date = new Date(OrderDate);
		SimpleDateFormat df2 = new SimpleDateFormat("MM/dd/yyyy");
	    String dateText = df2.format(date);
	    StringWriter w = new StringWriter();
		w.append(dateText);
		w.append(" 08:00:00");
		
		StringWriter w1 = new StringWriter();
	    w1.append(dateText);
	    w1.append(" 22:00:00");
	    
	    Date nextDaydate = new Date(OrderDate + 86400000L);
	    logger.info("nextDaydate->"+nextDaydate);
	    String nextDaydateText = df2.format(nextDaydate);
	    StringWriter w2 = new StringWriter();
	    w2.append(nextDaydateText);
	    w2.append(" 08:45:00");
	    
		SimpleDateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
		
		try {
		
		Date startofTheDay = (Date)df.parse(w.toString());
		logger.info("startofTheDay"+startofTheDay);
		
		long startTimeOfTheDay = startofTheDay.getTime();
		
		Date latestDeliveryDate = (Date)df.parse(w1.toString());
		
		long latestDeliveryTime = latestDeliveryDate.getTime();
		logger.info("latestDeliveryDate"+latestDeliveryDate);
		
		Date nextDayStartDate =  (Date)df.parse(w2.toString());
		long nextDayStartTime = nextDayStartDate.getTime();
		logger.info("latestDeliveryDate"+latestDeliveryDate);
		
		String CurrLoc = resource.getStateVariableBy("Location").getValueAt(latestDeliveryTime).getValueAsString();
	    logger.info("CurrentLoc:"+CurrLoc);
	    
	    long earliestAvailTime = resource.getStateVariableBy("Location").getValueAt(latestDeliveryTime).getTime();
	   
	    if(earliestAvailTime > startTimeOfTheDay)
	    	start = earliestAvailTime;
	    else 
	    	start = startTimeOfTheDay;
	    
	    logger.info("start->"+new Date(start));
	    
	    long currToPickupDur = getDuration(CurrLoc,pickupLocation);
	    
	    
	    estimatedPickupTime = start + currToPickupDur;
		long deliveryTime1 = estimatedPickupTime + buffer + tripDuration;
		end = deliveryTime1;
		
		long estimatedEnd = (long) (end + noOfDrops*(inTransitTravelTime  + buffer));
		
		logger.info("estimatedEnd->"+new Date(estimatedEnd));
		
		int a = 0;
        for (int i = 0; (i < noOfDrops) ; i++){
        	
			end = end + inTransitTravelTime  + buffer;
			
			if(end <= latestDeliveryTime)
				a++;
			else if (end > latestDeliveryTime)
				break;
		}
        logger.info("a->"+a);
        
        if(estimatedEnd > latestDeliveryTime){
        	logger.info("estimatedEnd is greater than latestDeliveryTime");
        	end = (long) (nextDayStartTime + (noOfDrops-a)*(inTransitTravelTime  + buffer));
        }
        
        logger.info("end->"+new Date(end));
        
			
	    FloatState capacityState = (FloatState) resource.getStateVariableBy(CAPACITYSTATE_KEY);
	    double startCapacity = capacityState.getValueAt(start).getValue();
		double endCapacity = capacityState.getValueAt(end).getValue();
		double actualCapacity = capacityState.getActualState().getValue();

		Objectives we = task.getObjectives();
		FloatParameter fp = (FloatParameter) we.getParameterBy("quantity");
		double taskQty = fp.getValue();
		
		String truckType = resource.getParameterBy("truckType").getValueAsString();
		String orderedType  = order.getParameterBy("truckType").getValueAsString();
		
	/*	List<Activity> resPrevActivities = resource.getActivitiesInInterval(startTimeoftheDay, start);
		
		
		if (resPrevActivities.size() > 1){
			makeID = resource.getStateVariableBy("Make").getValueAt(start).getValueAsString();
			String orderedMake = order.getParameterBy("Make").getValueAsString(); 
		
		if(!orderedMake.equalsIgnoreCase("makeID"))
			logger.info("Order Make is not same as the truck Make.");
		    return new DefaultPlacementResult(PlacementResult.FAILURE, "Order Make is not same as the truck Make.");
		}*/
		
		if(!truckType.equalsIgnoreCase(orderedType))
		{
			logger.info("Truck Type is not same as orderedType.");
			placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "Truck Type is not same as orderedType.");
		}
		
		else if(resource.getActivitiesInInterval(start, end).size() > 0)
		{
			logger.info("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
			placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
		}
		
		/*if(start < System.currentTimeMillis()) {
			logger.info("The Expected pickup time can not be fulfilled since the current time crossed over.");
			return new DefaultPlacementResult(PlacementResult.FAILURE, "The Expected pickup time can not be fulfilled since the current time crossed over.");
		}*/
		 
		else if(startCapacity + taskQty < capacityLowerBound || startCapacity +taskQty > capacityUpperBound) {
			logger.info("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
			placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
		}
		
		else if(startCapacity > capacityLowerBound || endCapacity > capacityLowerBound){
			logger.info("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
			placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
		}
		
		else if(actualCapacity < 0.0 || actualCapacity > 1.0 ) {
			logger.info("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
			placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
		}
		
		else{
			placementResult = new DefaultPlacementResult(PlacementResult.SUCCESS);
			placementResult.setStart(start);
			placementResult.setEnd(end);
			placementResult.setQuantity(quantity);
			placementResult.setResource(resource);
			
		}
		
		}
		catch(ParseException e){
		}
		
		 if(placementResult.getStatus() == PlacementResult.FAILURE){
		    	
		    	String statusMsg = placementResult.getMessage();
		    	remarks.setValue(statusMsg);
		    	logger.info("remarks: "+remarks.getValueAsString());
		    }
		
	   }
	
			logger.info("PlacementResult: "+placementResult);
			return placementResult;
		
		
	}
	  
	@Override
	public void updateStateVariables(Task task, Resource resource, PlacementResult placementResult) {
		
		
		Capacity = getFloatState(resource, CAPACITYSTATE_KEY);
		Location = getStringState(resource,LOCATION_KEY);
		ParameterizedElement tmp = task;
		String orderNetId = null;
		while (tmp != null) {
			orderNetId = tmp.getId();
			tmp = tmp.getParent();
		}
		
	   Order order = RSPContainerHelper.getOrderMap(true).getEntryBy(orderNetId);
	  
		double quantity = task.getQuantity();
		long start = placementResult.getStart();
		long end = placementResult.getEnd();
		long buffer = (long) 2.7e6;
		long inTransitTravelTime = (long) 9e5;
		
		
		 if ((order.getType().equalsIgnoreCase("Maintenance")) || (order.getType().equalsIgnoreCase("PlannedLeave"))){
			  
			  start = task.getStart();
			  end = start + duration;
			 
			 createAndAddFloatChange(task, start, Capacity, quantity );
			 createAndAddFloatChange(task, end, Capacity, -quantity);
		   }
		 else{
		
			    
			   String pickupLocation = order.getParameterBy("pickupLocation").getValueAsString();
			   String deliveryLocation = order.getParameterBy("deliveryLocation").getValueAsString();
			   long tripDuration = getDuration(pickupLocation,deliveryLocation);
			   
			   double Drops = (Double) order.getParameterBy("noOfDrops").getValue();
			   double noOfDrops = Drops -1;
			   
			   String CurrLoc = resource.getStateVariableBy("Location").getValueAt(end).getValueAsString();
			   long currToPickupDur = getDuration(CurrLoc,pickupLocation);
				
			   long estimatedPickupTime  = start + currToPickupDur;
			   logger.info("estimatedPickupTime->"+new Date(estimatedPickupTime));
			  
						
					 createAndAddFloatChange(task, start, Capacity, quantity );
					 createAndAddFloatChange(task, end, Capacity, -quantity);
					 
					 createAndAddStringChange(task, start, Location, CurrLoc);
					 createAndAddStringChange(task, estimatedPickupTime, Location, pickupLocation);
					 createAndAddStringChange(task, estimatedPickupTime+buffer, Location, pickupLocation);
					 createAndAddStringChange(task, estimatedPickupTime+buffer+tripDuration, Location, deliveryLocation);
					 createAndAddStringChange(task, end - buffer, Location,deliveryLocation );
					 createAndAddStringChange(task, end, Location,deliveryLocation );
					 
					 /*createAndAddStringChange(task, start, Make, orderedMake);
					 if (quantityAtEnd == 0.0 ){
					 createAndAddStringChange(task, end, Make,"" );}*/
					 
		String orderedSkill = order.getParameterBy("truckType").getValueAsString();			
		String driverId = resource.getParameterBy("driverId").getValueAsString();
		logger.info("truckDriver: "+driverId);
		
		List<Resource> driverList = (List<Resource>) RSPContainerHelper.getResourceMap(true).getByType("driver_KKCT");
		List<Resource> availDrivers = new ArrayList<Resource>();
		
		for(Resource dr:driverList){
			
			String driverSkill = dr.getParameterBy("skill").getValueAsString();
			if(dr.getActivitiesInInterval(start, end).size() == 0 && driverSkill.equals(orderedSkill))
				availDrivers.add(dr);	
		}
		
		logger.info("availDrivers : "+availDrivers);
		
		/*String selectedDriver ;
		
		if(availDrivers.size()>0){
		
		if (availDrivers.contains(driverId))
			
			selectedDriver = driverId;
		
		else
			
			selectedDriver = availDrivers.get(0).getId();
		
		 }
		else throw  RSPMessages.newRspMsgEx("No Driver is available");*/
			
		
		
		
		
		StringParameter selectedTruck = (StringParameter)order.getParameterBy("truckId");
		selectedTruck.setValue(resource.getId());
		logger.info("selectedTruck->"+selectedTruck);
				
		StringParameter preferDriver = (StringParameter) order.getParameterBy("driverId");
		preferDriver.setValue(driverId);
		logger.info("orderDriver :"+preferDriver.getValueAsString());
		
	//	Resource driverRes = RSPContainerHelper.getResourceMap(true).getEntryBy(selectedDriver);
		
		
		SimpleDateFormat df2 = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
	    String pickupDateText = df2.format(new Date(estimatedPickupTime));
		
		StringParameter estPickupTime = (StringParameter) order.getParameterBy("estPickupTime");
		estPickupTime.setValue(pickupDateText);
		logger.info("estPickupTime :"+estPickupTime.getValueAsString());
		
		Date Enddate = new Date(end - buffer);
	    String endtdateText = df2.format(Enddate);
		
		StringParameter estDeliveryTime = (StringParameter) order.getParameterBy("estDeliveryTime");
		estDeliveryTime.setValue(endtdateText);
		logger.info("estDeliveryTime :"+estDeliveryTime.getValueAsString());
		
		long travelTime = end - start;
	    double travelTimeT = (double)travelTime/60000L;
	    String travelTimeText = Double.toString(travelTimeT);
	    
		StringParameter estTravelTime = (StringParameter) order.getParameterBy("estTravelTime");
		estTravelTime.setValue(travelTimeText);
		logger.info("estTravelTime :"+estTravelTime.getValueAsString());
		
		double precedingDm = getDistance(CurrLoc,pickupLocation);
        StringParameter preceding_DM = (StringParameter)order.getParameterBy("preceding_DM");
        preceding_DM.setValue(Double.toString(precedingDm));
        logger.info("preceding_DM :"+preceding_DM.getValueAsString());
        
        String base = resource.getParameterBy("location").getValueAsString();
        double succeedingDm = getDistance(deliveryLocation,base);
        
        
        StringParameter succeeding_DM = (StringParameter)order.getParameterBy("succeeding_DM");
        succeeding_DM.setValue(Double.toString(succeedingDm));
        logger.info("succeeding_DM :"+succeeding_DM.getValueAsString());
        
        long totalTravelDuration = (long) (tripDuration + noOfDrops*(inTransitTravelTime));
        Integer travelDur = (int) (totalTravelDuration  / 60000);
        StringParameter travel_duration = (StringParameter)order.getParameterBy("travel_Duration");
        travel_duration.setValue(Integer.toString(travelDur));
        logger.info("travel_duration :"+travel_duration.getValueAsString());
        
        double bufferTime = 45 + noOfDrops*45;
        String b = Double.toString(bufferTime);
        StringParameter loadBuffer = (StringParameter)order.getParameterBy("loading_unloading_timeBuffer");
        loadBuffer.setValue(b);
        logger.info("loadBuffer :"+loadBuffer.getValueAsString());
        
       // Integer restDur = (int) (restHr / 60000);
        StringParameter restWaitBuffer = (StringParameter)order.getParameterBy("rest_Waiting_timeBuffer");
        restWaitBuffer.setValue(Integer.toString(0));
        logger.info("restWaitBuffer :"+restWaitBuffer.getValueAsString());
        
        String baseStartDateText = df2.format(new Date(start));
        StringParameter baseLocStartTime = (StringParameter)order.getParameterBy("base_location_StartTime");
        baseLocStartTime.setValue(baseStartDateText);
        logger.info("baseLocStartTime :"+baseLocStartTime.getValueAsString());
        
		 }
	}
	
	@Override
	public void validate(IPolicyContainer policyContainer) {

		super.validate(policyContainer);

		// check if the required parameter exist
		Resource re = (Resource) policyContainer;
		evaluateBounds(re);

	}
	
private long getDuration(String placeFrom , String placeTo){
		
		long result = 0L;
		Resource resFrom = RSPContainerHelper.getResourceMap(true).getEntryBy(placeFrom);
		String[] MatrixValues = resFrom.getParameterBy(placeTo).getValueAsString().split(",");
		logger.info("MatrixValues[1]-->"+MatrixValues[1] + "::placeFrom::"+placeFrom+ "::placeTo-->"+ placeTo );
	    double duration = Double.parseDouble(MatrixValues[1]);
	    result = (long)duration * 1000L;
logger.info("result: "+duration);
		return result;
		
		
	}
	
private double getDistance(String placeFrom , String placeTo){
		
		double result = 0.0;
		Resource resFrom = RSPContainerHelper.getResourceMap(true).getEntryBy(placeFrom);
		String[] MatrixValues = resFrom.getParameterBy(placeTo).getValueAsString().split(",");
		logger.info("MatrixValues[0]-->"+MatrixValues[0] + "::placeFrom::"+placeFrom+ "::placeTo-->"+ placeTo );
	    double distance = Double.parseDouble(MatrixValues[0]);
	    result = distance;
logger.info("result: "+distance);
		return result;
		
		
	}

	
}
