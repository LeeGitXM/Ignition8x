/**
 *   (c) 2014  ILS Automation. All rights reserved.
 */
package com.ils.blt.designer.editor;

import java.util.ArrayList;
import java.util.List;

import com.ils.blt.common.ApplicationRequestHandler;
import com.ils.blt.common.DiagramState;
import com.ils.blt.common.block.BlockConstants;
import com.ils.blt.common.block.BlockProperty;
import com.ils.blt.designer.workspace.DiagramWorkspace;
import com.ils.blt.designer.workspace.ProcessBlockView;
import com.ils.blt.designer.workspace.ProcessDiagramView;
import com.ils.common.persistence.ToolkitProperties;
import com.ils.common.tag.TagUtility;
import com.inductiveautomation.ignition.client.util.gui.SlidingPane;
import com.inductiveautomation.ignition.designer.blockandconnector.BlockDesignableContainer;
import com.inductiveautomation.ignition.designer.model.DesignerContext;


/**
 * This is the controller for the frame that contains various sliding panels
 * used for editing various block attributes.    
 */

public class BlockPropertyEditor extends SlidingPane   {
	private static final long serialVersionUID = 8971626415423709616L;

	private final DesignerContext context;
	private final DiagramWorkspace workspace;
	private final ProcessDiagramView diagram;
	private final ProcessBlockView block;
	private final ApplicationRequestHandler requestHandler;
	private static final List<String> coreAttributeNames;
	
	private final MainPanel          mainPanel;       		// display the properties for a block
	private final ConfigurationPanel configPanel;     		// configure a single block property
	private final ListEditPanel      listEditPanel;   		// configure a property that is a list of strings
	private final NameEditPanel      nameEditPanel;   		// configure a block's name
	private final TagBrowserPanel    tagPanel;        		// configure tag for a bound value
	private final FinalDiagnosisPanel finalDiagnosisPanel;	// Special case editor for FinalDiagnosis 
	private final SourceMainPanel     sourceMainPanel;		// Special case editor for SourceConnection 
	private final SourceEditPanel     sourceEditPanel;		// configure an editor for lists of source blocks
	
	static {
		// These are the attributes handled in the CorePropertyPanel
		coreAttributeNames = new ArrayList<String>();
		coreAttributeNames.add("class");
	}
	
	/**
	 * @param view the designer version of the block to edit. We 
	 */
	public BlockPropertyEditor(DesignerContext ctx,DiagramWorkspace wksp,ProcessBlockView view) {
		this.context = ctx;
		this.requestHandler = new ApplicationRequestHandler();
		this.workspace = wksp;
		this.diagram = wksp.getActiveDiagram();
		this.block = view;
        this.mainPanel = new MainPanel(context,this,block, wksp);
        this.configPanel = new ConfigurationPanel(this);
        this.listEditPanel = new ListEditPanel(this);
        this.nameEditPanel = new NameEditPanel(this);
        this.tagPanel = new TagBrowserPanel(context,this);
        this.finalDiagnosisPanel = new FinalDiagnosisPanel(context,this,block, wksp);
        this.sourceMainPanel = new SourceMainPanel(context,this,block, wksp);
        this.sourceEditPanel = new SourceEditPanel(this);
        init();    
	}

	public ApplicationRequestHandler getRequestHandler() { return this.requestHandler; }
	
	/** 
	 * Create the various panels. We keep one of each type.
	 */
	private void init() {
		if (block.getClassName().toLowerCase().contains(".finaldiagnosis")) {
			add(finalDiagnosisPanel);            // HOME_PANEL
		} 
		else if (block.getClassName().equals(BlockConstants.BLOCK_CLASS_SOURCE)) {
			sourceMainPanel.initialize();
			add(sourceMainPanel);            // HOME_PANEL
		} 
		else {
			mainPanel.initialize();
			add(mainPanel);                       // HOME_PANEL
		}
		add(configPanel);                     // CONFIGURATION_PANEL
		add(listEditPanel);                   // LIST_EDIT_PANEL
		add(nameEditPanel);                   // NAME_EDIT_PANEL
		add(tagPanel);                        // TAG_BROWSER_PANEL
		add(sourceEditPanel);                 // SOURCE_EDIT_PANEL
		setSelectedPane(BlockEditConstants.HOME_PANEL);
	}
	public ProcessBlockView getBlock() { return this.block; }
	public ProcessDiagramView getDiagram() { return this.diagram; }
	

