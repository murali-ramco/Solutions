package com.ramco.giga.formula;

import com.rsp.core.base.RSPContainerHelper;
import com.rsp.core.base.exception.DataModelException;
import com.rsp.core.base.exception.RSPException;
import com.rsp.core.base.formula.LookupFormula;
import com.rsp.core.base.model.Activity;
import com.rsp.core.base.model.DescriptiveParameter;
import com.rsp.core.base.model.ElementMap;
import com.rsp.core.base.model.Order;
import com.rsp.core.base.model.ParameterizedElement;
import com.rsp.core.base.model.Resource;
import com.rsp.core.base.model.constants.PlanningState;
import com.rsp.core.base.model.parameter.DateParameter;
import com.rsp.core.base.model.parameter.FloatListParameter;
import com.rsp.core.base.model.parameter.FloatParameter;
import com.rsp.core.base.model.parameter.IntegerParameter;
import com.rsp.core.base.model.parameter.Parameter;
import com.rsp.core.base.model.parameter.StringParameter;
import com.rsp.core.base.model.stateVariable.FloatState;
import com.rsp.core.base.model.stateVariable.StateValue;
import com.rsp.core.base.model.stateVariable.StateVariable;
import com.rsp.core.i18n.RSPMessages;
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
import java.util.Map.Entry;
import java.util.Set;
import org.apache.log4j.Logger;

public class TrucksByType_east extends LookupFormula
{
  private static final long serialVersionUID = 1L;

