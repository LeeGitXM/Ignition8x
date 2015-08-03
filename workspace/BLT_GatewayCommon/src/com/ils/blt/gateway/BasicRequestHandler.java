/**
 *   (c) 2014-2015  ILS Automation. All rights reserved.
 *  
 */
package com.ils.blt.gateway;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ils.blt.common.BLTProperties;
import com.ils.blt.common.DiagramState;
import com.ils.blt.common.ToolkitRequestHandler;
import com.ils.blt.common.annotation.ExecutableBlock;
import com.ils.blt.common.block.AnchorPrototype;
import com.ils.blt.common.block.BindingType;
import com.ils.blt.common.block.BlockConstants;
import com.ils.blt.common.block.BlockProperty;
import com.ils.blt.common.block.CoreBlock;
import com.ils.blt.common.block.PalettePrototype;
import com.ils.blt.common.block.TransmissionScope;
import com.ils.blt.common.notification.BlockPropertyChangeEvent;
import com.ils.blt.common.notification.BroadcastNotification;
import com.ils.blt.common.notification.OutgoingNotification;
import com.ils.blt.common.notification.Signal;
import com.ils.blt.common.script.ScriptExtensionManager;
import com.ils.blt.common.serializable.SerializableAnchor;
import com.ils.blt.common.serializable.SerializableBlockStateDescriptor;
import com.ils.blt.common.serializable.SerializableResourceDescriptor;
import com.ils.blt.gateway.engine.BlockExecutionController;
import com.ils.blt.gateway.engine.ProcessApplication;
import com.ils.blt.gateway.engine.ProcessNode;
import com.ils.blt.gateway.persistence.ToolkitRecord;
import com.ils.common.ClassList;
import com.ils.common.watchdog.AcceleratedWatchdogTimer;
import com.inductiveautomation.ignition.common.datasource.DatasourceStatus;
import com.inductiveautomation.ignition.common.model.values.BasicQualifiedValue;
import com.inductiveautomation.ignition.common.model.values.BasicQuality;
import com.inductiveautomation.ignition.common.model.values.QualifiedValue;
import com.inductiveautomation.ignition.common.model.values.Quality;
import com.inductiveautomation.ignition.common.util.LogUtil;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.datasource.Datasource;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

/**
 *  This handler provides is a common class for handling requests for block properties and control
 *  of the execution engine. The requests can be expected arrive both through the scripting interface
 *  and the RPC diispatcher.In general, the calls are made to update properties 
 *  in the block objects and to trigger their evaluation. Any methods that do not generally apply
 *  are stubbed.
  
 *  This class is a singleton for easy access throughout the application.
 */
public class BasicRequestHandler implements ToolkitRequestHandler  {
	private final static String TAG = "BasicRequestHandler";
	protected final LoggerEx log;
	protected final GatewayContext context;
	protected final String moduleId;
	protected final BlockExecutionController controller = BlockExecutionController.getInstance();
	protected final PythonRequestHandler pyHandler;
	protected final ScriptExtensionManager sem = ScriptExtensionManager.getInstance();
    
	/**
	 * Initialize with context and appropriate python handler.
	 * Note: The PythonRequestHandler class is initialized in thehook.
	 */
	public BasicRequestHandler(GatewayContext ctx,String module) {
		this.context = ctx;
		this.moduleId = module;
		this.log = LogUtil.getLogger(getClass().getPackage().getName());
		this.pyHandler = new PythonRequestHandler();
	}
	

	@Override
	public List<SerializableResourceDescriptor> childNodes(String nodeId) {
		UUID uuid = makeUUID(nodeId);
		ProcessNode node = controller.getProcessNode(uuid);
		List<SerializableResourceDescriptor> result = new ArrayList<>();
		if( node!=null ) {
			Collection<ProcessNode> children =  node.getChildren();
			for( ProcessNode child:children ) {
				result.add(child.toResourceDescriptor());
			}
		}
		return result;
	}
	
	/**
	 * Remove all diagrams from the controller.
	 * Cancel all tag subscriptions.
	 */
	@Override
	public void clearController() {
		controller.removeAllDiagrams();
	}

	
	@Override
	public boolean diagramExists(String uuidString) {
		UUID diagramUUID = null;
		try {
			diagramUUID = UUID.fromString(uuidString);
		}
		catch(IllegalArgumentException iae) {
			log.warnf("%s.diagramExists: Diagram UUID string is illegal (%s), creating new",TAG,uuidString);
			diagramUUID = UUID.nameUUIDFromBytes(uuidString.getBytes());
		}
		BasicDiagram diagram = controller.getDiagram(diagramUUID);
		return diagram!=null;
	}
	@Override
	public void evaluateBlock(String diagramId, String blockId) {
		UUID diagramUUID = null;
		UUID blockUUID = null;
		try {
			diagramUUID = UUID.fromString(diagramId);
			blockUUID = UUID.fromString(blockId);
		}
		catch(IllegalArgumentException iae) {
			log.warnf("%s.evaluateBlock: Diagram or block UUID string is illegal (%s, %s), creating new",TAG,diagramId,blockId);
			diagramUUID = UUID.nameUUIDFromBytes(diagramId.getBytes());
			blockUUID = UUID.nameUUIDFromBytes(blockId.getBytes());
		}
		controller.evaluateBlock(diagramUUID, blockUUID);
	}
	
