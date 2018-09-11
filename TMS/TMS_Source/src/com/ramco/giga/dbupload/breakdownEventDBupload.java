package com.ramco.giga.dbupload;

import com.rsp.core.base.RSPContainerHelper;
import com.rsp.core.base.model.ElementMap;
import com.rsp.core.base.model.Order;
import com.rsp.core.base.model.Resource;
import com.rsp.core.base.model.Task;
import com.rsp.core.base.model.Workflow;
import com.rsp.core.base.model.constants.PlanningState;
import com.rsp.core.base.model.parameter.Parameter;
import com.rsp.core.base.model.parameter.StringParameter;
import com.rsp.core.base.model.stateVariable.StateValue;
import com.rsp.core.base.model.stateVariable.StringState;
import com.rsp.core.base.service.DBUploadService;
import com.rsp.core.base.service.DBUploadService.Argument;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;

public class breakdownEventDBupload
{
  protected Logger logger = Logger.getLogger(breakdownEventDBupload.class);

  private final String TRUE = "true";

  private final String FALSE = "false";

  public String doService(Connection connection, DBUploadService.Argument argDBUpload)
    throws SQLException, Exception
  {
    long totalTime = System.currentTimeMillis();
    String result = "false";
    try
    {
      result = insertPlannedOrdersIntoDB(connection, argDBUpload);
      this.logger.info("Total time taken for DBupload is " + (
        System.currentTimeMillis() - totalTime) + " ms");
    } catch (Exception e) {
      result = "DBupload failed due to " + e.getMessage();
      this.logger.error(result);
    }
    return result;
  }

