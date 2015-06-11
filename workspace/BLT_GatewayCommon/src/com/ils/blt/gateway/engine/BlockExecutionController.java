/**
 *   (c) 2014-2105  ILS Automation. All rights reserved.
 *  
 *   The block controller is designed to be called from the client
 *   via RPC. All methods must be thread safe,
 */
package com.ils.blt.gateway.engine;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.ils.blt.common.BLTProperties;
import com.ils.blt.common.DiagramState;
import com.ils.blt.common.ToolkitRequestHandler;
import com.ils.blt.common.block.BindingType;
import com.ils.blt.common.block.BlockProperty;
import com.ils.blt.common.block.CoreBlock;
import com.ils.blt.common.connection.Connection;
import com.ils.blt.common.control.ExecutionController;
import com.ils.blt.common.notification.BroadcastNotification;
import com.ils.blt.common.notification.ConnectionPostNotification;
import com.ils.blt.common.notification.IncomingNotification;
import com.ils.blt.common.notification.NotificationKey;
import com.ils.blt.common.notification.OutgoingNotification;
import com.ils.blt.common.notification.SignalNotification;
import com.ils.blt.common.serializable.SerializableBlockStateDescriptor;
import com.ils.blt.common.serializable.SerializableResourceDescriptor;
import com.ils.blt.gateway.common.BasicDiagram;
import com.ils.blt.gateway.tag.TagListener;
import com.ils.blt.gateway.tag.TagReader;
import com.ils.blt.gateway.tag.TagWriter;
import com.ils.common.BoundedBuffer;
import com.ils.common.watchdog.AcceleratedWatchdogTimer;
import com.ils.common.watchdog.WatchdogTimer;
import com.inductiveautomation.ignition.common.model.ApplicationScope;
import com.inductiveautomation.ignition.common.model.values.BasicQualifiedValue;
import com.inductiveautomation.ignition.common.model.values.QualifiedValue;
import com.inductiveautomation.ignition.common.util.LogUtil;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.clientcomm.GatewaySessionManager;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

/**
 *  The block execution controller is responsible for the dynamic activity for the collection
 *  of diagrams. It receives status updates from the RPC controller and from the resource manager
 *  which is its delegate regarding model changes. The changes are analyzed to
 *  determine if one or more downstream blocks are to be informed of the change.
 *  
 *  This class is a singleton for easy access throughout the application.
 */
public class BlockExecutionController implements ExecutionController, Runnable {
	private final static String TAG = "BlockExecutionController";
	private static BlockExecutionController instance = null;
	public final static String CONTROLLER_RUNNING_STATE = "running";
	public final static String CONTROLLER_STOPPED_STATE = "stopped";
	private static int BUFFER_SIZE = 100;       // Buffer Capacity
	private static int THREAD_POOL_SIZE = 10;   // Notification threads
	private final LoggerEx log;
	private ToolkitRequestHandler requestHandler = null;
	private GatewaySessionManager sessionManager = null;
	private ModelManager modelManager = null;
	private final WatchdogTimer watchdogTimer;
	private final AcceleratedWatchdogTimer secondaryWatchdogTimer;  // For isolated
	private final ExecutorService threadPool;

	// Cache the values for tag provider and database
	private String productionDatabase = null;
	private String isolationDatabase  = null;
	private String productionProvider = null;
	private String isolationProvider  = null;
	private double isolationTimeFactor= Double.NaN;

	private final BoundedBuffer buffer;
	private final TagReader  tagReader;
	private final TagListener tagListener;    // Tag subscriber
	private final TagWriter tagWriter;
	private Thread notificationThread = null;
	// Make this static so we can test without creating an instance.
	private static boolean stopped = true;
	
	/**
	 * Initialize with instances of the classes to be controlled.
	 */
	private BlockExecutionController() {
		log = LogUtil.getLogger(getClass().getPackage().getName());
		this.threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
		this.tagReader  = new TagReader();
		this.tagListener = new TagListener(this);
		this.tagWriter = new TagWriter();
		this.buffer = new BoundedBuffer(BUFFER_SIZE);
		// Timers get started and stopped with the controller
		this.watchdogTimer = new WatchdogTimer("MainTimer");
		this.secondaryWatchdogTimer = new AcceleratedWatchdogTimer("SecondaryTimer");
	}