	@Override
	public String getApplicationName(String uuid) {
		return BLTProperties.NOT_FOUND;
	}
	
	/**
	 * Query the block controller for a block specified by the block id. If the block
	 * does not exist, create it.
	 * 
	 * @param className
	 * @param projectId
	 * @param resourceId
	 * @param blockId
	 * @return the properties of an existing or new block.
	 */
	@Override
	public List<BlockProperty> getBlockProperties(String className,long projectId,long resourceId, UUID blockId) {
		// If the instance doesn't exist, create one
		log.debugf("%s.getBlockProperties of %s (%s)",TAG,className,blockId.toString());
		List<BlockProperty> results = new ArrayList<>();
		BasicDiagram diagram = controller.getDiagram(projectId, resourceId);
		CoreBlock block = null;
		if( diagram!=null ) block = diagram.getBlock(blockId);
		BlockProperty[] props = null;
		if(block!=null) {
			props = block.getProperties();  // Existing block
			log.tracef("%s.getProperties existing %s = %s",TAG,block.getClass().getName(),props.toString());
		}
		
		for(BlockProperty prop:props) {
			results.add(prop);
		}
		return results;
	}

	/**
	 * Query the execution controller for a specified block property. 
	 * 
	 * @param parentId UUID of the containing ProcessDiagram
	 * @param blockId UUID of the block
	 * @param propertyName name of the property
	 * @return the properties of an existing or new block.
	 */
	public BlockProperty getBlockProperty(UUID parentId,UUID blockId,String propertyName) {
		BasicDiagram diagram = controller.getDiagram(parentId);
		CoreBlock block = null;
		if( diagram!=null ) block = diagram.getBlock(blockId);
		BlockProperty property = null;
		if(block!=null) {
			property = block.getProperty(propertyName);  // Existing block
		}
		else {
			log.warnf("%s.getProperty Block not found for %s.%s",TAG,parentId.toString(),blockId.toString());
		}
		return property;
	}
	@Override
	public List<PalettePrototype> getBlockPrototypes() {
		List<PalettePrototype> results = new ArrayList<>();
		ClassList cl = new ClassList();
		List<Class<?>> classes = cl.getAnnotatedClasses(BLTProperties.BLOCK_JAR_NAME, ExecutableBlock.class,"com/ils/block/");
		for( Class<?> cls:classes) {
			log.debugf("   found block class: %s",cls.getName());
			try {
				Object obj = cls.newInstance();
				if( obj instanceof CoreBlock ) {
					PalettePrototype bp = ((CoreBlock)obj).getBlockPrototype();
					results.add(bp);
				}
				else {
					log.warnf("%s: Class %s not a ProcessBlock",TAG,cls.getName());
				}
			} 
			catch (InstantiationException ie) {
				log.warnf("%s.getBlockPrototypes: Exception instantiating block (%s)",TAG,ie.getLocalizedMessage());
			} 
			catch (IllegalAccessException iae) {
				log.warnf("%s.getBlockPrototypes: Access exception (%s)",TAG,iae.getMessage());
			}
			catch (Exception ex) {
				log.warnf("%s.getBlockPrototypes: Runtime exception (%s)",TAG,ex.getMessage(),ex);
			}
		}
		log.infof("%s.getBlockPrototypes: returning %d palette prototypes",TAG,results.size());
		return results;
	}
	
	@Override
	public String getBlockState(String diagramId, String blockName) {
		String state = "UNSET";
		return state;
	}
	
	@Override
	public String getControllerState() {
		return getExecutionState();
	}
	
	/**
	 * Find the parent application or diagram of the entity referenced by
	 * the supplied id. Test the state and return the name of the appropriate
	 * database.  
	 * @param uuid
	 * @return database name
	 */
	public String getDatabaseForUUID(String nodeId) {
		// Search up the tree for a parent diagram or application. Determine the
		// state. Unless we find a diagram, we don't return any connection name.
		String db = "NONE";
		DiagramState ds = DiagramState.DISABLED;
		try {
			UUID uuid = UUID.fromString(nodeId);
			
			ProcessNode node = controller.getProcessNode(uuid);
			while( node!=null ) {
				if( node instanceof BasicDiagram ) {
					ds = ((BasicDiagram)node).getState();
					//log.infof("%s.getApplication, found application = %s ",TAG,app.getName());
					break;
				}
				else if( node instanceof ProcessApplication ) {
					ds = ((ProcessApplication)node).getState();
					//log.infof("%s.getApplication, found application = %s ",TAG,app.getName());
					break;
				}
				node = controller.getProcessNode(node.getParent());
			}
			if(ds.equals(DiagramState.ACTIVE)) {
				db = getToolkitProperty(BLTProperties.TOOLKIT_PROPERTY_DATABASE);
			}
			else if(ds.equals(DiagramState.ISOLATED)) {
				db = getToolkitProperty(BLTProperties.TOOLKIT_PROPERTY_ISOLATION_DATABASE);
			}
		}
		catch(IllegalArgumentException iae) {
			log.warnf("%s.getApplication: %s is an illegal UUID (%s)",TAG,nodeId,iae.getMessage());
		}
		return db;
	}
	
