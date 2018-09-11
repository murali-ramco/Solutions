package com.ramco.giga.policy;

import java.util.ArrayList;
import java.util.List;

import com.rsp.core.base.RSPContainerHelper;
import com.rsp.core.base.exception.DataModelException;
import com.rsp.core.base.model.IPolicyContainer;
import com.rsp.core.base.model.Objectives;
import com.rsp.core.base.model.Order;
import com.rsp.core.base.model.ParameterizedElement;
import com.rsp.core.base.model.Resource;
import com.rsp.core.base.model.Task;
import com.rsp.core.base.model.TimeInterval;
import com.rsp.core.base.model.WorkCalendar;
import com.rsp.core.base.model.Workflow;
import com.rsp.core.base.model.constants.TimeDirection;
import com.rsp.core.base.model.parameter.DateParameter;
import com.rsp.core.base.model.parameter.FloatParameter;
import com.rsp.core.base.model.parameter.Parameter;
import com.rsp.core.base.model.parameter.StringParameter;
import com.rsp.core.base.model.stateVariable.FloatState;
import com.rsp.core.base.model.stateVariable.StateValue;
import com.rsp.core.base.model.stateVariable.StringState;
import com.rsp.core.planning.constants.PlanningConstants;
import com.rsp.core.planning.policy.CalendarPolicy;
import com.rsp.core.planning.policy.PlacementResult;
import com.rsp.core.planning.policy.placement.CapacityPlacement;
import com.rsp.core.planning.policy.placement.DefaultPlacementResult;

public class DriverPlacementEast extends CapacityPlacement {

	private static final long serialVersionUID = 1L;

	private static String CAPACITYSTATE_KEY = "Capacity";
	
//	private static String LOCATION_KEY = "Location";

	private static String QUANTITY_KEY = "quantity";

	private static String DURATION_KEY = "duration";

	protected FloatState Capacity;
//	protected StringState Location;
	
	protected double quantity;
	protected long duration;
//	protected String locationVal;
	
	
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

		/*String driverRes = resource.getParameterBy("driver").getValueAsString();
		logger.info("truckDriver: "+driverRes);*/
		PlacementResult placementResult = null;
		long start;
		long end;
		
		ParameterizedElement tmp = task;
		String orderNetId = null;
		while (tmp != null) {
			orderNetId = tmp.getId();
			tmp = tmp.getParent();
		}
		
		
		
		Workflow wf = RSPContainerHelper.getWorkflowMap(true).getEntryBy(orderNetId);

		logger.info("DriverSelectionPolicy");
		Workflow PlanOrderEast = (Workflow)wf.getElementBy("PlanOrderEast");
		Task selectTruck = (Task)PlanOrderEast.getElementBy("selectTruck");
		
		start = selectTruck.getStart();
		end = selectTruck.getEnd();
		
		double upperBound =  (Double) selectTruck.getResource().getStateVariableBy(CAPACITYSTATE_KEY).getUpperBound();
		double lowerBound =  (Double) selectTruck.getResource().getStateVariableBy(CAPACITYSTATE_KEY).getUpperBound();
       


        	FloatState capacityState = (FloatState) resource.getStateVariableBy(CAPACITYSTATE_KEY);
    		double startCapacity = capacityState.getValueAt(start).getValue();
    		double endCapacity = capacityState.getValueAt(end).getValue();
    		double actualCapacity = capacityState.getActualState().getValue();
        	
        	if(startCapacity < lowerBound || endCapacity > upperBound ) {
    			logger.info("Resource is unavailable.");
    			 placementResult =  new DefaultPlacementResult(PlacementResult.FAILURE, "Resource is unavailable.");
    		}

    		else if(endCapacity > lowerBound){
    			logger.info("Resource is unavailable.");
    			return new DefaultPlacementResult(PlacementResult.FAILURE, "Resource is unavailable.");
    		}
        	else if(actualCapacity < lowerBound || actualCapacity > upperBound ) {
     			logger.info("Resource is unavailable.");
     			placementResult = new DefaultPlacementResult(PlacementResult.FAILURE, "Resource is unavailable.");
     		}
        	 
        	else
        		
    	placementResult = new DefaultPlacementResult(PlacementResult.SUCCESS);

			placementResult.setStart(start);
			placementResult.setEnd(end);
			placementResult.setQuantity(quantity);
			placementResult.setResource(resource);
			
			return placementResult;
        	
        }

	
	
	
	/**
	 * updates the state variables of the assigned resource
	 * 
	 * @param task
	 * @param resource
	 * @throws DataModelException
	 */
	@Override
	public void updateStateVariables(Task task, Resource resource, PlacementResult placementResult) {

		Capacity = getFloatState(resource, CAPACITYSTATE_KEY);

		
		
		long start = task.getStart();
		long end = task.getEnd();
		double quantity = task.getQuantity();

		createAndAddFloatChange(task, start, Capacity, quantity );
		createAndAddFloatChange(task, end, Capacity, -quantity); 

	}
	
	@Override
	public void validate(IPolicyContainer policyContainer) {

		super.validate(policyContainer);

		// check if the required parameter exist
		Resource re = (Resource) policyContainer;
		evaluateBounds(re);

	}

	
}