	/**
	 * Static method to create and/or fetch the single instance.
	 */
	public static BlockExecutionController getInstance() {
		if( instance==null) {
			synchronized(BlockExecutionController.class) {
				instance = new BlockExecutionController();
			}
		}
		return instance;
	}
	/**
	 * The request handler must be specified before the controller is useful.
	 */
	public void setRequestHandler(ToolkitRequestHandler h) {
		this.requestHandler = h;
	}
	/**
	 * Someone has injected a message into the system via broadcast
	 */
	@Override
	public void acceptBroadcastNotification(BroadcastNotification note) {
		log.tracef("%s.acceptBroadcastNotification: %s (%s) %s", TAG,note.getDiagramId(),note.getSignal().getCommand(),
				(stopped?"REJECTED, controller stopped":""));
		BasicDiagram diagram = getDiagram(note.getDiagramId());
		if( diagram!=null && !diagram.getState().equals(DiagramState.DISABLED)) {
			try {
				if(!stopped) buffer.put(note);
			}
			catch( InterruptedException ie ) {}
		}
	}
	
	/**
	 * A block has completed evaluation. A new value has been placed on its output.
	 * Place the notification into the queue for delivery to the appropriate downstream blocks.
	 * If we're stopped or the diagram is not active, these all go into the bit bucket.
	 */
	@Override
	public void acceptCompletionNotification(OutgoingNotification note) {
		log.tracef("%s:acceptCompletionNotification: %s:%s = %s %s", TAG,note.getBlock().getBlockId().toString(),note.getPort(),
				note.getValue().toString(),
				(stopped?"REJECTED, controller stopped":""));
		BasicDiagram diagram = getDiagram(note.getBlock().getParentId());
		if( diagram!=null && !diagram.getState().equals(DiagramState.DISABLED)) {
			try {
				if(!stopped) buffer.put(note);
			}
			catch( InterruptedException ie ) {}
		}
	}
	
	/**
	 * @param note the notification to be distributed to all connection posts
	 *        interested in the sender. Sender must be ACTIVE.
	 */
	@Override
	public void acceptConnectionPostNotification(ConnectionPostNotification note) {
		log.tracef("%s:acceptConnectionPostNotification: %s %s", TAG,note.getOriginName(),
				(stopped?"REJECTED, controller stopped":""));
		BasicDiagram diagram = getDiagram(note.getDiagramId());
		if( diagram!=null && diagram.getState().equals(DiagramState.ACTIVE)) {
			try {
				if(!stopped) buffer.put(note);
			}
			catch( InterruptedException ie ) {}
		}
	}

	/**
	 * Change a tag subscription for a block's property. We assume that the property
	 * has been updated and contains the new path.
	 */
	@Override
	public void alterSubscription(UUID diagramId,UUID blockId,String propertyName) {
		BasicDiagram diagram = modelManager.getDiagram(diagramId);
		if( diagram!=null) {
			CoreBlock block = diagram.getBlock(blockId);
			if( block!=null ) {
				BlockProperty bp = block.getProperty(propertyName);
				if( bp!=null ) {
					tagListener.removeSubscription(block,bp);
					startSubscription(block,bp);
				}
			}
		}
	}
	/**
	 * Clear cached values to guarantee that next access forces a read from persistent storage.
	 */
	@Override
	public void clearCache() {
		productionDatabase = null;
		isolationDatabase  = null;
		productionProvider = null;
		isolationProvider  = null;
		isolationTimeFactor= Double.NaN;
	}
	@Override
	public String getIsolationDatabase() {
		if(isolationDatabase==null) {
			isolationDatabase = requestHandler.getToolkitProperty(BLTProperties.TOOLKIT_PROPERTY_ISOLATION_DATABASE);
		}
		return isolationDatabase;
	}
	@Override
	public String getProductionDatabase() {
		if(productionDatabase==null) {
			productionDatabase = requestHandler.getToolkitProperty(BLTProperties.TOOLKIT_PROPERTY_DATABASE);
		}
		return productionDatabase;
	}
	@Override
	public String getIsolationProvider() {
		if(isolationProvider==null) {
			isolationProvider = requestHandler.getToolkitProperty(BLTProperties.TOOLKIT_PROPERTY_ISOLATION_PROVIDER);
		}
		return isolationProvider;
	}
	@Override
	public String getProductionProvider() {
		if(productionProvider==null) {
			productionProvider = requestHandler.getToolkitProperty(BLTProperties.TOOLKIT_PROPERTY_PROVIDER);
		}
		return productionProvider;
	}
	/**
	 * NOTE: This value is a "speed-up" factor. .
	 * @return
	 */
	@Override
	public double getIsolationTimeFactor() {
		if(Double.isNaN(isolationTimeFactor) ) {
			String factor = requestHandler.getToolkitProperty(BLTProperties.TOOLKIT_PROPERTY_ISOLATION_TIME);
			try {
				isolationTimeFactor = Double.parseDouble(factor);
			}
			catch(NumberFormatException nfe) {
				log.errorf("%s.getIsolationTimeFactor: Could not parse (%s) to a double value (%s)",TAG,factor,nfe.getMessage());
				isolationTimeFactor = 1.0;
			}
		}
		return isolationTimeFactor;
	}
	
