/**
 *   (c) 2014  ILS Automation. All rights reserved. 
 */
package com.ils.blt.test.gateway.mock;

import java.util.Date;
import java.util.UUID;

import com.ils.block.AbstractProcessBlock;
import com.ils.blt.common.block.AnchorDirection;
import com.ils.blt.common.block.AnchorPrototype;
import com.ils.blt.common.block.BindingType;
import com.ils.blt.common.block.BlockProperty;
import com.ils.blt.common.block.ProcessBlock;
import com.ils.blt.common.block.PropertyType;
import com.ils.blt.common.block.TruthValue;
import com.ils.blt.common.connection.ConnectionType;
import com.ils.blt.common.control.BlockPropertyChangeEvent;
import com.ils.blt.common.control.OutgoingNotification;
import com.ils.blt.gateway.engine.BlockExecutionController;
import com.inductiveautomation.ignition.common.model.values.BasicQualifiedValue;
import com.inductiveautomation.ignition.common.model.values.Quality;
import com.inductiveautomation.ignition.common.sqltags.model.types.DataQuality;


/**
 * This block is strictly for use with a MockDiagram to provide
 * inputs that can be subscribed to tags.
 */
public class MockInputBlock extends AbstractProcessBlock implements ProcessBlock {
	private final static String TAG = "MockInputBlock";
	private final static String BLOCK_PROPERTY_INPUT = "Input";
	private final String portName;
	private final PropertyType propertyType;
	private final String tagPath;
	
	public MockInputBlock(UUID parent,String tag,PropertyType pt,String port) {
		super(BlockExecutionController.getInstance(),parent,UUID.randomUUID());
		this.portName = port;
		this.propertyType = pt;
		this.tagPath = tag;
		initialize();
	}
	
	public PropertyType getPropertyType() { return propertyType; }
	public String getPort() { return portName; }
	
	/**
	 * If the input property changes, then place the new value on the output.
	 * This is the normal path when a subscribed tag changes value.
	 */
	@Override
	public void propertyChange(BlockPropertyChangeEvent event) {
		super.propertyChange(event);
		if( event.getPropertyName().equals(BLOCK_PROPERTY_INPUT)) {
			OutgoingNotification nvn = new OutgoingNotification(this,portName,event.getNewValue());
			controller.acceptCompletionNotification(nvn);
		}
	}
	
	/**
	 * Pass a value directly on the output. This callable directly. Convert 
	 * arguments to proper type for the outgoing connection.
	 * @param value as a string. The block will convert to the correct datatype.
	 * @param quality as a string. Good is 'good'.
	 * @return the timestamp ~msec
	 */
	public long writeValue(String value,String quality) {
		// Convert the string to a proper data type
		Object obj = value;
		Date now = new Date();
		if( propertyType.equals(PropertyType.BOOLEAN)) {
			obj = TruthValue.UNKNOWN;
			try {
				obj = TruthValue.valueOf(value.toUpperCase());
			}
			catch(IllegalArgumentException iae) {
				log.infof("%s.writeValue: Unknown truth value %s (%s)",TAG,value,iae.getLocalizedMessage());
			}
		}
		Quality q = DataQuality.OPC_BAD_DATA;
		if(  "good".equalsIgnoreCase(quality)) q = DataQuality.GOOD_DATA;
		obj = new BasicQualifiedValue(obj,q,now);
		OutgoingNotification nvn = new OutgoingNotification(this,portName,obj);
		controller.acceptCompletionNotification(nvn);
		return now.getTime();
	}
	
	/**
	 * Add the tag property and link it to the value property.
	 */
	private void initialize() {
		setName("MockInput");
		BlockProperty value = new BlockProperty(BLOCK_PROPERTY_INPUT,null,propertyType,true);
		if( tagPath!=null && tagPath.length()>0 ) {
			value.setBinding(tagPath);
			value.setBindingType(BindingType.TAG_READ);
		}
		properties.put(BLOCK_PROPERTY_INPUT, value);
		
		// Define a single output
		ConnectionType ctype = ConnectionType.connectionTypeForPropertyType(propertyType);
		AnchorPrototype output = new AnchorPrototype(portName,AnchorDirection.OUTGOING,ctype);
		anchors.add(output);
	}
}