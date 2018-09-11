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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.log4j.Logger;

public class TrucksByType_MB extends LookupFormula
{
  private static final long serialVersionUID = 1L;

  @SuppressWarnings("unused")
public Parameter<?> evaluate()
    throws RSPException
  {
    StringParameter resultParam = null;
    String truckType = null;
    String args = this.argument;
   
    truckType = args;
   

    ElementMap<Resource> ResourceMap = RSPContainerHelper.getResourceMap(true);
    Collection<Resource> resourcesByType = ResourceMap.getByType(truckType);
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

    String pickup = order.getParameterBy("pickupLocation").getValueAsString();
    //logger.info("pickup: " +pickup);
    String delivery = order.getParameterBy("deliveryLocation").getValueAsString();
    //logger.info("delivery: " +delivery);

    for (Resource resource : resourcesByType)
    {
    	trucksResIds.add(resource);
      
    }

    logger.info("List of Selected Resources-->" + trucksResIds + "size : " + trucksResIds.size());
  

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
      remarks.setValue("Trucks with type: " +truckType+ "is not available.");
      throw  RSPMessages.newRspMsgEx("Trucks with type: " +truckType+ "is not available.");}
      
    Collection<Resource> RoutesMap = ResourceMap.getByType("Routes");
   
    Resource pickupRes = ResourceMap.getEntryBy(pickup);
    //logger.info("pickupLocation is not null");
    if (!RoutesMap.contains(pickupRes)) {
    	remarks.setValue("Pickup Location with id " + pickup + " is not available in Routes");
      throw RSPMessages.newRspMsgEx("Pickup Location with id " + pickup + " is not available in Routes");
    }

    
    Resource deliveryRes = ResourceMap.getEntryBy(delivery);
    
    if (!RoutesMap.contains(deliveryRes)) {
    	remarks.setValue("Delivery Location with id " + delivery + " is not available in Routes");
      throw RSPMessages.newRspMsgEx("Delivery Location with id " + delivery + " is not available in Routes");
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
}