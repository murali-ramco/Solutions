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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.log4j.Logger;

public class TrucksByPreference extends LookupFormula
{
  private static final long serialVersionUID = 1L;

  @SuppressWarnings("unused")
public Parameter<?> evaluate()
    throws RSPException
  {
    StringParameter resultParam = null;
    String orderedType = null;
    String truckType = null;
    String resMake = null;
    String[] args = this.argument.split(",");
    String resType = null;
    String orderParam = null;
    String resParam = null;

    if (args.length >= 3)
    {
      resType = args[0].trim();
      orderParam = args[1].trim();
      resParam = args[2].trim();
    }
    else
    {
      DataModelException e = new DataModelException("Malformed argument $argument for formula $className", 
        "datamodel.formulaArgument");
      e.addProperty("argument", this.argument);
      e.addProperty("className", getClass().getSimpleName());
      throw e;
    }
    if (orderParam.startsWith("$"))
    {
      orderParam = getKeyFromParameter(orderParam);
    }

    ElementMap<Resource> ResourceMap = RSPContainerHelper.getResourceMap(true);
    Collection<Resource> resourcesByType = ResourceMap.getByType(resType);
    List<Resource> trucksResIds = new ArrayList();

    ParameterizedElement tmp = getElement();
    String orderNetId = null;
    while (tmp != null) {
      orderNetId = tmp.getId();
      tmp = tmp.getParent();
    }

    Order order = (Order)RSPContainerHelper.getOrderMap(true).getEntryBy(orderNetId);
    //logger.info("orderNetId: " + orderNetId);

    StringParameter remarks = (StringParameter) order.getParameterBy("remarks");
    
    long pickupTime = ((DateParameter)order.getParameterBy("pickupDate")).getValue().longValue();
    long deliveryTime = ((DateParameter)order.getParameterBy("deliveryDate")).getValue().longValue();
    
    //logger.info("pickupTime "+pickupTime);

    orderedType = order.getParameterBy("truckType").getValueAsString();
    if (orderedType == null) {
    	
    	remarks.setValue("Parameter with id " + orderedType + " is not found in Order with id " + orderNetId);
    	
      throw RSPMessages.newRspMsgEx("Parameter with id " + orderedType + " is not found in Order with id " + orderNetId);
    }

    String pickup = order.getParameterBy("pickupLocation").getValueAsString();
    //logger.info("pickup: " +pickup);
    String delivery = order.getParameterBy("deliveryLocation").getValueAsString();
    //logger.info("delivery: " +delivery);
   
    Resource pickupLocation = (Resource)ResourceMap.getEntryBy(pickup);
    //logger.info("pickupLocation is not null");
    if (pickupLocation == null) {
    	remarks.setValue("Location with id " + pickup + " is not available in Routes");
      throw RSPMessages.newRspMsgEx("Location with id " + pickup + " is not available in Routes");
    }

    FloatListParameter deliveryLoc = (FloatListParameter) pickupLocation.getParameterBy(delivery);
    //logger.info("deliveryLoc is not null");
    if (deliveryLoc == null) {
    	
    	remarks.setValue("Parameter with id " + delivery + " is not found in  Location with id " + pickup);
        throw RSPMessages.newRspMsgEx("Parameter with id " + delivery + " is not found in  Location with id " + pickup);
      }
    String[] pickupToDelivery = deliveryLoc.getValueAsString().split(",");
    //logger.info("pickupToDelivery is not null");

    double tripDur = Double.parseDouble(pickupToDelivery[1]);
    //logger.info("tripDur: "+tripDur );
    long PickupToDelivery = (long)tripDur * 1000L;
    
    List<Resource> availArticulatedTrucks = new ArrayList<Resource>();
    List<Resource> availRigidTrucks = new ArrayList<Resource>();

    for (Resource resource : resourcesByType)
    {
      truckType = resource.getParameterBy("truckType").getValueAsString();
      resMake = resource.getParameterBy(resParam).getValueAsString();

      String CurrentLoc = resource.getStateVariableBy("Location").getValueAt(deliveryTime).getValueAsString();
      logger.info("CurrentLoc : "+CurrentLoc);
      
      List<Activity> currentActivities = resource.getActivitiesAt(deliveryTime);
      
      if (currentActivities.size() < 1){
      Resource CurrentLocRes = (Resource)ResourceMap.getEntryBy(CurrentLoc);
      
      if(CurrentLocRes != null){
      String[] CurrLocMatrixValues = CurrentLocRes.getParameterBy(pickup).getValueAsString().split(",");
      double currToPickup = Double.parseDouble(CurrLocMatrixValues[1]);
      long currToPickupDur = (long)currToPickup * 1000L;

      long lowerD = deliveryTime - PickupToDelivery - currToPickupDur;
      long upperD = deliveryTime;

      FloatState CapacityState = (FloatState)resource.getStateVariableBy("Capacity");

      List<Activity> Activities = resource.getActivitiesInInterval(lowerD, upperD);

      if (Activities.size() < 1)
      {
    	  if(truckType.equalsIgnoreCase("articulated"))
    		  availArticulatedTrucks.add(resource);
    	  }
    		  
        double CapacityVal = ((Double)CapacityState.getValueAt(deliveryTime).getValue()).doubleValue();

        if (CapacityState == null) {
        	
          throw RSPMessages.newRspMsgEx("SateVariable with id " + CapacityState + " is not found Resource with id " + resource.getId());
        }

        if ((CapacityVal < ((Double)CapacityState.getUpperBound()).doubleValue()) && (truckType.contains(orderedType)) && (resMake.contains(orderParam)) && (!orderParam.equals("others")))
        {
          trucksResIds.add(resource);
        }
        else if ((CapacityVal < ((Double)CapacityState.getUpperBound()).doubleValue()) && (truckType.contains(orderedType)) && (orderParam.equals("others") && (!resMake.equals("ISUZU"))))
        {
          trucksResIds.add(resource);
        }

      
      }
      }
      
      
    }

    Collection<Order> Orders_CR = (Collection<Order>) RSPContainerHelper.getOrderMap(true).getByType("CR");
    
    List<Order> unplannedIsuzuOrders = new ArrayList<Order>();  
    List<Resource> IsuzuTrucks = new ArrayList<Resource>();
    List<Resource> idleTrucks = new ArrayList<Resource>();
    
    for(Resource r : availArticulatedTrucks){
    	if(r.getParameterBy("make").getValueAsString().equalsIgnoreCase("ISUZU"))
    		IsuzuTrucks.add(r);	
    	
    	if(r.getActivityMap().size() < 1)
    		idleTrucks.add(r);
    }
    	
    logger.info("IsuzuTrucks-> "+IsuzuTrucks.size());
    logger.info("idleTrucks-> "+idleTrucks);
    
    
    
    for(Order o : Orders_CR){
    	
    	if(o.getParameterBy("make").getValueAsString().equalsIgnoreCase("ISUZU") && o.getState().equals(PlanningState.CREATED))
    		unplannedIsuzuOrders.add(o);
    }
    
    logger.info("unplannedIsuzuOrders-> "+unplannedIsuzuOrders.size());
   
    if(orderedType.equalsIgnoreCase("articulated") && !orderParam.equals("ISUZU") && IsuzuTrucks.size() > unplannedIsuzuOrders.size()){
    	trucksResIds = availArticulatedTrucks;
    }
    
    if(trucksResIds.size() < 1 && orderedType.equalsIgnoreCase("rigid") && !orderParam.equals("ISUZU")){
    	trucksResIds = availArticulatedTrucks;
    }
    	
    

    if (trucksResIds.size() < 1) {
      try {
    	  
    	 remarks.setValue("Trucks with make ID: " +orderParam+ " and type: "+orderedType+ " is not available for the day. ") ;
        throw  RSPMessages.newRspMsgEx("Trucks with make ID: " +orderParam+ " and type: "+orderedType+ " is not available. ");
      }
      catch (Exception e) {
        e.printStackTrace();
      }

    }

    logger.info("trucks feasible->"+trucksResIds);
    HashMap resDur = new HashMap();
    Date date = new Date(pickupTime);
    SimpleDateFormat df2 = new SimpleDateFormat("MM/dd/yyyy");
    String dateText = df2.format(date);
    StringWriter Writer = new StringWriter();
    Writer.append(dateText);
    Writer.append(" 08:00:00");
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
    try
    {
      Date date1 = simpleDateFormat.parse(Writer.toString());

      long lower = date1.getTime();
      long upper = deliveryTime;
      Resource CurrentLocRes;
      for (Resource res : trucksResIds)
      {
        String CurrentLoc = res.getStateVariableBy("Location").getValueAt(deliveryTime).getValueAsString();
        CurrentLocRes = (Resource)ResourceMap.getEntryBy(CurrentLoc);
        String[] LocMatrixValues = CurrentLocRes.getParameterBy(pickup).getValueAsString().split(",");
        double nextPickupDur = Double.parseDouble(LocMatrixValues[1]);

        resDur.put(res, Double.valueOf(nextPickupDur));
      }

      HashMap<Resource,Double> sortedResDur = sortByValues(resDur);
      Set set = sortedResDur.entrySet();
      Iterator iterator = set.iterator();

      while (iterator.hasNext()) {
        
        Map.Entry me = (Map.Entry)iterator.next();
      }

      List<Resource> ResIds = new ArrayList(sortedResDur.keySet());
      //logger.info("sortedRes :" + ResIds);

      StringWriter resultWriter = new StringWriter();

      for (Resource re : ResIds) {
        resultWriter.append(re.getId());
        resultWriter.append(",");
      }

      String resultS = resultWriter.toString();
      
      int latestKommaPosition = resultS.lastIndexOf(",");
      
      if (latestKommaPosition >= 0){  
      resultS = resultS.substring(0, latestKommaPosition);
      StringParameter evaluatedTrucks = (StringParameter) order.getParameterBy("evaluatedTrucks");
      evaluatedTrucks.setValue(resultS);
      //logger.info("evaluatedTrucks"+evaluatedTrucks);
      }
      else{
      remarks.setValue("Trucks with make ID: " +orderParam+ "is not available.");
      throw  RSPMessages.newRspMsgEx("Trucks with make ID: " +orderParam+ " is not available.");}

      StringParameter resultParameter = new StringParameter(this.descriptiveParameter.getId(), 
        this.descriptiveParameter.getName(), resultS.toString());

      resultParameter.setHidden(this.descriptiveParameter.isHidden());
      resultParameter.setUom(this.descriptiveParameter.getUom());
      resultParameter.setInterpretation(this.descriptiveParameter.getInterpretation());
      resultParameter.setSortIndex(this.descriptiveParameter.getSortIndex());

      resultParameter.setParent(getElement());
      resultParam = resultParameter;
    }
    catch (ParseException e) {
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
}