	@Override
	public List<String> getDatasourceNames() {
		List<Datasource> sources = context.getDatasourceManager().getDatasources();
		List<String> result = new ArrayList<>();
		for( Datasource source:sources) {
			if(source.getStatus().equals(DatasourceStatus.VALID)) {
				result.add(source.getName());
			}
		}
		return result;
	}
	
	/**
	 * When called from the gateway, we have no project. Get them all.
	 */
	public List<SerializableResourceDescriptor> getDiagramDescriptors() {
		List<SerializableResourceDescriptor> descriptors = controller.getDiagramDescriptors();
		return descriptors;
	}
	@Override
	public SerializableResourceDescriptor getDiagramForBlock(String blockId) {
		SerializableResourceDescriptor result = null;
		UUID uuid = makeUUID(blockId);
		ProcessNode block = controller.getProcessNode(uuid);
		if( block!=null ) {
			ProcessNode diagram = controller.getProcessNode(block.getParent());
			if( diagram!=null ) result = diagram.toResourceDescriptor();
		}
		return result;
	}
	
	/**
	 * @param projectId
	 * @param resourceId
	 * @return the current state of the specified diagram as a DiagramState.
	 */
	public DiagramState getDiagramState(Long projectId,Long resourceId) {
		DiagramState state = DiagramState.ACTIVE;
		BasicDiagram diagram = controller.getDiagram(projectId, resourceId);
		if( diagram!=null ) {
			state = diagram.getState();
		}
		return state;
	}
	/**
	 * @param resourceId
	 * @return the current state of the specified diagram as a DiagramState.
	 */
	public DiagramState getDiagramState(String diagramId) {
		DiagramState state = DiagramState.ACTIVE;
		UUID diagramuuid=makeUUID(diagramId);
		BasicDiagram diagram = controller.getDiagram(diagramuuid);
		if( diagram!=null ) {
			state = diagram.getState();
		}
		return state;
	}
	
	public String getExecutionState() {
		return BlockExecutionController.getExecutionState();
	}
	@Override
	public String getFamilyName(String uuid) {
		return BLTProperties.NOT_FOUND;
	}
	
	/**
	 * Query a block for its internal state. This allows a read-only display in the
	 * designer to be useful for block debugging.
	 * 
	 * @param diagramId
	 * @param blockId
	 * @return a SerializableBlockStateDescriptor
	 */
	public SerializableBlockStateDescriptor getInternalState(String diagramId,String blockId) {
		SerializableBlockStateDescriptor descriptor = null;
		BasicDiagram diagram = controller.getDiagram(UUID.fromString(diagramId));
		if(diagram!=null) {
			CoreBlock block = controller.getBlock(diagram, UUID.fromString(blockId));
			if( block!=null ) descriptor = block.getInternalStatus();
		}
		return descriptor;
	}
	@Override
	public Object getPropertyValue(String diagramId, String blockId,String propertyName) {
		BlockProperty property = null;
		UUID diagramUUID;
		UUID blockUUID;
		try {
			diagramUUID = UUID.fromString(diagramId);
			blockUUID = UUID.fromString(blockId);
			property = getBlockProperty(diagramUUID,blockUUID,propertyName);
		}
		catch(IllegalArgumentException iae) {
			log.warnf("%s.getPropertyValue: Diagram or block UUID string is illegal (%s,%s),",TAG,diagramId,blockId);
		}
		return property.getValue();
	}
	public Object getPropertyValue(UUID diagramId,UUID blockId,String propertyName) {
		Object val = null;
		BasicDiagram diagram = controller.getDiagram(diagramId);
		if(diagram!=null) {
			CoreBlock block = controller.getBlock(diagram, blockId);
			if( block!=null ) {
				BlockProperty prop = block.getProperty(propertyName);
				if( prop!=null) val = prop.getValue();
			}
		}
		return val;

	}
	
	/**
	 * On a failure to find the property, an empty string is returned.
	 */
	@Override
	public String getToolkitProperty(String propertyName) {
		String value = "";
		try {
			ToolkitRecord record = context.getPersistenceInterface().find(ToolkitRecord.META, propertyName);
			if( record!=null) value =  record.getValue();
		}
		catch(Exception ex) {
			log.warnf("%s.getToolkitProperty: Exception retrieving %s (%s),",TAG,propertyName,ex.getMessage());
		}
		return value;
	}
	@Override
	public boolean isControllerRunning() {
		return getExecutionState().equalsIgnoreCase("running");
	}
	
