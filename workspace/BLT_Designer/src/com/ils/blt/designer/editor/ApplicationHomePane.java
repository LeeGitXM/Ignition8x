package com.ils.blt.designer.editor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.ils.blt.common.ApplicationRequestHandler;
import com.ils.blt.common.UtilityFunctions;
import com.ils.blt.common.notification.NotificationChangeListener;
import com.ils.blt.common.notification.NotificationKey;
import com.ils.blt.common.script.Script;
import com.ils.blt.common.script.ScriptConstants;
import com.ils.blt.common.script.ScriptExtensionManager;
import com.ils.blt.designer.NotificationHandler;
import com.ils.common.GeneralPurposeDataContainer;
import com.ils.common.log.ILSLogger;
import com.ils.common.log.LogMaker;
import com.ils.common.persistence.ToolkitProperties;
import com.inductiveautomation.ignition.common.model.values.QualifiedValue;

import net.miginfocom.swing.MigLayout;

public class ApplicationHomePane extends JPanel implements  NotificationChangeListener {
	private static String CLSS = "ApplicationHomePane";
	private final ScriptExtensionManager extensionManager = ScriptExtensionManager.getInstance();
	private final NotificationHandler notificationHandler = NotificationHandler.getInstance();
	private final ApplicationPropertyEditor editor;
	private final ApplicationRequestHandler requestHandler;
	private final GeneralPurposeDataContainer model;
	private static final long serialVersionUID = 2882399376824334427L;
	
	protected static final Dimension AREA_SIZE  = new Dimension(250,80);
	private final String key;
	private final JPanel mainPanel;
	private final JTextField nameField = new JTextField();
	private final JTextArea descriptionTextArea = new JTextArea();
	private final JComboBox<String> queueComboBox = new JComboBox<String>();
	private final JComboBox<String> groupRampMethodComboBox = new JComboBox<String>();
	private final JComboBox<String> unitComboBox = new JComboBox<String>();
	final JCheckBox managedCheckBox = new JCheckBox();
	
	private static Icon nextIcon = new ImageIcon(ApplicationHomePane.class.getResource("/images/arrow_right_green.png"));
	private final JButton nextButton = new JButton("Outputs", nextIcon);;
	private final UtilityFunctions fcns = new UtilityFunctions();
	protected final ILSLogger log;
	private final String provider;
	private final String database;

	// Don't add an Apply button because then I need to manage getting the id's of any quant outputs they create 
	// back from the extension manager.
	

