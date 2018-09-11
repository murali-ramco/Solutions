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
import java.util.InputMismatchException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.log4j.Logger;

public class resourceByEmptyRun extends LookupFormula
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
    
    for (Resource resource : resourcesByType)
    {
    	String truckType = resource.getParameterBy("truckType").getValueAsString();
    	if(truckType.equalsIgnoreCase(orderedTruckType))
    	trucksResIds.add(resource);
      
    }
    
    logger.info("List of Selected Resources-->" + trucksResIds + "size : " + trucksResIds.size());
    
    Collection<Order> imports  =  RSPContainerHelper.getOrderMap(true).getByType("KKCT");
    
    Collection<Resource> Routes =  RSPContainerHelper.getResourceMap(true).getByType("Routes");
    List<Order> imp = new ArrayList<Order>();
    
    for (Order o: imports){	
    	String pickupLoc = o.getParameterBy("pickupLocation").getValueAsString();
    	Resource p = RSPContainerHelper.getResourceMap(true).getEntryBy(pickupLoc);
    	
    	if(!Routes.contains(p)){
    //		imports.remove(o);
    	    continue;}
    	
    	
    	String deliveryLoc = o.getParameterBy("deliveryLocation").getValueAsString();
    	Resource d = RSPContainerHelper.getResourceMap(true).getEntryBy(deliveryLoc);

    	
    	if(!Routes.contains(d)){
  //  		imports.remove(o);
    	    continue;}
    	
   String orderType =  o.getParameterBy("orderType").getValueAsString();
   if(orderType.equalsIgnoreCase("Individual_Local_DLT"))
	   imp.add(o);
    }
    
    logger.info("imp"+imp);
    String pickupLoc = imp.get(0).getParameterBy("pickupLocation").getValueAsString();
    logger.info("pickupLoc->"+pickupLoc);
    List<Order> ordSeq = getOrderSequence(pickupLoc,imp);
    
    logger.info("imp->"+imp);

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

    Collection<Resource> routesMap = ResourceMap.getByType("routes");
   
    Resource pickupRes = ResourceMap.getEntryBy(pickup);
    //logger.info("pickupLocation is not null");
    if (!routesMap.contains(pickupRes)) {
    	remarks.setValue("Pickup Location with id " + pickup + " is not available in routes");
      throw RSPMessages.newRspMsgEx("Pickup Location with id " + pickup + " is not available in routes");
    }

    
    Resource deliveryRes = ResourceMap.getEntryBy(delivery);
    
    if (!routesMap.contains(deliveryRes)) {
    	remarks.setValue("Delivery Location with id " + delivery + " is not available in routes");
      throw RSPMessages.newRspMsgEx("Delivery Location with id " + delivery + " is not available in routes");
    }
    
    
 

  

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
	
}