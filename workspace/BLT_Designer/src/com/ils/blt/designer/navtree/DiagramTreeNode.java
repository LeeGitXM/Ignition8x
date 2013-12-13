/**
 *   (c) 2013  ILS Automation. All rights reserved.
 *  
 *  Based on sample code provided by Inductive Automation.
 */
package com.ils.blt.designer.navtree;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.UUID;

import javax.swing.JPopupMenu;
import javax.swing.tree.TreePath;

import com.ils.blt.common.BLTProperties;
import com.ils.blt.common.serializable.SerializableDiagram;
import com.ils.blt.designer.BLTDesignerHook;
import com.ils.blt.designer.workspace.DiagramWorkspace;
import com.inductiveautomation.ignition.client.util.action.BaseAction;
import com.inductiveautomation.ignition.client.util.gui.ErrorUtil;
import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.model.ApplicationScope;
import com.inductiveautomation.ignition.common.project.ProjectResource;
import com.inductiveautomation.ignition.common.util.LogUtil;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.common.xmlserialization.serialization.XMLSerializer;
import com.inductiveautomation.ignition.designer.UndoManager;
import com.inductiveautomation.ignition.designer.gui.IconUtil;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import com.inductiveautomation.ignition.designer.navtree.model.AbstractNavTreeNode;
import com.inductiveautomation.ignition.designer.navtree.model.AbstractResourceNavTreeNode;
import com.inductiveautomation.ignition.designer.navtree.model.FolderNode;
import com.inductiveautomation.ignition.designer.navtree.model.ResourceDeleteAction;
/**
 * A folder in the designer scope to support the diagnostics toolkit diagram
 * layout. The folder depth is two or three. Menu options vary depending on whether
 * this is the root node, or not. Labels depend on the depth.
 */
public class DiagramTreeNode extends FolderNode {
	private static final String TAG = "DiagnosticsFolderNode";
	private static final String NAV_PREFIX = "NavTree";       // Required for some defaults
	private static final String BUNDLE_NAME = "navtree";      // Name of properties file
	private static final int DIAGRAM_DEPTH = 2;                   // For a two-tier menu
	private final LoggerEx log = LogUtil.getLogger(getClass().getPackage().getName());
	// These are the various actions beyond defaults
	private DebugAction debugAction = null;
	private ApplicationAction applicationAction = null;
	private FamilyAction familyAction = null;
	protected DiagramAction diagramAction = null;
	private final DiagramWorkspace workspace;
	
	static {
		BundleUtil.get().addBundle(NAV_PREFIX,DiagramTreeNode.class,BUNDLE_NAME);
	}

	/** 
	 * Create a new folder node representing the root folder
	 * @param context the designer context
	 */
	public DiagramTreeNode(DesignerContext ctx) {
		super(ctx, BLTProperties.MODULE_ID, ApplicationScope.GATEWAY,BLTProperties.ROOT_FOLDER_UUID);
		workspace = ((BLTDesignerHook)ctx.getModule(BLTProperties.MODULE_ID)).getWorkspace();
		setText(BundleUtil.get().getString(NAV_PREFIX+".RootFolderName"));
		setIcon(IconUtil.getIcon("folder_closed"));
		log.info(TAG+"root:"+this.pathToRoot());	
	}

	/**
	 * This version of the constructor is used for all except the root. Create
	 * either a family container or a diagram holder.
	 * 
	 * NOTE: At this point the depth is unknown. We wait until setting edit actions
	 *       to actually define the depth-based actions.
	 * 
	 * @param context the designer context
	 * @param resource the project resource
	 */
	public DiagramTreeNode(DesignerContext context,ProjectResource resource) {
		super(context, resource);
		workspace = ((BLTDesignerHook)context.getModule(BLTProperties.MODULE_ID)).getWorkspace();
		setIcon(IconUtil.getIcon("folder_closed"));
	}

	private boolean isRootFolder() {
		return getFolderId().equals(BLTProperties.ROOT_FOLDER_UUID);
	}


