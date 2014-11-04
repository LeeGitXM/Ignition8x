/**
 *   (c) 2014  ILS Automation. All rights reserved. 
 */
package com.ils.block;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.ils.block.annotation.ExecutableBlock;
import com.ils.blt.common.block.AnchorDirection;
import com.ils.blt.common.block.AnchorPrototype;
import com.ils.blt.common.block.BindingType;
import com.ils.blt.common.block.BlockConstants;
import com.ils.blt.common.block.BlockDescriptor;
import com.ils.blt.common.block.BlockProperty;
import com.ils.blt.common.block.BlockState;
import com.ils.blt.common.block.BlockStyle;
import com.ils.blt.common.block.ProcessBlock;
import com.ils.blt.common.block.PropertyType;
import com.ils.blt.common.block.TruthValue;
import com.ils.blt.common.connection.ConnectionType;
import com.ils.blt.common.control.ExecutionController;
import com.ils.blt.common.notification.BlockPropertyChangeEvent;
import com.ils.blt.common.notification.IncomingNotification;
import com.ils.blt.common.notification.OutgoingNotification;
import com.ils.common.watchdog.Watchdog;
import com.inductiveautomation.ignition.common.model.values.BasicQualifiedValue;
import com.inductiveautomation.ignition.common.model.values.QualifiedValue;
import com.inductiveautomation.ignition.common.model.values.Quality;
import com.inductiveautomation.ignition.common.sqltags.model.types.DataQuality;

/**
 * This class emits the "and" of its inputs. Synchronizing
 * is available. Inputs and outputs are truth-values.
 */
@ExecutableBlock
public class And extends AbstractProcessBlock implements ProcessBlock {
	private final String TAG = "And";
	// Keep map of values by originating block id
	protected final Map<String,QualifiedValue> qualifiedValueMap;
	private final Watchdog dog;
	private double synchInterval = 0.5; // 1/2 sec synchronization by default
	protected TruthValue truthValue;
	
	/**
	 * Constructor: The no-arg constructor is used when creating a prototype for use in the palette.
	 */
	public And() {
		dog = new Watchdog(TAG,this);
		qualifiedValueMap = new HashMap<String,QualifiedValue>();
		initialize();
		initializePrototype();
	}
	
	/**
	 * Constructor. 
	 * 
	 * @param ec execution controller for handling block output
	 * @param parent universally unique Id identifying the parent of this block
	 * @param block universally unique Id for the block
	 */
	public And(ExecutionController ec,UUID parent,UUID block) {
		super(ec,parent,block);
		dog = new Watchdog(TAG,this);
		qualifiedValueMap = new HashMap<String,QualifiedValue>();
		initialize();
	}
	
	/**
	 * Define the synchronization property and ports.
	 */
	private void initialize() {	
		setName("And");
		truthValue = TruthValue.UNSET;
		// Define the time for "coalescing" inputs ~ msec
		BlockProperty synch = new BlockProperty(BlockConstants.BLOCK_PROPERTY_SYNC_INTERVAL,new Double(synchInterval),PropertyType.TIME,true);
		properties.put(BlockConstants.BLOCK_PROPERTY_SYNC_INTERVAL, synch);
		BlockProperty valueProperty = new BlockProperty(BlockConstants.BLOCK_PROPERTY_VALUE,TruthValue.UNKNOWN,PropertyType.TRUTHVALUE,false);
		valueProperty.setBindingType(BindingType.ENGINE);
		properties.put(BlockConstants.BLOCK_PROPERTY_VALUE, valueProperty);
		
		// Define a single input -- but allow multiple connections
		AnchorPrototype input = new AnchorPrototype(BlockConstants.IN_PORT_NAME,AnchorDirection.INCOMING,ConnectionType.TRUTHVALUE);
		anchors.add(input);

		// Define a single output
		AnchorPrototype output = new AnchorPrototype(BlockConstants.OUT_PORT_NAME,AnchorDirection.OUTGOING,ConnectionType.TRUTHVALUE);
		anchors.add(output);
	}
	
