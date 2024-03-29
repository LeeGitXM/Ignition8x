/**
 *   (c) 2013-2016  ILS Automation. All rights reserved. 
 */
package com.ils.block;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ils.block.annotation.ExecutableBlock;
import com.ils.blt.common.BLTProperties;
import com.ils.blt.common.DiagnosticDiagram;
import com.ils.blt.common.ProcessBlock;
import com.ils.blt.common.block.Activity;
import com.ils.blt.common.block.AnchorDirection;
import com.ils.blt.common.block.AnchorPrototype;
import com.ils.blt.common.block.BlockConstants;
import com.ils.blt.common.block.BlockDescriptor;
import com.ils.blt.common.block.BlockProperty;
import com.ils.blt.common.block.BlockStyle;
import com.ils.blt.common.block.LimitType;
import com.ils.blt.common.block.PlacementHint;
import com.ils.blt.common.block.PropertyType;
import com.ils.blt.common.block.TruthValue;
import com.ils.blt.common.connection.ConnectionType;
import com.ils.blt.common.control.ExecutionController;
import com.ils.blt.common.notification.BlockPropertyChangeEvent;
import com.ils.blt.common.notification.IncomingNotification;
import com.ils.blt.common.notification.OutgoingNotification;
import com.ils.blt.common.notification.Signal;
import com.ils.blt.common.notification.SignalNotification;
import com.ils.blt.common.serializable.SerializableBlockStateDescriptor;
import com.ils.common.FixedSizeQueue;
import com.ils.common.watchdog.TestAwareQualifiedValue;
import com.inductiveautomation.ignition.common.model.values.BasicQualifiedValue;
import com.inductiveautomation.ignition.common.model.values.QualifiedValue;
import com.inductiveautomation.ignition.common.model.values.Quality;

/**
 * This class applies one of the Westinghouse (Western Electric) SPC rules to its input.
 * If the scan interval is zero, then the application runs in an event-driven mode.
 * Otherwise, a timing loop is created and the input (the last value to have arrived) is
 * evaluated on loop timeout. The output is TRUE if there is a rule violation.
 */
@ExecutableBlock
public class SQC extends AbstractProcessBlock implements ProcessBlock {
	private final static boolean DEBUG = false;
	protected static final String BLOCK_PROPERTY_MINIMUM_OUT_OF_RANGE = "MinimumOutOfRange";
	protected static final String BLOCK_PROPERTY_SQC_LIMIT = "NumberOfStandardDeviations";
	protected static final String BLOCK_PROPERTY_TEST_LABEL = "TestLabel";
	protected static final String PORT_STANDARD_DEVIATION = "standardDeviation";
	protected static final String PORT_TARGET = "target";
	protected static final String PORT_VALUE = "value";
	private final static int DEFAULT_BUFFER_SIZE = 10;
		
	private boolean clearOnReset = true;
	private double limit = 3.0;
	private LimitType limitType = LimitType.HIGH;
	private FixedSizeQueue<Double> queue;
	private int sampleSize = DEFAULT_BUFFER_SIZE;
	private int minOut = sampleSize;  // Min out-of-range to conclude TRUE
	private int outside  = 0; // current number of observations out of limits.
	private int total  = 0;  // current number of observations in buffer.
	private double standardDeviation = Double.NaN;
	private double mean = Double.NaN;

	/**
	 * Constructor: The no-arg constructor is used when creating a prototype for use in the palette.
	 */
	public SQC() {
		queue = new FixedSizeQueue<Double>(DEFAULT_BUFFER_SIZE);
		initialize();
		initializePrototype();
	}
	
	/**
	 * Constructor. Custom properties are limit, standardDeviation
	 * 
	 * @param ec execution controller for handling block output
	 * @param parent universally unique Id identifying the parent of this block
	 * @param block universally unique Id for the block
	 */
	public SQC(ExecutionController ec,UUID parent,UUID block) {
		super(ec,parent,block);
		queue = new FixedSizeQueue<Double>(DEFAULT_BUFFER_SIZE);
		initialize();
		
		BlockDescriptor desc = prototype.getBlockDescriptor();
		desc.setEditorClass("com.ils.blt.designer.config.SQCDiagnosisConfiguration");
	}
	
