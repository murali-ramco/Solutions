package com.ramco.giga.event;

import java.io.StringWriter;
import java.sql.SQLException;
import org.apache.log4j.Logger;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.rsp.core.base.FeatherliteAgent;
import com.rsp.core.base.RSPContainerHelper;
import com.rsp.core.base.command.AddOrderCommand;
import com.rsp.core.base.model.Activity;
import com.rsp.core.base.model.ElementMap;
import com.rsp.core.base.model.Event;
import com.rsp.core.base.model.Order;
import com.rsp.core.base.model.Resource;
import com.rsp.core.base.model.Task;
import com.rsp.core.base.model.Workflow;
import com.rsp.core.base.model.parameter.DateParameter;
import com.rsp.core.base.model.parameter.DurationParameter;
import com.rsp.core.base.model.parameter.StringParameter;
import com.rsp.core.base.model.stateVariable.FloatState;
import com.rsp.core.base.model.stateVariable.StateValue;
import com.rsp.core.base.model.stateVariable.StateVariable;
import com.rsp.core.base.transaction.RSPTransaction;
import com.rsp.core.helper.ISO8601FormatFactory;
import com.rsp.core.i18n.RSPMessages;
import com.rsp.core.planning.command.PlanOrderCommand;
import com.rsp.core.planning.service.EventService;
import com.ramco.giga.policy.TruckSelectionPolicy;


public class EastMalaysiaOrdersEventHandler  {
	
	private final  static  Logger logger   = Logger.getLogger(EastMalaysiaOrdersEventHandler.class);

	private final String TRUE = "true";

	private final String FALSE = "false";

