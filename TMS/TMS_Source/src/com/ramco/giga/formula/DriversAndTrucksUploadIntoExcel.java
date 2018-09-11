package com.ramco.giga.formula;

import java.io.File;
import java.io.FileOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;

import com.rsp.core.base.RSPContainerHelper;
import com.rsp.core.base.exception.RSPException;
import com.rsp.core.base.formula.LookupFormula;
import com.rsp.core.base.model.ElementMap;
import com.rsp.core.base.model.Order;
import com.rsp.core.base.model.Resource;
import com.rsp.core.base.model.parameter.Parameter;
import com.rsp.core.base.model.parameter.StringParameter;

public class DriversAndTrucksUploadIntoExcel extends LookupFormula {

	public static final Logger logger = Logger
			.getLogger(DriversAndTrucksUploadIntoExcel.class);
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	public Parameter<?> evaluate() throws RSPException {
		// TODO Auto-generated method stub
		logger.info("Inside DriversAndTrucksUploadIntoExcel");
/*		List<String> vehicleIds = new ArrayList<String>();
		
		vehicleIds.add("Car");
		vehicleIds.add("Car1");*/
		
		writeIntoExcel();
		
		StringParameter resultP = new StringParameter(
				this.descriptiveParameter.getId(),
				this.descriptiveParameter.getName(), "success");
		
		logger.info("resultP: " + resultP);

		return resultP;

	}

	private void writeIntoExcel() {
		try {

			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
			Date date = new Date();
			
			HSSFWorkbook workbook = new HSSFWorkbook();

			HSSFSheet driverSheet = workbook.createSheet("Driver");
			HSSFSheet vehicleSheet = workbook.createSheet("Truck");
			HSSFSheet maintenanceSheet = workbook.createSheet("Maintenance");
			

			ElementMap<Resource> vehicleResourceMap = RSPContainerHelper
					.getResourceMap(true);
			Collection<Resource> vehicleResourcesByType = vehicleResourceMap
					.getByType("truck_CR");
			
			ElementMap<Order> orderMap = RSPContainerHelper.getOrderMap(true);
			Collection<Order> orderMapByType = orderMap.getByType("Maintenance");
			Collection<Order> plannedLeave = orderMap.getByType("PlannedLeave");
			logger.info("Maintenance order: " + orderMapByType);
	
			Map<String, Object[]> vehicleData = new TreeMap<String, Object[]>();
			vehicleData.put("1", new Object[] { "VEHICLE_ID", "VEHICLE_NAME", "VEHICLE_TYPE", "MAPPED_DRIVER", "BASE_LOCATION", "TRUCK_REG_NO"});
			int indexVehicle = 2;
			HashSet<String> availableDriver = new HashSet<String>();
			for (Resource resource : vehicleResourcesByType) {
				String indexNo = Integer.toString(indexVehicle);
				vehicleData.put(indexNo, new Object[] { resource.getId(),
						resource.getName(), resource.getType(), resource.getParameterBy("driverId").getValueAsString(),
						resource.getParameterBy("location").getValueAsString(), resource.getParameterBy("truckRegNo").getValueAsString()});
				availableDriver.add(resource.getParameterBy("driverId").getValueAsString());
				indexVehicle = indexVehicle + 1;
			}
			
			// Iterate over data and write to sheet
			Set<String> keyset1 = vehicleData.keySet();
			int rownum1 = 0;
			for (String key : keyset1) {
				Row row = vehicleSheet.createRow(rownum1++);
				Object[] objArr = vehicleData.get(key);
				int cellnum = 0;
				for (Object obj : objArr) {
					Cell cell = row.createCell(cellnum++);
					if (obj instanceof String)
						cell.setCellValue((String) obj);
					else if (obj instanceof Integer)
						cell.setCellValue((Integer) obj);
				}
			}
			
			// This data needs to be written (Object[])
			Map<String, Object[]> driverData = new TreeMap<String, Object[]>();
			driverData.put("1", new Object[] { "DRIVER_ID", "DRIVER_NAME", "CURRENT_SYSTEM_DATE", "STATUS" });
			int indexDriver = 2;
			for (Order order : plannedLeave) {
				String indexNo = Integer.toString(indexDriver);
				driverData.put(indexNo, new Object[] { order.getParameterBy("driverNo").getValueAsString(),
						order.getParameterBy("driverName").getValueAsString(), dateFormat.format(date), order.getParameterBy("status").getValueAsString()});
				indexDriver = indexDriver + 1;
				
			}
			

			// Iterate over data and write to sheet
			Set<String> keyset = driverData.keySet();
			int rownum = 0;
			for (String key : keyset) {
				Row row = driverSheet.createRow(rownum++);
				Object[] objArr = driverData.get(key);
				int cellnum = 0;
				for (Object obj : objArr) {
					Cell cell = row.createCell(cellnum++);
					if (obj instanceof String)
						cell.setCellValue((String) obj);
					else if (obj instanceof Integer)
						cell.setCellValue((Integer) obj);
				}
			}
			
			Map<String, Object[]> maintenance = new TreeMap<String, Object[]>();
			maintenance.put("1", new Object[] {"MAINTENANCE_TRUCK" });
			indexVehicle = 2;
			
			for (Order order : orderMapByType) {
				String truckNo = (String) order.getParameterBy("truckNo").getValue();
				logger.info("truck no: " + truckNo);
				String indexNo = Integer.toString(indexVehicle);
				maintenance.put(indexNo, new Object[] {order.getParameterBy("truckNo").getValue()});
				indexVehicle = indexVehicle + 1;
			}
			
			Set<String> keyset2 = maintenance.keySet();
			int rownum2 = 0;
			for (String key : keyset2) {
				Row row = maintenanceSheet.createRow(rownum2++);
				Object[] objArr = maintenance.get(key);
				int cellnum = 0;
				for (Object obj : objArr) {
					Cell cell = row.createCell(cellnum++);
					if (obj instanceof String)
						cell.setCellValue((String) obj);
					else if (obj instanceof Integer)
						cell.setCellValue((Integer) obj);
				}
			}
			
			
			

			// Write the workbook in file system
/*			FileOutputStream out = new FileOutputStream(new File(
					"D:\\gigaApril14\\DriverAndVehicle.xls"));*/
			
			FileOutputStream out = new FileOutputStream(new File(
					"E:\\GIGA_IPO_Deliverables\\DriverAndVehicle.xls"));
			out.flush();
			workbook.write(out);

			out.close();
			System.out.println("Excel created successfully");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}