  @SuppressWarnings("unused")
public Parameter<?> evaluate()
    throws RSPException
  {
    StringParameter resultParam = null;
    String ResType = null;
    String args = this.argument;
   
    ResType = args;
   

    ElementMap<Resource> ResourceMap = RSPContainerHelper.getResourceMap(true);
    Collection<Resource> resourcesByType = ResourceMap.getByType(ResType);
    List<Resource> trucksResIds = new ArrayList();
    List<Resource> trucksWithoutDriver = new ArrayList();
    List<Resource> singleTruckRes = new ArrayList<Resource>();
    List<Resource> rigidTruckRes = new ArrayList<Resource>();
    List<Resource> articulatedTruckRes = new ArrayList<Resource>();
    

    ParameterizedElement tmp = getElement();
    String orderNetId = null;
    while (tmp != null) {
      orderNetId = tmp.getId();
      tmp = tmp.getParent();
    }

    Order order = (Order)RSPContainerHelper.getOrderMap(true).getEntryBy(orderNetId);
    //logger.info("orderNetId: " + orderNetId);

    StringParameter remarks = (StringParameter) order.getParameterBy("remarks");
    
    String orderedTruckType = order.getParameterBy("truckType").getValueAsString();

    String pickup = order.getParameterBy("pickupLocation").getValueAsString();
    //logger.info("pickup: " +pickup);
    String delivery = order.getParameterBy("deliveryLocation").getValueAsString();
    //logger.info("delivery: " +delivery);
    
    long pickupTime = ((DateParameter) order.getParameterBy("pickupDate"))
			.getValue().longValue();
	long deliveryTime = ((DateParameter) order
			.getParameterBy("deliveryDate")).getValue().longValue();
    
   String orderedType = order.getParameterBy("truckType").getValueAsString();
	if (orderedType == null) {

		remarks.setValue("Parameter with id " + orderedType
				+ " is not found Order with id " + orderNetId);

		throw RSPMessages.newRspMsgEx("Parameter with id " + orderedType
				+ " is not found Order with id " + orderNetId);
	}

    
    Collection<Resource> RoutesMap = ResourceMap.getByType("Routes");
    
    Resource pickupRes = ResourceMap.getEntryBy(pickup);
    //logger.info("pickupLocation is not null");
    if (pickupRes == null){
    	remarks.setValue("Pickup Location with id " + pickup + " is not available in Routes");
      throw RSPMessages.newRspMsgEx("Pickup Location with id " + pickup + " is not available in Routes");
    }

    
    Resource deliveryRes = ResourceMap.getEntryBy(delivery);
    
    if (deliveryRes == null) {
    	remarks.setValue("Delivery Location with id " + delivery + " is not available in Routes");
      throw RSPMessages.newRspMsgEx("Delivery Location with id " + delivery + " is not available in Routes");
    }
    
    for (Resource resource : resourcesByType)
    {
    	
    	
    	String truckType = resource.getParameterBy("truckType").getValueAsString();
        String driverId = resource.getParameterBy("driverId").getValueAsString();
        String	CurrentLoc  =null;
        
        if(driverId.equalsIgnoreCase("TBA"))
         trucksWithoutDriver.add(resource);
        
        if (truckType.equalsIgnoreCase("single")) {
        	singleTruckRes.add(resource);
        }
        if (truckType.equalsIgnoreCase("rigid")) {
        	rigidTruckRes.add(resource);
        }
        if (truckType.equalsIgnoreCase("articulated")) {
        	articulatedTruckRes.add(resource);
        }
		List<Activity> prevTasks = resource.getActivitiesInInterval(
				Long.MIN_VALUE, Long.MAX_VALUE);

		if (prevTasks.size() < 1) {
			CurrentLoc = resource.getStateVariableBy("Location")
					.getValueAt(deliveryTime).getValueAsString();

			if (CurrentLoc == null)
				continue;

			
			logger.info("currentLocValue " + CurrentLoc);
		} else {
		
				String orderId = prevTasks.get(prevTasks.size()-1).getOrderNetId();
				Order prevOrder = RSPContainerHelper.getOrderMap(true)
						.getEntryBy(orderId);
				String OrderType = prevOrder.getType();
				logger.info("prevOrder->" + orderId);

				if (OrderType.equalsIgnoreCase("Maintenance")) {
					logger.info("It is a maintenance order");
					continue;
				}

				CurrentLoc = prevOrder.getParameterBy("deliveryLocation")
						.getValueAsString();
				
				if (prevOrder.getParameterBy("orderType")
						.getValueAsString().contains("Outstation")) {

					long prevOrderStartTime = prevTasks.get(prevTasks.size()-1).getStart() - 2 * 60000L;
					logger.info("prevOrderStartTime->"
							+ new Date(prevOrderStartTime));

					CurrentLoc = resource.getStateVariableBy(
							"Location").getValueAt(prevOrderStartTime).getValueAsString();
					
					
				}

			}
			

			if (CurrentLoc == null)
				continue;

			logger.info("currentLoc: " + CurrentLoc);
			
			
			Resource currentLocRes = (Resource) ResourceMap
					.getEntryBy(CurrentLoc);
			logger.info(resource.getId() + " CurrentLoc : " + currentLocRes);

			if (currentLocRes == null)
				continue;
			FloatListParameter currPickParam = (FloatListParameter) currentLocRes
					.getParameterBy(pickup);
			logger.info("currPickParam " + currPickParam);

			if (currPickParam == null)
				continue;

		
        	
    	
    	
    	if(truckType.equalsIgnoreCase(orderedTruckType))
    	trucksResIds.add(resource);
      
    }
    
    if(checkIfIdle(trucksResIds)<1 && orderedTruckType.equalsIgnoreCase("single")){
    	
    	trucksResIds.addAll(rigidTruckRes);
    	
    	if(checkIfIdle(trucksResIds)<1 )
    		trucksResIds.addAll(articulatedTruckRes);
    	
    	
    	
    }
    
if(checkIfIdle(trucksResIds)<1 && orderedTruckType.equalsIgnoreCase("rigid")){
    	
    
    		trucksResIds.addAll(articulatedTruckRes);
    	
    	
    	
    }
    
	logger.info("trucksResIds.size" + trucksResIds.size());
	if (trucksResIds.size() < 1) {
		try {

			remarks.setValue("Trucks with type: " + orderedType
					+ " are not available for the day. ");
			throw RSPMessages.newRspMsgEx("Trucks with type: " + orderedType
					+ " are not available. ");
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
			logger.info(truck.getId());
			StringParameter driverId = (StringParameter) truck
					.getParameterBy("driverId");
			String driver = driverId.getValueAsString();
			Resource assignedDriver = null;
			List<Activity> truckActlist = truck.getActivitiesInInterval(0, java.lang.Long.MAX_VALUE);
			if (truckActlist.size() == 0) {
			if (driver.equalsIgnoreCase("TBA")) {
				driversFromMaintenance = getPreferredDriverFromMaintenance(
						order, truck);
				if (driversFromMaintenance.size() > 0)
					assignedDriver = driversFromMaintenance.get(0);
				logger.info("assignedDriver: " + assignedDriver);

				while (true) {
					if (assignedDriver == null) {
						itr.remove();
						break;
					} else {

						if (!assignedDriversFromMaint
								.contains(assignedDriver)) {
							driverId.setValue(assignedDriver.getId());
							assignedDriversFromMaint.add(assignedDriver);
							// logger.info("assigned drivers  : " +
							// assignedDriversFromMaint + "new drivers " +
							// assignedDriver);
							break;
						}

						else {
							driversFromMaintenance.remove(0);
							if (driversFromMaintenance.size() > 0)
								assignedDriver = driversFromMaintenance
										.get(0);
							else
								assignedDriver = null;
						}
					}
				}
			}
		}
			else {
				Order prevOrder = RSPContainerHelper.getOrderMap(true).getEntryBy(truckActlist.get(0).getOrderNetId());
				((StringParameter)truck.getParameterBy("driverId")).setValue(prevOrder.getParameterBy("driverId").getValueAsString());
			}
		}

		logger.info("trucksResIds : " + trucksResIds.size());
		if (trucksResIds.size() < 1) {
			try {

				remarks.setValue("No drivers are present to drive the truck of ordered type : "
						+ orderedType + " for the day.");
				throw RSPMessages
						.newRspMsgEx("No drivers are present to drive the truck of ordered type : "
								+ orderedType + " for the day.");
			} catch (Exception e) {
				e.printStackTrace();
			}

		}
	}
    
    
    
    

    StringWriter resultWriter = new StringWriter();

    for (Resource re : trucksResIds) {
      resultWriter.append(re.getId());
      resultWriter.append(",");
    }

    String resultS = resultWriter.toString();
    int latestKommaPosition = resultS.lastIndexOf(",");
    if (latestKommaPosition >= 0){  
    resultS = resultS.substring(0, latestKommaPosition);
    StringParameter evaluatedTrucks = (StringParameter) order.getParameterBy("evaluatedTrucks");
    evaluatedTrucks.setValue(resultS);
    
    }
    else{
    remarks.setValue("Trucks with type: " +orderedTruckType+ "is not available.");
    throw  RSPMessages.newRspMsgEx("Trucks with type: " +orderedTruckType+ "is not available.");}

   
    
 

  

      StringParameter resultParameter = new StringParameter(this.descriptiveParameter.getId(), 
        this.descriptiveParameter.getName(), resultS.toString());

      resultParameter.setHidden(this.descriptiveParameter.isHidden());
      resultParameter.setUom(this.descriptiveParameter.getUom());
      resultParameter.setInterpretation(this.descriptiveParameter.getInterpretation());
      resultParameter.setSortIndex(this.descriptiveParameter.getSortIndex());

      resultParameter.setParent(getElement());
      resultParam = resultParameter;
    
    
    return resultParam;
  }