	@Override
	public List<SerializableBlockStateDescriptor> listBlocksDownstreamOf(String diagramId,String blockName) {
		List<SerializableBlockStateDescriptor> descriptors = new ArrayList<>();
		UUID diauuid = makeUUID(diagramId);
		BasicDiagram diagram = controller.getDiagram(diauuid);
		if( diagram!=null ) {
			CoreBlock blk = diagram.getBlockByName(blockName);
			if(blk!=null) descriptors = controller.listBlocksDownstreamOf(diauuid,blk.getBlockId());
		}
		return descriptors;
	}
	
	@Override
	public List<SerializableBlockStateDescriptor> listBlocksForTag(String tagpath) {
		List<SerializableBlockStateDescriptor> results = new ArrayList<>();
		List<SerializableResourceDescriptor> descriptors = controller.getDiagramDescriptors();
		for(SerializableResourceDescriptor descriptor:descriptors) {
			UUID diagId = makeUUID(descriptor.getId());

			BasicDiagram diagram = controller.getDiagram(diagId);
			if( diagram!=null) {
				Collection<CoreBlock> blocks = diagram.getDiagramBlocks();
				for(CoreBlock block:blocks) {
					if( block.usesTag(tagpath)) {
						results.add(block.toDescriptor());
					}
				}
			}
			else {
				log.warnf("%s.queryDiagramForBlocks: no diagram found for descriptor %s",TAG,descriptor.getId());
			}
		}
		return results;
	}
	
	/**
	 * Query the ModelManager for a list of the project resources that it is currently
	 * managing. This is a debugging service.
	 * @return
	 */
	@Override
	public List<SerializableBlockStateDescriptor> listBlocksInDiagram(String diagramId) {
		List<SerializableBlockStateDescriptor> descriptors = new ArrayList<>();
		UUID diauuid = makeUUID(diagramId);
		BasicDiagram diagram = controller.getDiagram(diauuid);
		if( diagram!=null) {
			Collection<CoreBlock> blocks = diagram.getDiagramBlocks();
			for(CoreBlock block:blocks) {
				SerializableBlockStateDescriptor desc = block.toDescriptor();
				Map<String,String> attributes = desc.getAttributes();
				attributes.put(BLTProperties.BLOCK_ATTRIBUTE_ID,block.getClass().getName());
				attributes.put(BLTProperties.BLOCK_ATTRIBUTE_ID,block.getBlockId().toString());
				descriptors.add(desc);
			}
		}
		else {
			log.warnf("%s.queryDiagramForBlocks: no diagram found for %s",TAG,diagramId);
		}
		return descriptors;
	}
	
	@Override
	public List<SerializableBlockStateDescriptor> listBlocksUpstreamOf(String diagramId, String blockName) {
		List<SerializableBlockStateDescriptor> descriptors = new ArrayList<>();
		UUID diauuid = makeUUID(diagramId);
		BasicDiagram diagram = controller.getDiagram(diauuid);
		if( diagram!=null ) {
			CoreBlock blk = diagram.getBlockByName(blockName);
			if(blk!=null) descriptors = controller.listBlocksUpstreamOf(diauuid,blk.getBlockId());
		}
		return descriptors;
	}

	@Override
	public List<SerializableBlockStateDescriptor> listConfigurationErrors() {
		List<SerializableBlockStateDescriptor> result = new ArrayList<>();
		List<SerializableResourceDescriptor> descriptors = controller.getDiagramDescriptors();
		for(SerializableResourceDescriptor res:descriptors) {
			UUID diagramId = makeUUID(res.getId());
			BasicDiagram diagram = controller.getDiagram(diagramId);
			for( CoreBlock block:diagram.getDiagramBlocks() ) {
				String problem = block.validate();
				if( problem!=null) {
					SerializableBlockStateDescriptor descriptor = block.toDescriptor();
					descriptor.getAttributes().put(BLTProperties.BLOCK_ATTRIBUTE_PATH, pathForBlock(diagramId.toString(),block.getName()));
					descriptor.getAttributes().put(BLTProperties.BLOCK_ATTRIBUTE_ISSUE, problem);
					result.add(descriptor);
				}
			}
		}
		return result;
	}
	@Override
	public List<SerializableBlockStateDescriptor> listDiagramBlocksOfClass(String diagramId, String className) {
		UUID diagramuuid = makeUUID(diagramId);
		BasicDiagram diagram = controller.getDiagram(diagramuuid);
		List<SerializableBlockStateDescriptor> result = new ArrayList<>();
		if( diagram!=null ) {
			for(CoreBlock block:diagram.getDiagramBlocks()) {
				if( block.getClassName().equalsIgnoreCase(className)) {
					SerializableBlockStateDescriptor rd = block.toDescriptor();
					result.add(rd);
				}
			}
		}
		return result;
	}
	@Override
	public List<SerializableResourceDescriptor> listDiagramDescriptors(String projectName) {
		List<SerializableResourceDescriptor> descriptors = controller.getDiagramDescriptors(projectName);
		return descriptors;
	}
	/**
	 * Query the ModelManager for a list of the project resources that it is currently
	 * managing. This is a debugging service.
	 * @return
	 */
	public List<SerializableResourceDescriptor> listResourceNodes() {
		return controller.queryControllerResources();
	}
	