	/**
	 * Obtain the running state of the controller. This is a static method
	 * so that we don't have to instantiate an instance if there is none currently.
	 * @return the run state of the controller. ("running" or "stopped")
	 */
	public static String getExecutionState() {
		if( stopped ) return CONTROLLER_STOPPED_STATE;
		else          return CONTROLLER_RUNNING_STATE;
	}
	
	public WatchdogTimer getTimer() {return watchdogTimer;}
	public AcceleratedWatchdogTimer getSecondaryTimer() {return secondaryWatchdogTimer;}

	/**
	 * Start the controller, watchdogTimer, tagListener and TagWriter.
	 * @param ctxt the gateway context
	 */
	public synchronized void start(GatewayContext context) {
		log.infof("%s: STARTED",TAG);
		if(!stopped) return;  
		stopped = false;
		tagReader.initialize(context);
		tagListener.start(context);
		tagWriter.initialize(context);
		this.notificationThread = new Thread(this, "BlockExecutionController");
		log.debugf("%s START - notification thread %d ",TAG,notificationThread.hashCode());
		notificationThread.setDaemon(true);
		notificationThread.start();
		watchdogTimer.start();
		// NOTE: The watchdog uses the reciprocal of this ...
		secondaryWatchdogTimer.setFactor(getIsolationTimeFactor());
		secondaryWatchdogTimer.start();
		sessionManager = context.getGatewaySessionManager();
		// Activate all of the blocks in the diagram.
		modelManager.startBlocks();
	}
	
	/**
	 * Stop the controller, watchdogTimer, tagListener and TagWriter. Set all
	 * instance values to null to, hopefully, allow garbage collection.
	 */
	public synchronized void stop() {
		log.infof("%s: STOPPING ...",TAG);
		if(stopped) return;
		stopped = true;
		if(notificationThread!=null) {
			notificationThread.interrupt();
		}
		tagListener.stop();
		secondaryWatchdogTimer.stop();
		watchdogTimer.stop();
		// Shutdown all of the blocks in the diagram.
		modelManager.stopBlocks();
		log.infof("%s: STOPPED",TAG);
	}
	public  void setDelegate(ModelManager resmgr) { this.modelManager = resmgr; }
	
	public void triggerStatusNotifications() {
		for( BasicDiagram diagram:modelManager.getDiagrams()) {
			for(CoreBlock block:diagram.getDiagramBlocks()) {
				block.notifyOfStatus();
			}
		}
	}
	