  public static HashMap sortByValues(HashMap map) { 
	     List list = new LinkedList(map.entrySet());
	     List<Object> keySet = new ArrayList<Object>();
	     List<Object> valueSet = new ArrayList<Object>();
	     // Defined Custom Comparator here
	     Collections.sort(list, new Comparator() {
	          public int compare(Object o1, Object o2) {
	             return ((Comparable) ((Map.Entry) (o1)).getValue())
	                .compareTo(((Map.Entry) (o2)).getValue());
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
  

	private List<Resource> getPreferredDriverFromMaintenance(Order order,
			Resource truck) {

		logger.info("DriverUnavailable");
		List<Resource> result = new ArrayList<Resource>();
		long pickupTime = (Long) order.getParameterBy("pickupDate").getValue();
		String truckMake = truck.getParameterBy("make").getValueAsString();
		List<Resource> maintenanceTrucks = new ArrayList<Resource>();

		Collection<Order> MaintenanceOrders = RSPContainerHelper.getOrderMap(
				true).getByType("Maintenance");

		for (Order o : MaintenanceOrders) {
			String truckNo = o.getParameterBy("truckNo").getValueAsString();
			logger.info("truckUnderMaintenance:" + truckNo);
			
			Resource truckRes = RSPContainerHelper.getResourceMap(true)
					.getEntryBy(truckNo);
			
			if(truckRes == null)
				continue;
			logger.info("truckno in maintenance" + truckRes.getId());
			String maintenanceTruckMake = truckRes.getParameterBy("make")
					.getValueAsString();
 if(truckMake.equalsIgnoreCase("OTHERS") && o.getState().equals(PlanningState.PLANNED))
	   maintenanceTrucks.add(truckRes);
 else    {
			if (maintenanceTruckMake.equalsIgnoreCase(truckMake) && o.getState().equals(PlanningState.PLANNED))
				maintenanceTrucks.add(truckRes);
             }
		}

		String truckType = truck.getParameterBy("truckType").getValueAsString();
		logger.info("truckType:" + truckType);

		List<Resource> idleRigidDrivers = new ArrayList<Resource>();
		List<Resource> idleArticulatedDrivers = new ArrayList<Resource>();
		List<Resource> idleSingleDrivers = new ArrayList<Resource>();

		for (Resource t : maintenanceTrucks) {
			StringParameter driver = (StringParameter) t
					.getParameterBy("driverId");
			String driverId = driver.getValueAsString();
			if (driverId.equalsIgnoreCase("TBA"))
				continue;

			Resource driverRes = RSPContainerHelper.getResourceMap(true)
					.getEntryBy(driverId);
			logger.info(driverRes);
			if (driverRes == null)
				continue;
			String skill = t.getParameterBy("truckType").getValueAsString();
			StateValue<?> driverCapacityState = driverRes.getStateVariableBy(
					"Capacity").getValueAt(pickupTime);
			logger.info("driverCapacityState: " + driverCapacityState);
			double driverCapacity = (Double) driverCapacityState.getValue();
			logger.info("driverCapacity: " + driverCapacity);

			if (driverCapacity == 0.0) {
				if (skill.equals("rigid"))
					idleRigidDrivers.add(driverRes);

				else if (skill.equals("articulated"))
					idleArticulatedDrivers.add(driverRes);

				else if (skill.equals("single"))
					idleSingleDrivers.add(driverRes);
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
			if (r.getActivityMap().size() < 1
					&& !r.getParameterBy("driverId").getValueAsString()
							.equalsIgnoreCase("TBA"))
				idleTrucks.add(r);
		}

		return idleTrucks.size();

	}
}