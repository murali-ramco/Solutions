package com.ramco.giga.dbupload;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.ramco.giga.constant.GigaConstants;
import com.rsp.core.base.RSPContainerHelper;
import com.rsp.core.base.model.Order;
import com.rsp.core.base.model.Resource;
import com.rsp.core.base.model.Task;
import com.rsp.core.base.model.Workflow;
import com.rsp.core.base.model.constants.PlanningState;
import com.rsp.core.base.model.parameter.BooleanMapParameter;
import com.rsp.core.base.model.parameter.DateParameter;
import com.rsp.core.base.model.parameter.IntegerParameter;
import com.rsp.core.base.model.stateVariable.StateValue;
import com.rsp.core.base.model.stateVariable.StateVariable;
import com.rsp.core.base.model.stateVariable.StringState;
import com.rsp.core.base.service.DBUploadService;
import com.rsp.core.planning.constants.PlanningConstants;

public class GigaDBUpload {
    protected static Logger logger = Logger.getLogger(GigaDBUpload.class);
    private static final String TRUE = "true";
    private static final String FALSE = "false";

    public String doService(Connection connection, DBUploadService.Argument argDBUpload) throws SQLException, Exception {
	long totalTime = System.currentTimeMillis();
	String result = "false";
	try {
	    result = insertPlannedOrdersIntoDB(connection);
	    logger.info("Total time taken for DBupload is " + (System.currentTimeMillis() - totalTime) + " ms");
	} catch (Exception e) {
	    result = "DBupload failed due to " + e.getMessage();
	    logger.error(result);
	}
	return result;
    }

    public static void driverLoadUpdate(Connection conn, Order order) {
	String driverId = order.getParameterBy("driverId").getValueAsString();
	long orderPickDate = ((DateParameter) order.getParameterBy(GigaConstants.ORD_PARAM_PICKUP_DATE)).getValue();
	Resource driverRes = RSPContainerHelper.getResourceMap(true).getEntryBy(driverId);
	if (driverRes != null) {
	    int localOrderCount = ((IntegerParameter) driverRes.getParameterBy(GigaConstants.NO_OF_LOCAL_ORDERS_CD)).getValue();
	    int outstationOrderCount = ((IntegerParameter) driverRes.getParameterBy(GigaConstants.NO_OF_OUTSTATION_ORDERS_CD)).getValue();
	    int localOrderTotalCount = ((IntegerParameter) driverRes.getParameterBy(GigaConstants.NO_OF_LOCAL_ORDERS)).getValue();
	    int outstationOrderTotalCount = ((IntegerParameter) driverRes.getParameterBy(GigaConstants.NO_OF_OUTSTATION_ORDERS)).getValue();
	    String updateQuery = "update ral_driverLoad set TotalLocalOrders = ?, TotalOutstationOrders = ?, TotalOrders = ?, NoOfLocalOrders = ?, NoOfOutstationOrders = ? , ModifiedDate = ?, ModifiedBy = ? where DriverId = ? and CurrentDate = ?";
	    PreparedStatement preStmt = null;
	    CallableStatement calStmt = null;
	    try {
		/* Calendar cal = Calendar.getInstance();
		 cal.setTimeInMillis(orderPickDate);
		 java.util.Date today = cal.getTime();
		 SimpleDateFormat formatter = new SimpleDateFormat("dd-mm-yyyy hh.mm.ss");
		 String currentDate = formatter.format(today);
		 SimpleDateFormat sdf = new SimpleDateFormat("dd-mm-yyyy");
		 java.util.Date date = sdf.parse(currentDate);
		 preStmt = conn.prepareStatement(updateQuery);
		 preStmt.setInt(1, localOrderTotalCount);
		 preStmt.setInt(2, outstationOrderTotalCount);
		 preStmt.setInt(3, (localOrderTotalCount + outstationOrderTotalCount));
		 preStmt.setInt(4, localOrderCount);
		 preStmt.setInt(5, outstationOrderCount);
		 preStmt.setTimestamp(6, new java.sql.Timestamp(System.currentTimeMillis()));
		 preStmt.setString(7, "IPO");
		 preStmt.setString(8, driverId);
		 preStmt.setTimestamp(9, new Timestamp(date.getTime()));
		 preStmt.execute();*/
		calStmt = conn.prepareCall("exec ral_InsertDetailDriverLoad(?,?,?,?,?,?)");
		calStmt.setString(1, driverId);
		calStmt.setInt(2, localOrderTotalCount);
		calStmt.setInt(3, outstationOrderTotalCount);
		calStmt.setInt(4, (localOrderTotalCount + outstationOrderTotalCount));
		calStmt.setInt(5, localOrderCount);
		calStmt.setInt(6, outstationOrderCount);
		calStmt.execute();
		conn.commit();
	    } catch (Exception e) {
		e.printStackTrace();
	    } finally {
		try {
		    if (preStmt != null) {
			preStmt.close();
		    }
		    if (calStmt != null) {
			calStmt.close();
		    }
		} catch (Exception e) {
		    e.printStackTrace();
		}
	    }
	}
    }

    // remove state variable from the table
    public static void clearStateEntries(List<String> orderList, Connection connection) {
	logger.info("remove order from db started");
	PreparedStatement pst = null;
	try {
	    for (int i = 0; i < orderList.size(); i++) {
		pst = connection.prepareStatement("delete from ral_Order_StateVariable_Update where orderId = ?");
		pst.setString(1, orderList.get(i));
		pst.addBatch();
	    }
	    if (orderList.size() > 0)
		pst.executeBatch();
	    connection.commit();
	} catch (SQLException e) {
	    try {
		connection.rollback();
	    } catch (SQLException e1) {
		e1.printStackTrace();
	    }
	    e.printStackTrace();
	} finally {
	    try {
		if (pst != null)
		    pst.close();
	    } catch (Exception ex) {
	    }
	}
	logger.info("remove order from db end");
    }

    public static void changeStatus(Connection conn, String driverId, String truckId, String event) {
	PreparedStatement pst = null;
	logger.info("changeStatus started");
	try {
	    if (event.equalsIgnoreCase("driverLeaveEvent")) {
		pst = conn.prepareStatement("update DriverLeaveEvent set flag = 'C' where driverid = ? and flag = 'P'");
		pst.setString(1, driverId);
	    } else if (event.equalsIgnoreCase("truckLeaveEvent")) {
		pst = conn.prepareStatement("update TruckMaintenanceEvent set flag = 'C' where truckid = ? and flag = 'P'");
		pst.setString(1, truckId);
	    }
	    int x = pst.executeUpdate();
	    logger.info(x + " updated successfully");
	    logger.info("flag changed successfully");
	    conn.commit();
	} catch (Exception e) {
	    e.printStackTrace();
	} finally {
	    if (pst != null) {
		try {
		    pst.close();
		} catch (SQLException e) {
		    e.printStackTrace();
		} catch (Exception e2) {
		    e2.printStackTrace();
		}
	    }
	}
    }

