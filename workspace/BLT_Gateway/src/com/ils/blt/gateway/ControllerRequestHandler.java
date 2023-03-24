/**
 *   (c) 2014-2022 ILS Automation. All rights reserved.
 *  
 */
package com.ils.blt.gateway;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ils.block.annotation.ExecutableBlock;
import com.ils.blt.common.BLTProperties;
import com.ils.blt.common.DiagramState;
import com.ils.blt.common.ProcessBlock;
import com.ils.blt.common.ToolkitRequestHandler;
import com.ils.blt.common.UtilityFunctions;
import com.ils.blt.common.block.AnchorPrototype;
import com.ils.blt.common.block.BindingType;
import com.ils.blt.common.block.BlockConstants;
import com.ils.blt.common.block.BlockProperty;
import com.ils.blt.common.block.PalettePrototype;
import com.ils.blt.common.block.TransmissionScope;
import com.ils.blt.common.block.TruthValue;
import com.ils.blt.common.control.ExecutionController;
import com.ils.blt.common.notification.BlockPropertyChangeEvent;
import com.ils.blt.common.notification.BroadcastNotification;
import com.ils.blt.common.notification.OutgoingNotification;
import com.ils.blt.common.notification.Signal;
import com.ils.blt.common.serializable.SerializableAnchor;
import com.ils.blt.common.serializable.SerializableBlockStateDescriptor;
import com.ils.blt.common.serializable.SerializableResourceDescriptor;
import com.ils.blt.gateway.engine.BlockExecutionController;
import com.ils.blt.gateway.engine.ProcessDiagram;
import com.ils.blt.gateway.engine.ProcessNode;
import com.ils.blt.gateway.proxy.ProxyHandler;
import com.ils.common.GeneralPurposeDataContainer;
import com.ils.common.help.HelpRecordProxy;
import com.ils.common.persistence.ToolkitProjectRecordHandler;
import com.ils.common.persistence.ToolkitProperties;
import com.ils.common.persistence.ToolkitRecordHandler;
import com.ils.common.tag.TagFactory;
import com.ils.common.watchdog.AcceleratedWatchdogTimer;
import com.inductiveautomation.ignition.common.datasource.DatasourceStatus;
import com.inductiveautomation.ignition.common.model.values.BasicQualifiedValue;
import com.inductiveautomation.ignition.common.model.values.QualifiedValue;
import com.inductiveautomation.ignition.common.model.values.QualityCode;
import com.inductiveautomation.ignition.common.project.Project;
import com.inductiveautomation.ignition.common.project.RuntimeProject;
import com.inductiveautomation.ignition.common.project.resource.ProjectResource;
import com.inductiveautomation.ignition.common.project.resource.ProjectResourceBuilder;
import com.inductiveautomation.ignition.common.project.resource.ProjectResourceId;
import com.inductiveautomation.ignition.common.project.resource.ResourceType;
import com.inductiveautomation.ignition.common.sqltags.model.types.DataType;
import com.inductiveautomation.ignition.common.util.LogUtil;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.designer.project.DesignableProject;
import com.inductiveautomation.ignition.gateway.datasource.Datasource;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

import simpleorm.dataset.SQuery;

/**
 *  This handler provides is a common class for handling requests for block properties and control
 *  of the execution engine. The requests can be expected arrive both through the scripting interface
 *  and the RPC dispatcher.In general, the calls are made to update properties 
 *  in the block objects and to trigger their evaluation.
 *  
 *  
 *  This class is a singleton for easy access throughout the application.
 */
public class ControllerRequestHandler implements ToolkitRequestHandler  {
	private final static String CLSS = "ControllerRequestHandler";
	private final static boolean DEBUG = false;
	private final LoggerEx log;
	private GatewayContext context = null;
	private static ControllerRequestHandler instance = null;
	private final BlockExecutionController controller = BlockExecutionController.getInstance();
	private final PythonRequestHandler pyHandler;
	private ToolkitProjectRecordHandler toolkitProjectRecordHandler;
	private ToolkitRecordHandler toolkitRecordHandler;
	private final UtilityFunctions fcns;
	private TagFactory tagHandler; 
    
