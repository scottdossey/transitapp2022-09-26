package com.tts.transitapp.service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.tts.transitapp.model.Bus;
import com.tts.transitapp.model.BusRequest;
import com.tts.transitapp.model.DistanceResponse;
import com.tts.transitapp.model.GeocodingResponse;
import com.tts.transitapp.model.Location;

@Service
public class TransitService {
	@Value("${transit_url}")
	public String transitUrl;
	
	@Value("${geocoding_url}")
	public String geocodingUrl;
	
	@Value("${distance_url}")
	public String distanceUrl;
	
	@Value("${google_api_key}")
	public String googleApiKey;
	
	
	private List<Bus> getBuses() {
		RestTemplate restTemplate = new RestTemplate();
		Bus[] buses = restTemplate.getForObject(transitUrl, Bus[].class);
		return Arrays.asList(buses);
	}
	
	private Location getCoordinates(String description) {
		try {
			description = URLEncoder.encode(description, "utf-8");
		} catch (UnsupportedEncodingException e) {
			System.out.println("Error urlencoding");
			System.exit(1);
		}
		String url = geocodingUrl + description + "+GA&key=" + googleApiKey;
		RestTemplate restTemplate = new RestTemplate();
		GeocodingResponse response = restTemplate.getForObject(url, GeocodingResponse.class);
		return response.results.get(0).geometry.location;
	}
	
	private double getDistance(Location origin, Location destination) {
		String url = distanceUrl + "origins=" + origin.lat + "," + origin.lng;
		url += "&destinations=" + destination.lat + "," + destination.lng;
		url += "&key=" + googleApiKey;
		
		RestTemplate restTemplate = new RestTemplate();
		DistanceResponse response = restTemplate.getForObject(url,  DistanceResponse.class);
		
		//Conversion factor is to convert meters to miles.
		return response.rows.get(0).elements.get(0).distance.value * 0.000621371;
	}
	
	public List<Bus> getNearbyBuses(BusRequest request) {
		//To get the nearby buses our strategy will be
		//to get all the buses and then filter them by distance.
		
		//Start by getting all the buses
		List<Bus> allBuses = this.getBuses();
		
		//Use geocoding API to get lat,lng of request.
		Location personLocation = getCoordinates(request.address+ " " + request.city);
		
		List<Bus> nearbyBuses = new ArrayList<>();
		
		for(Bus bus: allBuses) {
			Location busLocation = new Location();
			busLocation.lat = bus.LATITUDE;
			busLocation.lng = bus.LONGITUDE;
			
			//What we could do here is we could then use the google DISTANCE API to 
			//figure out the distance from each bus to the person location.
			
			//HOWEVER, this is not a good idea to do naively...because there are potentially
			//a lot of buses in the system, and consequently, we could end up doing
			//a lot of API calls to the google DISTANCE MATRIX API.  This takes time, and
			//could potentially cost money or exceed our quotas...so we want to
			//do a preliminary filtering to eliminate buses which are clearly to far
			//away to be within our search range.
			
			double latDistance = Double.parseDouble(busLocation.lat) - 
					Double.parseDouble(personLocation.lat);
			
			double lngDistance = Double.parseDouble(busLocation.lng) - 
					Double.parseDouble(personLocation.lng);
		
			//This is our rough filter to eliminate the vast majority of API calls.
			if (Math.abs(latDistance) <= 0.02 && Math.abs(lngDistance) < 0.02) {
				//Then we are going to do exact queries to the distance API
				//inside this if statement.
				double distance = getDistance(busLocation, personLocation);
				if ( distance <= 1.0) {
					//Round to two decimal digits.
					bus.distance = (double) Math.round(distance * 100) / 100;
					nearbyBuses.add(bus);
				}							
			}
		}
		Collections.sort(nearbyBuses, 
						 (bus1, bus2) -> {
							 if (bus1.distance < bus2.distance)  {
								 return -1;
							 }
							 if (bus1.distance > bus2.distance)  {
								 return 1;
							 }
							 return 0;
						 });
				
				
		return nearbyBuses;					
	}
	
	
}