    public static void changeOrderStatus(List<String> orderList, Connection connection) {
	PreparedStatement pst = null;
	clearStateEntries(orderList, connection);
	logger.info("changeOrderStatus started");
	try {
	    for (int i = 0; i < orderList.size(); i++) {
		pst = connection
			.prepareStatement("update ral_ipo_order_dtl set order_status = ?, tripid = ?,trip_Sequence_no = ?,preceding_DM = ?, succeeding_DM = ?, travel_duration = ?, loading_unloading_timebuffer = ?, rest_Waiting_timebuffer = ?, Base_location_StartTime = ?, truck_id = ?, driver_id = ?, ipo_process_flag = ?, evaluatedTrucks = ?, evaluatedODM = ?, est_pickuptime = ?, est_deliverytime = ?, est_TraveltimeinMins = ?, evalTravelDistance = ?, evalTravelDuration = ? where Business_Division = 'CR' and Orderid = ?");
		pst.setString(1, "unplanned");
		pst.setString(2, "");
		pst.setNull(3, java.sql.Types.INTEGER);
		pst.setNull(4, java.sql.Types.FLOAT);
		pst.setNull(5, java.sql.Types.FLOAT);
		pst.setNull(6, java.sql.Types.FLOAT);
		pst.setNull(7, java.sql.Types.FLOAT);
		pst.setNull(8, java.sql.Types.FLOAT);
		pst.setNull(9, java.sql.Types.DATE);
		pst.setString(10, "");
		pst.setString(11, "");
		pst.setString(12, "N");
		pst.setString(13, "");
		pst.setNull(14, java.sql.Types.CHAR);
		pst.setNull(15, java.sql.Types.CHAR);
		pst.setNull(16, java.sql.Types.CHAR);
		pst.setNull(17, java.sql.Types.FLOAT);
		pst.setNull(18, java.sql.Types.CHAR);
		pst.setNull(19, java.sql.Types.CHAR);
		pst.setString(20, orderList.get(i));
		pst.addBatch();
	    }
	    if (pst != null)
		pst.executeBatch();
	    /*for(int j=1; j<=count.length; j++){
	        System.out.println("Query "+ j +" has effected "+count[j]+" times");
	    }*/
	    connection.commit();
	} catch (SQLException e) {
	    e.printStackTrace();
	    try {
		connection.rollback();
	    } catch (SQLException e1) {
		e1.printStackTrace();
	    } catch (Exception e2) {
		e2.printStackTrace();
	    }
	} finally {
	    try {
		if (pst != null)
		    pst.close();
	    } catch (Exception ex) {
	    }
	}
	logger.info("changeOrderStatus end");
    }

