package com.ramco.giga.policy;

import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import com.ramco.giga.formula.FindDistanceDuration;
import com.ramco.giga.formula.TruckSelectionFormula;
import com.rsp.core.base.RSPContainerHelper;
import com.rsp.core.base.exception.DataModelException;
import com.rsp.core.base.exception.RSPException;
import com.rsp.core.base.model.Activity;
import com.rsp.core.base.model.IPolicyContainer;
import com.rsp.core.base.model.Objectives;
import com.rsp.core.base.model.Order;
import com.rsp.core.base.model.ParameterizedElement;
import com.rsp.core.base.model.Resource;
import com.rsp.core.base.model.Task;
import com.rsp.core.base.model.TimeInterval;
import com.rsp.core.base.model.WorkCalendar;
import com.rsp.core.base.model.constants.PlanningState;
import com.rsp.core.base.model.constants.TimeDirection;
import com.rsp.core.base.model.parameter.DateParameter;
import com.rsp.core.base.model.parameter.FloatListParameter;
import com.rsp.core.base.model.parameter.FloatParameter;
import com.rsp.core.base.model.parameter.StringParameter;
import com.rsp.core.base.model.stateVariable.FloatState;
import com.rsp.core.base.model.stateVariable.StateValue;
import com.rsp.core.base.model.stateVariable.StringState;
import com.rsp.core.i18n.RSPMessages;
import com.rsp.core.planning.policy.CalendarPolicy;
import com.rsp.core.planning.policy.PlacementResult;
import com.rsp.core.planning.policy.placement.CapacityPlacement;
import com.rsp.core.planning.policy.placement.DefaultPlacementResult;

public class EastMtruckPlacement extends CapacityPlacement{
	
	private static final long serialVersionUID = 1L;
	
    private static String CAPACITYSTATE_KEY = "Capacity";
	
	private static String LOCATION_KEY = "Location";

	private static String QUANTITY_KEY = "quantity";

	private static String DURATION_KEY = "duration";

	protected FloatState Capacity;
	protected StringState Location;
	protected StringState Make;
	

	private long twoHours = (long) 7.2e6;
	private long fourHours = 2 * twoHours;
	private long eightHours = 4 * twoHours;

	protected double quantity;
	protected long duration;
	protected String locationVal;
	//protected String makeID;
	
	
	protected Double capacityLowerBound;
	protected Double capacityUpperBound;

	
	public void evaluateObjectives(Task task) {

		try {

			quantity = getFloatObjective(task, QUANTITY_KEY);
			duration = getDurationObjective(task, DURATION_KEY);
			
		} catch (DataModelException e) {

			DataModelException dme = new DataModelException(
					"Error in placement policy $policyName while reading objectives of task $taskId",
					"datamodel.objectivesRead", e);
			dme.addProperty("policyName", this.getName());
			dme.addProperty("taskId", task == null ? "null" : task.getId());
			throw dme;
		}
	}

	public void evaluateBounds(Resource resource) {
		
		capacityLowerBound = getFloatState(resource, CAPACITYSTATE_KEY).getLowerBound();
		capacityUpperBound = getFloatState(resource, CAPACITYSTATE_KEY).getUpperBound();
		// nothing to do
	}

	@Override
	public PlacementResult place(Task task, Resource resource, TimeInterval horizon, CalendarPolicy calendarPolicy,
			WorkCalendar workCalendar) {
		
		String direction = getStringObjective(task, TimeDirection.TIMEDIRECTION_KEY);
		return place(task, resource, horizon, direction, calendarPolicy, workCalendar);
	}