	@Override
	public void reset() {
		super.reset();
		qualifiedValueMap.clear();
		truthValue = TruthValue.UNSET;
	}
	
	
	/**
	 * Notify the block that a new value has appeared on one of its input anchors.
	 * For now we simply record the change in the map and start the watchdog. 
	 * 
	 * Note: there can be several connections attached to a given port.
	 * @param incoming new value.
	 */
	@Override
	public void acceptValue(IncomingNotification incoming) {
		super.acceptValue(incoming);
		this.state = BlockState.ACTIVE;
		String key = String.format("%s:%s",incoming.getConnection().getSource().toString(),
                                           incoming.getConnection().getUpstreamPortName());
		QualifiedValue qv = incoming.getValue();
		qualifiedValueMap.put(key, qv);
		dog.setSecondsDelay(synchInterval);
		controller.pet(dog);
	}
	
	
	/**
	 * The coalescing time has expired. Place the current state on the output,
	 * if it has changed.
	 */
	@Override
	public void evaluate() {
		if( !isLocked() ) {
			TruthValue newState = getAggregateState();
			if(newState!=truthValue) {
				truthValue = newState;
				QualifiedValue result = new BasicQualifiedValue(truthValue.name(),
						                                        (truthValue.equals(TruthValue.UNKNOWN)?getAggregateQuality():DataQuality.GOOD_DATA));
				OutgoingNotification nvn = new OutgoingNotification(this,BlockConstants.OUT_PORT_NAME,result);
				controller.acceptCompletionNotification(nvn);
				controller.sendPropertyNotification(getBlockId().toString(), BlockConstants.BLOCK_PROPERTY_VALUE,result);
			}
		}
	}
	
	/**
	 * Handle a change to the coalescing interval.
	 */
	@Override
	public void propertyChange(BlockPropertyChangeEvent event) {
		super.propertyChange(event);
		String propertyName = event.getPropertyName();
		if(propertyName.equalsIgnoreCase(BlockConstants.BLOCK_PROPERTY_SYNC_INTERVAL)) {
			try {
				synchInterval = Double.parseDouble(event.getNewValue().toString());
			}
			catch(NumberFormatException nfe) {
				log.warnf("%s: propertyChange Unable to convert synch interval to an double (%s)",TAG,nfe.getLocalizedMessage());
			}
		}
	}

	/**
	 * Augment the palette prototype for this block class.
	 */
	private void initializePrototype() {
		prototype.setPaletteIconPath("Block/icons/palette/and.png");
		prototype.setPaletteLabel("And");
		prototype.setTooltipText("And incoming values and place state changes on output");
		prototype.setTabName(BlockConstants.PALETTE_TAB_LOGIC);
		
		BlockDescriptor desc = prototype.getBlockDescriptor();
		desc.setEmbeddedLabel("AND");
		desc.setEmbeddedFontSize(18);
		desc.setPreferredHeight(60);
		desc.setPreferredWidth(60);
		desc.setBlockClass(getClass().getCanonicalName());
		desc.setStyle(BlockStyle.LOGIC_AND);
		desc.setBackground(BlockConstants.BLOCK_BACKGROUND_LIGHT_GRAY);
	}
	/**
	 * Compute the overall quality.
	 * NOTE: This is only valid if the current state is UNKNOWN.
	 */
	private Quality getAggregateQuality() {
		Collection<QualifiedValue> values = qualifiedValueMap.values();
		Quality q = DataQuality.GOOD_DATA; 
		for(QualifiedValue qv:values) {
			if( !qv.getQuality().isGood() ) return qv.getQuality();
		}
		return q;	
	}
	
	/**
	 * Compute the overall state, presumably because of a new input.
	 * This is an "and"
	 */
	private TruthValue getAggregateState() {
		Collection<QualifiedValue> values = qualifiedValueMap.values();
		TruthValue result = TruthValue.UNSET;
		
		for(QualifiedValue qv:values) {
			TruthValue ts = qualifiedValueAsTruthValue(qv);
			if( ts==TruthValue.FALSE ) {
				result = ts;
				break;
			}
			else if(ts==TruthValue.UNKNOWN || !qv.getQuality().isGood()) {
				result = TruthValue.UNKNOWN;
			}
			else {
				if(result!=TruthValue.UNKNOWN ) result = TruthValue.TRUE;
			}
		}
		if(result==TruthValue.UNSET) result = TruthValue.UNKNOWN;  // All were bad
		return result;	
	}
}