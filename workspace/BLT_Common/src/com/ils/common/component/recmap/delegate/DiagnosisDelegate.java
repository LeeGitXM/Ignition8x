/**
 * Copyright 2016. ILS Automation. All rights reserved.
 */
package com.ils.common.component.recmap.delegate;

import java.awt.Image;
import java.util.Map;
import java.util.Properties;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import com.ils.common.component.recmap.RecMapConstants;
import com.ils.common.component.recmap.RecommendationMap;
import com.ils.common.component.recmap.TextDelegate;
import com.inductiveautomation.ignition.client.images.ImageLoader;

import prefuse.visual.VisualItem;

/**
 * Render the block that holds recommendations.
 */
//public class DiagnosisRenderer extends TableLabelRenderer {
	
public class DiagnosisDelegate implements TextDelegate {
	String badgePath = "Block/icons/badges/SQC.png";
	private final RecommendationMap recmap;
	private final Map<Integer,Properties> propertyMap;

    public DiagnosisDelegate(RecommendationMap rm,Map<Integer,Properties> propMap) {
    	this.recmap = rm;
    	this.propertyMap = propMap;
    }
    
    public Image getBadge(VisualItem item) { 
    	int row = item.getInt(RecMapConstants.ROW);
		Properties properties = propertyMap.get(new Integer(row));
		boolean hasSQC = false;
        
		if( properties!=null && properties.getProperty(RecMapConstants.HAS_SQC)!=null ) {
			hasSQC = properties.getProperty(RecMapConstants.HAS_SQC).equalsIgnoreCase("TRUE");
		}
		Image image = null;
		if( hasSQC ) {
			image = ImageLoader.getInstance().loadImage(badgePath,RecMapConstants.BADGE_SIZE);
		}
		return image;
    }
    /**
     * Returns the text to draw. multiple properties separated
     * by a blank lines.
     * @param item the item to represent as a <code>String</code>
     * @return a <code>String</code> to draw
     */
    @Override
    public String getBodyText(VisualItem item) {
    	int row = item.getInt(RecMapConstants.ROW);
		Properties properties = propertyMap.get(new Integer(row));
        StringBuilder sb = new StringBuilder();
        String problem = "";
		if( properties!=null && properties.getProperty(RecMapConstants.PROBLEM)!=null ) {
			problem = properties.getProperty(RecMapConstants.PROBLEM);
		}
        String multiplier = "0.0";
		if( properties!=null && properties.getProperty(RecMapConstants.MULTIPLIER)!=null ) {
			multiplier = properties.getProperty(RecMapConstants.MULTIPLIER);
		}
		sb.append(RecMapConstants.PROBLEM);
		sb.append(": ");
		sb.append(problem);
        sb.append("\n");
		sb.append(RecMapConstants.MULTIPLIER);
		sb.append(": ");
		sb.append(multiplier);
        sb.append("\n");
        return sb.toString();
    }
    /**
     * Returns the text that appears in the block header. 
     * @param item the item to represent as a <code>String</code>
     * @return a <code>String</code> to draw
     */
    @Override
    public String getHeaderText(VisualItem item) {
        StringBuilder sb = new StringBuilder();
		sb.append("Diagnosis\n");
        sb.append(item.getString(RecMapConstants.NAME)); 
        return sb.toString();
    }

	@Override
	public String getTooltipText(VisualItem item) {
		int row = item.getInt(RecMapConstants.ROW);
		Properties properties = propertyMap.get(new Integer(row));
		return getHtml(item,properties);
	}
	
	private String getHtml(VisualItem item,Properties properties) {
		String name = "DIAGNOSIS";
		if( item.canGetString(RecMapConstants.NAME) ) {
			name = item.getString(RecMapConstants.NAME);
		}
		String problem = "PROBLEM";
		if( properties!=null && properties.getProperty(RecMapConstants.PROBLEM)!=null ) {
			problem = properties.getProperty(RecMapConstants.PROBLEM);
		}
		String multiplier = "1.0";
		if( properties!=null && properties.getProperty(RecMapConstants.MULTIPLIER)!=null ) {
			String prop = properties.getProperty(RecMapConstants.MULTIPLIER);
			if( prop!=null) multiplier = String.valueOf(prop);
		}
		String html = 
			"<html>" + 
				"<div style=\"background:rgb(204,215,235);border-style:solid;border-width:3px 0px 0px 0px;border-color:rgb(250,250,250)\">" +
					"<center><h3>"+name+"</h3></center>" +
				"</div>" +
				"<div>" +
				"<table>" +
				"<tr>" +
				"<td>Problem:</td><td>"+problem+"</td>"+
				"</tr>" +
				"<tr>" +
				"<td>Multiplier:</td><td>"+multiplier+"</td>"+
				"</tr>" +
				"</table>" +
				"</div>" +
			"</html>";
		return html;
	}
	
	@Override
	public void addMenuItems(VisualItem item,JPopupMenu menu) {
		int row = item.getInt(RecMapConstants.ROW);
		Properties properties = propertyMap.get(new Integer(row));
		
		// Original dataset row
		int dsrow = item.getInt(RecMapConstants.DSROW);

		JMenuItem menuItem;
		menuItem = new JMenuItem(new ScriptAction(recmap,"expand","expandFinalDiagnosis",dsrow));
		menu.add(menuItem);
		menuItem = new JMenuItem(new ScriptAction(recmap,"hide","hideFinalDiagnosis",dsrow));
	    menu.add(menuItem);
	    
	    menuItem = new JMenuItem(new ScriptAction(recmap,"change multiplier","changeMultiplier",dsrow));
	    menu.add(menuItem);
	    
		if( properties!=null && properties.getProperty(RecMapConstants.HAS_SQC)!=null ) {
			if( properties.getProperty(RecMapConstants.HAS_SQC).equalsIgnoreCase("TRUE") ) {
				menuItem = new JMenuItem(new ScriptAction(recmap,"plot sqc","sqcPlot",row));
			    menu.add(menuItem);
			}
		}
	}
} 
