package com.ramco.giga.policy;

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
import com.rsp.core.base.model.parameter.DateParameter;
import com.rsp.core.base.model.parameter.FloatParameter;
import com.rsp.core.base.model.parameter.Parameter;
import com.rsp.core.base.model.parameter.StringParameter;
import com.rsp.core.base.model.stateVariable.FloatState;
import com.rsp.core.base.model.stateVariable.StateValue;
import com.rsp.core.base.model.stateVariable.StateVariable;
import com.rsp.core.base.model.stateVariable.StringState;
import com.rsp.core.i18n.RSPMessages;
import com.rsp.core.planning.policy.CalendarPolicy;
import com.rsp.core.planning.policy.PlacementResult;
import com.rsp.core.planning.policy.placement.CapacityPlacement;
import com.rsp.core.planning.policy.placement.DefaultPlacementResult;
import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
//import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.DateUtil;

public class TruckDriverPlacement extends CapacityPlacement
{
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

  public void evaluateObjectives(Task task)
  {
    try
    {
      this.quantity = getFloatObjective(task, QUANTITY_KEY);
      this.duration = getDurationObjective(task, DURATION_KEY);
    }
    catch (DataModelException e)
    {
      DataModelException dme = new DataModelException(
        "Error in placement policy $policyName while reading objectives of task $taskId", 
        "datamodel.objectivesRead", e);
      dme.addProperty("policyName", getName());
      dme.addProperty("taskId", task == null ? "null" : task.getId());
      throw dme;
    }
  }

  public void evaluateBounds(Resource resource)
  {
    this.capacityLowerBound = ((Double)getFloatState(resource, CAPACITYSTATE_KEY).getLowerBound());
    this.capacityUpperBound = ((Double)getFloatState(resource, CAPACITYSTATE_KEY).getUpperBound());
  }

  public PlacementResult place(Task task, Resource resource, TimeInterval horizon, CalendarPolicy calendarPolicy, WorkCalendar workCalendar)
  {
    String direction = getStringObjective(task, "direction");
    return place(task, resource, horizon, direction, calendarPolicy, workCalendar);
  }

