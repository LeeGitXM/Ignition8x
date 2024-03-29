/**
 *   (c) 2014-2107  ILS Automation. All rights reserved. 
 */
package com.ils.block;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

import com.ils.block.annotation.ExecutableBlock;
import com.ils.blt.common.ProcessBlock;
import com.ils.blt.common.block.Activity;
import com.ils.blt.common.block.AnchorDirection;
import com.ils.blt.common.block.AnchorPrototype;
import com.ils.blt.common.block.BindingType;
import com.ils.blt.common.block.BlockConstants;
import com.ils.blt.common.block.BlockDescriptor;
import com.ils.blt.common.block.BlockProperty;
import com.ils.blt.common.block.BlockStyle;
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
import com.ils.common.watchdog.AcceleratedWatchdogTimer;
import com.ils.common.watchdog.TestAwareQualifiedValue;
import com.ils.common.watchdog.Watchdog;
import com.inductiveautomation.ignition.common.model.values.BasicQualifiedValue;
import com.inductiveautomation.ignition.common.model.values.QualifiedValue;

/**
 * On receipt of a trigger, this class inhibits further input from propagating.
 * Values that arrive during the inhibit period are discarded. Otherwise
 * values are propagated without change.
 */
@ExecutableBlock
public class Inhibitor extends AbstractProcessBlock implements ProcessBlock {
	private BlockProperty expirationProperty = null;
	private double interval = 0.0;   // ~minutes
	private boolean inhibiting = false;
	private TruthValue controlValue = TruthValue.UNSET;
	private TruthValue initialValue = TruthValue.UNSET;
	private TruthValue trigger = TruthValue.TRUE;  // why was this set to UNSET previously?  Shouldn't the default be TRUE? 

	private final Watchdog dog;
	
	/**
	 * Constructor: The no-arg constructor is used when creating a prototype for use in the palette.
	 */
	public Inhibitor() {
		initialize();
		dog = new Watchdog(getName(),this);
		initializePrototype();
	}
	
	/**
	 * Constructor. Custom property is "interval".
	 * 
	 * @param ec execution controller for handling block output
	 * @param parent universally unique Id identifying the parent of this block
	 * @param block universally unique Id for the block
	 */
	public Inhibitor(ExecutionController ec,UUID parent,UUID block) {
		super(ec,parent,block);
		initialize();
		dog = new Watchdog(getName(),this);
	}
	
	// On a reset, set the time to the start of the Unix epoch
	@Override
	public void reset() {
		super.reset();
		expirationProperty.setValue(new Long(0L));
		inhibiting = controlValue.equals(trigger) && trigger != TruthValue.UNSET;  // don't inhibit if unset
		setState(initialValue);
		if(!locked && !inhibiting && !initialValue.equals(TruthValue.UNSET)) {
			lastValue = new TestAwareQualifiedValue(timer,initialValue);
			OutgoingNotification nvn = new OutgoingNotification(this,BlockConstants.OUT_PORT_NAME,lastValue);
			controller.acceptCompletionNotification(nvn);
			notifyOfStatus();
		}
		timer.removeWatchdog(dog);
		controller.sendPropertyNotification(getBlockId().toString(), BlockConstants.BLOCK_PROPERTY_EXPIRATION_TIME,
				new BasicQualifiedValue(expirationProperty.getValue()));
	}

	/**
	 * Under the right circumstances we propagate the initial value.
	 */
	@Override
	public void start() {
		super.start();
		if(propagateOnStart()) {
			lastValue = new TestAwareQualifiedValue(timer,initialValue);
			OutgoingNotification nvn = new OutgoingNotification(this,BlockConstants.OUT_PORT_NAME,lastValue);
			controller.acceptCompletionNotification(nvn);
			state = initialValue;
		}
	}
	

