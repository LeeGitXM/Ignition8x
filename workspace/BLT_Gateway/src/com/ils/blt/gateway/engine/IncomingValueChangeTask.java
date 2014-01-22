/**
 *   (c) 2013  ILS Automation. All rights reserved. 
 */
package com.ils.blt.gateway.engine;

import com.ils.block.ProcessBlock;
import com.ils.block.control.ValueChangeNotification;

/**
 * A value has been received as an output from a block
 * connected to the target block. Record the new input then
 * evaluate the specified block. The thread should end with the block placing a
 * value on its output. In this case, the block responds to a property change, 
 * usually for a tag that is an attribute of the block.
 */
public class IncomingValueChangeTask implements Runnable{
	private final ProcessBlock block;
	/**
	 * Constructor.
	 * 
	 * @param vcn notification describing the new value
	 */
	public IncomingValueChangeTask(ValueChangeNotification vcn)  {
		this.block = vcn.getBlock();
		block.setValue(vcn);
	}
	
	public void run()   { 
		block.evaluate();
	}
}