    public static String insertPlannedOrdersIntoDB(Connection connection) throws ParseException, SQLException {
	String result = "false";
	PreparedStatement orderUpdatePS = null;
	PreparedStatement truckResUpdatePS = null;
	CallableStatement proc = null;
	PreparedStatement orderStateVariablePS = null;
	try {
	    //Map<String, String> customProperties = argDBUpload.customProperties;
	    List<Order> listOrder = RSPContainerHelper.getOrderMap(true).asList();
	    logger.info("orderSize : " + listOrder.size());
	    List<Order> PlanOrders = new ArrayList<Order>();
	    for (Order ord : listOrder) {
		if (!ord.getType().equalsIgnoreCase("Maintenance") && !ord.getType().equalsIgnoreCase("PlannedLeave") && !ord.getType().equalsIgnoreCase("UploadIntoDbScript"))
		    PlanOrders.add(ord);
	    }
	    logger.info("PlanOrders : " + PlanOrders.size());
	    deleteOrderProcessTable(connection);
	    for (Order o1 : PlanOrders) {
		//String Id = RSPContainerHelper.getUniqueId();
		String orderId = o1.getId();
		logger.info(orderId);
		Order order = (Order) RSPContainerHelper.getOrderMap(true).getEntryBy(orderId);
		IntegerParameter uploadStatus = (IntegerParameter) order.getParameterBy("uploadstatus");
		Integer DBstatus = uploadStatus.getValue();
		List<List<String>> truckOutput = new ArrayList<List<String>>();
		logger.info(order.getState());
		if (order.getState().equals(PlanningState.PLANNED) && DBstatus == 0) {
		    logger.info("planned");
		    driverLoadUpdate(connection, order);
		    logger.info(order.getId());
		    String orderList = order.getId();
		    String batchId = (String) order.getParameterBy("batchId").getValue();
		    logger.info("batchId : " + batchId);
		    Integer ou = (Integer) order.getParameterBy("plan_ou").getValue();
		    logger.info("ou : " + ou);
		    long orderDate = order.getDate();
		    logger.info("orderDate : " + orderDate);
		    Date OrdrDate = new Date(orderDate);
		    logger.info("OrdrDate : " + OrdrDate);
		    SimpleDateFormat df2 = new SimpleDateFormat("MM/dd/yyyy HH:mm:sss");
		    String OrderdateText = df2.format(OrdrDate);
		    logger.info("OrderdateText : " + OrderdateText);
		    long pickupTime = ((Long) order.getParameterBy("pickupDate").getValue()).longValue();
		    Date pickupDate = new Date(pickupTime);
		    String pickupDateText = df2.format(pickupDate);
		    logger.info("pickupDateText : " + pickupDateText);
		    long deliveryTime = ((Long) order.getParameterBy("deliveryDate").getValue()).longValue();
		    Date deliveryDate = new Date(deliveryTime);
		    String deliveryDateText = df2.format(deliveryDate);
		    logger.info("deliveryDateText : " + deliveryDateText);
		    String tripId = order.getParameterBy("tripId").getValueAsString();
		    logger.info("tripId : " + tripId);
		    String truckId = order.getParameterBy("truckId").getValueAsString();
		    logger.info("truckId : " + truckId);
		    Resource truckResource = RSPContainerHelper.getResourceMap(true).getEntryBy(truckId);
		    String driverId = order.getParameterBy("driverId").getValueAsString();
		    logger.info("driverId : " + driverId);
		    String evaluatedTrucks = order.getParameterBy("evaluatedTrucks").getValueAsString();
		    logger.info("evaluatedTrucks : " + evaluatedTrucks);
		    if (evaluatedTrucks == null)
			evaluatedTrucks = "";
		    String evaluatedODM_Hrs = order.getParameterBy("evaluatedODM_Hrs").getValueAsString();
		    logger.info("evaluatedODM_Hrs : " + evaluatedODM_Hrs);
		    if (evaluatedODM_Hrs == null)
			evaluatedODM_Hrs = "";
		    String estimatedTravelDuration = order.getParameterBy("estimatedTravelDuration").getValueAsString();
		    logger.info("estimatedTravelDuration : " + estimatedTravelDuration);
		    if (estimatedTravelDuration == null)
			estimatedTravelDuration = "";
		    String estimatedTravelDistance = order.getParameterBy("estimatedTravelDistance").getValueAsString();
		    logger.info("estimatedTravelDistance : " + estimatedTravelDistance);
		    if (estimatedTravelDistance == null)
			estimatedTravelDistance = "";
		    String BusinessDivision = order.getParameterBy("BD").getValueAsString();
		    logger.info("BusinessDivision : " + BusinessDivision);
		    String evaluatedODM_Km = order.getParameterBy("evaluatedODM_Km").getValueAsString();
		    logger.info("evaluatedODM_Km : " + evaluatedODM_Km);
		    if (evaluatedODM_Km == null)
			evaluatedODM_Km = "";
		    String estPickupTime = order.getParameterBy("estPickupTime").getValueAsString();
		    if (estPickupTime.contains("?"))
			estPickupTime = "";
		    logger.info("estPickupTime : " + estPickupTime);
		    String estDeliveryTime = order.getParameterBy("estDeliveryTime").getValueAsString();
		    if (estDeliveryTime.contains("?"))
			estDeliveryTime = "";
		    logger.info("estDeliveryTime : " + estDeliveryTime);
		    logger.info("workflow reached");
		    Workflow workflow = RSPContainerHelper.getWorkflowMap(true).getEntryBy(order.getId());
		    List<StateValue<StringState>> Start = ((StateVariable<StringState>) truckResource.getStateVariableBy("Location")).getValueTail(workflow.getStart());
		    String tripStartLocation = truckResource.getStateVariableBy("Location").getValueAt(workflow.getStart()).getValueAsString();
		    String tripEndLocation = truckResource.getStateVariableBy("Location").getValueAt(workflow.getEnd()).getValueAsString();
		    //	 Update of State Variable Table
		    logger.info("update start");
		    String tripStartTime = df2.format(new Date(workflow.getStart()));
		    String tripEndTime = df2.format(new Date(workflow.getEnd()));
		    String updateIntoOrderStateVariable = "Insert into ral_Order_StateVariable_Update (truck_id,Driver_id,truckType,TripStartTime,TripEndTime,PickupLocation,DeliveryLocation,Marshall_Name,orderId) values (?,?,?,?,?,?,?,?,?)";
		    logger.info("updateIntoOrderStateVariable:" + updateIntoOrderStateVariable);
		    orderStateVariablePS = connection.prepareStatement(updateIntoOrderStateVariable);
		    orderStateVariablePS.setString(1, truckId);
		    orderStateVariablePS.setString(2, driverId);
		    orderStateVariablePS.setString(3, truckResource.getParameterBy("truckType").getValueAsString());
		    orderStateVariablePS.setString(4, tripStartTime); //4. tripStartTime	
		    logger.info("tripStartTime" + tripStartTime);
		    logger.info("tripStartLocation" + tripStartLocation);
		    orderStateVariablePS.setString(6, tripStartLocation);
		    logger.info("OrderId: " + order.getId());
		    orderStateVariablePS.setString(9, order.getId());
		    logger.info("tripStartTime -" + tripStartTime + "tripStartLocation - " + tripStartLocation);
		    //if (!order.getParameterBy("orderType").getValueAsString().contains("Individual")) {
		    String businessDivision = order.getParameterBy(GigaConstants.ORD_PARAM_BUSINESS_DIVISION).getValueAsString();
		    boolean isOSDoneLocally = ((BooleanMapParameter) order.getParameterBy(GigaConstants.ORDER_PARAM_ISOSDONELOCALLY)).getValue().get(truckId);
		    if (isOSDoneLocally) {
			//if (order.getParameterBy("orderType").getValueAsString().contains("Local")) {
			logger.info("inside local orders");
			int match = 0;
			StateValue<StringState> pickStateVariable = Start.get(0);
			long pickUpTime = pickStateVariable.getTime();
			String currLocation = truckResource.getStateVariableBy("Location").getValueAt(workflow.getStart()).getValueAsString();
			String pickUpLocation = truckResource.getStateVariableBy("Location").getValueAt(pickUpTime).getValueAsString();
			String pickStartTime = df2.format(new Date(pickStateVariable.getTime()));
			if (currLocation.equalsIgnoreCase(pickUpLocation)) {
			    match = 1;
			    pickStartTime = tripStartTime;
			} else
			    match = 0;
			StateValue<StringState> loadingTimeStateVariable = Start.get(1 - match);
			String loadingTime = df2.format(new Date(loadingTimeStateVariable.getTime()));
			StateValue<StringState> delivryTimeStateVariable = Start.get(2 - match);
			String dvryTime = df2.format(new Date(delivryTimeStateVariable.getTime()));
			orderStateVariablePS.setString(5, pickStartTime); // trip end time --->>> pickup time
			logger.info("pickStartTime: " + pickStartTime);
			orderStateVariablePS.setString(7, pickUpLocation); // trip end location
			logger.info("pickUpLocation: " + pickUpLocation);
			orderStateVariablePS.setString(8, "Current Location to Pickup Location");
			//	orderStateVariablePS.setString(9, estimatedTravelDuration);
			orderStateVariablePS.addBatch();
			for (int i = 1; i < 3; i++) {
			    //		String updateIntoOrderStateVariable = "Insert into ral_Order_StateVariable_Update (truck_id,Driver_id,truckType,TripStartTime,TripEndTime,PickupLocation,DeliveryLocation,Marshall_Name,BaseLocation,evalTravelDuration) values (?,?,?,?,?,?,?,?,?,?)";
			    if (i == 1) {
				orderStateVariablePS.setString(1, truckId);
				logger.info("truckId " + truckId);
				orderStateVariablePS.setString(2, driverId);
				logger.info(" driverId" + driverId);
				orderStateVariablePS.setString(3, truckResource.getParameterBy("truckType").getValueAsString());
				orderStateVariablePS.setString(4, pickStartTime); // trip start time --->>> pick start time
				orderStateVariablePS.setString(5, loadingTime); // trip end time --->>> loading time
				orderStateVariablePS.setString(6, pickUpLocation);
				orderStateVariablePS.setString(7, pickUpLocation);
				logger.info("OrderId: " + order.getId());
				orderStateVariablePS.setString(9, order.getId());
				orderStateVariablePS.setString(8, "Loading at Pickup Location");
				//	orderStateVariablePS.setString(9, estimatedTravelDuration);
				orderStateVariablePS.addBatch();
			    } else if (i == 2) {
				orderStateVariablePS.setString(1, truckId);
				orderStateVariablePS.setString(2, driverId);
				orderStateVariablePS.setString(3, truckResource.getParameterBy("truckType").getValueAsString());
				orderStateVariablePS.setString(4, loadingTime); // trip start time --->>> loading time
				orderStateVariablePS.setString(5, dvryTime); // trip end time --->>> delivery time 
				orderStateVariablePS.setString(6, pickUpLocation);
				orderStateVariablePS.setString(7, tripEndLocation);
				logger.info("OrderId: " + order.getId());
				orderStateVariablePS.setString(9, order.getId());
				orderStateVariablePS.setString(8, "Pickup Location to Delivery Location");
				//orderStateVariablePS.setString(9, estimatedTravelDuration);
				orderStateVariablePS.addBatch();
			    }
			}
			orderStateVariablePS.setString(1, truckId);
			orderStateVariablePS.setString(2, driverId);
			orderStateVariablePS.setString(3, truckResource.getParameterBy("truckType").getValueAsString());
			orderStateVariablePS.setString(4, dvryTime); // trip start time --->>> delivery time
			orderStateVariablePS.setString(5, tripEndTime); // trip end time --->>> end of the trip
			orderStateVariablePS.setString(6, tripEndLocation);
			orderStateVariablePS.setString(7, tripEndLocation);
			logger.info("OrderId: " + order.getId());
			orderStateVariablePS.setString(9, order.getId());
			orderStateVariablePS.setString(8, "Unloading at Delivery Location");
			//	orderStateVariablePS.setString(9, estimatedTravelDuration);
			orderStateVariablePS.addBatch();
		    } else if (order.getParameterBy("jobType").getValueAsString().equalsIgnoreCase("Outstation")) {
			if (businessDivision.equalsIgnoreCase(GigaConstants.BUSINESS_DIVISION_WEST_MALAYSIA)) {
			    int match = 0;
			    logger.info("inside outstation");
			    StateValue<StringState> pickStateVariable = Start.get(0);
			    long pickUpTime = pickStateVariable.getTime();
			    logger.info("pickUpTime" + pickUpTime);
			    String currLocation = truckResource.getStateVariableBy("Location").getValueAt(workflow.getStart()).getValueAsString();
			    String pickUpLocation = truckResource.getStateVariableBy("Location").getValueAt(pickUpTime).getValueAsString();
			    String pickStartTime = df2.format(new Date(pickStateVariable.getTime())); // to be insert
			    if (currLocation.equalsIgnoreCase(pickUpLocation)) {
				match = 1;
				pickStartTime = tripStartTime;
			    } else
				match = 0;
			    logger.info("pickUpLocation" + pickUpLocation);
			    logger.info("pickStartTime" + pickStartTime);
			    orderStateVariablePS.setString(5, pickStartTime); // trip end time --->>> pickup time
			    logger.info("pickUpLocation" + pickUpLocation);
			    orderStateVariablePS.setString(7, pickUpLocation);
			    logger.info("OrderId: " + order.getId());
			    orderStateVariablePS.setString(9, order.getId());
			    orderStateVariablePS.setString(8, "Current Location to Pickup Location");
			    logger.info("estimatedTravelDuration" + estimatedTravelDuration);
			    //orderStateVariablePS.setString(9, estimatedTravelDuration);
			    orderStateVariablePS.addBatch();
			    StateValue<StringState> loadingTimeStateVariable = Start.get(1 - match);
			    String loadingTime = df2.format(new Date(loadingTimeStateVariable.getTime())); // to be insert
			    StateValue<StringState> base1StateVariable = Start.get(2 - match);
			    long base1time = base1StateVariable.getTime();
			    String base1Location = truckResource.getStateVariableBy("Location").getValueAt(base1time).getValueAsString();
			    String base1Time = df2.format(new Date(base1StateVariable.getTime())); // to be insert
			    //		String baseTestLocation = truckResource.getStateVariableBy("Location").getValueAt(pickStateVariable.getTime()).getValueAsString();
			    StateValue<StringState> base2StateVariable = Start.get(3 - match);
			    long base2time = base1StateVariable.getTime();
			    String base2Location = truckResource.getStateVariableBy("Location").getValueAt(base2time).getValueAsString();
			    String base2Time = df2.format(new Date(base2StateVariable.getTime()));
			    StateValue<StringState> base2ToDvryStateVariable = Start.get(4 - match);
			    String base2ToDvryTime = df2.format(new Date(base2ToDvryStateVariable.getTime())); // to be insert
			    for (int i = 1; i < 5; i++) {
				if (i == 1) {
				    orderStateVariablePS.setString(1, truckId);
				    orderStateVariablePS.setString(2, driverId);
				    orderStateVariablePS.setString(3, truckResource.getParameterBy("truckType").getValueAsString());
				    orderStateVariablePS.setString(4, pickStartTime); // trip start time --->>> pickup location time
				    orderStateVariablePS.setString(5, loadingTime); // trip end time --->>> loading time
				    orderStateVariablePS.setString(6, pickUpLocation);
				    orderStateVariablePS.setString(7, pickUpLocation);
				    logger.info("OrderId: " + order.getId());
				    orderStateVariablePS.setString(9, order.getId());
				    orderStateVariablePS.setString(8, "Loading at Pickup Location");
				    //	orderStateVariablePS.setString(9, estimatedTravelDuration);
				    orderStateVariablePS.addBatch();
				} else if (i == 2) {
				    orderStateVariablePS.setString(1, truckId);
				    orderStateVariablePS.setString(2, driverId);
				    orderStateVariablePS.setString(3, truckResource.getParameterBy("truckType").getValueAsString());
				    orderStateVariablePS.setString(4, loadingTime); // trip start time --->>> loading time
				    orderStateVariablePS.setString(5, base1Time); // trip end time --->>> base1time
				    orderStateVariablePS.setString(6, pickUpLocation);
				    orderStateVariablePS.setString(7, base1Location);
				    logger.info("OrderId: " + order.getId());
				    orderStateVariablePS.setString(9, order.getId());
				    orderStateVariablePS.setString(8, "Pickup Location to Resting Location");
				    //	orderStateVariablePS.setString(9, estimatedTravelDuration);
				    orderStateVariablePS.addBatch();
				} else if (i == 3) {
				    orderStateVariablePS.setString(1, truckId);
				    orderStateVariablePS.setString(2, driverId);
				    orderStateVariablePS.setString(3, truckResource.getParameterBy("truckType").getValueAsString());
				    orderStateVariablePS.setString(4, base1Time); // trip start time --->>> base1time
				    orderStateVariablePS.setString(5, base2Time); // trip end time --->>> delivery time
				    orderStateVariablePS.setString(6, base1Location);
				    orderStateVariablePS.setString(7, base2Location);
				    logger.info("OrderId: " + order.getId());
				    orderStateVariablePS.setString(9, order.getId());
				    orderStateVariablePS.setString(8, "Rest Hours");
				    //orderStateVariablePS.setString(9, estimatedTravelDuration);
				    orderStateVariablePS.addBatch();
				} else if (i == 4) {
				    orderStateVariablePS.setString(1, truckId);
				    orderStateVariablePS.setString(2, driverId);
				    orderStateVariablePS.setString(3, truckResource.getParameterBy("truckType").getValueAsString());
				    orderStateVariablePS.setString(4, base2Time); // trip start time --->>> base1time
				    orderStateVariablePS.setString(5, base2ToDvryTime); // trip end time --->>> delivery time
				    orderStateVariablePS.setString(6, base2Location);
				    orderStateVariablePS.setString(7, tripEndLocation);
				    logger.info("OrderId: " + order.getId());
				    orderStateVariablePS.setString(9, order.getId());
				    orderStateVariablePS.setString(8, "Resting Location to Delivery Location");
				    //orderStateVariablePS.setString(9, estimatedTravelDuration);
				    orderStateVariablePS.addBatch();
				}
			    }
			    orderStateVariablePS.setString(1, truckId);
			    orderStateVariablePS.setString(2, driverId);
			    orderStateVariablePS.setString(3, truckResource.getParameterBy("truckType").getValueAsString());
			    orderStateVariablePS.setString(4, base2ToDvryTime); // trip start time --->>> delivery time
			    orderStateVariablePS.setString(5, tripEndTime); // trip end time --->>> unload time
			    orderStateVariablePS.setString(6, tripEndLocation);
			    orderStateVariablePS.setString(7, tripEndLocation);
			    logger.info("OrderId: " + order.getId());
			    orderStateVariablePS.setString(9, order.getId());
			    orderStateVariablePS.setString(8, "Unloading at Delivery Location");
			    //orderStateVariablePS.setString(9, estimatedTravelDuration);
			    orderStateVariablePS.addBatch();
			} else if (businessDivision.equalsIgnoreCase(GigaConstants.BUSINESS_DIVISION_EAST_MALAYSIA)) {
			    logger.info("inside local orders");
			    int match = 0;
			    StateValue<StringState> pickStateVariable = Start.get(0);
			    long pickUpTime = pickStateVariable.getTime();
			    String currLocation = truckResource.getStateVariableBy("Location").getValueAt(workflow.getStart()).getValueAsString();
			    String pickUpLocation = truckResource.getStateVariableBy("Location").getValueAt(pickUpTime).getValueAsString();
			    String pickStartTime = df2.format(new Date(pickStateVariable.getTime()));
			    if (currLocation.equalsIgnoreCase(pickUpLocation)) {
				match = 1;
				pickStartTime = tripStartTime;
			    } else
				match = 0;
			    StateValue<StringState> loadingTimeStateVariable = Start.get(1 - match);
			    String loadingTime = df2.format(new Date(loadingTimeStateVariable.getTime()));
			    StateValue<StringState> delivryTimeStateVariable = Start.get(2 - match);
			    String dvryTime = df2.format(new Date(delivryTimeStateVariable.getTime()));
			    orderStateVariablePS.setString(5, pickStartTime); // trip end time --->>> pickup time
			    logger.info("pickStartTime: " + pickStartTime);
			    orderStateVariablePS.setString(7, pickUpLocation); // trip end location
			    logger.info("pickUpLocation: " + pickUpLocation);
			    orderStateVariablePS.setString(8, "Current Location to Pickup Location");
			    //	orderStateVariablePS.setString(9, estimatedTravelDuration);
			    orderStateVariablePS.addBatch();
			    for (int i = 1; i < 3; i++) {
				//		String updateIntoOrderStateVariable = "Insert into ral_Order_StateVariable_Update (truck_id,Driver_id,truckType,TripStartTime,TripEndTime,PickupLocation,DeliveryLocation,Marshall_Name,BaseLocation,evalTravelDuration) values (?,?,?,?,?,?,?,?,?,?)";
				if (i == 1) {
				    orderStateVariablePS.setString(1, truckId);
				    logger.info("truckId " + truckId);
				    orderStateVariablePS.setString(2, driverId);
				    logger.info(" driverId" + driverId);
				    orderStateVariablePS.setString(3, truckResource.getParameterBy("truckType").getValueAsString());
				    orderStateVariablePS.setString(4, pickStartTime); // trip start time --->>> pick start time
				    orderStateVariablePS.setString(5, loadingTime); // trip end time --->>> loading time
				    orderStateVariablePS.setString(6, pickUpLocation);
				    orderStateVariablePS.setString(7, pickUpLocation);
				    logger.info("OrderId: " + order.getId());
				    orderStateVariablePS.setString(9, order.getId());
				    orderStateVariablePS.setString(8, "Loading at Pickup Location");
				    //	orderStateVariablePS.setString(9, estimatedTravelDuration);
				    orderStateVariablePS.addBatch();
				} else if (i == 2) {
				    orderStateVariablePS.setString(1, truckId);
				    orderStateVariablePS.setString(2, driverId);
				    orderStateVariablePS.setString(3, truckResource.getParameterBy("truckType").getValueAsString());
				    orderStateVariablePS.setString(4, loadingTime); // trip start time --->>> loading time
				    orderStateVariablePS.setString(5, dvryTime); // trip end time --->>> delivery time 
				    orderStateVariablePS.setString(6, pickUpLocation);
				    orderStateVariablePS.setString(7, tripEndLocation);
				    logger.info("OrderId: " + order.getId());
				    orderStateVariablePS.setString(9, order.getId());
				    orderStateVariablePS.setString(8, "Pickup Location to Delivery Location");
				    //orderStateVariablePS.setString(9, estimatedTravelDuration);
				    orderStateVariablePS.addBatch();
				}
			    }
			    orderStateVariablePS.setString(1, truckId);
			    orderStateVariablePS.setString(2, driverId);
			    orderStateVariablePS.setString(3, truckResource.getParameterBy("truckType").getValueAsString());
			    orderStateVariablePS.setString(4, dvryTime); // trip start time --->>> delivery time
			    orderStateVariablePS.setString(5, tripEndTime); // trip end time --->>> end of the trip
			    orderStateVariablePS.setString(6, tripEndLocation);
			    orderStateVariablePS.setString(7, tripEndLocation);
			    logger.info("OrderId: " + order.getId());
			    orderStateVariablePS.setString(9, order.getId());
			    orderStateVariablePS.setString(8, "Unloading at Delivery Location");
			    //	orderStateVariablePS.setString(9, estimatedTravelDuration);
			    orderStateVariablePS.addBatch();
			}
		    }
		    //}
		    orderStateVariablePS.executeBatch();
		    String estPickupTimeDeparture = "?";
		    String estDeliveryTimeArrival = "?";
		    if (tripStartTime.contains("?"))
			tripStartTime = "";
		    if (!order.getParameterBy("orderType").getValueAsString().contains("Individual")) {
			if (order.getParameterBy("orderType").getValueAsString().contains("Local")) {
			    estPickupTimeDeparture = df2.format(new Date(Start.get(1).getTime()));
			    estDeliveryTimeArrival = df2.format(new Date(Start.get(2).getTime()));
			} else if (order.getParameterBy("orderType").getValueAsString().contains("Outstation")) {
			    {
				estPickupTimeDeparture = df2.format(new Date(Start.get(1).getTime()));
				estDeliveryTimeArrival = df2.format(new Date(Start.get(Start.size() - 2).getTime()));
			    }
			}
		    }
		    logger.info("===before estPickupTimeDeparture=====" + estPickupTimeDeparture);
		    logger.info("===before estDeliveryTimeArrival=====" + estDeliveryTimeArrival);
		    if (estPickupTimeDeparture.contains("?"))
			estPickupTimeDeparture = "";
		    if (estDeliveryTimeArrival.contains("?"))
			estDeliveryTimeArrival = "";
		    String estTravelTime = order.getParameterBy("estTravelTime").getValueAsString();
		    logger.info("estTravelTime : " + estTravelTime);
		    if (estTravelTime.contains("?"))
			estTravelTime = "0";
		    String marshal_id = "";
		    logger.info("marshal_id : " + marshal_id);
		    String marshal_name = "";
		    logger.info("marshal_name : " + marshal_name);
		    String remarks = " ";
		    if (order.getType().contains("KKCT") || order.getType().contains("CB")) {
			marshal_id = order.getParameterBy("Marshal_id").getValueAsString();
			logger.info("marshal_id2 : " + marshal_id);
			marshal_name = order.getParameterBy("Marshal_name").getValueAsString();
			logger.info("marshal_name : " + marshal_name);
		    } else {
			logger.info("before orderparam:");
		    }
		    logger.info("after orderparam:");
		    String preceding_DM = order.getParameterBy("preceding_DM").getValueAsString();
		    if (preceding_DM.contains("?")) {
			preceding_DM = "0";
		    }
		    logger.info("preceding_DM : " + preceding_DM);
		    String succeeding_DM = order.getParameterBy("succeeding_DM").getValueAsString();
		    if (succeeding_DM.contains("?")) {
			succeeding_DM = "0";
		    }
		    logger.info("succeeding_DM : " + succeeding_DM);
		    String travel_Duration = order.getParameterBy("travel_Duration").getValueAsString();
		    if (travel_Duration.contains("?")) {
			travel_Duration = "0";
		    }
		    logger.info("travel_Duration : " + travel_Duration);
		    String loading_unloading_timeBuffer = order.getParameterBy("loading_unloading_timeBuffer").getValueAsString();
		    if (loading_unloading_timeBuffer.contains("?")) {
			loading_unloading_timeBuffer = "0";
		    }
		    logger.info("loading_unloading_timeBuffer : " + loading_unloading_timeBuffer);
		    String rest_Waiting_timeBuffer = order.getParameterBy("rest_Waiting_timeBuffer").getValueAsString();
		    if (rest_Waiting_timeBuffer.contains("?")) {
			rest_Waiting_timeBuffer = "0";
		    }
		    logger.info("rest_Waiting_timeBuffer : " + rest_Waiting_timeBuffer);
		    String base_location_StartTime = order.getParameterBy("base_location_StartTime").getValueAsString();
		    logger.info("base_location_StartTime : " + base_location_StartTime);
		    if (base_location_StartTime.contains("?")) {
			base_location_StartTime = "";
		    }
		    logger.info("base_location_StartTime : " + base_location_StartTime);
		    String updateOrderQuery = "Insert into ral_apo_order_outputs_tmp (batchid,ou,orderId,orderDate,pickuptime,deliverytime,tripid,truckid,driverid,evaluatedTrucks,evaluatedODM_Hrs,estimatedTravelDuration,estimatedTravelDistance,BusinessDivision,estTraveltimeinmins,estPickupTime,estDeliveryTime,evaluatedODM_Km,Marshal_id,Marshal_name,planning_log,preceding_DM,succeeding_DM,travel_duration,loading_unloading_timebuffer,rest_Waiting_timebuffer,Base_location_StartTime,tripStartLocation,tripStartTime,estPickupTimeDeparture,estDeliveryTimeArrival,ipo_remarks)Values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
		    orderUpdatePS = connection.prepareStatement(updateOrderQuery);
		    orderUpdatePS.setString(1, batchId);
		    orderUpdatePS.setInt(2, ou.intValue());
		    orderUpdatePS.setString(3, orderId);
		    logger.info("orderDate-->" + OrderdateText);
		    orderUpdatePS.setString(4, OrderdateText);
		    orderUpdatePS.setString(5, pickupDateText);
		    logger.info("pickupTime-->" + pickupDateText);
		    orderUpdatePS.setString(6, deliveryDateText);
		    orderUpdatePS.setString(7, tripId);
		    orderUpdatePS.setString(8, truckId);
		    orderUpdatePS.setString(9, driverId);
		    orderUpdatePS.setString(10, evaluatedTrucks);
		    logger.info("estimatedTravelDuration-->" + estimatedTravelDuration);
		    orderUpdatePS.setString(11, evaluatedODM_Hrs);
		    orderUpdatePS.setString(12, estimatedTravelDuration);
		    orderUpdatePS.setString(13, estimatedTravelDistance);
		    logger.info("estimatedTravelDistance-->" + estimatedTravelDistance);
		    orderUpdatePS.setString(14, BusinessDivision);
		    logger.info("BusinessDivision-->" + BusinessDivision);
		    orderUpdatePS.setString(15, estTravelTime);
		    logger.info("estTravelTime-->" + estTravelTime);
		    orderUpdatePS.setString(16, estPickupTime);
		    orderUpdatePS.setString(17, estDeliveryTime);
		    orderUpdatePS.setString(18, evaluatedODM_Km);
		    orderUpdatePS.setString(19, marshal_id);
		    orderUpdatePS.setString(20, marshal_name);
		    orderUpdatePS.setString(21, remarks);
		    logger.info("remarks-->" + remarks);
		    orderUpdatePS.setString(22, preceding_DM);
		    logger.info("preceding_DM-->" + preceding_DM);
		    orderUpdatePS.setString(23, succeeding_DM);
		    logger.info("succeeding_DM-->" + succeeding_DM);
		    orderUpdatePS.setString(24, travel_Duration);
		    logger.info("travel_Duration-->" + travel_Duration);
		    orderUpdatePS.setString(25, loading_unloading_timeBuffer);
		    logger.info("loading_unloading_timeBuffer-->" + loading_unloading_timeBuffer);
		    orderUpdatePS.setString(26, rest_Waiting_timeBuffer);
		    logger.info("rest_Waiting_timeBuffer-->" + rest_Waiting_timeBuffer);
		    orderUpdatePS.setString(27, base_location_StartTime);
		    logger.info("base_location_StartTime-->" + base_location_StartTime);
		    orderUpdatePS.setString(28, tripStartLocation);
		    logger.info("tripStartLocation-->" + tripStartLocation);
		    orderUpdatePS.setString(29, tripStartTime);
		    logger.info("tripStartTime-->" + tripStartTime);
		    orderUpdatePS.setString(30, estPickupTimeDeparture);
		    logger.info("estPickupTimeDeparture-->" + estPickupTimeDeparture);
		    orderUpdatePS.setString(31, estDeliveryTimeArrival);
		    logger.info("estDeliveryTimeArrival-->" + estDeliveryTimeArrival);
		    String ipoRemarks = order.getParameterBy(PlanningConstants.SCRIPT_SELECTION_KEY).getValueAsString();
		    String prevDriverId = null;
		    String prevDriverName = null;
		    String prevTruckId = null;
		    String prevTruckRegNo = null;
		    if (order.hasParameter(GigaConstants.ORDER_PARAM_PREV_DRIVER_ID)) {
			prevDriverId = order.getParameterBy(GigaConstants.ORDER_PARAM_PREV_DRIVER_ID).getValueAsString();
			Resource prevDriverRes = RSPContainerHelper.getResourceMap(true).getEntryBy(prevDriverId);
			if (prevDriverRes != null)
			    prevDriverName = prevDriverRes.getName();
		    }
		    if (order.hasParameter(GigaConstants.ORDER_PARAM_PREV_TRUCK_ID)) {
			prevTruckId = order.getParameterBy(GigaConstants.ORDER_PARAM_PREV_TRUCK_ID).getValueAsString();
			Resource prevTruckRes = RSPContainerHelper.getResourceMap(true).getEntryBy(prevTruckId);
			if (prevTruckRes != null)
			    prevTruckRegNo = prevTruckRes.getParameterBy(GigaConstants.RES_PARAM_TRUCK_REG_NO).getValueAsString();
		    }
		    if (ipoRemarks.equalsIgnoreCase(GigaConstants.SCRIPT_REASSIGNMANUALLY)) {
			ipoRemarks = "Manually reassigned - [Previous driver ID: " + prevDriverId + "(" + prevDriverName + ")] [Previous Truck ID: " + prevTruckId + "(" + prevTruckRegNo + ")]";
		    }
		    orderUpdatePS.setString(32, ipoRemarks);
		    logger.info("ipoRemarks-->" + ipoRemarks);
		    orderUpdatePS.execute();
		    logger.info("Completed");
		    uploadStatus.setValue(1);
		    if (order.getType().equalsIgnoreCase("CR")) {
			Workflow wf = (Workflow) RSPContainerHelper.getWorkflowMap(true).getEntryBy(orderId);
			Workflow chooseReswf = (Workflow) wf.getElementBy("chooseRes");
			Task selectTruck = (Task) chooseReswf.getElementBy("selectTruck");
			if (selectTruck == null)
			    return "Task with id selectTruck not found in workflow with id chooseRes.";
			Resource re = selectTruck.getResource();
			StringState stateVariable = (StringState) re.getStateVariableBy("Location");
			String LocationId;
			for (StateValue<String> stringValue : stateVariable.getValues()) {
			    ArrayList<String> updateTruckInfo = new ArrayList<String>();
			    LocationId = (String) stringValue.getValue();
			    logger.info("locationId :" + LocationId);
			    long plannedDate = stringValue.getTime();
			    Date planDate = new Date(plannedDate);
			    String plannedDateText = df2.format(planDate);
			    updateTruckInfo.add(batchId);
			    updateTruckInfo.add(ou.toString());
			    updateTruckInfo.add(re.getId());
			    updateTruckInfo.add(plannedDateText);
			    updateTruckInfo.add(LocationId);
			    updateTruckInfo.add(re.getParameterBy("BD").getValueAsString());
			    truckOutput.add(updateTruckInfo);
			}
			String updateTruckQuery = "Insert into ral_apo_truck_outputs_tmp (batchid,ou,TruckId,PlannedDate,LocationId,BusinessDivision)Values (?,?,?,?,?,?)";
			truckResUpdatePS = connection.prepareStatement(updateTruckQuery);
			for (List<String> effect : truckOutput) {
			    List<String> listE = effect;
			    truckResUpdatePS.setObject(1, listE.get(0));
			    truckResUpdatePS.setObject(2, listE.get(1));
			    truckResUpdatePS.setObject(3, listE.get(2));
			    truckResUpdatePS.setObject(4, listE.get(3));
			    truckResUpdatePS.setObject(5, listE.get(4));
			    truckResUpdatePS.setObject(6, listE.get(5));
			    truckResUpdatePS.addBatch();
			}
			truckResUpdatePS.executeBatch();
		    }
		} else if (order.getState().equals(PlanningState.CREATED)) {
		    logger.info("unplanned");
		    String batchid = order.getParameterBy("batchId").getValueAsString();
		    String remarks = order.getParameterBy("remarks").getValueAsString();
		    String evaluatedTrucks = order.getParameterBy("evaluatedTrucks").getValueAsString();
		    if (order.getType().equalsIgnoreCase("KKCT") && remarks.contains("?"))
			remarks = "All trucks are occupied.";
		    if (order.getType().equalsIgnoreCase("IndividualOrders")) {
			remarks = "There are no feasible trucks to fulfill this order.";
		    }
		    String updateOrderQuery = "Insert into ral_apo_order_outputs_tmp (batchid,orderId,evaluatedTrucks,planning_log)Values (?,?,?,?)";
		    orderUpdatePS = connection.prepareStatement(updateOrderQuery);
		    orderUpdatePS.setString(1, batchid);
		    orderUpdatePS.setString(2, orderId);
		    orderUpdatePS.setString(3, evaluatedTrucks);
		    orderUpdatePS.setString(4, remarks);
		    orderUpdatePS.execute();
		}
		connection.commit();
	    }
	    /*proc = connection.prepareCall("{call RAL_SP_OUTPUTS_PROCESSING}");
	    proc.execute();*/
	    updateOrderTable(connection);
	    updateTripSequence(connection);
	    updateSucceedingDeadMileage(connection);
	    updateUnplannedOrderLog(connection);
	    connection.commit();
	    result = "true";
	} catch (Exception e) {
	    e.printStackTrace();
	    result = "DBupload failed due to " + e.getMessage();
	    logger.error(result);
	} finally {
	    if (orderUpdatePS != null) {
		orderUpdatePS.close();
		orderUpdatePS = null;
	    }
	    if (truckResUpdatePS != null) {
		truckResUpdatePS.close();
		truckResUpdatePS = null;
	    }
	    if (orderStateVariablePS != null) {
		orderStateVariablePS.close();
		orderStateVariablePS = null;
	    }
	}
	return result;
    }

