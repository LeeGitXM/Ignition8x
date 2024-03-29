/**
 *   (c) 2012  ILS Automation. All rights reserved.
 *  
 *   The tag factory is designed to be called from Python to
 *   create a Python-ready tag object.
 */
package com.ils.blt.client;

import com.inductiveautomation.ignition.client.gateway_interface.PushNotificationListener;
import com.inductiveautomation.ignition.common.gateway.messages.PushNotification;
import com.inductiveautomation.ignition.common.util.LogUtil;
import com.inductiveautomation.ignition.common.util.LoggerEx;


/**
 *  The controller is a singleton used to manage interactions between sequential control blocks
 *  in the Designer scope with the model of these blocks in the Gateway. The handler maintains a map of 
 *  blocks, by name, and uses this to apply property value changes as indicated
 *  by the Gateway.
 *  
 *  The moduleId used within the calls refers to the module that has the handler for the 
 *  method that is invoked.
 */
public class GatewayClientDelegate implements PushNotificationListener {
	private static String TAG = "GatewayDelegate: ";
	private LoggerEx log = null;

	
	/**
	 * The delegate ....
	 */
	public GatewayClientDelegate() {
		super();
		log = LogUtil.getLogger(getClass().getPackage().getName());

	}
	

	
	/**
	 * Receive notification from the gateway. We are interested in specific messages
	 * addressed to a block. We must match:
	 *   1) The module ID
	 *   2) The message type
	 *   3) The workspace UUID
	 *   4) The block name
	 *   Update the block state.
	 */
	@Override
	public void receiveNotification(PushNotification notice) {
		//String type = notice.getMessageType();
		//Object payload = notice.getMessage();

		/*
		if(!type.equalsIgnoreCase(ILSProperties.GATEWAY_BLOCK_STATE_MESSAGE)) return;
		try {
			Properties props = (Properties) payload;
			String model = props.getProperty(ILSProperties.MSG_WORKSPACE_ID);
			String name  = props.getProperty(ILSProperties.MSG_BLOCK_NAME);
			String state = props.getProperty(ILSProperties.MSG_BLOCK_STATE);
			// The workspace ID must match the model
			if( model.equals(wksp.getParentFolderUUID())) {
				List<AbstractCoreComponent> components = new ArrayList<AbstractCoreComponent>();
				wksp.findModelComponents(wksp.getRootContainer(),components);
				for( AbstractCoreComponent blk:components ) {
					if( blk.getName().equals(name) ) {
						blk.setPropertyValue(ILSProperties.MSG_BLOCK_STATE, state);
						break;
					}
				}
			}
		}
		catch(ClassCastException cce ) {}  // Ignore
		*/
	}
}
