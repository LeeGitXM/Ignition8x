/**
 *   (c) 2013-2014  ILS Automation. All rights reserved.
 */
package com.ils.blt.designer.editor;

import java.awt.BorderLayout;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.ils.blt.designer.workspace.DiagramWorkspace;
import com.ils.blt.designer.workspace.ProcessBlockView;
import com.inductiveautomation.ignition.common.util.LogUtil;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.designer.blockandconnector.BlockComponent;
import com.inductiveautomation.ignition.designer.blockandconnector.BlockDesignableContainer;
import com.inductiveautomation.ignition.designer.blockandconnector.model.Connection;
import com.inductiveautomation.ignition.designer.designable.DesignableWorkspaceAdapter;
import com.inductiveautomation.ignition.designer.gui.IconUtil;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import com.inductiveautomation.ignition.designer.model.ResourceWorkspaceFrame;
import com.jidesoft.docking.DockableFrame;

/**
 * A PropertyEditorFrame is a DockableFrame in the JIDE workspace (lower left) meant
 * to hold an editor for properties of the selected block or connection. 
 */
@SuppressWarnings("serial")
public class PropertyEditorFrame extends DockableFrame implements ResourceWorkspaceFrame{
	private static final String TAG = "PropertyEditorFrame";
	public static final String DOCKING_KEY = "ProcessDiagramEditorFrame";
	public static final String TITLE = "Symbolic AI Property Editor";
	public static final String SHORT_TITLE = "Properties";
	private final DesignerContext context;
	private final DiagramWorkspace workspace;
	private final JPanel contentPanel;
	private BlockPropertyEditor editor = null;
	private LoggerEx log = LogUtil.getLogger(getClass().getPackage().getName());
	
	/**
	 * Constructor 
	 */
	public PropertyEditorFrame(DesignerContext ctx,DiagramWorkspace workspace) {
		super(DOCKING_KEY, IconUtil.getRootIcon("delay_block_16.png"));  // Pinned icon
		this.context = ctx;
		log.debugf("%s.PropertyEditorFrame: CONSTRUCTOR ...",TAG);
		this.workspace = workspace;
		workspace.addDesignableWorkspaceListener(new DiagramWorkspaceListener());
		contentPanel = new JPanel(new BorderLayout());
		init();	
	}


	@Override
	public String getKey() {
		return DOCKING_KEY;
	}

	/** 
	 * Initialize the UI components. The "master" version of the block's
	 * properties resides in the gateway.
	 */
	private void init() {
		setTitle(TITLE);
		setTabTitle(SHORT_TITLE);
		setSideTitle(SHORT_TITLE);
		
		setContentPane(contentPanel);
		contentPanel.setBorder(BorderFactory.createEtchedBorder());
	}
	

	@Override
	public boolean isInitiallyVisible() {
		return true;
	}
	
	public BlockPropertyEditor getEditor() {
		return editor;
	}

	public void updateForFinalDiagnosis() {
		if( editor!=null ) editor.shutdown();
		editor = new BlockPropertyEditor(context,workspace,editor.getBlock());
		contentPanel.removeAll();
		//Create a scroll pane
	    JScrollPane scrollPane = new JScrollPane(editor);
		contentPanel.add(scrollPane,BorderLayout.CENTER);
		validate();
	}


	private class DiagramWorkspaceListener extends DesignableWorkspaceAdapter {
		@Override
		public void itemSelectionChanged(List<JComponent> selections) {
			if( selections!=null && selections.size()==1 ) {
				JComponent selection = selections.get(0);
				log.debugf("%s: DiagramWorkspaceListener: selected a %s",TAG,selection.getClass().getName());
				if( selection instanceof BlockComponent ) {
					BlockComponent bc = ( BlockComponent)selection;
					ProcessBlockView blk = (ProcessBlockView)bc.getBlock();
					if( editor!=null ) editor.shutdown();
					editor = new BlockPropertyEditor(context,workspace,blk);
					contentPanel.removeAll();
					//Create a scroll pane
				    JScrollPane scrollPane = new JScrollPane(editor);
					contentPanel.add(scrollPane,BorderLayout.CENTER);
					validate();
					return;
				}
				// There may be a connection selected
				else {
					BlockDesignableContainer container = ( BlockDesignableContainer)selection;
					Connection cxn = container.getSelectedConnection();
					if( cxn!=null && cxn.getOrigin()!=null && cxn.getTerminus()!= null ) {
						// The block is a ProcessBlockView.
						// The origin is a BasicAnchorPoint (extends AnchorPoint).
						log.debugf("%s: DiagramWorkspaceListener: connection origin is a %s",TAG,cxn.getOrigin().getClass().getName());
						log.debugf("%s: DiagramWorkspaceListener: connection id is a %s",TAG,cxn.getOrigin().getId().getClass().getName());
						log.debugf("%s: DiagramWorkspaceListener: connection block is a %s",TAG,cxn.getOrigin().getBlock().getClass().getName());
						ConnectionPropertyEditor editr = new ConnectionPropertyEditor(context,cxn);
						contentPanel.removeAll();
						//Create a scroll pane
					    JScrollPane scrollPane = new JScrollPane(editr);
						contentPanel.add(scrollPane,BorderLayout.CENTER);
						return;
					}
				}
			}
		}
	}
	
}