  public PlacementResult place(Task task, Resource resource, TimeInterval horizon, String direction, CalendarPolicy calendarPolicy, WorkCalendar workCalendar)
  {
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
logger.info("orderNetId: "+orderNetId);
    Order order = (Order)RSPContainerHelper.getOrderMap(true).getEntryBy(orderNetId);
    StringParameter remarks = (StringParameter) order.getParameterBy("remarks");
    StringParameter script = (StringParameter) order.getParameterBy("script");

    if ((order.getType().equalsIgnoreCase("Maintenance")) || (order.getType().equalsIgnoreCase("PlannedLeave")))
    {
       start = horizon.getLower();
      logger.info("maintenance order upload");
      placementResult = new DefaultPlacementResult(PlacementResult.SUCCESS);
      placementResult.setStart(start);
      placementResult.setEnd(start + this.duration);
      placementResult.setQuantity(this.quantity);
      placementResult.setResource(resource);

      logger.info("maintenance order planned");

      return placementResult;
    }
    
    else{
    	
    Resource pickupSlots = RSPContainerHelper.getResourceMap(true).getEntryBy("pickupSlots");
    Resource deliverySlots = RSPContainerHelper.getResourceMap(true).getEntryBy("deliverySlots");
    
    
    String customerType = order.getParameterBy("orderType").getValueAsString();

    String pickupLocation = order.getParameterBy("pickupLocation").getValueAsString();
    logger.info("pickupLocation"+ pickupLocation);
    
    String deliveryLocation = order.getParameterBy("deliveryLocation").getValueAsString();
    logger.info("deliveryLocation"+ pickupLocation);
    
    Resource pickupRes = (Resource)RSPContainerHelper.getResourceMap(true).getEntryBy(pickupLocation);
    String[] matrixValues = pickupRes.getParameterBy(deliveryLocation).getValueAsString().split(",");
    double tripDur = Double.parseDouble(matrixValues[1]);
    long tripDuration = (long) (tripDur * 1000L);
    long buffer = 2700000L;
    
    logger.info("tripDuration" + tripDur/3600);

 //   long pickupTime = ((DateParameter)task.getParameterBy("start")).getValue().longValue();
    long pickupTime = (Long) order.getParameterBy("pickupDate").getValue();
    logger.info("pickupOrdertime: "+order.getParameterBy("pickupDate").getValueAsString());
    long deliveryTime = ((DateParameter)task.getParameterBy("end")).getValue().longValue();
    
     end = deliveryTime;
     
     logger.info("orderEnd : "+new Date(end));
     
     Date pickupDate = new Date(pickupTime);
     SimpleDateFormat df2 = new SimpleDateFormat("MM/dd/yyyy");
     String pickupDateText = df2.format(pickupDate);
     String deliveryDateText = df2.format(end);
     
     StringWriter w1 = new StringWriter();
     w1.append(pickupDateText);
     
     logger.info("pickupDay"+pickupDateText);
     logger.info("currentTime: "+System.currentTimeMillis());
     w1.append(" 08:00:00");
     SimpleDateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
     
     logger.info("latestDeliverTime:"+deliverySlots.getParameterBy(customerType).getValueAsString());
     String latestDeliveryTime = deliverySlots.getParameterBy(customerType).getValueAsString().split(",")[1];
     
     StringWriter w2 = new StringWriter();
     w2.append(pickupDateText);
     w2.append(latestDeliveryTime);
     
     StringWriter s = new StringWriter();
     s.append(pickupDateText);
     s.append(" 22:00:00");
     
     
     
     
     try
     {
       Date startoftheDay = df.parse(w1.toString());

       logger.info("startoftheDay" + startoftheDay);

       long startTimeoftheDay = startoftheDay.getTime();
       
       Date endoftheDay = df.parse(s.toString());

       logger.info("startoftheDay" + startoftheDay);

       long endTimeoftheDay = endoftheDay.getTime();
       

     logger.info("truck: "+resource.getId());
    String CurrLoc = resource.getStateVariableBy("Location").getValueAt(end).getValueAsString();
    logger.info("truckCurrLoc: "+CurrLoc);
    
    long earliestAvailTime = resource.getStateVariableBy("Location").getValueAt(end).getTime();
    
    if(earliestAvailTime > startTimeoftheDay)
    	start = earliestAvailTime;
    else 
    	start = startTimeoftheDay;
    logger.info("EarliestStart:"+ new Date(start));
    
    
    Date latestDeliveryOfTheDay = df.parse(w2.toString());

    logger.info("latestDeliveryTimeOfTheDay" + latestDeliveryOfTheDay);

    long latestDeliveryTimeOfTheDay = latestDeliveryOfTheDay.getTime();
    
 //   lowerD = currLocTime;
    
    Resource CurrentLocRes = (Resource)RSPContainerHelper.getResourceMap(true).getEntryBy(CurrLoc);
    String[] CurrLocMatrixValues = CurrentLocRes.getParameterBy(pickupLocation).getValueAsString().split(",");
    double currToPickup = Double.parseDouble(CurrLocMatrixValues[1]);
    long currToPickupDur = (long)currToPickup * 1000L;
    logger.info("currToPickupDur: "+currToPickup/3600);

    
if(script.getValueAsString().equalsIgnoreCase("ReassignManually")){
	
	long earliestAvailtime = resource.getStateVariableBy("Location").getValueAt(latestDeliveryTimeOfTheDay).getTime();

	
    if(earliestAvailtime > startTimeoftheDay)
    	start = earliestAvailtime;
    else 
    	start = startTimeoftheDay;
//	start = earliestAvailtime;
	logger.info("start"+new Date(start));
	
	
	
	end = start + currToPickupDur + tripDuration + 2*buffer;
	logger.info("end"+new Date(end));
	
	long duration = end-start;
	
	List<Activity> ActivitiesAssigned = resource.getActivitiesInInterval(start, end) ;
	List<Activity> nextActivities = new ArrayList<Activity>();
	logger.info("ActivityAtEnd " + ActivitiesAssigned.size());
	
	
	long min = Long.MAX_VALUE;
	Order nextOrder = null ;
	long newEstPickupTime;
	
	
	for(Activity a: ActivitiesAssigned){
		
		if(a.getStart() > end){
			nextActivities.add(a); 
		}
		}
	logger.info("nextActivities "+nextActivities.size());
	for(Activity a1: nextActivities){
		
		if(a1.getStart() < min){
			min = a1.getStart();
			nextOrder = RSPContainerHelper.getOrderMap(true).getEntryBy(a1.getOrderNetId());
		}
	
	}
	logger.info("nextOrder: "+nextOrder);
	
	if(nextActivities.size() > 0 ){
	    String estPickupTime = nextOrder.getParameterBy("estPickupTime").getValueAsString();
	    String nextPickup = nextOrder.getParameterBy("pickupLocation").getValueAsString();
	    logger.info("estPickupTime: "+estPickupTime);
	    logger.info("nextPickup: "+nextPickup);
	    long estPickupTimeOfNextOrder = df.parse(estPickupTime).getTime();
		
	    
	    long deliveryToNextPickupDur = getDuration(deliveryLocation,nextPickup);
	    
	    newEstPickupTime = end + deliveryToNextPickupDur;
	    
	    long prevCurrLocToNextPickup = estPickupTimeOfNextOrder - earliestAvailtime;
	    
	    long difBWnewPickupTimeNold  = newEstPickupTime - estPickupTimeOfNextOrder;
	    logger.info("difBWnewPickupTimeNold"+difBWnewPickupTimeNold);
	    
	    if(start == startTimeoftheDay && difBWnewPickupTimeNold < 7.2e6){
	    	start = (long) (start - difBWnewPickupTimeNold);
	    	end = start + duration;
	    	newEstPickupTime  = start + currToPickupDur;
	    	logger.info("newEstimatedPickupTime"+new Date(newEstPickupTime));
	    }
	
	    if (newEstPickupTime > prevCurrLocToNextPickup){
	        logger.info("truck cannot fulfill the order as it won't be able to fulfill the next order");
	        placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
	 //       throw RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
	      }    
	}
	FloatState capacityState = (FloatState)resource.getStateVariableBy(CAPACITYSTATE_KEY);
    double startCapacity = ((Double)capacityState.getValueAt(start).getValue()).doubleValue();
    double endCapacity = ((Double)capacityState.getValueAt(end).getValue()).doubleValue();
    double actualCapacity = ((Double)capacityState.getActualState().getValue()).doubleValue();

    Objectives we = task.getObjectives();
    FloatParameter fp = (FloatParameter)we.getParameterBy("quantity");
    double taskQty = fp.getValue().doubleValue();
    
   // List<Activity> previousActs = resource.getActivitiesInInterval(startTimeoftheDay, start);
    long prevDur = 0L;
    for (Activity acts : ActivitiesAssigned) {
      long prevDuration = acts.getDuration();
      prevDur = prevDuration;
    }
    
/*    long difBWnewPickupTimeNold  = newEstPickupTime - estPickupTimeOfNextOrder;
    logger.info("difBWnewPickupTimeNold"+difBWnewPickupTimeNold);
    
    if(start == startTimeoftheDay && difBWnewPickupTimeNold < 7.2e6){
    	start = (long) (start - difBWnewPickupTimeNold);
    	end = start + duration;
    	newEstPickupTime  = start + currToPickupDur;
    	logger.info("newEstimatedPickupTime"+new Date(newEstPickupTime));
    }*/
    
	if (end > endTimeoftheDay)
	  {
        logger.info("Derived End time is greater than the required delivery time");
        placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The feasible trucks to fulfill this order cannot complete the trip within End time is greater than the required delivery time.");
//             throw RSPMessages.newRspMsgEx("Derived End time is greater than the required delivery time");
     } 
	
	/*else if (newEstPickupTime > prevCurrLocToNextPickup){
        logger.info("truck cannot fulfill the order as it won't be able to fulfill the next order");
        placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
 //       throw RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
      }*/
	
	else if ((startCapacity + taskQty < this.capacityLowerBound.doubleValue()) || (startCapacity + taskQty > this.capacityUpperBound.doubleValue())) {
        logger.info("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
        placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
//           throw RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
      }

	else  if ((startCapacity > this.capacityLowerBound.doubleValue()) || (endCapacity > this.capacityLowerBound.doubleValue())) {
        logger.info("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
        placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
//         throw RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
      }

	else  if ((actualCapacity < 0.0D) || (actualCapacity > 1.0D)) {
        logger.info("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
        placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
        throw RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
      }
      else if (prevDur + (end - start) > 43200000.0D) {
        logger.info("Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
        placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
//           throw RSPMessages.newRspMsgEx("Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
      }
  /*    else if (start < System.currentTimeMillis())
      {
        logger.info("Start time to fulfill this order is less than the current time");
        placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The Expected pickup time can not be fulfilled since the current time crossed over.");
//          throw RSPMessages.newRspMsgEx("Start time to fulfill this order is less than the current time");
      }*/
      
	else{
	
	   placementResult = new DefaultPlacementResult(PlacementResult.SUCCESS);
	   placementResult.setStart(start);
	   placementResult.setEnd(end);
	   placementResult.setQuantity(this.quantity);
	   placementResult.setResource(resource);

	      logger.info("maintenance order planned");

	   //   return placementResult;
	      }
	
}
else{
      if ((customerType.contains("Corporate_Outstation")|| (customerType.contains("Freight_Forwarding_Outstation"))))
      {
    	
        logger.info(customerType);
        StringWriter w3 = new StringWriter();
        w3.append(pickupDateText);
        w3.append(" 18:00:00");
        
        StringWriter w4 = new StringWriter();
        w4.append(deliveryDateText);
        w4.append(" 10:15:00");

        Date latestPickupDate = df.parse(w3.toString());
        
        long latestPickuptime = latestPickupDate.getTime();
        logger.info("latestPickuptime" + latestPickupDate);
        
        Date earliestDeliveryDate = df.parse(w4.toString());
        long earliestDeliverytime = earliestDeliveryDate.getTime();
        
        logger.info("earliestDeliverytime"+earliestDeliveryDate);
        
        if(end < earliestDeliverytime)
        	end = end + buffer;
        
        logger.info("end->"+new Date(end));

        long lowerD = end - tripDuration - currToPickupDur;
        
        if (lowerD + currToPickupDur < latestPickuptime) {
          logger.info("resource available");
          
          start = lowerD - 2*buffer;

          int localDeliveryCount = 0;

          List<Activity> previousActs = resource.getActivitiesInInterval(startTimeoftheDay, end);

          for (Activity activity : previousActs)
          {
            String PrevOrderID = activity.getOrderNetId();
            Order PrevOrder = (Order)RSPContainerHelper.getOrderMap(true).getEntryBy(PrevOrderID);
            String prevJobType = PrevOrder.getParameterBy("jobType").getValueAsString();
            
            if (prevJobType.equals("Local"))
              localDeliveryCount++;
          }
          logger.info("localDeliveryCount" + localDeliveryCount);
          List<Activity> availCheck = resource.getActivitiesInInterval(start, end);
          logger.info("activities for the whole day" + availCheck);

          FloatState  capacityState = (FloatState)resource.getStateVariableBy(CAPACITYSTATE_KEY);
          double startCapacity = ((Double)((FloatState)capacityState).getValueAt(start).getValue()).doubleValue();
          double endCapacity = ((Double)((FloatState)capacityState).getValueAt(end).getValue()).doubleValue();
          double actualCapacity = ((Double)((FloatState)capacityState).getActualState().getValue()).doubleValue();

          Objectives we = task.getObjectives();
          FloatParameter fp = (FloatParameter)we.getParameterBy("quantity");
          double taskQty = fp.getValue().doubleValue();

          if ((startCapacity + taskQty < this.capacityLowerBound.doubleValue()) || (startCapacity + taskQty > this.capacityUpperBound.doubleValue())) {
            logger.info("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
            placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
            
 //           throw RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
          }

          else if ((startCapacity > this.capacityLowerBound.doubleValue()) || (endCapacity > this.capacityLowerBound.doubleValue())) {
            logger.info("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
            
            placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
            
 //           throw RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
          }

          else if ((actualCapacity < 0.0D) || (actualCapacity > 1.0D)) {
            logger.info("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
            placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
//            throw RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
          }
          else if (availCheck.size() > 0) {
            logger.info("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
            placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "No truck is feasible to fulfill this order between "+new Date(start)+" and " +new Date(end));
  //          throw RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
          }
          else if ((previousActs.size() > 0) && (localDeliveryCount < 1))
          {
            logger.info("Truck has not completed any local orders ");
            placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "System is not able to find the truck which had done atleast a local order to fulfill this outstation trip .");
            
          }
          else if (start < System.currentTimeMillis())
          {
            logger.info("Start time to fulfill this order is less than the current time");
            placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The Expected pickup time can not be fulfilled since the current time crossed over.");
//            throw RSPMessages.newRspMsgEx("Start time to fulfill this order is less than the current time");
          }
          else
          
          placementResult = new DefaultPlacementResult(PlacementResult.SUCCESS);
          placementResult.setStart(start);
          placementResult.setEnd(end);
          placementResult.setQuantity(this.quantity);
          placementResult.setResource(resource);
          
          remarks.setValue("");

          logger.info("PlacementResult: " + placementResult);
 //         return placementResult;
        }
          
        
          else  if ((lowerD + currToPickupDur) > latestPickuptime)
        {
           start = latestPickuptime - currToPickupDur - buffer;

          int localDeliveryCount = 0;

          List<Activity> previousActs = resource.getActivitiesInInterval(startTimeoftheDay, end);

          logger.info("previousActs: "+previousActs.size());
          for (Activity activity : previousActs) {
    		  
    	      String PrevOrderID = activity.getOrderNetId();
    	      Order PrevOrder = RSPContainerHelper.getOrderMap(true).getEntryBy(PrevOrderID);
    	      String prevJobType = PrevOrder.getParameterBy("jobType").getValueAsString();
    	      if (prevJobType.equalsIgnoreCase("Local")) 
    	    	  localDeliveryCount++;
        }
          
    	  logger.info("localDeliveryCount"+localDeliveryCount);
    	  List<Activity> availCheck = resource.getActivitiesInInterval(start,end);
    	  logger.info("activities for the whole day"+availCheck);

          FloatState capacityState = (FloatState)resource.getStateVariableBy(CAPACITYSTATE_KEY);
          double startCapacity = ((Double)capacityState.getValueAt(start).getValue()).doubleValue();
          double endCapacity = ((Double)capacityState.getValueAt(end).getValue()).doubleValue();
          double actualCapacity = ((Double)capacityState.getActualState().getValue()).doubleValue();

          Objectives we = task.getObjectives();
          FloatParameter fp = (FloatParameter)we.getParameterBy("quantity");
          double taskQty = fp.getValue().doubleValue();

          if ((startCapacity + taskQty < this.capacityLowerBound.doubleValue()) || (startCapacity + taskQty > this.capacityUpperBound.doubleValue())) {
              logger.info("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
              placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
              
  //            throw RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
            }

            else if ((startCapacity > this.capacityLowerBound.doubleValue()) || (endCapacity > this.capacityLowerBound.doubleValue())) {
              logger.info("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
              
              placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
              
   //           throw RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
            }

            else if ((actualCapacity < 0.0D) || (actualCapacity > 1.0D)) {
              logger.info("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
              placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
 //             throw RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
            }
            else if (availCheck.size() > 0) {
              logger.info("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
              placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "No truck is feasible to fulfill this order between "+new Date(start)+" and " +new Date(end));
 //             throw RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
            }
          else if ((previousActs.size() > 0) && (localDeliveryCount < 1)) {
            logger.info("Truck has not completed any local orders ");
            placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "System is not able to find the truck which had done atleast a local order to fulfill this outstation trip .");
          }
          else if (start < System.currentTimeMillis())
          {
            logger.info("Start time to fulfill this order is less than the current time");
            placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The Expected pickup time can not be fulfilled since the current time crossed over.");
 //           throw RSPMessages.newRspMsgEx("Start time to fulfill this order is less than the current time");
          }
          else
          
          placementResult = new DefaultPlacementResult(PlacementResult.SUCCESS);
          placementResult.setStart(start);
          placementResult.setEnd(end);
          placementResult.setQuantity(this.quantity);
          placementResult.setResource(resource);
          
          remarks.setValue("");

          logger.info("PlacementResult: " + placementResult);
//          return placementResult;
        }

      }
      else
      {
    //	  List<Activity> prevActivities = resource.getStateVariableBy("Location").getLatestStart
    	logger.info("LocalOrders")  ;
   //    long  lowerD = end - tripDuration - currToPickupDur;

    //     start = lowerD;

        long totalTripDur = currToPickupDur + tripDuration;

        logger.info("totalTripDuration: "+totalTripDur);
        
        Date Startdate = new Date(start);
        logger.info("startTime:" + Startdate);
        logger.info("resource evaluated in placement" + resource);

        Date endDate = new Date(end);
        logger.info("endTime" + endDate);

        if (totalTripDur <= 7200000.0D)
        {
        	
        logger.info("totalTripDur is less than 2 hrs");
        
        logger.info("tripDuration" + tripDur/3600);
        logger.info("currToPickupDur: "+currToPickup/3600);
        
 //         long buffer = 2700000L;
          
          logger.info("STARTBefore:" + new Date(start));
          logger.info("EndBefore:" + new Date(end));
    //      start += buffer;
          
          end = start +  currToPickupDur + tripDuration + 2*buffer;
          
          logger.info("START:" + new Date(start));
          logger.info("END:" + new Date(end));
          

          List<Activity> previousActs = resource.getActivitiesInInterval(startTimeoftheDay, start);
          long prevDur = 0L;
          for (Activity acts : previousActs) {
            long prevDuration = acts.getDuration();
            prevDur = prevDuration;
          }

          FloatState capacityState = (FloatState)resource.getStateVariableBy(CAPACITYSTATE_KEY);
          double startCapacity = ((Double)capacityState.getValueAt(start).getValue()).doubleValue();
          double endCapacity = ((Double)capacityState.getValueAt(end).getValue()).doubleValue();
          double actualCapacity = ((Double)capacityState.getActualState().getValue()).doubleValue();

          Objectives we = task.getObjectives();
          FloatParameter fp = (FloatParameter)we.getParameterBy("quantity");
          double taskQty = fp.getValue().doubleValue();

          if ((startCapacity + taskQty < this.capacityLowerBound.doubleValue()) || (startCapacity + taskQty > this.capacityUpperBound.doubleValue())) {
            logger.info("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
            placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
 //           throw RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
          }
          
          else if(resource.getActivitiesInInterval(start, end).size() > 0)
  		{
  			logger.info("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
  			placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
  		}

          else  if ((startCapacity > this.capacityLowerBound.doubleValue()) || (endCapacity > this.capacityLowerBound.doubleValue())) {
            logger.info("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
            placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
   //         throw RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
          }

          else  if ((actualCapacity < 0.0D) || (actualCapacity > 1.0D)) {
            logger.info("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
            placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
            throw RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
          }
          else if (prevDur + (end - start) > 43200000.0D) {
            logger.info("Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
            placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
 //           throw RSPMessages.newRspMsgEx("Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
          }
          else if (start < System.currentTimeMillis())
          {
            logger.info("Start time to fulfill this order is less than the current time");
            placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The Expected pickup time can not be fulfilled since the current time crossed over.");
  //          throw RSPMessages.newRspMsgEx("Start time to fulfill this order is less than the current time");
          }
          else if (end > latestDeliveryTimeOfTheDay)
          {
              logger.info("Derived End time is greater than the required delivery time");
              placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The feasible trucks to fulfill this order cannot complete the trip within End time is greater than the required delivery time.");
   //           throw RSPMessages.newRspMsgEx("Derived End time is greater than the required delivery time");
           }  
          
          placementResult = new DefaultPlacementResult(PlacementResult.SUCCESS);
          placementResult.setStart(start);
          placementResult.setEnd(end);
          placementResult.setQuantity(this.quantity);
          placementResult.setResource(resource);
          
          remarks.setValue("");

          logger.info("PlacementResult: " + placementResult);
 //         return placementResult;
        }
        
        if ((totalTripDur > 7200000.0D) && (totalTripDur <= 14400000.0D))
        {
        	
        	logger.info("totalTripDur is less than 4 hrs and greater than 2 hrs");
            logger.info("tripDuration" + tripDur/3600);
            logger.info("currToPickupDur: "+currToPickup/3600);
          long restHr = 900000L;
  //        long buffer = 2700000L;
          
          logger.info("STARTBefore:" + new Date(start));
          logger.info("EndBefore:" + new Date(end));
    //      start += buffer;
          
          end = start +  currToPickupDur + tripDuration + restHr + 2*buffer;
          
          logger.info("START:" + new Date(start));
          logger.info("END:" + new Date(end));
          

          List<Activity> previousActs = resource.getActivitiesInInterval(startTimeoftheDay, start);
          long prevDur = 0L;
          for (Activity acts : previousActs) {
            long prevDuration = acts.getDuration();
            prevDur = prevDuration;
          }

          FloatState capacityState = (FloatState)resource.getStateVariableBy(CAPACITYSTATE_KEY);
          double startCapacity = ((Double)capacityState.getValueAt(start).getValue()).doubleValue();
          double endCapacity = ((Double)capacityState.getValueAt(end).getValue()).doubleValue();
          double actualCapacity = ((Double)capacityState.getActualState().getValue()).doubleValue();

          Objectives we = task.getObjectives();
          FloatParameter fp = (FloatParameter)we.getParameterBy("quantity");
          double taskQty = fp.getValue().doubleValue();

          if ((startCapacity + taskQty < this.capacityLowerBound.doubleValue()) || (startCapacity + taskQty > this.capacityUpperBound.doubleValue())) {
            logger.info("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
            placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
 //           throw RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
          }

          if ((startCapacity > this.capacityLowerBound.doubleValue()) || (endCapacity > this.capacityLowerBound.doubleValue())) {
            logger.info("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
            placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
//            throw RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
          }

          if ((actualCapacity < 0.0D) || (actualCapacity > 1.0D)) {
            logger.info("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
            placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
 //           throw RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
          }
          else if (prevDur + (end - start) > 43200000.0D) {
            logger.info("Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
            placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
  //          throw RSPMessages.newRspMsgEx("Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
          }
          else if (start < System.currentTimeMillis())
          {
            logger.info("Start time to fulfill this order is less than the current time");
            placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The Expected pickup time can not be fulfilled since the current time crossed over.");
 //           throw RSPMessages.newRspMsgEx("Start time to fulfill this order is less than the current time");
          }
          
          else if (end > latestDeliveryTimeOfTheDay)
          {
              logger.info("Derived End time is greater than the required delivery time");
              placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The feasible trucks to fulfill this order cannot complete the trip within End time is greater than the required delivery time.");
 //             throw RSPMessages.newRspMsgEx("Derived End time is greater than the required delivery time");
           } 
          
          else
          
          placementResult = new DefaultPlacementResult(PlacementResult.SUCCESS);
          placementResult.setStart(start);
          placementResult.setEnd(end);
          placementResult.setQuantity(this.quantity);
          placementResult.setResource(resource);
          
          remarks.setValue("");

          logger.info("PlacementResult: " + placementResult);
//          return placementResult;
        }
        else if ((totalTripDur > 14400000.0D) && (totalTripDur <= 28800000.0D))
        {
        	logger.info("totalTripDur is less than 8 hrs and greater than 4 hrs");

            logger.info("tripDuration" + tripDur/3600);
            logger.info("currToPickupDur: "+currToPickup/3600);
          long restHr = 1800000L;
//          long buffer = 2700000L;
          
          logger.info("STARTBefore:" + new Date(start));
          logger.info("EndBefore:" + new Date(end));
    //      start += buffer;
          
          end = start +  currToPickupDur + tripDuration + restHr + 2*buffer;
          
          logger.info("START:" + new Date(start));
          logger.info("END:" + new Date(end));

          List<Activity> previousActs = resource.getActivitiesInInterval(startTimeoftheDay, start);
          long prevDur = 0L;
          for (Activity acts : previousActs) {
            long prevDuration = acts.getDuration();
            prevDur = prevDuration;
          }

          FloatState capacityState = (FloatState)resource.getStateVariableBy(CAPACITYSTATE_KEY);
          double startCapacity = ((Double)capacityState.getValueAt(start).getValue()).doubleValue();
          double endCapacity = ((Double)capacityState.getValueAt(end).getValue()).doubleValue();
          double actualCapacity = ((Double)capacityState.getActualState().getValue()).doubleValue();

          Objectives we = task.getObjectives();
          FloatParameter fp = (FloatParameter)we.getParameterBy("quantity");
          double taskQty = fp.getValue().doubleValue();

          if ((startCapacity + taskQty < this.capacityLowerBound.doubleValue()) || (startCapacity + taskQty > this.capacityUpperBound.doubleValue())) {
            logger.info("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
            placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
  //          throw RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
          }

          if ((startCapacity > this.capacityLowerBound.doubleValue()) || (endCapacity > this.capacityLowerBound.doubleValue())) {
            logger.info("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
            placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
  //          throw RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
          }

          if ((actualCapacity < 0.0D) || (actualCapacity > 1.0D)) {
            logger.info("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
            placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
  //          throw RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
          }
          else if (prevDur + (end - start) > 43200000.0D) {
            logger.info("Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
            placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
//            throw RSPMessages.newRspMsgEx("Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
          }
          else if (start < System.currentTimeMillis())
          {
            logger.info("Start time to fulfill this order is less than the current time");
            placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The Expected pickup time can not be fulfilled since the current time crossed over.");
 //           throw RSPMessages.newRspMsgEx("Start time to fulfill this order is less than the current time");
          }
          
          else if (end > latestDeliveryTimeOfTheDay)
          {
              logger.info("Derived End time is greater than the required delivery time");
              placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The feasible trucks to fulfill this order cannot complete the trip within End time is greater than the required delivery time.");
 //             throw RSPMessages.newRspMsgEx("Derived End time is greater than the required delivery time");
          } 
          else
          
          placementResult = new DefaultPlacementResult(PlacementResult.SUCCESS);
          placementResult.setStart(start);
          placementResult.setEnd(end);
          placementResult.setQuantity(this.quantity);
          placementResult.setResource(resource);
          
          remarks.setValue("");

          logger.info("PlacementResult: " + placementResult);
 //         return placementResult;
        }
        else if ((totalTripDur > 28800000.0D))
        {
        	logger.info("totalTripDur is greater than 8 hrs");
            logger.info("tripDuration" + tripDur/3600);
            logger.info("currToPickupDur: "+currToPickup/3600);
          long restHr1 = 1800000L;
          long restHr2 = 3600000L;
 //         long buffer = 2700000L;
          
          logger.info("STARTBefore:" + new Date(start));
          logger.info("EndBefore:" + new Date(end));
    //      start += buffer;
          
          end = start +  currToPickupDur + tripDuration + restHr1 + restHr2 + 2*buffer;
          
          logger.info("START:" + new Date(start));
          logger.info("END:" + new Date(end));


          List<Activity> previousActs = resource.getActivitiesInInterval(startTimeoftheDay, start);
          long prevDur = 0L;
          for (Activity acts : previousActs) {
            long prevDuration = acts.getDuration();
            prevDur = prevDuration;
          }

          FloatState capacityState = (FloatState)resource.getStateVariableBy(CAPACITYSTATE_KEY);
          double startCapacity = ((Double)capacityState.getValueAt(start).getValue()).doubleValue();
          double endCapacity = ((Double)capacityState.getValueAt(end).getValue()).doubleValue();
          double actualCapacity = ((Double)capacityState.getActualState().getValue()).doubleValue();

          Objectives we = task.getObjectives();
          FloatParameter fp = (FloatParameter)we.getParameterBy("quantity");
          double taskQty = fp.getValue().doubleValue();

          if ((startCapacity + taskQty < this.capacityLowerBound.doubleValue()) || (startCapacity + taskQty > this.capacityUpperBound.doubleValue())) {
            logger.info("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
            placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
  //          throw RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
          }

          if ((startCapacity > this.capacityLowerBound.doubleValue()) || (endCapacity > this.capacityLowerBound.doubleValue())) {
            logger.info("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
            placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
  //          throw RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
          }

          if ((actualCapacity < 0.0D) || (actualCapacity > 1.0D)) {
            logger.info("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
            placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
 //           throw RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new Date(start)+" and " +new Date(end));
          }
          else if (prevDur + (end - start) > 43200000.0D) {
            logger.info("Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
            placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
  //          throw RSPMessages.newRspMsgEx("Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
          }
          else if (start < System.currentTimeMillis())
          {
            logger.info("Start time to fulfill this order is less than the current time");
            placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The Expected pickup time can not be fulfilled since the current time crossed over.");
 //           throw RSPMessages.newRspMsgEx("Start time to fulfill this order is less than the current time");
          }
          
          else if (end > latestDeliveryTimeOfTheDay)
          {
              logger.info("Derived End time is greater than the required delivery time");
              placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "The feasible trucks to fulfill this order cannot complete the trip within End time is greater than the required delivery time.");
//              throw RSPMessages.newRspMsgEx("Derived End time is greater than the required delivery time");
          }
          
          else
          
          placementResult = new DefaultPlacementResult(PlacementResult.SUCCESS);
          placementResult.setStart(start);
          placementResult.setEnd(end);
          placementResult.setQuantity(this.quantity);
          placementResult.setResource(resource);
          
          remarks.setValue("");

          logger.info("PlacementResult: " + placementResult);
//          return placementResult;
        }
        
       

      }
    }
     }
    catch (ParseException e1)
    {
      e1.printStackTrace();
    }
     
     if(placementResult.getStatus() == PlacementResult.FAILURE){
     	
     	String statusMsg = placementResult.getMessage();
     	remarks.setValue(statusMsg);
     }
    }
    return placementResult;
  }

  public void updateStateVariables(Task task, Resource resource, PlacementResult placementResult)
  {
    this.Capacity = getFloatState(resource, CAPACITYSTATE_KEY);
    this.Location = getStringState(resource, LOCATION_KEY);
    ParameterizedElement tmp = task;
    String orderNetId = null;
    while (tmp != null) {
      orderNetId = tmp.getId();
      tmp = tmp.getParent();
    }

    Order order = (Order)RSPContainerHelper.getOrderMap(true).getEntryBy(orderNetId);

    logger.info("ordupdateStateVar: "+orderNetId);
    double quantity = task.getQuantity();
    long start = placementResult.getStart();
    logger.info("start:"+new Date(start));
    long end = placementResult.getEnd();
    logger.info("end:"+new Date(end));
    long buffer = 2700000L;

    String script = order.getParameterBy("script").getValueAsString(); 
    if ((order.getType().equalsIgnoreCase("Maintenance")) || (order.getType().equalsIgnoreCase("PlannedLeave")))
    {
      start = task.getStart();
      end = start + this.duration;

      createAndAddFloatChange(task, start, this.Capacity, quantity);
      createAndAddFloatChange(task, end, this.Capacity, -quantity);
    }
    else
    {
      String customerType = order.getParameterBy("orderType").getValueAsString();

      String pickupLocation = order.getParameterBy("pickupLocation").getValueAsString();
      String deliveryLocation = order.getParameterBy("deliveryLocation").getValueAsString();
      Resource pickupRes = (Resource)RSPContainerHelper.getResourceMap(true).getEntryBy(pickupLocation);
      Resource deliveryRes = (Resource)RSPContainerHelper.getResourceMap(true).getEntryBy(deliveryLocation);
      String[] matrixValues = pickupRes.getParameterBy(deliveryLocation).getValueAsString().split(",");
      double tripDur = Double.parseDouble(matrixValues[1]);
      
      String BaseLocation = resource.getParameterBy("location").getValueAsString();
      double deliveryToBasekm = Double.parseDouble(deliveryRes.getParameterBy(BaseLocation).getValueAsString().split(",")[0]);
      

      long tripDuration = (long)tripDur * 1000L;
      
      long restHr = 0L;

      long pickupDate = ((DateParameter)task.getParameterBy("start")).getValue().longValue();
      long deliveryDate = ((DateParameter)task.getParameterBy("end")).getValue().longValue();

      String CurrLoc = resource.getStateVariableBy("Location").getValueAt(deliveryDate).getValueAsString();
      Resource CurrentLocRes = (Resource)RSPContainerHelper.getResourceMap(true).getEntryBy(CurrLoc);
      String[] CurrLocMatrixValues = CurrentLocRes.getParameterBy(pickupLocation).getValueAsString().split(",");
      double currToPickupDist = Double.parseDouble(CurrLocMatrixValues[0]);
      double currToPickup = Double.parseDouble(CurrLocMatrixValues[1]);
      long currToPickupDur = (long)currToPickup * 1000L;
      
      
      if(script.equalsIgnoreCase("ReassignManually"))   {
    	  
    	  long estimatedPickupTime = start + currToPickupDur;
    	  
    	  createAndAddFloatChange(task, start, this.Capacity, quantity);
          createAndAddFloatChange(task, end, this.Capacity, -quantity);

          createAndAddStringChange(task, start, this.Location, CurrLoc);
          createAndAddStringChange(task, estimatedPickupTime, this.Location, pickupLocation);
          createAndAddStringChange(task, estimatedPickupTime + buffer, this.Location, pickupLocation);
          createAndAddStringChange(task, end - buffer, this.Location, deliveryLocation);
          createAndAddStringChange(task, end, this.Location, deliveryLocation);
          
          

          StringParameter selectedTruck = (StringParameter)order.getParameterBy("truckId");
          selectedTruck.setValue(resource.getId());

   
          String driverId = resource.getParameterBy("driverId").getValueAsString();
          
          StringParameter preferDriver = (StringParameter)order.getParameterBy("driverId");
          preferDriver.setValue(driverId);
          
          logger.info("driver "+preferDriver.getValueAsString());
          
          Date pickdate = new Date(estimatedPickupTime);
          SimpleDateFormat df2 = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
          String pickupDateText = df2.format(pickdate);

          StringParameter estPickupTime = (StringParameter)order.getParameterBy("estPickupTime");
          estPickupTime.setValue(pickupDateText);
          logger.info("estPickupTime :" + pickupDateText);

          Date Enddate = new Date(end);
          String endtdateText = df2.format(Enddate);

          StringParameter estDeliveryTime = (StringParameter)order.getParameterBy("estDeliveryTime");
          estDeliveryTime.setValue(endtdateText);
          logger.info("estDeliveryTime :" + endtdateText);

          long travelTime = end - start;
          
          Integer travelTimeT = (int) (travelTime / 60000);
          String travelTimeText = travelTimeT.toString(travelTimeT);
          
          logger.info("TravelTime: "+travelTimeT);

          StringParameter estTravelTime = (StringParameter)order.getParameterBy("estTravelTime");
          estTravelTime.setValue(travelTimeText);
         
          double precedingDm = currToPickupDist;
          StringParameter preceding_DM = (StringParameter)order.getParameterBy("preceding_DM");
          preceding_DM.setValue(Double.toString(precedingDm));
          
          StringParameter succeeding_DM = (StringParameter)order.getParameterBy("succeeding_DM");
          succeeding_DM.setValue(Double.toString(deliveryToBasekm));
          
          Integer travelDur = (int) (tripDuration / 60000);
          StringParameter travel_duration = (StringParameter)order.getParameterBy("travel_Duration");
          travel_duration.setValue(Integer.toString(travelDur));
          
          StringParameter loadBuffer = (StringParameter)order.getParameterBy("loading_unloading_timeBuffer");
          loadBuffer.setValue(Integer.toString(90));
          
          Integer restDur = (int) (restHr / 60000);
          StringParameter restWaitBuffer = (StringParameter)order.getParameterBy("rest_Waiting_timeBuffer");
          restWaitBuffer.setValue(Integer.toString(restDur));
          
          if(CurrLoc.equalsIgnoreCase(BaseLocation)){
          String baseStartDateText = df2.format(new Date(start));
          StringParameter baseLocStartTime = (StringParameter)order.getParameterBy("base_location_StartTime");
          baseLocStartTime.setValue(baseStartDateText);
          }
          
      }
      
      else{

        if ((customerType.contains("Corporate_Outstation")|| (customerType.contains("Freight_Forwarding_Outstation"))))
      {
        String IntermediateBase;
		long pickupToBase;
		long BaseToDelivery;
		
        String base1 = ((Resource)RSPContainerHelper.getResourceMap(true).getEntryBy("base_west")).getParameterBy("base1").getValueAsString();
        String[] matrixVal1 = pickupRes.getParameterBy(base1).getValueAsString().split(",");
        double pickupToBase1 = Double.parseDouble(matrixVal1[1]);
        long pickupToBase1Dur = (long)pickupToBase1 * 1000L;

        Resource BaseRes1 = (Resource)RSPContainerHelper.getResourceMap(true).getEntryBy(base1);
        String[] matVal1 = BaseRes1.getParameterBy(deliveryLocation).getValueAsString().split(",");
        double base1ToDel = Double.parseDouble(matVal1[1]);
        long base1ToDelDur = (long)base1ToDel * 1000L;

        long intermediate1 = pickupToBase1Dur + base1ToDelDur;

        String base2 = ((Resource)RSPContainerHelper.getResourceMap(true).getEntryBy("base_west")).getParameterBy("base2").getValueAsString();
        String[] matrixVal2 = pickupRes.getParameterBy(base1).getValueAsString().split(",");
        double pickupToBase2 = Double.parseDouble(matrixVal2[1]);
        long pickupToBase2Dur = (long)pickupToBase2 * 1000L;

        Resource BaseRes2 = (Resource)RSPContainerHelper.getResourceMap(true).getEntryBy(base2);
        String[] matVal2 = BaseRes2.getParameterBy(deliveryLocation).getValueAsString().split(",");
        double base2ToDel = Double.parseDouble(matVal2[1]);
        long base2ToDelDur = (long)base2ToDel * 1000L;

        long intermediate2 = pickupToBase2Dur + base2ToDelDur;

        long dif = Math.min(base1ToDelDur, base2ToDelDur);
        
        if (dif == base1ToDelDur) {
          IntermediateBase = base1;
          pickupToBase = pickupToBase1Dur;
          BaseToDelivery = base1ToDelDur;
        }
        else
        {
          IntermediateBase = base2;
          pickupToBase = pickupToBase2Dur;
          BaseToDelivery = base2ToDelDur;
        }

        Date Startdate = new Date(start);
        logger.info("startTime:" + Startdate);

        long estimatedPickupTime = start + currToPickupDur;

        createAndAddFloatChange(task, start, this.Capacity, quantity);
        createAndAddFloatChange(task, end, this.Capacity, -quantity);

        createAndAddStringChange(task, start, this.Location, CurrLoc);
        createAndAddStringChange(task, estimatedPickupTime, this.Location, pickupLocation);
        createAndAddStringChange(task, estimatedPickupTime + buffer, this.Location, pickupLocation);
        createAndAddStringChange(task, estimatedPickupTime + buffer + pickupToBase, this.Location, IntermediateBase);
        createAndAddStringChange(task, end - buffer - BaseToDelivery, this.Location, IntermediateBase);
        createAndAddStringChange(task, end - buffer, this.Location, deliveryLocation);
        createAndAddStringChange(task, end, this.Location, deliveryLocation);

        
        restHr  = (end - buffer - BaseToDelivery) - (estimatedPickupTime + buffer + pickupToBase);
        
        String driverRes = resource.getParameterBy("driverId").getValueAsString();
        logger.info("truckDriver: " + driverRes);

        StringParameter selectedTruck = (StringParameter)order.getParameterBy("truckId");
        selectedTruck.setValue(resource.getId());

        StringParameter preferDriver = (StringParameter)order.getParameterBy("driverId");
        preferDriver.setValue(driverRes);
        logger.info("orderDriver :" + preferDriver.getValueAsString());

        Date pickdate = new Date(estimatedPickupTime);
        SimpleDateFormat df2 = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        String pickupDateText = df2.format(pickdate);

        StringParameter estPickupTime = (StringParameter)order.getParameterBy("estPickupTime");
        estPickupTime.setValue(pickupDateText);
        logger.info("estPickupTime :" + estPickupTime.getValueAsString());

        Date Enddate = new Date(end);
        String endtdateText = df2.format(Enddate);

        StringParameter estDeliveryTime = (StringParameter)order.getParameterBy("estDeliveryTime");
        estDeliveryTime.setValue(endtdateText);
        logger.info("estDeliveryTime :" + estDeliveryTime.getValueAsString());

        long travelTime = end - start;
        Integer travelTimeT = (int) (travelTime / 60000);
        String travelTimeText = Integer.toString(travelTimeT);

        StringParameter estTravelTime = (StringParameter)order.getParameterBy("estTravelTime");
        estTravelTime.setValue(travelTimeText);
        
        double precedingDm = currToPickupDist;
        StringParameter preceding_DM = (StringParameter)order.getParameterBy("preceding_DM");
        preceding_DM.setValue(Double.toString(precedingDm));
        
        StringParameter succeeding_DM = (StringParameter)order.getParameterBy("succeeding_DM");
        succeeding_DM.setValue(Integer.toString(0));
        
        Integer travelDur = (int) (tripDuration / 60000);
        StringParameter travel_duration = (StringParameter)order.getParameterBy("travel_Duration");
        travel_duration.setValue(Integer.toString(travelDur));
        
        StringParameter loadBuffer = (StringParameter)order.getParameterBy("loading_unloading_timeBuffer");
        loadBuffer.setValue(Integer.toString(90));
        
        Integer restDur = (int) (restHr / 60000);
        StringParameter restWaitBuffer = (StringParameter)order.getParameterBy("rest_Waiting_timeBuffer");
        restWaitBuffer.setValue(Integer.toString(restDur));
        
        String baseStartDateText = df2.format(new Date(end - buffer - BaseToDelivery));
        StringParameter baseLocStartTime = (StringParameter)order.getParameterBy("base_location_StartTime");
        baseLocStartTime.setValue(baseStartDateText);
            
      }
      else
      {
        long estimatedPickupTime = 0L;

        String startTaskLoc = task.getParameterBy("from").getValueAsString();
        String endTaskLoc = task.getParameterBy("to").getValueAsString();

        long buffer1 = 2700000L;
        long buffer2 = 2700000L;
        long totalTripDur = end - start;
        long twoHrs = 7200000L;
        long fourHrs = 14400000L;
        long eightHrs = 28800000L;
        long elevenHrs = 39600000L;
        long restHr4 = 900000L;
        long restHr8 = 1800000L;
        long restHr11a = 1800000L;
        long restHr11b = 3600000L;

        if (totalTripDur <= twoHrs + buffer1 + buffer2)
        {
          estimatedPickupTime = start + currToPickupDur;
          restHr = 0L;

          createAndAddFloatChange(task, start, this.Capacity, quantity);
          createAndAddFloatChange(task, end, this.Capacity, -quantity);

          createAndAddStringChange(task, start, this.Location, CurrLoc);
          createAndAddStringChange(task, estimatedPickupTime, this.Location, startTaskLoc);
          createAndAddStringChange(task, estimatedPickupTime + buffer1, this.Location, startTaskLoc);
          createAndAddStringChange(task, end - buffer2, this.Location, endTaskLoc);
          createAndAddStringChange(task, end, this.Location, endTaskLoc);
          
          
          
          //if activities between end and 23:00 is null,then update truck location to base location
          //if(resource.getActivitiesInInterval(end, arg1))
        }

        if ((totalTripDur > twoHrs + buffer1 + buffer2) && (totalTripDur <= fourHrs + buffer1 + buffer2 + restHr4))
        {
        	restHr = restHr4;
          if (start + currToPickupDur < twoHrs)
          {
            estimatedPickupTime = start + currToPickupDur;
            long restLoc = estimatedPickupTime + buffer1 + (twoHrs - currToPickupDur);

            createAndAddFloatChange(task, start, this.Capacity, quantity);
            createAndAddFloatChange(task, end, this.Capacity, -quantity);

            createAndAddStringChange(task, start, this.Location, CurrLoc);
            createAndAddStringChange(task, estimatedPickupTime, this.Location, startTaskLoc);
            createAndAddStringChange(task, estimatedPickupTime + buffer1, this.Location, startTaskLoc);
            createAndAddStringChange(task, restLoc, this.Location, "RestLocation");
            createAndAddStringChange(task, restLoc + restHr4, this.Location, "RestLocation");
            createAndAddStringChange(task, end - buffer2, this.Location, endTaskLoc);
            createAndAddStringChange(task, end, this.Location, endTaskLoc);
          }
          else if (start + currToPickupDur > twoHrs)
          {
            long restLoc = start + twoHrs;
            estimatedPickupTime = start + currToPickupDur + restHr4;

            createAndAddFloatChange(task, start, this.Capacity, quantity);
            createAndAddFloatChange(task, end, this.Capacity, -quantity);

            createAndAddStringChange(task, start, this.Location, CurrLoc);
            createAndAddStringChange(task, restLoc, this.Location, "RestLocation");
            createAndAddStringChange(task, restLoc + restHr4, this.Location, "RestLocation");
            createAndAddStringChange(task, estimatedPickupTime, this.Location, startTaskLoc);
            createAndAddStringChange(task, estimatedPickupTime + buffer1, this.Location, startTaskLoc);
            createAndAddStringChange(task, end - buffer2, this.Location, endTaskLoc);
            createAndAddStringChange(task, end, this.Location, endTaskLoc);
          }
        }

        if ((totalTripDur > fourHrs + buffer1 + buffer2 + restHr4) && (totalTripDur <= eightHrs + buffer1 + buffer2 + restHr8))
        {
        	restHr = restHr8;
        	
          if (start + currToPickupDur < fourHrs)
          {
            estimatedPickupTime = start + currToPickupDur;
            long restLoc = estimatedPickupTime + buffer1 + (fourHrs - currToPickupDur);

            createAndAddFloatChange(task, start, this.Capacity, quantity);
            createAndAddFloatChange(task, end, this.Capacity, -quantity);

            createAndAddStringChange(task, start, this.Location, CurrLoc);
            createAndAddStringChange(task, estimatedPickupTime, this.Location, startTaskLoc);
            createAndAddStringChange(task, estimatedPickupTime + buffer1, this.Location, startTaskLoc);
            createAndAddStringChange(task, restLoc, this.Location, "RestLocation");
            createAndAddStringChange(task, restLoc + restHr8, this.Location, "RestLocation");
            createAndAddStringChange(task, end - buffer2, this.Location, endTaskLoc);
            createAndAddStringChange(task, end, this.Location, endTaskLoc);
          }
          else if (start + currToPickupDur > fourHrs)
          {
            long restLoc = start + fourHrs;
            estimatedPickupTime = start + currToPickupDur + restHr8;

            createAndAddFloatChange(task, start, this.Capacity, quantity);
            createAndAddFloatChange(task, end, this.Capacity, -quantity);

            createAndAddStringChange(task, start, this.Location, CurrLoc);
            createAndAddStringChange(task, restLoc, this.Location, "RestLocation");
            createAndAddStringChange(task, restLoc + restHr8, this.Location, "RestLocation");
            createAndAddStringChange(task, estimatedPickupTime, this.Location, startTaskLoc);
            createAndAddStringChange(task, estimatedPickupTime + buffer1, this.Location, startTaskLoc);
            createAndAddStringChange(task, end - buffer2, this.Location, endTaskLoc);
            createAndAddStringChange(task, end, this.Location, endTaskLoc);
          }
        }

        if ((totalTripDur > eightHrs + buffer1 + buffer2 + restHr8) && (totalTripDur <= elevenHrs + buffer1 + buffer2 + restHr11a + restHr11b))
        {
        	restHr = restHr11a + restHr11b;
          if (start + currToPickupDur < fourHrs)
          {
            estimatedPickupTime = start + currToPickupDur;
            long restLoc1 = estimatedPickupTime + buffer1 + (fourHrs - currToPickupDur);
            long restLoc2 = restLoc1 + restHr11a + fourHrs;

            createAndAddFloatChange(task, start, this.Capacity, quantity);
            createAndAddFloatChange(task, end, this.Capacity, -quantity);

            createAndAddStringChange(task, start, this.Location, CurrLoc);
            createAndAddStringChange(task, estimatedPickupTime, this.Location, startTaskLoc);
            createAndAddStringChange(task, estimatedPickupTime + buffer1, this.Location, startTaskLoc);
            createAndAddStringChange(task, restLoc1, this.Location, "RestLocation_1");
            createAndAddStringChange(task, restLoc1 + restHr11a, this.Location, "RestLocation_1");
            createAndAddStringChange(task, restLoc2, this.Location, "RestLocation_2");
            createAndAddStringChange(task, restLoc2 + restHr11b, this.Location, "RestLocation_2");
            createAndAddStringChange(task, end - buffer2, this.Location, endTaskLoc);
            createAndAddStringChange(task, end, this.Location, endTaskLoc);
          }
          else if (start + currToPickupDur > fourHrs)
          {
            long restLoc1 = start + fourHrs;
            estimatedPickupTime = start + currToPickupDur + restHr11a;
            long restLoc2 = start + restHr11a + buffer1 + eightHrs;

            createAndAddFloatChange(task, start, this.Capacity, quantity);
            createAndAddFloatChange(task, end, this.Capacity, -quantity);

            createAndAddStringChange(task, start, this.Location, CurrLoc);
            createAndAddStringChange(task, restLoc1, this.Location, "RestLocation_1");
            createAndAddStringChange(task, restLoc1 + restHr11a, this.Location, "RestLocation_1");
            createAndAddStringChange(task, estimatedPickupTime, this.Location, startTaskLoc);
            createAndAddStringChange(task, estimatedPickupTime + buffer1, this.Location, startTaskLoc);
            createAndAddStringChange(task, restLoc2, this.Location, "RestLocation_2");
            createAndAddStringChange(task, restLoc2 + restHr11b, this.Location, "RestLocation_2");
            createAndAddStringChange(task, end - buffer2, this.Location, endTaskLoc);
            createAndAddStringChange(task, end, this.Location, endTaskLoc);
          }
          else if (start + currToPickupDur > eightHrs)
          {
            long restLoc1 = start + fourHrs;
            long restLoc2 = restLoc1 + restHr11a + fourHrs;
            estimatedPickupTime = start + currToPickupDur + restHr11a + restHr11b;

            createAndAddFloatChange(task, start, this.Capacity, quantity);
            createAndAddFloatChange(task, end, this.Capacity, -quantity);

            createAndAddStringChange(task, start, this.Location, CurrLoc);
            createAndAddStringChange(task, restLoc1, this.Location, "RestLocation_1");
            createAndAddStringChange(task, restLoc1 + restHr11a, this.Location, "RestLocation_1");
            createAndAddStringChange(task, restLoc2, this.Location, "RestLocation_2");
            createAndAddStringChange(task, restLoc2 + restHr11b, this.Location, "RestLocation_2");
            createAndAddStringChange(task, estimatedPickupTime, this.Location, startTaskLoc);
            createAndAddStringChange(task, estimatedPickupTime + buffer1, this.Location, startTaskLoc);
            createAndAddStringChange(task, end - buffer2, this.Location, endTaskLoc);
            createAndAddStringChange(task, end, this.Location, endTaskLoc);
          }

        }

        String driverRes = resource.getParameterBy("driverId").getValueAsString();
        logger.info("truckDriver: " + driverRes);

        StringParameter selectedTruck = (StringParameter)order.getParameterBy("truckId");
        selectedTruck.setValue(resource.getId());

        StringParameter preferDriver = (StringParameter)order.getParameterBy("driverId");
        preferDriver.setValue(driverRes);
        logger.info("orderDriver :" + preferDriver.getValueAsString());

        Date pickdate = new Date(estimatedPickupTime);
        SimpleDateFormat df2 = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        String pickupDateText = df2.format(pickdate);

        StringParameter estPickupTime = (StringParameter)order.getParameterBy("estPickupTime");
        estPickupTime.setValue(pickupDateText);
        logger.info("estPickupTime :" + pickupDateText);

        Date Enddate = new Date(end);
        String endtdateText = df2.format(Enddate);

        StringParameter estDeliveryTime = (StringParameter)order.getParameterBy("estDeliveryTime");
        estDeliveryTime.setValue(endtdateText);
        logger.info("estDeliveryTime :" + endtdateText);

        long travelTime = end - start;
        
        Integer travelTimeT = (int) (travelTime / 60000);
        String travelTimeText = travelTimeT.toString(travelTimeT);
        
        logger.info("TravelTime: "+travelTimeT);

        StringParameter estTravelTime = (StringParameter)order.getParameterBy("estTravelTime");
        estTravelTime.setValue(travelTimeText);
       
        double precedingDm = currToPickupDist;
        StringParameter preceding_DM = (StringParameter)order.getParameterBy("preceding_DM");
        preceding_DM.setValue(Double.toString(precedingDm));
        
        StringParameter succeeding_DM = (StringParameter)order.getParameterBy("succeeding_DM");
        succeeding_DM.setValue(Double.toString(deliveryToBasekm));
        
        Integer travelDur = (int) (tripDuration / 60000);
        StringParameter travel_duration = (StringParameter)order.getParameterBy("travel_Duration");
        travel_duration.setValue(Integer.toString(travelDur));
        
        StringParameter loadBuffer = (StringParameter)order.getParameterBy("loading_unloading_timeBuffer");
        loadBuffer.setValue(Integer.toString(90));
        
        Integer restDur = (int) (restHr / 60000);
        StringParameter restWaitBuffer = (StringParameter)order.getParameterBy("rest_Waiting_timeBuffer");
        restWaitBuffer.setValue(Integer.toString(restDur));
        
        if(CurrLoc.equalsIgnoreCase(BaseLocation)){
        String baseStartDateText = df2.format(new Date(start));
        StringParameter baseLocStartTime = (StringParameter)order.getParameterBy("base_location_StartTime");
        baseLocStartTime.setValue(baseStartDateText);
        }
      /*  StringParameter remarks = (StringParameter)order.getParameterBy("remarks");
        estTravelTime.setValue(travelTimeText);*/
  
        
      }
    }
  }
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
  public void validate(IPolicyContainer policyContainer)
  {
    super.validate(policyContainer);

    Resource re = (Resource)policyContainer;
    evaluateBounds(re);
  }
}