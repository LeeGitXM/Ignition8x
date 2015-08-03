/**
 *  Copyright (c) 2014  ILS Automation. All rights reserved. 
 */
package com.ils.blt.gateway.test.mock;
import java.util.UUID;

import com.ils.block.AbstractProcessBlock;
import com.ils.block.ProcessBlock;
import com.ils.blt.common.block.AnchorDirection;
import com.ils.blt.common.block.AnchorPrototype;
import com.ils.blt.common.block.BindingType;
import com.ils.blt.common.block.BlockProperty;
import com.ils.blt.common.block.PropertyType;
import com.ils.blt.common.connection.ConnectionType;
import com.ils.blt.common.notification.IncomingNotification;
import com.ils.blt.gateway.engine.BlockExecutionController;
import com.inductiveautomation.ignition.common.model.values.BasicQualifiedValue;
import com.inductiveautomation.ignition.common.model.values.QualifiedValue;


/**
 * This block is strictly for use with a MockDiagram to provide
 * a spot to collect and store output values.
 */
public class MockOutputBlock extends AbstractProcessBlock implements ProcessBlock {
	private final static String TAG = "MockOutputBlock";
	private static String BLOCK_PROPERTY_OUTPUT = "Output";
	private final String portName;
	private final PropertyType propertyType;
	private final String tagPath;
	private QualifiedValue value = new BasicQualifiedValue("unset");
	
	public MockOutputBlock(UUID parent,String tag,PropertyType pt,String port) {
		super(BlockExecutionController.getInstance(),parent,UUID.randomUUID());
		this.portName = port;
		this.propertyType = pt;
		this.tagPath = tag;
		initialize();
	}
	
	public PropertyType getPropertyType() { return propertyType; }
	public String getPort() { return portName; }
	
	/**
	 * Add the tag property and link it to the value property.
	 */
	private void initialize() {
		setName("MockOutput");
		BlockProperty valu = new BlockProperty(BLOCK_PROPERTY_OUTPUT,"",propertyType,true);
		valu.setBinding(tagPath);
		valu.setBindingType(BindingType.TAG_WRITE);
		setProperty(BLOCK_PROPERTY_OUTPUT, valu);
		
		// Define a single input. Accept any data type.
		ConnectionType ctype = ConnectionType.ANY;
		AnchorPrototype input = new AnchorPrototype(portName,AnchorDirection.INCOMING,ctype);
		anchors.add(input);
	}
	/**
	 * Clear the block's internal value so that we can detect the next arrival
	 * (in case it is the same as the previous)..
	 */
	public void clearValue() { value = new BasicQualifiedValue("unset"); }
	
	/**
	 * @return the latest value received by this block.
	 */
	public QualifiedValue getValue() { return value; }
	
	/**
	 * The block is notified that a new value has appeared on one of its input anchors.
	 * Save the value as a local variable.
	 * @param vcn notification of the new value.
	 */
	@Override
	public void acceptValue(IncomingNotification vcn) {
		super.acceptValue(vcn);
		QualifiedValue qv = vcn.getValue();
		if( qv!=null && qv.getValue()!=null ) {
			log.infof("%s.acceptValue value .... %s=%s",TAG,qv.getClass().getName(),qv.getValue().toString());
			value = qv;	
		}
		else if( qv!=null ) {
			log.errorf("%s.acceptValue ERROR: expected qualified value got a.... %s=%s",TAG,qv.getClass().getName(),qv.toString());
			value = new BasicQualifiedValue(qv);	
		}
		else {
			log.infof("%s.acceptValue:incoming value .... is NULL",TAG);
			value = new BasicQualifiedValue("unset");
		}
	}
}