/**
 *   (c) 2014-2015  ILS Automation. All rights reserved.
 *  
 */
package com.ils.blt.common;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ils.blt.common.block.BlockProperty;
import com.ils.blt.common.block.PalettePrototype;
import com.ils.blt.common.serializable.SerializableAnchor;
import com.ils.blt.common.serializable.SerializableBlockStateDescriptor;
import com.ils.blt.common.serializable.SerializableResourceDescriptor;
import com.inductiveautomation.ignition.client.gateway_interface.GatewayConnectionManager;
import com.inductiveautomation.ignition.common.util.LogUtil;
import com.inductiveautomation.ignition.common.util.LoggerEx;



/**
 *  This class is a common point for managing requests to the gateway dealing with the
 *  execution engine and block status. It is designed for use by Java code in the designer 
 *  as well as Python scripting. It provides a way to request/set properties of 
 *  diagrams, blocks and connections.
 *  
 *  Each request is relayed to the Gateway scope via an RPC call.
 */
public class ApplicationRequestHandler implements ToolkitRequestHandler {
	private final static String TAG = "ApplicationRequestHandler";
	private final LoggerEx log;

	/**
	 * Constructor adds common attributes that are needed to generate unique keys to identify
	 * blocks and connectors.
	 */
	public ApplicationRequestHandler()  {
		log = LogUtil.getLogger(getClass().getPackage().getName());
	}