	public PlacementResult place(Task task, Resource resource, TimeInterval horizon, String direction,
			CalendarPolicy calendarPolicy, WorkCalendar workCalendar) {
		evaluateObjectives(task);
		evaluateBounds(resource);

		PlacementResult placementResult = null;
		long start;
		long end;

		ParameterizedElement tmp = task;
		String orderNetId = null;
		while (tmp != null) {
			orderNetId = tmp.getId();
			tmp = tmp.getParent();
		}
		logger.info("orderNetId: " + orderNetId);
		Order order = RSPContainerHelper.getOrderMap(true).getEntryBy(
				orderNetId);
		StringParameter remarks = (StringParameter) order
				.getParameterBy("remarks");
		StringParameter script = (StringParameter) order
				.getParameterBy("script");
		SimpleDateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");

		Collection<Resource> customerLocationMapList = RSPContainerHelper
				.getResourceMap(true).getByType("Customer_Location_Mapping");
		String customer_pickupLocId = null;
		String customer_deliveryLocId = null;

		Resource giga_parameters = RSPContainerHelper.getResourceMap(true)
				.getEntryBy("giga_parameters");

		String resourceType = resource.getParameterBy("truckType")
				.getValueAsString();

		double maxWorkHrsValue = (Double) giga_parameters.getParameterBy(
				"maxWorkHrs").getValue();
		long maxWorkHrs = (long) (maxWorkHrsValue * 3.6e6);

		double loadingBufferValue = (Double) giga_parameters.getParameterBy(
				"loadingBuffer").getValue();
		long loadingBuffer = (long) (loadingBufferValue * 60000L);

		double unloadingBufferValue = (Double) giga_parameters.getParameterBy(
				"unloadingBuffer").getValue();
		long unloadingBuffer = (long) (unloadingBufferValue * 60000L);

		double restHrs4value = (Double) giga_parameters.getParameterBy(
				"restHrs4").getValue();
		long restHrs4 = (long) (restHrs4value * 60000L);

		double restHrs8value = (Double) giga_parameters.getParameterBy(
				"restHrs8").getValue();
		long restHrs8 = (long) (restHrs8value * 60000L);

		double restHrs111value = (Double) giga_parameters.getParameterBy(
				"restHrs111").getValue();
		long restHrs111 = (long) (restHrs111value * 60000L);

		double restHrs211value = (Double) giga_parameters.getParameterBy(
				"restHrs211").getValue();
		long restHrs211 = (long) (restHrs211value * 60000L);

		if ((order.getType().equalsIgnoreCase("Maintenance"))
				|| (order.getType().equalsIgnoreCase("PlannedLeave"))) {
			start = horizon.getLower();
			logger.info("maintenance order upload");
			placementResult = new DefaultPlacementResult(
					PlacementResult.SUCCESS);
			placementResult.setStart(start);
			placementResult.setEnd(start + this.duration);
			placementResult.setQuantity(this.quantity);
			placementResult.setResource(resource);

			logger.info("maintenance order planned");

			return placementResult;
		}

		else {

			String customerID = order.getParameterBy("customerID")
					.getValueAsString();
			String orderedTruckType = order.getParameterBy("truckType")
					.getValueAsString();
			logger.info("orderedTruckType->" + orderedTruckType);
			String customerType = order.getParameterBy("orderType")
					.getValueAsString();

			String pickupLocation = order.getParameterBy("pickupLocation")
					.getValueAsString();
			logger.info("pickupLocation" + pickupLocation);

			String deliveryLocation = order.getParameterBy("deliveryLocation")
					.getValueAsString();
			logger.info("deliveryLocation" + deliveryLocation);

			double noOfDrops = 1.0;
			if (order.hasParameter("noOfDrops"))
				noOfDrops = (Double) order.getParameterBy("noOfDrops").getValue();

			long pickupTime = (Long) order.getParameterBy("pickupDate")
					.getValue();
			logger.info("pickupOrdertime: " + new Date(pickupTime));

			long deliveryTime = (Long) order.getParameterBy("deliveryDate")
					.getValue();
			logger.info("deliveryOrdertime: " + new Date(deliveryTime));

			Resource customer_pickupLocRes = null;
			Resource customer_deliveryLocRes = null;

			String pickupLocPeakStart1 = null;
			String pickupLocPeakEnd1 = null;
			String pickupLocPeakStart2 = null;
			String pickupLocPeakEnd2 = null;
			String pickupLocPeakStart3 = null;
			String pickupLocPeakEnd3 = null;
			String pickupLocPeakStart4 = null;
			String pickupLocPeakEnd4 = null;
			String deliveryLocPeakStart1 = null;
			String deliveryLocPeakEnd1 = null;
			String deliveryLocPeakStart2 = null;
			String deliveryLocPeakEnd2 = null;
			String deliveryLocPeakStart3 = null;
			String deliveryLocPeakEnd3 = null;
			String deliveryLocPeakStart4 = null;
			String deliveryLocPeakEnd4 = null;

			long tripDuration = getDuration(pickupLocation, deliveryLocation);
			// long buffer = 2700000L;

			logger.info("tripDuration" + tripDuration / 3600000);
			end = deliveryTime;

			logger.info("orderEnd : " + new Date(end));

			/*
			 * Date pickupDate = new Date(pickupTime); SimpleDateFormat df2 =
			 * new SimpleDateFormat("MM/dd/yyyy"); String pickupDateText =
			 * df2.format(pickupDate); String deliveryDateText =
			 * df2.format(end);
			 * 
			 * StringWriter s = new StringWriter(); s.append(pickupDateText);
			 * 
			 * logger.info("pickupDay"+pickupDateText);
			 */
			/*
			 * s.append(" "+startTime);
			 */

			for (Resource r : customerLocationMapList) {

				if ((customerID + "_" + pickupLocation).equalsIgnoreCase(r
						.getId()))
					customer_pickupLocId = r.getId();

				if ((customerID + "_" + deliveryLocation).equalsIgnoreCase(r
						.getId()))
					customer_deliveryLocId = r.getId();

			}

			if (customer_pickupLocId == null) {
				//for (Resource r : customerLocationMapList) {
					

						customer_pickupLocId = "OTHERS_OTHERS";

					customer_pickupLocRes = RSPContainerHelper.getResourceMap(
							true).getEntryBy(customer_pickupLocId);

					//if (pickupLocation.equalsIgnoreCase(location)) {
						pickupLocPeakStart1 = customer_pickupLocRes
								.getParameterBy("peakStart1")
								.getValueAsString();
						pickupLocPeakEnd1 = customer_pickupLocRes
								.getParameterBy("peakEnd1").getValueAsString();

						pickupLocPeakStart2 = customer_pickupLocRes
								.getParameterBy("peakStart2")
								.getValueAsString();
						pickupLocPeakEnd2 = customer_pickupLocRes
								.getParameterBy("peakEnd2").getValueAsString();

						pickupLocPeakStart3 = customer_pickupLocRes
								.getParameterBy("peakStart3")
								.getValueAsString();
						pickupLocPeakEnd3 = customer_pickupLocRes
								.getParameterBy("peakEnd3").getValueAsString();

						pickupLocPeakStart4 = customer_pickupLocRes
								.getParameterBy("peakStart4")
								.getValueAsString();
						pickupLocPeakEnd4 = customer_pickupLocRes
								.getParameterBy("peakEnd4").getValueAsString();
					//}

					
					
				//}
			}
			if (customer_deliveryLocId == null) {
				//for (Resource r : customerLocationMapList) {
					
					
						customer_deliveryLocId = "OTHERS_OTHERS";

					customer_deliveryLocRes = RSPContainerHelper
							.getResourceMap(true).getEntryBy(
									customer_deliveryLocId);

					//if (deliveryLocation.equalsIgnoreCase(location)) {
						deliveryLocPeakStart1 = customer_deliveryLocRes
								.getParameterBy("peakStart1")
								.getValueAsString();
						deliveryLocPeakEnd1 = customer_deliveryLocRes
								.getParameterBy("peakEnd1").getValueAsString();

						deliveryLocPeakStart2 = customer_deliveryLocRes
								.getParameterBy("peakStart2")
								.getValueAsString();
						deliveryLocPeakEnd2 = customer_deliveryLocRes
								.getParameterBy("peakEnd2").getValueAsString();

						deliveryLocPeakStart3 = customer_deliveryLocRes
								.getParameterBy("peakStart3")
								.getValueAsString();
						deliveryLocPeakEnd3 = customer_deliveryLocRes
								.getParameterBy("peakEnd3").getValueAsString();

						deliveryLocPeakStart4 = customer_deliveryLocRes
								.getParameterBy("peakStart4")
								.getValueAsString();
						deliveryLocPeakEnd4 = customer_deliveryLocRes
								.getParameterBy("peakEnd4").getValueAsString();
					//}

				//}
			}
			logger.info("customer_pickupLocId->" + customer_pickupLocId);
			logger.info("customer_deliveryLocId->" + customer_deliveryLocId);

			customer_deliveryLocRes = RSPContainerHelper.getResourceMap(true)
					.getEntryBy(customer_deliveryLocId);
			logger.info("latestDeliveryTime:"
					+ customer_deliveryLocRes.getParameterBy("deliveryEndTime")
							.getValueAsString());
			String latestDeliveryTime = customer_deliveryLocRes.getParameterBy(
					"deliveryEndTime").getValueAsString();
			String earliestDeliveryTime = customer_deliveryLocRes
					.getParameterBy("deliveryStartTime").getValueAsString();

			customer_pickupLocRes = RSPContainerHelper.getResourceMap(true)
					.getEntryBy(customer_pickupLocId);
			logger.info("earliestPickupTime:"
					+ customer_pickupLocRes.getParameterBy("pickupStartTime")
							.getValueAsString());
			String earliestPickupTime = customer_pickupLocRes.getParameterBy(
					"pickupStartTime").getValueAsString();
			String latestPickupTime = customer_pickupLocRes.getParameterBy(
					"pickupEndTime").getValueAsString();
			logger.info("latestpickuptimeff " + latestPickupTime);

			try {
				String startTime = giga_parameters.getParameterBy(
						"dayStartTime").getValueAsString();
				long startTimeoftheDay = getTime(pickupTime, startTime);
				;
				logger.info("startoftheDay->" + new Date(startTimeoftheDay));

				long earliestPickupTimeOfTheDay = getTime(pickupTime,
						earliestPickupTime);
				long latestPickupTimeOfTheDay = getTime(pickupTime,
						latestPickupTime) - loadingBuffer;

				if (latestPickupTimeOfTheDay < earliestPickupTimeOfTheDay)
					latestPickupTimeOfTheDay = (long) (latestPickupTimeOfTheDay + 24 * 3.6e6);

				long earliestDeliveryTimeOfTheDay = getTime(deliveryTime,
						earliestDeliveryTime);
				long latestDeliveryTimeOfTheDay = getTime(deliveryTime,
						latestDeliveryTime);

				if (latestDeliveryTimeOfTheDay < earliestDeliveryTimeOfTheDay)
					latestDeliveryTimeOfTheDay = (long) (latestDeliveryTimeOfTheDay + 24 * 3.6e6);

				logger.info("earliestPickupTimeOfTheDay"
						+ new Date(earliestPickupTimeOfTheDay));
				logger.info("latestPickupTimeOfTheDay"
						+ new Date(latestPickupTimeOfTheDay));

				logger.info("earliestDeliveryTimeOfTheDay"
						+ new Date(earliestDeliveryTimeOfTheDay));
				logger.info("latestDeliveryTimeOfTheDay"
						+ new Date(latestDeliveryTimeOfTheDay));

				double pickup_AT = (Double) customer_pickupLocRes
						.getParameterBy("articulated").getValue();
				double delivery_AT = (Double) customer_deliveryLocRes
						.getParameterBy("articulated").getValue();

				double pickup_RT = (Double) customer_pickupLocRes
						.getParameterBy("rigid").getValue();
				double delivery_RT = (Double) customer_deliveryLocRes
						.getParameterBy("rigid").getValue();

				double pickup_ST = (Double) customer_pickupLocRes
						.getParameterBy("single").getValue();
				double delivery_ST = (Double) customer_deliveryLocRes
						.getParameterBy("single").getValue();

				logger.info("truck: " + resource.getId());
				end = latestDeliveryTimeOfTheDay;

				String CurrLoc = null;
				long earliestAvailTime = 0;
				List<Activity> prevTasks = resource.getActivitiesInInterval(
						Long.MIN_VALUE, Long.MAX_VALUE);

				if (prevTasks.size() < 1) {
					StateValue<?> currentLocValue = resource
							.getStateVariableBy("Location").getValueAt(end);
					earliestAvailTime = resource.getStateVariableBy("Location")
							.getValueAt(end).getTime();

					CurrLoc = currentLocValue.getValueAsString();
					logger.info("currentLocValue " + CurrLoc);
				} else {
					for (Activity act : prevTasks) {
						String orderId = act.getOrderNetId();
						Order prevOrder = RSPContainerHelper.getOrderMap(true)
								.getEntryBy(orderId);
						String OrderType = prevOrder.getType();
						logger.info("prevOrder->" + orderId);

						if (OrderType.equalsIgnoreCase("Maintenance")) {
							logger.info("It is a maintenance order");
							continue;
						}
						logger.info("prevOrder->" + orderId);
						CurrLoc = prevOrder.getParameterBy("deliveryLocation")
								.getValueAsString();
						earliestAvailTime = act.getEnd();

						if (prevOrder.getParameterBy("jobType")
								.getValueAsString()
								.equalsIgnoreCase("Outstation")) {

							long prevOrderStartTime = act.getStart() - 2 * 60000L;
							earliestAvailTime = resource
									.getStateVariableBy("Location")
									.getValueAt(prevOrderStartTime).getTime();
							CurrLoc = resource.getStateVariableBy("Location")
									.getValueAt(earliestAvailTime)
									.getValueAsString();
							break;
						}

					}
					logger.info("currentLocValue " + CurrLoc);
				}

				if (earliestAvailTime > startTimeoftheDay)
					start = earliestAvailTime;
				else
					start = startTimeoftheDay;

				long currentTime = System.currentTimeMillis();
				if (start < currentTime)
					start = currentTime + 10 * 60000L;

				logger.info("startTimeOfTheDay: " + startTimeoftheDay);
				logger.info("earliestAvailTime: " + earliestAvailTime);
				logger.info("EstimatedStartTime:" + start);

				logger.info("truckCurrLoc: " + CurrLoc);

				long currToPickupDur = getDuration(CurrLoc, pickupLocation);
				logger.info("currToPickupDur: " + currToPickupDur / 3600000);

				if (script.getValueAsString().equalsIgnoreCase(
						"ReassignManually")) {
					// CurrLoc =
					// resource.getStateVariableBy("Location").getValueAt(start).getValueAsString();
					currToPickupDur = getDuration(CurrLoc, pickupLocation);

					end = start + currToPickupDur + tripDuration
							+ loadingBuffer + unloadingBuffer;
					logger.info("end" + new Date(end));

					long duration = end - start;

					List<Activity> ActivitiesAssigned = resource
							.getActivitiesInInterval(start, end);
					List<Activity> nextActivities = new ArrayList<Activity>();
					logger.info("ActivityAtEnd " + ActivitiesAssigned.size());

					long min = Long.MAX_VALUE;
					Order nextOrder = null;
					long newEstPickupTime;

					for (Activity a : ActivitiesAssigned) {

						if (a.getStart() > end) {
							nextActivities.add(a);
						}
					}
					logger.info("nextActivities " + nextActivities.size());
					for (Activity a1 : nextActivities) {

						if (a1.getStart() < min) {
							min = a1.getStart();
							nextOrder = RSPContainerHelper.getOrderMap(true)
									.getEntryBy(a1.getOrderNetId());
						}

					}
					logger.info("nextOrder: " + nextOrder);

					if (nextActivities.size() > 0) {
						String estPickupTime = nextOrder.getParameterBy(
								"estPickupTime").getValueAsString();
						String nextPickup = nextOrder.getParameterBy(
								"pickupLocation").getValueAsString();
						logger.info("estPickupTime: " + estPickupTime);
						logger.info("nextPickup: " + nextPickup);
						long estPickupTimeOfNextOrder = df.parse(estPickupTime)
								.getTime();

						long deliveryToNextPickupDur = getDuration(
								deliveryLocation, nextPickup);

						newEstPickupTime = end + deliveryToNextPickupDur;

						long prevCurrLocToNextPickup = estPickupTimeOfNextOrder
								- earliestAvailTime;

						long difBWnewPickupTimeNold = newEstPickupTime
								- estPickupTimeOfNextOrder;
						logger.info("difBWnewPickupTimeNold"
								+ difBWnewPickupTimeNold);

						if (start == startTimeoftheDay
								&& difBWnewPickupTimeNold < 7.2e6) {
							start = (long) (start - difBWnewPickupTimeNold);
							end = start + duration;
							newEstPickupTime = start + currToPickupDur;
							logger.info("newEstimatedPickupTime"
									+ new Date(newEstPickupTime));
						}

						if (newEstPickupTime > prevCurrLocToNextPickup) {
							logger.info("truck cannot fulfill the order as it won't be able to fulfill the next order");
							placementResult = new DefaultPlacementResult(
									PlacementResult.FAILURE,
									"All feasible trucks for this order are occupied between "
											+ new Date(start) + " and "
											+ new Date(end));
							// throw
							// RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new
							// Date(start)+" and " +new Date(end));
						}
					}
					FloatState capacityState = (FloatState) resource
							.getStateVariableBy(CAPACITYSTATE_KEY);
					double startCapacity = ((Double) capacityState.getValueAt(
							start).getValue()).doubleValue();
					double endCapacity = ((Double) capacityState
							.getValueAt(end).getValue()).doubleValue();
					double actualCapacity = ((Double) capacityState
							.getActualState().getValue()).doubleValue();

					Objectives we = task.getObjectives();
					FloatParameter fp = (FloatParameter) we
							.getParameterBy("quantity");
					double taskQty = fp.getValue().doubleValue();

					// List<Activity> previousActs =
					// resource.getActivitiesInInterval(startTimeoftheDay,
					// start);
					long prevDur = 0L;
					for (Activity acts : ActivitiesAssigned) {
						long prevDuration = acts.getDuration();
						prevDur = prevDuration;
					}

					if (end > latestDeliveryTimeOfTheDay) {
						logger.info("Derived End time is greater than the latest delivery time for the delivery location "
								+ deliveryLocation);
						placementResult = new DefaultPlacementResult(
								PlacementResult.FAILURE,
								"The feasible trucks to fulfill this order cannot complete the trip within End time is greater than the required delivery time.");
						// throw
						// RSPMessages.newRspMsgEx("Derived End time is greater than the required delivery time");
					}

					else if (resource.getActivitiesInInterval(start, end)
							.size() > 0) {
						logger.info("The truck is assigned to some another order between "
								+ new Date(start) + " and " + new Date(end));
						placementResult = new DefaultPlacementResult(
								PlacementResult.FAILURE,
								"The truck"
										+ resource.getId()
										+ "  is assigned to some another order between "
										+ new Date(start) + " and "
										+ new Date(end));
						// throw
						// RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new
						// Date(start)+" and " +new Date(end));
					}

					else if ((startCapacity + taskQty < this.capacityLowerBound
							.doubleValue())
							|| (startCapacity + taskQty > this.capacityUpperBound
									.doubleValue())) {
						logger.info("All feasible trucks for this order are occupied between "
								+ new Date(start) + " and " + new Date(end));
						placementResult = new DefaultPlacementResult(
								PlacementResult.FAILURE,
								"All feasible trucks for this order are occupied between "
										+ new Date(start) + " and "
										+ new Date(end));
						// throw
						// RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new
						// Date(start)+" and " +new Date(end));
					}

					else if ((startCapacity > this.capacityLowerBound
							.doubleValue())
							|| (endCapacity > this.capacityLowerBound
									.doubleValue())) {
						logger.info("All feasible trucks for this order are occupied between "
								+ new Date(start) + " and " + new Date(end));
						placementResult = new DefaultPlacementResult(
								PlacementResult.FAILURE,
								"All feasible trucks for this order are occupied between "
										+ new Date(start) + " and "
										+ new Date(end));
						// throw
						// RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new
						// Date(start)+" and " +new Date(end));
					}

					else if ((actualCapacity < 0.0D) || (actualCapacity > 1.0D)) {
						logger.info("All feasible trucks for this order are occupied between "
								+ new Date(start) + " and " + new Date(end));
						placementResult = new DefaultPlacementResult(
								PlacementResult.FAILURE,
								"All feasible trucks for this order are occupied between "
										+ new Date(start) + " and "
										+ new Date(end));
						throw RSPMessages
								.newRspMsgEx("All feasible trucks for this order are occupied between "
										+ new Date(start)
										+ " and "
										+ new Date(end));
					}

					else if (prevDur + (end - start) > maxWorkHrs) {
						logger.info("Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
						placementResult = new DefaultPlacementResult(
								PlacementResult.FAILURE,
								"Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
						// throw
						// RSPMessages.newRspMsgEx("Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
					}

					else {

						placementResult = new DefaultPlacementResult(
								PlacementResult.SUCCESS);
						placementResult.setStart(start);
						placementResult.setEnd(end);
						placementResult.setQuantity(this.quantity);
						placementResult.setResource(resource);

						logger.info("maintenance order planned");

						// return placementResult;
					}

				} else {

					if (customerType.contains("Outstation")) {

						logger.info(customerType);

						int localDeliveryCount = 0;

						long estimatedStart = earliestDeliveryTimeOfTheDay
								- tripDuration - currToPickupDur
								- loadingBuffer;
						logger.info("estimatedStart:"
								+ new Date(estimatedStart));

						//if (estimatedStart + currToPickupDur + loadingBuffer <= latestPickupTimeOfTheDay) {
							start = estimatedStart;
							logger.info("Case1_start: " + new Date(start));
						/*}

						else if ((estimatedStart + currToPickupDur + loadingBuffer) > latestPickupTimeOfTheDay) {
							start = latestPickupTimeOfTheDay - currToPickupDur
									- loadingBuffer;
							logger.info(new Date(latestPickupTimeOfTheDay)
									+ "-----" + currToPickupDur / 3600000);
							logger.info("Case2_start: " + new Date(start));
						}*/

						long estPickupTime = start + currToPickupDur;

						if ((estPickupTime + loadingBuffer) >= getTime(
								pickupTime, pickupLocPeakStart1)
								&& estPickupTime <= getTime(pickupTime,
										pickupLocPeakEnd1)) {

							logger.info("pickupTime lies between peak1");
							long latePickupBuffer = estPickupTime
									+ loadingBuffer
									- getTime(pickupTime, pickupLocPeakStart1);

							start = start - latePickupBuffer;
						}

						if ((estPickupTime + loadingBuffer) >= getTime(
								pickupTime, pickupLocPeakStart2)
								&& estPickupTime <= getTime(pickupTime,
										pickupLocPeakEnd2)) {
							logger.info("pickupTime lies between peak2");
							long latePickupBuffer = estPickupTime
									+ loadingBuffer
									- getTime(pickupTime, pickupLocPeakStart2);

							start = start - latePickupBuffer;
						}

						if ((estPickupTime + loadingBuffer) >= getTime(
								pickupTime, pickupLocPeakStart3)
								&& estPickupTime <= getTime(pickupTime,
										pickupLocPeakEnd3)) {
							logger.info("pickupTime lies between peak3");
							long latePickupBuffer = estPickupTime
									+ loadingBuffer
									- getTime(pickupTime, pickupLocPeakStart3);

							start = start - latePickupBuffer;
						}

						if ((estPickupTime + loadingBuffer) >= getTime(
								pickupTime, pickupLocPeakStart4)
								&& estPickupTime <= getTime(pickupTime,
										pickupLocPeakEnd4)) {
							logger.info("pickupTime lies between peak4");
							long latePickupBuffer = estPickupTime
									+ loadingBuffer
									- getTime(pickupTime, pickupLocPeakStart4);

							start = start - latePickupBuffer;
						}

						if (noOfDrops > 1.0) {

							logger.info("Outstation Order With Multiple No.of Drops");
							long deliveryTime1 = earliestDeliveryTimeOfTheDay;

							logger.info("1st delivery: "
									+ new Date(deliveryTime1));

							long inTransitTravelTime = (long) 9e5;

							end = (long) (deliveryTime1 + unloadingBuffer + (noOfDrops - 1)
									* (inTransitTravelTime + unloadingBuffer));

							logger.info("start->" + new Date(start));
							logger.info("end->" + new Date(end));

						}

						else {
							end = earliestDeliveryTimeOfTheDay
									+ unloadingBuffer;

							long estimatedDelivery = earliestDeliveryTimeOfTheDay;
							logger.info("estimatedDelivery->"
									+ estimatedDelivery);

							if ((estimatedDelivery + unloadingBuffer) >= getTime(
									deliveryTime, deliveryLocPeakStart1)
									&& (end - unloadingBuffer) <= getTime(
											deliveryTime, deliveryLocPeakEnd1)) {
								logger.info("deliveryTime lies between peak1");
								long lateDeliveryBuffer = getTime(deliveryTime,
										deliveryLocPeakEnd1)
										- estimatedDelivery;

								end = end + lateDeliveryBuffer;
							}
						}
						logger.info("start->" + new Date(start));
						logger.info("end->" + new Date(end));

						List<Activity> previousActs = resource
								.getActivitiesInInterval(startTimeoftheDay, end);

						for (Activity activity : previousActs) {
							String PrevOrderID = activity.getOrderNetId();
							Order PrevOrder = (Order) RSPContainerHelper
									.getOrderMap(true).getEntryBy(PrevOrderID);
							String prevJobType = PrevOrder.getParameterBy(
									"jobType").getValueAsString();

							if (prevJobType.equals("Local"))
								localDeliveryCount++;
						}
						logger.info("localDeliveryCount" + localDeliveryCount);
						List<Activity> availCheck = resource
								.getActivitiesInInterval(start, end);
						logger.info("activities for the whole day" + availCheck);

						FloatState capacityState = (FloatState) resource
								.getStateVariableBy(CAPACITYSTATE_KEY);
						double startCapacity = ((Double) ((FloatState) capacityState)
								.getValueAt(start).getValue()).doubleValue();
						double endCapacity = ((Double) ((FloatState) capacityState)
								.getValueAt(end).getValue()).doubleValue();
						double actualCapacity = ((Double) ((FloatState) capacityState)
								.getActualState().getValue()).doubleValue();

						Objectives we = task.getObjectives();
						FloatParameter fp = (FloatParameter) we
								.getParameterBy("quantity");
						double taskQty = fp.getValue().doubleValue();

						if (!resourceType.equalsIgnoreCase(orderedTruckType)
								&& resourceType.equalsIgnoreCase("articulated")
								&& pickup_AT == 0.0) {

							logger.info("For the given customer "
									+ customerID
									+ " and pickup location "
									+ pickupLocation
									+ " the truck type permitted are not available for allocation");
							placementResult = new DefaultPlacementResult(
									PlacementResult.FAILURE,
									"For the given customer "
											+ customerID
											+ " and pickup location "
											+ pickupLocation
											+ " the truck type permitted are not available for allocation");

						} else if (!resourceType
								.equalsIgnoreCase(orderedTruckType)
								&& resourceType.equalsIgnoreCase("articulated")
								&& delivery_AT == 0.0) {

							logger.info("For the given customer "
									+ customerID
									+ " and delivery location "
									+ deliveryLocation
									+ " the truck type permitted are not available for allocation");
							placementResult = new DefaultPlacementResult(
									PlacementResult.FAILURE,
									"For the given customer "
											+ customerID
											+ " and delivery location "
											+ deliveryLocation
											+ " the truck type permitted are not available for allocation");

						} else if (!resourceType
								.equalsIgnoreCase(orderedTruckType)
								&& resourceType.equalsIgnoreCase("rigid")
								&& pickup_RT == 0.0) {

							logger.info("For the given customer "
									+ customerID
									+ " and pickup location "
									+ pickupLocation
									+ " the truck type permitted are not available for allocation");
							placementResult = new DefaultPlacementResult(
									PlacementResult.FAILURE,
									"For the given customer "
											+ customerID
											+ " and pickup location "
											+ pickupLocation
											+ " the truck type permitted are not available for allocation");

						} else if (!resourceType
								.equalsIgnoreCase(orderedTruckType)
								&& resourceType.equalsIgnoreCase("rigid")
								&& delivery_RT == 0.0) {

							logger.info("For the given customer "
									+ customerID
									+ " and delivery location "
									+ deliveryLocation
									+ " the truck type permitted are not available for allocation");
							placementResult = new DefaultPlacementResult(
									PlacementResult.FAILURE,
									"For the given customer "
											+ customerID
											+ " and delivery location "
											+ deliveryLocation
											+ " the truck type permitted are not available for allocation");

						}

						else if (!resourceType
								.equalsIgnoreCase(orderedTruckType)
								&& resourceType.equalsIgnoreCase("single")
								&& pickup_ST == 0.0) {

							logger.info("For the given customer "
									+ customerID
									+ " and pickup location "
									+ pickupLocation
									+ " the truck type permitted are not available for allocation");
							placementResult = new DefaultPlacementResult(
									PlacementResult.FAILURE,
									"For the given customer "
											+ customerID
											+ " and pickup location "
											+ pickupLocation
											+ " the truck type permitted are not available for allocation");

						} else if (!resourceType
								.equalsIgnoreCase(orderedTruckType)
								&& resourceType.equalsIgnoreCase("single")
								&& delivery_ST == 0.0) {

							logger.info("For the given customer "
									+ customerID
									+ " and delivery location "
									+ deliveryLocation
									+ " the truck type permitted are not available for allocation");
							placementResult = new DefaultPlacementResult(
									PlacementResult.FAILURE,
									"For the given customer "
											+ customerID
											+ " and delivery location "
											+ deliveryLocation
											+ " the truck type permitted are not available for allocation");

						}

						else if ((startCapacity + taskQty < this.capacityLowerBound
								.doubleValue())
								|| (startCapacity + taskQty > this.capacityUpperBound
										.doubleValue())) {
							logger.info("All feasible trucks for this order are occupied between "
									+ new Date(start) + " and " + new Date(end));
							placementResult = new DefaultPlacementResult(
									PlacementResult.FAILURE,
									"All feasible trucks for this order are occupied between "
											+ new Date(start) + " and "
											+ new Date(end));

							// throw
							// RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new
							// Date(start)+" and " +new Date(end));
						}

						else if ((startCapacity > this.capacityLowerBound
								.doubleValue())
								|| (endCapacity > this.capacityLowerBound
										.doubleValue())) {
							logger.info("All feasible trucks for this order are occupied between "
									+ new Date(start) + " and " + new Date(end));

							placementResult = new DefaultPlacementResult(
									PlacementResult.FAILURE,
									"All feasible trucks for this order are occupied between "
											+ new Date(start) + " and "
											+ new Date(end));

							// throw
							// RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new
							// Date(start)+" and " +new Date(end));
						}

						else if ((actualCapacity < 0.0D)
								|| (actualCapacity > 1.0D)) {
							logger.info("All feasible trucks for this order are occupied between "
									+ new Date(start) + " and " + new Date(end));
							placementResult = new DefaultPlacementResult(
									PlacementResult.FAILURE,
									"All feasible trucks for this order are occupied between "
											+ new Date(start) + " and "
											+ new Date(end));
							// throw
							// RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new
							// Date(start)+" and " +new Date(end));
						}

						else if (resource.getActivitiesInInterval(start, end)
								.size() > 0) {
							logger.info("All feasible trucks for this order are occupied between "
									+ new Date(start) + " and " + new Date(end));
							placementResult = new DefaultPlacementResult(
									PlacementResult.FAILURE,
									"No truck is feasible to fulfill this order between "
											+ new Date(start) + " and "
											+ new Date(end));
							// throw
							// RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new
							// Date(start)+" and " +new Date(end));
						} else if ((previousActs.size() > 0)
								&& (localDeliveryCount < 1)) {
							logger.info("Truck has not completed any local orders ");
							placementResult = new DefaultPlacementResult(
									PlacementResult.FAILURE,
									"System is not able to find the truck which had done atleast a local order to fulfill this outstation trip .");

						} else if (start < System.currentTimeMillis()) {
							logger.info("Start time to fulfill this order is less than the current time");
							placementResult = new DefaultPlacementResult(
									PlacementResult.FAILURE,
									"The Expected pickup time can not be fulfilled since the current time crossed over.");

						}

						/*else if (estPickupTime > latestPickupTimeOfTheDay) {
							logger.info("The Expected pickup time can not be fulfilled since the latest pickup time will cross over.");
							placementResult = new DefaultPlacementResult(
									PlacementResult.FAILURE,
									"The Expected pickup time can not be fulfilled since the latest pickup time will cross over.");

							// throw
							// RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new
							// Date(start)+" and " +new Date(end));
						} */else if (end > latestDeliveryTimeOfTheDay) {
							logger.info("Derived End time is greater than the required delivery time");
							placementResult = new DefaultPlacementResult(
									PlacementResult.FAILURE,
									"The feasible trucks to fulfill this order cannot complete the trip within End time is greater than the required delivery time.");
							// throw
							// RSPMessages.newRspMsgEx("Derived End time is greater than the required delivery time");
						}

						else {
							logger.info("start->" + new Date(start));
							logger.info("end->" + new Date(end));
							placementResult = new DefaultPlacementResult(
									PlacementResult.SUCCESS);
							placementResult.setStart(start);
							placementResult.setEnd(end);
							placementResult.setQuantity(this.quantity);
							placementResult.setResource(resource);

							remarks.setValue("");

							logger.info("PlacementResult: " + placementResult);

						}

					} else {

						logger.info("LocalOrders");

						if (noOfDrops > 1.0) {

							logger.info("Local Order With Multiple No.of Drops");
							long deliveryTime1 = start + currToPickupDur
									+ tripDuration + loadingBuffer;

							logger.info("1st delivery: "
									+ new Date(deliveryTime1));

							long inTransitTravelTime = (long) 9e5;

							end = (long) (deliveryTime1 + unloadingBuffer + (noOfDrops - 1)
									* (inTransitTravelTime + unloadingBuffer));

							logger.info("start->" + new Date(start));
							logger.info("end->" + new Date(end));

							List<Activity> previousActs = resource
									.getActivitiesInInterval(startTimeoftheDay,
											start);
							long prevDur = 0L;
							for (Activity acts : previousActs) {
								long prevDuration = acts.getDuration();
								prevDur = +prevDuration;
							}

							FloatState capacityState = (FloatState) resource
									.getStateVariableBy(CAPACITYSTATE_KEY);
							double startCapacity = ((Double) capacityState
									.getValueAt(start).getValue())
									.doubleValue();
							double endCapacity = ((Double) capacityState
									.getValueAt(end).getValue()).doubleValue();
							double actualCapacity = ((Double) capacityState
									.getActualState().getValue()).doubleValue();

							Objectives we = task.getObjectives();
							FloatParameter fp = (FloatParameter) we
									.getParameterBy("quantity");
							double taskQty = fp.getValue().doubleValue();

							if (!resourceType
									.equalsIgnoreCase(orderedTruckType)
									&& resourceType
											.equalsIgnoreCase("articulated")
									&& pickup_AT == 0.0) {

								logger.info("For the given customer "
										+ customerID
										+ " and pickup location "
										+ pickupLocation
										+ " the truck type permitted are not available for allocation");
								placementResult = new DefaultPlacementResult(
										PlacementResult.FAILURE,
										"For the given customer "
												+ customerID
												+ " and pickup location "
												+ pickupLocation
												+ " the truck type permitted are not available for allocation");

							} else if (!resourceType
									.equalsIgnoreCase(orderedTruckType)
									&& resourceType
											.equalsIgnoreCase("articulated")
									&& delivery_AT == 0.0) {

								logger.info("For the given customer "
										+ customerID
										+ " and delivery location "
										+ deliveryLocation
										+ " the truck type permitted are not available for allocation");
								placementResult = new DefaultPlacementResult(
										PlacementResult.FAILURE,
										"For the given customer "
												+ customerID
												+ " and delivery location "
												+ deliveryLocation
												+ " the truck type permitted are not available for allocation");

							} else if (!resourceType
									.equalsIgnoreCase(orderedTruckType)
									&& resourceType.equalsIgnoreCase("rigid")
									&& pickup_RT == 0.0) {

								logger.info("For the given customer "
										+ customerID
										+ " and pickup location "
										+ pickupLocation
										+ " the truck type permitted are not available for allocation");
								placementResult = new DefaultPlacementResult(
										PlacementResult.FAILURE,
										"For the given customer "
												+ customerID
												+ " and pickup location "
												+ pickupLocation
												+ " the truck type permitted are not available for allocation");

							} else if (!resourceType
									.equalsIgnoreCase(orderedTruckType)
									&& resourceType.equalsIgnoreCase("rigid")
									&& delivery_RT == 0.0) {

								logger.info("For the given customer "
										+ customerID
										+ " and delivery location "
										+ deliveryLocation
										+ " the truck type permitted are not available for allocation");
								placementResult = new DefaultPlacementResult(
										PlacementResult.FAILURE,
										"For the given customer "
												+ customerID
												+ " and delivery location "
												+ deliveryLocation
												+ " the truck type permitted are not available for allocation");

							}

							else if (!resourceType
									.equalsIgnoreCase(orderedTruckType)
									&& resourceType.equalsIgnoreCase("single")
									&& pickup_ST == 0.0) {

								logger.info("For the given customer "
										+ customerID
										+ " and pickup location "
										+ pickupLocation
										+ " the truck type permitted are not available for allocation");
								placementResult = new DefaultPlacementResult(
										PlacementResult.FAILURE,
										"For the given customer "
												+ customerID
												+ " and pickup location "
												+ pickupLocation
												+ " the truck type permitted are not available for allocation");

							} else if (!resourceType
									.equalsIgnoreCase(orderedTruckType)
									&& resourceType.equalsIgnoreCase("single")
									&& delivery_ST == 0.0) {

								logger.info("For the given customer "
										+ customerID
										+ " and delivery location "
										+ deliveryLocation
										+ " the truck type permitted are not available for allocation");
								placementResult = new DefaultPlacementResult(
										PlacementResult.FAILURE,
										"For the given customer "
												+ customerID
												+ " and delivery location "
												+ deliveryLocation
												+ " the truck type permitted are not available for allocation");

							}

							else if ((startCapacity + taskQty < this.capacityLowerBound
									.doubleValue())
									|| (startCapacity + taskQty > this.capacityUpperBound
											.doubleValue())) {
								logger.info("All feasible trucks for this order are occupied between "
										+ new Date(start)
										+ " and "
										+ new Date(end));
								placementResult = new DefaultPlacementResult(
										PlacementResult.FAILURE,
										"All feasible trucks for this order are occupied between "
												+ new Date(start) + " and "
												+ new Date(end));
								// throw
								// RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new
								// Date(start)+" and " +new Date(end));
							}

							else if (resource.getActivitiesInInterval(start,
									end).size() > 0) {
								logger.info("All feasible trucks for this order are occupied between "
										+ new Date(start)
										+ " and "
										+ new Date(end));
								placementResult = new DefaultPlacementResult(
										PlacementResult.FAILURE,
										"All feasible trucks for this order are occupied between "
												+ new Date(start) + " and "
												+ new Date(end));
							}

							else if ((startCapacity > this.capacityLowerBound
									.doubleValue())
									|| (endCapacity > this.capacityLowerBound
											.doubleValue())) {
								logger.info("All feasible trucks for this order are occupied between "
										+ new Date(start)
										+ " and "
										+ new Date(end));
								placementResult = new DefaultPlacementResult(
										PlacementResult.FAILURE,
										"All feasible trucks for this order are occupied between "
												+ new Date(start) + " and "
												+ new Date(end));
								// throw
								// RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new
								// Date(start)+" and " +new Date(end));
							}

							else if ((actualCapacity < 0.0D)
									|| (actualCapacity > 1.0D)) {
								logger.info("All feasible trucks for this order are occupied between "
										+ new Date(start)
										+ " and "
										+ new Date(end));
								placementResult = new DefaultPlacementResult(
										PlacementResult.FAILURE,
										"All feasible trucks for this order are occupied between "
												+ new Date(start) + " and "
												+ new Date(end));
								throw RSPMessages
										.newRspMsgEx("All feasible trucks for this order are occupied between "
												+ new Date(start)
												+ " and "
												+ new Date(end));
							}

							else if (prevDur + (end - start) > maxWorkHrs) {
								logger.info("Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
								placementResult = new DefaultPlacementResult(
										PlacementResult.FAILURE,
										"Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
								// throw
								// RSPMessages.newRspMsgEx("Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
							} else if (start < System.currentTimeMillis()) {
								logger.info("Start time to fulfill this order is less than the current time");
								placementResult = new DefaultPlacementResult(
										PlacementResult.FAILURE,
										"The Expected pickup time can not be fulfilled since the current time crossed over.");
								// throw
								// RSPMessages.newRspMsgEx("Start time to fulfill this order is less than the current time");
							} else if (start + currToPickupDur > latestPickupTimeOfTheDay) {
								logger.info("The Expected pickup time can not be fulfilled since the latest pickup time will cross over.");
								placementResult = new DefaultPlacementResult(
										PlacementResult.FAILURE,
										"The Expected pickup time can not be fulfilled since the latest pickup time will cross over.");

								// throw
								// RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new
								// Date(start)+" and " +new Date(end));
							} else if (end > latestDeliveryTimeOfTheDay) {
								logger.info("Derived End time is greater than the required delivery time");
								placementResult = new DefaultPlacementResult(
										PlacementResult.FAILURE,
										"The feasible trucks to fulfill this order cannot complete the trip within End time is greater than the required delivery time.");
								// throw
								// RSPMessages.newRspMsgEx("Derived End time is greater than the required delivery time");
							}

							else {

								logger.info("START: " + new Date(start));
								logger.info("END:" + new Date(end));

								placementResult = new DefaultPlacementResult(
										PlacementResult.SUCCESS);
								placementResult.setStart(start);
								placementResult.setEnd(end);
								placementResult.setQuantity(this.quantity);
								placementResult.setResource(resource);

								remarks.setValue("");

								logger.info("PlacementResult: "
										+ placementResult);
								// return placementResult;

							}

						}

						else {

							long totalTripDur = currToPickupDur + tripDuration;

							logger.info("totalTripDuration: " + totalTripDur);

							Date Startdate = new Date(start);
							logger.info("startTime:" + Startdate);
							logger.info("resource evaluated in placement"
									+ resource);

							Date endDate = new Date(end);
							logger.info("endTime" + endDate);

							if (totalTripDur <= 7200000.0D) {

								logger.info("totalTripDur is less than 2 hrs");

								logger.info("tripDuration" + totalTripDur
										/ 3600000);
								logger.info("currToPickupDur: "
										+ currToPickupDur / 3600000);

								// long buffer = 2700000L;

								logger.info("STARTBefore:" + new Date(start));
								logger.info("EndBefore:" + new Date(end));
								// start += buffer;
								long estPickupTime = start + currToPickupDur;

								if ((estPickupTime + loadingBuffer) >= getTime(
										pickupTime, pickupLocPeakStart1)
										&& estPickupTime <= getTime(pickupTime,
												pickupLocPeakEnd1)) {

									logger.info("pickupTime lies between peak1");
									long latePickupBuffer = getTime(pickupTime,
											pickupLocPeakEnd1) - estPickupTime;

									start = start + latePickupBuffer;
								}

								if ((estPickupTime + loadingBuffer) >= getTime(
										pickupTime, pickupLocPeakStart2)
										&& estPickupTime <= getTime(pickupTime,
												pickupLocPeakEnd2)) {
									logger.info("pickupTime lies between peak2");
									long latePickupBuffer = getTime(pickupTime,
											pickupLocPeakEnd2) - estPickupTime;

									start = start + latePickupBuffer;
								}

								if ((estPickupTime + loadingBuffer) >= getTime(
										pickupTime, pickupLocPeakStart3)
										&& estPickupTime <= getTime(pickupTime,
												pickupLocPeakEnd3)) {
									logger.info("pickupTime lies between peak3");
									long latePickupBuffer = getTime(pickupTime,
											pickupLocPeakEnd3) - estPickupTime;

									start = start + latePickupBuffer;
								}
								if ((estPickupTime + loadingBuffer) >= getTime(
										pickupTime, pickupLocPeakStart4)
										&& estPickupTime <= getTime(pickupTime,
												pickupLocPeakEnd4)) {
									logger.info("pickupTime lies between peak4");
									long latePickupBuffer = getTime(pickupTime,
											pickupLocPeakEnd1) - estPickupTime;

									start = start + latePickupBuffer;
								}

								end = start + currToPickupDur + tripDuration
										+ loadingBuffer + unloadingBuffer;

								long estimatedDelivery = end - unloadingBuffer;

								logger.info("estimatedDelivery->"
										+ estimatedDelivery);

								if ((estimatedDelivery + unloadingBuffer) >= getTime(
										deliveryTime, deliveryLocPeakStart1)
										&& (end - unloadingBuffer) <= getTime(
												deliveryTime,
												deliveryLocPeakEnd1)) {
									logger.info("deliveryTime lies between peak1");
									long lateDeliveryBuffer = getTime(
											deliveryTime, deliveryLocPeakEnd1)
											- estimatedDelivery;

									start = start + lateDeliveryBuffer;
									end = end + lateDeliveryBuffer;
								}

								if ((estimatedDelivery + unloadingBuffer) >= getTime(
										deliveryTime, deliveryLocPeakStart2)
										&& (end - unloadingBuffer) <= getTime(
												deliveryTime,
												deliveryLocPeakEnd2)) {
									logger.info("deliveryTime lies between peak2");
									long lateDeliveryBuffer = getTime(
											deliveryTime, deliveryLocPeakEnd2)
											- estimatedDelivery;

									start = start + lateDeliveryBuffer;
									end = end + lateDeliveryBuffer;
								}

								if ((estimatedDelivery + unloadingBuffer) >= getTime(
										deliveryTime, deliveryLocPeakStart3)
										&& (end - unloadingBuffer) <= getTime(
												deliveryTime,
												deliveryLocPeakEnd3)) {
									logger.info("deliveryTime lies between peak3");
									long lateDeliveryBuffer = getTime(
											deliveryTime, deliveryLocPeakEnd3)
											- estimatedDelivery;

									start = start + lateDeliveryBuffer;
									end = end + lateDeliveryBuffer;
								}

								if ((estimatedDelivery + unloadingBuffer) >= getTime(
										deliveryTime, deliveryLocPeakStart4)
										&& (end - unloadingBuffer) <= getTime(
												deliveryTime,
												deliveryLocPeakEnd4)) {
									logger.info("deliveryTime lies between peak4");
									long lateDeliveryBuffer = getTime(
											deliveryTime, deliveryLocPeakEnd4)
											- estimatedDelivery;

									start = start + lateDeliveryBuffer;
									end = end + lateDeliveryBuffer;
								}
								logger.info("currentTime: "
										+ new Date(currentTime) + " START: "
										+ new Date(start));
								logger.info("END:" + new Date(end));

								List<Activity> previousActs = resource
										.getActivitiesInInterval(
												startTimeoftheDay, start);
								long prevDur = 0L;
								for (Activity acts : previousActs) {
									long prevDuration = acts.getDuration();
									prevDur = prevDuration;
								}

								FloatState capacityState = (FloatState) resource
										.getStateVariableBy(CAPACITYSTATE_KEY);
								double startCapacity = ((Double) capacityState
										.getValueAt(start).getValue())
										.doubleValue();
								double endCapacity = ((Double) capacityState
										.getValueAt(end).getValue())
										.doubleValue();
								double actualCapacity = ((Double) capacityState
										.getActualState().getValue())
										.doubleValue();

								Objectives we = task.getObjectives();
								FloatParameter fp = (FloatParameter) we
										.getParameterBy("quantity");
								double taskQty = fp.getValue().doubleValue();

								if (!resourceType
										.equalsIgnoreCase(orderedTruckType)
										&& resourceType
												.equalsIgnoreCase("articulated")
										&& pickup_AT == 0.0) {

									logger.info("For the given customer "
											+ customerID
											+ " and pickup location "
											+ pickupLocation
											+ " the truck type permitted are not available for allocation");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"For the given customer "
													+ customerID
													+ " and pickup location "
													+ pickupLocation
													+ " the truck type permitted are not available for allocation");

								} else if (!resourceType
										.equalsIgnoreCase(orderedTruckType)
										&& resourceType
												.equalsIgnoreCase("articulated")
										&& delivery_AT == 0.0) {

									logger.info("For the given customer "
											+ customerID
											+ " and delivery location "
											+ deliveryLocation
											+ " the truck type permitted are not available for allocation");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"For the given customer "
													+ customerID
													+ " and delivery location "
													+ deliveryLocation
													+ " the truck type permitted are not available for allocation");

								} else if (!resourceType
										.equalsIgnoreCase(orderedTruckType)
										&& resourceType
												.equalsIgnoreCase("rigid")
										&& pickup_RT == 0.0) {

									logger.info("For the given customer "
											+ customerID
											+ " and pickup location "
											+ pickupLocation
											+ " the truck type permitted are not available for allocation");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"For the given customer "
													+ customerID
													+ " and pickup location "
													+ pickupLocation
													+ " the truck type permitted are not available for allocation");

								} else if (!resourceType
										.equalsIgnoreCase(orderedTruckType)
										&& resourceType
												.equalsIgnoreCase("rigid")
										&& delivery_RT == 0.0) {

									logger.info("For the given customer "
											+ customerID
											+ " and delivery location "
											+ deliveryLocation
											+ " the truck type permitted are not available for allocation");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"For the given customer "
													+ customerID
													+ " and delivery location "
													+ deliveryLocation
													+ " the truck type permitted are not available for allocation");

								}

								else if (!resourceType
										.equalsIgnoreCase(orderedTruckType)
										&& resourceType
												.equalsIgnoreCase("single")
										&& pickup_ST == 0.0) {

									logger.info("For the given customer "
											+ customerID
											+ " and pickup location "
											+ pickupLocation
											+ " the truck type permitted are not available for allocation");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"For the given customer "
													+ customerID
													+ " and pickup location "
													+ pickupLocation
													+ " the truck type permitted are not available for allocation");

								} else if (!resourceType
										.equalsIgnoreCase(orderedTruckType)
										&& resourceType
												.equalsIgnoreCase("single")
										&& delivery_ST == 0.0) {

									logger.info("For the given customer "
											+ customerID
											+ " and delivery location "
											+ deliveryLocation
											+ " the truck type permitted are not available for allocation");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"For the given customer "
													+ customerID
													+ " and delivery location "
													+ deliveryLocation
													+ " the truck type permitted are not available for allocation");

								} else if ((startCapacity + taskQty < this.capacityLowerBound
										.doubleValue())
										|| (startCapacity + taskQty > this.capacityUpperBound
												.doubleValue())) {
									logger.info("All feasible trucks for this order are occupied between "
											+ new Date(start)
											+ " and "
											+ new Date(end));
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"All feasible trucks for this order are occupied between "
													+ new Date(start) + " and "
													+ new Date(end));
									// throw
									// RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new
									// Date(start)+" and " +new Date(end));
								}

								else if (resource.getActivitiesInInterval(
										start, end).size() > 0) {
									logger.info("All feasible trucks for this order are occupied between "
											+ new Date(start)
											+ " and "
											+ new Date(end));
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"All feasible trucks for this order are occupied between "
													+ new Date(start) + " and "
													+ new Date(end));
								}

								else if ((startCapacity > this.capacityLowerBound
										.doubleValue())
										|| (endCapacity > this.capacityLowerBound
												.doubleValue())) {
									logger.info("All feasible trucks for this order are occupied between "
											+ new Date(start)
											+ " and "
											+ new Date(end));
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"All feasible trucks for this order are occupied between "
													+ new Date(start) + " and "
													+ new Date(end));
									// throw
									// RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new
									// Date(start)+" and " +new Date(end));
								}

								else if ((actualCapacity < 0.0D)
										|| (actualCapacity > 1.0D)) {
									logger.info("All feasible trucks for this order are occupied between "
											+ new Date(start)
											+ " and "
											+ new Date(end));
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"All feasible trucks for this order are occupied between "
													+ new Date(start) + " and "
													+ new Date(end));
									throw RSPMessages
											.newRspMsgEx("All feasible trucks for this order are occupied between "
													+ new Date(start)
													+ " and "
													+ new Date(end));
								} else if (prevDur + (end - start) > maxWorkHrs) {
									logger.info("Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
									// throw
									// RSPMessages.newRspMsgEx("Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
								} else if (start < System.currentTimeMillis()) {
									logger.info("Start time to fulfill this order is less than the current time");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"The Expected pickup time can not be fulfilled since the current time crossed over.");
									// throw
									// RSPMessages.newRspMsgEx("Start time to fulfill this order is less than the current time");
								} else if (estPickupTime > latestPickupTimeOfTheDay) {
									logger.info("The Expected pickup time can not be fulfilled since the latest pickup time will cross over.");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"The Expected pickup time can not be fulfilled since the latest pickup time will cross over.");

									// throw
									// RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new
									// Date(start)+" and " +new Date(end));
								} else if (end > latestDeliveryTimeOfTheDay) {
									logger.info("Derived End time is greater than the required delivery time");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"The feasible trucks to fulfill this order cannot complete the trip within End time is greater than the required delivery time.");
									// throw
									// RSPMessages.newRspMsgEx("Derived End time is greater than the required delivery time");
								}

								else {

									logger.info("START: " + new Date(start));
									logger.info("END:" + new Date(end));

									placementResult = new DefaultPlacementResult(
											PlacementResult.SUCCESS);
									placementResult.setStart(start);
									placementResult.setEnd(end);
									placementResult.setQuantity(this.quantity);
									placementResult.setResource(resource);

									remarks.setValue("");

									logger.info("PlacementResult: "
											+ placementResult);
									// return placementResult;
								}

							}

							else if ((totalTripDur > 7200000.0D)
									&& (totalTripDur <= 14400000.0D)) {

								logger.info("totalTripDur is less than 4 hrs and greater than 2 hrs");
								logger.info("tripDuration" + totalTripDur
										/ 3600000);
								logger.info("currToPickupDur: "
										+ currToPickupDur / 3600000);

								logger.info("STARTBefore:" + new Date(start));
								logger.info("EndBefore:" + new Date(end));
								// start += buffer;

								long estPickupTime = 0;

								if (start + currToPickupDur < 7200000.0D)
									estPickupTime = start + currToPickupDur;

								else if (start + currToPickupDur > 7200000.0D)
									estPickupTime = start + currToPickupDur
											+ restHrs4;

								if ((estPickupTime + loadingBuffer) >= getTime(
										pickupTime, pickupLocPeakStart1)
										&& estPickupTime <= getTime(pickupTime,
												pickupLocPeakEnd1)) {

									logger.info("pickupTime lies between peak1");
									long latePickupBuffer = getTime(pickupTime,
											pickupLocPeakEnd1) - estPickupTime;

									start = start + latePickupBuffer;
								}

								if ((estPickupTime + loadingBuffer) >= getTime(
										pickupTime, pickupLocPeakStart2)
										&& estPickupTime <= getTime(pickupTime,
												pickupLocPeakEnd2)) {
									logger.info("pickupTime lies between peak2");
									long latePickupBuffer = getTime(pickupTime,
											pickupLocPeakEnd2) - estPickupTime;

									start = start + latePickupBuffer;
								}

								if ((estPickupTime + loadingBuffer) >= getTime(
										pickupTime, pickupLocPeakStart3)
										&& estPickupTime <= getTime(pickupTime,
												pickupLocPeakEnd3)) {
									logger.info("pickupTime lies between peak3");
									long latePickupBuffer = getTime(pickupTime,
											pickupLocPeakEnd3) - estPickupTime;

									start = start + latePickupBuffer;
								}
								if ((estPickupTime + loadingBuffer) >= getTime(
										pickupTime, pickupLocPeakStart4)
										&& estPickupTime <= getTime(pickupTime,
												pickupLocPeakEnd4)) {
									logger.info("pickupTime lies between peak4");
									long latePickupBuffer = getTime(pickupTime,
											pickupLocPeakEnd1) - estPickupTime;

									start = start + latePickupBuffer;
								}

								end = start + currToPickupDur + tripDuration
										+ restHrs4 + loadingBuffer
										+ unloadingBuffer;

								long estimatedDelivery = end - unloadingBuffer;
								logger.info("estimatedDelivery->"
										+ estimatedDelivery);

								if ((estimatedDelivery + unloadingBuffer) >= getTime(
										deliveryTime, deliveryLocPeakStart1)
										&& (end - unloadingBuffer) <= getTime(
												deliveryTime,
												deliveryLocPeakEnd1)) {
									logger.info("deliveryTime lies between peak1");
									long lateDeliveryBuffer = getTime(
											deliveryTime, deliveryLocPeakEnd1)
											- estimatedDelivery;

									start = start + lateDeliveryBuffer;
									end = end + lateDeliveryBuffer;
								}

								if ((estimatedDelivery + unloadingBuffer) >= getTime(
										deliveryTime, deliveryLocPeakStart2)
										&& (end - unloadingBuffer) <= getTime(
												deliveryTime,
												deliveryLocPeakEnd2)) {
									logger.info("deliveryTime lies between peak2");
									long lateDeliveryBuffer = getTime(
											deliveryTime, deliveryLocPeakEnd2)
											- estimatedDelivery;

									start = start + lateDeliveryBuffer;
									end = end + lateDeliveryBuffer;
								}

								if ((estimatedDelivery + unloadingBuffer) >= getTime(
										deliveryTime, deliveryLocPeakStart3)
										&& (end - unloadingBuffer) <= getTime(
												deliveryTime,
												deliveryLocPeakEnd3)) {
									logger.info("deliveryTime lies between peak3");
									long lateDeliveryBuffer = getTime(
											deliveryTime, deliveryLocPeakEnd3)
											- estimatedDelivery;

									start = start + lateDeliveryBuffer;
									end = end + lateDeliveryBuffer;
								}

								if ((estimatedDelivery + unloadingBuffer) >= getTime(
										deliveryTime, deliveryLocPeakStart4)
										&& (end - unloadingBuffer) <= getTime(
												deliveryTime,
												deliveryLocPeakEnd4)) {
									logger.info("deliveryTime lies between peak4");
									long lateDeliveryBuffer = getTime(
											deliveryTime, deliveryLocPeakEnd4)
											- estimatedDelivery;

									start = start + lateDeliveryBuffer;
									end = end + lateDeliveryBuffer;
								}

								logger.info("currentTime: "
										+ new Date(currentTime) + " START: "
										+ new Date(start));
								logger.info("END:" + new Date(end));

								List<Activity> previousActs = resource
										.getActivitiesInInterval(
												startTimeoftheDay, start);
								long prevDur = 0L;
								for (Activity acts : previousActs) {
									long prevDuration = acts.getDuration();
									prevDur = prevDuration;
								}

								FloatState capacityState = (FloatState) resource
										.getStateVariableBy(CAPACITYSTATE_KEY);
								double startCapacity = ((Double) capacityState
										.getValueAt(start).getValue())
										.doubleValue();
								double endCapacity = ((Double) capacityState
										.getValueAt(end).getValue())
										.doubleValue();
								double actualCapacity = ((Double) capacityState
										.getActualState().getValue())
										.doubleValue();

								Objectives we = task.getObjectives();
								FloatParameter fp = (FloatParameter) we
										.getParameterBy("quantity");
								double taskQty = fp.getValue().doubleValue();

								if (!resourceType
										.equalsIgnoreCase(orderedTruckType)
										&& resourceType
												.equalsIgnoreCase("articulated")
										&& pickup_AT == 0.0) {

									logger.info("For the given customer "
											+ customerID
											+ " and pickup location "
											+ pickupLocation
											+ " the truck type permitted are not available for allocation");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"For the given customer "
													+ customerID
													+ " and pickup location "
													+ pickupLocation
													+ " the truck type permitted are not available for allocation");

								} else if (!resourceType
										.equalsIgnoreCase(orderedTruckType)
										&& resourceType
												.equalsIgnoreCase("articulated")
										&& delivery_AT == 0.0) {

									logger.info("For the given customer "
											+ customerID
											+ " and delivery location "
											+ deliveryLocation
											+ " the truck type permitted are not available for allocation");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"For the given customer "
													+ customerID
													+ " and delivery location "
													+ deliveryLocation
													+ " the truck type permitted are not available for allocation");

								} else if (!resourceType
										.equalsIgnoreCase(orderedTruckType)
										&& resourceType
												.equalsIgnoreCase("rigid")
										&& pickup_RT == 0.0) {

									logger.info("For the given customer "
											+ customerID
											+ " and pickup location "
											+ pickupLocation
											+ " the truck type permitted are not available for allocation");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"For the given customer "
													+ customerID
													+ " and pickup location "
													+ pickupLocation
													+ " the truck type permitted are not available for allocation");

								} else if (!resourceType
										.equalsIgnoreCase(orderedTruckType)
										&& resourceType
												.equalsIgnoreCase("rigid")
										&& delivery_RT == 0.0) {

									logger.info("For the given customer "
											+ customerID
											+ " and delivery location "
											+ deliveryLocation
											+ " the truck type permitted are not available for allocation");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"For the given customer "
													+ customerID
													+ " and delivery location "
													+ deliveryLocation
													+ " the truck type permitted are not available for allocation");

								}

								else if (!resourceType
										.equalsIgnoreCase(orderedTruckType)
										&& resourceType
												.equalsIgnoreCase("single")
										&& pickup_ST == 0.0) {

									logger.info("For the given customer "
											+ customerID
											+ " and pickup location "
											+ pickupLocation
											+ " the truck type permitted are not available for allocation");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"For the given customer "
													+ customerID
													+ " and pickup location "
													+ pickupLocation
													+ " the truck type permitted are not available for allocation");

								} else if (!resourceType
										.equalsIgnoreCase(orderedTruckType)
										&& resourceType
												.equalsIgnoreCase("single")
										&& delivery_ST == 0.0) {

									logger.info("For the given customer "
											+ customerID
											+ " and delivery location "
											+ deliveryLocation
											+ " the truck type permitted are not available for allocation");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"For the given customer "
													+ customerID
													+ " and delivery location "
													+ deliveryLocation
													+ " the truck type permitted are not available for allocation");

								} else if ((startCapacity + taskQty < this.capacityLowerBound
										.doubleValue())
										|| (startCapacity + taskQty > this.capacityUpperBound
												.doubleValue())) {
									logger.info("All feasible trucks for this order are occupied between "
											+ new Date(start)
											+ " and "
											+ new Date(end));
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"All feasible trucks for this order are occupied between "
													+ new Date(start) + " and "
													+ new Date(end));
									// throw
									// RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new
									// Date(start)+" and " +new Date(end));
								}

								else if ((startCapacity > this.capacityLowerBound
										.doubleValue())
										|| (endCapacity > this.capacityLowerBound
												.doubleValue())) {
									logger.info("All feasible trucks for this order are occupied between "
											+ new Date(start)
											+ " and "
											+ new Date(end));
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"All feasible trucks for this order are occupied between "
													+ new Date(start) + " and "
													+ new Date(end));
									// throw
									// RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new
									// Date(start)+" and " +new Date(end));
								}

								else if ((actualCapacity < 0.0D)
										|| (actualCapacity > 1.0D)) {
									logger.info("All feasible trucks for this order are occupied between "
											+ new Date(start)
											+ " and "
											+ new Date(end));
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"All feasible trucks for this order are occupied between "
													+ new Date(start) + " and "
													+ new Date(end));
									// throw
									// RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new
									// Date(start)+" and " +new Date(end));
								}

								else if (resource.getActivitiesInInterval(
										start, end).size() > 0) {
									logger.info("All feasible trucks for this order are occupied between "
											+ new Date(start)
											+ " and "
											+ new Date(end));
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"All feasible trucks for this order are occupied between "
													+ new Date(start) + " and "
													+ new Date(end));
								}

								else if (prevDur + (end - start) > maxWorkHrs) {
									logger.info("Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
									// throw
									// RSPMessages.newRspMsgEx("Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
								} else if (start < System.currentTimeMillis()) {
									logger.info("Start time to fulfill this order is less than the current time");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"The Expected pickup time can not be fulfilled since the current time crossed over.");
									// throw
									// RSPMessages.newRspMsgEx("Start time to fulfill this order is less than the current time");
								}

								else if (estPickupTime > latestPickupTimeOfTheDay) {
									logger.info("The Expected pickup time can not be fulfilled since the latest pickup time will cross over.");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"The Expected pickup time can not be fulfilled since the latest pickup time will cross over.");

									// throw
									// RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new
									// Date(start)+" and " +new Date(end));
								} else if (end > latestDeliveryTimeOfTheDay) {
									logger.info("Derived End time is greater than the required delivery time");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"The feasible trucks to fulfill this order cannot complete the trip within End time is greater than the required delivery time.");
									// throw
									// RSPMessages.newRspMsgEx("Derived End time is greater than the required delivery time");
								} else {

									logger.info("START: " + new Date(start));
									logger.info("END:" + new Date(end));

									placementResult = new DefaultPlacementResult(
											PlacementResult.SUCCESS);
									placementResult.setStart(start);
									placementResult.setEnd(end);
									placementResult.setQuantity(this.quantity);
									placementResult.setResource(resource);

									remarks.setValue("");

									logger.info("PlacementResult: "
											+ placementResult);
									// return placementResult;

								}

							} else if ((totalTripDur > 14400000.0D)
									&& (totalTripDur <= 28800000.0D)) {
								logger.info("totalTripDur is less than 8 hrs and greater than 4 hrs");

								logger.info("tripDuration" + totalTripDur
										/ 3600000);
								logger.info("currToPickupDur: "
										+ currToPickupDur / 3600000);
								logger.info("STARTBefore:" + new Date(start));
								logger.info("EndBefore:" + new Date(end));
								// start += buffer;

								long estPickupTime = 0;

								if (start + currToPickupDur < 14400000.0D)
									estPickupTime = start + currToPickupDur;

								if (start + currToPickupDur > 14400000.0D)
									estPickupTime = start + currToPickupDur
											+ restHrs8;

								if ((estPickupTime + loadingBuffer) >= getTime(
										pickupTime, pickupLocPeakStart1)
										&& estPickupTime <= getTime(pickupTime,
												pickupLocPeakEnd1)) {

									logger.info("pickupTime lies between peak1");
									long latePickupBuffer = getTime(pickupTime,
											pickupLocPeakEnd1) - estPickupTime;

									start = start + latePickupBuffer;
								}

								if ((estPickupTime + loadingBuffer) >= getTime(
										pickupTime, pickupLocPeakStart2)
										&& estPickupTime <= getTime(pickupTime,
												pickupLocPeakEnd2)) {
									logger.info("pickupTime lies between peak2");
									long latePickupBuffer = getTime(pickupTime,
											pickupLocPeakEnd2) - estPickupTime;

									start = start + latePickupBuffer;
								}

								if ((estPickupTime + loadingBuffer) >= getTime(
										pickupTime, pickupLocPeakStart3)
										&& estPickupTime <= getTime(pickupTime,
												pickupLocPeakEnd3)) {
									logger.info("pickupTime lies between peak3");
									long latePickupBuffer = getTime(pickupTime,
											pickupLocPeakEnd3) - estPickupTime;

									start = start + latePickupBuffer;
								}
								if ((estPickupTime + loadingBuffer) >= getTime(
										pickupTime, pickupLocPeakStart4)
										&& estPickupTime <= getTime(pickupTime,
												pickupLocPeakEnd4)) {

									logger.info("pickupTime lies between peak4");
									long latePickupBuffer = getTime(pickupTime,
											pickupLocPeakEnd1) - estPickupTime;

									start = start + latePickupBuffer;
								}

								end = start + currToPickupDur + tripDuration
										+ restHrs8 + loadingBuffer
										+ unloadingBuffer;

								long estimatedDelivery = end - unloadingBuffer;
								logger.info("estimatedDelivery->"
										+ estimatedDelivery);

								if ((estimatedDelivery + unloadingBuffer) >= getTime(
										deliveryTime, deliveryLocPeakStart1)
										&& (end - unloadingBuffer) <= getTime(
												deliveryTime,
												deliveryLocPeakEnd1)) {
									logger.info("deliveryTime lies between peak1");
									long lateDeliveryBuffer = getTime(
											deliveryTime, deliveryLocPeakEnd1)
											- estimatedDelivery;

									start = start + lateDeliveryBuffer;
									end = end + lateDeliveryBuffer;
								}

								if ((estimatedDelivery + unloadingBuffer) >= getTime(
										deliveryTime, deliveryLocPeakStart2)
										&& (end - unloadingBuffer) <= getTime(
												deliveryTime,
												deliveryLocPeakEnd2)) {
									logger.info("deliveryTime lies between peak2");
									long lateDeliveryBuffer = getTime(
											deliveryTime, deliveryLocPeakEnd2)
											- estimatedDelivery;

									start = start + lateDeliveryBuffer;
									end = end + lateDeliveryBuffer;
								}

								if ((estimatedDelivery + unloadingBuffer) >= getTime(
										deliveryTime, deliveryLocPeakStart3)
										&& (end - unloadingBuffer) <= getTime(
												deliveryTime,
												deliveryLocPeakEnd3)) {
									logger.info("deliveryTime lies between peak3");
									long lateDeliveryBuffer = getTime(
											deliveryTime, deliveryLocPeakEnd3)
											- estimatedDelivery;

									start = start + lateDeliveryBuffer;
									end = end + lateDeliveryBuffer;
								}

								if ((estimatedDelivery + unloadingBuffer) >= getTime(
										deliveryTime, deliveryLocPeakStart4)
										&& (end - unloadingBuffer) <= getTime(
												deliveryTime,
												deliveryLocPeakEnd4)) {
									logger.info("deliveryTime lies between peak4");
									long lateDeliveryBuffer = getTime(
											deliveryTime, deliveryLocPeakEnd4)
											- estimatedDelivery;

									start = start + lateDeliveryBuffer;
									end = end + lateDeliveryBuffer;
								}

								logger.info("currentTime: "
										+ new Date(currentTime) + " START: "
										+ new Date(start));
								logger.info("END: " + new Date(end));

								List<Activity> previousActs = resource
										.getActivitiesInInterval(
												startTimeoftheDay, start);
								long prevDur = 0L;
								for (Activity acts : previousActs) {
									long prevDuration = acts.getDuration();
									prevDur = prevDuration;
								}

								FloatState capacityState = (FloatState) resource
										.getStateVariableBy(CAPACITYSTATE_KEY);
								double startCapacity = ((Double) capacityState
										.getValueAt(start).getValue())
										.doubleValue();
								double endCapacity = ((Double) capacityState
										.getValueAt(end).getValue())
										.doubleValue();
								double actualCapacity = ((Double) capacityState
										.getActualState().getValue())
										.doubleValue();

								Objectives we = task.getObjectives();
								FloatParameter fp = (FloatParameter) we
										.getParameterBy("quantity");
								double taskQty = fp.getValue().doubleValue();

								if (!resourceType
										.equalsIgnoreCase(orderedTruckType)
										&& resourceType
												.equalsIgnoreCase("articulated")
										&& pickup_AT == 0.0) {

									logger.info("For the given customer "
											+ customerID
											+ " and pickup location "
											+ pickupLocation
											+ " the truck type permitted are not available for allocation");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"For the given customer "
													+ customerID
													+ " and pickup location "
													+ pickupLocation
													+ " the truck type permitted are not available for allocation");

								} else if (!resourceType
										.equalsIgnoreCase(orderedTruckType)
										&& resourceType
												.equalsIgnoreCase("articulated")
										&& delivery_AT == 0.0) {

									logger.info("For the given customer "
											+ customerID
											+ " and delivery location "
											+ deliveryLocation
											+ " the truck type permitted are not available for allocation");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"For the given customer "
													+ customerID
													+ " and delivery location "
													+ deliveryLocation
													+ " the truck type permitted are not available for allocation");

								} else if (!resourceType
										.equalsIgnoreCase(orderedTruckType)
										&& resourceType
												.equalsIgnoreCase("rigid")
										&& pickup_RT == 0.0) {

									logger.info("For the given customer "
											+ customerID
											+ " and pickup location "
											+ pickupLocation
											+ " the truck type permitted are not available for allocation");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"For the given customer "
													+ customerID
													+ " and pickup location "
													+ pickupLocation
													+ " the truck type permitted are not available for allocation");

								} else if (!resourceType
										.equalsIgnoreCase(orderedTruckType)
										&& resourceType
												.equalsIgnoreCase("rigid")
										&& delivery_RT == 0.0) {

									logger.info("For the given customer "
											+ customerID
											+ " and delivery location "
											+ deliveryLocation
											+ " the truck type permitted are not available for allocation");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"For the given customer "
													+ customerID
													+ " and delivery location "
													+ deliveryLocation
													+ " the truck type permitted are not available for allocation");

								}

								else if (!resourceType
										.equalsIgnoreCase(orderedTruckType)
										&& resourceType
												.equalsIgnoreCase("single")
										&& pickup_ST == 0.0) {

									logger.info("For the given customer "
											+ customerID
											+ " and pickup location "
											+ pickupLocation
											+ " the truck type permitted are not available for allocation");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"For the given customer "
													+ customerID
													+ " and pickup location "
													+ pickupLocation
													+ " the truck type permitted are not available for allocation");

								} else if (!resourceType
										.equalsIgnoreCase(orderedTruckType)
										&& resourceType
												.equalsIgnoreCase("single")
										&& delivery_ST == 0.0) {

									logger.info("For the given customer "
											+ customerID
											+ " and delivery location "
											+ deliveryLocation
											+ " the truck type permitted are not available for allocation");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"For the given customer "
													+ customerID
													+ " and delivery location "
													+ deliveryLocation
													+ " the truck type permitted are not available for allocation");

								} else if ((startCapacity + taskQty < this.capacityLowerBound
										.doubleValue())
										|| (startCapacity + taskQty > this.capacityUpperBound
												.doubleValue())) {
									logger.info("All feasible trucks for this order are occupied between "
											+ new Date(start)
											+ " and "
											+ new Date(end));
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"All feasible trucks for this order are occupied between "
													+ new Date(start) + " and "
													+ new Date(end));
									// throw
									// RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new
									// Date(start)+" and " +new Date(end));
								}

								else if ((startCapacity > this.capacityLowerBound
										.doubleValue())
										|| (endCapacity > this.capacityLowerBound
												.doubleValue())) {
									logger.info("All feasible trucks for this order are occupied between "
											+ new Date(start)
											+ " and "
											+ new Date(end));
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"All feasible trucks for this order are occupied between "
													+ new Date(start) + " and "
													+ new Date(end));
									// throw
									// RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new
									// Date(start)+" and " +new Date(end));
								}

								else if ((actualCapacity < 0.0D)
										|| (actualCapacity > 1.0D)) {
									logger.info("All feasible trucks for this order are occupied between "
											+ new Date(start)
											+ " and "
											+ new Date(end));
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"All feasible trucks for this order are occupied between "
													+ new Date(start) + " and "
													+ new Date(end));
									// throw
									// RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new
									// Date(start)+" and " +new Date(end));
								}

								else if (resource.getActivitiesInInterval(
										start, end).size() > 0) {
									logger.info("All feasible trucks for this order are occupied between "
											+ new Date(start)
											+ " and "
											+ new Date(end));
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"All feasible trucks for this order are occupied between "
													+ new Date(start) + " and "
													+ new Date(end));
								} else if (prevDur + (end - start) > maxWorkHrs) {
									logger.info("Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
									// throw
									// RSPMessages.newRspMsgEx("Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
								} else if (start < System.currentTimeMillis()) {
									logger.info("Start time to fulfill this order is less than the current time");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"The Expected pickup time can not be fulfilled since the current time crossed over.");
									// throw
									// RSPMessages.newRspMsgEx("Start time to fulfill this order is less than the current time");
								} else if (estPickupTime > latestPickupTimeOfTheDay) {
									logger.info("The Expected pickup time can not be fulfilled since the latest pickup time will cross over.");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"The Expected pickup time can not be fulfilled since the latest pickup time will cross over.");

									// throw
									// RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new
									// Date(start)+" and " +new Date(end));
								} else if (end > latestDeliveryTimeOfTheDay) {
									logger.info("Derived End time is greater than the required delivery time");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"The feasible trucks to fulfill this order cannot complete the trip within End time is greater than the required delivery time.");
									// throw
									// RSPMessages.newRspMsgEx("Derived End time is greater than the required delivery time");
								}

								else {

									logger.info("START: " + new Date(start));
									logger.info("END:" + new Date(end));

									placementResult = new DefaultPlacementResult(
											PlacementResult.SUCCESS);
									placementResult.setStart(start);
									placementResult.setEnd(end);
									placementResult.setQuantity(this.quantity);
									placementResult.setResource(resource);

									remarks.setValue("");

									logger.info("PlacementResult: "
											+ placementResult);
									// return placementResult;
								}

							}

							else if ((totalTripDur > 28800000.0D)) {

								logger.info("totalTripDur is greater than 8 hrs");
								logger.info("tripDuration" + totalTripDur
										/ 3600000);
								logger.info("currToPickupDur: "
										+ currToPickupDur / 3600000);

								logger.info("STARTBefore:" + new Date(start));
								logger.info("EndBefore:" + new Date(end));
								// start += buffer;

								long estPickupTime = 0;

								if (start + currToPickupDur <= 14400000.0D)
									estPickupTime = start + currToPickupDur;

								else if ((start + currToPickupDur) > 14400000.0D
										&& (start + currToPickupDur) <= 28800000.0D)
									estPickupTime = start + currToPickupDur
											+ restHrs111;

								else if (start + currToPickupDur > 28800000.0D)
									estPickupTime = start + currToPickupDur
											+ restHrs111 + restHrs211;

								if ((estPickupTime + loadingBuffer) >= getTime(
										pickupTime, pickupLocPeakStart1)
										&& estPickupTime <= getTime(pickupTime,
												pickupLocPeakEnd1)) {
									logger.info("pickupTime lies between peak1");
									long latePickupBuffer = getTime(pickupTime,
											pickupLocPeakEnd1) - estPickupTime;

									start = start + latePickupBuffer;
								}

								if ((estPickupTime + loadingBuffer) >= getTime(
										pickupTime, pickupLocPeakStart2)
										&& estPickupTime <= getTime(pickupTime,
												pickupLocPeakEnd2)) {
									logger.info("pickupTime lies between peak2");
									long latePickupBuffer = getTime(pickupTime,
											pickupLocPeakEnd2) - estPickupTime;

									start = start + latePickupBuffer;
								}

								if ((estPickupTime + loadingBuffer) >= getTime(
										pickupTime, pickupLocPeakStart3)
										&& estPickupTime <= getTime(pickupTime,
												pickupLocPeakEnd3)) {
									logger.info("pickupTime lies between peak3");
									long latePickupBuffer = getTime(pickupTime,
											pickupLocPeakEnd3) - estPickupTime;

									start = start + latePickupBuffer;
								}
								if ((estPickupTime + loadingBuffer) >= getTime(
										pickupTime, pickupLocPeakStart4)
										&& estPickupTime <= getTime(pickupTime,
												pickupLocPeakEnd4)) {

									logger.info("pickupTime lies between peak4");
									long latePickupBuffer = getTime(pickupTime,
											pickupLocPeakEnd1) - estPickupTime;

									start = start + latePickupBuffer;
								}

								end = start + currToPickupDur + tripDuration
										+ restHrs111 + restHrs211
										+ loadingBuffer + unloadingBuffer;

								long estimatedDelivery = end - unloadingBuffer;
								logger.info("estimatedDelivery->"
										+ estimatedDelivery);

								if ((estimatedDelivery + unloadingBuffer) >= getTime(
										deliveryTime, deliveryLocPeakStart1)
										&& (end - unloadingBuffer) <= getTime(
												deliveryTime,
												deliveryLocPeakEnd1)) {
									logger.info("deliveryTime lies between peak1");
									long lateDeliveryBuffer = getTime(
											deliveryTime, deliveryLocPeakEnd1)
											- estimatedDelivery;

									start = start + lateDeliveryBuffer;
									end = end + lateDeliveryBuffer;
								}

								if ((estimatedDelivery + unloadingBuffer) >= getTime(
										deliveryTime, deliveryLocPeakStart2)
										&& (end - unloadingBuffer) <= getTime(
												deliveryTime,
												deliveryLocPeakEnd2)) {
									logger.info("deliveryTime lies between peak2");
									long lateDeliveryBuffer = getTime(
											deliveryTime, deliveryLocPeakEnd2)
											- estimatedDelivery;

									start = start + lateDeliveryBuffer;
									end = end + lateDeliveryBuffer;
								}

								if ((estimatedDelivery + unloadingBuffer) >= getTime(
										deliveryTime, deliveryLocPeakStart3)
										&& (end - unloadingBuffer) <= getTime(
												deliveryTime,
												deliveryLocPeakEnd3)) {
									logger.info("deliveryTime lies between peak3");
									long lateDeliveryBuffer = getTime(
											deliveryTime, deliveryLocPeakEnd3)
											- estimatedDelivery;

									start = start + lateDeliveryBuffer;
									end = end + lateDeliveryBuffer;
								}

								if ((estimatedDelivery + unloadingBuffer) >= getTime(
										deliveryTime, deliveryLocPeakStart4)
										&& (end - unloadingBuffer) <= getTime(
												deliveryTime,
												deliveryLocPeakEnd4)) {
									logger.info("deliveryTime lies between peak4");
									long lateDeliveryBuffer = getTime(
											deliveryTime, deliveryLocPeakEnd4)
											- estimatedDelivery;

									start = start + lateDeliveryBuffer;
									end = end + lateDeliveryBuffer;
								}

								logger.info("currentTime: "
										+ new Date(currentTime) + " START: "
										+ new Date(start));
								logger.info("END:" + new Date(end));

								List<Activity> previousActs = resource
										.getActivitiesInInterval(
												startTimeoftheDay, start);
								long prevDur = 0L;
								for (Activity acts : previousActs) {
									long prevDuration = acts.getDuration();
									prevDur = prevDuration;
								}

								FloatState capacityState = (FloatState) resource
										.getStateVariableBy(CAPACITYSTATE_KEY);
								double startCapacity = ((Double) capacityState
										.getValueAt(start).getValue())
										.doubleValue();
								double endCapacity = ((Double) capacityState
										.getValueAt(end).getValue())
										.doubleValue();
								double actualCapacity = ((Double) capacityState
										.getActualState().getValue())
										.doubleValue();

								Objectives we = task.getObjectives();
								FloatParameter fp = (FloatParameter) we
										.getParameterBy("quantity");
								double taskQty = fp.getValue().doubleValue();

								if (!resourceType
										.equalsIgnoreCase(orderedTruckType)
										&& resourceType
												.equalsIgnoreCase("articulated")
										&& pickup_AT == 0.0) {

									logger.info("For the given customer "
											+ customerID
											+ " and pickup location "
											+ pickupLocation
											+ " the truck type permitted are not available for allocation");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"For the given customer "
													+ customerID
													+ " and pickup location "
													+ pickupLocation
													+ " the truck type permitted are not available for allocation");

								} else if (!resourceType
										.equalsIgnoreCase(orderedTruckType)
										&& resourceType
												.equalsIgnoreCase("articulated")
										&& delivery_AT == 0.0) {

									logger.info("For the given customer "
											+ customerID
											+ " and delivery location "
											+ deliveryLocation
											+ " the truck type permitted are not available for allocation");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"For the given customer "
													+ customerID
													+ " and delivery location "
													+ deliveryLocation
													+ " the truck type permitted are not available for allocation");

								} else if (!resourceType
										.equalsIgnoreCase(orderedTruckType)
										&& resourceType
												.equalsIgnoreCase("rigid")
										&& pickup_RT == 0.0) {

									logger.info("For the given customer "
											+ customerID
											+ " and pickup location "
											+ pickupLocation
											+ " the truck type permitted are not available for allocation");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"For the given customer "
													+ customerID
													+ " and pickup location "
													+ pickupLocation
													+ " the truck type permitted are not available for allocation");

								} else if (!resourceType
										.equalsIgnoreCase(orderedTruckType)
										&& resourceType
												.equalsIgnoreCase("rigid")
										&& delivery_RT == 0.0) {

									logger.info("For the given customer "
											+ customerID
											+ " and delivery location "
											+ deliveryLocation
											+ " the truck type permitted are not available for allocation");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"For the given customer "
													+ customerID
													+ " and delivery location "
													+ deliveryLocation
													+ " the truck type permitted are not available for allocation");

								}

								else if (!resourceType
										.equalsIgnoreCase(orderedTruckType)
										&& resourceType
												.equalsIgnoreCase("single")
										&& pickup_ST == 0.0) {

									logger.info("For the given customer "
											+ customerID
											+ " and pickup location "
											+ pickupLocation
											+ " the truck type permitted are not available for allocation");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"For the given customer "
													+ customerID
													+ " and pickup location "
													+ pickupLocation
													+ " the truck type permitted are not available for allocation");

								} else if (!resourceType
										.equalsIgnoreCase(orderedTruckType)
										&& resourceType
												.equalsIgnoreCase("single")
										&& delivery_ST == 0.0) {

									logger.info("For the given customer "
											+ customerID
											+ " and delivery location "
											+ deliveryLocation
											+ " the truck type permitted are not available for allocation");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"For the given customer "
													+ customerID
													+ " and delivery location "
													+ deliveryLocation
													+ " the truck type permitted are not available for allocation");

								} else if ((startCapacity + taskQty < this.capacityLowerBound
										.doubleValue())
										|| (startCapacity + taskQty > this.capacityUpperBound
												.doubleValue())) {
									logger.info("All feasible trucks for this order are occupied between "
											+ new Date(start)
											+ " and "
											+ new Date(end));
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"All feasible trucks for this order are occupied between "
													+ new Date(start) + " and "
													+ new Date(end));
									// throw
									// RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new
									// Date(start)+" and " +new Date(end));
								}

								else if ((startCapacity > this.capacityLowerBound
										.doubleValue())
										|| (endCapacity > this.capacityLowerBound
												.doubleValue())) {
									logger.info("All feasible trucks for this order are occupied between "
											+ new Date(start)
											+ " and "
											+ new Date(end));
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"All feasible trucks for this order are occupied between "
													+ new Date(start) + " and "
													+ new Date(end));
									// throw
									// RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new
									// Date(start)+" and " +new Date(end));
								}

								else if ((actualCapacity < 0.0D)
										|| (actualCapacity > 1.0D)) {
									logger.info("All feasible trucks for this order are occupied between "
											+ new Date(start)
											+ " and "
											+ new Date(end));
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"All feasible trucks for this order are occupied between "
													+ new Date(start) + " and "
													+ new Date(end));
									// throw
									// RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new
									// Date(start)+" and " +new Date(end));
								} else if (resource.getActivitiesInInterval(
										start, end).size() > 0) {
									logger.info("All feasible trucks for this order are occupied between "
											+ new Date(start)
											+ " and "
											+ new Date(end));
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"All feasible trucks for this order are occupied between "
													+ new Date(start) + " and "
													+ new Date(end));
								} else if (prevDur + (end - start) > maxWorkHrs) {
									logger.info("Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
									// throw
									// RSPMessages.newRspMsgEx("Standard working hours for the evaluated driver is exceeding the limits if this order is allocated.");
								} else if (start < System.currentTimeMillis()) {
									logger.info("Start time to fulfill this order is less than the current time");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"The Expected pickup time can not be fulfilled since the current time crossed over.");
									// throw
									// RSPMessages.newRspMsgEx("Start time to fulfill this order is less than the current time");
								}

								else if (estPickupTime > latestPickupTimeOfTheDay) {
									logger.info("The Expected pickup time can not be fulfilled since the latest pickup time will cross over.");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"The Expected pickup time can not be fulfilled since the latest pickup time will cross over.");

									// throw
									// RSPMessages.newRspMsgEx("All feasible trucks for this order are occupied between "+new
									// Date(start)+" and " +new Date(end));
								} else if (end > latestDeliveryTimeOfTheDay) {
									logger.info("Derived End time is greater than the required delivery time");
									placementResult = new DefaultPlacementResult(
											PlacementResult.FAILURE,
											"The feasible trucks to fulfill this order cannot complete the trip within End time is greater than the required delivery time.");
									// throw
									// RSPMessages.newRspMsgEx("Derived End time is greater than the required delivery time");
								}

								else {

									logger.info("START: " + new Date(start));
									logger.info("END:" + new Date(end));

									placementResult = new DefaultPlacementResult(
											PlacementResult.SUCCESS);
									placementResult.setStart(start);
									placementResult.setEnd(end);
									placementResult.setQuantity(this.quantity);
									placementResult.setResource(resource);

									remarks.setValue("");

									logger.info("PlacementResult: "
											+ placementResult);
									return placementResult;
								}
							}
						}
					}
				}
			} catch (ParseException e1) {
				e1.printStackTrace();
			}
			logger.info(placementResult);
			if (placementResult == null
					|| placementResult.getStatus() == PlacementResult.FAILURE) {
			//	String driverIdSelected =  resource.getParameterBy("driverId").getValueAsString();
				for (Resource r : TruckSelectionFormula.truckWithoutDrivers) {
					if (r.getId().equalsIgnoreCase(resource.getId())) {
						StringParameter driverIdParam = (StringParameter) resource.getParameterBy("driverId");
						driverIdParam.setValue("TBA");
					}
				}
				String statusMsg = placementResult.getMessage();
				remarks.setValue(statusMsg);
			}
		}
		return placementResult;
	}
	  
	@Override
	public void updateStateVariables(Task task, Resource resource, PlacementResult placementResult) {
		this.Capacity = getFloatState(resource, CAPACITYSTATE_KEY);
		this.Location = getStringState(resource, LOCATION_KEY);
		ParameterizedElement tmp = task;
		String orderNetId = null;
		while (tmp != null) {
			orderNetId = tmp.getId();
			tmp = tmp.getParent();
		}

		Order order = (Order) RSPContainerHelper.getOrderMap(true).getEntryBy(
				orderNetId);

		logger.info("ordupdateStateVar: " + orderNetId);
		double quantity = task.getQuantity();

		long start = placementResult.getStart();
		logger.info("start:" + new Date(start));

		long end = placementResult.getEnd();
		logger.info("end:" + new Date(end));

		logger.info("Start: " + new Date(start) + " End: " + new Date(end));

		Resource giga_parameters = RSPContainerHelper.getResourceMap(true)
				.getEntryBy("giga_parameters");

		double loadingBufferValue = (Double) giga_parameters.getParameterBy(
				"loadingBuffer").getValue();
		long loadingBuffer = (long) (loadingBufferValue * 60000L);

		double unloadingBufferValue = (Double) giga_parameters.getParameterBy(
				"unloadingBuffer").getValue();
		long unloadingBuffer = (long) (unloadingBufferValue * 60000L);

		double restHrs4value = (Double) giga_parameters.getParameterBy(
				"restHrs4").getValue();
		long restHrs4 = (long) (restHrs4value * 60000L);

		double restHrs8value = (Double) giga_parameters.getParameterBy(
				"restHrs8").getValue();
		long restHrs8 = (long) (restHrs8value * 60000L);

		double restHrs111value = (Double) giga_parameters.getParameterBy(
				"restHrs111").getValue();
		long restHrs111 = (long) (restHrs111value * 60000L);

		double restHrs211value = (Double) giga_parameters.getParameterBy(
				"restHrs211").getValue();
		long restHrs211 = (long) (restHrs211value * 60000L);

		String script = order.getParameterBy("script").getValueAsString();
		if ((order.getType().equalsIgnoreCase("Maintenance"))
				|| (order.getType().equalsIgnoreCase("PlannedLeave"))) {
			start = task.getStart();
			end = start + this.duration;

			createAndAddFloatChange(task, start, this.Capacity, quantity);
			createAndAddFloatChange(task, end, this.Capacity, -quantity);
		} else {
			String customerType = order.getParameterBy("orderType")
					.getValueAsString();
			double noOfDrops = 1.0;
			if (order.hasParameter("noOfDrops"))
				noOfDrops = (Double) order.getParameterBy("noOfDrops").getValue();

			String pickupLocation = order.getParameterBy("pickupLocation")
					.getValueAsString();
			String deliveryLocation = order.getParameterBy("deliveryLocation")
					.getValueAsString();

			String BaseLocation = resource.getParameterBy("location")
					.getValueAsString();
			double deliveryToBasekm = getDistance(deliveryLocation,
					BaseLocation);

			long tripDuration = getDuration(pickupLocation, deliveryLocation);

			long restHr = 0L;

			long pickupDate = ((DateParameter) task.getParameterBy("start"))
					.getValue().longValue();
			long deliveryDate = ((DateParameter) task.getParameterBy("end"))
					.getValue().longValue();

			logger.info(resource);
			String CurrLoc = null;
			List<Activity> prevTasks = resource.getActivitiesInInterval(
					Long.MIN_VALUE, Long.MAX_VALUE);

			if (prevTasks.size() < 1) {
				StateValue<?> currentLocValue = resource.getStateVariableBy(
						"Location").getValueAt(end);

				CurrLoc = currentLocValue.getValueAsString();
				logger.info("currentLocValue " + CurrLoc);
			} else {
				for (Activity act : prevTasks) {
					String orderId = act.getOrderNetId();
					Order prevOrder = RSPContainerHelper.getOrderMap(true)
							.getEntryBy(orderId);
					String OrderType = prevOrder.getType();
					logger.info("prevOrder->" + orderId);

					if (OrderType.equalsIgnoreCase("Maintenance")) {
						logger.info("It is a maintenance order");
						continue;
					}
					logger.info("prevOrder->" + orderId);
					CurrLoc = prevOrder.getParameterBy("deliveryLocation")
							.getValueAsString();
					
					if (prevOrder.getParameterBy("orderType").getValueAsString()
							.contains("Outstation")) {

						long estimatedAvailableTime = act.getStart() - 2 * 60000L;
						CurrLoc = resource.getStateVariableBy("Location")
								.getValueAt(estimatedAvailableTime)
								.getValueAsString();
						break;
					}

				}
				logger.info("currentLocValue " + CurrLoc);
			}
			// String CurrLoc =
			// resource.getStateVariableBy("Location").getValueAt(start).getValueAsString();
			logger.info("currentLoc: " + CurrLoc);

			Resource currLocRes = RSPContainerHelper.getResourceMap(true)
					.getEntryBy(CurrLoc);
			FloatListParameter pickupVal = (FloatListParameter) currLocRes
					.getParameterBy(pickupLocation);
			/*
			 * if(pickupVal == null){ remarks.new
			 * DefaultPlacementResult(PlacementResult
			 * .FAILURE,"Parameter with Id "+ pickupLocation+
			 * " is not available in Current Location "+CurrLoc); }
			 */
			double currToPickupDist = getDistance(CurrLoc, pickupLocation);

			StringParameter selectedTruck = (StringParameter) order.getParameterBy("truckId");
			selectedTruck.setValue(resource.getId());
			long currToPickupDur = getDuration(CurrLoc, pickupLocation);
			String driverId = resource.getParameterBy("driverId").getValueAsString();
			Resource DriverRes = RSPContainerHelper.getResourceMap(true).getEntryBy(driverId);
			/*StateValue<Double> driverCapacity = null;
			driverCapacity = (StateValue<Double>) DriverRes.getStateVariableBy("Capacity").getValueAt(pickupDate);
			driverCapacity.setValue(1.0);

	logger.info("OrderiD choosen :" + orderNetId + "  truckid chosen " + selectedTruck + " driverid"  + driverId + "  /capcaity  : "  + driverCapacity);*/


			if (script.equalsIgnoreCase("ReassignManually")) {

				long estimatedPickupTime = start + currToPickupDur;

				createAndAddFloatChange(task, start, this.Capacity, quantity);
				createAndAddFloatChange(task, end, this.Capacity, -quantity);

				createAndAddStringChange(task, start, this.Location, CurrLoc);
				createAndAddStringChange(task, estimatedPickupTime,
						this.Location, pickupLocation);
				createAndAddStringChange(task, estimatedPickupTime
						+ loadingBuffer, this.Location, pickupLocation);
				createAndAddStringChange(task, end - unloadingBuffer,
						this.Location, deliveryLocation);
				createAndAddStringChange(task, end, this.Location,
						deliveryLocation);

				/*StringParameter selectedTruck = (StringParameter) order
						.getParameterBy("truckId");
				selectedTruck.setValue(resource.getId());

				String driverId = resource.getParameterBy("driverId")
						.getValueAsString();
				Resource DriverRes = RSPContainerHelper.getResourceMap(true)
						.getEntryBy(driverId);
				StateValue<Double> driverCapacity = null;
				driverCapacity = (StateValue<Double>) DriverRes
						.getStateVariableBy("Capacity").getValueAt(pickupDate);
				driverCapacity.setValue(1.0);

				logger.info("OrderiD choosen :" + orderNetId + "  truckid chosen " + selectedTruck + " driverid"  + driverId + "  /capcaity  : "  + driverCapacity);
*/				
				StringParameter preferDriver = (StringParameter) order
						.getParameterBy("driverId");
				preferDriver.setValue(driverId);

				logger.info("driver " + preferDriver.getValueAsString());

				Date pickdate = new Date(estimatedPickupTime);
				SimpleDateFormat df2 = new SimpleDateFormat(
						"MM/dd/yyyy HH:mm:ss");
				String pickupDateText = df2.format(pickdate);

				StringParameter estPickupTime = (StringParameter) order
						.getParameterBy("estPickupTime");
				estPickupTime.setValue(pickupDateText);
				logger.info("estPickupTime :" + pickupDateText);

				Date Enddate = new Date(end);
				String endtdateText = df2.format(Enddate);

				StringParameter estDeliveryTime = (StringParameter) order
						.getParameterBy("estDeliveryTime");
				estDeliveryTime.setValue(endtdateText);
				logger.info("estDeliveryTime :" + endtdateText);

				long travelTime = end - start;

				Integer travelTimeT = (int) (travelTime / 60000);
				String travelTimeText = travelTimeT.toString(travelTimeT);

				logger.info("TravelTime: " + travelTimeT);

				StringParameter estTravelTime = (StringParameter) order
						.getParameterBy("estTravelTime");
				estTravelTime.setValue(travelTimeText);

				double precedingDm = currToPickupDist;
				StringParameter preceding_DM = (StringParameter) order
						.getParameterBy("preceding_DM");
				preceding_DM.setValue(Double.toString(precedingDm));

				StringParameter succeeding_DM = (StringParameter) order
						.getParameterBy("succeeding_DM");
				succeeding_DM.setValue(Double.toString(deliveryToBasekm));

				Integer travelDur = (int) (tripDuration / 60000);
				StringParameter travel_duration = (StringParameter) order
						.getParameterBy("travel_Duration");
				travel_duration.setValue(Integer.toString(travelDur));

				StringParameter loadBuffer = (StringParameter) order
						.getParameterBy("loading_unloading_timeBuffer");
				loadBuffer.setValue(Double.toString(loadingBuffer
						+ unloadingBuffer));

				Integer restDur = (int) (restHr / 60000);
				StringParameter restWaitBuffer = (StringParameter) order
						.getParameterBy("rest_Waiting_timeBuffer");
				restWaitBuffer.setValue(Integer.toString(restDur));

				if (CurrLoc.equalsIgnoreCase(BaseLocation)) {
					String baseStartDateText = df2.format(new Date(start));
					StringParameter baseLocStartTime = (StringParameter) order
							.getParameterBy("base_location_StartTime");
					baseLocStartTime.setValue(baseStartDateText);
				}

			}

			else {

				if ((customerType.contains("Outstation"))) {
					String IntermediateBase;
					long pickupToBase;
					long BaseToDelivery;

					/*String base1 = ((Resource) RSPContainerHelper
							.getResourceMap(true).getEntryBy("base_west"))
							.getParameterBy("base1").getValueAsString();
					long pickupToBase1Dur = getDuration(pickupLocation, base1);
*/
					//long base1ToDelDur = getDuration(base1, deliveryLocation);

					//long intermediate1 = pickupToBase1Dur + base1ToDelDur;

				/*	String base2 = ((Resource) RSPContainerHelper
							.getResourceMap(true).getEntryBy("base_west"))
							.getParameterBy("base2").getValueAsString();
					long pickupToBase2Dur = getDuration(pickupLocation, base2);

					long base2ToDelDur = getDuration(base2, deliveryLocation);

					long intermediate2 = pickupToBase2Dur + base2ToDelDur;

					long dif = Math.min(base1ToDelDur, base2ToDelDur);

					if (dif == base1ToDelDur) {
						IntermediateBase = base1;
						pickupToBase = pickupToBase1Dur;
						BaseToDelivery = base1ToDelDur;
					} else {
						IntermediateBase = base2;
						pickupToBase = pickupToBase2Dur;
						BaseToDelivery = base2ToDelDur;
					}*/

					Date Startdate = new Date(start);
					logger.info("startTime:" + Startdate);

					long estimatedPickupTime = start + currToPickupDur;
					long estimatedDeliveryTime = end;

					/*
					 * if(noOfDrops > 1.0){ estimatedDeliveryTime = (long) (end
					 * - (noOfDrops-1)*(9e5 + unloadingBuffer)); }
					 */

					createAndAddFloatChange(task, start, this.Capacity,
							quantity);
					createAndAddFloatChange(task, end, this.Capacity, -quantity);

					logger.info("CurrLoc:" + CurrLoc);
					createAndAddStringChange(task, start, this.Location,
							CurrLoc);
					createAndAddStringChange(task, estimatedPickupTime,
							this.Location, pickupLocation);
					createAndAddStringChange(task, estimatedPickupTime
							+ loadingBuffer, this.Location, pickupLocation);
					/*createAndAddStringChange(task, estimatedPickupTime
							+ loadingBuffer + pickupToBase, this.Location,
							IntermediateBase);*/
				/*	createAndAddStringChange(task, estimatedDeliveryTime
							- unloadingBuffer - BaseToDelivery, this.Location,
							IntermediateBase);*/
					createAndAddStringChange(task, estimatedDeliveryTime
							- unloadingBuffer, this.Location, deliveryLocation);
					createAndAddStringChange(task, estimatedDeliveryTime,
							this.Location, deliveryLocation);
					createAndAddStringChange(task, end, this.Location,
							deliveryLocation);

					restHr = (end - unloadingBuffer )
							- (estimatedPickupTime + loadingBuffer );

					StringParameter driver = (StringParameter) resource
							.getParameterBy("driverId");
					String driverRes = driver.getValueAsString();
					logger.info("truckDriver: " + driverRes);

					/*StringParameter selectedTruck = (StringParameter) order
							.getParameterBy("truckId");
					selectedTruck.setValue(resource.getId());

					Resource DriverRes = RSPContainerHelper
							.getResourceMap(true).getEntryBy(driverRes);
					StateValue<Double> driverCapacity = null;
					driverCapacity = (StateValue<Double>) DriverRes
							.getStateVariableBy("Capacity").getValueAt(
									pickupDate);
					driverCapacity.setValue(1.0);
  
					logger.info("OrderiD choosen :" + orderNetId + "  truckid chosen " + selectedTruck + " driverid"  + driverRes + "  /capcaity  : "  + driverCapacity);*/
					StringParameter preferDriver = (StringParameter) order
							.getParameterBy("driverId");
					preferDriver.setValue(driverRes);
					logger.info("orderDriver :"
							+ preferDriver.getValueAsString());

					Date pickdate = new Date(estimatedPickupTime);
					SimpleDateFormat df2 = new SimpleDateFormat(
							"MM/dd/yyyy HH:mm:ss");
					String pickupDateText = df2.format(pickdate);

					StringParameter estPickupTime = (StringParameter) order
							.getParameterBy("estPickupTime");
					estPickupTime.setValue(pickupDateText);
					logger.info("estPickupTime :"
							+ estPickupTime.getValueAsString());

					Date Enddate = new Date(end);
					String endtdateText = df2.format(Enddate);

					StringParameter estDeliveryTime = (StringParameter) order
							.getParameterBy("estDeliveryTime");
					estDeliveryTime.setValue(endtdateText);
					logger.info("estDeliveryTime :"
							+ estDeliveryTime.getValueAsString());

					long travelTime = end - start;
					Integer travelTimeT = (int) (travelTime / 60000);
					String travelTimeText = Integer.toString(travelTimeT);

					StringParameter estTravelTime = (StringParameter) order
							.getParameterBy("estTravelTime");
					estTravelTime.setValue(travelTimeText);

					double precedingDm = currToPickupDist;
					StringParameter preceding_DM = (StringParameter) order
							.getParameterBy("preceding_DM");
					preceding_DM.setValue(Double.toString(precedingDm));

					StringParameter succeeding_DM = (StringParameter) order
							.getParameterBy("succeeding_DM");
					succeeding_DM.setValue(Integer.toString(0));

					Integer travelDur = (int) (tripDuration / 60000);
					StringParameter travel_duration = (StringParameter) order
							.getParameterBy("travel_Duration");
					travel_duration.setValue(Integer.toString(travelDur));

					StringParameter loadBuffer = (StringParameter) order
							.getParameterBy("loading_unloading_timeBuffer");
					loadBuffer.setValue(Double.toString(90 + (noOfDrops - 1)
							* (loadingBuffer / 60000L)));

					Integer restDur = (int) (restHr / 60000);
					StringParameter restWaitBuffer = (StringParameter) order
							.getParameterBy("rest_Waiting_timeBuffer");
					restWaitBuffer.setValue(Integer.toString(restDur));

					String baseStartDateText = df2.format(new Date(end
							- unloadingBuffer ));
					StringParameter baseLocStartTime = (StringParameter) order
							.getParameterBy("base_location_StartTime");
					baseLocStartTime.setValue(baseStartDateText);

				} else {
					long estimatedPickupTime = 0L;

					String startTaskLoc = task.getParameterBy("from")
							.getValueAsString();
					String endTaskLoc = task.getParameterBy("to")
							.getValueAsString();

					long buffer1 = loadingBuffer;
					long buffer2 = unloadingBuffer;
					long totalTripDur = end - start;
					long twoHrs = 7200000L;
					long fourHrs = 14400000L;
					long eightHrs = 28800000L;
					long elevenHrs = 39600000L;

					if (totalTripDur <= twoHrs + buffer1 + buffer2) {
						estimatedPickupTime = start + currToPickupDur;
						restHr = 0L;

						createAndAddFloatChange(task, start, this.Capacity,
								quantity);
						createAndAddFloatChange(task, end, this.Capacity,
								-quantity);

						createAndAddStringChange(task, start, this.Location,
								CurrLoc);
						createAndAddStringChange(task, estimatedPickupTime,
								this.Location, startTaskLoc);
						createAndAddStringChange(task, estimatedPickupTime
								+ buffer1, this.Location, startTaskLoc);
						createAndAddStringChange(task, end - buffer2,
								this.Location, endTaskLoc);
						createAndAddStringChange(task, end, this.Location,
								endTaskLoc);

						// if activities between end and 23:00 is null,then
						// update truck location to base location
						// if(resource.getActivitiesInInterval(end, arg1))
					}

					if ((totalTripDur > twoHrs + buffer1 + buffer2)
							&& (totalTripDur <= fourHrs + buffer1 + buffer2
									+ restHrs4)) {
						restHr = restHrs4;
						if (start + currToPickupDur < twoHrs) {
							estimatedPickupTime = start + currToPickupDur;
							long restLoc = estimatedPickupTime + buffer1
									+ (twoHrs - currToPickupDur);

							createAndAddFloatChange(task, start, this.Capacity,
									quantity);
							createAndAddFloatChange(task, end, this.Capacity,
									-quantity);

							createAndAddStringChange(task, start,
									this.Location, CurrLoc);
							createAndAddStringChange(task, estimatedPickupTime,
									this.Location, startTaskLoc);
							createAndAddStringChange(task, estimatedPickupTime
									+ buffer1, this.Location, startTaskLoc);
							// createAndAddStringChange(task, restLoc,
							// this.Location, "RestLocation");
							// createAndAddStringChange(task, restLoc +
							// restHrs4, this.Location, "RestLocation");
							createAndAddStringChange(task, end - buffer2,
									this.Location, endTaskLoc);
							createAndAddStringChange(task, end, this.Location,
									endTaskLoc);
						} else if (start + currToPickupDur > twoHrs) {
							long restLoc = start + twoHrs;
							estimatedPickupTime = start + currToPickupDur
									+ restHrs4;

							createAndAddFloatChange(task, start, this.Capacity,
									quantity);
							createAndAddFloatChange(task, end, this.Capacity,
									-quantity);

							createAndAddStringChange(task, start,
									this.Location, CurrLoc);
							// createAndAddStringChange(task, restLoc,
							// this.Location, "RestLocation");
							// createAndAddStringChange(task, restLoc +
							// restHrs4, this.Location, "RestLocation");
							createAndAddStringChange(task, estimatedPickupTime,
									this.Location, startTaskLoc);
							createAndAddStringChange(task, estimatedPickupTime
									+ buffer1, this.Location, startTaskLoc);
							createAndAddStringChange(task, end - buffer2,
									this.Location, endTaskLoc);
							createAndAddStringChange(task, end, this.Location,
									endTaskLoc);
						}
					}

					if ((totalTripDur > fourHrs + buffer1 + buffer2 + restHrs4)
							&& (totalTripDur <= eightHrs + buffer1 + buffer2
									+ restHrs8)) {
						restHr = restHrs8;

						if (start + currToPickupDur < fourHrs) {
							estimatedPickupTime = start + currToPickupDur;
							long restLoc = estimatedPickupTime + buffer1
									+ (fourHrs - currToPickupDur);

							createAndAddFloatChange(task, start, this.Capacity,
									quantity);
							createAndAddFloatChange(task, end, this.Capacity,
									-quantity);

							createAndAddStringChange(task, start,
									this.Location, CurrLoc);
							createAndAddStringChange(task, estimatedPickupTime,
									this.Location, startTaskLoc);
							createAndAddStringChange(task, estimatedPickupTime
									+ buffer1, this.Location, startTaskLoc);
							// createAndAddStringChange(task, restLoc,
							// this.Location, "RestLocation");
							// createAndAddStringChange(task, restLoc +
							// restHrs8, this.Location, "RestLocation");
							createAndAddStringChange(task, end - buffer2,
									this.Location, endTaskLoc);
							createAndAddStringChange(task, end, this.Location,
									endTaskLoc);
						} else if (start + currToPickupDur > fourHrs) {
							long restLoc = start + fourHrs;
							estimatedPickupTime = start + currToPickupDur
									+ restHrs8;

							createAndAddFloatChange(task, start, this.Capacity,
									quantity);
							createAndAddFloatChange(task, end, this.Capacity,
									-quantity);

							createAndAddStringChange(task, start,
									this.Location, CurrLoc);
							// createAndAddStringChange(task, restLoc,
							// this.Location, "RestLocation");
							// createAndAddStringChange(task, restLoc +
							// restHrs8, this.Location, "RestLocation");
							createAndAddStringChange(task, estimatedPickupTime,
									this.Location, startTaskLoc);
							createAndAddStringChange(task, estimatedPickupTime
									+ buffer1, this.Location, startTaskLoc);
							createAndAddStringChange(task, end - buffer2,
									this.Location, endTaskLoc);
							createAndAddStringChange(task, end, this.Location,
									endTaskLoc);
						}
					}

					if ((totalTripDur > eightHrs + buffer1 + buffer2 + restHrs8)
							&& (totalTripDur <= elevenHrs + buffer1 + buffer2
									+ restHrs111 + restHrs211)) {
						restHr = restHrs111 + restHrs211;
						if (start + currToPickupDur < fourHrs) {
							estimatedPickupTime = start + currToPickupDur;
							long restLoc1 = estimatedPickupTime + buffer1
									+ (fourHrs - currToPickupDur);
							long restLoc2 = restLoc1 + restHrs111 + fourHrs;

							createAndAddFloatChange(task, start, this.Capacity,
									quantity);
							createAndAddFloatChange(task, end, this.Capacity,
									-quantity);

							createAndAddStringChange(task, start,
									this.Location, CurrLoc);
							createAndAddStringChange(task, estimatedPickupTime,
									this.Location, startTaskLoc);
							createAndAddStringChange(task, estimatedPickupTime
									+ buffer1, this.Location, startTaskLoc);
							// createAndAddStringChange(task, restLoc1,
							// this.Location, "RestLocation_1");
							// createAndAddStringChange(task, restLoc1 +
							// restHrs111, this.Location, "RestLocation_1");
							// createAndAddStringChange(task, restLoc2,
							// this.Location, "RestLocation_2");
							// createAndAddStringChange(task, restLoc2 +
							// restHrs211, this.Location, "RestLocation_2");
							createAndAddStringChange(task, end - buffer2,
									this.Location, endTaskLoc);
							createAndAddStringChange(task, end, this.Location,
									endTaskLoc);
						} else if (start + currToPickupDur > fourHrs) {
							long restLoc1 = start + fourHrs;
							estimatedPickupTime = start + currToPickupDur
									+ restHrs111;
							long restLoc2 = start + restHrs111 + buffer1
									+ eightHrs;

							createAndAddFloatChange(task, start, this.Capacity,
									quantity);
							createAndAddFloatChange(task, end, this.Capacity,
									-quantity);

							createAndAddStringChange(task, start,
									this.Location, CurrLoc);
							// createAndAddStringChange(task, restLoc1,
							// this.Location, "RestLocation_1");
							// createAndAddStringChange(task, restLoc1 +
							// restHrs111, this.Location, "RestLocation_1");
							createAndAddStringChange(task, estimatedPickupTime,
									this.Location, startTaskLoc);
							createAndAddStringChange(task, estimatedPickupTime
									+ buffer1, this.Location, startTaskLoc);
							// createAndAddStringChange(task, restLoc2,
							// this.Location, "RestLocation_2");
							// createAndAddStringChange(task, restLoc2 +
							// restHrs211, this.Location, "RestLocation_2");
							createAndAddStringChange(task, end - buffer2,
									this.Location, endTaskLoc);
							createAndAddStringChange(task, end, this.Location,
									endTaskLoc);
						} else if (start + currToPickupDur > eightHrs) {
							long restLoc1 = start + fourHrs;
							long restLoc2 = restLoc1 + restHrs111 + fourHrs;
							estimatedPickupTime = start + currToPickupDur
									+ restHrs111 + restHrs211;

							createAndAddFloatChange(task, start, this.Capacity,
									quantity);
							createAndAddFloatChange(task, end, this.Capacity,
									-quantity);

							createAndAddStringChange(task, start,
									this.Location, CurrLoc);
							// createAndAddStringChange(task, restLoc1,
							// this.Location, "RestLocation_1");
							// createAndAddStringChange(task, restLoc1 +
							// restHrs111, this.Location, "RestLocation_1");
							// createAndAddStringChange(task, restLoc2,
							// this.Location, "RestLocation_2");
							// createAndAddStringChange(task, restLoc2 +
							// restHrs211, this.Location, "RestLocation_2");
							createAndAddStringChange(task, estimatedPickupTime,
									this.Location, startTaskLoc);
							createAndAddStringChange(task, estimatedPickupTime
									+ buffer1, this.Location, startTaskLoc);
							createAndAddStringChange(task, end - buffer2,
									this.Location, endTaskLoc);
							createAndAddStringChange(task, end, this.Location,
									endTaskLoc);
						}

					}

					StringParameter driver = (StringParameter) resource
							.getParameterBy("driverId");
					String driverRes = driver.getValueAsString();
					logger.info("truckDriver: " + driverRes);

					/*StringParameter selectedTruck = (StringParameter) order
							.getParameterBy("truckId");
					selectedTruck.setValue(resource.getId());

					Resource DriverRes = RSPContainerHelper
							.getResourceMap(true).getEntryBy(driverRes);
					StateValue<Double> driverCapacity = null;
					driverCapacity = (StateValue<Double>) DriverRes
							.getStateVariableBy("Capacity").getValueAt(
									pickupDate);
					driverCapacity.setValue(1.0);
					
					logger.info("OrderiD choosen :" + orderNetId + "  truckid chosen " + selectedTruck + " driverid"  + driverRes + "  /capcaity  : "  + driverCapacity);*/

					StringParameter preferDriver = (StringParameter) order
							.getParameterBy("driverId");
					preferDriver.setValue(driverRes);
					logger.info("orderDriver :"
							+ preferDriver.getValueAsString());

					Date pickdate = new Date(estimatedPickupTime);
					SimpleDateFormat df2 = new SimpleDateFormat(
							"MM/dd/yyyy HH:mm:ss");
					String pickupDateText = df2.format(pickdate);

					StringParameter estPickupTime = (StringParameter) order
							.getParameterBy("estPickupTime");
					estPickupTime.setValue(pickupDateText);
					logger.info("estPickupTime :" + pickupDateText);

					Date Enddate = new Date(end);
					String endtdateText = df2.format(Enddate);

					StringParameter estDeliveryTime = (StringParameter) order
							.getParameterBy("estDeliveryTime");
					estDeliveryTime.setValue(endtdateText);
					logger.info("estDeliveryTime :" + endtdateText);

					long travelTime = end - start;

					Integer travelTimeT = (int) (travelTime / 60000);
					String travelTimeText = travelTimeT.toString(travelTimeT);

					logger.info("TravelTime: " + travelTimeT);

					StringParameter estTravelTime = (StringParameter) order
							.getParameterBy("estTravelTime");
					estTravelTime.setValue(travelTimeText);

					double precedingDm = currToPickupDist;
					StringParameter preceding_DM = (StringParameter) order
							.getParameterBy("preceding_DM");
					preceding_DM.setValue(Double.toString(precedingDm));

					StringParameter succeeding_DM = (StringParameter) order
							.getParameterBy("succeeding_DM");
					succeeding_DM.setValue(Double.toString(deliveryToBasekm));

					Integer travelDur = (int) (tripDuration / 60000);
					StringParameter travel_duration = (StringParameter) order
							.getParameterBy("travel_Duration");
					travel_duration.setValue(Integer.toString(travelDur));

					StringParameter loadBuffer = (StringParameter) order
							.getParameterBy("loading_unloading_timeBuffer");
					loadBuffer.setValue(Double.toString(90 + (noOfDrops - 1)
							* (loadingBuffer / 60000L)));

					Integer restDur = (int) (restHr / 60000);
					StringParameter restWaitBuffer = (StringParameter) order
							.getParameterBy("rest_Waiting_timeBuffer");
					restWaitBuffer.setValue(Integer.toString(restDur));

					if (CurrLoc.equalsIgnoreCase(BaseLocation)) {
						String baseStartDateText = df2.format(new Date(start));
						StringParameter baseLocStartTime = (StringParameter) order
								.getParameterBy("base_location_StartTime");
						baseLocStartTime.setValue(baseStartDateText);
					}
					/*
					 * StringParameter remarks =
					 * (StringParameter)order.getParameterBy("remarks");
					 * estTravelTime.setValue(travelTimeText);
					 */

				}
				logger.info("outsidetruckwithout driver entry" + resource);
				logger.info(TruckSelectionFormula.truckWithoutDrivers);
				
				String driverIdSelected =  resource
						.getParameterBy("driverId").getValueAsString();
		
				
				/*for (Resource r : testFormula.truckWithoutDrivers) {
					StringParameter driverIdParam = (StringParameter) resource
							.getParameterBy("driverId");
					driverIdParam.setValue("TBA");
				}
				
				((StringParameter)resource
				.getParameterBy("driverId")).setValue(driverIdSelected);
				
						
					 
                       Resource truckUnderMaintenance = getTrucksUnderMaintenance(resource);
						
						
						if(truckUnderMaintenance == null);
						else
						{
							StringParameter mainDriverUpdate = (StringParameter) truckUnderMaintenance.getParameterBy("driverId");
							mainDriverUpdate.setValue("TBA");
							
						}*/
				for (Resource r : TruckSelectionFormula.truckWithoutDrivers) {
			          logger.info("truckwithout driver entry" + resource);

			          StringParameter driverIdParam = (StringParameter)r
			            .getParameterBy("driverId");
			          if (!r.getId().equalsIgnoreCase(resource.getId())) {
			            logger.info("assigned driver :" + r.getParameterBy("driverId").getValueAsString() + "truck" + r.getId());
			            driverIdParam.setValue("TBA");
			          }
			          else
			          {
			            Resource truckUnderMaintenance = getTrucksUnderMaintenance(resource);

			            if (truckUnderMaintenance != null)
			            {
			              StringParameter mainDriverUpdate = (StringParameter)truckUnderMaintenance.getParameterBy("driverId");
			              mainDriverUpdate.setValue("TBA");
			            }
			          }
			
					}
				/*((StringParameter)resource.getParameterBy("driverId")).setValue(driverIdSelected);
						Resource driverRes = RSPContainerHelper.getResourceMap(true).getEntryBy(driverIdSelected);
				if (driverRes.hasParameter("isAlreadyAllocated")) {
					logger.info("The driver "+driverRes.getId()+" is getting duplicated");
				}
				else {
					logger.info("The driver "+driverRes.getId()+" isAlreadyAllocated added");
					BooleanParameter isAlreadyAllocatedP = new BooleanParameter("isAlreadyAllocated", "isAlreadyAllocated", true);
					driverRes.addParameter(isAlreadyAllocatedP);
					logger.info("The driver "+driverRes.getId()+" isAlreadyAllocated added"+ driverRes.getParameterBy("isAlreadyAllocated").getValueAsString());
				}*/
				}
		}
	}
	
	@Override
	public void validate(IPolicyContainer policyContainer) {

		super.validate(policyContainer);

		// check if the required parameter exist
		Resource re = (Resource) policyContainer;
		evaluateBounds(re);

	}
	
	public static long getTime(long date, String time) {

		long result = 0;
		if(time == null || time.equalsIgnoreCase("?"))
			time ="00:00:00";

		Date Date = new Date(date);
		SimpleDateFormat df = new SimpleDateFormat("MM/dd/yyyy");
		SimpleDateFormat df1 = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
		String DateText = df.format(Date);

		StringWriter s = new StringWriter();
		s.append(DateText);
		s.append(" " + time);

		try {

			Date dateTime = df1.parse(s.toString());

			result = dateTime.getTime();

		} catch (ParseException e) {
			e.printStackTrace();
		}
		return result;

	}

	public static long getDuration(String placeFrom, String placeTo) {
		logger.info("placeFrom::" + placeFrom + "::placeTo-->" + placeTo);
		long result = 0L;
		Resource resFrom = RSPContainerHelper.getResourceMap(true).getEntryBy(
				placeFrom);

		String[] MatrixValues = resFrom.getParameterBy(placeTo)
				.getValueAsString().split(",");
		logger.info("MatrixValues[1]-->" + MatrixValues[1] + "::placeFrom::"
				+ placeFrom + "::placeTo-->" + placeTo);
		double duration = Double.parseDouble(MatrixValues[1]);
		result = (long) duration * 1000L;
		logger.info("result: " + duration);
		return result;

	}

	public static double getDistance(String placeFrom, String placeTo) {

		logger.info("placeFrom::" + placeFrom + "::placeTo-->" + placeTo);
		double result = 0.0;
		Resource resFrom = RSPContainerHelper.getResourceMap(true).getEntryBy(
				placeFrom);
		String[] MatrixValues = resFrom.getParameterBy(placeTo)
				.getValueAsString().split(",");
		logger.info("MatrixValues[0]-->" + MatrixValues[0] + "::placeFrom::"
				+ placeFrom + "::placeTo-->" + placeTo);
		double distance = Double.parseDouble(MatrixValues[0]);
		result = distance;
		logger.info("result: " + distance);
		return result;
	}

	protected static String getCurrentLocation(Resource truckRes) {
		String currentLoc = null;
		List<Activity> prevTasks = truckRes.getActivitiesInInterval(
				Long.MIN_VALUE, Long.MAX_VALUE);
		if (prevTasks.size() < 1) {
			// if the truck is idle
			StateValue<?> currentLocValue = truckRes.getStateVariableBy(
					"Location").getValueAt(Long.MAX_VALUE);
			currentLoc = currentLocValue.getValueAsString();
		} else {
			// if truck is fulfilling another order.
			Activity lastAct = prevTasks.get(prevTasks.size() - 1);
			String orderId = lastAct.getOrderNetId();
			Order prevOrder = RSPContainerHelper.getOrderMap(true).getEntryBy(
					orderId);
			String OrderType = prevOrder.getType();

			if (OrderType.equalsIgnoreCase("Maintenance")) {
				// if truck is under maintenance.
				currentLoc = truckRes.getStateVariableBy("Location")
						.getValueAt(lastAct.getEnd()).getValueAsString();
			} else {
				// if truck is not under maintenance and fulfilling another
				// order.
				currentLoc = prevOrder.getParameterBy("deliveryLocation")
						.getValueAsString();

				if (prevOrder.getParameterBy("jobType").getValueAsString()
						.equalsIgnoreCase("Outstation")) {
					// if truck has fulfilled an out station order.
					long prevOrderStartTime = lastAct.getStart() - 2 * 60000L;
					currentLoc = truckRes.getStateVariableBy("Location")
							.getValueAt(prevOrderStartTime).getValueAsString();
				}
			}
		}
		return currentLoc;

	}

	private Resource getTrucksUnderMaintenance( Resource res) {
		
		Collection<Order> MaintenanceOrders = RSPContainerHelper.getOrderMap(
				true).getByType("Maintenance");
		
		Resource result =null;
		String selectedDriver = res.getParameterBy("driverId").getValueAsString();

		for (Order o : MaintenanceOrders) {
			String truckNo = o.getParameterBy("truckNo").getValueAsString();
			logger.info("truckUnderMaintenance:" + truckNo);
			Resource truckRes = RSPContainerHelper.getResourceMap(true)
					.getEntryBy(truckNo);
			String mainTruckDriver = truckRes.getParameterBy("driverId").getValueAsString();

			if (o.getState().equals(PlanningState.PLANNED)){
				if(selectedDriver.equalsIgnoreCase(mainTruckDriver)){
					result = truckRes;
				break;
				}
			}
		}
		return result;
	}

	
	
}