	/**
	 * A new value has appeared on an input anchor. If we are in an "inhibit" state, then 
	 * send this value to the bit-bucket.
	 * 
	 * Exponentially smooth values. The filter constant is the time-difference
     * between measurements divided by a time constant. The longer the time difference,
     * the more that we favor the current measurement. evaluation interval and time window
     * must have the same units.
	 * @param vcn change notification.
	 */
	@Override
	public void acceptValue(IncomingNotification vcn) {
		super.acceptValue(vcn);
		if( !isLocked() ) {
			String port = vcn.getConnection().getDownstreamPortName();
			if( port.equals(BlockConstants.IN_PORT_NAME)) {
				QualifiedValue qv = vcn.getValue();
				if( qv != null && qv.getValue()!=null ) {
					log.tracef("%s.acceptValue: Received value %s (%s)",getName(),qv.getValue().toString(),
							dateFormatter.format(qv.getTimestamp()));
					long expirationTime = ((Long)expirationProperty.getValue()).longValue();
					
					if( qv.getQuality().isGood() && (expirationTime==0 || qv.getTimestamp().getTime()>=expirationTime)) {
						lastValue = new BasicQualifiedValue(coerceToMatchOutput(BlockConstants.OUT_PORT_NAME,qv.getValue()),qv.getQuality(),qv.getTimestamp());
						if (!inhibiting) {
							OutgoingNotification nvn = new OutgoingNotification(this,BlockConstants.OUT_PORT_NAME,lastValue);
							controller.acceptCompletionNotification(nvn);
							notifyOfStatus();
							state = qualifiedValueAsTruthValue(lastValue);
						}
					}
					else {
						recordActivity(Activity.ACTIVITY_BLOCKED,qv.getValue().toString());
						log.infof("%s.acceptValue: Ignoring inhibited or BAD input ... (%s)",getName(),qv.getValue().toString());
					}
				}
				else {
					log.infof("%s.acceptValue: Received null %s (IGNORED)",getName(),(qv==null?"":"value"));
				}
			}
			else if( port.equals(BlockConstants.CONTROL_PORT_NAME)  ) {
				QualifiedValue qv = vcn.getValue();
				if( qv != null && qv.getValue()!=null ) {
					if( qv.getQuality().isGood() ) {
						TruthValue cv = qualifiedValueAsTruthValue(qv);
						// If this leads to a new mismatch, then we propagate the last value
						if( inhibiting && !cv.equals(trigger)) {
							if (lastValue != null) {
								OutgoingNotification nvn = new OutgoingNotification(this,BlockConstants.OUT_PORT_NAME,lastValue);
								controller.acceptCompletionNotification(nvn);
								notifyOfStatus();
								state = qualifiedValueAsTruthValue(lastValue);
							}
						}
						controlValue = cv;
						inhibiting = controlValue.equals(trigger);
						recordActivity((inhibiting?Activity.ACTIVITY_BLOCKING:Activity.ACTIVITY_UNBLOCKED),controlValue.toString());
					}
				}
				else {
					log.infof("%s.acceptValue: Received null %s (IGNORED)",getName(),(lastValue==null?"":"value"));
				}
			}

		}
	}
	
	/**
	 * We've received a transmitted signal. Deal with it, if appropriate.
	 * At a later time, we may implement pattern filtering or some other
	 * method to filter out unwanted messages. For now, if we recognize the command,
	 * then execute it.
	 * 
	 * @param sn signal notification.
	 */
	@Override
	public void acceptValue(SignalNotification sn) {
		Signal signal = sn.getSignal();
		if( signal.getCommand().equalsIgnoreCase(BlockConstants.COMMAND_INHIBIT)) {
			expirationProperty.setValue(new Long(sn.getValue().getTimestamp().getTime()+(long)(interval*1000)));
			inhibiting = true;
			controller.sendPropertyNotification(getBlockId().toString(), BlockConstants.BLOCK_PROPERTY_EXPIRATION_TIME,
					new BasicQualifiedValue(expirationProperty.getValue(),sn.getValue().getQuality(),sn.getValue().getTimestamp()));
			long time = ((Long)expirationProperty.getValue()).longValue();
			Date expiration = new Date(time);
			if( log.isDebugEnabled()) {
				log.debugf("%s.acceptValue: Received inhibit command (delay %f secs to %s)",getName(),interval,dateFormatter.format(expiration));
				if( timer instanceof AcceleratedWatchdogTimer) {
					AcceleratedWatchdogTimer awt = (AcceleratedWatchdogTimer)timer;
					log.debugf("%s.acceptValue: Setting %f/%f seconds delay from %s",getName(),interval,awt.getFactor(),
							dateFormatter.format(new Date(awt.getTestTime())));
				}
			}
			recordActivity(Activity.ACTIVITY_SET_EXPIRATION,dateFormatter.format(expiration));
			dog.setSecondsDelay(interval);
			timer.updateWatchdog(dog);  // pet dog
		}
	}

	/**
	 * @return a block-specific description of internal statue
	 */
	@Override
	public SerializableBlockStateDescriptor getInternalStatus() {
		SerializableBlockStateDescriptor descriptor = super.getInternalStatus();
		Map<String,String> attributes = descriptor.getAttributes();
		
		attributes.put("Interval~secs", String.valueOf(interval));
		attributes.put("Inhibiting", (inhibiting?"true":"false"));
		long time = ((Long)expirationProperty.getValue()).longValue();
		if( time>0 ) {
			Date expiration = new Date(time);
			attributes.put("Inhibit Expiration",dateFormatter.format(expiration) );
		}
		return descriptor;
	}
	/**
	 * The inhibit interval timer has expired. The "inhibiting" flag
	 * merely indicates that inputs with a current time-stamp will be
	 * denied. Inputs with past time-stamps may or may not propagate.
	 */
	@Override
	public void evaluate() {
		inhibiting = false;
		log.tracef("%s.evaluate: Set inhibit flag false",getName());
		recordActivity(Activity.ACTIVITY_UNBLOCKED,"resume pass-thru");
	}

