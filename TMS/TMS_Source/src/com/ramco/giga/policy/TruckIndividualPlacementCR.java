package com.ramco.giga.policy;

import com.ramco.giga.utils.GigaUtils;
import com.rsp.core.base.RSPContainerHelper;
import com.rsp.core.base.exception.DataModelException;
import com.rsp.core.base.model.IPolicyContainer;
import com.rsp.core.base.model.Order;
import com.rsp.core.base.model.ParameterizedElement;
import com.rsp.core.base.model.Resource;
import com.rsp.core.base.model.Task;
import com.rsp.core.base.model.TimeInterval;
import com.rsp.core.base.model.WorkCalendar;
import com.rsp.core.base.model.constants.TimeDirection;
import com.rsp.core.base.model.parameter.DurationParameter;
import com.rsp.core.base.model.stateVariable.FloatState;
import com.rsp.core.base.model.stateVariable.StringState;
import com.rsp.core.planning.constants.PlanningConstants;
import com.rsp.core.planning.policy.CalendarPolicy;
import com.rsp.core.planning.policy.PlacementResult;
import com.rsp.core.planning.policy.placement.AbstractPlacement;
import com.rsp.core.planning.policy.placement.DefaultPlacementResult;

/**
 * A simple example for a placement policy. It places the task just to lower bound of the time interval set by the calling function. It updates the capacity state if present.
 * 
 * @author apo team
 */
public class TruckIndividualPlacementCR extends AbstractPlacement {
    private static final long serialVersionUID = 1L;
    /**
     * deprecated, use {@link PlanningConstants} instead
     */
    @Deprecated
    private static String CAPACITYSTATE_KEY = PlanningConstants.CAPACITYSTATE_KEY;
    /**
     * deprecated, use {@link PlanningConstants} instead
     */
    @Deprecated
    private static String QUANTITY_KEY = PlanningConstants.OBJ_QUANTITY_KEY;
    /**
     * deprecated, use {@link PlanningConstants} instead
     */
    @Deprecated
    private static String DURATION_KEY = PlanningConstants.OBJ_DURATION;
    protected FloatState capacity;
    protected double quantity;
    protected long duration;

    /**
     * setup the variables needed in the methods below
     * 
     * @param task
     * @throws DataModelException
     */
    public void evaluateObjectives(Task task) {
	try {
	    quantity = getFloatObjective(task, QUANTITY_KEY);
	    duration = getDurationObjective(task, DURATION_KEY);
	} catch (DataModelException e) {
	    DataModelException dme = new DataModelException("Error in placement policy $policyName while reading objectives of task $taskId", "datamodel.objectivesRead", e);
	    dme.addProperty("policyName", this.getName());
	    dme.addProperty("taskId", task == null ? "null" : task.getId());
	    throw dme;
	}
    }

    /**
     * lookup the bounds on the resource and store to the parameter package
     * 
     * @param resource
     */
    public void evaluateBounds(Resource resource) {
	// nothing to do
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
	capacity = getFloatState(resource, CAPACITYSTATE_KEY);
	long start = task.getStart();
	long end = task.getEnd();
	double quantity = task.getQuantity();
	// the start
	ParameterizedElement tmp = task;
	String orderNetId = null;
	while (tmp != null) {
	    orderNetId = tmp.getId();
	    tmp = tmp.getParent();
	}
	Order order = RSPContainerHelper.getOrderMap(true).getEntryBy(orderNetId);
	StringState location = getStringState(resource, "Location");
	String pickupLocation = order.getParameterBy("pickupLocation").getValueAsString();
	String deliveryLocation = order.getParameterBy("deliveryLocation").getValueAsString();
	Resource giga_parameters = RSPContainerHelper.getResourceMap(true).getEntryBy("giga_parameters");
	double loadingBufferValue = (Double) giga_parameters.getParameterBy("loadingBuffer").getValue();
	long loadingBuffer = (long) (loadingBufferValue * 60000L);
	double unloadingBufferValue = (Double) giga_parameters.getParameterBy("unloadingBuffer").getValue();
	long unloadingBuffer = (long) (unloadingBufferValue * 60000L);
	String CurrLoc = null;
	CurrLoc = GigaUtils.getCurrentLocation(resource);
	createAndAddStringChange(task, start, location, CurrLoc);
	createAndAddStringChange(task, start, location, pickupLocation);
	createAndAddStringChange(task, start + loadingBuffer, location, pickupLocation);
	createAndAddStringChange(task, end - unloadingBuffer, location, deliveryLocation);
	createAndAddStringChange(task, end, location, deliveryLocation);
	createAndAddFloatChange(task, start, capacity, quantity);
	createAndAddFloatChange(task, end, capacity, -quantity);
    }

    /**
     * place the task in time
     * 
     * @param task
     * @param resource
     * @return PlacementResult
     * @throws DataModelException
     */
    @Override
    public PlacementResult place(Task task, Resource resource, TimeInterval horizon, CalendarPolicy calendarPolicy, WorkCalendar workCalendar) {
	String direction = getStringObjective(task, TimeDirection.TIMEDIRECTION_KEY);
	return place(task, resource, horizon, direction, calendarPolicy, workCalendar);
    }

    /**
     * 
     * @see com.rsp.core.planning.policy.placement.AbstractPlacement#place(com.rsp.core.base.model.Task, com.rsp.core.base.model.Resource, com.rsp.core.base.model.TimeInterval, java.lang.String)
     */
    public PlacementResult place(Task task, Resource resource, TimeInterval horizon, String direction, CalendarPolicy calendarPolicy, WorkCalendar workCalendar) {
	evaluateObjectives(task);
	evaluateBounds(resource);
	long start;
	ParameterizedElement tmp = task;
	String orderNetId = null;
	while (tmp != null) {
	    orderNetId = tmp.getId();
	    tmp = tmp.getParent();
	}
	Order order = RSPContainerHelper.getOrderMap(true).getEntryBy(orderNetId);
	if (direction.equals(TimeDirection.TIMEDIRECTION_BACKWARD)) {
	    start = horizon.getUpper() - duration;
	} else {
	    start = horizon.getLower();
	}
	start = ((DurationParameter) order.getParameterBy("TaskStart")).getValue();
	Long end = ((DurationParameter) order.getParameterBy("TaskEnd")).getValue();
	duration = end - start;
	PlacementResult placementResult = new DefaultPlacementResult(PlacementResult.SUCCESS);
	placementResult.setStart(start);
	placementResult.setEnd(start + duration);
	placementResult.setQuantity(quantity);
	placementResult.setResource(resource);
	return placementResult;
    }

    /**
     * @see com.rsp.core.base.policy.Policy#validate(com.rsp.core.base.model.IPolicyContainer)
     */
    @Override
    public void validate(IPolicyContainer policyContainer) {
	super.validate(policyContainer);
	// check if the required parameter exist
	Resource re = (Resource) policyContainer;
	evaluateBounds(re);
    }
}