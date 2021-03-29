package com.ils.blt.designer.editor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.text.NumberFormat;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import com.ils.blt.common.UtilityFunctions;
import com.ils.common.GeneralPurposeDataContainer;
import com.ils.common.log.ILSLogger;
import com.ils.common.log.LogMaker;

import net.miginfocom.swing.MigLayout;

/**
 * Handle editing Quant Outputs for an application. This panel handles details for 
 * a single named output.
 */
public class OutputEditorPane extends JPanel implements FocusListener  {
	private static final long serialVersionUID = -5387165467458025431L;
	private final static String CLSS = "OutputEditorPane";
	private static final Insets insets = new Insets(0,0,0,0);
	private final ApplicationPropertyEditor editor;
	private final JPanel mainPanel;
	private final GeneralPurposeDataContainer model;
	private Map<String,String> outputMap;
	private final ILSLogger log;
	final JTextField nameField = new JTextField();
	final JTextField tagField = new JTextField();
	final JFormattedTextField mostNegativeIncrementField = new JFormattedTextField(NumberFormat.getInstance());
	final JFormattedTextField mostPositiveIncrementField = new JFormattedTextField(NumberFormat.getInstance());
	final JFormattedTextField minimumIncrementField = new JFormattedTextField(NumberFormat.getInstance());
	final JFormattedTextField setpointHighLimitField = new JFormattedTextField(NumberFormat.getInstance());
	final JFormattedTextField setpointLowLimitField = new JFormattedTextField(NumberFormat.getInstance());
	final JCheckBox incrementalOutputCheckBox = new JCheckBox();
	final JComboBox<String> feedbackMethodComboBox = new JComboBox<String>();
	private static Icon previousIcon = new ImageIcon(OutputEditorPane.class.getResource("/images/arrow_left_green.png"));
	final JButton previousButton = new JButton(previousIcon);
	private static Icon tagBrowserIcon = new ImageIcon(OutputEditorPane.class.getResource("/images/arrow_right_green.png"));
	final JButton nextButton = new JButton("Tags", tagBrowserIcon);
	private final UtilityFunctions fcns = new UtilityFunctions();
	
	protected static final Dimension TEXT_FIELD_SIZE  = new Dimension(120,24);
	protected static final Dimension NUMERIC_FIELD_SIZE  = new Dimension(100,24);
	