	@SuppressWarnings("null")
	public String doService(com.rsp.core.planning.service.EventService.Argument argument) {
		
	
		String result = TRUE;
		try {
			
			Map<String, List<Order>> getBulkOrders = argument.bulkOrders;
			
			List<Order> bulkOrders = getBulkOrders.get("EastMalaysia");
			
			
			logger.info("size: "+bulkOrders.size());
			
			
			logger.info("size: "+bulkOrders.size());
			
			if(bulkOrders == null || bulkOrders.size() == 0)
				return "The argument orders is null.";
			
			
			Date date = new Date(System.currentTimeMillis());
		    SimpleDateFormat df = new SimpleDateFormat("MM/dd/yyyy");
		    SimpleDateFormat DateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
		    String dateText = df.format(date);
		    StringWriter W = new StringWriter();
		    StringWriter W1 = new StringWriter();
		      W.append(dateText);
		      W.append(" 08:00:00");
		      W1.append(dateText);
		      W1.append(" 22:00:00");
		      Date startDate = DateFormat.parse(W.toString());
		      long startTime = startDate.getTime();
		      logger.info("StartTime "+startDate);
		      
		      Date endDate = DateFormat.parse(W1.toString());
		      long endTime = endDate.getTime();
		      logger.info("EndTime "+endDate);
		      
		      long buffer = 2700000L;
		      

		      ElementMap<Resource> resourceMap = RSPContainerHelper.getResourceMap(true);
		      if(resourceMap == null || resourceMap.getSize() == 0)
					return "The resourceMap is null.";
		      logger.info("resourceMap: "+resourceMap.getSize());
		      
		      	     
		      List<Resource> KKCT_trucks = (List<Resource>) resourceMap.getByType("truck_KKCT");
		      List<Resource> CB_trucks = (List<Resource>) resourceMap.getByType("truck_CB");
		      List<Resource> Marshals = (List<Resource>) resourceMap.getByType("marshal_east");
		      
			  List<Resource> avlTrucks_KKCT = new ArrayList<Resource>();
		      List<Resource> avlSingle_KKCT = new ArrayList<Resource>();
		      List<Resource> avlRigid_KKCT = new ArrayList<Resource>();
		      List<Resource> avlArticulated_KKCT = new ArrayList<Resource>();
		      
		      List<Resource> avlTrucks_CB = new ArrayList<Resource>();
		      List<Resource> avlSingle_CB = new ArrayList<Resource>();
		      List<Resource> avlRigid_CB = new ArrayList<Resource>();
		      List<Resource> avlArticulated_CB = new ArrayList<Resource>();
		      Collection<Resource> routes = RSPContainerHelper.getResourceMap(true).getByType("Routes");
		      
		      
		      
		   
		      for (Resource res : KKCT_trucks) {
		    	  
		    	  logger.info("res: "+res.getId());
		    	long EarliestAvailableTime = res.getStateVariableBy("Location").getValueAt(endTime).getTime();
		    	
		    	if (EarliestAvailableTime < startTime){
		    		
		    		EarliestAvailableTime = startTime;
		    	}
		    	
		    	logger.info("EarliestAvailableTime->"+new Date(EarliestAvailableTime));
		    	
		        List<Activity> activities = res.getActivitiesInInterval(EarliestAvailableTime, endTime);
		        
		        if (activities.size() < 1)
		        {
		        	avlTrucks_KKCT.add(res);

		           if (res.getParameterBy("truckType").getValueAsString().equalsIgnoreCase("single")) 
		        	   avlSingle_KKCT.add(res);
		          
		        else if (res.getParameterBy("truckType").getValueAsString().equalsIgnoreCase("rigid")) 
		        	avlRigid_KKCT.add(res);
		          
		        else if (res.getParameterBy("truckType").getValueAsString().equalsIgnoreCase("articulated")) 
		        	  avlArticulated_KKCT.add(res);
		          
		        }
		      }
		      logger.info("avlTrucks: "+avlTrucks_KKCT.size()+"  "+avlTrucks_KKCT);
		      
               for (Resource res : CB_trucks) {
  //          	   res.getStateVariableBy("Capacity").add(1.0,));
		    	  
		    	  logger.info("res: "+res.getId());
		    	long EarliestAvailableTime = res.getStateVariableBy("Location").getValueAt(endTime).getTime();
		    	
		    	if (EarliestAvailableTime < startTime){
		    		
		    		EarliestAvailableTime = startTime;
		    	}
		    	
		    	logger.info("EarliestAvailableTime->"+new Date(EarliestAvailableTime));
		    	
		        List<Activity> activities = res.getActivitiesInInterval(EarliestAvailableTime, endTime);
		        
		        if (activities.size() < 1)
		        {
		        	avlTrucks_CB.add(res);

		           if (res.getParameterBy("truckType").getValueAsString().equalsIgnoreCase("single")) 
		        	   avlSingle_CB.add(res);
		          
		        else if (res.getParameterBy("truckType").getValueAsString().equalsIgnoreCase("rigid")) 
		        	avlRigid_CB.add(res);
		          
		        else if (res.getParameterBy("truckType").getValueAsString().equalsIgnoreCase("articulated")) 
		        	  avlArticulated_CB.add(res);
		          
		        }
		      }
		      logger.info("avlTrucks_CB: "+avlTrucks_CB.size()+"  "+avlTrucks_CB);
			

				List<Order> orders = RSPContainerHelper.getOrderMap(true).asList();
				
				
				
				
				List<Order> CB_orders = new ArrayList<Order>();
				List<Order> KKCT_orders = new ArrayList<Order>();
				List<Order> Corporate_Local_DLT = new ArrayList<Order>();
				List<Order> Individual_Local_DLT = new ArrayList<Order>();
				
				
				for (Order o : bulkOrders ){
					
					RSPTransaction tx = FeatherliteAgent.getTransaction(this);
					AddOrderCommand aoc = new AddOrderCommand();
					aoc.setOrder(o);
					tx.addCommand(aoc);
					tx.commit();
				
					String OrderType = o.getParameterBy("orderType").getValueAsString();
					String businessDiv = o.getParameterBy("BD").getValueAsString();
					
					if (businessDiv.equalsIgnoreCase("KKCT")){
						KKCT_orders.add(o);
						
					/*	if(OrderType.equalsIgnoreCase("Corporate_Local_DLT"))
						Corporate_Local_DLT.add(o);
						
						else if(OrderType.equalsIgnoreCase("Individual_Local_DLT"))
						Individual_Local_DLT.add(o);*/
							
					}
					
			   else if (businessDiv.equalsIgnoreCase("CB"))
					    CB_orders.add(o);
			   else throw RSPMessages.newRspMsgEx("No East Malaysia Orders are there");
				}
				logger.info("KKCT_orders: "+KKCT_orders.size());
				logger.info("CB_orders: "+CB_orders.size());/*
				logger.info("Corporate_Local_DLT: "+Corporate_Local_DLT.size());
				logger.info("Individual_Local_DLT: "+Individual_Local_DLT.size());*/
					
					/*int noOfBatchesOfIndLocImp = indLocalImportOrders.size()/5;
					logger.info("noOfBatchesOfIndLocImp->"+noOfBatchesOfIndLocImp);*/
				
			//	Individual_Local_DLT = getOrderSequence(pickup,Individual_Local_DLT);
				logger.info("KKCT_orders->"+KKCT_orders);
				
				List<Order> IndLocDLT = getImportSequence(KKCT_orders);
				logger.info("ILD : "+IndLocDLT);
				
					int a = 0;
					
					while (IndLocDLT.size() > 0){
						logger.info("Individual_Local_DLT-newBatchStarted");
					List <Order> imp = new ArrayList<Order>();
					
					String pickupLoc = IndLocDLT.get(0).getParameterBy("pickupLocation").getValueAsString();
					logger.info("pickup: "+pickupLoc);
					
			//		Individual_Local_DLT = getOrderSequence(pickupLoc,Individual_Local_DLT);
					logger.info("Individual_Local_DLT->"+IndLocDLT);
					
					String lastDelivery = null;
					long lastDeliveryTime = 0L;
					long truckToPickupDur = 0L;
				/*	List<Order> orderSeqToPlan = getOrderSequence(pickupLoc,Individual_Local_DLT);
					logger.info("orderSeqToPlan->"+orderSeqToPlan);*/
					
					long min = Long.MAX_VALUE;
					Resource selectedTruck = null;
					String selectedDriver = null;
					Resource selectedMarshal = null;
					
					StringWriter w = new StringWriter();

					logger.info("avlArticulated_KKCT: "+avlArticulated_KKCT.size());
					logger.info("avlRigid_KKCT: "+avlRigid_KKCT.size());
					if(avlRigid_KKCT.size() > 0){
					
						logger.info("avlRigid_KKCT available");
					for (Resource r: avlRigid_KKCT){
						w.append(r.getId()+", ");
						String currentTruckLocation = r.getStateVariableBy("Location").getValueAt(endTime).getValueAsString();
						logger.info("currentTruckLocation->"+currentTruckLocation);
						
						if (currentTruckLocation.equalsIgnoreCase(pickupLoc))
							truckToPickupDur = 0L;
						
						else
						truckToPickupDur = getDuration(currentTruckLocation , pickupLoc);
						
						if (truckToPickupDur < min){
							min = truckToPickupDur;
							selectedTruck = r;
							logger.info(r.getId());
							selectedDriver = r.getParameterBy("driverId").getValueAsString();
							logger.info(selectedDriver);
						/*	selectedMarshal = avlMarshals.get(0);
							logger.info(selectedMarshal);*/
							
						}
					}
					}
					
					else if(avlRigid_KKCT.size() < 1 && avlArticulated_KKCT.size() >0){
					
					for (Resource r: avlArticulated_KKCT){
						w.append(r.getId()+", ");
						String currentTruckLocation = r.getStateVariableBy("Location").getValueAt(endTime).getValueAsString();
						truckToPickupDur = getDuration(currentTruckLocation , pickupLoc);
						if (truckToPickupDur < min){
							min = truckToPickupDur;
							selectedTruck = r;
							logger.info(r.getId());
							selectedDriver = r.getParameterBy("driverId").getValueAsString();
							logger.info(selectedDriver);
						/*	selectedMarshal = avlMarshals.get(0);
							logger.info(selectedMarshal);*/
							
						}
					}
					}
					else if(avlArticulated_KKCT.size() < 1 && avlRigid_KKCT.size() < 1){
						logger.info("truckNotAvailable");
						for(Order o: IndLocDLT){
							StringParameter remarks = (StringParameter) o.getParameterBy("remarks");
							remarks.setValue("All feasible trucks for this order are occupied.");
						}
						
						break;}
					
					for (Resource r : Marshals)
					{
						logger.info(r.getId()+" activityMap: "+r.getActivityMap().size());
						if (r.getActivityMap().size() < 1)
							selectedMarshal = r;
						else{
							logger.info("no marshals are there");
						}
					}
				
					
					logger.info("selectedTruck->"+selectedTruck);
					logger.info("selectedDriver->"+selectedDriver);
					if(selectedMarshal== null)
				    logger.info("selectedMarshal->TBA");
					else
				    logger.info("selectedMarshal->"+selectedMarshal.getId());
					
					
					double lowerBoundCapacity = (Double) selectedTruck.getStateVariableBy("Capacity").getLowerBound();
					
					Long earliestAvailTime  = selectedTruck.getStateVariableBy("Location").getValueAt(endTime).getTime();
					if (earliestAvailTime < startTime){
			    		
						earliestAvailTime = startTime;
			    	}
					logger.info("earliestAvailTimeofSelectedTruck->" +new Date(earliestAvailTime));
					
					String CurrTruckLoc = selectedTruck.getStateVariableBy("Location").getValueAt(endTime).getValueAsString();
					
					Long estimatedPickupTime = earliestAvailTime + min;
					logger.info("estimatedPickupTime->" +new Date(estimatedPickupTime));
					
			        String pickupDateText = DateFormat.format(new Date(estimatedPickupTime));
					
					Long estimatedDeliveryTime = estimatedPickupTime;
					
					double units = (Double) selectedTruck.getStateVariableBy("Capacity").getUpperBound();
					logger.info("units: "+units);
					
					for (int i = 0; i < units ;a++){
						
						logger.info("loop round-> "+a);
						Order order = IndLocDLT.get(i);
						
						StringParameter evaluatedTrucks = (StringParameter) order.getParameterBy("evaluatedTrucks");
						evaluatedTrucks.setValue(w.toString());
						
						logger.info("oder: "+order.getId());
						String prevLoc;
						String nextDeliveryLoc;
						
						logger.info(IndLocDLT.get(0));
						if(order == IndLocDLT.get(0)){
							logger.info("It is the first order");
							prevLoc = pickupLoc;}
						else {prevLoc = IndLocDLT.get(i-1).getParameterBy("deliveryLocation").getValueAsString();};
						
						
						nextDeliveryLoc =  order.getParameterBy("deliveryLocation").getValueAsString();
						
						logger.info("Before Planning: from "+prevLoc+" To "+nextDeliveryLoc);
						long prevLocToNextdeliveryDur;
						
						if(nextDeliveryLoc.equalsIgnoreCase(prevLoc))
							prevLocToNextdeliveryDur = 0L;
						else
						prevLocToNextdeliveryDur = getDuration(prevLoc , nextDeliveryLoc);
						
						if(!nextDeliveryLoc.equalsIgnoreCase(prevLoc))
						estimatedDeliveryTime += prevLocToNextdeliveryDur + buffer;
						
						logger.info("orderPickup "+order.getParameterBy("pickupLocation").getValueAsString() +" Batchpickup"+pickupLoc);
						logger.info("activitySize: "+selectedTruck.getActivitiesInInterval(earliestAvailTime, estimatedDeliveryTime).size()+" units->"+units);
						
						if (estimatedDeliveryTime > (endTime-buffer)){
							logger.info("order:" +order+" cannot be planned in this batch");
							break;}
							
						else {
						
						if((order.getParameterBy("pickupLocation").getValueAsString().equalsIgnoreCase(pickupLoc)) &&( selectedTruck.getActivitiesInInterval(earliestAvailTime, estimatedDeliveryTime).size() < units)){	
						i++;
						imp.add(order);
						logger.info("imp +1");
						lastDeliveryTime = estimatedDeliveryTime;

						Activity activity = new Activity();
						
						activity.setOrderNetId(order.getId());
						activity.setStart(earliestAvailTime);
						activity.setDuration(estimatedDeliveryTime+buffer - earliestAvailTime);
						
						selectedTruck.addActivity(activity);
						
						lastDelivery = nextDeliveryLoc;
						logger.info("After Planning: from "+prevLoc+" To "+nextDeliveryLoc);
						
						StringParameter script = (StringParameter) order.getParameterBy("script");
						logger.info(script);
						
						StringParameter truckId = (StringParameter) order.getParameterBy("truckId");
						logger.info(truckId.getValueAsString());
						
						StringParameter driverId = (StringParameter) order.getParameterBy("driverId");
						
						StringParameter marshalId = (StringParameter) order.getParameterBy("Marshal_id");
						StringParameter marshalName = (StringParameter) order.getParameterBy("Marshal_name");
						
						script.setValue("PlanOrderKKCT");
						truckId.setValue(selectedTruck.getId());
						
						lowerBoundCapacity ++;
						
						driverId.setValue(selectedDriver);
						if(selectedMarshal != null){
						marshalId.setValue(selectedMarshal.getId());
						marshalName.setValue(selectedMarshal.getName());}
						else{
						marshalId.setValue("Marshal-TBA");	
						marshalName.setValue("Marshal-TBA");}
						
				
					
						logger.info(driverId);
						logger.info(marshalId);
						
						RSPTransaction tx1 = FeatherliteAgent.getTransaction(this);
						PlanOrderCommand poc = new PlanOrderCommand();
						poc.setOrder(order);
						tx1.addCommand(poc);
						tx1.commit();
						
						StringParameter estPickupTime = (StringParameter) order.getParameterBy("estPickupTime");
				        estPickupTime.setValue(pickupDateText);
				        logger.info("estPickupTime :" + estPickupTime.getValueAsString());// set the estPickupTime 
				        
				        String estDeliveryDate = DateFormat.format(new Date(estimatedDeliveryTime));

				        StringParameter estDeliveryTime = (StringParameter) order.getParameterBy("estDeliveryTime");
				        estDeliveryTime.setValue(estDeliveryDate);
				        logger.info("estDeliveryTime :" + estDeliveryTime.getValueAsString());//set the estDeliveryTime
				        
				        Integer estTravelTym = (int) ((estimatedDeliveryTime - earliestAvailTime)/60000);
				        StringParameter estTravelTime = (StringParameter)order.getParameterBy("estTravelTime");
				        estTravelTime.setValue(Integer.toString(estTravelTym));
				        logger.info("estTravelTime :" + estTravelTime);//set the estTravelTime(inMins)
				        
				        
				        StringParameter preceding_DM = (StringParameter)order.getParameterBy("preceding_DM");
				        preceding_DM.setValue("0");
				        logger.info("preceding_DM :" + preceding_DM);//set the estTravelTime(inMins)
				        
				        String Base = selectedTruck.getParameterBy("location").getValueAsString();
				        StringParameter succeeding_DM = (StringParameter)order.getParameterBy("succeeding_DM");
				        succeeding_DM.setValue("0");
				        logger.info("succeeding_DM :" + succeeding_DM);//set the estTravelTime(inMins)
				        
				        Integer travelDur = (int) (getDuration(pickupLoc,nextDeliveryLoc)/60000);
				        StringParameter travel_duration = (StringParameter)order.getParameterBy("travel_Duration");
				        travel_duration.setValue(Integer.toString(travelDur));
				        logger.info("travel_duration :" + travel_duration);
				        
				        StringParameter loadBuffer = (StringParameter)order.getParameterBy("loading_unloading_timeBuffer");
				        loadBuffer.setValue(Integer.toString(90));
				        logger.info("loadBuffer :" + loadBuffer);
				        
				 //       Integer restDur = (int) (restHr / 60000);
				        StringParameter restWaitBuffer = (StringParameter)order.getParameterBy("rest_Waiting_timeBuffer");
				        restWaitBuffer.setValue(Integer.toString(0));
				        logger.info("restWaitBuffer :" + restWaitBuffer);
				        
				        StringParameter baseLocStartTime = (StringParameter)order.getParameterBy("base_location_StartTime");
				        if(order.equals(imp.get(0)) && CurrTruckLoc.equalsIgnoreCase(Base)){
				        	
				        String baseStartDateText = DateFormat.format(new Date(earliestAvailTime));
				        baseLocStartTime.setValue(baseStartDateText);
				        
				        }
				        else baseLocStartTime.setValue("");
				        
				        logger.info("baseLocStartTime :" + baseLocStartTime);
						}
						else
						break;
						
						logger.info("updated");
						
					}
						logger.info("loop completed");
					
						if(i == IndLocDLT.size())
							break;}
					
					
					if(imp.size() < 1){
					
						StringParameter remarks = (StringParameter) IndLocDLT.get(0).getParameterBy("remarks");
						remarks.setValue("The feasible trucks to fulfill this order cannot complete the trip within End time is greater than the required delivery time.");
						IndLocDLT.remove(IndLocDLT.get(0));
					}
					else{
					logger.info("imp->"+imp);
					 logger.info("imp"+imp.size());
					 
					 
					 Activity activity = new Activity();
						
						activity.setOrderNetId(imp.get(0).getId());
						activity.setStart(earliestAvailTime);
						activity.setDuration(lastDeliveryTime+buffer - earliestAvailTime);
						
						if(selectedMarshal == null)
						selectedMarshal.addActivity(activity);
		
			     
			        
			        StringParameter preceding_DM = (StringParameter)imp.get(0).getParameterBy("preceding_DM");
			        double preDeadM;
			        if(CurrTruckLoc.equalsIgnoreCase(pickupLoc))
			        preDeadM = 0L;
			        else 
			        preDeadM = getDistance(CurrTruckLoc,pickupLoc);
			        preceding_DM.setValue(Double.toString(preDeadM));
			        logger.info("preceding_DM :" + preceding_DM);
			        
			        logger.info("imp.get "+(imp.size()-1));
			        String Base = selectedTruck.getParameterBy("location").getValueAsString();
			        StringParameter succeeding_DM = (StringParameter)imp.get(imp.size()-1).getParameterBy("succeeding_DM");
			        succeeding_DM.setValue(Double.toString(getDistance(lastDelivery,Base)));
			        logger.info("succeeding_DM :" + succeeding_DM);
			        
					
					logger.info("imp: "+imp.size()+"Orders: "+imp);
					
  
					if (avlArticulated_KKCT.contains(selectedTruck))
						avlArticulated_KKCT.remove(selectedTruck);
					
					else if(avlRigid_KKCT.contains(selectedTruck))
						avlRigid_KKCT.remove(selectedTruck);
					
					logger.info(selectedTruck+"truckRemoved");
					
					logger.info("KKCT_orders: "+KKCT_orders);
					
			/*		
					FloatState capacity = (FloatState) selectedTruck.getStateVariableBy("Capacity");
		        	
			        StateValue<Double> truckCapacity = null;
			   
					truckCapacity.setId("");
					truckCapacity.setValue(lowerBoundCapacity);
					truckCapacity.setType("Float");
					truckCapacity.setTime(earliestAvailTime);
					capacity .add(truckCapacity);*/
					
					
						for (Order o : imp){
							

						/*	lowerBoundCapacity--;
						    StateValue<Double> truckCap = null;
							   
						    truckCap.setId("");
						    truckCap.setValue(lowerBoundCapacity);
						    truckCap.setType("Float");
						    truckCap.setTime(earliestAvailTime);
						    
							capacity .add(truckCapacity);*/
							
							if(IndLocDLT.contains(o))
							{
								logger.info("o_imp: "+o);
								IndLocDLT.remove(o);	
								logger.info(o + "is removed ");
							}
						}
					}
						logger.info("a->:"+a);
						logger.info("BatchCompleted");	
						logger.info("ordersLeft: "+IndLocDLT);
					}
					
					logger.info("Individual_Local_DLT plan completed ");	
	
					
					
					/*logger.info("Corporate_Local_DLT: "+Corporate_Local_DLT.size());
					
			
					
					for(Order order: Corporate_Local_DLT){
						long min = Long.MAX_VALUE;
						Resource selectedTruck = null;
						String selectedDriver = null;
						String selectedMarshal = null;
						long truckToPickupDur = 0L;
						StringParameter remarks = (StringParameter) order.getParameterBy("remarks");
						
						String pickupLoc = order.getParameterBy("pickupLocation").getValueAsString();
					    Resource pickupRes = RSPContainerHelper.getResourceMap(true).getEntryBy(pickupLoc);
					    if(!routes.contains(pickupRes)){
					    	logger.info("Pickup Location "+pickupLoc+" is not avaolable in routes");
					    	remarks.setValue("Pickup Location "+pickupLoc+" is not avaolable in routes");
					    	continue;
					    }
						
						String deliveryLoc = order.getParameterBy("deliveryLocation").getValueAsString();
						
						Resource deliveryRes = RSPContainerHelper.getResourceMap(true).getEntryBy(deliveryLoc);
					    if(!routes.contains(deliveryRes)){
					    	logger.info("Delivery Location "+deliveryLoc+" is not avaolable in routes");
					    	remarks.setValue("Delivery Location "+deliveryLoc+" is not avaolable in routes");
					    	continue;
					    }
						long EarliestAvailableTime = 0;
						
						StringParameter script = (StringParameter) order.getParameterBy("script");
						logger.info(script);
						StringParameter truckId = (StringParameter) order.getParameterBy("truckId");
						logger.info(truckId.getValueAsString());
						StringParameter driverId = (StringParameter) order.getParameterBy("driverId");
						StringParameter marshalId = (StringParameter) order.getParameterBy("Marshal_id");
						
						long estimatedPickupTime = 0;
						long estimatedDeliveryTime = 0;
						
						StringWriter w = new StringWriter();
						
						String truckType = order.getParameterBy("truckType").getValueAsString();
						List<Resource> trucks = new ArrayList<Resource>();
						
						if(truckType.equalsIgnoreCase("articulated"))
							trucks = avlArticulated_KKCT;
						
						if(truckType.equalsIgnoreCase("rigid"))
							trucks = avlRigid_KKCT;
						
						if(truckType.equalsIgnoreCase("single"))
							trucks = avlSingle_KKCT;
						
						if(trucks.size() < 1)
							break;
						
						
						logger.info("trucks.size()->"+trucks.size());
						for (Resource r: trucks){
					    	  logger.info("res: "+r.getId());
					    	  w.append(r.getId()+", ");
					    	 
						String currentTruckLocation = r.getStateVariableBy("Location").getValueAt(endTime).getValueAsString();
						logger.info("currentTruckLocation" +currentTruckLocation);
						
						logger.info(pickupLoc);
						truckToPickupDur = getDuration(currentTruckLocation , pickupLoc);
						
						if (truckToPickupDur < min){
							min = truckToPickupDur;
							selectedTruck = r;
							logger.info(r);
							selectedDriver = r.getParameterBy("driverId").getValueAsString();
							logger.info(selectedDriver);
							
							selectedMarshal = avlMarshals.get(0).getId();
							logger.info(selectedDriver);
							
							
							
						}
					}
						
						StringParameter evaluatedTrucks = (StringParameter) order.getParameterBy("evaluatedTrucks");
						evaluatedTrucks.setValue(w.toString());
						
						 EarliestAvailableTime = selectedTruck.getStateVariableBy("Location").getValueAt(endTime).getTime();
					    	
					    	if (EarliestAvailableTime < startTime){
					    		
					    		EarliestAvailableTime = startTime;
					    	}
					logger.info("EarliestAvailableTime_DLT:" +new Date(EarliestAvailableTime));
					
					estimatedPickupTime = EarliestAvailableTime + truckToPickupDur;
					logger.info("estimatedPickupTime->"+estimatedPickupTime);

					estimatedDeliveryTime =  estimatedPickupTime + getDuration(pickupLoc,deliveryLoc) + 2*buffer;
					logger.info("estimatedDeliveryTime->"+estimatedDeliveryTime);
					
					if(estimatedDeliveryTime > endTime || selectedTruck.getActivitiesInInterval(estimatedPickupTime, estimatedDeliveryTime).size() > 0){
						continue;
					}
					

						
						script.setValue("PlanOrderKKCT");
						truckId.setValue(selectedTruck.getId());
						driverId.setValue(selectedDriver);
						marshalId.setValue(selectedMarshal);
						logger.info(driverId);
						
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
				        
				        Integer estTravelTym = (int) ((estimatedDeliveryTime - EarliestAvailableTime)/60000);
				        StringParameter estTravelTime = (StringParameter) order.getParameterBy("estTravelTime");
				        estTravelTime.setValue(Integer.toString(estTravelTym));
				        logger.info("estTravelTime :" + estTravelTime.getValueAsString());   
				        
				        String CurrTruckLoc = selectedTruck.getStateVariableBy("Location").getValueAt(endTime).getValueAsString();
				        StringParameter preceding_DM = (StringParameter)order.getParameterBy("preceding_DM");
				        preceding_DM.setValue(Double.toString(getDistance(CurrTruckLoc,pickupLoc)));
				        
				        String Base = selectedTruck.getParameterBy("location").getValueAsString();
				        StringParameter succeeding_DM = (StringParameter)order.getParameterBy("succeeding_DM");
				        succeeding_DM.setValue(Double.toString(getDistance(deliveryLoc,Base)));
				        
				        Integer travelDur = (int) (getDuration(pickupLoc,deliveryLoc)/60000);
				        StringParameter travel_duration = (StringParameter)order.getParameterBy("travel_Duration");
				        travel_duration.setValue(Integer.toString(travelDur));
				        
				        StringParameter loadBuffer = (StringParameter)order.getParameterBy("loading_unloading_timeBuffer");
				        loadBuffer.setValue(Integer.toString(90));
				        
				 //       Integer restDur = (int) (restHr / 60000);
				        StringParameter restWaitBuffer = (StringParameter)order.getParameterBy("rest_Waiting_timeBuffer");
				        restWaitBuffer.setValue(Integer.toString(0));
				        
				        StringParameter baseLocStartTime = (StringParameter)order.getParameterBy("base_location_StartTime");
				        if(CurrTruckLoc.equalsIgnoreCase(Base)){
				        	
				        String baseStartDateText = DateFormat.format(new Date(EarliestAvailableTime));
				        baseLocStartTime.setValue(baseStartDateText);
				        
				        }
				        else baseLocStartTime.setValue("");
				        

						if (avlArticulated_KKCT.contains(selectedTruck)){
							avlArticulated_KKCT.remove(selectedTruck);
							trucks.remove(selectedTruck);}
						
						else if(avlRigid_KKCT.contains(selectedTruck)){
							avlRigid_KKCT.remove(selectedTruck);
							trucks.remove(selectedTruck);
							}
						
						logger.info(selectedTruck+"removed");
					}
					logger.info("Corporate_Local_DLT PlanCompleted");*/
		}
					
					
	catch (Exception e) {
		result = FALSE;
	e.printStackTrace();
	result = e.getMessage();
}
return result;
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
	

private List<Order>  getOrderSequence(String pickup,List<Order> Orders){
	logger.info("start");
List<Order> sortOrders = new ArrayList<Order>();
Map<Order,String> orderLocations = new HashMap<Order,String>();
List<String> route = new ArrayList<String>();
List<String> Hubs = new ArrayList<String>();

for(Order o:Orders){
	String delivery = o.getParameterBy("deliveryLocation").getValueAsString();
	if(!Hubs.contains(delivery))
	Hubs.add(delivery);	
	// logger.info("delivery-->"+delivery);
	orderLocations.put(o, delivery);
}
// logger.info("deliveryHubs: "+Hubs.size()+Hubs);


String newDrop = null;

while(Hubs.size()>1){
long min = Long.MAX_VALUE;
for(int i = 0; i < Hubs.size(); i++){
	String s = Hubs.get(i);
	long dur = getDuration(pickup , s);
	
	if(min > dur){
		min = dur;
		newDrop = s;
	}
}
// logger.info("newDrop"+newDrop);
route.add(newDrop);
Hubs.remove(newDrop);
// logger.info("left->"+Hubs);
pickup = newDrop;
}
route.add(Hubs.get(0));


logger.info("route->"+route);

 for (String p :route ){
     for (Order o : Orders) {
         if (o.getParameterBy("deliveryLocation").getValueAsString().equalsIgnoreCase(p)) 
        	 sortOrders.add(o);
         }
     }
 logger.info("sortOrders"+sortOrders);
	return sortOrders;

}


private List<Order>  getOrderSequenceImport(String pickup,List<Order> Orders){
	logger.info("start");
List<Order> sortOrders = new ArrayList<Order>();
Map<Order,String> orderLocations = new HashMap<Order,String>();
List<String> route = new ArrayList<String>();
List<String> Hubs = new ArrayList<String>();

for(Order o:Orders){
	String delivery = o.getParameterBy("deliveryLocation").getValueAsString();
	if(!Hubs.contains(delivery))
	Hubs.add(delivery);	
	logger.info("delivery-->"+delivery);
	orderLocations.put(o, delivery);
}
logger.info("deliveryHubs: "+Hubs.size()+Hubs);


String newDrop = null;

while(Hubs.size()>1){
long min = Long.MAX_VALUE;
for(int i = 0; i < Hubs.size(); i++){
	String s = Hubs.get(i);
	long dur = getDuration(pickup , s);
	
	if(min > dur){
		min = dur;
		newDrop = s;
	}
}
logger.info("newDrop"+newDrop);
route.add(newDrop);
Hubs.remove(newDrop);
logger.info("left->"+Hubs);
pickup = newDrop;
}
route.add(Hubs.get(0));


logger.info("route->"+route);

 for (String p :route ){
     for (Order o : Orders) {
         if (o.getParameterBy("deliveryLocation").getValueAsString().equalsIgnoreCase(p)) 
        	 sortOrders.add(o);
         }
     }
 logger.info("sortOrders"+sortOrders);
	return sortOrders;
	
	
	
}

private HashMap<String,List<Order>>  getImport(List<Order> ImportOrders){
	logger.info("start");

	Collection<Resource> Routes =  RSPContainerHelper.getResourceMap(true).getByType("Routes");
	List<Order> imp = new ArrayList<Order>();
	HashMap<String,List<Order>> pickupToDeliveryMap = new HashMap<String,List<Order>>();
	HashMap<String,List<Order>> Result = new HashMap<String,List<Order>>();
	List<String> Pickups = new ArrayList<String>();
	
	 for (Order o: ImportOrders){	
	    	String pickupLoc = o.getParameterBy("pickupLocation").getValueAsString();
	    	Resource p = RSPContainerHelper.getResourceMap(true).getEntryBy(pickupLoc);
	    	StringParameter remarks = (StringParameter) o.getParameterBy("remarks");
	    	
	    	if(!Routes.contains(p)){
	    		remarks.setValue("Pickup Location "+pickupLoc+" is not available in routes.");
	    //		imports.remove(o);
	    	    continue;}
	    	
	    	
	    	String deliveryLoc = o.getParameterBy("deliveryLocation").getValueAsString();
	    	Resource d = RSPContainerHelper.getResourceMap(true).getEntryBy(deliveryLoc);

	    	
	    	if(!Routes.contains(d)){
	    		remarks.setValue("Delivery Location "+deliveryLoc+" is not available in routes.");
	  //  		imports.remove(o);
	    	    continue;}
	    	
		   imp.add(o);
		   if(!Pickups.contains(pickupLoc))
			   Pickups.add(pickupLoc);
	    }
	 
    for (String pickupPoint: Pickups){
    	
    	List<Order> deliveryHubs = new ArrayList<Order>();
    	
    	for (Order o1: imp){
    		if(o1.getParameterBy("pickupLocation").getValueAsString().equalsIgnoreCase(pickupPoint))
    			deliveryHubs.add(o1);	
    	}
    	pickupToDeliveryMap.put(pickupPoint, deliveryHubs);
    }
    
    logger.info("pickupToDeliveryMap"+pickupToDeliveryMap);
    
    for (String s:pickupToDeliveryMap.keySet()){
    	List<Order> routeSeq = new ArrayList<Order>();
    	routeSeq = getOrderSequenceImport(s,pickupToDeliveryMap.get(s));
    	Result.put(s, routeSeq);
    }
	

 logger.info("Result"+Result);
	return Result;
	
	
	
}

private List<Order>  getImportSequence(List<Order> ImportOrders){
	logger.info("start");

	Collection<Resource> Routes =  RSPContainerHelper.getResourceMap(true).getByType("Routes");
	List<Order> imp = new ArrayList<Order>();
	HashMap<String,List<Order>> pickupToDeliveryMap = new HashMap<String,List<Order>>();
	List<Order> Result = new ArrayList<Order>();
	List<String> Pickups = new ArrayList<String>();
	
	 for (Order o: ImportOrders){	
	    	String pickupLoc = o.getParameterBy("pickupLocation").getValueAsString();
	    	Resource p = RSPContainerHelper.getResourceMap(true).getEntryBy(pickupLoc);
	    	StringParameter remarks = (StringParameter) o.getParameterBy("remarks");
	    	
	    	if(!Routes.contains(p)){
	    		remarks.setValue("Pickup Location "+pickupLoc+" is not available in routes.");
	    //		imports.remove(o);
	    	    continue;}
	    	
	    	
	    	String deliveryLoc = o.getParameterBy("deliveryLocation").getValueAsString();
	    	Resource d = RSPContainerHelper.getResourceMap(true).getEntryBy(deliveryLoc);

	    	
	    	if(!Routes.contains(d)){
	    		remarks.setValue("Delivery Location "+deliveryLoc+" is not available in routes.");
	  //  		imports.remove(o);
	    	    continue;}
	    	
		   imp.add(o);
		   if(!Pickups.contains(pickupLoc))
			   Pickups.add(pickupLoc);
	    }
	 
    for (String pickupPoint: Pickups){
    	
    	List<Order> deliveryHubs = new ArrayList<Order>();
    	
    	for (Order o1: imp){
    		if(o1.getParameterBy("pickupLocation").getValueAsString().equalsIgnoreCase(pickupPoint))
    			deliveryHubs.add(o1);	
    		     
    	}
    	pickupToDeliveryMap.put(pickupPoint, getOrderSequenceImport(pickupPoint,deliveryHubs));
    }
    
    logger.info("pickupToDeliveryMap"+pickupToDeliveryMap);
    
    for (String s:pickupToDeliveryMap.keySet()){
    	
    	for(Order o: pickupToDeliveryMap.get(s)){
    	Result.add(o);
          } 
    }

 logger.info("Result"+Result);
	return Result;
	
	
	
}
}