	/**
	 * Add properties that are new for this class.
	 * Populate them with default values.
	 */
	private void initialize() {	
		setName("SQC");
//		this.setReceiver(true);
//		this.setTransmitter(true);
		BlockProperty clearProperty = new BlockProperty(BlockConstants.BLOCK_PROPERTY_CLEAR_ON_RESET,new Boolean(clearOnReset),PropertyType.BOOLEAN,true);
		setProperty(BlockConstants.BLOCK_PROPERTY_CLEAR_ON_RESET, clearProperty);
		BlockProperty limitProperty = new BlockProperty(BLOCK_PROPERTY_SQC_LIMIT,new Double(limit),PropertyType.DOUBLE,true);
		setProperty(BLOCK_PROPERTY_SQC_LIMIT, limitProperty);
		BlockProperty limitTypeProperty = new BlockProperty(BlockConstants.BLOCK_PROPERTY_LIMIT_TYPE,new String(limitType.name()),PropertyType.LIMIT,true);
		setProperty(BlockConstants.BLOCK_PROPERTY_LIMIT_TYPE, limitTypeProperty);
		BlockProperty maxOutProperty = new BlockProperty(BLOCK_PROPERTY_MINIMUM_OUT_OF_RANGE,new Integer(minOut),PropertyType.INTEGER,true);
		setProperty(BLOCK_PROPERTY_MINIMUM_OUT_OF_RANGE, maxOutProperty);
		BlockProperty sizeProperty = new BlockProperty(BlockConstants.BLOCK_PROPERTY_SAMPLE_SIZE,new Integer(DEFAULT_BUFFER_SIZE),PropertyType.INTEGER,true);
		setProperty(BlockConstants.BLOCK_PROPERTY_SAMPLE_SIZE, sizeProperty);
		BlockProperty labelProperty = new BlockProperty(BLOCK_PROPERTY_TEST_LABEL,"",PropertyType.STRING,true);
		setProperty(BLOCK_PROPERTY_TEST_LABEL, labelProperty);
		
		// Define a 3 inputs.
		AnchorPrototype input = new AnchorPrototype(PORT_TARGET,AnchorDirection.INCOMING,ConnectionType.DATA);
		input.setAnnotation("T");
		input.setHint(PlacementHint.LT);
		input.setIsMultiple(false);
		anchors.add(input);
		input = new AnchorPrototype(PORT_VALUE,AnchorDirection.INCOMING,ConnectionType.DATA);
		input.setAnnotation("V");
		input.setHint(PlacementHint.L);
		input.setIsMultiple(false);
		anchors.add(input);
		input = new AnchorPrototype(PORT_STANDARD_DEVIATION,AnchorDirection.INCOMING,ConnectionType.DATA);
		input.setAnnotation("S");
		input.setHint(PlacementHint.LB);
		input.setIsMultiple(false);
		anchors.add(input);

		// Define the main output, a truth value.
		AnchorPrototype output = new AnchorPrototype(BlockConstants.OUT_PORT_NAME,AnchorDirection.OUTGOING,ConnectionType.TRUTHVALUE);
		anchors.add(output);
	}
	
	@Override
	public void reset() {
//		super.reset();  // this block needs to propagate UNKNOWN
		
		this.state = TruthValue.UNKNOWN;
		this.lastValue = null;
		recordActivity(Activity.ACTIVITY_RESET,"");
		if( controller!=null ) {
			// Send notifications on all outputs to indicate empty connections.
			// For truth-values, actually propagate UNKNWON since UNSET doesn't get propagated
			for(AnchorPrototype ap:getAnchors()) {
				if( ap.getAnchorDirection().equals(AnchorDirection.OUTGOING) ) {
					if( ap.getConnectionType().equals(ConnectionType.TRUTHVALUE) ) {
						QualifiedValue UNKNOWN_TRUTH_VALUE = new TestAwareQualifiedValue(timer,TruthValue.UNKNOWN);
						controller.sendConnectionNotification(getBlockId().toString(), ap.getName(),UNKNOWN_TRUTH_VALUE);
						OutgoingNotification nvn = new OutgoingNotification(this,ap.getName(),UNKNOWN_TRUTH_VALUE);
						controller.acceptCompletionNotification(nvn);
					}
				}
			}
		}

		if( clearOnReset ) {
			clear();
		}
	}
	
	private void clear() {
		log.debugf("%s.clear: reset data buffer",getName());
		queue.clear();
	}
	