    public static String updateOrderTable(Connection con) throws SQLException {
	PreparedStatement orderUpdatePS = null;
	String result = TRUE;
	try {
	    StringBuilder sb = new StringBuilder();
	    sb.append("update tmp ");
	    sb.append("set tmp.order_Status = 'Planned', ");
	    sb.append("tmp.truck_id    = t.truckid, ");
	    sb.append("tmp.Driver_id   = t.driverid, ");
	    sb.append("tmp.tripid      = t.truckid + '_'+ 'trip_01'+  '_'+ replace(convert(Varchar(10),getdate(),120),'-','') , ");
	    sb.append("tmp.est_pickuptime = t.estPickupTime, ");
	    sb.append("tmp.est_deliverytime = t.estDeliveryTime, ");
	    sb.append("tmp.est_TraveltimeinMins = isnull(t.estTraveltimeinMins,0), ");
	    sb.append("tmp.evaluatedTrucks = t.evaluatedTrucks, ");
	    sb.append("tmp.evaluatedODM = t.evaluatedODM_Km, ");
	    sb.append("tmp.evalTravelDistance = t.estimatedTravelDistance, ");
	    sb.append("tmp.evalTravelDuration =  t.estimatedTravelDuration, ");
	    sb.append("tmp.IPO_Process_flag = 'Y' , ");
	    sb.append("tmp.Marshal_id =  t.Marshal_id, ");
	    sb.append("tmp.Marshal_Name=  t.Marshal_Name, ");
	    sb.append("tmp.planning_log = t.planning_log , ");
	    sb.append("tmp.preceding_DM = t.preceding_DM , ");
	    sb.append("tmp.succeeding_DM = t.succeeding_DM, ");
	    sb.append("tmp.travel_duration = t.travel_duration, ");
	    sb.append("tmp.loading_unloading_timebuffer = t.loading_unloading_timebuffer , ");
	    sb.append("tmp.rest_Waiting_timebuffer = t.rest_Waiting_timebuffer, ");
	    sb.append("tmp.Base_location_StartTime = t.Base_location_StartTime, ");
	    sb.append("tmp.tripStartLocation = t.tripStartLocation, ");
	    sb.append("tmp.tripStartTime =t.tripStartTime, ");
	    sb.append("tmp.estPickupTimeDeparture =t.estPickupTimeDeparture, ");
	    sb.append("tmp.estDeliveryTimeArrival =t.estDeliveryTimeArrival, ");
	    sb.append("tmp.IPO_remarks =t.ipo_remarks, ");
	    sb.append("tmp.ordertime = getdate() ");
	    sb.append("from   ral_ipo_order_dtl tmp with (rowlock) , ");
	    sb.append("ral_apo_order_outputs_tmp t ( nolock ) ");
	    sb.append("where  t.orderId    = tmp.Orderid ");
	    sb.append("AND    LEN(isnull(nullif(T.truckid,''),'!'))  > 1 ");
	    sb.append("AND    len(isnull(nullif(t.driverid,''),'!')) > 1 ");
	    String updateOrderQuery = sb.toString();
	    orderUpdatePS = con.prepareStatement(updateOrderQuery);
	    orderUpdatePS.execute();
	} catch (Exception e) {
	    e.printStackTrace();
	    result = "Update Order Table failed due to " + e.getMessage();
	    logger.error(result);
	    return result;
	} finally {
	    if (orderUpdatePS != null) {
		orderUpdatePS.close();
		orderUpdatePS = null;
	    }
	}
	return TRUE;
    }