	/**
	 * Do an exhaustive search for all sink blocks that have the same binding
	 * as the specified block. We cover all diagrams in the system.
	 * @param blockId
	 * @return
	 */
	@Override
	public List<SerializableBlockStateDescriptor> listSinksForSource(String diagramId,String blockName) {
		List<SerializableBlockStateDescriptor> results = new ArrayList<>();
		UUID diagramuuid = makeUUID(diagramId);
		BasicDiagram diagram = controller.getDiagram(diagramuuid);
		CoreBlock source = null;
		if(diagram!=null) {
			source = diagram.getBlockByName(blockName);
		}
		String tagPath = null;
		if( source!=null ) {
			BlockProperty prop = source.getProperty(BlockConstants.BLOCK_PROPERTY_TAG_PATH);
			if( prop!=null ) tagPath = prop.getBinding();
		}
		
		if( tagPath!=null && tagPath.length()>0 ) {
			List<SerializableResourceDescriptor> descriptors = controller.getDiagramDescriptors();
			for(SerializableResourceDescriptor desc:descriptors) {
				UUID diaguuid = makeUUID(desc.getId());
				diagram = controller.getDiagram(diaguuid);
				for(CoreBlock sink:diagram.getDiagramBlocks()) {
					if( sink.getClassName().equalsIgnoreCase("com.ils.block.SinkConnection") ) {
						BlockProperty prop = sink.getProperty(BlockConstants.BLOCK_PROPERTY_TAG_PATH);
						if( prop!=null && tagPath.equals(prop.getBinding())  ) {
							results.add(sink.toDescriptor());
						}
					}
				}
			}
		}
		else {
			log.warnf("%s.listSinksForSource: Block %s not found or not bound",TAG,blockName);
		}
		
		return results;
	}
	/**
	 * Do an exhaustive search for all source blocks that have the same binding
	 * as the specified block. We cover all diagrams in the system.
	 * @param blockId
	 * @return
	 */
	@Override
	public List<SerializableBlockStateDescriptor> listSourcesForSink(String diagramId,String blockName) {
		List<SerializableBlockStateDescriptor> results = new ArrayList<>();
		UUID diagramuuid = makeUUID(diagramId);
		BasicDiagram diagram = controller.getDiagram(diagramuuid);
		CoreBlock sink = null;
		if(diagram!=null) {
			sink = diagram.getBlockByName(blockName);
		}
		String tagPath = null;
		if( sink!=null ) {
			BlockProperty prop = sink.getProperty(BlockConstants.BLOCK_PROPERTY_TAG_PATH);
			if( prop!=null ) tagPath = prop.getBinding();
		}
		
		if( tagPath!=null && tagPath.length()>0 ) {
			List<SerializableResourceDescriptor> descriptors = controller.getDiagramDescriptors();
			for(SerializableResourceDescriptor descriptor:descriptors) {
				UUID diaguuid = makeUUID(descriptor.getId());
				diagram = controller.getDiagram(diaguuid);
				for(CoreBlock source:diagram.getDiagramBlocks()) {
					if( source.getClassName().equalsIgnoreCase("com.ils.block.SourceConnection") ) {
						BlockProperty prop = source.getProperty(BlockConstants.BLOCK_PROPERTY_TAG_PATH);
						if( prop!=null && tagPath.equals(prop.getBinding())  ) {
							results.add(source.toDescriptor());
						}
					}
				}
			}
		}
		else {
			log.warnf("%s.listSourcesForSink: Block %s not found or not bound",TAG,blockName);
		}
		return results;
	}
	