	/**
	 * Initialize with instances of the classes to be controlled.
	 */
	private ControllerRequestHandler() {
		log = LogUtil.getLogger(getClass().getPackage().getName());
		pyHandler = new PythonRequestHandler();
		fcns = new UtilityFunctions();
	}
	/**
	 * Static method to create and/or fetch the single instance.
	 * @return the static instance of the handler
	 */
	public static ControllerRequestHandler getInstance() {
		if( instance==null) {
			synchronized(ControllerRequestHandler.class) {
				instance = new ControllerRequestHandler();
			}
		}
		return instance;
	}
	@Override
	public List<SerializableResourceDescriptor> childNodes(ProjectResourceId id) {
		String projectName = id.getProjectName();
		String resourcePath = id.getResourcePath().getFolderPath();
		return childNodes(projectName,resourcePath);
	}
	public List<SerializableResourceDescriptor> childNodes(String projectName,String resource) {
		ProcessNode node = controller.getProcessNode(projectName,resource);
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
	
	/**
	 * Clear any watermark on a diagram. 
	 * @param diagramId UUID of the diagram as a string
	 */
	@Override
	public void clearWatermark(ProjectResourceId diagramId) {
		controller.sendWatermarkNotification(diagramId,"");
	}
	
	/**
	 * Create an instance of a named class. If the class is not found in the JVM, try Python. 
	 * @param className class to create
	 * @param parentId diagram identifier
	 * @param blockId identifier to assign to the newly created block
	 * @return the instance created, else null
	 */
	public ProcessBlock createInstance(String className,ProjectResourceId parentId,UUID blockId) {
		log.debugf("%s.createInstance of %s (%s:%s)",CLSS,className,(parentId==null?"null":parentId.toString()),blockId.toString());
		ProcessBlock block = null;
		try {
			Class<?> clss = Class.forName(className);
			Constructor<?> ctor = clss.getDeclaredConstructor(new Class[] {ExecutionController.class,ProjectResourceId.class,UUID.class});
			block = (ProcessBlock)ctor.newInstance(BlockExecutionController.getInstance(),parentId,blockId);
		}
		catch(InvocationTargetException ite ) {
			Throwable cause = ite.getCause();
			log.warn(String.format("%s.createInstance %s: Invocation of constructor failed (%s)",CLSS,
					className,(cause==null?"No cause available":cause.getLocalizedMessage())),cause); 
		}
		catch(NoSuchMethodException nsme ) {
			log.warnf("%s.createInstance %s: Three argument constructor not found (%s)",CLSS,className,nsme.getMessage()); 
		}
		catch( ClassNotFoundException cnf ) {
			log.debugf("%s.createInstance: Java class %s not found - trying Python",CLSS,className);
			ProxyHandler ph = ProxyHandler.getInstance();
			block = ph.createBlockInstance(className,parentId,blockId,"");
		}
		catch( InstantiationException ie ) {
			log.warnf("%s.createInstance: Error instantiating %s (%s)",CLSS,className,ie.getLocalizedMessage()); 
		}
		catch( IllegalAccessException iae ) {
			log.warnf("%s.createInstance: Security exception creating %s (%s)",CLSS,className,iae.getLocalizedMessage()); 
		}
		return block;
	}
	/**
	 * Create a ProjectResourceId object from String components. This is designed for Python
	 * scripts to easily generate resourceId inputs to the various methods.
	 */
	@Override
	public ProjectResourceId createResourceId(String projectName, String path) {
		ResourceType rtype = BLTProperties.DIAGRAM_RESOURCE_TYPE;
		ProjectResourceId resourceId = new ProjectResourceId(projectName,rtype,path);
		return resourceId;
	}
	
	/**
	 * Create the tag in both production and isolation
	 */
	@Override
	public void createTag(String projectName,DataType type,String path) {
		String provider = getProjectProductionTagProvider(projectName);
		tagHandler.createTag(provider,path,type.name());
		provider = getProjectIsolationTagProvider(projectName);
		tagHandler.createTag(provider,path,type.name());
	}
	/**
	 * Delete the tag for both production and isolation
	 */
	@Override
	public void deleteTag(String projectName,String path) {
		String provider = getProjectProductionTagProvider(projectName);
		tagHandler.deleteTag(provider,path);
		provider = getProjectIsolationTagProvider(projectName);
		tagHandler.deleteTag(provider,path);
	}
	@Override
	public boolean diagramExists(ProjectResourceId diagramId) {
		ProcessDiagram diagram = controller.getDiagram(diagramId);
		return diagram!=null;
	}

	@Override
	public String getBlockId(ProjectResourceId diagramId, String blockName) {
		String id = "UNKNOWN";
		try {
			ProcessDiagram diagram = controller.getDiagram(diagramId);
			for(ProcessBlock block:diagram.getProcessBlocks()) {
				if( block.getName().equalsIgnoreCase(blockName)) {
					id = block.getBlockId().toString();
					break;
				}
			}
		}
		catch(IllegalArgumentException iae) {
			log.warnf("%s.getBlockId: Diagram UUID string is illegal (%s)",CLSS,diagramId);
		}
		return id;
	}
	/**
	 * Query the block controller for a block specified by the block id. If the block
	 * does not exist, create it. If the diagram is null, it means that the controller
	 * has not "seen" it yet.
	 * 
	 * @param className class of the block
	 * @param projectId id of the project containing the diagram
	 * @param resourceId id of the diagram as a project resource
	 * @param blockId UUID of the block
	 * @return the properties of an existing or new block.
	 */
	@Override
	public synchronized List<BlockProperty> getBlockProperties(String className,ProjectResourceId resourceId, UUID blockId) {
		// If the instance doesn't exist, create one
		log.debugf("%s.getBlockProperties of %s (%s)",CLSS,className,blockId.toString());
		List<BlockProperty> results = new ArrayList<>();
		ProcessDiagram diagram = controller.getDiagram(resourceId);
		ProcessBlock block = null;
		if( diagram!=null ) block = diagram.getProcessBlock(blockId);
		BlockProperty[] props = null;
		if(block!=null) {
			props = block.getProperties();  // Existing block
			log.tracef("%s.getProperties existing %s = %s",CLSS,block.getClass().getName(),props.toString());
		}
		else {
			block = createInstance(className,(diagram!=null?diagram.getResourceId():resourceId),blockId);  // Block is not (yet) attached to a diagram
			if(block!=null) {
				props = block.getProperties();
				log.tracef("%s.getProperties new %s = %s",CLSS,block.getClass().getName(),props.toString());
			}
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
	public synchronized BlockProperty getBlockProperty(ProcessDiagram diagram,UUID blockId,String propertyName) {
		ProcessBlock block = null;
		if( diagram!=null ) block = diagram.getProcessBlock(blockId);
		BlockProperty property = null;
		if(block!=null) {
			property = block.getProperty(propertyName);  // Existing block
		}
		else {
			log.warnf("%s.getProperty Block not found for %s.%s",CLSS,diagram.getPath(),blockId.toString());
		}
		return property;
	}
	/**
	 * The list of prototypes depends on the module, not the project.
	 */
	@SuppressWarnings("rawtypes")
	@Override
	public synchronized List<PalettePrototype> getBlockPrototypes() {
		List<PalettePrototype> results = new ArrayList<>();
		// List all classes in "com.ils.block" package
		List<Class<?>> classes = new ArrayList<>();
		try {
			// Find the jar with the block classes - the jar URLs are colon-separated
			String classPath = System.getProperty("java.class.path");
			log.infof("%s.getBlockPrototypes: ClassPath = %s",CLSS,classPath);
			String[] pathElements = classPath.split(File.pathSeparator);
			for(String pathElement:pathElements) {
				if(pathElement.contains(BLTProperties.BLOCK_JAR_NAME)) {
					File libdir = context.getSystemManager().getLibDir();
					pathElement = pathElement.substring(4);  // Remove /lib
					String path= libdir.getAbsolutePath()+File.separator+pathElement;
					log.infof("%s.getBlockPrototypes: Block jar = %s",CLSS,path);
					URL url = new URL(String.format("jar:file:%s!/",path));
					JarURLConnection jarConnection = (JarURLConnection)url.openConnection();
					JarFile jar = jarConnection.getJarFile();
					classes = findAnnotatedClassesInJar(jar,ExecutableBlock.class);
					break;
				}
			}
		}
		catch (Throwable ex) {
			log.warnf("%s.getBlockPrototypes: Exception getting block class jsr (%s)",CLSS,ex.getMessage(),ex);
		}
		for( Class<?> cls:classes) {
			//log.infof("%s.getBlockPrototypes:   found block class: %s",CLSS,cls.getName());
			Constructor[] ctors = cls.getDeclaredConstructors();
			Constructor ctor = null;
			for (int i = 0; i < ctors.length; i++) {
			    ctor = ctors[i];
			    if (ctor.getGenericParameterTypes().length == 0)
				break;
			}
			try {
				ctor.setAccessible(true);
		 	    Object obj = ctor.newInstance();
				if( obj instanceof ProcessBlock ) {
					PalettePrototype bp = ((ProcessBlock)obj).getBlockPrototype();
					log.infof("%s.getBlockPrototypes: Add %s on %s",CLSS,bp.getPaletteLabel(),bp.getTabName());
					results.add(bp);
				}
				else {
					log.warnf("%s: Class %s not a ProcessBlock",CLSS,cls.getName());
				}
			} 
			catch (InstantiationException ie) {
				log.warnf("%s.getBlockPrototypes: Exception instantiating block (%s)",CLSS,ie.getLocalizedMessage());
			} 
			catch (IllegalAccessException iae) {
				log.warnf("%s.getBlockPrototypes: Access exception (%s)",CLSS,iae.getMessage());
			}		
			catch (Exception ex) {
				log.warnf("%s.getBlockPrototypes: Runtime exception (%s)",CLSS,ex.getMessage(),ex);
			}
		}
		// Now add prototypes from Python-defined blocks
		// NOTE: We use the gateway script manager because these blocks do
		//       not yet exist in a diagram (or project).
		ProxyHandler phandler = ProxyHandler.getInstance();
		try {
			List<PalettePrototype> prototypes = phandler.getPalettePrototypes();
			for( PalettePrototype pp:prototypes) {
				log.infof("%s.getBlockPrototypes: Adding python %s on %s",CLSS,pp.getPaletteLabel(),pp.getTabName());
				results.add(pp);
			}
		}
		catch (Exception ex) {
			log.warnf("%s.getBlockPrototypes: Runtime exception (%s)",CLSS,ex.getMessage(),ex);
		}
		return results;
	}
	/** 
	 * Loop through all classes in the jar file. Return the subset
	 * with the indicated annotation. 
	 * The search involves instantiating classes that match. Since
	 * instantiating random classes can have unexpected side effects
	 * we limit the search to classes that belong to some parent package.
	 * Specify the package with '/', as in "com/ils/block/".
	 *
	 * @param jar the jar file containing the classes to search
	 * @param annotation the constructor annotation to test for
	 * @param pattern first part of package to be considered. 
	 * @return the classes (loaded) 
	 */
	private List<Class<?>> findAnnotatedClassesInJar(JarFile jar,Class<? extends Annotation> annotation)  { 
		List<Class<?>> classes = new ArrayList<Class<?>>();
		Enumeration<JarEntry> jarWalker = jar.entries();
		while( jarWalker.hasMoreElements()) {
			JarEntry entry = jarWalker.nextElement();
			if( entry.getName()!=null && entry.getName().endsWith(".class") && !entry.isDirectory()) {
				// Reject anonymous internal classes
				if( entry.getName().contains("$")) continue;
				// Convert the path to a classname
				StringBuilder className = new StringBuilder();
				for( String pak:entry.getName().split("/")) {
					if( className.length()!=0) className.append(".");
					className.append(pak);
					if( pak.endsWith(".class")) className.setLength(className .length()-".class".length());
				}
				Class<?> clss = null;
				try {
					clss = Class.forName(className.toString());
					if( clss!=null ) {
						if( clss.getAnnotation(annotation) !=null ) {
							log.debugf("%s: %s is annotated",CLSS,className.toString());
							classes.add(clss);
						}
					}
				}
				catch(Exception cnf) {
					log.warnf("%s: findAnnotatedClassesInJar - error instantiating %s (%s) ",CLSS,entry.getName(),cnf.getLocalizedMessage());
				}
			}
		} 
		return classes; 
	} 
	@Override
	public synchronized String getBlockState(ProjectResourceId diagramId, String blockName) {
		String state = "NOTFOUND";
		try {
			ProcessDiagram diagram = controller.getDiagram(diagramId);
			for(ProcessBlock block:diagram.getProcessBlocks()) {
				if( block.getName().equalsIgnoreCase(blockName)) {
					state = block.getState().name();
					break;
				}
			}
		}
		catch(IllegalArgumentException iae) {
			log.warnf("%s.getBlockState: Diagram UUID string is illegal (%s)",CLSS,diagramId);
		}
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
	 * @param resid identifier for node
	 * @return database name
	 */
	@Override
	public String getDatabaseForId(ProjectResourceId resid) {
		// Search up the tree for a parent diagram or application. Determine the
		// state. Unless we find a diagram, we don't return any connection name.
		String db = "NONE";
		DiagramState ds = DiagramState.DISABLED;
		try {
			ProcessNode node = controller.getProcessNode(resid);
			while( node!=null ) {
				if( node instanceof ProcessDiagram ) {
					ds = ((ProcessDiagram)node).getState();
					//log.debugf("%s.getApplication, found application = %s ",CLSS,app.getName());
					break;
				}
				// The parent can be either a folder, or root
				ProcessNode parentNode = controller.getParentNode(node);
				ProjectResourceId parent = parentNode.getResourceId();
				node = controller.getProcessNode(parent);
			}
			if(ds.equals(DiagramState.ACTIVE)) {
				db = getProjectToolkitProperty(resid.getProjectName(),ToolkitProperties.TOOLKIT_PROPERTY_DATABASE);
			}
			else if(ds.equals(DiagramState.ISOLATED)) {
				db = getProjectToolkitProperty(resid.getProjectName(),ToolkitProperties.TOOLKIT_PROPERTY_ISOLATION_DATABASE);
			}
		}
		catch(IllegalArgumentException iae) {
			log.warnf("%s.getDatabaseForId: %s is an unknown ID (%s)",CLSS,resid,iae.getMessage());
		}
		return db;
	}
	
	@Override
	public synchronized List<String> getDatasourceNames() {
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
	 * @param diagramId String representation of the diagram's internal Id.
	 * @return a descriptor for the diagram that corresponds to that Id.
	 */
	@Override
	public SerializableResourceDescriptor getDiagram(ProjectResourceId diagramId) {
		SerializableResourceDescriptor descriptor = null;
		ProcessDiagram diagram = controller.getDiagram(diagramId);
		if( diagram!=null ) {
			descriptor = diagram.toResourceDescriptor();
		}
		return descriptor;
	}
	/**
	 * When called from the gateway, we have no project. Get them all.
	 */
	public synchronized List<SerializableResourceDescriptor> getDiagramDescriptors() {
		List<SerializableResourceDescriptor> descriptors = controller.getDiagramDescriptors();
		return descriptors;
	}
	// Search all diagrams for the target block.
	@Override
	public SerializableResourceDescriptor getDiagramForBlock(String blockId) {
		UUID uuid = makeUUID(blockId);
		List<ProcessDiagram> diagrams = controller.getDelegate().getDiagrams();
		for(ProcessDiagram diagram:diagrams) {
			if( diagram.getProcessBlock(uuid)!=null) return diagram.toResourceDescriptor();
		}
		return null;
	}
	
	/**
	 * @param diagramId diagram identifier
	 * @return the current state of the specified diagram as a DiagramState.
	 */
	@Override
	public synchronized DiagramState getDiagramState(ProjectResourceId diagramId) {
		DiagramState state = DiagramState.ACTIVE;
		ProcessDiagram diagram = controller.getDiagram(diagramId);
		if( diagram!=null ) {
			state = diagram.getState();
		}
		return state;
	}

	public String getExecutionState() {
		return BlockExecutionController.getExecutionState();
	}

	/**
	 * Use the UUID of this block to signify that this is the source.
	 * @param diagramId
	 * @param blockId
	 * @return an explanation for the block's current state
	 */
	@Override
	public synchronized String getExplanation(ProjectResourceId diagramId,String blockName) {
		String explanation = "";
		ProcessDiagram diagram = controller.getDiagram(diagramId);
		if(diagram!=null) {
			ProcessBlock blk = diagram.getBlockByName(blockName);
			UUID uuid = blk.getBlockId();
			List<UUID> members = new ArrayList<>();
			members.add(uuid);
			if( blk!=null ) explanation = blk.getExplanation(diagram, members); 
		}
		return explanation;
	}
	/**
	 * Query a block for its internal state. This allows a read-only display in the
	 * designer to be useful for block debugging.
	 * 
	 * @param diagramId
	 * @param blockId
	 * @return a SerializableBlockStateDescriptor
	 */
	@Override
	public SerializableBlockStateDescriptor getInternalState(ProjectResourceId diagramId,String blockId) {
		SerializableBlockStateDescriptor descriptor = null;
		ProcessDiagram diagram = controller.getDiagram(diagramId);
		if(diagram!=null) {
			ProcessBlock block = controller.getBlock(diagram, UUID.fromString(blockId));
			if( block!=null ) descriptor = block.getInternalStatus();
		}
		return descriptor;
	}
	/**
	 * Find the name of the isolation datasource from the internal SQLite database. 
	 * @return isolation database name
	 */
	@Override
	public String getProjectIsolationDatabase(String projectName) {
		return getProjectToolkitProperty(projectName,ToolkitProperties.TOOLKIT_PROPERTY_ISOLATION_DATABASE);
	}

	/**
	 * Find the name of the isolation tag provider from the internal SQLite database. 
	 * @return isolation tag provider name
	 */
	@Override
	public String getProjectIsolationTagProvider(String projectName) {
		return getProjectToolkitProperty(projectName,ToolkitProperties.TOOLKIT_PROPERTY_ISOLATION_PROVIDER);
	}
	/**
	 * Find the name of the production datasource from the internal SQLite database. 
	 * @return production database name
	 */
	@Override
	public String getProjectProductionDatabase(String projectName) {
		return getProjectToolkitProperty(projectName,ToolkitProperties.TOOLKIT_PROPERTY_DATABASE);
	}
	/**
	 * Find the name of the isolation tag provider from the internal SQLite database. 
	 * @return production tag provider name
	 */
	@Override
	public String getProjectProductionTagProvider(String projectName) {
		return getProjectToolkitProperty(projectName,ToolkitProperties.TOOLKIT_PROPERTY_PROVIDER);
	}
	@Override
	public Object getPropertyBinding(ProjectResourceId diagramId, String blockId,String propertyName) {
		BlockProperty property = null;
		ProcessDiagram diagram = controller.getDiagram(diagramId);
		UUID blockUUID;
		try {
			blockUUID = UUID.fromString(blockId);
			property = getBlockProperty(diagram,blockUUID,propertyName);
		}
		catch(IllegalArgumentException iae) {
			log.warnf("%s.getPropertyBinding: Diagram or block UUID string is illegal (%s,%s),",CLSS,diagramId.getResourcePath().getPath().toString(),blockId);
		}
		String binding = property.getBinding();
		if( binding==null ) binding = "";
		return binding;
	}
	@Override
	public Object getPropertyValue(ProjectResourceId diagramId, String blockId,String propertyName) {
		BlockProperty property = null;
		ProcessDiagram diagram = controller.getDiagram(diagramId);
		UUID blockUUID;
		try {
			blockUUID = UUID.fromString(blockId);
			property = getBlockProperty(diagram,blockUUID,propertyName);
		}
		catch(IllegalArgumentException iae) {
			log.warnf("%s.getPropertyValue: Diagram or block UUID string is illegal (%s,%s),",CLSS,diagramId.getResourcePath().getPath().toString(),blockId);
		}
		return property.getValue();
	}
	public Object getPropertyValue(ProjectResourceId diagramId,UUID blockId,String propertyName) {
		Object val = null;
		ProcessDiagram diagram = controller.getDiagram(diagramId);
		if(diagram!=null) {
			ProcessBlock block = controller.getBlock(diagram, blockId);
			if( block!=null ) {
				BlockProperty prop = block.getProperty(propertyName);
				if( prop!=null) val = prop.getValue();
			}
		}
		return val;

	}
	/**
	 * Find the parent application or diagram of the entity referenced by
	 * the supplied id. Test the state and return the name of the appropriate
	 * provider.  
	 * @param resid identifier for node
	 * @return database name
	 */
	@Override
	public String getProviderForId(ProjectResourceId resid) {
		// Search up the tree for a parent diagram or application. Determine the
		// state. Unless we find a diagram, we don't return any connection name.
		String provider = "NONE";
		DiagramState ds = DiagramState.DISABLED;
		try {
			ProcessNode node = controller.getProcessNode(resid);
			while( node!=null ) {
				if( node instanceof ProcessDiagram ) {
					ds = ((ProcessDiagram)node).getState();
					break;
				}
				node = controller.getParentNode(node);
			}
			if(ds.equals(DiagramState.ACTIVE)) {
				provider = getProjectToolkitProperty(resid.getProjectName(),ToolkitProperties.TOOLKIT_PROPERTY_PROVIDER);
			}
			else if(ds.equals(DiagramState.ISOLATED)) {
				provider = getProjectToolkitProperty(resid.getProjectName(),ToolkitProperties.TOOLKIT_PROPERTY_ISOLATION_PROVIDER);
			}
		}
		catch(IllegalArgumentException iae) {
			log.warnf("%s.getProviderForId: %s is an illegal UUID (%s)",CLSS,resid.getResourcePath().getPath().toString(),iae.getMessage());
		}
		return provider;
	}
	/**
	 * @param diagramId string representation of the diagram's unique id
	 * @param blockName name of the block within the diagram
	 * @return the time at which the block last changed its state
	 */
	@Override
	public Date getTimeOfLastBlockStateChange(ProjectResourceId diagramId, String blockName) {
		Date date = null;
		try {
			ProcessDiagram diagram = controller.getDiagram(diagramId);
			for(ProcessBlock block:diagram.getProcessBlocks()) {
				if( block.getName().equalsIgnoreCase(blockName)) {
					date = block.getTimeOfLastStateChange();
					break;
				}
			}
		}
		catch(IllegalArgumentException iae) {
			log.warnf("%s.getTimeOfLastBlockStateChange: Diagram UUID string is illegal (%s)",CLSS,diagramId);
		}
		return date;
	}
	/**
	 * On a failure to find the property, an empty string is returned.
	 */
	@Override
	public String getProjectToolkitProperty(String projectName,String propertyName) {
		return toolkitProjectRecordHandler.getToolkitProjectProperty(projectName,propertyName);
	}
	
	/**
	 * On a failure to find the property, an empty string is returned.
	 */
	@Override
	public String getToolkitProperty(String propertyName) {
		return toolkitRecordHandler.getToolkitProperty(propertyName);
	}
	
	/**
	 * Retrieve the configured browser path from the ORM database HelpRecord
	 * @return the configured browser path (for Windows)
	 */
	@Override
	public String getWindowsBrowserPath() {
		String path = null;
		log.infof("***** In getWindowsBrowserPath, getting path to browser from ORM ****");
		/*
		path = "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe";
		return path;
		*/
		SQuery<HelpRecordProxy> query = new SQuery<HelpRecordProxy>(HelpRecordProxy.META).eq(HelpRecordProxy.Id,0L);
		HelpRecordProxy rec = this.context.getPersistenceInterface().queryOne(query);
		if(rec!=null ) path = rec.getWindowsBrowserPath();
		return path;
	}
	@Override
	public boolean isControllerRunning() {
		return getExecutionState().equalsIgnoreCase("running");
	}
	@Override
	public synchronized List<SerializableBlockStateDescriptor> listBlocksConnectedAtPort(ProjectResourceId diagramId,String blockId,String portName) {
		List<SerializableBlockStateDescriptor> descriptors = new ArrayList<>();
		ProcessDiagram diagram = controller.getDiagram(diagramId);
		if( diagram!=null ) {
			ProcessBlock blk = diagram.getProcessBlock(blockId);
			if(blk!=null) {
				List<ProcessBlock>connectedBlocks =  diagram.getConnectedBlocksAtPort(blk,portName);
				for( ProcessBlock pb:connectedBlocks ) {
					if(pb==null) continue; 
					descriptors.add(pb.toDescriptor());
				}
			}
		}
		else {
			log.warnf("%s.listBlocksConnectedAtPort: Parent diagram not found for %s at port %s",CLSS,diagramId.getResourcePath().getPath().toString(),portName);
		}
		return descriptors;
	}
	
	@Override
	public synchronized List<SerializableBlockStateDescriptor> listBlocksDownstreamOf(ProjectResourceId diagramId,String blockName) {
		List<SerializableBlockStateDescriptor> descriptors = new ArrayList<>();
		ProcessDiagram diagram = controller.getDiagram(diagramId);
		if( diagram!=null ) {
			ProcessBlock blk = diagram.getBlockByName(blockName);
			if(blk!=null) descriptors = controller.listBlocksDownstreamOf(diagramId,blk.getBlockId(),false);
		}
		else {
			log.warnf("%s.listBlocksDownstreamOf: no diagram found for id %s",CLSS,diagramId.getResourcePath().getPath().toString());
		}
		return descriptors;
	}
	
	@Override
	public synchronized List<SerializableBlockStateDescriptor> listBlocksForTag(String projectName,String tagpath) {
		if(DEBUG) log.infof("%s.listBlocksForTag: project=%s, tagpath=%s",CLSS,projectName,tagpath);
		List<SerializableBlockStateDescriptor> results = new ArrayList<>();
		if( tagpath!=null && !tagpath.isEmpty() ) {
			List<SerializableResourceDescriptor> descriptors = controller.getDiagramDescriptors();
			for(SerializableResourceDescriptor descriptor:descriptors) {
				ProcessDiagram diagram = controller.getDiagram(descriptor.getResourceId());
				if( diagram!=null) {
					if( diagram.getProjectName().equals(projectName)) {
						Collection<ProcessBlock> blocks = diagram.getProcessBlocks();
						for(ProcessBlock block:blocks) {
							if( block.usesTag(tagpath)) {
								results.add(block.toDescriptor());
							}
						}
					}
				}
				else {
					log.warnf("%s.listBlocksForTag: no diagram found for id %s",CLSS,descriptor.getPath());
				}
			}
		}
		if(DEBUG) log.infof("%s.listBlocksForTag: %s returns %d blocks",CLSS,tagpath,results.size());
		return results;
	}
	@Override
	public synchronized List<SerializableBlockStateDescriptor> listBlocksGloballyDownstreamOf(ProjectResourceId diagramId, String blockName) {
		List<SerializableBlockStateDescriptor> descriptors = new ArrayList<>();
		ProcessDiagram diagram = controller.getDiagram(diagramId);
		if( diagram!=null ) {
			ProcessBlock blk = diagram.getBlockByName(blockName);
			if(blk!=null) descriptors = controller.listBlocksDownstreamOf(diagramId,blk.getBlockId(),true);
		}
		else {
			log.warnf("%s.listBlocksGloballyDownstreamOf: no diagram found for %s",CLSS,diagramId.getResourcePath().getPath().toString());
		}
		return descriptors;
	}
	@Override
	public synchronized List<SerializableBlockStateDescriptor> listBlocksGloballyUpstreamOf(ProjectResourceId diagramId, String blockName) {
		log.tracef("%s.listBlocksGloballyUpstreamOf: diagramId %s:%s",CLSS,diagramId,blockName);
		List<SerializableBlockStateDescriptor> descriptors = new ArrayList<>();
		ProcessDiagram diagram = controller.getDiagram(diagramId);
		if( diagram!=null ) {
			ProcessBlock blk = diagram.getBlockByName(blockName);
			if(blk!=null) {
				descriptors = controller.listBlocksUpstreamOf(diagramId,blk.getBlockId(),true);
			}
			else {
				log.warnf("%s.listBlocksGloballyUpstreamOf: block %s not found on diagram %s",CLSS,blockName,diagramId.getResourcePath().getPath().toString());
			}
		}
		else {
			log.warnf("%s.listBlocksGloballyUpstreamOf: no diagram found for %s",CLSS,diagramId.getResourcePath().getPath().toString());
		}
		log.tracef("%s.listBlocksGloballyUpstreamOf: diagramId %s returning %d descriptors",CLSS,diagramId.getResourcePath().getPath().toString(),descriptors.size());
		return descriptors;
	}
	
	/**
	 * Query the ModelManager for a list of the project resources that it is currently
	 * managing. This is a debugging service.
	 * @return a list of descriptors containing all blocks in the diagram
	 */
	@Override
	public synchronized List<SerializableBlockStateDescriptor> listBlocksInDiagram(ProjectResourceId diagramId) {
		//log.infof("%s.listBlocksInDiagram: diagramId %s",TAG,diagramId);
		List<SerializableBlockStateDescriptor> descriptors = new ArrayList<>();
		ProcessDiagram diagram = controller.getDiagram(diagramId);
		if( diagram!=null) {
			Collection<ProcessBlock> blocks = diagram.getProcessBlocks();
			for(ProcessBlock block:blocks) {
				SerializableBlockStateDescriptor desc = block.toDescriptor();
				//log.infof("%s.listBlocksInDiagram: process block %s",TAG,desc.getName());
				Map<String,String> attributes = desc.getAttributes();
				attributes.put(BLTProperties.BLOCK_ATTRIBUTE_ID,block.getClass().getName());
				attributes.put(BLTProperties.BLOCK_ATTRIBUTE_ID,block.getBlockId().toString());
				descriptors.add(desc);
			}
		}
		else {
			log.warnf("%s.listBlocksInDiagram: no diagram found for %s",CLSS,diagramId.getResourcePath().getPath().toString());
		}
		//log.infof("%s.listBlocksInDiagram: diagramId %s returning %d descriptors",TAG,diagramId,descriptors.size());
		return descriptors;
	}
	/**
	 * @param className fully qualified class name of blocks to be listed
	 * @return a list of state descriptors for blocks that are of the specified class.
	 */
	public List<SerializableBlockStateDescriptor> listBlocksOfClass(String projectName,String className) {
		List<SerializableBlockStateDescriptor> descriptors = new ArrayList<>();
		List<SerializableResourceDescriptor> diagrams = controller.getDiagramDescriptors();
		for(SerializableResourceDescriptor diag:diagrams) {
			if(diag.getProjectName().equals(projectName)) {
				List<SerializableBlockStateDescriptor> blocks = listBlocksInDiagram(diag.getResourceId());
				for(SerializableBlockStateDescriptor desc:blocks) {
					if( desc.getClassName().equals(className)) descriptors.add(desc);
				}
			}
		}
		if(DEBUG) log.infof("%s.listBlocksOfClass: %s returning %d descriptors",CLSS,className,descriptors.size());
		return descriptors;
	}
	
	@Override
	public synchronized List<SerializableBlockStateDescriptor> listBlocksUpstreamOf(ProjectResourceId diagramId, String blockName) {
		List<SerializableBlockStateDescriptor> descriptors = new ArrayList<>();
		ProcessDiagram diagram = controller.getDiagram(diagramId);
		if( diagram!=null ) {
			ProcessBlock blk = diagram.getBlockByName(blockName);
			if(blk!=null) descriptors = controller.listBlocksUpstreamOf(diagramId,blk.getBlockId(),false);
		}
		else {
			log.warnf("%s.listBlocksUpstreamOf: no diagram found for %s",CLSS,diagramId.getResourcePath().getPath().toString());
		}
		return descriptors;
	}

	@Override
	public synchronized List<SerializableBlockStateDescriptor> listConfigurationErrors() {
		log.tracef("%s.listConfigurationErrors:",CLSS);
		List<SerializableBlockStateDescriptor> result = new ArrayList<>();
		List<SerializableResourceDescriptor> descriptors = controller.getDiagramDescriptors();
		try {
			for(SerializableResourceDescriptor res:descriptors) {
				ProcessDiagram diagram = controller.getDiagram(res.getResourceId());
				for( ProcessBlock block:diagram.getProcessBlocks() ) {
					String problem = block.validate();
					if( problem!=null) {
						SerializableBlockStateDescriptor descriptor = block.toDescriptor();
						descriptor.getAttributes().put(BLTProperties.BLOCK_ATTRIBUTE_PATH, diagram.getResourceId().getFolderPath());
						descriptor.getAttributes().put(BLTProperties.BLOCK_ATTRIBUTE_ISSUE, problem);
						result.add(descriptor);
					}
				}
			}
		}
		catch(Exception ex) {
			log.debug(CLSS+".listConfigurationErrors: Exception ("+ex.getMessage()+")",ex);
		}
		return result;
	}
	
	@Override
	public synchronized List<SerializableBlockStateDescriptor> listDiagramBlocksOfClass(ProjectResourceId diagramId, String className) {
		ProcessDiagram diagram = controller.getDiagram(diagramId);
		List<SerializableBlockStateDescriptor> result = new ArrayList<>();
		if( diagram!=null ) {
			for(ProcessBlock block:diagram.getProcessBlocks()) {
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
		if(DEBUG) log.infof("%s.listDiagramDescriptors: %d diagrams for project %s",CLSS,descriptors.size(),projectName);
		return descriptors;
	}
	/**
	 * Query the ModelManager for a list of the project resources that it is currently
	 * managing. This is a debugging service.
	 * @return the node list
	 */
	@Override
	public List<SerializableResourceDescriptor> listResourceNodes() {
		return controller.queryControllerResources();
	}
	
	/**
	 * Do an exhaustive search for all sink blocks that have the same binding
	 * as the specified block. We cover all diagrams in the system. There 
	 * shoud only be one.
	 * 
	 * @param diagramId identifier for the diagram
	 * @param blockId Id of the source
	 * @return a list of block descriptors for the sinks that were found
	 */
	@Override
	public synchronized List<SerializableBlockStateDescriptor> listSinksForSource(ProjectResourceId diagramId,String blockId) {
		List<SerializableBlockStateDescriptor> results = new ArrayList<>();
		ProcessDiagram diagram = controller.getDiagram(diagramId);
		ProcessBlock source = null;
		if(diagram!=null) {
			source = diagram.getProcessBlock(makeUUID(blockId));
		}
	
		String tagPath = null;
		if( source!=null && (source.getClassName().equalsIgnoreCase(BlockConstants.BLOCK_CLASS_SOURCE))) {
			BlockProperty prop = source.getProperty(BlockConstants.BLOCK_PROPERTY_TAG_PATH);
			if( prop!=null ) tagPath = fcns.providerlessPath(prop.getBinding());
		}

		if( tagPath!=null && tagPath.length()>0 ) {
			List<SerializableResourceDescriptor> descriptors = controller.getDiagramDescriptors();
			for(SerializableResourceDescriptor desc:descriptors) {
				diagram = controller.getDiagram(desc.getResourceId());
				if( diagram!=null ) {
					for(ProcessBlock sink:diagram.getProcessBlocks()) {
						if( sink.getClassName().equalsIgnoreCase(BlockConstants.BLOCK_CLASS_SINK) ) {
							BlockProperty prop = sink.getProperty(BlockConstants.BLOCK_PROPERTY_TAG_PATH);
							if( prop!=null && tagPath.equalsIgnoreCase(fcns.providerlessPath(prop.getBinding()))  ) {
								results.add(sink.toDescriptor());
							}
						}
					}
				}
				else {
					log.warnf("%s.listSinksForSource: Diagram %s not found",CLSS,desc.getResourceId().getFolderPath());
				}
			}
		}
		else {
			log.warnf("%s.listSinksForSource: Block %s not found, not a source/input or not bound",CLSS,blockId);
		}
		return results;
	}
	/**
	 * Do an exhaustive search for all source blocks that have the same binding
	 * as the specified block. We cover all diagrams in the system.
	 * @param diagramId identifier for the diagram
	 * @param blockId Id of the sink
	 * @return a list of descriptors for the sources that were found
	 */
	@Override
	public synchronized List<SerializableBlockStateDescriptor> listSourcesForSink(ProjectResourceId diagramId,String blockId) {
		List<SerializableBlockStateDescriptor> results = new ArrayList<>();
		ProcessDiagram diagram = controller.getDiagram(diagramId);
		ProcessBlock sink = null;
		if(diagram!=null) {
			sink = diagram.getProcessBlock(makeUUID(blockId));
		}

		String tagPath = null;
		if( sink!=null && (sink.getClassName().equalsIgnoreCase(BlockConstants.BLOCK_CLASS_SINK))) {
			BlockProperty prop = sink.getProperty(BlockConstants.BLOCK_PROPERTY_TAG_PATH);
			if( prop!=null ) tagPath = fcns.providerlessPath(prop.getBinding());
		}
		
		if( tagPath!=null && tagPath.length()>0 ) {
			List<SerializableResourceDescriptor> descriptors = controller.getDiagramDescriptors();
			for(SerializableResourceDescriptor descriptor:descriptors) {
				diagram = controller.getDiagram(descriptor.getResourceId());
				if( diagram!=null ) {
					for(ProcessBlock source:diagram.getProcessBlocks()) {
						if( source.getClassName().equalsIgnoreCase(BlockConstants.BLOCK_CLASS_SOURCE) ) {
							BlockProperty prop = source.getProperty(BlockConstants.BLOCK_PROPERTY_TAG_PATH);
							if( prop!=null && tagPath.equalsIgnoreCase(fcns.providerlessPath(prop.getBinding()))  ) {
								results.add(source.toDescriptor());
							}
						}
					}
				}
				else {
					log.warnf("%s.listSourcesForSink: Diagram %s not found",CLSS,descriptor.getResourceId().getFolderPath());
				}
			}
		}
		else {
			log.warnf("%s.listSourcesForSink: Block %s not found, not a sink/output or not bound",CLSS,blockId);
		}
		return results;
	}
	/**
	 * Do an exhaustive search for all source blocks that have the same binding
	 * as the specified block. We cover all diagrams in the system. This method 
	 * is not part of the external interface, as the result is not serializable.
	 * @param diagramId identifier for the diagram
	 * @param blockId Id of the sink
	 * @return a list of descriptors for the sources that were found
	 */
	public synchronized List<ProcessBlock> listSourceBlocksForSink(ProjectResourceId diagramId,String blockId) {
		List<ProcessBlock> results = new ArrayList<>();
		ProcessDiagram diagram = controller.getDiagram(diagramId);
		log.infof("%s.listSourceBlocksForSink: %s",CLSS,diagramId.getFolderPath());
		ProcessBlock sink = null;
		if(diagram!=null) {
			sink = diagram.getProcessBlock(makeUUID(blockId));
		}
		String tagPath = null;
		if( sink!=null && (sink.getClassName().equalsIgnoreCase(BlockConstants.BLOCK_CLASS_SINK))) {
			BlockProperty prop = sink.getProperty(BlockConstants.BLOCK_PROPERTY_TAG_PATH);
			if( prop!=null ) tagPath = fcns.providerlessPath(prop.getBinding());
		}
		
		if( tagPath!=null && tagPath.length()>0 ) {
			List<SerializableResourceDescriptor> descriptors = controller.getDiagramDescriptors();
			for(SerializableResourceDescriptor descriptor:descriptors) {
				diagram = controller.getDiagram(descriptor.getResourceId());
				if( diagram!=null ) {
					for(ProcessBlock source:diagram.getProcessBlocks()) {
						if( source.getClassName().equalsIgnoreCase(BlockConstants.BLOCK_CLASS_SOURCE) ) {
							BlockProperty prop = source.getProperty(BlockConstants.BLOCK_PROPERTY_TAG_PATH);
							if( prop!=null && tagPath.equalsIgnoreCase(fcns.providerlessPath(prop.getBinding()))  ) {
								results.add(source);
							}
						}
					}
				}
				else {
					log.warnf("%s.listSourceBlocksForSink: Diagram %s not found",CLSS,descriptor.getResourceId().getFolderPath());
				}
			}
		}
		else {
			log.warnf("%s.listSourceBlocksForSink: Block %s not found, not a sink/output or not bound",CLSS,blockId);
		}
		return results;
	}
	@Override
	public synchronized List<SerializableBlockStateDescriptor> listSubscriptionErrors() {
		log.tracef("%s.listSubscriptionErrors:",CLSS);
		List<SerializableBlockStateDescriptor> result = new ArrayList<>();
		List<SerializableResourceDescriptor> descriptors = controller.getDiagramDescriptors();
		try {
			for(SerializableResourceDescriptor res:descriptors) {
				ProcessDiagram diagram = controller.getDiagram(res.getResourceId());
				if( diagram!=null && !diagram.getState().equals(DiagramState.DISABLED)) {
					for( ProcessBlock block:diagram.getProcessBlocks() ) {
						String problem = block.validateSubscription();
						if( problem!=null) {
							SerializableBlockStateDescriptor descriptor = block.toDescriptor();
							descriptor.getAttributes().put(BLTProperties.BLOCK_ATTRIBUTE_PATH, diagram.getResourceId().getResourcePath().getFolderPath());
							descriptor.getAttributes().put(BLTProperties.BLOCK_ATTRIBUTE_ISSUE, problem);
							result.add(descriptor);
						}
					}
				}
			}
		}
		catch(Exception ex) {
			log.debug(CLSS+".listConfigurationErrors: Exception ("+ex.getMessage()+")",ex);
		}
		return result;
	}
	
	@Override
	public synchronized List<SerializableBlockStateDescriptor> listUnresponsiveBlocks(double hours,String className) {
		log.tracef("%s.listUnresponsiveBlocks: Hrs %f, class %s,",CLSS,hours,className);
		List<SerializableBlockStateDescriptor> result = new ArrayList<>();
		List<SerializableResourceDescriptor> descriptors = controller.getDiagramDescriptors();
		long interval = (long)(hours*3600*1000);  // mses
		long now = System.currentTimeMillis();
		try {
			for(SerializableResourceDescriptor res:descriptors) {
				ProcessDiagram diagram = controller.getDiagram(res.getResourceId());
				if( diagram!=null && !diagram.getState().equals(DiagramState.DISABLED)) {
					for( ProcessBlock block:diagram.getProcessBlocks() ) {
						if( className==null || className.isEmpty() || block.getClassName().equals(className)) {
							QualifiedValue qv = block.getLastValue();
							if( qv!=null && now-qv.getTimestamp().getTime()>interval ) {
								double hrs = ((double)(now-qv.getTimestamp().getTime()))/(3600*1000);
								SerializableBlockStateDescriptor descriptor = block.toDescriptor();
								descriptor.getAttributes().put(BLTProperties.BLOCK_ATTRIBUTE_PATH, res.getResourceId().getFolderPath());
								descriptor.getAttributes().put(BLTProperties.BLOCK_ATTRIBUTE_ISSUE, String.format("Unchanged for %.2f hrs", hrs));
								result.add(descriptor);
							}
						}
					}
				}
			}
		}
		catch(Exception ex) {
			log.debug(CLSS+".listConfigurationErrors: Exception ("+ex.getMessage()+")",ex);
		}
		return result;
	}
	
	/**
	 * Post a (simulated) block result on its output.
	 * @param diagramId the parent diagram
	 * @param blockId the block
	 * @param port anchor for the incoming connection
	 * @param value new value
	 */
	@Override
	public void postResult(ProjectResourceId diagramId,String blockId,String port,String value) {
		log.tracef("%s.postResult - %s = %s on %s",CLSS,blockId,value,port);
		try {
			UUID blockuuid   = UUID.fromString(blockId);
			ProcessDiagram diagram = controller.getDiagram(diagramId);
			for(ProcessBlock block:diagram.getProcessBlocks()) {
				if( block.getBlockId().equals(blockuuid)) {
					block.forcePost(port, value);
					break;
				}
			}
		}
		catch(IllegalArgumentException iae) {
			log.warnf("%s.postValue: one of %s or %s illegal UUID (%s)",CLSS,diagramId.getResourcePath().getPath().toString(),blockId,iae.getMessage());
		}
	}
	/**
	 * Handle the block placing a new value on its output. This minimalist version
	 * is likely called from an external source through an RPC.
	 * 
	 * @param diagramId identifier for the parent
	 * @param blockId identifier for the block
	 * @param port the output port on which to insert the result
	 * @param value the result of the block's computation
	 */
	public void postValue(String projectName,String resourcePath, String blockId,String port,String value)  {
		log.tracef("%s.postValue - %s = %s on %s",CLSS,blockId,value,port);
		UUID blockuuid   = UUID.fromString(blockId);
		ProjectResourceId diagramId = new ProjectResourceId(projectName,BLTProperties.DIAGRAM_RESOURCE_TYPE,resourcePath);
		postValue(diagramId,blockuuid,port,value,BLTProperties.QUALITY_GOOD) ;
	}
	
	/**
	 * Handle the block placing a new value on its output. This minimalist version
	 * is likely called from an external source through an RPC.
	 * 
	 * @param diagramId identifier for the parent
	 * @param blockId identifier for the block
	 * @param port the output port on which to insert the result
	 * @param value the result of the block's computation
	 */
	public void postValue(ProjectResourceId diagramId,String blockId,String port,String value)  {
		log.tracef("%s.postValue - %s = %s on %s",CLSS,blockId,value,port);
		try {
			UUID blockuuid   = UUID.fromString(blockId);
			postValue(diagramId,blockuuid,port,value,BLTProperties.QUALITY_GOOD) ;
		}
		catch(IllegalArgumentException iae) {
			log.warnf("%s.postValue: one of %s or %s illegal UUID (%s)",CLSS,diagramId.getResourcePath().getPath().toString(),blockId,iae.getMessage());
		}
	}
	/**
	 * Handle the block placing a new value on its output. This version is called from
	 * a Python implementation of a block.
	 * 
	 * @param diagramId identifier for the parent diagram
	 * @param blockId identifier for the block
	 * @param port the output port on which to insert the result
	 * @param value the result of the block's computation
	 * @param quality of the reported output
	 */
	public void postValue(ProjectResourceId diagramId,UUID blockId,String port,String value,String quality)  {
		log.tracef("%s.postValue - %s = %s (%s) on %s",CLSS,blockId,value,quality,port);
		try {
			ProcessDiagram diagram = controller.getDiagram(diagramId);
			if( diagram!=null) {
				ProcessBlock block = diagram.getProcessBlock(blockId);
				QualifiedValue qv = new BasicQualifiedValue(value,
						(quality.equalsIgnoreCase(BLTProperties.QUALITY_GOOD)?QualityCode.Good:QualityCode.Bad));
				OutgoingNotification note = new OutgoingNotification(block,port,qv);
				controller.acceptCompletionNotification(note);
			}
			else {
				log.warnf("%s.postValue: no diagram found for %s",CLSS,diagramId.getResourcePath().getPath().toString());
			}
		}
		catch(IllegalArgumentException iae) {
			log.warnf("%s.postValue: one of %s or %s illegal UUID (%s)",CLSS,diagramId.getResourcePath().getPath().toString(),blockId,iae.getMessage());
		}
	}

	/**
	 * Tell the block to send its current value on the outputs.
	 */
	@Override
	public void propagateBlockState(ProjectResourceId diagramId, String blockName) {
		
		ProcessDiagram diagram = null;
		UUID blockUUID = null;
		diagram = controller.getDiagram(diagramId);
		if( diagram!=null ) {
			ProcessBlock block = diagram.getBlockByName(blockName);
			if( block!=null ) {
				try {
					if(DEBUG) log.infof("%s.propagateBlockState: Propagating state of %s on %s", CLSS, blockName, diagramId.toString());
					blockUUID = block.getBlockId();
					controller.propagateBlockState(diagramId, blockUUID);
				}
				catch(IllegalArgumentException iae) {
					log.warnf("%s.propagateBlockState: Unable to propagate blok state for %s on %s", CLSS, blockName, diagramId.toString());
				}
			}
			else{
				log.warnf("%s.propagateBlockState: Unable to find block %s in diagram %s", CLSS, blockName, diagramId.toString());
			}
		}
	}
	
	/**
	 * Execute the getAux extension function in Gateway scope for the supplied resource.
	 * The notifications and resource saves are pretty heavy weight. If the aux data
	 * were and remain empty, forego the update.
	 * Send notification to the designer of any changes.
	 * @param projectId the project
	 * @param resid the resourceId of an application to be refreshed
	 * @param nodeId identifier of specified node
	 * @param provider tag provider
	 * @param db datasource
	 * @return auxiliary data read from the database
	 */
	@Override
	public synchronized GeneralPurposeDataContainer readAuxData(ProjectResourceId resid,String nodeId,String provider,String db) {
		Optional<RuntimeProject> optional = context.getProjectManager().getProject(resid.getProjectName());
		Project project = optional.get();
		Optional<ProjectResource> ores = project.getResource(resid);
		ProjectResource res = ores.get();
		GeneralPurposeDataContainer container = new GeneralPurposeDataContainer();
		if( res!=null && res.getResourceType().equals(BLTProperties.DIAGRAM_RESOURCE_TYPE)) {
			ProcessBlock block = controller.getDelegate().getBlock(resid, UUID.fromString(nodeId));
			block.getAuxData(container);
			controller.sendAuxDataNotification(nodeId, new BasicQualifiedValue(container));
		}
		return container;
	}
	
	// Modify a resource in a project with aux data back into the project.
	// Do not save the resource.
	// Note: This triggers the ModelManager project change listener.
	public void modifyResource(ProjectResource resource,Object node) {
		ObjectMapper mapper = new ObjectMapper();
		try{
			byte[] bytes = mapper.writeValueAsBytes(node);
			//log.tracef("%s.run JSON = %s",CLSS,new String(bytes));
			ProjectResourceBuilder builder = ProjectResource.newBuilder();
			builder.setResourceId(resource.getResourceId());
			builder.putData(bytes);
			builder.setVersion(resource.getVersion());
			Optional<RuntimeProject> optional = context.getProjectManager().getProject(resource.getProjectName());
			DesignableProject project = (DesignableProject)(optional.get());
			project.modifyResource(builder.build());
			log.infof("%s.modifyResource: Saving, resource %s (%s)",CLSS,resource.getResourceName(),resource.getResourceType());
		}
		catch(JsonProcessingException jpe) {
			log.warnf("%s.modifyResource: Exception serializing application, resource %d (%s)",CLSS,resource.getResourceId(),jpe.getMessage());
		}
		catch(Exception ex) {
			log.warnf("%s.modifyResource: Exception saving project, resource %d (%s)",CLSS,resource.getResourceId(),ex.getMessage());
		}
	}
	/**
	 * Set the name of a block. Property listeners are notified. 
	 * @param diagramId diagram Id
	 * @param blockId Id of the target block
	 * @param name the new name
	 */
	@Override
	public void renameBlock(ProjectResourceId diagramId, String blockId, String name) {
		ProcessDiagram diagram = controller.getDiagram(diagramId);
		ProcessBlock block = null;
		UUID blockUUID = makeUUID(blockId);
		if( diagram!=null ) block = diagram.getProcessBlock(blockUUID);
		if(block!=null) {
			block.setName(name);
			controller.sendNameChangeNotification(blockId, name);
		}
	}
	/**
	 * Rename the tag in both production and isolation
	 */
	@Override
	public void renameTag(String projectName,String name,String path) {
		String provider = getProjectProductionTagProvider(projectName);
		tagHandler.renameTag(provider,name,path);
		provider = getProjectIsolationTagProvider(projectName);
		tagHandler.renameTag(provider,name,path);
	}
	@Override
	public void resetBlock(ProjectResourceId diagramId, String blockName) {
		ProcessDiagram diagram = controller.getDiagram(diagramId);
		if( diagram!=null) {
			ProcessBlock block = diagram.getBlockByName(blockName);
			if( block!=null ) block.reset();
			else log.warnf("%s.resetBlock: block %s not found on diagram %s",CLSS,blockName,diagram.getName());
		}
		else {
			log.warnf("%s.resetBlock: no diagram found for %s",CLSS,diagramId.getResourcePath().getPath().toString());
		}
	}
	@Override
	public void resetDiagram(ProjectResourceId diagramId) {
		BlockExecutionController.getInstance().resetDiagram(diagramId);	
	}
	@Override
	public void restartBlock(ProjectResourceId diagramId, String blockName) {
		ProcessDiagram diagram = controller.getDiagram(diagramId);
		if( diagram!=null) {
			ProcessBlock block = diagram.getBlockByName(blockName);
			if( block!=null ) {
				block.stop();
				block.start();
			}
			else log.warnf("%s.restartBlock: block %s not found on diagram %s",CLSS,blockName,diagram.getName());
		}
		else {
			log.warnf("%s.restartBlock: no diagram found for %s",CLSS,diagramId);
		}
	}
	@Override
	public boolean resourceExists(ProjectResourceId resid) {
		ProcessDiagram diagram = controller.getDiagram(resid);
		return diagram!=null;
	}


	@Override
	public boolean sendLocalSignal(ProjectResourceId diagramId, String command, String message, String argument) {
		return sendTimestampedSignal( diagramId, command, message, argument,new Date().getTime());
	}
	
	/**
	 * We wrap a signal into a Qualified value and send it to a particular block.
	 * @param diagramId diagram identifier
	 * @param blockName name of the subject block
	 * @param command parameter of the signal
	 * @param message parameter of the signal (optional)
	 * @return
	 */
	@Override
	public boolean sendSignal(ProjectResourceId diagramId,String blockName,String command,String message) {
		boolean success = Boolean.TRUE;
		ProcessDiagram diagram = BlockExecutionController.getInstance().getDiagram(diagramId);
		if( diagram!=null ) {
			// Create an output notification
			Signal sig = new Signal(command,message,"");
			BroadcastNotification broadcast = new BroadcastNotification(diagram.getResourceId(),blockName,new BasicQualifiedValue(sig));
			BlockExecutionController.getInstance().acceptBroadcastNotification(broadcast);
		}
		else {
			log.warnf("%s.sendSignal: Unable to find diagram %s for %s command to %s",CLSS,diagramId,command,blockName);
			success = Boolean.FALSE;
		}
		return success;
	}
	@Override
	public boolean sendTimestampedSignal(ProjectResourceId diagramId, String command, String message, String argument,long time) {
		boolean success = Boolean.TRUE;
		ProcessDiagram diagram = BlockExecutionController.getInstance().getDiagram(diagramId);
		if( diagram!=null ) {
			// Create a broadcast notification
			log.warnf("%s.sendTimestampedSignal: Sending a signal to diagram %s for %s command",CLSS,diagramId,command);
			Signal sig = new Signal(command,message,argument);
			BroadcastNotification broadcast = new BroadcastNotification(diagram.getResourceId(),TransmissionScope.LOCAL,
					                              new BasicQualifiedValue(sig,QualityCode.Good,new Date(time)));
			BlockExecutionController.getInstance().acceptBroadcastNotification(broadcast);
		}
		else {
			log.warnf("%s.sendTimestampedSignal: Unable to find diagcram %s for %s command",CLSS,diagramId,command);
			success = Boolean.FALSE;
		}
		return success;
	}
	/**
	 * Set the values of named properties in a block. This method ignores any binding that the
	 * property may have and sets the value directly. Theoretically the value should be of the right
	 * type for the property, but if not, it can be expected to be coerced into the proper data type 
 	 * upon receipt by the block. The quality is assumed to be Good.
	 * 
	 * @param parentId diagram identifier
	 * @param blockId id of the target block
	 * @param properties a collection of properties that may have changed
	 */
	public void setBlockProperties(ProjectResourceId parentId, UUID blockId, Collection<BlockProperty> properties) {
		ProcessDiagram diagram = controller.getDiagram(parentId);
		ProcessBlock block = null;
		if( diagram!=null ) block = diagram.getProcessBlock(blockId);
		if(block!=null) {
			for( BlockProperty property:properties ) {
				BlockProperty existingProperty = block.getProperty(property.getName());
				setBlockProperty(parentId,blockId,property);
			}
		}
	}

	/**
	 * Set the value of a named property in a block. This method ignores any binding that the
	 * property may have and sets the value directly. Theoretically the value should be of the right
	 * type for the property, but if not, it can be expected to be coerced into the proper data type 
 	 * upon receipt by the block. The quality is assumed to be Good.
	 * 
	 * @param parentId diagram Id
	 * @param blockId Id of the target block
	 * @param property the newly changed or added block property
	 */
	public void setBlockProperty(ProjectResourceId parentId, UUID blockId, BlockProperty property) {

		ProcessDiagram diagram = controller.getDiagram(parentId);
		ProcessBlock block = null;
		if( diagram!=null ) block = diagram.getProcessBlock(blockId);
		if(block!=null) {
			BlockProperty existingProperty = block.getProperty(property.getName());
			if( existingProperty!=null ) {
				// Update the property, notify the designer
				updateProperty(diagram.getState(),block,existingProperty,property);
			}
			else {
				log.warnf("%s.setBlockProperty: Property %s not found in block %s", CLSS,property.getName(),block.getName());
			}
		}
	}

	/** Change the binding value of a block property in such a way that the block and UI
	 * are notified of the change.
	 *  
	 * @param diagramId diagram's unique Id as a String (not used)
	 * @param blockId block Id as a String
	 * @param pname the changed property
	 * @param binding the new binding value of the property. The value will be a tagpath as a String.
	 */
	public void setBlockPropertyBinding(ProjectResourceId diagramId,String blockId,String pname,String binding )  {
		controller.sendPropertyBindingNotification(blockId, pname, binding);
	}
	
	/** Change the value of a block property in such a way that the block and UI
	 * are notified of the change.
	 *  
	 * @param diagramId diagram's unique Id as a String
	 * @param bname block name
	 * @param pname the changed property
	 * @param value the new value of the property. The value will be coerced into the correct data type in the gateway 
	 */
	@Override
	public void setBlockPropertyValue(ProjectResourceId diagramId,String bname,String pname,String value )  {
		ProcessDiagram diagram = controller.getDiagram(diagramId);
		if( diagram!=null ) {
			ProcessBlock block = diagram.getBlockByName(bname);
			if( block!=null ) {
				BlockProperty prop = block.getProperty(pname);
				if( prop!=null ) {
					Object oldValue = prop.getValue();	
					BlockPropertyChangeEvent bpe = new BlockPropertyChangeEvent(bname,pname,oldValue,value);	
					block.propertyChange(bpe);
					controller.sendPropertyNotification(block.getBlockId().toString(),pname,new BasicQualifiedValue(value));
				}
				else{
					log.warnf("%s.setBlockPropertyValue: Unable to find property %s in block %s:%s",CLSS,pname,diagramId,bname,diagram.getName());
				}
			}
			else{
				log.warnf("%s.setBlockPropertyValue: Unable to find block %s in diagram %s",CLSS,diagramId,bname,diagram.getName());
			}
		}
		else{
			log.warnf("%s.setBlockPropertyValue: Unable to find diagram %s for block %s",CLSS,diagramId,bname);
		}
	}
	@Override
	public void setBlockState(ProjectResourceId diagramId,String bname,String stateName ) {
		ProcessDiagram diagram = null;
		diagram = controller.getDiagram(diagramId);
		if( diagram!=null ) {
			ProcessBlock block = diagram.getBlockByName(bname);
			if( block!=null ) {
				try {
					if(DEBUG) log.infof("%s.setBlockState: Setting %s on %s to %s", CLSS, bname, diagramId.toString(), stateName);
					TruthValue state = TruthValue.valueOf(stateName.toUpperCase());
					block.setState(state);
					block.notifyOfStatus();
				}
				catch(IllegalArgumentException iae) {
					log.warnf("%s.setBlockState: State %s in block %s:%s is not a legal truth value",CLSS,stateName,bname,diagram.getName());
				}
			}
			else{
				log.warnf("%s.setBlockState: Unable to find block %s in diagram %s",CLSS,diagramId,bname,diagram.getName());
			}
		}
		else{
			log.warnf("%s.setBlockState: Unable to find diagram %s for block %s",CLSS,diagramId,bname);
		}
	}
	
	/**
	 * The gateway context must be specified before the instance is useful.
	 * @param cntx the GatewayContext
	 */
	public void setContext(GatewayContext cntx) {
		this.context = cntx;
		toolkitProjectRecordHandler = new ToolkitProjectRecordHandler(context);
		toolkitRecordHandler = new ToolkitRecordHandler(context);
		tagHandler = new TagFactory(context);
		toolkitRecordHandler = new ToolkitRecordHandler(context); 
	}

	/**
	 * Set the state of the specified diagram. 
	 * @param diagramId UUID of the diagram
	 * @param state to which the diagram is to be set
	 */
	@Override
	public void setDiagramState(ProjectResourceId diagramId,String state) {
		ProcessDiagram diagram = controller.getDiagram(diagramId);
		if( diagram!=null && state!=null ) {
			try {
				DiagramState ds = DiagramState.valueOf(state.toUpperCase());
				diagram.setState(ds);
			}
			catch( IllegalArgumentException iae) {
				log.warnf("%s.setDiagramState: Unrecognized state(%s) sent to %s (%s)",CLSS,state,diagram.getName());
			}
		}
	}

	/**
	 * Tell the testing timer about the difference between test time
	 * and current time. Apply this automatically to the test timer
	 * @param offset the difference between test time and current time
	 *        ~ msecs. A positive number implies that the test time is
	 *        in the past.
	 */
	public void setTestTimeOffset(long offset) {
		AcceleratedWatchdogTimer timer = controller.getSecondaryTimer();
		timer.setTestTimeOffset(offset);
	}
	
	public void setTimeFactor(Double factor) {
		AcceleratedWatchdogTimer timer = controller.getSecondaryTimer();
		timer.setFactor(factor.doubleValue());
	}


	/**
	 * Set a project property in the ORM database.
	 */
	@Override
	public void setProjectToolkitProperty(String projectName,String propertyName, String value) {
		toolkitProjectRecordHandler.setToolkitProjectProperty(projectName,propertyName, value);
	}
	/**
	 * Set a property in the ORM database.
	 */
	@Override
	public void setToolkitProperty(String propertyName, String value) {
		toolkitRecordHandler.setToolkitProperty(propertyName, value);
	}
	/**
	 * Define a watermark for a diagram. 
	 */
	public void setWatermark(ProjectResourceId diagramId,String text) {
		controller.sendWatermarkNotification(diagramId,text);
	}
	
	public void startController() {
		BlockExecutionController.getInstance().start(context);
	}
	@Override
	public void stopController() {
		BlockExecutionController.getInstance().stop(context);
	}

	/**
	 * Direct blocks in all diagrams to report their status for a UI update.
	 */
	@Override
	public void triggerStatusNotifications(String projectName) {
		BlockExecutionController.getInstance().triggerStatusNotifications(projectName);
		log.debugf("%s.triggerStatusNotifications: Complete.",CLSS);
	}


	/** Change the properties of anchors for a block. 
	 * @param diagramId the uniqueId of the parent diagram
	 * @param blockId the uniqueId of the block
	 * @param anchorUpdates the complete anchor list for the block.
	 */
	@Override
	public void updateBlockAnchors(ProjectResourceId diagramId,String blockId, Collection<SerializableAnchor> anchorUpdates) {
		ProcessDiagram diagram = controller.getDiagram(diagramId);
		ProcessBlock block = null;
		UUID blockUUID = UUID.fromString(blockId);
		if( diagram!=null ) block = diagram.getProcessBlock(blockUUID);
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


	private UUID makeUUID(String name) {
		UUID uuid = null;
		try {
			uuid = UUID.fromString(name);
		}
		catch(IllegalArgumentException iae) {
			uuid = UUID.nameUUIDFromBytes(name.getBytes());
		}
		return uuid;
	}


	// Handle all the intricacies of a property change
	private void updateProperty(DiagramState ds,ProcessBlock block,BlockProperty existingProperty,BlockProperty newProperty) {
		if( !existingProperty.isEditable() )  return;
		
		log.debugf("%s.updateProperty old: %s, new:%s",CLSS,existingProperty.toString(),newProperty.toString());
		if( !existingProperty.getBindingType().equals(newProperty.getBindingType()) ) {
			// If the binding has changed - fix subscriptions.
			controller.removeSubscription(block, existingProperty);
			existingProperty.setBindingType(newProperty.getBindingType());
			existingProperty.setBinding(newProperty.getBinding());
			controller.startSubscription(ds,block,newProperty);
			// If the new binding is a tag write - do the write.
			if( !block.isLocked() && 
				(newProperty.getBindingType().equals(BindingType.TAG_READWRITE) ||
				 newProperty.getBindingType().equals(BindingType.TAG_WRITE))	   ) {
					controller.updateTag(block.getParentId(),newProperty.getBinding(), new BasicQualifiedValue(newProperty.getValue()));
			}	
		}
		else if( !existingProperty.getBinding().equals(newProperty.getBinding()) ) {
			// Same type, new binding target.
			controller.removeSubscription(block, existingProperty);
			existingProperty.setBinding(newProperty.getBinding());
			controller.startSubscription(ds,block,newProperty);
			// If the new binding is a tag write - do the write.
			if( !block.isLocked() && 
					(newProperty.getBindingType().equals(BindingType.TAG_READWRITE) ||
				     newProperty.getBindingType().equals(BindingType.TAG_WRITE))	   ) {
				controller.updateTag(block.getParentId(),newProperty.getBinding(), new BasicQualifiedValue(newProperty.getValue()));
			}	
		}
		else {
			// The event came explicitly from the designer/client. Send event whether it changed or not.
			if( existingProperty.getBindingType().equals(BindingType.NONE) && newProperty.getValue()!=null   )   {
				log.debugf("%s.setProperty sending event ...",CLSS);
				BlockPropertyChangeEvent event = new BlockPropertyChangeEvent(block.getBlockId().toString(),newProperty.getName(),
						existingProperty.getValue(),newProperty.getValue());
				block.propertyChange(event);
			}
		}
		// inform the designer of the change
		controller.sendPropertyNotification(block.getBlockId().toString(), newProperty.getName(), new BasicQualifiedValue(newProperty.getValue()));
	}
	/**
	 * Execute the setAux extension function in Gateway scope for the supplied resource.
	 * No notifications are sent
	 * Send notification to the designer of any changes.
	 * @param projectId the project
	 * @param resid the resourceId of an application to be refreshed
	 * @param provider tag provider
	 * @param db datasource
	 */
	@Override
	public synchronized void writeAuxData(ProjectResourceId resid,String nodeId,GeneralPurposeDataContainer container,String provider,String db) {
		Optional<RuntimeProject> op = context.getProjectManager().getProject(resid.getProjectName());
		Project proj = op.get();
		proj.getResource(resid);
		Optional<ProjectResource> optres = proj.getResource(resid);
		ProjectResource res = optres.get();
		if( res!=null && res.getResourceType().equals(BLTProperties.DIAGRAM_RESOURCE_TYPE)) {
			ProcessBlock block = controller.getProcessBlock(resid, nodeId);
			block.setAuxData(container);
		}
	}
}