	// The constructor
	public OutputEditorPane(ApplicationPropertyEditor editor) {
		super(new BorderLayout(20, 30));
		this.editor = editor;
		this.log = LogMaker.getLogger(this);
		this.model = editor.getModel();
				
		//mainPanel = new JPanel(new MigLayout("", "[right]"));
		mainPanel = new JPanel(new MigLayout());
		add(mainPanel,BorderLayout.CENTER);

		JLabel label = new JLabel("Quant Output Editor");
		label.setHorizontalAlignment(SwingConstants.CENTER);
		mainPanel.add(label, BorderLayout.NORTH);
		
		mainPanel.add(new JLabel("Name:"), "gap 10,gaptop 10");
		nameField.setPreferredSize(TEXT_FIELD_SIZE);
		nameField.setToolTipText("The name of the Quant Output.");
		nameField.addFocusListener(this);
		mainPanel.add(nameField, "span, growx, wrap");

		mainPanel.add(new JLabel("Tag:"), "gap 10");		
		tagField.setPreferredSize(TEXT_FIELD_SIZE);
		tagField.setToolTipText("The name of the OPC tag that corresponds to this quant output.");
		tagField.addFocusListener(this);
		mainPanel.add(tagField, "growx");
		
		mainPanel.add(nextButton,"right, wrap");
		nextButton.setHorizontalTextPosition(SwingConstants.LEFT);
		nextButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {doTagSelector();}			
		});

		// Configure the Feedback method combo box
		mainPanel.add(new JLabel("Feedback Method:"), "gap 10");
		List<String> feedbackMethods = model.getLists().get("FeedbackMethods");
		if( feedbackMethods!=null ) {
			for(String feedbackMethod : feedbackMethods) {
				feedbackMethodComboBox.addItem(feedbackMethod);
			}
		}
		feedbackMethodComboBox.setToolTipText("The technique used to combine multiple recommendations for the this output!");
		feedbackMethodComboBox.setPreferredSize(ApplicationPropertyEditor.COMBO_SIZE);
		feedbackMethodComboBox.addFocusListener(this);
		mainPanel.add(feedbackMethodComboBox, "span, growx, wrap");
		
		mainPanel.add(new JLabel("Incremental Output:"), "gap 10");
		mainPanel.add(incrementalOutputCheckBox, "wrap, align left");
		
		JPanel incrementalContainer = new JPanel(new MigLayout("", "[right,110][]", ""));
		incrementalContainer.setBorder(BorderFactory.createTitledBorder("Incremental Changes"));
		
		incrementalContainer.add(new JLabel("Most Negative:"), "gap 10");
		mostNegativeIncrementField.setPreferredSize(NUMERIC_FIELD_SIZE);
		mostNegativeIncrementField.setToolTipText("The largest negative change.");
		mostNegativeIncrementField.setFocusLostBehavior(JFormattedTextField.COMMIT_OR_REVERT);
		mostNegativeIncrementField.addFocusListener(this);
		incrementalContainer.add(mostNegativeIncrementField, "span, growx");

		incrementalContainer.add(new JLabel("Most Positive:"), "gap 10");
		mostPositiveIncrementField.setPreferredSize(NUMERIC_FIELD_SIZE);
		mostPositiveIncrementField.setToolTipText("The largest positive change.");
		mostPositiveIncrementField.setFocusLostBehavior(JFormattedTextField.COMMIT_OR_REVERT);
		mostPositiveIncrementField.addFocusListener(this);
		incrementalContainer.add(mostPositiveIncrementField, "span, growx");
		
		incrementalContainer.add(new JLabel("Min Change:"), "gap 10");
		minimumIncrementField.setPreferredSize(NUMERIC_FIELD_SIZE);
		minimumIncrementField.setToolTipText("The minimum absolute change.");
		minimumIncrementField.setFocusLostBehavior(JFormattedTextField.COMMIT_OR_REVERT);
		minimumIncrementField.addFocusListener(this);
		incrementalContainer.add(minimumIncrementField, "span, growx");

		mainPanel.add(incrementalContainer, "span, growx, wrap");
		
		JPanel absoluteContainer = new JPanel(new MigLayout("", "[right,110][]", ""));
		absoluteContainer.setBorder(BorderFactory.createTitledBorder("Absolute Changes"));
		
		absoluteContainer.add(new JLabel("Low Limit:"), "gap 10");
		setpointLowLimitField.setPreferredSize(NUMERIC_FIELD_SIZE);
		setpointLowLimitField.setToolTipText("The absolute lower limit.");
		setpointLowLimitField.setFocusLostBehavior(JFormattedTextField.COMMIT_OR_REVERT);
		setpointLowLimitField.addFocusListener(this);
		absoluteContainer.add(setpointLowLimitField, "span, growx");
		
		absoluteContainer.add(new JLabel("High Limit:"), "gap 10");
		setpointHighLimitField.setPreferredSize(NUMERIC_FIELD_SIZE);
		setpointHighLimitField.setToolTipText("The absolute upper limit.");
		setpointHighLimitField.setFocusLostBehavior(JFormattedTextField.COMMIT_OR_REVERT);
		setpointHighLimitField.addFocusListener(this);
		absoluteContainer.add(setpointHighLimitField, "span, growx");
		
		mainPanel.add(absoluteContainer, "span, growx, wrap");
		
		// Now the buttons that go at the bottom
		JPanel bottomPanel = new JPanel(new MigLayout("","[25%, left][50%, center][25%]",""));
		mainPanel.add(bottomPanel, "span");
		bottomPanel.add(previousButton);
		previousButton.setPreferredSize(ApplicationPropertyEditor.BUTTON_SIZE);
		previousButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {save(); editor.saveResource();}
		});
	}

	/**
	 * Called from the OutputsPane to indicate which output to edit.
	 * @param map
	 */
	public void updateFields(Map<String,String> map){
		outputMap=map;
		nameField.setText((String) outputMap.get("QuantOutput"));
		tagField.setText((String) outputMap.get("TagPath"));
		incrementalOutputCheckBox.setSelected(fcns.coerceToBoolean(outputMap.get("IncrementalOutput")));
		double dbl = fcns.coerceToDouble(outputMap.get("MostNegativeIncrement"));
		mostNegativeIncrementField.setValue(dbl);
		dbl = fcns.coerceToDouble(outputMap.get("MostPositiveIncrement"));
		mostPositiveIncrementField.setValue(dbl);
		dbl = fcns.coerceToDouble(outputMap.get("MinimumIncrement"));
		minimumIncrementField.setValue(dbl);
		dbl = fcns.coerceToDouble(outputMap.get("SetpointLowLimit"));
		setpointLowLimitField.setValue(dbl);
		dbl = fcns.coerceToDouble(outputMap.get("SetpointHighLimit"));
		setpointHighLimitField.setValue(dbl);
		feedbackMethodComboBox.setSelectedItem((String) outputMap.get("FeedbackMethod"));
	}
	
	public JTextField getTagField() { return this.tagField; }

	// The user exited a field, so save everything (I don't keep track of what, if anything, was 
	// changed.
	protected void save() {
		
		// Update the outputMap with everything in the screen
		outputMap.put("QuantOutput", nameField.getText());
		outputMap.put("TagPath", tagField.getText());
		outputMap.put("IncrementalOutput", (incrementalOutputCheckBox.isSelected()?"1":"0"));
		outputMap.put("MostNegativeIncrement", mostNegativeIncrementField.getText());
		outputMap.put("MostPositiveIncrement", mostPositiveIncrementField.getText());
		outputMap.put("MinimumIncrement", minimumIncrementField.getText());
		outputMap.put("SetpointLowLimit", setpointLowLimitField.getText());
		outputMap.put("SetpointHighLimit", setpointHighLimitField.getText());
		if(feedbackMethodComboBox.getSelectedItem()!=null) {
			outputMap.put("FeedbackMethod", feedbackMethodComboBox.getSelectedItem().toString());
		}

		log.infof("%s.doPrevious: Saving values %s\n ",CLSS,outputMap.toString());
		
		editor.refreshOutputs();

		// Slide back to the Outputs pane
		editor.setSelectedPane(ApplicationPropertyEditor.OUTPUTS);	
	}
	
	protected void doTagSelector() {
		editor.setSelectedPane(ApplicationPropertyEditor.TAGSELECTOR);	
	}


	public void activate() {
		editor.setSelectedPane(ApplicationPropertyEditor.EDITOR);
	}
	// ============================================== Focus listener ==========================================
	@Override
	public void focusGained(FocusEvent event) {
	}
	@Override
	public void focusLost(FocusEvent event) {
		save();
		editor.saveResource();
	}
}