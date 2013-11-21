/**
 *   (c) 2013  ILS Automation. All rights reserved.
 */
package com.ils.blt.designer.graphics;

import java.util.List;

import javax.swing.ImageIcon;

import com.ils.block.annotation.ExecutableBlock;
import com.ils.block.classes.ClassList;
import com.ils.blt.common.BLTProperties;
import com.ils.jgx.editor.EditorPalette;
import com.ils.jgx.editor.JgxPalette;
import com.inductiveautomation.ignition.common.util.LogUtil;
import com.inductiveautomation.ignition.common.util.LoggerEx;


/**
 *  Define the blocks that will appear in the Diagnostics palette.
 */
public class PaletteBlocks  {
	public final static String TAG = "PaletteBlocks";
	public static LoggerEx log = LogUtil.getLogger(PaletteBlocks.class.getPackage().getName());

	/**
	 * Add templates to the supplied palette. 
	 * @param palete the EditorPalette that we are populating
	 */
	public static void populatePalette(EditorPalette palette) {
		log.infof("%s: populatePalette ...",TAG);
		ClassList lister = new ClassList();
		try {
			List<Class<?>> list = lister.getAnnotatedClasses(BLTProperties.BLOCK_JAR_NAME_PATTERN,ExecutableBlock.class);
		}
		catch(Exception ex) {
			log.infof("%s: populatePalette getClasses exception(%s)",TAG,ex.getMessage());
		}

		// Adds some template cells for dropping into the graph
		// NOTE:  First string is the label in the palette
		//        Next string is style properties
		//        Final string is an initial JSON string. The JSON 
		//        represents a dictionary of attribute dictionaries.
		String json = null;
		json = "{\"class\":{\"value\":\"app.diagnostics.classes.Entry\"," +
				"\"editable\":\"False\"}," +
				"\"label\":{\"value\":\"Entry\"}}";		
		palette.addTemplate(
						"Entry",
						new ImageIcon(
								JgxPalette.class
										.getResource("/com/ils/jgx/images/link.png")),
						"roundImage;image=/com/ils/jgx/images/link.png",
						80, 80, json);
		
		json = "{\"class\":{\"value\":\"app.diagnostics.classes.Diagnosis\"," +
				"\"editable\":\"False\"}," +
				"\"label\":{\"value\":\"Diagnosis\"}}";	

		palette.addTemplate(
					"Diagnosis",
					new ImageIcon(
							JgxPalette.class
									.getResource("/com/ils/jgx/images/rectangle.png")),
					null, 180, 120, json);
		
		json = "{\"class\":{\"value\":\"app.diagnostics.classes.Recommendation\"," +
				"\"editable\":\"False\"}," +
				"\"label\":{\"value\":\"Recommendation\"}}";	
		palette.addTemplate(
						"Recommendation",
						new ImageIcon(
								JgxPalette.class
										.getResource("/com/ils/jgx/images/cloud.png")),
						"ellipse;shape=cloud", 180, 120, json);
		
		json = "{\"class\":{\"value\":\"app.diagnostics.classes.Sum\"," +
				"\"editable\":\"False\"}," +
				"\"label\":{\"value\":\"Sum\"}}";		
		palette.addTemplate(
						"Sum",
						new ImageIcon(
								JgxPalette.class
										.getResource("/com/ils/jgx/images/rounded.png")),
						"rounded=1", 180, 120, json);
		
		json = "{\"class\":{\"value\":\"app.diagnostics.classes.And\"," +
				"\"editable\":\"False\"}," +
				"\"label\":{\"value\":\"+\"}}";		
		palette.addTemplate(
						"And",
						new ImageIcon(
								JgxPalette.class
										.getResource("/com/ils/jgx/images/rhombus.png")),
						"rhombus", 180, 120, json);
		
		json = "{\"type\":\"numeric\"}";		
		palette.addEdgeTemplate(
						"Numeric Cxn",
						new ImageIcon(
								JgxPalette.class
										.getResource("/com/ils/jgx/images/arrow_gray.png")),
						"numeric", 180, 120, json);
		
		json = "{\"type\":\"logical\"}";
		palette.addEdgeTemplate(
						"Logical Cxn",
						new ImageIcon(
								JgxPalette.class
										.getResource("/com/ils/jgx/images/arrow_green.png")),
						"logical", 180, 120, json);
		
		json = "{\"type\":\"text\"}";
		palette.addEdgeTemplate(
						"Text Cxn",
						new ImageIcon(
								JgxPalette.class
										.getResource("/com/ils/jgx/images/arrow_mustard.png")),
						"text", 180, 120, json);
		

	}
}