package com.ramco.giga.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import com.rsp.core.base.FeatherliteAgent;
import com.rsp.core.base.RSPContainerHelper;
import com.rsp.core.base.model.ElementMap;
import com.rsp.core.base.model.Order;
import com.rsp.core.base.model.Resource;
import com.rsp.core.base.model.Workflow;
import com.rsp.core.base.model.constants.PlanningState;
import com.rsp.core.base.model.constants.ProcessingState;
import com.rsp.core.base.model.parameter.IntegerParameter;
import com.rsp.core.base.service.ServiceArgument;
import com.rsp.core.base.service.ServiceResult;
import com.rsp.core.base.service.SimpleServiceResult;
import com.rsp.core.base.transaction.RSPTransaction;
import com.rsp.core.control.command.EvictOrderToCreatedCommand;
import com.rsp.core.planning.command.ClearWorkflowCommand;
import com.rsp.core.planning.command.PlanOrderCommand;
import com.rsp.core.planning.service.PlanOrderListService;

public class LoadBalanceService extends PlanOrderListService {
    @Override
    public ServiceResult doService(ServiceArgument argument) throws Exception {
	try {
	    Argument arg = (Argument) argument;
	    ElementMap<Resource> resourceMap = RSPContainerHelper.getResourceMap(true);
	    ElementMap<Order> orderMap = RSPContainerHelper.getOrderMap(true);
	    //updateInMemoryDriverLoad(resourceMap);
	    updateLoadBalanceInMemory();
	    Collection<Order> localOneOrders = orderMap.getByType("CR_1_Local");
	    Collection<Order> outStnOneOrders = orderMap.getByType("CR_1_Outstation");
	    Collection<Order> localTwoOrders = orderMap.getByType("CR_2_Local");
	    Collection<Order> outStnTwoOrders = orderMap.getByType("CR_2_Outstation");
	    List<Order> orders = new ArrayList<Order>(localOneOrders);
	    orders.addAll(outStnOneOrders);
	    orders.addAll(localTwoOrders);
	    orders.addAll(outStnTwoOrders);
	    /*for (Order order : orders) {
	    planOrder(order, this);
	    }*/
	    clearOrderList(orders, this);
	    planOrderList(orders, this);
	    //updateFileDriverLoadCount(resourceMap);
	    updateLoadBalanceTable();
	    insertIntoDriverLoadDetail(arg.className);
	} catch (Exception e) {
	    logger.info("======Inside 123 exception" + e.getMessage());
	    // return new SimpleServiceResult(ServiceResult.ERROR, e.getMessage());
	} finally {
	}
	return new SimpleServiceResult(ServiceResult.SUCCESS);
    }

    public static void planOrder(Order order, LoadBalanceService currentInst) {
	try {
	    RSPTransaction tx = FeatherliteAgent.getTransaction(currentInst);
	    PlanOrderCommand planCmd = new PlanOrderCommand();
	    planCmd.setOrder(order);
	    tx.addCommand(planCmd);
	    tx.commit();
	} catch (Exception e) {
	    logger.info("======Inside plan order exception" + e.getMessage());
	} finally {
	}
    }

    public static void planOrderList(List<Order> orderList, LoadBalanceService currentInst) {
	try {
	    for (Order order : orderList) {
		try {
		    RSPTransaction tx = FeatherliteAgent.getTransaction(currentInst);
		    PlanOrderCommand planCmd = new PlanOrderCommand();
		    planCmd.setOrder(order);
		    tx.addCommand(planCmd);
		    tx.commit();
		} catch (Exception e) {
		    logger.info("======Inside plan order exception" + e.getMessage());
		}
	    }
	} catch (Exception e) {
	    logger.info("======Inside plan order exception" + e.getMessage());
	} finally {
	}
    }

    public static void clearOrderList(List<Order> orderList, LoadBalanceService currentInst) {
	try {
	    RSPTransaction tx = FeatherliteAgent.getTransaction(currentInst);
	    for (Order order : orderList) {
		PlanningState state = order.getState();
		if (!(state.compareTo(PlanningState.PLANNED) > 0)) {
		    Workflow workflow = RSPContainerHelper.getWorkflowMap(true).getEntryBy(order.getId());
		    if (order.getProcessingState() == ProcessingState.OPEN) {
			ClearWorkflowCommand command = new ClearWorkflowCommand();
			command.setWorkflow(workflow);
			tx.addCommand(command);
			EvictOrderToCreatedCommand evictOrderCommand = new EvictOrderToCreatedCommand();
			evictOrderCommand.setOrder(order);
			tx.addCommand(evictOrderCommand);
		    }
		}
	    }
	    tx.commit();
	} catch (Exception e) {
	    logger.info("======Inside clear order exception" + e.getMessage());
	} finally {
	}
    }

