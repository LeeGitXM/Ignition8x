/**
 *   (c) 2021  ILS Automation. All rights reserved.
 *  
 */
package com.ils.blt.gateway.proxy;

/**
 * Execute a block reset() method. 
 */
public class OnSave extends Callback {

	public OnSave() {
		module = "onSave";
		setLocalVariableList("block");
	}

}

