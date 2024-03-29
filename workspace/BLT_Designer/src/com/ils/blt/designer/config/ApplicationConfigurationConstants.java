/**
 *   (c) 2015  ILS Automation. All rights reserved.
 */
package com.ils.blt.designer.config;

import java.awt.Dimension;



/**
 *  These constants refer to the order of individual panes
 *  in the block editor's sliding pane.
 */
public interface ApplicationConfigurationConstants   {
	// Indices for the sub-panes. We add them in this order ...
	public static final int HOME = 0;
	public static final int OUTPUTS = 1;
	public static final int EDITOR = 2;
	public static final int TAGSELECTOR = 3;
	
	// Some universal sizes
	public static final Dimension BUTTON_SIZE  = new Dimension(80,36);
	public static final Dimension COMBO_SIZE  = new Dimension(300,24);
	public static final Dimension EDIT_BUTTON_SIZE  = new Dimension(60,36);
	
	public static int DIALOG_HEIGHT = 500;
	public static int DIALOG_WIDTH = 440;
	
}