	// ======================= Delegated to ModelManager ======================
	/**
	 * Add a temporary diagram that is not associated with a project resource. This
	 * diagram will not be persisted. Subscriptions are not activated at this point.
	 * @param diagram the diagram to be added to the engine.
	 */
	public void addTemporaryDiagram(BasicDiagram diagram) {
		modelManager.addTemporaryDiagram(diagram);
	}
	/**
	 * Execute a block's evaluate method.
	 * @param diagramId the block or diagram identifier.
	 * @param blockId the block or diagram identifier.
	 */
	public void evaluateBlock(UUID diagramId,UUID blockId) {
		BasicDiagram diagram = modelManager.getDiagram(diagramId);
		if( diagram!=null) {
			CoreBlock block = modelManager.getBlock(diagram, blockId);
			if( block!=null) block.evaluate();
		}
	}
	public CoreBlock getBlock(BasicDiagram diagram,UUID blockId) {
		return modelManager.getBlock(diagram,blockId);
	}
	public CoreBlock getBlock(long projectId,long resourceId,UUID blockId) {
		return modelManager.getBlock(projectId,resourceId,blockId);
	}
	public Connection getConnection(long projectId,long resourceId,String connectionId) {
		return modelManager.getConnection(projectId,resourceId,connectionId);
	}
	public ModelManager getDelegate() {
		return modelManager;
	}
	public BasicDiagram getDiagram(long projectId,long resourceId) {
		return modelManager.getDiagram(projectId,resourceId);
	}
	public BasicDiagram getDiagram(UUID id) {
		return modelManager.getDiagram(id);
	}
	public ProcessNode getProcessNode(UUID id) {
		return modelManager.getProcessNode(id);
	}
	public List<SerializableResourceDescriptor> getDiagramDescriptors() {
		return modelManager.getDiagramDescriptors();
	}
	public List<SerializableResourceDescriptor> getDiagramDescriptors(String projectName) {
		return modelManager.getDiagramDescriptors(projectName);
	}
	public List<SerializableBlockStateDescriptor> listBlocksDownstreamOf(UUID diagramId,UUID blockId) {
		return modelManager.listBlocksDownstreamOf(diagramId, blockId);
	}
	public List<SerializableBlockStateDescriptor> listBlocksUpstreamOf(UUID diagramId,UUID blockId) {
		return modelManager.listBlocksUpstreamOf(diagramId, blockId);
	}
	/**
	 * The node must be an element of the nav-tree, that is an application,
	 * family, folder or diagram. 
	 * @param nodeId
	 * @return colon-separated path to the indicated node
	 */
	public String pathForNode(UUID nodeId) {
		return modelManager.pathForNode(nodeId);
	}
	/**
	 * Reset a block.
	 * @param diagramId the block or diagram identifier.
	 * @param blockId the block or diagram identifier.
	 */
	public void resetBlock(UUID diagramId,UUID blockId) {
		BasicDiagram diagram = modelManager.getDiagram(diagramId);
		if( diagram!=null) {
			CoreBlock block = modelManager.getBlock(diagram, blockId);
			if( block!=null) block.reset();
		}
	}
	/**
	 * Reset all blocks on a diagram. Resetting blocks with
	 * truth-value outputs, propagates an UNKNOWN. This is 
	 * done by the blocks. After all of this, cause the input
	 * blocks to evaluate.
	 * @param diagramId the diagram identifier.
	 */
	public void resetDiagram(UUID diagramId) {
		BasicDiagram diagram = modelManager.getDiagram(diagramId);
		diagram.reset();
	}

	public List<SerializableResourceDescriptor> queryControllerResources() {
		return modelManager.queryControllerResources();
	}
	/**
	 * Remove all diagrams and applications from the controller. 
	 * Before doing so, stop all subscriptions.
	 */
	public void removeAllDiagrams() {
		clearSubscriptions();
		modelManager.removeAllDiagrams();
	}
	/**
	 * Delete a temporary diagram that is not associated with a project resource. 
	 * Any subscriptions are de-activated before removal.
	 * @param Id the UUID of the diagram to be deleted from the engine.
	 */
	public void removeTemporaryDiagram(UUID Id) {
		modelManager.removeTemporaryDiagram(Id);
	}
	
	// ======================= Delegated to TagListener ======================
	/**
	 * Tell the tag listener to forget about any past subscriptions. By default,
	 * the listener will re-establish all previous subscriptions when restarted.
	 */
	@Override
	public void clearSubscriptions() {
		tagListener.clearSubscriptions();
	}
	
	@Override
	public QualifiedValue getTagValue(UUID diagramId,String path) {
		BasicDiagram diagram = getDiagram(diagramId);
		if( diagram!=null && diagram.getState().equals(DiagramState.ISOLATED) ) {
			path = replaceProviderInPath(path, getIsolationProvider());
		}
		return tagReader.readTag(path);
	}
	/**
	 * Stop the tag subscription associated with a particular property of a block.
	 * There may be other entities still subscribed to the same tag.
	 */
	public void removeSubscription(CoreBlock block,BlockProperty property) {
		if( property!=null && property.getBinding()!=null && 
			(	property.getBindingType()==BindingType.TAG_READ || 
				property.getBindingType()==BindingType.TAG_READ ||
				property.getBindingType()==BindingType.TAG_MONITOR )  ) {
			String tagPath = property.getBinding().toString();
			BasicDiagram diagram = getDiagram(block.getParentId());
			if( diagram!=null && diagram.getState().equals(DiagramState.ISOLATED) ) {
				tagPath = replaceProviderInPath(tagPath, getIsolationProvider());
			}
			if( tagPath!=null && tagPath.length()>0) {
				tagListener.removeSubscription(block,property,tagPath);
			}
		}
	}
	/**
	 * Start a subscription for a block attribute associated with a tag.
	 */
	public void startSubscription(CoreBlock block,BlockProperty property) {
		if( block==null || property==null || 
				!(property.getBindingType().equals(BindingType.TAG_READ) || 
				  property.getBindingType().equals(BindingType.TAG_READWRITE) ||
				  property.getBindingType().equals(BindingType.TAG_MONITOR) )   ) return;
		
		String tagPath = property.getBinding();
		BasicDiagram diagram = getDiagram(block.getParentId());
		if( diagram!=null && diagram.getState().equals(DiagramState.ISOLATED) ) {
			tagPath = replaceProviderInPath(tagPath, getIsolationProvider());
		}
		tagListener.defineSubscription(block,property,tagPath);
	}
	
