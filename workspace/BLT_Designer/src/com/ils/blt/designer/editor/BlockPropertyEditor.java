/**
 *   (c) 2014  ILS Automation. All rights reserved.
 */
package com.ils.blt.designer.editor;

import java.util.ArrayList;
import java.util.List;

import com.ils.blt.common.ApplicationRequestHandler;
import com.ils.blt.common.BLTProperties;
import com.ils.blt.common.block.BindingType;
import com.ils.blt.common.block.BlockProperty;
import com.ils.blt.designer.BLTDesignerHook;
import com.ils.blt.designer.workspace.ProcessBlockView;
import com.inductiveautomation.ignition.client.util.gui.SlidingPane;
import com.inductiveautomation.ignition.designer.model.DesignerContext;


/**
 * This is the controller for the frame that contains various sliding panels
 * used for editing various block attributes.    
 */

public class BlockPropertyEditor extends SlidingPane   {
	private static final long serialVersionUID = 8971626415423709616L;

	private final ProcessBlockView block;
	private final DesignerContext context;
	private final long projectId;
	private final long resourceId;
	private static final List<String> coreAttributeNames;
	
	private final MainPanel          mainPanel;       // display the properties for a block
	private final ConfigurationPanel configPanel;     // configure a single block property
	private final EnumEditPanel      enumEditPanel;  // configure a single enumerated block property
	private final ListEditPanel      listEditPanel;   // configure a property that is a list of strings
	private final NameEditPanel      nameEditPanel;   // configure a block's name
	private final TagBrowserPanel    tagPanel;        // configure tag for a bound value
	private final ValueEditPanel     valueEditPanel;  // configure a single value block property
	
	
	
	static {
		// These are the attributes handled in the CorePropertyPanel
		coreAttributeNames = new ArrayList<String>();
		coreAttributeNames.add("class");
	}
	
	/**
	 * @param view the designer version of the block to edit. We 
	 */
	public BlockPropertyEditor(DesignerContext ctx,long res,ProcessBlockView view) {
		this.context = ctx;
		this.projectId = ctx.getProject().getId();
		this.resourceId = res;
		this.block = view;
        this.mainPanel = new MainPanel(this,block);
        this.configPanel = new ConfigurationPanel(this);
        this.enumEditPanel = new EnumEditPanel(this);
        this.listEditPanel = new ListEditPanel(this);
        this.nameEditPanel = new NameEditPanel(this);
        this.tagPanel = new TagBrowserPanel(this,context);
        this.valueEditPanel = new ValueEditPanel(this);
        init();    
	}

	
	/** 
	 * Create the various panels. We keep one of each type.
	 */
	private void init() {
		add(mainPanel);                       // HOME_PANEL
		add(configPanel);                     // CONFIGURATION_PANEL
		add(enumEditPanel);                   // ENUM_EDIT_PANEL
		add(listEditPanel);                   // LIST_EDIT_PANEL
		add(nameEditPanel);                   // NAME_EDIT_PANEL
		add(tagPanel);                        // TAG_BROWSER_PANEL
		add(valueEditPanel);                  // VALUE_EDIT_PANEL
		setSelectedPane(BlockEditConstants.HOME_PANEL);   
	}
	public ProcessBlockView getBlock() { return this.block; }
	public void updatePanelForBlock(int panelIndex,ProcessBlockView block) {
		switch(panelIndex) {
		case BlockEditConstants.NAME_EDIT_PANEL:
			nameEditPanel.updateForBlock(block);
			break;
		case BlockEditConstants.CONFIGURATION_PANEL:
		case BlockEditConstants.HOME_PANEL: 
		default:
			break;
		}
	}
	public void updatePanelForProperty(int panelIndex,BlockProperty prop) {
		switch(panelIndex) {
		case BlockEditConstants.CONFIGURATION_PANEL:
			configPanel.updateForProperty(prop);
			break;
		case BlockEditConstants.ENUM_EDIT_PANEL:
			enumEditPanel.updateForProperty(prop);
			break;
		case BlockEditConstants.LIST_EDIT_PANEL:
			listEditPanel.updateForProperty(prop);
			break;
		case BlockEditConstants.HOME_PANEL: 
			mainPanel.updatePanelForProperty(prop);
			break;
		case BlockEditConstants.TAG_BROWSER_PANEL:
			tagPanel.updateForProperty(prop);
			break;
		case BlockEditConstants.VALUE_EDIT_PANEL:
			valueEditPanel.updateForProperty(prop);
			break;
		case BlockEditConstants.NAME_EDIT_PANEL:
		default:
			break;
		}
	}
}


