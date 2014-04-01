/**
 *   (c) 2012-2013  ILS Automation. All rights reserved. 
 */
package com.ils.blt.gateway.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ils.block.ProcessBlock;
import com.ils.block.common.BlockProperty;
import com.ils.blt.common.BLTProperties;
import com.ils.blt.common.serializable.SerializableApplication;
import com.ils.blt.common.serializable.SerializableDiagram;
import com.ils.blt.common.serializable.SerializableFamily;
import com.ils.connection.Connection;
import com.inductiveautomation.ignition.common.project.Project;
import com.inductiveautomation.ignition.common.project.ProjectResource;
import com.inductiveautomation.ignition.common.project.ProjectVersion;
import com.inductiveautomation.ignition.common.util.LogUtil;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.project.ProjectListener;

/**
 * The model manager keeps track of the gateway version of model resources. On startup
 * and whenever a resource change takes place, the manager analyzes the resources
 * and extracts diagram and block information. This information is relayed to the
 * block manager via a passed-in controller instance.
 * 
 * In addition, the model manager keeps a gateway representation of the NavTree.
 * This is used by Designer/Client scripts to find/access model components. 
 * 
 * NOTE: The project listener interface only triggers when the user selects 
 *       "Save and Publish".  We provide separate entry points for application
 *       startup and for the user selecting "Save" from the Designer.
 *
 */
public class ModelManager implements ProjectListener  {
	
	private static String TAG = "ModelResourceManager";
	private final GatewayContext context;
	private final LoggerEx log;
	/** Access nodes by either UUID or tree path */
	private final RootNode root;
	private final Map<ProjResKey,ProcessDiagram> diagramsByKey;  // Diagrams only
	private final Map<UUID,ProcessNode> orphansByUUID;
	private final Map<UUID,ProcessNode> nodesByUUID;
	
	
	/**
	 * Initially we query the gateway context to discover what resources exists. After that
	 * we rely on notifications of project resource updates. After discovering block resources
	 * we deserialize and inform the BlockExecutionController.
	 * 
	 * @param cntx the gateway context. 
	 */
	public ModelManager(GatewayContext ctx) { 
		this.context = ctx;
		log = LogUtil.getLogger(getClass().getPackage().getName());
		
		diagramsByKey = new HashMap<ProjResKey,ProcessDiagram>();
		orphansByUUID = new HashMap<UUID,ProcessNode>();
		nodesByUUID = new HashMap<UUID,ProcessNode>();
		root = new RootNode(context);
	}
	
	
	
	/**
	 * Analyze a project resource for its embedded object. If, appropriate, add
	 * to the engine. Handle both additions and updates.
	 * @param projectId the identity of a project
	 * @param res the model resource
	 */
	public void analyzeResource(Long projectId,ProjectResource res) {
		String type = res.getResourceType();
		if( type.equalsIgnoreCase(BLTProperties.APPLICATION_RESOURCE_TYPE) ) {
			addApplicationResource(projectId,res);
		}
		else if( type.equalsIgnoreCase(BLTProperties.FAMILY_RESOURCE_TYPE) ) {
			addFamilyResource(projectId,res);
		}
		else if( type.equalsIgnoreCase(BLTProperties.DIAGRAM_RESOURCE_TYPE) ) {
			addDiagramResource(projectId,res);	
		}
		else if( type.equalsIgnoreCase(BLTProperties.FOLDER_RESOURCE_TYPE) ) {
			addFolderResource(projectId,res);
		}
		else {
			// Don't care
			log.tracef("%s.analyze: Ignoring %s resource",TAG,type);
		}
		
	}
	/**
	 * Get a block from an existing diagram. 
	 * @param projectId
	 * @param resourceId
	 * @param blockId identifier of the block.
	 * @return the specified ProcessBlock. If not found, return null. 
	 */
	public ProcessBlock getBlock(long projectId,long resourceId,UUID blockId) {
		ProcessBlock block = null;
		ProcessDiagram dm = getDiagram(projectId,resourceId);
		if( dm!=null ) {
			block = dm.getBlock(blockId);
		}
		return block;
	}
	