	/**
	 * Remove all current diagrams from the controller.
	 */
	public void clearController() {
		try {
			GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.MODULE_ID, "clearController");
			log.debugf("%s.clearController ...",TAG);
		}
		catch(Exception ge) {
			log.infof("%s.clearController: GatewayException (%s)",TAG,ge.getMessage());
		}
	}

	/**
	 * Determine whether or not the indicated diagram is known to the controller.
	 */
	public boolean diagramExists(String uuidString) {
		boolean result = false;
		try {
			Boolean value = (Boolean)GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.MODULE_ID, "diagramExists",uuidString);
			log.debugf("%s.diagramExists  ...%s = %s",TAG,uuidString,result);
			if( value!=null ) result = value.booleanValue();
		}
		catch(Exception ge) {
			log.infof("%s.diagramExists: GatewayException (%s)",TAG,ge.getMessage());
		}
		return result;
	}
	/**
	 * Execute evaluate() on a specified block
	 */
	public void evaluateBlock(String diagramId,String blockId) {
		log.debugf("%s.evaluateBlock ...",TAG);

		try {
			GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.MODULE_ID, "evaluateBlock",diagramId,blockId);
		}
		catch(Exception ge) {
			log.infof("%s.evaluateBlock: GatewayException (%s)",TAG,ge.getMessage());
		}
	}
	@Override
	public String getApplicationName(String uuid) {
		String name = "NULL UUID";
		if( uuid!=null) {
			log.infof("%s.getApplicationName... %s",TAG,uuid);
			try {
				name = (String)GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
						BLTProperties.MODULE_ID, "getApplicationName",uuid);
			}
			catch(Exception ex) {
				log.infof("%s.getApplicationName: Exception (%s)",TAG,ex.getMessage());
			};
		}
		return name;
	}
	/**
	 * Find the parent application or diagram of the entity referenced by
	 * the supplied id. Test the state and return the name of the appropriate
	 * database.  
	 * @param uuid
	 * @return database name
	 */
	@Override
	public String getDatabaseForUUID(String uuid) {
		String db = "NONE";
		if( uuid!=null) {
			log.infof("%s.getDatabaseForUUID... %s",TAG,uuid);
			try {
				db = (String)GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
						BLTProperties.MODULE_ID, "getDatabaseForUUID",uuid);
			}
			catch(Exception ex) {
				log.infof("%s.getDatabaseForUUID: Exception (%s)",TAG,ex.getMessage());
			};
		}
		return db;
	}
	
	/**
	 * Obtain a list of BlockProperty objects for the specified block. If the block is not known to the gateway
	 * it will be created.
	 * 
	 * @param projectId
	 * @param resourceId
	 * @param blockId
	 * @param className
	 * @return an array of block properties for the subject block
	 */
	@SuppressWarnings("unchecked")
	public BlockProperty[] getBlockProperties(String className,long projectId,long resourceId,UUID blockId) {
		log.debugf("%s.getBlockProperties: for block %s (%s)",TAG,blockId.toString(),className);
		BlockProperty[] result = null;
		List<String> jsonList = new ArrayList<String>();
		try {
			jsonList = (List<String>)GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.MODULE_ID, "getBlockProperties",className,new Long(projectId),new Long(resourceId),blockId.toString());
		}
		catch(Exception ge) {
			log.infof("%s.getBlockProperties: GatewayException (%s)",TAG,ge.getMessage());
		}
				
		if( jsonList!=null) {
			result = new BlockProperty[jsonList.size()];
			int index = 0;
			for( String json:jsonList ) {
				log.tracef("%s: property: %s",TAG,json);
				BlockProperty bp = BlockProperty.createProperty(json);
				log.debugf("%s.getBlockProperties: %s",TAG, bp.toString());
				result[index]=bp;
				index++;
			}
		}
		else 
		{
			result = new BlockProperty[0];
		}
		return result;
	}
	
	@SuppressWarnings("unchecked")
	public List<PalettePrototype> getBlockPrototypes() {
		log.tracef("%s.getBlockPrototypes ...",TAG);
		List<PalettePrototype> result = new ArrayList<PalettePrototype>();
		List<String> jsonList = new ArrayList<String>();
		try {
			jsonList = (List<String> )GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.MODULE_ID, "getBlockPrototypes");
		}
		catch(Exception ge) {
			log.infof("%s.getBlockPrototypes: GatewayException (%s)",TAG,ge.getMessage());
		}
		
		if( jsonList!=null) {
			for( String json:jsonList ) {
				log.tracef("%s.getBlockPrototypes: %s",TAG,json);
				PalettePrototype bp = PalettePrototype.createPrototype(json);
				result.add(bp);
			}
		}
		return result;
	}


	/**
	 * Determine whether or not the engine is running.
	 */
	public String getControllerState() {
		String state = "";
		try {
			// Returns either "running" or "stopped"
			state = (String)GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.MODULE_ID, "getControllerState");
			log.debugf("%s.getControllerState ... %s",TAG,state);
		}
		catch(Exception ge) {
			log.infof("%s.getControllerState: GatewayException (%s)",TAG,ge.getMessage());
		}
		return state;
	}
	
	
	@SuppressWarnings("unchecked")
	@Override
	public List<String> getDatasourceNames() {
		List<String> names = new ArrayList<>();
		try {
			names = (List<String>)GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.MODULE_ID, "getDatasourceNames");
		}
		catch(Exception ge) {
			log.infof("%s.getControllerState: GatewayException (%s)",TAG,ge.getMessage());
		}
		return names;
	}
	
	/**
	 * @param diagramId identifier of the diagram to be queried, a String
	 * @param className fully qualified class name of blocks to be listed
	 * @return a list of ids for blocks owned by a specified diagram that are of a
	 *         specified class.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public List getDiagramBlocksOfClass(String diagramId,String className) {
		log.debugf("%s.getDiagramBlocksOfClass: for diagram %s (%s)",TAG,diagramId,className);
		List<String> blockList = new ArrayList<String>();
		try {
			blockList = (List<String>)GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.MODULE_ID, "getDiagramBlocksOfClass",diagramId,className);
		}
		catch(Exception ge) {
			log.infof("%s.getBlockProperties: GatewayException (%s)",TAG,ge.getMessage());
		}
		return blockList;
	}
	/**
	 * @return the current state of the specified block.
	 */
	public String getBlockState(String diagramId, String blockName) {
		String state = "UNKNOWN";
		try {
			state = (String)GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.MODULE_ID, "getBlockState",diagramId,blockName);
			log.debugf("%s.getBlockState %s = %s",TAG,blockName,state);
		}
		catch(Exception ge) {
			log.infof("%s.getBlockState: GatewayException (%s)",TAG,ge.getMessage());
		}
		return state;
	}
	@SuppressWarnings("unchecked")
	public List<SerializableResourceDescriptor> getDiagramDescriptors(String projectName) {
		log.debugf("%s.getDiagramDescriptors for %s ...",TAG,projectName);
		List<SerializableResourceDescriptor> result = new ArrayList<>();
		List<String> jsonList = new ArrayList<String>();
		try {
			jsonList = (List<String>)GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.MODULE_ID, "getDiagramDescriptors",projectName);
		}
		catch(Exception ge) {
			log.infof("%s.getDiagramDescriptors: GatewayException (%s)",TAG,ge.getMessage());
		}
		if( jsonList!=null) {

			ObjectMapper mapper = new ObjectMapper();
			for(String json:jsonList) {
				try {
					SerializableResourceDescriptor entry = mapper.readValue(json, SerializableResourceDescriptor.class);
					result.add(entry);
				} 
				catch (JsonParseException jpe) {
					log.warnf("%s: getDiagramDescriptors parse exception (%s)",TAG,jpe.getLocalizedMessage());
				}
				catch(JsonMappingException jme) {
					log.warnf("%s: getDiagramDescriptors mapping exception (%s)",TAG,jme.getLocalizedMessage());
				}
				catch(IOException ioe) {
					log.warnf("%s: getDiagramDescriptors IO exception (%s)",TAG,ioe.getLocalizedMessage());
				}
			}
		}
		return result;
	}
	/**
	 * @return the current state of the specified diagram.
	 */
	public DiagramState getDiagramState(Long projectId, Long resourceId) {
		DiagramState result = DiagramState.ACTIVE;
		try {
			String state = (String)GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.MODULE_ID, "getDiagramState",projectId,resourceId);
			log.debugf("%s.getDiagramState ... %s",TAG,result.toString());
			result = DiagramState.valueOf(state);
		}
		catch(Exception ge) {
			log.infof("%s.getDiagramState: GatewayException (%s)",TAG,ge.getMessage());
		}
		return result;
	}
	@Override
	public String getFamilyName(String uuid) {
		String name = "NULL UUID";
		if( uuid!=null ) {
			log.infof("%s.getFamilyName... %s",TAG,uuid);
			try {
				name = (String)GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
						BLTProperties.MODULE_ID, "getFamilyName",uuid);
			}
			catch(Exception ex) {
				log.infof("%s.getFamilyName: Exception (%s)",TAG,ex.getMessage());
			};
		}
		return name;
	}
	
	/**
	 * @return internal details of a block for debugging purposes.
	 */
	public SerializableBlockStateDescriptor getInternalState(String diagramId,String blockId) {
		//log.infof("%s.getInternalState ... %s,%s",TAG,diagramId,blockId);
		SerializableBlockStateDescriptor result = new SerializableBlockStateDescriptor();
		String json = null;
		try {
			// Returns either "running" or "stopped"
			json = (String)GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.MODULE_ID, "getInternalState",diagramId,blockId);
			log.debugf("%s.getInternalState ... %s",TAG,json);
		}
		catch(Exception ge) {
			log.infof("%s.getInternalState: GatewayException (%s)",TAG,ge.getMessage());
		}
		if( json!=null) {
			ObjectMapper mapper = new ObjectMapper();

			try {
				result = mapper.readValue(json, SerializableBlockStateDescriptor.class);
			} 
			catch (JsonParseException jpe) {
				log.warnf("%s: getInternalState parse exception (%s)",TAG,jpe.getLocalizedMessage());
			}
			catch(JsonMappingException jme) {
				log.warnf("%s: getInternalState mapping exception (%s)",TAG,jme.getLocalizedMessage());
			}
			catch(IOException ioe) {
				log.warnf("%s: getInternalState IO exception (%s)",TAG,ioe.getLocalizedMessage());
			}
		}
		return result;
	}
	
	/**
	 * @param diagramId identifier of the diagram owning the block, a String
	 * @param blockId identifier of the block within the diagram, a String
	 * @param propertyName name of the property for which a value is to be returned
	 * @return the value of a specified block property.
	 */
	public Object getPropertyValue(String diagramId,String blockId,String propertyName) {
		Object result = null;
		try {
			result = GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.MODULE_ID, "getPropertyValue",diagramId,blockId,propertyName);
			log.debugf("%s.getPropertyValue ... %s",TAG,result.toString());
		}
		catch(Exception ge) {
			log.infof("%s.getPropertyValue: GatewayException (%s)",TAG,ge.getMessage());
		}
		return result;
	}
	
	/**
	 * Acquire a value from the HSQL database table associated with the toolkit. A
	 * empty string is returned if the string is not found, null if an exception is thrown.
	 * @param propertyName name of the property for which a value is to be returned
	 * @return the value of the specified property.
	 */
	public String getToolkitProperty(String propertyName) {
		String result = null;
		//log.infof("%s.getToolkitProperty ... %s",TAG,propertyName);
		try {
			result = (String)GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.MODULE_ID, "getToolkitProperty",propertyName);
			log.tracef("%s.getToolkitProperty ... %s = %s",TAG,propertyName,result.toString());
		}
		catch(Exception ge) {
			log.infof("%s.getToolkitProperty: GatewayException (%s:%s)",TAG,ge.getClass().getName(),ge.getMessage());
		}
		return result;
	}
	/**
	 * Determine whether or not the engine is running.
	 */
	public boolean isControllerRunning() {
		boolean isRunning = false;
		String state = getControllerState();
		if( state.equalsIgnoreCase("running")) isRunning = true;
		return isRunning;
	}
	/**
	 * Post a (simulated) block result on its output.
	 * @param diagramId the parent diagram
	 * @param blockId
	 * @param port
	 * @param value
	 */
	public void postResult(String diagramId,String blockId,String port,String value) {
		try {
			GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.MODULE_ID, "postResult",diagramId,blockId,port,value);
		}
		catch(Exception ge) {
			log.infof("%s.postResult: GatewayException (%s)",TAG,ge.getMessage());
		}
	}
	/**
	 * Query the gateway for list of resources that the block controller knows about. 
	 * This is a debugging aid. 
	 * 
	 * @return a list of resources known to the BlockController.
	 */
	@SuppressWarnings("unchecked")
	public List<SerializableResourceDescriptor> queryControllerResources() {
		List<SerializableResourceDescriptor> result = null;
		try {
			result = (List<SerializableResourceDescriptor> )GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.MODULE_ID, "queryControllerResources");
		}
		catch(Exception ge) {
			log.infof("%s.queryControllerResources: GatewayException (%s)",TAG,ge.getMessage());
		}
		return result;
	}
	/**
	 * Query a diagram in the gateway for list of blocks that it knows about. 
	 * This is a debugging aid. 
	 * 
	 * @return a list of blocks known to the diagram.
	 */
	@SuppressWarnings("unchecked")
	public List<SerializableResourceDescriptor> queryDiagram(String diagramId) {
		List<SerializableResourceDescriptor> result = null;
		try {
			result = (List<SerializableResourceDescriptor> )GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.MODULE_ID, "queryDiagram",diagramId);
		}
		catch(Exception ge) {
			log.infof("%s.queryDiagram: GatewayException (%s)",TAG,ge.getMessage());
		}
		return result;
	}
	/**
	 * Execute reset() on a specified block
	 */
	public void resetBlock(String diagramId,String blockId) {
		log.debugf("%s.resetBlock ...",TAG);

		try {
			GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.MODULE_ID, "resetBlock",diagramId,blockId);
		}
		catch(Exception ge) {
			log.infof("%s.resetBlock: GatewayException (%s)",TAG,ge.getMessage());
		}
	}

	/**
	 * Execute reset() on every block on the diagram
	 */
	public void resetDiagram(String diagramId) {
		log.debugf("%s.resetDiagram ...",TAG);

		try {
			GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.MODULE_ID, "resetDiagram",diagramId);
		}
		catch(Exception ge) {
			log.infof("%s.resetDiagram: GatewayException (%s)",TAG,ge.getMessage());
		}
	}
	/**
	 * Determine whether or not the indicated resource is known to the controller.
	 */
	public boolean resourceExists(long projectId,long resid) {
		Boolean result = null;
		try {
			result = (Boolean)GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.MODULE_ID, "resourceExists",new Long(projectId),new Long(resid));
			log.debugf("%s.resourceExists ...%d:%d = %s",TAG,projectId,resid,result);
		}
		catch(Exception ge) {
			log.infof("%s.resourceExists: GatewayException (%s)",TAG,ge.getMessage());
		}
		if( result==null ) return false;
		return result.booleanValue();
	}
	
	/**
	 * Send a signal to all blocks of a particular class on a specified diagram.
	 * This is a "local" transmission. The diagram is specified by a tree-path.
	 * There may be no successful recipients.
	 * 
	 * @param diagramId
	 * @param className filter of the receiver blocks to be targeted.
	 * @param command string of the signal.
	 */
	@Override
	public boolean sendLocalSignal(String diagramId, String command,String message,String arg) {
		log.infof("%s.sendLocalSignal for %s %s %s %s...",TAG,diagramId,command,message,arg);
		boolean result = false;
		try {
			Boolean value = GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.MODULE_ID, "sendLocalSignal",diagramId,command,message,arg);
			if( value!=null ) result = value.booleanValue();
		}
		catch(Exception ex) {
			log.infof("%s.sendLocalSignal: Exception (%s)",TAG,ex.getMessage());
		}
		return result;
	}
	/**
	 * Change the state of every diagram in the named application
	 * to the specified state.
	 * @param appname name of the application
	 * @param state new diagram state
	 */
	@Override
	public void setApplicationState(String appname, String state) {
		log.infof("%s.setApplicationState for %s to %s...",TAG,appname,state);
		try {
			GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.MODULE_ID, "setApplicationState",appname,state);
		}
		catch(Exception ex) {
			log.infof("%s.setApplicationState: Exception (%s)",TAG,ex.getMessage());
		}
	}
	/** Update all changed properties for a block 
	 * @param duuid diagram unique Id
	 * @param buuid block unique Id
	 */
	public void setBlockProperties(UUID duuid,UUID buuid, Collection<BlockProperty> props ) {
		String diagId  = duuid.toString();
		String blockId = buuid.toString();
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		String json="";
		try {
			json = mapper.writeValueAsString(props);
		}
		catch(Exception ge) {
			log.warnf("%s: toJson (%s)",TAG,ge.getMessage());
		}
		log.tracef("%s: json properties = %s",TAG,json);
		log.debugf("%s.setBlockProperties: %s %s %s %s: %s", TAG, diagId,blockId, json);
		try {
			GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
				BLTProperties.MODULE_ID, "setBlockProperties", diagId,blockId, json);
		}
		catch(Exception ge) {
			log.infof("%s.setBlockProperties: GatewayException (%s)",TAG,ge.getMessage());
		}		
	}


	/** Update a single changed property for a block 
	 * @param duuid diagram unique Id
	 * @param buuid block unique Id
	 * @param property the changed property
	 */
	public void setBlockProperty(UUID duuid,UUID buuid,BlockProperty property ) {
		String diagId  = duuid.toString();
		String blockId = buuid.toString();
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		String json="";
		try {
			json = mapper.writeValueAsString(property);
		}
		catch(Exception ge) {
			log.warnf("%s: toJson (%s)",TAG,ge.getMessage());
		}
		log.tracef("%s: json property = %s",TAG,json);
		log.debugf("%s.setBlockProperty: %s %s %s", TAG, diagId,blockId, json);
		try {
			GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
				BLTProperties.MODULE_ID, "setBlockProperty", diagId,blockId, json);
		}
		catch(Exception ge) {
			log.infof("%s.setBlockProperty: GatewayException (%s)",TAG,ge.getMessage());
		}		
	}
	
	
	public void setDiagramState(Long projectId, Long resourceId, String state) {
		log.debugf("%s.setDiagramState ... %d:%d %s",TAG,projectId.longValue(),resourceId.longValue(),state);
		try {
			GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.MODULE_ID, "setDiagramState",projectId,resourceId,state);

		}
		catch(Exception ge) {
			log.infof("%s.setDiagramState: GatewayException (%s)",TAG,ge.getMessage());
		}
	}
	
	@Override
	public void setDiagramState(String diagramId, String state) {
		log.debugf("%s.setDiagramState ... %s %s",TAG,diagramId,state);
		try {
			GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.MODULE_ID, "setDiagramState",diagramId,state);

		}
		catch(Exception ge) {
			log.infof("%s.setDiagramState: GatewayException (%s)",TAG,ge.getMessage());
		}
	}

	/**
	 * Set a clock rate factor for isolation mode only. We set in the SFC module
	 * as well. If that module is not present, then we simply ignore the exception.
	 * @param factor the amount to speed up or slow down the clock.
	 */
	public void setTimeFactor(double factor) {
		log.infof("%s.setTimeFactor ... %s",TAG,String.valueOf(factor));
		try {
			GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.MODULE_ID, "setTimeFactor",new Double(factor));
		}
		catch(Exception ge) {
			log.infof("%s.setTimeFactor: GatewayException (%s:%s)",TAG,ge.getClass().getName(),ge.getMessage());
		}
		try {
			GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.SFC_MODULE_ID, "setTimeFactor",new Double(factor));
		}
		catch(Exception ignore) {}
	}
	
	/**
	 * Save a value into the HSQL database table associated with the toolkit. The 
	 * table contains name-value pairs, so any name is allowable. We also execute
	 * this method on behalf of the SFC-module in case there are any side-effects
	 * of saving particular parameters.
	 * @param propertyName name of the property for which a value is to be set
	 * @param the new value of the property.
	 */
	public void setToolkitProperty(String propertyName,String value) {
		log.tracef("%s.setToolkitProperty ... %s=%s",TAG,propertyName,value);
		try {
			GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.MODULE_ID, "setToolkitProperty",propertyName,value);
		}
		catch(Exception ge) {
			log.infof("%s.setToolkitProperty: GatewayException (%s:%s)",TAG,ge.getClass().getName(),ge.getMessage());
		}
		try {
			GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.SFC_MODULE_ID, "setToolkitProperty",propertyName,value);
		}
		catch(Exception ignore) {}
	}
	
	/**
	 * Start the block execution engine in the gateway.
	 */
	public void startController() {
		log.debugf("%s.startController ...",TAG);

		try {
			GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.MODULE_ID, "startController");
		}
		catch(Exception ge) {
			log.infof("%s.startController: GatewayException (%s)",TAG,ge.getMessage());
		}
	}
	
	/**
	 * Shutdown the block execution engine in the gateway.
	 */
	public void stopController() {
		log.debugf("%s.stopController ...",TAG);

		try {
			GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.MODULE_ID, "stopController");
		}
		catch(Exception ge) {
			log.infof("%s.stopController: GatewayException (%s)",TAG,ge.getMessage());
		}
	}

	/**
	 * Direct the blocks in a specified diagram to report their
	 * status values. This is in order to update the UI. 
	 */
	public void triggerStatusNotifications() {
		//log.infof("%s.triggerStatusNotifications...",TAG);
		try {
			GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
					BLTProperties.MODULE_ID, "triggerStatusNotifications");
		}
		catch(Exception ex) {
			log.infof("%s.triggerStatusNotifications: Exception (%s:%s)",TAG,ex.getClass().getName(),ex.getMessage());
		}
	}

	/** Update connections for a block. New connections will be added, old connections
	 * may undergo a type conversion.  
	 * @param duuid diagram unique Id
	 * @param buuid block unique Id
	 */
	public void updateBlockAnchors(UUID duuid,UUID buuid, Collection<SerializableAnchor> anchors ) {
		String diagId  = duuid.toString();
		String blockId = buuid.toString();
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		String json="";
		try {
			json = mapper.writeValueAsString(anchors);
		}
		catch(Exception ge) {
			log.warnf("%s: toJson (%s)",TAG,ge.getMessage());
		}
		log.tracef("%s: json properties = %s",TAG,json);
		log.debugf("%s.setBlockProperties: %s %s %s %s: %s", TAG, diagId,blockId, json);
		try {
			GatewayConnectionManager.getInstance().getGatewayInterface().moduleInvoke(
				BLTProperties.MODULE_ID, "updateBlockAnchors", diagId,blockId, json);
		}
		catch(Exception ge) {
			log.infof("%s.setBlockProperties: GatewayException (%s)",TAG,ge.getMessage());
		}		
	}
}
