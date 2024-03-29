/**
 *   (c) 2015-1029  ILS Automation. All rights reserved. 
 */
 package com.ils.blt.common.serializable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ils.blt.common.block.Activity;
import com.ils.blt.common.block.BlockProperty;


/**
 * Use this class to transfer internal block attributes between a block
 * in the Gateway and a status screen in the Designer. We assume that
 * this is a one-way communication. It is the responsibility of the editor
 * make sense of the data transmitted.
 * 
 * Note that the diagramId is guaranteed to be in the attributes
 *      keyed by: BLTProperties.BLOCK_ATTRIBUTE_PARENT
 */
public class SerializableBlockStateDescriptor implements Comparable,Serializable {
	private static final long serialVersionUID = 5499297358912286066L;
	private String className;
	private String name;
	private String idString = null;           // Block Id
	private Map<String,String> attributes;
	private Map<String,BlockProperty> properties;
	private List<Map<String,String>> buffer;
	private List<Activity> activities;
	private List<String> displayedProperties;
	
	public SerializableBlockStateDescriptor() {	
		name="UNSET";
		properties = new HashMap<>();
		attributes = new HashMap<>();
		displayedProperties = new ArrayList<>();  //  the list of blocks attached
		activities = new ArrayList<>();
		buffer = new ArrayList<>();
	}
    public String getClassName() { return className; }
    public void setClassName(String name) { this.className = name; }
	public String getName() {return name;}
	public void setName(String name) {this.name = name;}
	public String getIdString() {return idString;}
	public void setIdString(String idString) {this.idString = idString;}
	public Map<String, String> getAttributes() {return attributes;}
	public Map<String, BlockProperty> getProperties() {return properties;}
	public List<Map<String, String>> getBuffer() {return buffer;}
	public List<Activity> getActivities() {return activities;}
	public List<String> getDisplayedProperties() {return displayedProperties;}
	
	/**
	 * Compare based on name, case insensitive
	 */
	@Override
	public int compareTo(Object obj) {
		SerializableBlockStateDescriptor other = (SerializableBlockStateDescriptor)obj;
		return getName().toUpperCase().compareTo(other.getName().toUpperCase());
	}
}