	/**
	 * Get a connection from the existing diagrams. 
	 * @param projectId
	 * @param resourceId
	 * @param connectionId
	 * @return the specified Connection. If not found, return null. 
	 */
	public Connection getConnection(long projectId,long resourceId,String connectionId) {
		Connection cxn = null;
		ProcessDiagram dm = getDiagram(projectId,resourceId);
		if( dm!=null ) {
			cxn = dm.getConnection(connectionId);
		}
		return cxn;
	}
	/**
	 * Get a specified diagram by its Id. 
	 * @param diagramId

	 * @return the specified diagram. If not found, return null. 
	 */
	public ProcessDiagram getDiagram(UUID diagramId) {
		ProcessDiagram diagram = null;
		ProcessNode node = nodesByUUID.get(diagramId);
		if( node instanceof ProcessDiagram ) diagram = (ProcessDiagram)node;
		return diagram;
	}
	
	/**
	 * Get a specified diagram given projectId and resourceId. 
	 * @param projectId
	 * @param resourceId

	 * @return the specified diagram. If not found, return null. 
	 */
	public ProcessDiagram getDiagram(long projectId,long resourceId) {
		ProjResKey key = new ProjResKey(projectId,resourceId);
		ProcessDiagram diagram = diagramsByKey.get(key);
		return diagram;
	}
	
	/**
	 * Get a specified diagram by project name and tree path. Useful for a query from the UI. 
	 * @param projectName
	 * @param treePath path to the diagram from the navigation tree in the Designer
	 * @return the specified diagram. If not found, return null. 
	 */
	public ProcessDiagram getDiagram(String projectName,String treePath) {
		ProcessDiagram diagram = null;
		ProcessNode node = root.findNode(projectName,treePath);
		if( node!=null && node instanceof ProcessDiagram) diagram = (ProcessDiagram)node;
		return diagram;
	}
	
	/**
	 * Get a list of diagram tree paths known to the specified project. 
	 * @param projectName 
	 * @return a list of diagram tree paths. If none found, return null. 
	 */
	public List<String> getDiagramTreePaths(String projectName) {
		List<String> result = new ArrayList<String>();
		// First obtain a list of diagrams by recursively descending the tree
		Long projectId = context.getProjectManager().getProjectId(projectName);
		if( projectId!=null) {
			List<ProcessNode> nodes = root.nodesForProject(projectId);
			// For each diagram discovered, create a tree path.
			for(ProcessNode node:nodes) {
				if( node instanceof ProcessDiagram ) {
					String path = node.getTreePath(nodesByUUID);
					result.add(path);
				}
			}
		}
		else {
			log.warnf("%s.getDiagramTreePaths: Project %s not found", TAG,projectName);
		}
		
		
		return result;	
	}
	
	// ====================== Project Listener Interface ================================
	/**
	 * We don't care if the new project is a staging or published version.
	 * Analyze either project resources and update the controller.
	 */
	@Override
	public void projectAdded(Project staging, Project published) {
		if( staging!=null ) {
			long projectId = published.getId();
			log.debugf("%s: projectAdded - published project %d", TAG,projectId);
			List<ProjectResource> resources = published.getResources();
			for( ProjectResource res:resources ) {
				if( res.getResourceType().equalsIgnoreCase(BLTProperties.DIAGRAM_RESOURCE_TYPE)) {
					log.debugf("%s: projectAdded - published %s %d = %s", TAG,res.getName(),
						res.getResourceId(),res.getResourceType());
					analyzeResource(projectId,res);
					
				}
			}
		}
	}
	/**
	 * Assume that the project resources are already gone. This is a cleanup step.
	 */
	@Override
	public void projectDeleted(long projectId) {
		deleteProjectResources(new Long(projectId));
		
	}
	/**
	 * Handle project resource updates of type model.
	 * @param diff represents differences to the updated project. That is any updated, dirty or deleted resources.
	 * @param vers a value of "Staging" means is a result of a "Save". A value of "Published" occurs when a 
	 *        project is published. For our purposes both actions are equivalent.
	 */
	/* (non-Javadoc)
	 * @see com.inductiveautomation.ignition.gateway.project.ProjectListener#projectUpdated(com.inductiveautomation.ignition.common.project.Project, com.inductiveautomation.ignition.common.project.ProjectVersion)
	 */
	@Override
	public void projectUpdated(Project diff, ProjectVersion vers) { 
		log.infof("%s: projectUpdated - version = %s", TAG,vers.toString());
		if( vers==ProjectVersion.Published ) return;  // Consider only the "Staging" version
		
		long projectId = diff.getId();
		Set<Long> deleted = diff.getDeletedResources();
		for (Long  resid : deleted) {
			log.infof("%s: projectUpdated -delete resource %d:%d", TAG,projectId,resid);
			deleteResource(projectId,resid);
		}
		
		log.debugf("%s: projectUpdated - res of type model = %d", TAG,diff.getResourcesOfType(BLTProperties.MODULE_ID, BLTProperties.DIAGRAM_RESOURCE_TYPE).size());
		List<ProjectResource> resources = diff.getResourcesOfType(BLTProperties.MODULE_ID, BLTProperties.DIAGRAM_RESOURCE_TYPE);
		for( ProjectResource res:resources ) {
			log.infof("%s: projectUpdated -  %s %d = %s (%s)", TAG,res.getName(),
					res.getResourceId(),res.getResourceType(),(diff.isResourceDirty(res)?"dirty":"clean"));
			analyzeResource(projectId,res);
		}
	}
	
