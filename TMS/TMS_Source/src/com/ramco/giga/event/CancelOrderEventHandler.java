package com.ramco.giga.event;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.ramco.giga.dbupload.GigaDBUpload;
import com.ramco.giga.utils.GigaUtils;
import com.rsp.core.base.RSPContainerHelper;
import com.rsp.core.base.encrypt.PasswordEncrypter;
import com.rsp.core.base.model.Order;
import com.rsp.core.planning.service.EventService;

public class CancelOrderEventHandler {
    private final static Logger logger = Logger.getLogger(CancelOrderEventHandler.class);

    public String doService(EventService.Argument argument) {
	String result = "true";
	Connection conn = null;
	String driverId = null;
	String truckId = null;
	String orderId = null;
	Order order = argument.order;
	if (order == null) {
	    return "order argument is null";
	}
	try {
	    Class.forName(argument.jdbcDriver);
	    conn = DriverManager.getConnection(argument.jdbcUrl, argument.jdbcUserId, PasswordEncrypter.decrypt(argument.jdbcPassword));
	    conn.setAutoCommit(false);
	    if (order != null) {
		if (order.hasParameter("orderId")) {
		    orderId = order.getParameterBy("orderId").getValueAsString();
		} else {
		    return "orderId parameter is missing in the event parameter list.";
		}
		logger.info("order id: " + orderId);
		if (order.hasParameter("driverId")) {
		    driverId = order.getParameterBy("driverId").getValueAsString();
		} else {
		    return "driverId parameter is missing in the event parameter list.";
		}
		if (order.hasParameter("truckId")) {
		    truckId = order.getParameterBy("truckId").getValueAsString();
		} else {
		    return "truckId parameter is missing in the event parameter list.";
		}
	    }
	    Order cancelOrder = RSPContainerHelper.getOrderMap(true).getEntryBy(orderId);
	    if (cancelOrder != null)
		cancelEvent(cancelOrder, truckId, driverId, conn);
	} catch (Exception e) {
	    e.printStackTrace();
	} finally {
	    if (conn != null) {
		try {
		    conn.close();
		    conn = null;
		} catch (Exception e) {
		    e.printStackTrace();
		}
	    }
	}
	return result;
    }

    private void cancelEvent(Order order, String truckId, String driverId, Connection conn) {
	try {
	    List<String> orderList = new ArrayList<String>();
	    orderList.add(order.getId());
	    GigaDBUpload.clearStateEntries(orderList, conn);
	    GigaUtils.unplanOrders(order);
	    GigaUtils.clearDriverLoadStatus(order);
	    GigaUtils.evictOrder(order);
	    GigaUtils.removeOrder(order);
	    changeOrderStatus(orderList, conn);
	} catch (Exception e) {
	    e.printStackTrace();
	    logger.info("Exception in Cancel Evevt" + e.getMessage());
	}
    }

    public static void changeOrderStatus(List<String> orderList, Connection connection) {
	PreparedStatement pst = null;
	logger.info("changeOrderStatus started");
	try {
	    for (int i = 0; i < orderList.size(); i++) {
		pst = connection
			.prepareStatement("update ral_ipo_order_dtl set order_status = ?, tripid = ?,trip_Sequence_no = ?,preceding_DM = ?, succeeding_DM = ?, travel_duration = ?, loading_unloading_timebuffer = ?, rest_Waiting_timebuffer = ?, Base_location_StartTime = ?, truck_id = ?, driver_id = ?, ipo_process_flag = ?, evaluatedTrucks = ?, evaluatedODM = ?, est_pickuptime = ?, est_deliverytime = ?, est_TraveltimeinMins = ?, evalTravelDistance = ?, evalTravelDuration = ? where Business_Division = 'CR' and Orderid = ?");
		pst.setString(1, "cancelled");
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
	    pst.executeBatch();
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
}