	/**
	 * Create a child node because we've discovered a resource that matches this instance as a parent
	 * based on its content matching the our UUID.
	 */
	@Override
	protected AbstractNavTreeNode createChildNode(ProjectResource res) {
		log.debug(String.format("%s.createChildNode type:%s, level=%d", TAG,res.getResourceType(),getDepth()));
		AbstractNavTreeNode node = null;
		if (ProjectResource.FOLDER_RESOURCE_TYPE.equals(res.getResourceType())) {
			node = new DiagramTreeNode(context, res);
			log.debug(TAG+"createChildFolder:"+this.pathToRoot()+"->"+node.pathToRoot());
			return node;
		}
		else if (BLTProperties.MODEL_RESOURCE_TYPE.equals(res.getResourceType())) {
			node = new DiagramNode(context,res,workspace);
			log.debug(TAG+"createChildPanel:"+this.pathToRoot()+"->"+node.pathToRoot());
			return node;
		} 
		else {
			log.warnf("%s: Attempted to create a child of type %s (ignored)",TAG,res.getResourceType());
			throw new IllegalArgumentException();
		}
	}
	
	@Override
	public String getWorkspaceName() {
		return DiagramWorkspace.key;
	}
	
	@Override
	public boolean isEditActionHandler() {
		return isRootFolder();
	}
	/**
	 * Define the menu used for popups. This appears to be called only once for each node.
	 */
	@Override
	protected void initPopupMenu(JPopupMenu menu, TreePath[] paths,List<AbstractNavTreeNode> selection, int modifiers) {
		setupEditActions(paths, selection);
		
		if (isRootFolder()) { 
			applicationAction = new ApplicationAction(this.folderId);
			debugAction = new DebugAction();
			menu.add(applicationAction);
			menu.addSeparator();
			menu.add(debugAction);
		}
		else if( getDepth()==DIAGRAM_DEPTH) {
			diagramAction = new DiagramAction();
			menu.add(diagramAction);
			menu.addSeparator();
			addEditActions(menu);
			
		}
		else {   // Depth == 2 and DIAGRAM_DEPTH==3
			familyAction = new FamilyAction(this.folderId);
			menu.add(familyAction);
			menu.addSeparator();
			addEditActions(menu);
		}
	}
	/**
	 * Exclude cut and paste which are currently not supported.
	 */
	@Override
	protected void addEditActions(JPopupMenu menu)
    {
        menu.add(renameAction);
        menu.add(deleteAction);
    }
	
