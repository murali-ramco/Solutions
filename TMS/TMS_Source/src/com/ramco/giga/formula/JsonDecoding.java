package com.ramco.giga.formula;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class JsonDecoding {
    public static final Logger logger = Logger.getLogger(JsonDecoding.class);
    JSONParser parser = new JSONParser();

    public JSONObject parseToJSONObj(String s) {
	//logger.info("logger.info(pe);");
	JSONObject j = null;
	try {
	    Object obj = parser.parse(s);
	    j = (JSONObject) obj;
	} catch (ParseException pe) {
	    logger.info("position: " + pe.getPosition());
	    logger.info(pe);
	}
	return j;
    }
}
