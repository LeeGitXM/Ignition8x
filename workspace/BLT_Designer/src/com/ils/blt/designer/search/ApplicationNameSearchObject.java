package com.ils.blt.designer.search;

import java.awt.Dimension;
import java.awt.Image;
import java.util.ResourceBundle;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import com.inductiveautomation.ignition.client.images.ImageLoader;
import com.inductiveautomation.ignition.client.util.gui.ErrorUtil;
import com.inductiveautomation.ignition.common.util.LogUtil;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.designer.findreplace.SearchObject;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
/**
 * Simply return the diagram name for editing.
 * @author chuckc
 *
 */
public class ApplicationNameSearchObject implements SearchObject {
	private final String TAG = "ApplicationNameSearchObject";
	private final LoggerEx log;
	private static final Dimension IMAGE_SIZE = new Dimension(18,18);
	private final String applicationName;
	private final String rootName;
	private final DesignerContext context;
	private final ResourceBundle rb;
	
	public ApplicationNameSearchObject(DesignerContext ctx,String root,String app) {
		this.context = ctx;
		this.applicationName = app;
		this.rootName = root;
		this.log = LogUtil.getLogger(getClass().getPackage().getName());
		this.rb = ResourceBundle.getBundle("com.ils.blt.designer.designer");  // designer.properties
	}
	@Override
	public Icon getIcon() {
		ImageIcon icon = null;
		Image img = ImageLoader.getInstance().loadImage("Block/icons/navtree/application_folder_closed.png",IMAGE_SIZE);
		if( img !=null) icon = new ImageIcon(img);
		return icon;
	}

	@Override
	public String getName() {
		return applicationName;
	}

	@Override
	public String getOwnerName() {
		return rootName;
	}

	@Override
	public String getText() {
		return applicationName;
	}

	@Override
	public void locate() {
		NavTreeLocator locator = new NavTreeLocator(context);
		locator.locate(applicationName);
		
	}

	@Override
	public void setText(String arg0) throws IllegalArgumentException {
		ErrorUtil.showWarning(rb.getString("Locator.ApplicationChangeWarning"),rb.getString("Locator.WarningTitle") ,false);
	}

}