	public ApplicationHomePane(ApplicationPropertyEditor editor) {
		super(new BorderLayout());
		this.editor = editor;
		this.requestHandler = new ApplicationRequestHandler();
		this.model = editor.getModel();
		this.log = LogMaker.getLogger(this);
		this.database = requestHandler.getProductionDatabase();
		this.provider = requestHandler.getProductionTagProvider();
		this.key = NotificationKey.keyForAuxData(editor.getApplication().getId().toString());
		
		mainPanel = new JPanel(new MigLayout());
		add(mainPanel,BorderLayout.CENTER);
		
		// Add components to the main panel
		mainPanel.add(new JLabel("Name:"),"align right");
		
		nameField.setPreferredSize(ApplicationPropertyEditor.COMBO_SIZE);
		nameField.setEditable(false);
		nameField.setToolTipText("The name can only be changed from the project tree.");
		mainPanel.add(nameField,"span,wrap");

		mainPanel.add(new JLabel("Description:"),"align right");
		descriptionTextArea.setEditable(true);
		descriptionTextArea.setLineWrap(true);
		descriptionTextArea.setWrapStyleWord(true);
		descriptionTextArea.setToolTipText("Optional description of this application");
		descriptionTextArea.setPreferredSize(AREA_SIZE);
		
		JScrollPane scrollPane = new JScrollPane(descriptionTextArea);
		
		mainPanel.add(scrollPane,"gaptop 2,aligny top,span,wrap");
		
		// Add the Managed check box
		mainPanel.add(new JLabel("Managed:"), "gap 10");
		mainPanel.add(managedCheckBox, "wrap, align left");

		// Create up the Message Queue Combo Box
		mainPanel.add(new JLabel("Queue:"), "align right");
		queueComboBox.setToolTipText("The message queue where messages for this application will be posted!");
		queueComboBox.setPreferredSize(ApplicationPropertyEditor.COMBO_SIZE);
		mainPanel.add(queueComboBox, "wrap");

		// Create up the Group Ramp Method Combo Box
		mainPanel.add(new JLabel("Ramp Method:"),"align right");
		groupRampMethodComboBox.setToolTipText("The Group Ramp Method that will be used for outputs in this application!");
		groupRampMethodComboBox.setPreferredSize(ApplicationPropertyEditor.COMBO_SIZE);
		mainPanel.add(groupRampMethodComboBox, "wrap");
		
		// Create the unit combo box
		mainPanel.add(new JLabel("Unit:"),"align right");
		unitComboBox.setToolTipText("The unit associated with this application!");
		unitComboBox.setPreferredSize(ApplicationPropertyEditor.COMBO_SIZE);
		mainPanel.add(unitComboBox, "wrap");
		//Script script = extensionManager.createExtensionScript(classKey, ScriptConstants.GET_LIST_OPERATION, toolkitHandler.getToolkitProperty(ToolkitProperties.TOOLKIT_PROPERTY_PROVIDER));
		//extensionManager.runScript(context.getProjectManager().getProjectScriptManager(node.getProjectId()), 
		//		script, node.getSelf().toString(),node.getAuxiliaryData());
		
		mainPanel.add(nextButton,"cell 1 13,center");
		nextButton.setHorizontalTextPosition(SwingConstants.LEFT);
		nextButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {doNext();}			
		});
		
		setUI();
		
		// Register for notifications - returns the current setting
		log.infof("%s: adding change listener %s",CLSS,key);
		notificationHandler.addNotificationChangeListener(key,CLSS,this);
	}

	// Fill widgets with current values
	private void setUI() {
		nameField.setText(editor.getApplication().getName());
		String description = model.getProperties().get("Description");
		if( description==null) description="";
		descriptionTextArea.setText(description);
		managedCheckBox.setSelected(fcns.coerceToBoolean( model.getProperties().get("Managed")));
		
		// Setup the Queue Combo box
		List<String> mqueues = model.getLists().get("MessageQueues");
		queueComboBox.removeAllItems();
		if(mqueues!=null ) {
			for(String q : mqueues) {
				queueComboBox.addItem(q);
			}
		}
		String queue = model.getProperties().get("MessageQueue");
		if( queue!=null ) queueComboBox.setSelectedItem(queue);
		else if( queueComboBox.getItemCount()>0) {
			queueComboBox.setSelectedIndex(0);
		}
		
		// Set up the method combo box
		List<String> methods = model.getLists().get("GroupRampMethods");
		groupRampMethodComboBox.removeAllItems();
		if( methods!=null ) {
			for(String o : methods) {
				groupRampMethodComboBox.addItem(o);
			}
		}
		String method = model.getProperties().get("GroupRampMethod");
		if( method!=null ) groupRampMethodComboBox.setSelectedItem(method);
		else if( groupRampMethodComboBox.getItemCount()>0) {
			groupRampMethodComboBox.setSelectedIndex(0);
		}
		
		// Set up the Unit Combo Box
		List<String> units = model.getLists().get("Units");
		unitComboBox.removeAllItems();
		if( units!=null ) {
			for(String o : units) {
				unitComboBox.addItem(o);
			}
		}
		String unit = model.getProperties().get("Unit");
		if( unit!=null ) unitComboBox.setSelectedItem(unit);
		else if( unitComboBox.getItemCount()>0) {
			unitComboBox.setSelectedIndex(0);
		}
	}
	
	protected void save(){
		// Set attributes from fields on this pane
		model.getProperties().put("Description",descriptionTextArea.getText());
		model.getProperties().put("MessageQueue",(String) queueComboBox.getSelectedItem());
		model.getProperties().put("GroupRampMethod",(String) groupRampMethodComboBox.getSelectedItem());
		model.getProperties().put("Unit",(String) unitComboBox.getSelectedItem());
		
		model.getProperties().put("Managed",(managedCheckBox.isSelected()?"1":"0"));
		editor.saveResource();
	}

	protected void doNext() {
		editor.setSelectedPane(ApplicationPropertyEditor.OUTPUTS);
	}
	public void shutdown() {
		log.infof("%s: removing change listener %s",CLSS,key);
		notificationHandler.removeNotificationChangeListener(key,CLSS);
		save();
	}

	// ======================================= Notification Change Listener ===================================
	@Override
	public void bindingChange(String pname,String binding) {}
	@Override
	public void diagramStateChange(long resId, String state) {}
	@Override
	public void nameChange(String name) {}
	@Override
	public void propertyChange(String pname,Object value) {}
	// The value is the aux data of the application. Note that the method is not
	// called on the Swing thread. It gets triggereed as soon as the panel is displayed.
	@Override
	public void valueChange(final QualifiedValue value) {
		if( value==null ) return;
		GeneralPurposeDataContainer container = (GeneralPurposeDataContainer)value.getValue();
		if( container==null) return;
		log.infof("%s.valueChange: new aux data %s for %s",CLSS,container.toString(),editor.getApplication().getName());
		model.setLists(container.getLists());
		model.setMapLists(container.getMapLists());
		model.setProperties(container.getProperties());
		SwingUtilities.invokeLater( new Runnable() {
			public void run() {
				setUI();
			}
		});
	}
	@Override
	public void watermarkChange(String mark) {}

}