/**
 *   (c) 2015,2018 ILS Automation. All rights reserved.
 *  
 */
package com.ils.blt.common.script;


/**
 *  These are constants dealing with the scripting extensions
 */
public interface ScriptConstants   {
	// The applications and families exist in the NavTree and are not really 
	// block classes. Here are the "className" keys that we use for extensions
	public final static String APPLICATION_CLASS_NAME  = "com.ils.application";
	public final static String FAMILY_CLASS_NAME       = "com.ils.family";
	public final static String DIAGRAM_CLASS_NAME      = "com.ils.diagram";
	// There are these scripts for each class with extensions. 
	public final static String NODE_DELETE_SCRIPT      = "node-delete-script";
	public final static String NODE_RENAME_SCRIPT      = "node-rename-script";
	public final static String NODE_SAVE_SCRIPT        = "node-save-script";
	public final static String PROPERTY_GET_SCRIPT     = "property-get-script";
	public final static String PROPERTY_SET_SCRIPT     = "property-set-script";

	// These are the keys for individual sub-dictionaries
	public final static String ARGS_KEY   = "arglist";
	public final static String ENTRY_KEY  = "entry";
	public final static String SCRIPT_KEY = "script";
	
	// These are standard names of properties
	public static final String PROPERTY_NAME        = "Name";
}