	/**
	 * A new value has arrived. Add it to the queue. Reset the timeout timer.
	 * @param incoming incoming new value.
	 */
	@Override
	public synchronized void acceptValue(IncomingNotification incoming) {
		super.acceptValue(incoming);
		QualifiedValue qv = incoming.getValue();
		Quality qual = qv.getQuality();
		String port = incoming.getConnection().getDownstreamPortName();
		if( port.equals(PORT_VALUE)  ) {
			if( qual.isGood() && qv!=null && qv.getValue()!=null ) {
				try {
					Double dbl  = Double.parseDouble(qv.getValue().toString());
					if( dbl!=null ) {
						queue.add(dbl);  // Synchronize to avoid concurrent modification
						evaluate();
					}
				}
				catch(NumberFormatException nfe) {
					log.warnf("%s.acceptValue exception converting incoming %s to double (%s)",getName(),qv.getValue().toString(),nfe.getLocalizedMessage());
				}
			}
			else {
				// Bad quality, emit the result immediately
				if( !state.equals(TruthValue.UNKNOWN) ) {
					state = TruthValue.UNKNOWN;
					lastValue = new BasicQualifiedValue(state,qual,qv.getTimestamp());
					if( !isLocked() ) {
						OutgoingNotification nvn = new OutgoingNotification(this,BlockConstants.OUT_PORT_NAME,lastValue);
						controller.acceptCompletionNotification(nvn);
					}
				}
				clear();   // Reset the current buffer
			}
		}
		else if( port.equals(PORT_TARGET)  ) {
			qv = incoming.getValue();
			if( qv==null || qv.getValue()==null) return;
			try {
				mean = Double.parseDouble(qv.getValue().toString());
			}
			catch(NumberFormatException nfe) {
				log.warnf("%s: propertyChange Unable to convert target value to a float (%s)",getName(),nfe.getLocalizedMessage());
			}
			evaluate();
		}
		else if( port.equals(PORT_STANDARD_DEVIATION)  ) {
			qv = incoming.getValue();
			if( qv==null || qv.getValue()==null) return;
			try {
				standardDeviation = Double.parseDouble(qv.getValue().toString());
			}
			catch(NumberFormatException nfe) {
				log.warnf("%s: propertyChange Unable to convert standard deviation value to a float (%s)",getName(),nfe.getLocalizedMessage());
			}
			evaluate();
		}
	}
	/**
	 * We're received a transmitted signal. Deal with it, if appropriate.
	 * At a later time, we may implement pattern filtering or some other
	 * method to filter out unwanted messages. For now, if we recognize the command,
	 * then execute it.
	 * 
	 * @param sn signal notification.
	 */
	@Override
	public void acceptValue(SignalNotification sn) {
		Signal signal = sn.getSignal();
		if( signal.getCommand().equalsIgnoreCase(BlockConstants.COMMAND_CLEAR_HIGH) && limitType.equals(LimitType.HIGH)) {
			log.tracef("%s.acceptValue: signal = %s ",getName(),signal.getCommand());
			if( state.equals(TruthValue.TRUE)) {
				state = TruthValue.FALSE; 
				lastValue = new TestAwareQualifiedValue(timer,state);
				OutgoingNotification nvn = new OutgoingNotification(this,BlockConstants.OUT_PORT_NAME,lastValue);
				controller.acceptCompletionNotification(nvn);
			}
		}
		else if( signal.getCommand().equalsIgnoreCase(BlockConstants.COMMAND_CLEAR_LOW) && limitType.equals(LimitType.LOW)) {
			log.tracef("%s.acceptValue: signal = %s ",getName(),signal.getCommand());
			if( state.equals(TruthValue.TRUE)) {
				state = TruthValue.FALSE; 
				lastValue = new TestAwareQualifiedValue(timer,state);
				OutgoingNotification nvn = new OutgoingNotification(this,BlockConstants.OUT_PORT_NAME,lastValue);
				controller.acceptCompletionNotification(nvn);
			}
		}
	}

	
	/**
	 * Unlike most blocks, this method is not associated with a timer expiration.
	 * We simply use this to do the calculation.
	 */
	@Override
	public synchronized void evaluate() {
		if( Double.isNaN(mean) )              return;
		if( Double.isNaN(standardDeviation) ) return;
		if( queue.size()==0 ) return;         // No value yet

		// Evaluate the buffer and report
		log.debugf("%s.evaluate %d of %d",getName(),queue.size(),sampleSize);
		TruthValue newState = getRuleState();
		if( !isLocked() && !newState.equals(state) ) {
			// Give it a new timestamp
			state = newState;
			lastValue = new TestAwareQualifiedValue(timer,state);
			OutgoingNotification nvn = new OutgoingNotification(this,BlockConstants.OUT_PORT_NAME,lastValue);
			controller.acceptCompletionNotification(nvn);
			notifyOfStatus(lastValue);
			// Clear any watermark
			controller.sendWatermarkNotification(getParentId().toString(), "");

			// Notify other blocks to suppress alternate results.
			// We only notify blocks in our same group, exclusive of ourself
			if( state.equals(TruthValue.TRUE)) {
				if( limitType.equals(LimitType.HIGH )) {
					Signal sig = new Signal(BlockConstants.COMMAND_CLEAR_LOW,"","");
					QualifiedValue qv = new TestAwareQualifiedValue(timer,sig);
					notifyGroupMembers(qv);
					
				}
				else if( limitType.equals(LimitType.LOW )) {
					Signal sig = new Signal(BlockConstants.COMMAND_CLEAR_HIGH,"","");
					QualifiedValue qv = new TestAwareQualifiedValue(timer,sig);
					notifyGroupMembers(qv);
				}
			}
		}
	}
	/**
	 * The explanation for this block just reports m of n.
	 * 
	 * @return an explanation for the current state of the block.
	 */
	@Override
	public String getExplanation(DiagnosticDiagram parent,List<UUID> members) {
		String explanation = "";
		if( state.equals(TruthValue.TRUE) ) {
			explanation = String.format("SQC %s (limit %s), %d of %d outside limits meets threshold of %d",getName(),limitType.name(),
					outside,total,minOut);		
		}
		else if( state.equals(TruthValue.FALSE) ) {
			int expected = sampleSize-minOut;
			if( expected>total ) expected = total;
			explanation = String.format("SQC %s (limit %s), %d of %d within limits (%d expected)",getName(),limitType.name(),
					total-outside,total,expected);		
		}
		return explanation;
	}
	/**
	 * Send status update notification for our last latest state.
	 */
	@Override
	public void notifyOfStatus() {
		QualifiedValue qv = new TestAwareQualifiedValue(timer,state);
		notifyOfStatus(qv);
		
	}
	private void notifyOfStatus(QualifiedValue qv) {
		//log.tracef("%s.notifyOfStatus %s = %s",getName(),getBlockId().toString(),qv.getValue().toString());
		controller.sendConnectionNotification(getBlockId().toString(), BlockConstants.OUT_PORT_NAME, qv);
	}
	/**
	 * Normally propagate re-sends the lastValue on its output. For most blocks, the last value is calculated
	 * immediately on receipt of input. Here we have to make sure we are propagating the state.
	 */
	@Override
	public void propagate() {
		lastValue = new TestAwareQualifiedValue(timer,state);
		super.propagate();
	}
	/**
	 * Handle a changes to the various attributes.
	 */
	@Override
	public void propertyChange(BlockPropertyChangeEvent event) {
		super.propertyChange(event);
		String propertyName = event.getPropertyName();
		log.debugf("%s.propertyChange: %s = %s",getName(),propertyName,event.getNewValue().toString());
		if(propertyName.equalsIgnoreCase(BlockConstants.BLOCK_PROPERTY_CLEAR_ON_RESET)) {
			try {
				clearOnReset = Boolean.parseBoolean(event.getNewValue().toString());
			}
			catch(NumberFormatException nfe) {
				log.warnf("%s: propertyChange Unable to convert clear flag to a boolean (%s)",getName(),nfe.getLocalizedMessage());
			}
		}
		else if(propertyName.equalsIgnoreCase(BLOCK_PROPERTY_MINIMUM_OUT_OF_RANGE)) {
			try {
				minOut = Integer.parseInt(event.getNewValue().toString());
				if( minOut<1 ) minOut = 1;
			}
			catch(NumberFormatException nfe) {
				log.warnf("%s: propertyChange Unable to convert max out-of-range to an integer (%s)",getName(),nfe.getLocalizedMessage());
			}
		}
		else if(propertyName.equalsIgnoreCase(BlockConstants.BLOCK_PROPERTY_SAMPLE_SIZE)) {
			try {
				sampleSize = Integer.parseInt(event.getNewValue().toString());
				if( sampleSize < 1 ) sampleSize = 1; 
				queue.setBufferSize(sampleSize);
				reset();
			}
			catch(NumberFormatException nfe) {
				log.warnf("%s: propertyChange Unable to convert sample size to an integer (%s)",getName(),nfe.getLocalizedMessage());
			}
		}
		else if(propertyName.equalsIgnoreCase(BLOCK_PROPERTY_SQC_LIMIT)) {
			try {
				limit = Double.parseDouble(event.getNewValue().toString());
			}
			catch(NumberFormatException nfe) {
				log.warnf("%s: propertyChange Unable to convert target value to a double (%s)",getName(),nfe.getLocalizedMessage());
			}
		}
		else if(propertyName.equalsIgnoreCase(BlockConstants.BLOCK_PROPERTY_LIMIT_TYPE)) {
			String type = event.getNewValue().toString().toUpperCase();
			limitType = LimitType.valueOf(type);
		}
		else if(propertyName.equalsIgnoreCase(BLOCK_PROPERTY_TEST_LABEL)) {
			;   // Default handling is sufficient
		}
		// Buffer size handled in superior method
		else if( !propertyName.equals(BlockConstants.BLOCK_PROPERTY_ACTIVITY_BUFFER_SIZE) ){
			log.warnf("%s.propertyChange:Unrecognized property (%s)",getName(),propertyName);
		}
	}
	/**
	 * @return a block-specific description of internal statue
	 */
	@Override
	public SerializableBlockStateDescriptor getInternalStatus() {
		SerializableBlockStateDescriptor descriptor = super.getInternalStatus();
		Map<String,String> attributes = descriptor.getAttributes();
		attributes.put("Mean (target)", String.valueOf(mean));
		attributes.put("StandardDeviation", String.valueOf(standardDeviation));
		attributes.put("Limit type", limitType.name());
		attributes.put("Limit ~ std deviations", String.valueOf(limit));
		attributes.put("Minimum Out of Range", String.valueOf(minOut));
		attributes.put("Current Out of Range", String.valueOf(outside));
		attributes.put("SampleSize", String.valueOf(sampleSize));
		attributes.put("Current QueueSize", String.valueOf(total));
		attributes.put("State", state.name());
		List<Map<String,String>> descBuffer = descriptor.getBuffer();
		for( Double dbl:queue) {
			Map<String,String> qvMap = new HashMap<>();
			qvMap.put("Value", String.valueOf(dbl));
			descBuffer.add(qvMap);
		}
		return descriptor;
	}

	
	/**
	 * Add the value of the target input to the standard descriptor.
	 * @return the descriptor
	 */
	@Override
	public SerializableBlockStateDescriptor toDescriptor() {
		SerializableBlockStateDescriptor descriptor = super.toDescriptor();
		Map<String,String> attributes = descriptor.getAttributes();
		attributes.put(BLTProperties.BLOCK_ATTRIBUTE_TARGET,String.valueOf(mean));
		return descriptor;
	}
	/**
	 * Augment the palette prototype for this block class.
	 */
	private void initializePrototype() {
		prototype.setPaletteIconPath("Block/icons/palette/SQC.png");
		prototype.setPaletteLabel("SQC");
		prototype.setTooltipText("Perform an SPC analysis on the input and place results on output");
		prototype.setTabName(BlockConstants.PALETTE_TAB_STATISTICS);
		
		BlockDescriptor desc = prototype.getBlockDescriptor();
		desc.setEmbeddedLabel("SQC");
		desc.setEmbeddedFontSize(20);
		desc.setBlockClass(getClass().getCanonicalName());
		desc.setStyle(BlockStyle.SQUARE);
		desc.setBackground(BlockConstants.BLOCK_BACKGROUND_LIGHT_GRAY);
	}
	/**
	 * Compute the state, presumably because of a new input.
	 */
	private TruthValue getRuleState() {
		TruthValue result = TruthValue.UNKNOWN;
		this.total= 0;
		int high = 0;
		int low  = 0;
		int highside  = 0;   // Consecutive
		int lowside   = 0;
		int maxhighside  = 0;   // Consecutive
		int maxlowside   = 0;
		this.outside = 0;
		double highLimit = mean+(standardDeviation*limit);
		double lowLimit  = mean-(standardDeviation*limit);
		// Got a concurrent modification exception here.l
		for( Double dbl:queue) {
			double val = dbl.doubleValue();
			
			if( DEBUG )log.infof("%s.getRuleState: val = %f (%f,%f,%f)",getName(),val,lowLimit,mean,highLimit);
			if( val < lowLimit) {
				low++;
			}
			else if( val > highLimit) {
				high++;
			}
			
			if( val>mean ) {
				highside++;
				if( highside>maxhighside ) maxhighside = highside;
				lowside= 0;
			}
			else if( val<mean ) {
				lowside++;
				if( lowside>maxlowside ) maxlowside = lowside;
				highside= 0;
			}
			else {
				lowside= 0;
				highside = 0;
			}
			
			if(limitType.equals(LimitType.LOW) ) {
				if( val <lowLimit) {
					outside++;
				}
			}
			else if(limitType.equals(LimitType.HIGH) ) {
				if( val > highLimit) {
					outside++;
				}
			}
			else if(limitType.equals(LimitType.BOTH) ) {
				if(      val > highLimit) outside++;
				else if( val < lowLimit) outside++;
			}
			total++;
		}
		
		if( limitType.equals(LimitType.CONSECUTIVE) ) {
			int maxcurrent = (highside>lowside?highside:lowside);
			if( maxhighside >= minOut || maxlowside >= minOut ) result = TruthValue.TRUE; 
			else if( total+minOut-maxcurrent>sampleSize ) result = TruthValue.FALSE;
		}
		else {
			if( outside>=minOut) result = TruthValue.TRUE;
			else if( total+minOut-outside>sampleSize ) result = TruthValue.FALSE;
		}
		
		if( DEBUG ) log.infof("%s.getRuleState: %d of %d results,  %d high, %d low, %d outside, (cons %d,%d) => %s (%s)",getName(),total,sampleSize,high,low,outside,maxlowside,maxhighside,result.toString(),limitType.toString());
		return result;	
	}
	
