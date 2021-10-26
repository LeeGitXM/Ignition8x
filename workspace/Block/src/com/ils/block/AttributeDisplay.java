/**
 *   (c) 2021  ILS Automation. All rights reserved. 
 */
package com.ils.block;

import java.util.UUID;

import com.ils.block.annotation.ExecutableBlock;
import com.ils.blt.common.ProcessBlock;
import com.ils.blt.common.block.BlockConstants;
import com.ils.blt.common.block.BlockDescriptor;
import com.ils.blt.common.block.BlockProperty;
import com.ils.blt.common.block.BlockStyle;
import com.ils.blt.common.block.PropertyType;
import com.ils.blt.common.control.ExecutionController;
import com.ils.blt.common.notification.BlockPropertyChangeEvent;

/**
 * This is a pseudo-block that displays the current value of another, associated
 * block. The "value" property is what is displayed. The gateway version of this block
 * has no behavior. It only exists to support serialization. The designer version of 
 * the block is a BlockAttributeView.
 */
@ExecutableBlock
public class AttributeDisplay extends AbstractProcessBlock implements ProcessBlock {
	private static final String CLSS = "AttributeDisplay";
	public static final String DEFAULT_FONT = "SansSerif";      // Font family - Serif, Dialog,Monospaced

	/**
	 * Constructor: The no-arg constructor is used when creating a prototype for use in the palette.
	 */
	public AttributeDisplay() {
		initialize();
		initializePrototype();
	}
	
	/**
	 * Constructor. 
	 * 
	 * @param ec execution controller for handling block output
	 * @param parent universally unique Id identifying the parent of this block
	 * @param block universally unique Id for the block
	 */
	public AttributeDisplay(ExecutionController ec,UUID parent,UUID block) {
		super(ec,parent,block);
		initialize();
	}
	/**
	 * Guarantee that the class name matches the constant used throughout
	 * the application to identify an attribute display.
	 */
	@Override
	public String getClassName() { return BlockConstants.BLOCK_CLASS_ATTRIBUTE; }
	
	/**
	 * Handle a change to the property value
	 */
	@Override
	public void propertyChange(BlockPropertyChangeEvent event) {
		super.propertyChange(event);
	}
	@Override
	public void notifyOfStatus() {}
	@Override
	public void propagate() {}
	/**
	 * Add properties that are new for this class.
	 * Populate them with default values.
	 */
	private void initialize() {
		setName(CLSS);
		// These two properties define which property to display
		BlockProperty blockId = new BlockProperty(BlockConstants.ATTRIBUTE_PROPERTY_BLOCK_ID,"", PropertyType.STRING, false);
		setProperty(BlockConstants.ATTRIBUTE_PROPERTY_BLOCK_ID, blockId);
		BlockProperty property = new BlockProperty(BlockConstants.ATTRIBUTE_PROPERTY_PROPERTY,"Name", PropertyType.STRING, false);
		setProperty(BlockConstants.ATTRIBUTE_PROPERTY_PROPERTY, property);
		BlockProperty value = new BlockProperty(BlockConstants.ATTRIBUTE_PROPERTY_VALUE,"", PropertyType.STRING, false);
		setProperty(BlockConstants.ATTRIBUTE_PROPERTY_VALUE, value);
		
		// These attributes defined how the display is configured
		BlockProperty backgroundColor = new BlockProperty(BlockConstants.ATTRIBUTE_PROPERTY_BACKGROUND_COLOR, "TRANSPARENT", PropertyType.COLOR,true);
		setProperty(BlockConstants.ATTRIBUTE_PROPERTY_BACKGROUND_COLOR, backgroundColor);
		BlockProperty foregroundColor = new BlockProperty(BlockConstants.ATTRIBUTE_PROPERTY_FOREGROUND_COLOR, "BLACK", PropertyType.COLOR,true);
		setProperty(BlockConstants.ATTRIBUTE_PROPERTY_FOREGROUND_COLOR, foregroundColor);
		BlockProperty height = new BlockProperty(BlockConstants.ATTRIBUTE_PROPERTY_HEIGHT, BlockConstants.ATTRIBUTE_DISPLAY_HEIGHT, PropertyType.INTEGER,true);
		setProperty(BlockConstants.ATTRIBUTE_PROPERTY_HEIGHT, height);		
		BlockProperty formatProperty = new BlockProperty(BlockConstants.ATTRIBUTE_PROPERTY_FORMAT, "%s", PropertyType.STRING,true);
		setProperty(BlockConstants.ATTRIBUTE_PROPERTY_FORMAT, formatProperty);
		BlockProperty fontSizeProperty = new BlockProperty(BlockConstants.ATTRIBUTE_PROPERTY_FONT_SIZE, 14, PropertyType.INTEGER,true);
		setProperty(BlockConstants.ATTRIBUTE_PROPERTY_FONT_SIZE, fontSizeProperty);
		BlockProperty offsetx = new BlockProperty(BlockConstants.ATTRIBUTE_PROPERTY_OFFSET_X, 0, PropertyType.INTEGER,false);
		setProperty(BlockConstants.ATTRIBUTE_PROPERTY_OFFSET_X, offsetx);		
		BlockProperty offsety = new BlockProperty(BlockConstants.ATTRIBUTE_PROPERTY_OFFSET_Y, 0, PropertyType.INTEGER,false);
		setProperty(BlockConstants.ATTRIBUTE_PROPERTY_OFFSET_Y, offsety);
		BlockProperty width = new BlockProperty(BlockConstants.ATTRIBUTE_PROPERTY_WIDTH, BlockConstants.ATTRIBUTE_DISPLAY_WIDTH, PropertyType.INTEGER,true);
		setProperty(BlockConstants.ATTRIBUTE_PROPERTY_WIDTH, width);
	}
	
	/**
	 * Augment the palette prototype for this block class. The block does not appear on the paletter.
	 */
	private void initializePrototype() {
		prototype.setPaletteIconPath("");
		prototype.setPaletteLabel("");
		prototype.setTooltipText("");
		prototype.setTabName(BlockConstants.PALETTE_TAB_NONE);

		BlockDescriptor desc = prototype.getBlockDescriptor();
		desc.setBlockClass(getClass().getCanonicalName());
		desc.setStyle(BlockStyle.ATTRIBUTE);
		desc.setPreferredHeight(BlockConstants.ATTRIBUTE_DISPLAY_HEIGHT);
		desc.setPreferredWidth(BlockConstants.ATTRIBUTE_DISPLAY_WIDTH);
	}
	
}