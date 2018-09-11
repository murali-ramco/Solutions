package com.ramco.giga.policy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import com.rsp.core.base.RSPContainerHelper;
import com.rsp.core.base.exception.RSPException;
import com.rsp.core.base.model.ElementMap;
import com.rsp.core.base.model.Order;
import com.rsp.core.base.model.ParameterizedElement;
import com.rsp.core.base.model.Resource;
import com.rsp.core.base.model.Task;
import com.rsp.core.base.model.Workflow;
import com.rsp.core.base.model.WorkflowElement;
import com.rsp.core.base.model.constants.TimeDirection;
import com.rsp.core.base.model.parameter.StringParameter;
import com.rsp.core.base.policy.TimeRelation;
import com.rsp.core.planning.policy.PlacementResult;
import com.rsp.core.planning.policy.resourceSelection.AbstractResourceSelection;
import com.rsp.core.planning.policy.resourceSelection.JITResourceSelection;

/**
 */
public class DriverSelection extends JITResourceSelection {

	private static final long serialVersionUID = 1L;
	

	@SuppressWarnings("deprecation")
	@Override
	public PlacementResult selectPlacementResult(WorkflowElement workflowElement, List<PlacementResult> placements) {

		if (placements.size() == 0)
			return null;

		PlacementResult result = null;
		
		ParameterizedElement tmp = workflowElement;
		String orderNetId = null;
		while (tmp != null) {
			orderNetId = tmp.getId();
			tmp = tmp.getParent();
		}

		
		Workflow wf = RSPContainerHelper.getWorkflowMap(true).getEntryBy(orderNetId);

		logger.info("DriverSelectionPolicy");
		Workflow chooseReswf = (Workflow)wf.getElementBy("chooseRes");
		Task selectTruck = (Task)chooseReswf.getElementBy("selectTruck");
		
		Resource previousRe = selectTruck.getResource();
		logger.info("selectTruck: "+previousRe);
		String dependentDriver = previousRe.getParameterBy("driverId").getValueAsString();
		
		ElementMap<Resource> resourceMap = RSPContainerHelper.getResourceMap(true);
		Resource preferredDriverRes = resourceMap.getEntryBy(dependentDriver);
		
		logger.info("dependentDriver : "+preferredDriverRes);
		

			for (PlacementResult placementResult : placements) {
					
				if(placementResult.getResource()== preferredDriverRes){	
					result = placementResult;
					
				}	
				
				else{
					
					result = super.selectPlacementResult(workflowElement, placements);
					
			}		
			}

		return result;
	}
	/**
	 * @see com.rsp.core.planning.policy.resourceSelection.ResourceSelectionPolicy#evaluatePlacements(com.rsp.core.base.model.WorkflowElement,
	 *      java.util.List)
	 */
	public PlacementResult evaluatePlacements(WorkflowElement workflowElement,
			List<PlacementResult> successfulPlacements) {

		if (successfulPlacements.isEmpty())
			return null;

		// We take the last placement that is available, that is, the last placement that was added, under the assumption, 
		// that all the other ones have already been evaluated and have failed.

		PlacementResult latestResult = successfulPlacements.get(successfulPlacements.size() - 1);

		TimeRelation timeRelation = workflowElement.getTimeRelation();

		// else, search for a new resource to place
		StringParameter timeArrowP = (StringParameter) workflowElement.getObjectiveBy(TimeDirection.TIMEDIRECTION_KEY);
		String timeArrow = timeArrowP.getValue();

		// do the search

			long EarliestStart = timeRelation.getEarliest(workflowElement, TimeDirection.TIMEDIRECTION_FORWARD);

			if (EarliestStart == latestResult.getStart()) {
				return latestResult;
			}

		

		// the last placement is not scheduled at the latest point in time: return none selected.
		return null;
	}

}
 