	/**
	 * Restart a subscription for a block attribute associated with a tag.
	 * This is probably due to a diagram state change. If so, the diagram is 
	 * temporarily disabled in order to suppress tag change updates.
	 */
	public void restartSubscription(BasicDiagram diagram,CoreBlock block,BlockProperty property,DiagramState state) {
		if( block==null || property==null || 
				!(property.getBindingType().equals(BindingType.TAG_READ) || 
				  property.getBindingType().equals(BindingType.TAG_READWRITE) ||
				  property.getBindingType().equals(BindingType.TAG_MONITOR) )   ) return;
		
		String tagPath = property.getBinding();
		if( state.equals(DiagramState.ISOLATED) ) {
			tagPath = replaceProviderInPath(tagPath, getIsolationProvider());
		}
		tagListener.defineSubscription(block,property,tagPath);
	}

	
	// ======================= Delegated to TagWriter ======================
	/**
	 * Write a value to a tag. If the diagram referenced diagram is disabled
	 * then this method has no effect.
	 * @param diagramId UUID of the parent diagram
	 * @param tagPath
	 * @param val
	 */
	public void updateTag(UUID diagramId,String tagPath,QualifiedValue val) {
		BasicDiagram diagram = modelManager.getDiagram(diagramId);
		if( diagram!=null && !diagram.getState().equals(DiagramState.DISABLED)) {
			if(diagram.getState().equals(DiagramState.ISOLATED)) {
				tagPath = replaceProviderInPath(tagPath, getIsolationProvider());
			}
			tagWriter.updateTag(diagram.getProjectId(),tagPath,val);
		}
		else {
			log.infof("%s.updateTag %s REJECTED, diagram not active",TAG,tagPath);
		}
	}
	
	/**
	 * Write a value to a tag. If the diagram referenced diagram is disabled
	 * then this method has no effect.
	 * @param diagramId UUID of the parent diagram
	 * @param tagPath
	 */
	public boolean validateTag(UUID diagramId,String tagPath) {
		boolean result = false;
		BasicDiagram diagram = modelManager.getDiagram(diagramId);
		if( diagram!=null ) {
			if(diagram.getState().equals(DiagramState.ISOLATED)) {
				tagPath = replaceProviderInPath(tagPath, getIsolationProvider());
			}
			result = tagWriter.validateTag(diagram.getProjectId(),tagPath);
		}
		else {
			log.infof("%s.validateTag %s, parent diagram not found",TAG,tagPath);
		}
		return result;
	}