	/**
	 * Send a signal to SQC blocks that share the same input and are not this block.
	 * We bypass the controller routing.
	 * @param qv the signal
	 */
	private void notifyGroupMembers(QualifiedValue qv) {
		String masterInput = getInputForBlock(getParentId(),getBlockId(),PORT_VALUE);
		if( masterInput!=null) {
			// Now compare all SQC blocks on the chart.
			DiagnosticDiagram diagram = controller.getDiagram(getParentId().toString());
			Collection<ProcessBlock> blocks = diagram.getProcessBlocks();
			for( ProcessBlock block:blocks) {
				if( !block.getClassName().equalsIgnoreCase("com.ils.block.SQC") ) continue;
				if( block.getBlockId().equals(getBlockId())) continue;
				String inputId = getInputForBlock(getParentId(),block.getBlockId(),PORT_VALUE);
				if( inputId==null) continue;
				if( inputId.equalsIgnoreCase(masterInput)) {
					SignalNotification sn = new SignalNotification(block,qv);
					block.acceptValue(sn);
				}
			}
		}
	}
	
	// Given a block state descriptor and port, return the id of the input block connected upstream.
	// The input must be on the same diagram.
	private String getInputForBlock(UUID diagramId,UUID blockId,String port) {
		String inputId = null;
		List<SerializableBlockStateDescriptor> immediateDescriptors = controller.listBlocksConnectedAtPort(diagramId.toString(), blockId.toString(), port);
		for(SerializableBlockStateDescriptor upstream:immediateDescriptors) {
			if(upstream.getClassName().equalsIgnoreCase("com.ils.block.Input") ||
			   upstream.getClassName().equalsIgnoreCase("com.ils.block.LabData") 	) {
				return upstream.getIdString();
			}
		}
		// The upstream block is not, itself, an input. Search further.
		for(SerializableBlockStateDescriptor upstream:immediateDescriptors) {
			if(upstream.getClassName().equalsIgnoreCase("com.ils.block.Input") ||
			   upstream.getClassName().equalsIgnoreCase("com.ils.block.LabData") 	) {
				List<SerializableBlockStateDescriptor> upstreamBlocks = controller.listBlocksUpstreamOf(diagramId, UUID.fromString(upstream.getIdString()), false);
				for(SerializableBlockStateDescriptor ancestor:upstreamBlocks) {
					if(ancestor.getClassName().equalsIgnoreCase("com.ils.block.Input") ||
							ancestor.getClassName().equalsIgnoreCase("com.ils.block.LabData") 	) {
						return ancestor.getIdString();
					}
				}
			}
		}
		return inputId;
	}
}