	/**
	 * Add properties that are new for this class.
	 * Populate them with default values.
	 */
	private void initialize() {
		setName("Inhibitor");
		delayStart = propagateOnStart();
		this.setReceiver(true);
		BlockProperty constant = new BlockProperty(BlockConstants.BLOCK_PROPERTY_INHIBIT_INTERVAL,new Double(interval),PropertyType.TIME_MINUTES,true);
		setProperty(BlockConstants.BLOCK_PROPERTY_INHIBIT_INTERVAL, constant);
		expirationProperty = new BlockProperty(BlockConstants.BLOCK_PROPERTY_EXPIRATION_TIME,new Long(0L),PropertyType.DATE,true);
		expirationProperty.setBindingType(BindingType.ENGINE);   // Is not editable outside this class
		setProperty(BlockConstants.BLOCK_PROPERTY_EXPIRATION_TIME, expirationProperty);

		// Define the control input
		AnchorPrototype triggerIn = new AnchorPrototype(BlockConstants.CONTROL_PORT_NAME,AnchorDirection.INCOMING,ConnectionType.TRUTHVALUE);
		triggerIn.setHint(PlacementHint.T);
//		triggerIn.setAnnotation("T");
		anchors.add(triggerIn);
		
		// Define a data input
		AnchorPrototype input = new AnchorPrototype(BlockConstants.IN_PORT_NAME,AnchorDirection.INCOMING,ConnectionType.ANY);
		input.setIsMultiple(false);
		anchors.add(input);
		
		// Define a single output
		AnchorPrototype output = new AnchorPrototype(BlockConstants.OUT_PORT_NAME,AnchorDirection.OUTGOING,ConnectionType.ANY);
		anchors.add(output);
	}
	
	/**
	 * Handle a change to the interval value. Note that this does not currently
	 * change any inhibit in-progress.
	 */
	@Override
	public void propertyChange(BlockPropertyChangeEvent event) {
		super.propertyChange(event);
		this.setReceiver(true);
		String propertyName = event.getPropertyName();
		if( propertyName.equals(BlockConstants.BLOCK_PROPERTY_INHIBIT_INTERVAL) ) {
			try {
				interval = Double.parseDouble(event.getNewValue().toString());
			}
			catch(NumberFormatException nfe) {
				log.warnf("%s.propertyChange Unable to convert interval value to an double (%s)",getName(),nfe.getLocalizedMessage());
			}
		} else	if( propertyName.equals(BlockConstants.BLOCK_PROPERTY_TRIGGER)) {
			trigger = TruthValue.valueOf(event.getNewValue().toString().toUpperCase());	
		}
		else if( propertyName.equals(BlockConstants.BLOCK_PROPERTY_INITIAL_VALUE)) {
			initialValue = TruthValue.valueOf(event.getNewValue().toString());
		} else {
			log.warnf("%s.propertyChange:Unrecognized property (%s)",getName(),propertyName);
		}

	}
	/**
	 * Send status update notification for our last transmitted value. If we've 
	 * never transmitted one, lastValue will be null.
	 */
	@Override
	public void notifyOfStatus() {
		if(lastValue!=null) {
			controller.sendPropertyNotification(getBlockId().toString(), BlockConstants.BLOCK_PROPERTY_VALUE,lastValue);
			controller.sendConnectionNotification(getBlockId().toString(), BlockConstants.OUT_PORT_NAME, lastValue);
		}
		else {
			QualifiedValue lv = new BasicQualifiedValue(coerceToMatchOutput(BlockConstants.OUT_PORT_NAME,null));
			controller.sendConnectionNotification(getBlockId().toString(), BlockConstants.OUT_PORT_NAME, lv);
		}
	}

	/**
	 * If the trigger condition and initial value match, then
	 * we propagate a value on startup and on reset.
	 * @return
	 */
	private boolean propagateOnStart() {
		boolean result = false;
		if( controlValue.equals(TruthValue.UNSET) && !initialValue.equals(TruthValue.UNSET)) {
			result = true;
		}
		else if( !controlValue.equals(TruthValue.UNSET) && !initialValue.equals(TruthValue.UNSET) &&
				!trigger.equals(TruthValue.UNSET) && !controlValue.equals(trigger) ) {
			result = true;
		}
		return result;
	}

	/**
	 * Augment the palette prototype for this block class.
	 */
	private void initializePrototype() {
		prototype.setPaletteIconPath("Block/icons/palette/inhibit.png");
		prototype.setPaletteLabel("Inhibitor");
		prototype.setTooltipText("Discard incoming values that arrive during an inhbit interval");
		prototype.setTabName(BlockConstants.PALETTE_TAB_CONTROL);
		
		BlockDescriptor desc = prototype.getBlockDescriptor();
		desc.setPreferredHeight(60);
		desc.setPreferredWidth(80);
		desc.setBlockClass(getClass().getCanonicalName());
		desc.setStyle(BlockStyle.CLAMP);
		desc.setReceiveEnabled(true);
		desc.setCtypeEditable(true);
	}
}