	public DesignerContext getContext() { return this.context; }
	/**
	 * Changing the name is non-structural. If the diagram is not
	 * dirty for structural reasons, then we go ahead and save the
	 * project resource.
	 */
	public void saveDiagram() {
		if( !diagram.isDirty()) {
			BlockDesignableContainer tab = (BlockDesignableContainer)workspace.findDesignableContainer(diagram.getResourceId());
			if( tab!=null )  workspace.saveDiagramResource(tab);
		}
	}
	/**
	 * Save a diagram that is not the current.
	 */
	public void saveDiagram(long resid) {
		BlockDesignableContainer tab = (BlockDesignableContainer)workspace.findDesignableContainer(resid);
		if( tab!=null )  workspace.saveDiagramResource(tab);
	}
	
	/**
	 * One of the edit panels has modified a block property. Update the
	 * running diagram directly. Do not mark the diagram as "dirty" since
	 * we've only changed a block property. Save the project resource.
	 */
	public void saveDiagramClean() {
		saveDiagram();
		diagram.setDirty(false);
	}
	/**
	 * Modify a tag path to account for global production/isolation providers
	 * as well as the current state of the diagram.
	 * @param path
	 * @return the modified path.
	 */
	public String modifyPathForProvider(String path) {
		String tagPath = path;
		if( path!=null && !path.isEmpty() ) {
			if( diagram.getState().equals(DiagramState.ISOLATED)) {
				String provider = requestHandler.getToolkitProperty(ToolkitProperties.TOOLKIT_PROPERTY_ISOLATION_PROVIDER);
				tagPath = TagUtility.replaceProviderInPath(provider,path);
			}
			else {
				String provider = requestHandler.getToolkitProperty(ToolkitProperties.TOOLKIT_PROPERTY_PROVIDER);
				tagPath = TagUtility.replaceProviderInPath(provider,path);
			}
		}
		return tagPath;
	}
	
	/**
	 * Un-subscribe to notifications to allow cleanup. These are all 
	 * done on the main panel.
	 */
	public void shutdown() {
		mainPanel.shutdown();
	}
	// Update the displayed value in the main panel
	public void updatePanelValue(String propertyName,Object val) {
		if (block.getClassName().equals(BlockConstants.BLOCK_CLASS_SOURCE)) {
			sourceMainPanel.updatePanelValue(propertyName,val);
		} 
		else {
			mainPanel.updatePanelValue(propertyName,val);
		}
	}
	
	public void updateCorePanel(int panelIndex,ProcessBlockView blk) {
		switch(panelIndex) {
		case BlockEditConstants.NAME_EDIT_PANEL:
			nameEditPanel.updateForBlock(blk);
			break;
		case BlockEditConstants.HOME_PANEL: 
			MainPanel mp = this.mainPanel;
			if( block.getClassName().equals(BlockConstants.BLOCK_CLASS_SOURCE)) mp = sourceMainPanel;
			mp.updateCorePanel(blk);
			break;
		case BlockEditConstants.CONFIGURATION_PANEL:
		default:
			break;
		}
	}
	
	public void updatePanelForProperty(int panelIndex,BlockProperty prop) {
		switch(panelIndex) {
		case BlockEditConstants.CONFIGURATION_PANEL:
			configPanel.updateForProperty(prop);
			break;
		case BlockEditConstants.LIST_EDIT_PANEL:
			listEditPanel.updateForProperty(prop);
			break;
		case BlockEditConstants.HOME_PANEL: 
			MainPanel mp = this.mainPanel;
			if( block.getClassName().equals(BlockConstants.BLOCK_CLASS_SOURCE)) mp = sourceMainPanel;
			mp.updatePanelForProperty(prop);
			break;
		case BlockEditConstants.TAG_BROWSER_PANEL:
			tagPanel.updateForProperty(prop);
			break;
		case BlockEditConstants.NAME_EDIT_PANEL:
		default:
			break;
		}
	}
}


