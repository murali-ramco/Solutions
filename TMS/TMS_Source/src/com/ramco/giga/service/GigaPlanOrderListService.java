package com.ramco.giga.service;

import java.sql.Connection;
import java.util.Map;

import org.apache.log4j.Logger;

import com.ramco.giga.constant.GigaConstants;
import com.ramco.giga.utils.PlanningUtils;
import com.rsp.core.base.service.DBUploadService;

public class GigaPlanOrderListService extends DBUploadService {
    private static final long serialVersionUID = 1L;
    protected static Logger logger = Logger.getLogger(GigaPlanOrderListService.class);
    private static final String TRUE = "true";
    private static final String FALSE = "false";

    public String doService(Connection connection, DBUploadService.Argument argDBUpload) {
	// cast to inner argument
	Map<String, String> customProperties = argDBUpload.customProperties;
	boolean isAdhocOrders = true;
	if (customProperties.get(GigaConstants.GIGA_ADHOC_ORDERS) != null) {
	    isAdhocOrders = Boolean.valueOf(customProperties.get(GigaConstants.GIGA_ADHOC_ORDERS));
	}
	try {
	    if (isAdhocOrders) {
		PlanningUtils.planInbetweenOrders(connection);
		PlanningUtils.planOrder(connection);
	    } else {
		PlanningUtils.planOrder(connection);
	    }
	} catch (Exception e) {
	    e.printStackTrace();
	    return FALSE;
	}
	return TRUE;
    }
}