    public static String updateTripSequence(Connection con) throws SQLException {
	PreparedStatement orderUpdatePS = null;
	String result = TRUE;
	try {
	    StringBuilder sb = new StringBuilder();
	    sb.append("update tmp  ");
	    sb.append("set tmp.trip_Sequence_no = x.sequence ");
	    sb.append("from    ral_ipo_order_dtl tmp with (rowlock) ,  ");
	    sb.append("( select orderId, tripId, rank() over(Partition by t3.truck_id  order by t3.est_pickuptime, t3.est_Deliverytime) 'Sequence'  ");
	    sb.append("from   ral_ipo_order_dtl t3 ( nolock )  ");
	    sb.append("where  1 = 1  ");
	    sb.append("and    t3.order_Status = 'Planned' ");
	    sb.append("and    nullif(t3.tripid,'') is not null  ");
	    sb.append("and    exists ( select 'x' from ral_apo_order_outputs_tmp t4 ( nolock ) ,  ");
	    sb.append("ral_ipo_order_Dtl t5 ( nolock )  ");
	    sb.append("where   t4.orderId = t5.orderId  ");
	    sb.append("and convert(Varchar(10),t3.orderDate,120) = convert(varchar(10),t5.orderDate,120) ");
	    sb.append("AND    LEN(isnull(nullif(T4.truckid,''),'!'))  > 1  ");
	    sb.append("AND    len(isnull(nullif(t4.driverid,''),'!')) > 1 )) x  ");
	    sb.append("where   tmp.order_status = 'Planned' ");
	    sb.append("and     nullif(tmp.tripid,'') is not null  ");
	    sb.append("and     tmp.orderId = x.orderId  ");
	    sb.append("and     tmp.tripId  = x.tripId  ");
	    sb.append("and     exists ( select 'x' from ral_apo_order_outputs_tmp t ( nolock ) ,  ");
	    sb.append("ral_ipo_order_Dtl t1 ( nolock )  ");
	    sb.append("where   t.orderId = t1.orderId  ");
	    sb.append("and   convert(Varchar(10),tmp.orderDate,120) = convert(varchar(10),t1.orderDate,120)  ");
	    sb.append("AND    LEN(isnull(nullif(T.truckid,''),'!'))  > 1  ");
	    sb.append("AND    len(isnull(nullif(t.driverid,''),'!')) > 1 )  ");
	    String updateOrderQuery = sb.toString();
	    orderUpdatePS = con.prepareStatement(updateOrderQuery);
	    orderUpdatePS.execute();
	} catch (Exception e) {
	    e.printStackTrace();
	    result = "Update Trip Sequence failed due to " + e.getMessage();
	    logger.error(result);
	    return result;
	} finally {
	    if (orderUpdatePS != null) {
		orderUpdatePS.close();
		orderUpdatePS = null;
	    }
	}
	return TRUE;
    }

