package com.ramco.giga.formula;

/* Distance and duration between two location.
 * @author: 11893
 * Date: June 12, 17
 */
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.ramco.giga.constant.GigaConstants;
import com.ramco.giga.utils.GigaUtils;
import com.rsp.core.base.RSPContainerHelper;
import com.rsp.core.base.encrypt.PasswordEncrypter;
import com.rsp.core.base.model.Resource;
import com.rsp.core.base.model.parameter.FloatParameter;

public class FindDistanceDuration {
    protected static Logger logger = Logger.getLogger(FindDistanceDuration.class);

    public static String getDistanceDuration(double sourceLatitude, double sourceLongitude, double destinationLatitude, double destinationLongitude) {
	StringBuffer result = new StringBuffer();
	StringBuffer output = null;
	String data = null;
	URL url = null;
	HttpsURLConnection connection = null;
	BufferedReader br = null;
	String origLatitude = Double.toString(sourceLatitude);
	String origLongitude = Double.toString(sourceLongitude);
	String destLatitude = Double.toString(destinationLatitude);
	String destLongitude = Double.toString(destinationLongitude);
	String origin = origLatitude.concat(",");
	origin = origin.concat(origLongitude).trim();
	String destination = destLatitude.concat(",");
	destination = destination.concat(destLongitude).trim();
	String googleDistanceMatrixUrl = "";
	float speedOfVehicle = 60.0f;
	logger.info("origin: " + origin + " " + "destination: " + destination);
	try {
	    Resource gigaParamRes = RSPContainerHelper.getResourceMap(true).getEntryBy("giga_parameters");
	    Double durationCalculator = ((FloatParameter) gigaParamRes.getParameterBy("durationCalculation")).getValue();
	    if (durationCalculator == 1.0) {
		String googleApiLicense = gigaParamRes.getParameterBy("googleApiLicense").getValueAsString();
		String decripLicence = PasswordEncrypter.decrypt(googleApiLicense);
		// googleDistanceMatrixUrl =
		// googleDistanceMatrixUrl.concat("/json?");
		googleDistanceMatrixUrl = gigaParamRes.getParameterBy("googleDistanceMatrixUrl").getValueAsString();
		String addressLink = googleDistanceMatrixUrl + "origins=" + origin + "&destinations=" + destination;
		addressLink = addressLink + "&mode=driving&language=en-US";
		if (decripLicence != null && !decripLicence.isEmpty())
		    addressLink = addressLink + "&key=" + decripLicence;
		url = new URL(addressLink);
		logger.info(url);
		connection = (HttpsURLConnection) url.openConnection();
		br = new BufferedReader(new InputStreamReader(connection.getInputStream(), Charset.forName("UTF-8")));
		if (connection.getResponseCode() == 200) {
		    output = new StringBuffer();
		    while ((data = br.readLine()) != null)
			output.append(data);
		} else {
		    logger.info("URL could not open");
		    logger.info("Status: " + connection.getResponseCode());
		}
		logger.info("DistMatrix Response: ----->>>>> " + output.toString());
		JsonDecoding jsonDecoding = new JsonDecoding();
		JSONObject jsonObject = jsonDecoding.parseToJSONObj(output.toString());
		String status = jsonObject.get("status").toString();
		if (status.equalsIgnoreCase("OK")) {
		    JSONArray jsonArray = (JSONArray) jsonObject.get("rows");
		    for (Object object : jsonArray) {
			JSONObject x = (JSONObject) object;
			JSONArray elements = (JSONArray) x.get("elements");
			for (Object element : elements) {
			    JSONObject n = (JSONObject) element;
			    if ((n.get("status").toString()).equalsIgnoreCase("OK")) {
				JSONObject jsonDistance = (JSONObject) n.get("distance");
				JSONObject jsonDuration = (JSONObject) n.get("duration");
				// logger.info(jsonDistance.get("value"));
				String distance = jsonDistance.get("value").toString();
				String duration = jsonDuration.get("value").toString();
				long di = Long.parseLong(distance);
				long du = GigaUtils.getDurationByDrivingFactor(Long.parseLong(duration));
				String s1 = Long.toString(di);
				String s2 = Long.toString(du);
				result.append(s1);
				result.append(",");
				result.append(s2);
				logger.info("Distance and duration is calculated by Google API");
			    } else {
				// result.append("Zero result set");
				logger.info("Distance and duration is calculated by Haversine with average speed " + speedOfVehicle);
				String res = getHeversine(sourceLatitude, sourceLongitude, destinationLatitude, destinationLongitude, speedOfVehicle);
				result.append(res);
				return result.toString();
			    }
			}
		    }
		} else {
		    // result.append("Zero result set");
		    logger.info("Distance and duration is calculated by Haversine with average speed " + speedOfVehicle);
		    String res = getHeversine(sourceLatitude, sourceLongitude, destinationLatitude, destinationLongitude, speedOfVehicle);
		    result.append(res);
		    return result.toString();
		}
	    } else {
		logger.info("Distance and duration is calculated by Haversine with average speed " + speedOfVehicle);
		String res = getHeversine(sourceLatitude, sourceLongitude, destinationLatitude, destinationLongitude, speedOfVehicle);
		result.append(res);
		return result.toString();
	    }
	    return result.toString();
	} catch (Exception ex) {
	    //ex.printStackTrace();
	    logger.info("Distance and duration is calculated by Haversine with average speed " + speedOfVehicle);
	    String res = getHeversine(sourceLatitude, sourceLongitude, destinationLatitude, destinationLongitude, speedOfVehicle);
	    result.append(res);
	    return result.toString();
	} finally {
	    if (br != null) {
		try {
		    br.close();
		} catch (IOException e) {
		    e.printStackTrace();
		}
		br = null;
	    }
	    if (connection != null) {
		connection.disconnect();
		connection = null;
	    }
	}
    }