	// ============================ Completion Handler =========================
	/**
	 * Wait for work to arrive at the output of a bounded buffer. The contents of the bounded buffer
	 * are OutgoingValueNotification objects. In/out are from the viewpoint of a block.
	 */
	public void run() {
		while( !stopped  ) {
			try {
				Object work = buffer.get();
				if( work instanceof OutgoingNotification) {
					OutgoingNotification inNote = (OutgoingNotification)work;
					// Query the diagram to find out what's next
					CoreBlock pb = inNote.getBlock();
					log.tracef("%s.run: processing incoming note from %s:%s = %s", TAG,pb.toString(),inNote.getPort(),inNote.getValue().toString());
					// Send the push notification
					sendConnectionNotification(pb.getBlockId().toString(),inNote.getPort(),inNote.getValue());
					BasicDiagram dm = modelManager.getDiagram(pb.getParentId());
					if( dm!=null) {
						Collection<IncomingNotification> outgoing = dm.getOutgoingNotifications(inNote);
						// It is common for display blocks, for example, to be left unconnected.
						// Don't get too worried about this.
						if( outgoing.isEmpty() ) log.debugf("%s: no downstream connections found ...",TAG);
						for(IncomingNotification outNote:outgoing) {
							UUID outBlockId = outNote.getConnection().getTarget();
							CoreBlock outBlock = dm.getBlock(outBlockId);
							if( outBlock!=null ) {
								log.tracef("%s.run: sending outgoing notification: to %s:%s = %s", TAG,outBlock.toString(),
										  outNote.getConnection().getDownstreamPortName(),outNote.getValue().toString());
								threadPool.execute(new IncomingValueChangeTask(outBlock,outNote));
							}
							else {
								log.warnf("%s: run: target block %s not found in diagram map ",TAG,outBlockId.toString());
							}
						}
					}
					else {
						log.warnf("%s: run: diagram %s not found for value change notification",TAG,pb.getParentId().toString());
					}
				}
				else if( work instanceof BroadcastNotification) {
					BroadcastNotification inNote = (BroadcastNotification)work;
					
					// Query the diagram to find out what's next. The diagramId is the resourceId
					BasicDiagram dm = modelManager.getDiagram(inNote.getDiagramId());
					if( dm!=null) {
						log.debugf("%s.run: processing broadcast to diagram %s (%s)", TAG,dm.getName(),inNote.getSignal().getCommand());
						Collection<SignalNotification> outgoing = dm.getBroadcastNotifications(inNote);
						if( outgoing.isEmpty() ) log.warnf("%s: no broadcast recipients found ...",TAG);
						for(SignalNotification outNote:outgoing) {
							CoreBlock outBlock = outNote.getBlock();
							log.debugf("%s.run: sending signal to %s", TAG,outBlock.toString());
							threadPool.execute(new IncomingBroadcastTask(outBlock,outNote));
							
						}
					}
					// Note: This can legitimately happen if the diagram is deleted.
					else {
						log.warnf("%s.run: diagram %s not found in value change notification",TAG,
								inNote.getDiagramId().toString());
					}
				}
				else {
					log.warnf("%s.run: Unexpected object in buffer (%s)",TAG,work.getClass().getName());
				}
			}
			catch( InterruptedException ie) {}
		}
	}

	/**
	 * Notify any notification listeners of changes to a block property. This is usually triggered by the 
	 * block itself. The ultimate receiver is typically a block property in the UI in a ProcessBlockView.
	 */
	@Override
	public void sendPropertyNotification(String blkid, String propertyName,QualifiedValue val) {
		String key = NotificationKey.keyForProperty(blkid,propertyName);
		log.tracef("%s.sendPropertyNotification: %s (%s)",TAG,key,val.toString());
		try {
			sessionManager.sendNotification(ApplicationScope.DESIGNER, BLTProperties.MODULE_ID, key, val);
		}
		catch(Exception ex) {
			// Probably no receiver registered. This is to be expected if the designer is not running.
			log.debugf("%s.sendPropertyNotification: Error transmitting %s (%s)",TAG,key,ex.getMessage());
		}
	}
	/**
	 * Notify any listeners in the Client or Designer scopes of the a change in the value carried by a connection.
	 * A connection is uniquely identified by a block and output port. The sender of this notification is the
	 * controller (this). The typical receiver is a BasicAnchorPoint embedded in a connection in the UI.
	 * @param blockid unique Id of the block
	 * @param port
	 * @param val
	 */
	@Override
	public void sendConnectionNotification(String blockid, String port, QualifiedValue val) {
		String key = NotificationKey.keyForConnection(blockid,port);
		try {
			sessionManager.sendNotification(ApplicationScope.DESIGNER, BLTProperties.MODULE_ID, key, val);
		}
		catch(Exception ex) {
			// Probably no receiver registered. This is to be expected if the designer is not running.
			log.debugf("%s.sendConnectionNotification: Error transmitting %s (%s)",TAG,key,ex.getMessage());
		}
	}
	/**
	 * Notify any listeners in the Client or Designer scopes that a diagram has changed state,
	 * presumably triggered from an external source.
	 * @param diagramid unique Id of the diagram
	 * @param val new state
	 */
	@Override
	public void sendStateNotification(String diagramid, String val) {
		String key = NotificationKey.keyForDiagram(diagramid);
		try {
			sessionManager.sendNotification(ApplicationScope.DESIGNER, BLTProperties.MODULE_ID, key, new BasicQualifiedValue(val));
		}
		catch(Exception ex) {
			// Probably no receiver registered. This is to be expected if the designer is not running.
			log.debugf("%s.sendDiagramNotification: Error transmitting %s (%s)",TAG,key,ex.getMessage());
		}
	}
	
	private String replaceProviderInPath(String path,String providerName) {
		int pos = path.indexOf("]");
		path = path.substring(pos+1);
		return String.format("[%s]%s", providerName,path);
	}

}