	// ===================================== Private Methods ==========================================
	/**
	 * Add or update an application in the model from a ProjectResource.
	 * This is essentially just a tree node.
	 * @param projectId the identity of a project
	 * @param res the project resource containing the diagram
	 */
	private void addApplicationResource(Long projectId,ProjectResource res) {
		ProcessApplication application = deserializeApplicationResource(projectId,res);
		if( application!=null ) {
			UUID self = application.getSelf();
			ProcessNode node = nodesByUUID.get(self);
			if( node==null ) {
				node = new ProcessApplication(res.getName(),res.getParentUuid(),self);
				addToHierarchy(projectId,node);
				resolveOrphans(node);
			}
			else {
				// The only attribute to update is the name
				node.setName(res.getName());
			}
		}
	}
	/**
	 * Add or update a diagram in the model from a ProjectResource.
	 * There is a one-one correspondence 
	 * between a model-project and diagram.
	 * @param projectId the identity of a project
	 * @param res the project resource containing the diagram
	 */
	private void addDiagramResource(Long projectId,ProjectResource res) {
		ProcessDiagram diagram = deserializeDiagramResource(projectId,res);
		if( diagram!=null) {
			BlockExecutionController controller = BlockExecutionController.getInstance();
			// If this is an existing diagram, we need to remove the old version
			ProcessDiagram oldDiagram = (ProcessDiagram)nodesByUUID.get(diagram.getSelf());
			if( oldDiagram!=null ) {
				nodesByUUID.remove(diagram.getSelf());
				// Remove old subscriptions
				for( ProcessBlock pb:diagram.getProcessBlocks()) {
					for(BlockProperty bp:pb.getProperties()) {
						controller.stopSubscription(pb,bp);
					}
				}
			}
			// Now add in the new Diagram
			ProjResKey key = new ProjResKey(projectId,res.getResourceId());
			diagramsByKey.put(key,diagram);
			nodesByUUID.put(diagram.getSelf(),diagram);
			log.infof("%s: addDiagramResource: starting tag subscriptions ...%d:%s",TAG,projectId,res.getName());
			for( ProcessBlock pb:diagram.getProcessBlocks()) {
				for(BlockProperty bp:pb.getProperties()) {
					controller.startSubscription(pb,bp);
				}
			}
		}
		else {
			log.warnf("%s.addDiagramResource - Failed to create diagram from resource (%s)",TAG,res.getName());
		}
	}
	/**
	 * Add or update an application in the model from a ProjectResource.
	 * This is essentially just a tree node.
	 * @param projectId the identity of a project
	 * @param res the project resource containing the diagram
	 */
	private void addFamilyResource(Long projectId,ProjectResource res) {
		ProcessFamily family = deserializeFamilyResource(projectId,res);
		if( family!=null ) {
			UUID self = family.getSelf();
			ProcessNode node = nodesByUUID.get(self);
			if( node==null ) {
				node = new ProcessFamily(res.getName(),res.getParentUuid(),self);
				addToHierarchy(projectId,node);
				resolveOrphans(node);
			}
			else {
				// The only attribute to update is the name
				node.setName(res.getName());
			}
		}
	}
	/**
	 * Add or update a folder resource from the ProjectResource.
	 * @param projectId the identity of a project
	 * @param resourceId the identity of the model resource
	 * @param model the diagram logic
	 */
	private void addFolderResource(long projectId,ProjectResource res) {
		UUID self = res.getDataAsUUID();
		ProcessNode node = nodesByUUID.get(self);
		if( node==null ) {
			node = new ProcessNode(res.getName(),res.getParentUuid(),self);
			addToHierarchy(projectId,node);
			resolveOrphans(node);
		}
		else {
			// The only attribute to update is the name
			node.setName(res.getName());
		}
	}
	
