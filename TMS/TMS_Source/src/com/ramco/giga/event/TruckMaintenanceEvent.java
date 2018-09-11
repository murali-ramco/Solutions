package com.ramco.giga.event;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.rsp.core.base.FeatherliteAgent;
import com.rsp.core.base.RSPContainerHelper;
import com.rsp.core.base.model.Order;
import com.rsp.core.base.model.Workflow;
import com.rsp.core.base.model.constants.PlanningState;
import com.rsp.core.base.service.DBUploadService;
import com.rsp.core.base.transaction.RSPTransaction;
import com.rsp.core.control.command.EvictOrderToCreatedCommand;
import com.rsp.core.planning.command.ClearWorkflowCommand;

public class TruckMaintenanceEvent {

	protected Logger logger = Logger.getLogger(TruckMaintenanceEvent.class);

	public String doService(Connection connection, DBUploadService.Argument argDBUpload) throws SQLException, Exception {
		String result = null;

		result = removeTruckFromMaintenanceOrder(connection);

		return result;
	}

	private String removeTruckFromMaintenanceOrder(Connection connection) throws ParseException, SQLException {
		String result = "true";
		Statement stmt = null;
		ResultSet rs = null;
		String availableTruck = "select truckNo from ral_fleet_availability where avl_status = 'available' and ou = 3";
		List<String> listOfAvailableTruck = new ArrayList<String>();
		try {
			stmt = connection.createStatement();
			rs = stmt.executeQuery(availableTruck);
			while (rs.next()) {
				listOfAvailableTruck.add(rs.getString(1));
			}
		} catch (SQLException se) {
			se.printStackTrace();
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			try {
				if (stmt != null)
					connection.close();
			} catch (SQLException se) {
			}// do nothing
			try {
				if (connection != null)
					connection.close();
			} catch (SQLException se) {
				se.printStackTrace();
			}
		}

		try {
			List<Order> plannedMaintenanceOrdersList = (List<Order>) RSPContainerHelper.getOrderMap(true).getByType("Maintenance");
			ClearWorkflowCommand clearWorkflowCommand = new ClearWorkflowCommand();
			EvictOrderToCreatedCommand evictOrderCommand = new EvictOrderToCreatedCommand();

			if (plannedMaintenanceOrdersList.size() > 0 && listOfAvailableTruck.size() > 0) {
				for (Order order : plannedMaintenanceOrdersList) {
					if (listOfAvailableTruck.contains(order.getParameterBy("truckNo").getValueAsString()) && order.getState().equals(PlanningState.PLANNED)) {
						RSPTransaction tx = FeatherliteAgent.getTransaction(this);
						Workflow workflow = RSPContainerHelper.getWorkflowMap(true).getEntryBy(order.getId());
						clearWorkflowCommand.setWorkflow(workflow);
						evictOrderCommand.setOrder(order);
						tx.addCommand(clearWorkflowCommand);
						tx.addCommand(evictOrderCommand);
						tx.commit();
					}
				}

			}
		} catch (Exception e) {
			e.printStackTrace();
			return "false";
		}
		return result;
	}

}
