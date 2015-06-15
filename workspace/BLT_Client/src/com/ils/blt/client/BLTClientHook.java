/**
 *   (c) 2013  ILS Automation. All rights reserved. 
 */
package com.ils.blt.client;

import com.ils.blt.common.ModuleScriptFunctions;
import com.ils.blt.common.BLTProperties;
import com.inductiveautomation.ignition.client.gateway_interface.GatewayConnectionManager;
import com.inductiveautomation.ignition.client.model.ClientContext;
import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.expressions.ExpressionFunctionManager;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.common.script.ScriptManager;
import com.inductiveautomation.ignition.common.xmlserialization.deserialization.XMLDeserializer;
import com.inductiveautomation.vision.api.client.ClientModuleHook;

public class BLTClientHook implements ClientModuleHook {
	public static String HOOK_BUNDLE_NAME   = "client";      // Properties file is client.properties
	// Register separate properties files for designer things and block things
	static {
		BundleUtil.get().addBundle(BLTProperties.CUSTOM_PREFIX,ClientModuleHook.class,HOOK_BUNDLE_NAME);
	}
	/**
	 * Make the interface script functions available.
	 */
	@Override
	public void initializeScriptManager(ScriptManager mgr) {
		mgr.addScriptModule(BLTProperties.CLASSIC_SCRIPT_PACKAGE,ModuleScriptFunctions.class);
		mgr.addScriptModule(BLTProperties.SCHEMATIC_SCRIPT_PACKAGE,ModuleScriptFunctions.class);
	}

	@Override
	public void configureDeserializer(XMLDeserializer arg0) {
		// TODO Auto-generated method stub
	}
	
	@Override
	public void notifyActivationStateChanged(LicenseState arg0) {
		// TODO Auto-generated method stub	
	}

	@Override
	public void shutdown() {
		// TODO Auto-generated method stub
	}

	@Override
	public void startup(ClientContext ctx, LicenseState arg1) throws Exception {
		GatewayConnectionManager.getInstance().addPushNotificationListener(new GatewayClientDelegate());
	}
	
	@Override
	public void configureFunctionFactory(ExpressionFunctionManager factory) {

		
	}

}