	/**
	 * Add a process node to our hierarchy.
	 * @param projectId the identity of a project
	 * @param node the node to be added
	 */
	private void addToHierarchy(long projectId,ProcessNode node) {
		UUID self     = node.getSelf();
		nodesByUUID.put(self, node);
		
		// If the parent is null, then we're the top of the chain for our project
		// Add the node to the root.
		if( node.getParent()==null )  {
			
			root.addChild(node,projectId);
		}
		else {
			// If the parent is already in the tree, simply add the node as a child
			// Otherwise add to our list of orphans
			ProcessNode parent = nodesByUUID.get(node.getParent());
			if(parent==null ) {
				orphansByUUID.put(self, node);
			}
			else {
				parent.addChild(node);
			}
		}	
	}
	/**
	 * Remove a diagram within a project.
	 * Presumably the diagram has been deleted.
	 * @param projectId the identity of a project.
	 */
	private void deleteResource(Long projectId,Long resourceId) {
		ProjResKey key = new ProjResKey(projectId.longValue(),resourceId.longValue());
		ProcessDiagram diagram = diagramsByKey.get(key);
		if( diagram==null ) return;    // Nothing to do
		diagramsByKey.remove(key);
		nodesByUUID.remove(diagram.getSelf());
		BlockExecutionController controller = BlockExecutionController.getInstance();
		for(ProcessBlock block:diagram.getProcessBlocks()) {
			for(BlockProperty prop:block.getProperties()) {
				controller.stopSubscription(block, prop);
			}
		}
		
		if( diagram.getParent()!=null ) {
			ProcessNode parent = nodesByUUID.get(diagram.getParent());
			if( parent!=null ) parent.removeChild(diagram);
		}
		
	}
	// Delete all process nodes for a given project.
	private void deleteProjectResources(Long projectId) {
		log.infof("%s.deleteProjectResources: proj = %d",TAG,projectId);
		List<ProcessNode> nodes = root.nodesForProject(projectId);
		BlockExecutionController controller = BlockExecutionController.getInstance();
		for(ProcessNode node:nodes) {
			if( node instanceof ProcessNode ) {
				ProcessDiagram diagram = (ProcessDiagram)node;
				for(ProcessBlock block:diagram.getProcessBlocks()) {
					for(BlockProperty prop:block.getProperties()) {
						controller.stopSubscription(block, prop);
					}
				}
				ProjResKey key = new ProjResKey(projectId.longValue(),diagram.getResourceId());
				diagramsByKey.remove(key);
			}
			nodesByUUID.remove(node.getSelf());
		}
		root.removeProject(projectId);
	}
	