	@Override
	public String pathForBlock(String diagramId,String blockName) {
		UUID uuid = makeUUID(diagramId);
		String path = controller.pathForNode(uuid);
		return String.format("%s:%s",path,blockName);
	}
	/**
	 * Handle the block placing a new value on its output. This minimalist version
	 * is likely called from an external source through an RPC.
	 * 
	 * @param parentId identifier for the parent
	 * @param blockId identifier for the block
	 * @param port the output port on which to insert the result
	 * @param value the result of the block's computation
	 * @param quality of the reported output
	 */
	@Override
	public void postValue(String parentId,String blockId,String port,String value)  {
		log.infof("%s.postValue - %s = %s on %s",TAG,blockId,value,port);
		try {
			UUID diagramuuid = UUID.fromString(parentId);
			UUID blockuuid   = UUID.fromString(blockId);
			postValue(diagramuuid,blockuuid,port,value,BLTProperties.QUALITY_GOOD) ;
		}
		catch(IllegalArgumentException iae) {
			log.warnf("%s.postValue: one of %s or %s illegal UUID (%s)",TAG,parentId,blockId,iae.getMessage());
		}
	}
	/**
	 * Handle the block placing a new value on its output. This version is called from
	 * a Python implementation of a block.
	 * 
	 * @param parentuuid identifier for the parent
	 * @param blockId identifier for the block
	 * @param port the output port on which to insert the result
	 * @param value the result of the block's computation
	 * @param quality of the reported output
	 */
	public void postValue(UUID parentuuid,UUID blockId,String port,String value,String quality)  {
		log.infof("%s.postValue - %s = %s (%s) on %s",TAG,blockId,value,quality,port);
		try {
			BasicDiagram diagram = controller.getDiagram(parentuuid);
			if( diagram!=null) {
				CoreBlock block = diagram.getBlock(blockId);
				QualifiedValue qv = new BasicQualifiedValue(value,new BasicQuality(quality,
						(quality.equalsIgnoreCase(BLTProperties.QUALITY_GOOD)?Quality.Level.Good:Quality.Level.Bad)));
				OutgoingNotification note = new OutgoingNotification(block,port,qv);
				controller.acceptCompletionNotification(note);
			}
			else {
				log.warnf("%s.postValue: no diagram found for %s",TAG,parentuuid);
			}
		}
		catch(IllegalArgumentException iae) {
			log.warnf("%s.postValue: one of %s or %s illegal UUID (%s)",TAG,parentuuid,blockId,iae.getMessage());
		}
	}
	@Override
	public void resetBlock(String diagramId, String blockName) {
		UUID diagramUUID = makeUUID(diagramId);
		BasicDiagram diagram = controller.getDiagram(diagramUUID);
		if( diagram!=null) {
			CoreBlock block = diagram.getBlockByName(blockName);
			if( block!=null ) controller.resetBlock(diagramUUID, block.getBlockId());
		}
		else {
			log.warnf("%s.resetBlock: no diagram found for %s",TAG,diagramId);
		}
		
	}
	@Override
	public void resetDiagram(String diagramId) {
		UUID diagramUUID = null;
		try {
			diagramUUID = UUID.fromString(diagramId);
		}
		catch(IllegalArgumentException iae) {
			log.warnf("%s.resetDiagram: Diagram UUID string is illegal (%s), creating new",TAG,diagramId);
			diagramUUID = UUID.nameUUIDFromBytes(diagramId.getBytes());
		}
		BlockExecutionController.getInstance().resetDiagram(diagramUUID);
		
	}
			
	@Override
	public boolean resourceExists(long projectId, long resid) {
		BasicDiagram diagram = controller.getDiagram(projectId, resid);
		log.infof("%s.resourceExists diagram %d:%d ...%s",TAG,projectId,resid,(diagram!=null?"true":"false"));
		return diagram!=null;
	}


	@Override
	public boolean sendLocalSignal(String diagramId, String command, String message, String argument) {
		Boolean success = new Boolean(true);
		UUID diagramUUID = null;
		try {
			diagramUUID = UUID.fromString(diagramId);
		}
		catch(IllegalArgumentException iae) {
			log.warnf("%s.sendLocalSignal: Diagram UUID string is illegal (%s), creating new",TAG,diagramId);
			diagramUUID = UUID.nameUUIDFromBytes(diagramId.getBytes());
		}
		BasicDiagram diagram = BlockExecutionController.getInstance().getDiagram(diagramUUID);
		if( diagram!=null ) {
			// Create a broadcast notification
			Signal sig = new Signal(command,message,argument);
			BroadcastNotification broadcast = new BroadcastNotification(diagram.getSelf(),TransmissionScope.LOCAL,sig);
			BlockExecutionController.getInstance().acceptBroadcastNotification(broadcast);
		}
		else {
			log.warnf("%s.sendLocalSignal: Unable to find diagram %s for %s command",TAG,diagramId,command);
			success = new Boolean(false);
		}
		return success;
	}


	/**
	 * Set the state of every diagram in an application to the specified value.
	 * This default is a no-op.
	 * @param appname
	 * @param state
	 */
	@Override
	public void setApplicationState(String appname, String state) {
	}


	/**
	 * Set the values of named properties in a block. This method ignores any binding that the
	 * property may have and sets the value directly. Theoretically the value should be of the right
	 * type for the property, but if not, it can be expected to be coerced into the proper data type 
 	 * upon receipt by the block. The quality is assumed to be Good.
	 * 
	 * @param parentId
	 * @param blockId
	 * @param properties a collection of properties that may have changed
	 */
	public void setBlockProperties(UUID parentId, UUID blockId, Collection<BlockProperty> properties) {
		BasicDiagram diagram = controller.getDiagram(parentId);
		CoreBlock block = null;
		if( diagram!=null ) block = diagram.getBlock(blockId);
		if(block!=null) {
			for( BlockProperty property:properties ) {
				BlockProperty existingProperty = block.getProperty(property.getName());
				if( existingProperty!=null ) {
					// Update the property
					updateProperty(block,existingProperty,property);
				}
				else {
					// Need to add a new one.
					BlockProperty[] props = new BlockProperty[block.getProperties().length+1];
					int index = 0;
					for(BlockProperty bp:block.getProperties()) {
						props[index] = bp;
						index++;
					}
					props[index] = property;
				}
			}
		}
	}


