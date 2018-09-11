package com.ramco.giga.utils;

import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.rsp.core.base.RSPContainerHelper;
import com.rsp.core.base.model.ElementMap;
import com.rsp.core.base.model.Order;
import com.rsp.core.base.model.constants.PlanningState;
import com.rsp.core.base.model.parameter.BooleanParameter;
import com.rsp.core.base.service.DBUploadService;

public class UnplannedOrderIntoExcel {
    //	private static int count = 0;
    private final static Logger logger = Logger.getLogger(UnplannedOrderIntoExcel.class);

    public String doService(Connection connection, DBUploadService.Argument argDBUpload) throws SQLException, Exception {
	String result = "true";
	ElementMap<Order> orderMap = RSPContainerHelper.getOrderMap(true);
	Map<String, String> inmap = argDBUpload.customProperties;
	String location = inmap.get("location");
	List<Order> unplannedOrderList = new ArrayList<Order>();
	List<Order> orderList = orderMap.asList();
	if (orderList.size() > 0) {
	    for (Order order : orderList) {
		if (!order.getState().equals(PlanningState.PLANNED) && order.hasParameter("mailSent")) {
		    if ((order.getId().subSequence(0, 3)).toString().equalsIgnoreCase("NTC") && ((BooleanParameter) order.getParameterBy("mailSent")).getValue() == false) {
			logger.info("Order Id:" + order);
			unplannedOrderList.add(order);
		    }
		}
	    }
	}
	boolean flag = true;
	if (unplannedOrderList.size() > 0) {
	    logger.info("flag value true: " + flag);
	    isWriteIntoExcel(connection, flag);
	    exportIntoExcel(unplannedOrderList, connection, location);
	} else {
	    flag = false;
	    logger.info("flag value false: " + flag);
	    isWriteIntoExcel(connection, flag);
	}
	return result;
    }

    private static void isWriteIntoExcel(Connection conn, boolean flag) {
	Statement statement = null;
	try {
	    statement = conn.createStatement();
	    String sql = null;
	    if (flag == true) {
		logger.info("flag value: " + flag);
		//	sql = "UPDATE a set a.excelGenerated = 1 from ral_write_into_excel a";
		sql = "update ral_write_into_excel set excelGenerated = 1";
		logger.info("sql query:: " + sql);
	    } else {
		logger.info("flag value: " + flag);
		//	sql = "UPDATE a set a.excelGenerated = 0 from ral_write_into_excel a";
		sql = "update ral_write_into_excel set excelGenerated = 0";
		logger.info("sql query:: " + sql);
	    }
	    statement.executeUpdate(sql);
	    conn.commit();
	} catch (SQLException e) {
	    e.printStackTrace();
	} finally {
	    try {
		if (statement != null)
		    statement.close();
	    } catch (Exception e) {
		e.printStackTrace();
	    }
	}
    }

    public static void exportIntoExcel(List<Order> unplannedOrderList, Connection connection, String location) {
	int count = 0;
	XSSFWorkbook workbook = new XSSFWorkbook();
	FileOutputStream out = null;
	try {
	    //List<Order> orderList = RSPContainerHelper.getOrderMap(true).asList();
	    for (int i = 0; i < unplannedOrderList.size(); i++) {
		XSSFSheet sheet = workbook.createSheet(unplannedOrderList.get(i).getId());
		//	BufferedReader bufferedReader = new BufferedReader(unplannedOrderList.get(i).getParameterBy("orderDetails").getValueAsString());
		String bufferedReader = unplannedOrderList.get(i).getParameterBy("orderDetails").getValueAsString();
		//exportIntoExcel(workbook, sheet, bufferedReader);
		int index = 1;
		String[] lines = bufferedReader.split("~");
		LinkedHashMap<String, Object[]> map = new LinkedHashMap<String, Object[]>();
		LinkedHashMap<String, List> mapObj = new LinkedHashMap<String, List>();
		for (String line : lines) {
		    System.out.println(line);
		    if (index >= 17) {
			ArrayList<Object> al = new ArrayList();
			Object[] oj = line.split(",");
			String indexNo = Integer.toString(index);
			for (int z = 0; z < oj.length; z++) {
			    al.add(oj[z]);
			}
			//System.out.println(al);
			mapObj.put(indexNo, al);
			index = index + 1;
		    }
		    if (index < 17) {
			String indexNo = Integer.toString(index);
			map.put(indexNo, new Object[] { line });
			index = index + 1;
		    }
		    //System.out.println(line);
		}
		Set<String> keyset1 = map.keySet();
		int rownum1 = 0;
		for (String key : keyset1) {
		    Row row = sheet.createRow(rownum1++);
		    Object[] objArr = map.get(key);
		    for (Object obj : objArr) {
			if (obj instanceof String) {
			    String string = (String) obj;
			    String a[] = string.split(",");
			    for (int k = 0; k < a.length; k++) {
				row.createCell(0).setCellValue(a[0]);
				row.createCell(1).setCellValue(a[k]);
			    }
			}
		    }
		}
		Set<String> keyset11 = mapObj.keySet();
		int rownum11 = 17;
		for (String key : keyset11) {
		    Row row = sheet.createRow(rownum11++);
		    List<?> objArr = mapObj.get(key);
		    int cellnum = 0;
		    for (Object obj : objArr) {
			Cell cell = row.createCell(cellnum++);
			if (obj instanceof String)
			    cell.setCellValue((String) obj);
			else if (obj instanceof Integer)
			    cell.setCellValue((Integer) obj);
			else if (obj instanceof Double)
			    cell.setCellValue((Double) obj);
			else if (obj instanceof Float)
			    cell.setCellValue((Float) obj);
			else if (obj instanceof Date)
			    cell.setCellValue(obj.toString());
		    }
		}
		count++;
		System.out.println("The total numbers of the sheet is: " + count);
		//bufferedReader.close();
	    }
	    /*	UUID idOne = UUID.randomUUID();
	    	String location = "D:\\GIGA_IPO_Deliverables\\report\\UnplannedOrderDetail"+idOne+".xlsx";*/
	    //	out = new FileOutputStream("D:\\GIGA_IPO_Deliverables\\report\\UnplannedOrderDetail.xlsx");
	    out = new FileOutputStream(location);
	    //	out = new FileOutputStream(location);
	    out.flush();
	    workbook.write(out);
	    for (int i = 0; i < unplannedOrderList.size(); i++) {
		//	BooleanParameter isMailSent = new BooleanParameter("mailSent", "mailSent", true);
		//	unplannedOrderList.get(i).addParameter(isMailSent);
		((BooleanParameter) unplannedOrderList.get(i).getParameterBy("mailSent")).setValue(true);
	    }
	} catch (Exception e) {
	    e.printStackTrace();
	    isWriteIntoExcel(connection, false); // if file did'nt got generated
	} finally {
	    try {
		out.close();
	    } catch (Exception e) {
		// TODO: handle exception
	    }
	}
    }
}
