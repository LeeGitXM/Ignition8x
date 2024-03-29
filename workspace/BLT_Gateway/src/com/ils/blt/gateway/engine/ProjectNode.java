/**
 *   (c) 2014  ILS Automation. All rights reserved. 
 */
package com.ils.blt.gateway.engine;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import com.inductiveautomation.ignition.common.project.ProjectVersion;

/**
 * A project node is a construction solely for the use of the status panel
 * browser. It is a way to create a full tree from the root node.
 * A project node is identified by the projectId. Its parent is the root node.
 */
public class ProjectNode extends ProcessNode {
	private static final long serialVersionUID = 6280701183405134254L;
	private final RootNode root;
	
	/**
	 * Constructor: 
	 * @param rootNode the root node
	 * @param me UUID of this node 
	 * @param projId the project
	 */
	public ProjectNode(RootNode rootNode, UUID me, long projId) { 
		super("",rootNode.self,me);
		this.projectId = projId;
		this.root = rootNode;
		setName(root.context.getProjectManager().getProjectName(projectId, ProjectVersion.Staging));
	}

	public void addChild(ProjectNode child)    { 
		throw new UnsupportedOperationException();
	}

	public Collection<ProcessNode> getChildren() { 
		return root.allNodesForProject(new Long(projectId)); 
	}


	public void setProjectId(long projectId) {
		throw new UnsupportedOperationException();
	}
	/**
	 * @return the UUID of this node
	 */
	public UUID getSelf() { return this.self; }

	/**
	 * Normally the tree path does not include the project, so is not appropriate here.
	 * @return
	 */
	@Override
	public String getTreePath(Map<UUID,ProcessNode> nodesByUUID) {
		throw new UnsupportedOperationException();
	} 
	@Override
	public void removeChild(ProcessNode child) { 
		throw new UnsupportedOperationException();
	} 
}