    public static void updateFileDriverLoadCount(ElementMap<Resource> resourceMap) {
	String filename = "F:\\GIGA\\Load_Balance\\DL.csv";
	BufferedWriter bw = null;
	FileWriter fw = null;
	try {
	    String content = "This is the content to write into file\n";
	    fw = new FileWriter(filename);
	    bw = new BufferedWriter(fw);
	    Collection<Resource> driverRessources = resourceMap.getByType("driver_CR");
	    for (Resource driverRes : driverRessources) {
		int localOrder = ((IntegerParameter) driverRes.getParameterBy("noOfLocalOrders")).getValue();
		int outStationOrder = ((IntegerParameter) driverRes.getParameterBy("noOfOutstationOrders")).getValue();
		//if (localOrder > 0 || outStationOrder > 0) {
		System.out.println(driverRes.getId() + "," + localOrder + "," + outStationOrder);
		content = driverRes.getId() + "," + localOrder + "," + outStationOrder + "\n";
		bw.write(content);
		//}
	    }
	} catch (IOException e) {
	    e.printStackTrace();
	} finally {
	    try {
		if (bw != null)
		    bw.close();
		if (fw != null)
		    fw.close();
	    } catch (IOException ex) {
		ex.printStackTrace();
	    }
	}
    }

    public static void updateInMemoryDriverLoad(ElementMap<Resource> resourceMap) {
	try {
	    String FilefilePath = "F:\\GIGA\\Load_Balance\\DL.csv";
	    FileReader driverLoadFile = new FileReader(FilefilePath);
	    //if (driverLoadFile != null) {
	    BufferedReader br = new BufferedReader(driverLoadFile);
	    String eachLine = null;
	    while ((eachLine = br.readLine()) != null) {
		String[] args = eachLine.split(",");
		if (args.length == 3) {
		    String driverID = args[0];
		    int localOrderCount = Integer.parseInt(args[1]);
		    int outstationOrderCount = Integer.parseInt(args[2]);
		    Resource driverRes = resourceMap.getEntryBy(driverID);
		    ((IntegerParameter) driverRes.getParameterBy("noOfLocalOrders")).setValue(localOrderCount);
		    ((IntegerParameter) driverRes.getParameterBy("noOfOutstationOrders")).setValue(outstationOrderCount);
		}
	    }
	    //}
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    public static void updateLoadBalanceTable() {
	Connection con = null;
	CallableStatement stmt = null;
	PreparedStatement pStmt = null;
	ResultSet rs = null;
	try {
	    ElementMap<Resource> resourceMap = RSPContainerHelper.getResourceMap(true);
	    Class.forName("net.sourceforge.jtds.jdbc.Driver");
	    con = DriverManager.getConnection("jdbc:jtds:sqlserver://10.60.105.222:1433/scmdb", "sa", "@giga2777");
	    Collection<Resource> driverRessources = resourceMap.getByType("driver_CR");
	    for (Resource driverRes : driverRessources) {
		String driverId = driverRes.getId();
		int localOrder = ((IntegerParameter) driverRes.getParameterBy("noOfLocalOrders")).getValue();
		int outStationOrder = ((IntegerParameter) driverRes.getParameterBy("noOfOutstationOrders")).getValue();
		stmt = con.prepareCall("exec DriverLoadUpdate(?,?,?)");
		stmt.setString(1, driverId);
		stmt.setInt(2, localOrder);
		stmt.setInt(3, outStationOrder);
		stmt.execute();
	    }
	} catch (Exception e) {
	    logger.info("======Inside exception" + e.getMessage());
	    e.printStackTrace();
	} finally {
	    try {
		if (con != null)
		    con.close();
		if (stmt != null)
		    stmt.close();
		if (pStmt != null)
		    pStmt.close();
		if (rs != null)
		    rs.close();
	    } catch (Exception e) {
		e.printStackTrace();
	    }
	}
    }

    public static void updateLoadBalanceInMemory() {
	Connection con = null;
	Statement stmt = null;
	PreparedStatement pStmt = null;
	ResultSet rs = null;
	try {
	    ElementMap<Resource> resourceMap = RSPContainerHelper.getResourceMap(true);
	    Class.forName("net.sourceforge.jtds.jdbc.Driver");
	    con = DriverManager.getConnection("jdbc:jtds:sqlserver://10.60.105.222:1433/scmdb", "sa", "@giga2777");
	    stmt = con.createStatement();
	    rs = stmt.executeQuery("select * from driverloadcount nolock");
	    logger.info("==rs size==> " + rs.getFetchSize());
	    while (rs.next()) {
		String driverID = rs.getString(1);
		int localOrderCount = rs.getInt(2);
		int outstationOrderCount = rs.getInt(3);
		logger.info(driverID + "," + localOrderCount + "," + outstationOrderCount);
		Resource driverRes = resourceMap.getEntryBy(driverID);
		if (driverRes != null) {
		    //logger.info("before local: " + ((IntegerParameter) driverRes.getParameterBy("noOfLocalOrders")).getValue() + ",out: " + ((IntegerParameter) driverRes.getParameterBy("noOfOutstationOrders")).getValue());
		    ((IntegerParameter) driverRes.getParameterBy("noOfLocalOrders")).setValue(localOrderCount);
		    ((IntegerParameter) driverRes.getParameterBy("noOfOutstationOrders")).setValue(outstationOrderCount);
		    //logger.info("After local: " + ((IntegerParameter) driverRes.getParameterBy("noOfLocalOrders")).getValue() + ",out: " + ((IntegerParameter) driverRes.getParameterBy("noOfOutstationOrders")).getValue());
		}
	    }
	} catch (Exception e) {
	    logger.info("======Inside exception" + e.getMessage());
	    e.printStackTrace();
	} finally {
	    try {
		if (con != null)
		    con.close();
		if (stmt != null)
		    stmt.close();
		if (pStmt != null)
		    pStmt.close();
		if (rs != null)
		    rs.close();
	    } catch (Exception e) {
		e.printStackTrace();
	    }
	}
    }

    public static void insertIntoDriverLoadDetail(String startDate) {
	Connection con = null;
	CallableStatement stmt = null;
	PreparedStatement pStmt = null;
	ResultSet rs = null;
	try {
	    ElementMap<Resource> resourceMap = RSPContainerHelper.getResourceMap(true);
	    Class.forName("net.sourceforge.jtds.jdbc.Driver");
	    con = DriverManager.getConnection("jdbc:jtds:sqlserver://10.60.105.222:1433/scmdb", "sa", "@giga2777");
	    Collection<Resource> driverRessources = resourceMap.getByType("driver_CR");
	    DateFormat df = new SimpleDateFormat("dd-mm-yyyy");
	    Date currDate = df.parse(startDate);
	    for (Resource driverRes : driverRessources) {
		String driverId = driverRes.getId();
		String driverName = driverRes.getName();
		int localOrder = 0;
		int outStationOrder = 0;
		if (driverRes.hasParameter("noOfCurrDayLocalOrders"))
		    localOrder = ((IntegerParameter) driverRes.getParameterBy("noOfCurrDayLocalOrders")).getValue();
		if (driverRes.hasParameter("noOfCurrDayOutstationOrders"))
		    outStationOrder = ((IntegerParameter) driverRes.getParameterBy("noOfCurrDayOutstationOrders")).getValue();
		stmt = con.prepareCall("exec InsertDetailDriverLoad(?,?,?,?,?)");
		stmt.setString(1, driverId);
		stmt.setString(2, driverName);
		stmt.setInt(3, localOrder);
		stmt.setInt(4, outStationOrder);
		stmt.setDate(5, new java.sql.Date(currDate.getTime()));
		stmt.execute();
	    }
	} catch (Exception e) {
	    logger.info("======Inside exception" + e.getMessage());
	    e.printStackTrace();
	} finally {
	    try {
		if (con != null)
		    con.close();
		if (stmt != null)
		    stmt.close();
		if (pStmt != null)
		    pStmt.close();
		if (rs != null)
		    rs.close();
	    } catch (Exception e) {
		e.printStackTrace();
	    }
	}
    }
}