	/**
	 * Set the value of a named property in a block. This method ignores any binding that the
	 * property may have and sets the value directly. Theoretically the value should be of the right
	 * type for the property, but if not, it can be expected to be coerced into the proper data type 
 	 * upon receipt by the block. The quality is assumed to be Good.
	 * 
	 * @param parentId
	 * @param blockId
	 * @param property the newly changed or added block property
	 */
	public void setBlockProperty(UUID parentId, UUID blockId, BlockProperty property) {

		BasicDiagram diagram = controller.getDiagram(parentId);
		CoreBlock block = null;
		if( diagram!=null ) block = diagram.getBlock(blockId);
		if(block!=null) {
			BlockProperty existingProperty = block.getProperty(property.getName());
			if( existingProperty!=null ) {
				// Update the property
				updateProperty(block,existingProperty,property);
			}
			else {
				// Need to add a new one.
				BlockProperty[] props = new BlockProperty[block.getProperties().length+1];
				int index = 0;
				for(BlockProperty bp:block.getProperties()) {
					props[index] = bp;
					index++;
				}
				props[index] = property;
			}
		}
	}


	/**
	 * Set the state of the specified diagram. 
	 * @param projectId
	 * @param resourceId
	 * @param state
	 */
	public void setDiagramState(Long projectId,Long resourceId,String state) {
		BasicDiagram diagram = controller.getDiagram(projectId, resourceId);
		if( diagram!=null && state!=null ) {
			try {
				DiagramState ds = DiagramState.valueOf(state.toUpperCase());
				diagram.setState(ds);
			}
			catch( IllegalArgumentException iae) {
				log.warnf("%s.setDiagramState: Unrecognized state(%s) sent to %s (%s)",TAG,state,diagram.getName());
			}
		}
	}


	/**
	 * Set the state of the specified diagram. 
	 * @param diagramId UUID of the diagram
	 * @param state
	 */
	public void setDiagramState(String diagramId,String state) {
		UUID diagramUUID = UUID.fromString(diagramId);
		BasicDiagram diagram = controller.getDiagram(diagramUUID);
		if( diagram!=null && state!=null ) {
			try {
				DiagramState ds = DiagramState.valueOf(state.toUpperCase());
				diagram.setState(ds);
			}
			catch( IllegalArgumentException iae) {
				log.warnf("%s.setDiagramState: Unrecognized state(%s) sent to %s (%s)",TAG,state,diagram.getName());
			}
		}
	}


	public void setTimeFactor(Double factor) {
		log.infof("%s.setTimeFactor: %s", TAG, String.valueOf(factor));
		AcceleratedWatchdogTimer timer = controller.getSecondaryTimer();
		timer.setFactor(factor.doubleValue());
	}


	/**
	 * We have two types of properties of interest here. The first set is found in ScriptConstants
	 * and represents scripts used for external Python interfaces to Application/Family.
	 * The second category represents database and tag interfaces for production and isolation
	 * modes.
	 */
	@Override
	public void setToolkitProperty(String propertyName, String value) {
		try {
			ToolkitRecord record = context.getPersistenceInterface().find(ToolkitRecord.META, propertyName);
			if( record==null) record = context.getPersistenceInterface().createNew(ToolkitRecord.META);
			if( record!=null) {
				record.setName(propertyName);
				record.setValue(value);
				context.getPersistenceInterface().save(record);
			}
			else {
				log.warnf("%s.setToolkitProperty: %s=%s - failed to create persistence record (%s)",TAG,propertyName,value,ToolkitRecord.META.quoteName);
			}
			sem.setModulePath(propertyName, value);  // Does nothing for properties not part of Python external interface 
			controller.clearCache();                 // Force retrieval production/isolation constants from HSQLdb on next access.
		}
		catch(Exception ex) {
			log.warnf("%s.setToolkitProperty: Exception setting %s=%s (%s),",TAG,propertyName,value,ex.getMessage());
		}
	}

	public void startController() {
		BlockExecutionController.getInstance().start(context);
	}


	public void stopController() {
		BlockExecutionController.getInstance().stop();
	}


	/**
	 * Direct blocks in all diagrams to report their status for a UI update.
	 */
	public void triggerStatusNotifications() {
		BlockExecutionController.getInstance().triggerStatusNotifications();
		log.infof("%s.triggerStatusNotifications: Complete.",TAG);
	}