    public static String updateSucceedingDeadMileage(Connection con) throws SQLException {
	PreparedStatement orderUpdatePS = null;
	String result = TRUE;
	try {
	    StringBuilder sb = new StringBuilder();
	    sb.append("update tmp  ");
	    sb.append("set tmp.succeeding_DM = 0.0 ");
	    sb.append("from    ral_ipo_order_dtl tmp with (rowlock)   ");
	    sb.append("where   tmp.order_status = 'Planned' ");
	    sb.append("and nullif(tmp.tripid,'') is not null  ");
	    sb.append("and     exists ( select 'x' from ral_apo_order_outputs_tmp t ( nolock ) ,  ");
	    sb.append("ral_ipo_order_Dtl t1 ( nolock )  ");
	    sb.append("where   t.orderId = t1.orderId  ");
	    sb.append("and   convert(Varchar(10),tmp.orderDate,120) = convert(varchar(10),t1.orderDate,120)  ");
	    sb.append("AND    LEN(isnull(nullif(T.truckid,''),'!'))  > 1  ");
	    sb.append("AND    len(isnull(nullif(t.driverid,''),'!')) > 1  ");
	    sb.append(")  ");
	    sb.append("and     tmp.trip_Sequence_no < isnull(( select max(h.trip_Sequence_no)  ");
	    sb.append("from ral_ipo_order_Dtl h ( nolock )  ");
	    sb.append("where h.tripid = tmp.tripid  ");
	    sb.append("and   h.orderdate = tmp.orderdate  ");
	    sb.append("and   h.truck_id  = tmp.truck_id ");
	    sb.append("),1) ");
	    String updateOrderQuery = sb.toString();
	    orderUpdatePS = con.prepareStatement(updateOrderQuery);
	    orderUpdatePS.execute();
	} catch (Exception e) {
	    e.printStackTrace();
	    result = "update Succeeding Dead Mileage failed due to " + e.getMessage();
	    logger.error(result);
	    return result;
	} finally {
	    if (orderUpdatePS != null) {
		orderUpdatePS.close();
		orderUpdatePS = null;
	    }
	}
	return TRUE;
    }