	private boolean siblings(List<AbstractNavTreeNode> nodes) {
		if (nodes == null || nodes.size() < 1) {
			return false;
		}
		int depth = nodes.get(0).getDepth();
		for (AbstractNavTreeNode node : nodes) {
			if (node.getDepth() != depth) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean canDelete(List<AbstractNavTreeNode> selectedChildren) {
		return isEditActionHandler() && siblings(selectedChildren);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void doDelete(List<? extends AbstractNavTreeNode> children,
			DeleteReason reason) {
		for (AbstractNavTreeNode node : children) {
			if (node instanceof DiagramNode) {
				((DiagramNode) node).closeAndCommit();
			}
		}

		ResourceDeleteAction delete = new ResourceDeleteAction(context,
				(List<AbstractResourceNavTreeNode>) children,
				reason.getActionWordKey(), (getDepth()==1? (NAV_PREFIX+".ApplicationNoun"):(NAV_PREFIX+".FamilyNoun")));
		if (delete.execute()) {
			UndoManager.getInstance().add(delete, DiagramTreeNode.class);
		}
	}

	@Override
	public void onSelected() {
		UndoManager.getInstance()
				.setSelectedContext(DiagramTreeNode.class);
	}
	
	
	// From the root node, recursively log the contents of the tree
	private class DebugAction extends BaseAction {
		private static final long serialVersionUID = 1L;
		public DebugAction()  {
			super(NAV_PREFIX+".Debug",IconUtil.getIcon("bug_yellow"));
		}

		public void actionPerformed(ActionEvent e) {
			log.info("=============================== Project Resources =========================");
			listAllResources();
			log.info("===========================================================================");
		}
	}
	// From the root node, create a folder for diagrams belonging to a family
	private class ApplicationAction extends BaseAction {
		private static final long serialVersionUID = 1L;
		private UUID parent;
	    public ApplicationAction(UUID parentUUID)  {
	    	super(NAV_PREFIX+".NewApplication",IconUtil.getIcon("folder_new"));
	    	this.parent = parentUUID;
	    }
	    
		public void actionPerformed(ActionEvent e) {
			try {
				final long newId = context.newResourceId();
				String newName = BundleUtil.get().getString(NAV_PREFIX+".DefaultNewApplicationName");
				if( newName==null) newName = "New Apps";  // Missing Resource
				context.addFolder(newId,moduleId,ApplicationScope.GATEWAY,newName,parent);
				selectChild(newId);
			} 
			catch (Exception err) {
				ErrorUtil.showError(err);
			}
		}
	}
	// From the root node, create a folder for diagrams belonging to a family
	private class FamilyAction extends BaseAction {
		private static final long serialVersionUID = 1L;
		private UUID parent;
	    public FamilyAction(UUID parentUUID)  {
	    	super(NAV_PREFIX+".NewFamily",IconUtil.getIcon("folder_new"));
	    	this.parent = parentUUID;
	    }
	    
		public void actionPerformed(ActionEvent e) {
			try {
				final long newId = context.newResourceId();
				String newName = BundleUtil.get().getString(NAV_PREFIX+".DefaultNewFamilyName");
				if( newName==null) newName = "New Folks";  // Missing Resource
				context.addFolder(newId,moduleId,ApplicationScope.GATEWAY,newName,parent);
				selectChild(newId);
			} 
			catch (Exception err) {
				ErrorUtil.showError(err);
			}
		}
	}
    private class DiagramAction extends BaseAction {
    	private static final long serialVersionUID = 1L;
	    public DiagramAction()  {
	    	super(NAV_PREFIX+".NewDiagram",IconUtil.getIcon("folder_new"));  // preferences
	    }
	    
		public void actionPerformed(ActionEvent e) {
			try {
				final long newId = context.newResourceId();
				String newName = BundleUtil.get().getString(NAV_PREFIX+".DefaultNewDiagramName");
				if( newName==null) newName = "New Diag";  // Missing string resource
				SerializableDiagram diagram = new SerializableDiagram();
				diagram.setName(newName);
				XMLSerializer serializer = context.createSerializer();
				serializer.addObject(diagram);
				byte[] bytes = serializer.serializeBinary(false);
				log.debugf("%s: DiagramAction. create new %s resource %d (%d bytes)",TAG,BLTProperties.MODEL_RESOURCE_TYPE,
						newId,bytes.length);
				ProjectResource resource = new ProjectResource(newId,
						BLTProperties.MODULE_ID, BLTProperties.MODEL_RESOURCE_TYPE,
						newName, ApplicationScope.GATEWAY, bytes);
				resource.setParentUuid(getFolderId());
				context.updateResource(resource);
				selectChild(newId);
				EventQueue.invokeLater(new Runnable() {
					public void run() {
						workspace.open(newId);
					}
				});
			} 
			catch (Exception err) {
				ErrorUtil.showError(err);
			}
		}
	}
    
    /**
	 * Search the project for all resources. This is for debugging.
	 */
	public void listAllResources() {
		List <ProjectResource> resources = context.getProject().getResources();
		for( ProjectResource res : resources ) {
			log.info("Res: "+res.getResourceId()+" "+res.getResourceType()+" "+res.getModuleId()+" ("+res.getName()+
					":"+res.getParentUuid()+")");
		}
	}
	
	private byte[] longsToByteArray(long most,long least) {
		byte[] byteArray = new byte[16];
	    int i = 0;
	    while (i < 16)
	    {
	      int j;
	      if (i == 0)
	        j = (int)most >>> 32;
	      else if (i == 4)
	        j = (int)most;
	      else if (i == 8)
	        j = (int)least >>> 32;
	      else
	        j = (int)least;
	      byteArray[(i++)] = ((byte)(j >>> 24));
	      byteArray[(i++)] = ((byte)(j >>> 16));
	      byteArray[(i++)] = ((byte)(j >>> 8));
	      byteArray[(i++)] = ((byte)j);
	    }
	    return byteArray;
	}
}