	/** Change the properties of anchors for a block. 
	 * @param diagramId the uniqueId of the parent diagram
	 * @param blockId the uniqueId of the block
	 * @param anchorUpdates the complete anchor list for the block.
	 */
	public void updateBlockAnchors(String diagramId,String blockId, Collection<SerializableAnchor> anchorUpdates) {
		UUID diagramUUID = UUID.fromString(diagramId);
		BasicDiagram diagram = controller.getDiagram(diagramUUID);
		CoreBlock block = null;
		UUID blockUUID = UUID.fromString(blockId);
		if( diagram!=null ) block = diagram.getBlock(blockUUID);
		if(block!=null) {
			List<AnchorPrototype> anchors = block.getAnchors();
			for( SerializableAnchor anchorUpdate:anchorUpdates ) {
				// These are undoubtedly very short lists ... do linear searches
				boolean found = false;
				for( AnchorPrototype anchor:anchors ) {
					if( anchor.getName().equalsIgnoreCase(anchorUpdate.getDisplay())) {
						anchor.setConnectionType(anchorUpdate.getConnectionType());
						found = true;
						break;
					}
				}
				if( !found ) {
					// Add previously unknown anchor
					AnchorPrototype proto = new AnchorPrototype(anchorUpdate.getDisplay(),anchorUpdate.getDirection(),anchorUpdate.getConnectionType());
					proto.setAnnotation(anchorUpdate.getAnnotation());
					proto.setHint(anchorUpdate.getHint());
					anchors.add(proto);
				}
			}
		}
	}


	/** Change the properties of anchors for a block. 
	 * @param diagramId the uniqueId of the parent diagram
	 * @param blockId the uniqueId of the block
	 * @param anchorUpdates the complete anchor list for the block.
	 */
	@Override
	public void updateBlockAnchors(UUID diagramUUID, UUID blockUUID,Collection<SerializableAnchor> anchorUpdates) {

		BasicDiagram diagram = controller.getDiagram(diagramUUID);
		CoreBlock block = null;
		if( diagram!=null ) block = diagram.getBlock(blockUUID);
		if(block!=null) {
			List<AnchorPrototype> anchors = block.getAnchors();
			for( SerializableAnchor anchorUpdate:anchorUpdates ) {
				// These are undoubtedly very short lists ... do linear searches
				boolean found = false;
				for( AnchorPrototype anchor:anchors ) {
					if( anchor.getName().equalsIgnoreCase(anchorUpdate.getDisplay())) {
						anchor.setConnectionType(anchorUpdate.getConnectionType());
						found = true;
						break;
					}
				}
				if( !found ) {
					// Add previously unknown anchor
					AnchorPrototype proto = new AnchorPrototype(anchorUpdate.getDisplay(),anchorUpdate.getDirection(),anchorUpdate.getConnectionType());
					proto.setAnnotation(anchorUpdate.getAnnotation());
					proto.setHint(anchorUpdate.getHint());
					anchors.add(proto);
				}
			}
		}
	}


	protected UUID makeUUID(String name) {
		UUID uuid = null;
		try {
			uuid = UUID.fromString(name);
		}
		catch(IllegalArgumentException iae) {
			uuid = UUID.nameUUIDFromBytes(name.getBytes());
		}
		return uuid;
	}


	// Handle the intricasies of a property change with respect to bound tags.
	// This version does not update the tags
	protected void updateProperty(CoreBlock block,BlockProperty existingProperty,BlockProperty newProperty) {
		if( !existingProperty.isEditable() )  return;
		
		log.debugf("%s.updateProperty old: %s, new:%s",TAG,existingProperty.toString(),newProperty.toString());
		if( !existingProperty.getBindingType().equals(newProperty.getBindingType()) ) {
			// If the binding has changed - fix subscriptions.
			controller.removeSubscription(block, existingProperty);
			existingProperty.setBindingType(newProperty.getBindingType());
			existingProperty.setBinding(newProperty.getBinding());
			controller.startSubscription(block,newProperty);
		}
		else if( !existingProperty.getBinding().equals(newProperty.getBinding()) ) {
			// Same type, new binding target.
			controller.removeSubscription(block, existingProperty);
			existingProperty.setBinding(newProperty.getBinding());
			controller.startSubscription(block,newProperty);
		}
		else {
			// The event came explicitly from the designer/client. Send event whether it changed or not.
			if( existingProperty.getBindingType().equals(BindingType.NONE) && newProperty.getValue()!=null   )   {
				log.debugf("%s.setProperty sending event ...",TAG);
				BlockPropertyChangeEvent event = new BlockPropertyChangeEvent(block.getBlockId().toString(),newProperty.getName(),
						existingProperty.getValue(),newProperty.getValue());
				block.propertyChange(event);
			}
		}
	}

	/**
	 * This only applies to the secondary timer.
	 */
	@Override
	public void setTimeFactor(double factor) {
		AcceleratedWatchdogTimer timer = controller.getSecondaryTimer();
		timer.setFactor(factor);
	}


	@Override
	public String getModuleId() {
		return moduleId;
	}

}