    public static List<String> getMultipleDistanceDuration(List<String> sourceLatlong, List<String> destinationLatLong) {
	StringBuffer result = new StringBuffer();
	StringBuffer output = null;
	String data = null;
	URL url = null;
	HttpsURLConnection connection = null;
	BufferedReader br = null;
	String origin = "";
	String destination = destinationLatLong.get(0);
	String googleDistanceMatrixUrl = "";
	//float speedOfVehicle = 60.0f;
	List<String> outDistDur = new ArrayList<String>();
	logger.info("origin: " + origin + " " + "destination: " + destination);
	try {
	    Resource gigaParamRes = RSPContainerHelper.getResourceMap(true).getEntryBy(GigaConstants.RES_GIGA_PARAM);
	    Double durationCalculator = ((FloatParameter) gigaParamRes.getParameterBy(GigaConstants.GIGA_PARAM_DUR_CALCULATION)).getValue();
	    int maxElementsGAPI = ((FloatParameter) gigaParamRes.getParameterBy(GigaConstants.GIGA_PARAM_MAXELEMENTS_GAPI)).getValue().intValue();
	    int sourceLatlongSize = sourceLatlong.size();
	    int noOfChops = sourceLatlongSize / maxElementsGAPI;
	    int noOfRemainders = sourceLatlongSize % maxElementsGAPI;
	    int subcount = 0;
	    for (int i = 0; i <= noOfChops; i++) {
		//while (sourceLatlongSize > 0) {
		if (i == noOfChops)
		    maxElementsGAPI = noOfRemainders;
		List<String> choppedSourceLatlong = sourceLatlong.subList(subcount, subcount + maxElementsGAPI);
		subcount = subcount + maxElementsGAPI;
		for (String latLong : choppedSourceLatlong)
		    origin = origin.concat(latLong).concat("|");
		if (durationCalculator == 1.0) {
		    String googleApiLicense = gigaParamRes.getParameterBy("googleApiLicense").getValueAsString();
		    String decripLicence = PasswordEncrypter.decrypt(googleApiLicense);
		    // googleDistanceMatrixUrl =
		    // googleDistanceMatrixUrl.concat("/json?");
		    googleDistanceMatrixUrl = gigaParamRes.getParameterBy("googleDistanceMatrixUrl").getValueAsString();
		    String addressLink = googleDistanceMatrixUrl + "origins=" + origin + "&destinations=" + destination;
		    addressLink = addressLink + "&mode=driving&language=en-US";
		    if (decripLicence != null && !decripLicence.isEmpty())
			addressLink = addressLink + "&key=" + decripLicence;
		    url = new URL(addressLink);
		    logger.info(url);
		    connection = (HttpsURLConnection) url.openConnection();
		    br = new BufferedReader(new InputStreamReader(connection.getInputStream(), Charset.forName("UTF-8")));
		    if (connection.getResponseCode() == 200) {
			output = new StringBuffer();
			while ((data = br.readLine()) != null)
			    output.append(data);
		    } else {
			logger.info("URL could not open");
			logger.info("Status: " + connection.getResponseCode());
		    }
		    logger.info("DistMatrix Response: ----->>>>> " + output.toString());
		    JsonDecoding jsonDecoding = new JsonDecoding();
		    JSONObject jsonObject = jsonDecoding.parseToJSONObj(output.toString());
		    String status = jsonObject.get("status").toString();
		    if (status.equalsIgnoreCase("OK")) {
			JSONArray jsonArray = (JSONArray) jsonObject.get("rows");
			for (Object object : jsonArray) {
			    JSONObject x = (JSONObject) object;
			    JSONArray elements = (JSONArray) x.get("elements");
			    for (Object element : elements) {
				JSONObject n = (JSONObject) element;
				if ((n.get("status").toString()).equalsIgnoreCase("OK")) {
				    JSONObject jsonDistance = (JSONObject) n.get("distance");
				    JSONObject jsonDuration = (JSONObject) n.get("duration");
				    // logger.info(jsonDistance.get("value"));
				    String distance = jsonDistance.get("value").toString();
				    String duration = jsonDuration.get("value").toString();
				    long di = Long.parseLong(distance);
				    long du = Long.parseLong(duration);
				    String s1 = Long.toString(di);
				    String s2 = Long.toString(du);
				    result.append(s1);
				    result.append(",");
				    result.append(s2);
				    outDistDur.add(result.toString());
				    result.delete(0, result.length());
				    logger.info("Distance and duration is calculated by Google API");
				    origin = "";
				} else {
				    /*// result.append("Zero result set");
				    logger.info("Distance and duration is calculated by Haversine with average speed " + speedOfVehicle);
				    String res = getHeversine(sourceLatitude, sourceLongitude, destinationLatitude, destinationLongitude, speedOfVehicle);
				    result.append(res);
				    return result.toString();*/
				}
			    }
			}
		    } else {
			// result.append("Zero result set");
			/*logger.info("Distance and duration is calculated by Haversine with average speed " + speedOfVehicle);
			String res = getHeversine(sourceLatitude, sourceLongitude, destinationLatitude, destinationLongitude, speedOfVehicle);
			result.append(res);
			return result.toString();*/
		    }
		} else {
		    /*logger.info("Distance and duration is calculated by Haversine with average speed " + speedOfVehicle);
		    String res = getHeversine(sourceLatitude, sourceLongitude, destinationLatitude, destinationLongitude, speedOfVehicle);
		    result.append(res);
		    return result.toString();*/
		}
	    }
	    return outDistDur;
	} catch (Exception ex) {
	    ex.printStackTrace();
	    /* logger.info("Distance and duration is calculated by Haversine with average speed " + speedOfVehicle);
	     String res = getHeversine(sourceLatitude, sourceLongitude, destinationLatitude, destinationLongitude, speedOfVehicle);
	     result.append(res);
	     return result.toString();*/
	} finally {
	    if (br != null) {
		try {
		    br.close();
		} catch (IOException e) {
		    e.printStackTrace();
		}
		br = null;
	    }
	    if (connection != null) {
		connection.disconnect();
		connection = null;
	    }
	}
	return outDistDur;
    }