  private String insertPlannedOrdersIntoDB(Connection connection, DBUploadService.Argument argDBUpload) throws ParseException, SQLException
  {
    String result = "false";
    PreparedStatement orderUpdatePS = null;
    PreparedStatement truckResUpdatePS = null;
    CallableStatement proc = null;
    try
    {
      Map customProperties = argDBUpload.customProperties;

      List<Order> listOrder = RSPContainerHelper.getOrderMap(true).asList();
      this.logger.info("orderSize : " + listOrder.size());
      
      List<Order> breakDownOrders = new ArrayList<Order>();
     
      
      for (Order ord : listOrder){
    	  if (ord.getParameterBy("remarks").getValueAsString().equalsIgnoreCase("breakdown"))
    	  
    		  breakDownOrders.add(ord);
      }
      
      for (Order o1 : breakDownOrders)
      {
        String Id = RSPContainerHelper.getUniqueId();
        String orderId = o1.getId();
        logger.info(orderId);
        Order order = (Order)RSPContainerHelper.getOrderMap(true).getEntryBy(orderId);
        List<List<String>> truckOutput = new ArrayList<List<String>>();
        this.logger.info(order.getState());

          this.logger.info("breakdown");

          String batchId = "truck breakDown";
          logger.info("batchId : "+batchId);
          
          Integer ou = (Integer)order.getParameterBy("plan_ou").getValue();
          logger.info("ou : "+ou);
          
          long orderDate = order.getDate();
          logger.info("orderDate : "+orderDate);
          
          Date OrdrDate = new Date(orderDate);
          logger.info("OrdrDate : "+OrdrDate);
          
          SimpleDateFormat df2 = new SimpleDateFormat("MM/dd/yyyy HH:mm:sss");
          String OrderdateText = df2.format(OrdrDate);
          logger.info("OrderdateText : "+OrderdateText);
          
          long pickupTime = ((Long)order.getParameterBy("pickupDate").getValue()).longValue();
          Date pickupDate = new Date(pickupTime);
          String pickupDateText = df2.format(pickupDate);
          logger.info("pickupDateText : "+pickupDateText);
          
          long deliveryTime = ((Long)order.getParameterBy("deliveryDate").getValue()).longValue();
          Date deliveryDate = new Date(deliveryTime);
          String deliveryDateText = df2.format(deliveryDate);
          logger.info("deliveryDateText : "+deliveryDateText);
          
          String tripId = order.getParameterBy("tripId").getValueAsString();
          logger.info("tripId : "+tripId);
          
          String truckId = order.getParameterBy("truckId").getValueAsString();
          logger.info("truckId : "+truckId);
          
          String driverId = order.getParameterBy("driverId").getValueAsString();
          logger.info("driverId : "+driverId);
          
          String evaluatedTrucks = order.getParameterBy("evaluatedTrucks").getValueAsString();
          logger.info("evaluatedTrucks : "+evaluatedTrucks);
         
          String evaluatedODM_Hrs = order.getParameterBy("evaluatedODM_Hrs").getValueAsString();
          logger.info("evaluatedODM_Hrs : "+evaluatedODM_Hrs);
          
          String estimatedTravelDuration = order.getParameterBy("estimatedTravelDuration").getValueAsString();
          logger.info("estimatedTravelDuration : "+estimatedTravelDuration);
          
          String estimatedTravelDistance = order.getParameterBy("estimatedTravelDistance").getValueAsString();
          logger.info("estimatedTravelDistance : "+estimatedTravelDistance);
          
          String BusinessDivision = order.getParameterBy("BD").getValueAsString();
          logger.info("BusinessDivision : "+BusinessDivision);
          
          String evaluatedODM_Km = order.getParameterBy("evaluatedODM_Km").getValueAsString();
          logger.info("evaluatedODM_Km : "+evaluatedODM_Km);
          
          String estPickupTime =  order.getParameterBy("estPickupTime").getValueAsString();
          logger.info("estPickupTime : "+estPickupTime);
          
          String estDeliveryTime = order.getParameterBy("estDeliveryTime").getValueAsString();
          logger.info("estDeliveryTime : "+estDeliveryTime);
          
          String estTravelTime = order.getParameterBy("estTravelTime").getValueAsString();
          logger.info("estTravelTime : "+estTravelTime);
          
         String marshal_id = order.getParameterBy("Marshal_id").getValueAsString();
          logger.info("marshal_id : "+marshal_id);
          
          
         String marshal_name = order.getParameterBy("Marshal_name").getValueAsString();
          logger.info("marshal_name : "+marshal_name);
          
          String remarks = "";
          
          String preceding_DM = order.getParameterBy("preceding_DM").getValueAsString();
          logger.info("preceding_DM : "+preceding_DM);
          
          String succeeding_DM = order.getParameterBy("succeeding_DM").getValueAsString();
          logger.info("succeeding_DM : "+succeeding_DM);
          
          String travel_Duration = order.getParameterBy("travel_Duration").getValueAsString();
          logger.info("travel_Duration : "+travel_Duration);
          
          String loading_unloading_timeBuffer = order.getParameterBy("loading_unloading_timeBuffer").getValueAsString();
          logger.info("loading_unloading_timeBuffer : "+loading_unloading_timeBuffer);
          
          String rest_Waiting_timeBuffer = order.getParameterBy("rest_Waiting_timeBuffer").getValueAsString();
          logger.info("rest_Waiting_timeBuffer : "+rest_Waiting_timeBuffer);
          
          String base_location_StartTime = order.getParameterBy("base_location_StartTime").getValueAsString();
          logger.info("base_location_StartTime : "+base_location_StartTime);
          
          String pickuplocation = order.getParameterBy("pickupLocation").getValueAsString();
          logger.info("after orderparam:");
          
          String updateOrderQuery = "Insert into ral_apo_breakdown_outputs_tmp (batchid,ou,orderId,orderDate,pickuptime,deliverytime,tripid,truckid,driverid,evaluatedTrucks,evaluatedODM_Hrs,estimatedTravelDuration,estimatedTravelDistance,BusinessDivision,estTraveltimeinmins,estPickupTime,estDeliveryTime,evaluatedODM_Km,Marshal_id,Marshal_name,planning_log,preceding_DM,succeeding_DM,travel_duration,loading_unloading_timebuffer,rest_Waiting_timebuffer,Base_location_StartTime,pickuplocation)Values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

          orderUpdatePS = connection.prepareStatement(updateOrderQuery);
          orderUpdatePS.setString(1, batchId);
          orderUpdatePS.setInt(2, ou.intValue());
          orderUpdatePS.setString(3, orderId);
          this.logger.info("orderDate-->" + OrderdateText);
          orderUpdatePS.setString(4, OrderdateText);
          orderUpdatePS.setString(5, pickupDateText);
          this.logger.info("pickupTime-->" + pickupDateText);
          orderUpdatePS.setString(6, deliveryDateText);
          orderUpdatePS.setString(7, tripId);
          orderUpdatePS.setString(8, truckId);
          orderUpdatePS.setString(9, driverId);
          orderUpdatePS.setString(10, evaluatedTrucks);
          this.logger.info("estimatedTravelDuration-->" + estimatedTravelDuration);
          orderUpdatePS.setString(11, evaluatedODM_Hrs);
          orderUpdatePS.setString(12, estimatedTravelDuration);
          orderUpdatePS.setString(13, estimatedTravelDistance);
          this.logger.info("estimatedTravelDistance-->" + estimatedTravelDistance);
          orderUpdatePS.setString(14, BusinessDivision);
          this.logger.info("BusinessDivision-->" + BusinessDivision);
          orderUpdatePS.setString(15, estTravelTime);
          this.logger.info("estTravelTime-->" + estTravelTime);
          orderUpdatePS.setString(16, estPickupTime);
          orderUpdatePS.setString(17, estDeliveryTime);
          orderUpdatePS.setString(18, evaluatedODM_Km);
          orderUpdatePS.setString(19, marshal_id);
          orderUpdatePS.setString(20, marshal_name);
          orderUpdatePS.setString(21, remarks);
          this.logger.info("remarks-->" + remarks);
          orderUpdatePS.setString(22, preceding_DM);
          this.logger.info("preceding_DM-->" + preceding_DM);
          orderUpdatePS.setString(23, succeeding_DM);
          this.logger.info("succeeding_DM-->" + succeeding_DM);
          orderUpdatePS.setString(24, travel_Duration);
          this.logger.info("travel_Duration-->" + travel_Duration);
          orderUpdatePS.setString(25, loading_unloading_timeBuffer);
          this.logger.info("loading_unloading_timeBuffer-->" + loading_unloading_timeBuffer);
          orderUpdatePS.setString(26, rest_Waiting_timeBuffer);
          this.logger.info("rest_Waiting_timeBuffer-->" + rest_Waiting_timeBuffer);
          orderUpdatePS.setString(27, base_location_StartTime);
          this.logger.info("base_location_StartTime-->" + base_location_StartTime);
          orderUpdatePS.setString(28, pickuplocation);
          this.logger.info("pickuplocation-->" + pickuplocation);
          orderUpdatePS.execute();
          this.logger.info("Girst Completed");  
        
        }
      

      proc = connection.prepareCall("{call ral_sp_breakdown_outputs_processing}");
      proc.execute();
      logger.info("eventDBupload done");

      connection.commit();

      result = "true";
    }
    catch (Exception e) {
      e.printStackTrace();
      result = "DBupload failed due to " + e.getMessage();
      this.logger.error(result);
    } finally {
      if (orderUpdatePS != null) {
        orderUpdatePS.close();
        orderUpdatePS = null;
      }
      if (truckResUpdatePS != null) {
        truckResUpdatePS.close();
        truckResUpdatePS = null;
      }
    }

    return result;
  }
}