    public static String updateUnplannedOrderLog(Connection con) throws SQLException {
	PreparedStatement orderUpdatePS = null;
	String result = TRUE;
	try {
	    StringBuilder sb = new StringBuilder();
	    sb.append("update tmp  ");
	    sb.append("set  tmp.order_Status = 'Unplanned',  ");
	    sb.append("tmp.IPO_Process_flag = 'N' , ");
	    sb.append("tmp.evaluatedTrucks = t.evaluatedTrucks, ");
	    sb.append("tmp.planning_log = isnull(isnull(nullif(nullif(t.planning_log,'?'),''),'System could not allocate the Load - All Feasible Trucks are occupied') + '| Evaluated Trucks: ' + convert(varchar(128),isnull(nullif(t.evaluatedTrucks,'?'),'')),'Error in Planing - Trucks Unavailable, Check Planning Logs')  ");
	    sb.append(",tmp.ordertime = getdate()  ");
	    sb.append("from   ral_ipo_order_dtl tmp with (rowlock) ,  ");
	    sb.append("ral_apo_order_outputs_tmp t ( nolock )  ");
	    sb.append("where  tmp.order_status = 'Unplanned'  ");
	    sb.append("and    t.orderId    = tmp.Orderid 	 ");
	    sb.append("AND    LEN(isnull(nullif(T.truckid,''),'!')) <= 1  ");
	    sb.append("AND    len(isnull(nullif(t.driverid,''),'!')) <= 1  ");
	    String updateOrderQuery = sb.toString();
	    orderUpdatePS = con.prepareStatement(updateOrderQuery);
	    orderUpdatePS.execute();
	} catch (Exception e) {
	    e.printStackTrace();
	    result = "update Succeeding Dead Mileage failed due to " + e.getMessage();
	    logger.error(result);
	    return result;
	} finally {
	    if (orderUpdatePS != null) {
		orderUpdatePS.close();
		orderUpdatePS = null;
	    }
	}
	return TRUE;
    }

    public static String deleteOrderProcessTable(Connection con) throws SQLException {
	PreparedStatement orderUpdatePS = null;
	String result = TRUE;
	try {
	    String updateOrderQuery = "delete from ral_apo_order_outputs_tmp";
	    orderUpdatePS = con.prepareStatement(updateOrderQuery);
	    orderUpdatePS.execute();
	} catch (Exception e) {
	    e.printStackTrace();
	    result = "Delete Order Process Table failed due to " + e.getMessage();
	    logger.error(result);
	    return result;
	} finally {
	    if (orderUpdatePS != null) {
		orderUpdatePS.close();
		orderUpdatePS = null;
	    }
	}
	return TRUE;
    }
}