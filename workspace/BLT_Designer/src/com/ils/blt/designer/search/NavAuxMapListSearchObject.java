package com.ils.blt.designer.search;

import java.awt.Dimension;
import java.awt.Image;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import com.inductiveautomation.ignition.client.images.ImageLoader;
import com.inductiveautomation.ignition.client.util.gui.ErrorUtil;
import com.inductiveautomation.ignition.common.project.resource.ProjectResourceId;
import com.inductiveautomation.ignition.common.util.LogUtil;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.designer.findreplace.SearchObject;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
/**
 * We treat the list of maps as a single colon,comma-delimited string
 * @author chuckc
 *
 */
public class NavAuxMapListSearchObject implements SearchObject {
	private final String CLSS = "NavAuxMapListSearchObject";
	private final LoggerEx log;
	private static final Dimension IMAGE_SIZE = new Dimension(18,18);
	private final String key;
	private final List<Map<String,String>> list;
	private final String parentName;
	private final String nodeName;
	private final ProjectResourceId parentId;
	private final DesignerContext context;
	private final ResourceBundle rb;
	
	public NavAuxMapListSearchObject(DesignerContext ctx,String k,List<Map<String,String>> data,ProjectResourceId parent,String node) {
		this.context = ctx;
		this.key = k;
		this.list = data;
		this.parentName = parent.getResourcePath().getName();
		this.nodeName = node;
		this.parentId = parent;
		this.rb = ResourceBundle.getBundle("com.ils.blt.designer.designer");  // designer.properties
		this.log = LogUtil.getLogger(getClass().getPackage().getName());
	}
	@Override
	public Icon getIcon() {
		ImageIcon icon = null;
		Image img = ImageLoader.getInstance().loadImage("Block/icons/palette/blank_analysis.png",IMAGE_SIZE);
		if( img !=null) icon = new ImageIcon(img);
		return icon;
	}

	@Override
	public String getName() {
		return "AuxData: "+key;
	}

	@Override
	public String getOwnerName() {
		return parentName+":"+nodeName;
	}

	@Override
	public String getText() {
		StringBuilder builder = new StringBuilder();
		for(Map<String,String> map:list) {
			if(builder.length()>0) builder.append(",");
			for(String key:map.keySet()) {
				builder.append("("+key+":"+map.get(key)+")");
			}
		}
		return builder.toString();
	}

	@Override
	public void locate() {
		NavTreeLocator locator = new NavTreeLocator(context);
		if( parentId!=null) {
		locator.locate(parentId,nodeName);
		}
		else {
			locator.locate(nodeName);
		}
	}

	@Override
	public void setText(String arg0) throws IllegalArgumentException {
		ErrorUtil.showWarning(rb.getString("Locator.AuxChangeWarning"),rb.getString("Locator.WarningTitle") ,false);
	}

}