	/**
	 * We've discovered a changed model resource. Deserialize and convert into a ProcessFamily.
	 * @param projId the identifier of the project
	 * @param res
	 */ 
	private ProcessApplication deserializeApplicationResource(long projId,ProjectResource res) {
		byte[] serializedObj = res.getData();
		String json = new String(serializedObj);
		log.infof("%s.deserializeApplicationResource: json = %s",TAG,json);
		ProcessApplication application = null;
		try{
			ObjectMapper mapper = new ObjectMapper();
			SerializableApplication sa = mapper.readValue(json, SerializableApplication.class);
			if( sa!=null ) {
				log.infof("%s.deserializeApplicationResource: successfully deserialized diagram %s",TAG,sa.getName());
				application = new ProcessApplication(sa,res.getParentUuid());
			}
			else {
				log.warnf("%s.deserializeApplicationResource: deserialization failed",TAG);
			}
		}
		// Print stack trace
		catch( Exception ex) {
			log.warnf("%s.deserializeApplicationResource: exception (%s)",TAG,ex.getLocalizedMessage(),ex);
		}
		return application;
	}
	/**
	 *  We've discovered a changed model resource. Deserialize and convert into a ProcessDiagram.
	 *  Note: We had difficulty with the Ignition XML serializer because it didn't handle Java generics;
	 *        thus the use of JSON. The returned object was not an instanceof...
	 * @param projId the identifier of the project
	 * @param res
	 */ 
	private ProcessDiagram deserializeDiagramResource(long projId,ProjectResource res) {
		byte[] serializedObj = res.getData();
		String json = new String(serializedObj);
		log.infof("%s.deserializeDiagramResource: json = %s",TAG,json);
		ProcessDiagram diagram = null;
		try{
			ObjectMapper mapper = new ObjectMapper();
			SerializableDiagram sd = mapper.readValue(json, SerializableDiagram.class);
			if( sd!=null ) {
				sd.setResourceId(res.getResourceId());
				log.infof("%s.deserializeDiagramResource: successfully deserialized diagram %s",TAG,sd.getName());
				diagram = new ProcessDiagram(sd,res.getParentUuid());
			}
			else {
				log.warnf("%s.deserializeDiagramResource: deserialization failed",TAG);
			}
		}
		// Print stack trace
		catch( Exception ex) {
			log.warnf("%s.deserializeDiagramResource: exception (%s)",TAG,ex.getLocalizedMessage(),ex);
		}
		return diagram;

	}
	
	/**
	 * We've discovered a changed model resource. Deserialize and convert into a ProcessFamily.
	 * @param projId the identifier of the project
	 * @param res
	 */ 
	private ProcessFamily deserializeFamilyResource(long projId,ProjectResource res) {
		byte[] serializedObj = res.getData();
		String json = new String(serializedObj);
		log.infof("%s.deserializeFamilyResource: json = %s",TAG,json);
		ProcessFamily family = null;
		try{
			ObjectMapper mapper = new ObjectMapper();
			SerializableFamily sf = mapper.readValue(json, SerializableFamily.class);
			if( sf!=null ) {
				log.infof("%s.deserializeModelResource: successfully deserialized diagram %s",TAG,sf.getName());
				family = new ProcessFamily(sf,res.getParentUuid());
			}
			else {
				log.warnf("%s: deserializeFamilyResource: deserialization failed",TAG);
			}
		}
		// Print stack trace
		catch( Exception ex) {
			log.warnf("%s.deserializeFamilyResource: exception (%s)",TAG,ex.getLocalizedMessage(),ex);
		}
		return family;
	}
	/**
	 * Traverse the node tree from the given node looking for orphans
	 * that have since been resolved. The parent is non-null, since
	 * any nodes at the root are automatically added to hierarchy.
	 * @param node
	 */
	private void resolveOrphans(ProcessNode node) {
		if(orphansByUUID.get(node.getSelf())==null) return;   // Was not an orphan
		ProcessNode parent = nodesByUUID.get(node.getParent());
		// If is now resolved, remove node from orhpan list and
		// add as child of parent. Recurse it's children.
		if(parent!=null ) {
			parent.addChild(node);
			orphansByUUID.remove(node.getSelf());
			// Now make sure children are also linked ...
			for(ProcessNode child:node.getChildren()) {
				resolveOrphans(child);
			}
		}
	}
	

	
	// ====================================== ProjectResourceKey =================================
	/**
	 * Class for keyed storage by projectId, resourceId
	 */
	private class ProjResKey {
		private final long projectId;
		private final long resourceId;
		public ProjResKey(long projid,long resid) {
			this.projectId = projid;
			this.resourceId = resid;
		}
		public long getProjectId() { return projectId; }
		public long getResourceId() { return resourceId; }
		
		// So that class may be used as a map key
		// Same projectId and resourceId is sufficient to prove equality
		@Override
		public boolean equals(Object arg) {
			boolean result = false;
			if( arg instanceof ProjResKey) {
				ProjResKey that = (ProjResKey)arg;
				if( (this.getProjectId()==that.getProjectId()) &&
					(this.getResourceId()==that.getResourceId())   ) {
					result = true;
				}
			}
			return result;
		}
		@Override
		public int hashCode() {
			return (int)(this.projectId*100000+this.resourceId);
		}
	}
}