    private static String getHeversine(double sourceLatitude, double sourceLongitude, double destinationLatitude, double destinationLongitude, float speedOfVehicle) {
	double dist = haversineFormula(sourceLatitude, sourceLongitude, destinationLatitude, destinationLongitude);
	double dur = dist / speedOfVehicle;
	long di = (long) dist;
	long du = (long) dur;
	du = GigaUtils.getDurationByDrivingFactor(du);
	String distance = Long.toString(di);
	String duration = Long.toString(du);
	String result = distance + "," + duration;
	return result;
    }

    public static double haversineFormula(double targetLat, double targetLon, double curLat, double curLon) {
	double result = 0.00;
	try {
	    final double R = 6372.8; // radius of earth
	    double dLat = Math.toRadians(curLat - targetLat);
	    double dLon = Math.toRadians(curLon - targetLon);
	    targetLat = Math.toRadians(targetLat);
	    curLat = Math.toRadians(curLat);
	    double a = Math.pow(Math.sin(dLat / 2), 2) + Math.pow(Math.sin(dLon / 2), 2) * Math.cos(targetLat) * Math.cos(curLat);
	    double c = 2 * Math.asin(Math.sqrt(a));
	    result = R * c;
	} catch (Exception ex) {
	    ex.printStackTrace();
	}
	return result * 1000;
    }

    public static void main(String[] args) {
	//FindDistanceDuration obj = new FindDistanceDuration();
	String result = getDistanceDuration(13.0827, 80.2707, 19.0760, 72.8777);
	logger.info("Distance and duration: " + result);
    }
}
