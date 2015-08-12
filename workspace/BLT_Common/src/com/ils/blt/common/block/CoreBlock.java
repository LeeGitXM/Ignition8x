/**
 *   (c) 2015  ILS Automation. All rights reserved. 
 */
package com.ils.blt.common.block;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.ils.blt.common.notification.BlockPropertyChangeEvent;
import com.ils.blt.common.notification.BlockPropertyChangeListener;
import com.ils.blt.common.notification.IncomingNotification;
import com.ils.blt.common.notification.SignalNotification;
import com.ils.blt.common.serializable.SerializableBlockStateDescriptor;


/**
 * This is the basic interface for a block in a diagram.
 * Each block carries its unique identity consisting of a projectId,
 * a diagramId and blockId - or alternatively a diagramId and blockId.
 * 
 * Additional interfaces are defined for more specific blocks
 */
public abstract interface CoreBlock extends BlockPropertyChangeListener {
	/**
	 * Notify the block that a new value has appeared on one of its
	 * input anchors. The notification contains the upstream source
	 * block, the port and value.
	 * @param vcn 
	 */
	public void acceptValue(IncomingNotification vcn);
	/**
	 * Notify the block that it is the recipient of a signal from
	 * "the ether". This signal is not associated with a connection.
	 * This method is meaningful only for blocks that are "receivers".
	 * @param sn 
	 */
	public void acceptValue(SignalNotification sn);
	/**
	 * In the case where the block has specified a coalescing time,
	 * this method will be called by the engine after receipt of input
	 * once the coalescing "quiet" time has passed without further input.
	 */
	public void evaluate();
	/**
	 * @return a list of anchor prototypes for the block.
	 */
	public List<AnchorPrototype> getAnchors();
	/**
	 * @return the universally unique Id of the block.
	 */
	public UUID getBlockId();
	/**
	 * @return information necessary to populate the block 
	 *          palette and subsequently paint a new block
	 *          dropped on the workspace.
	 */
	public PalettePrototype getBlockPrototype();
	/**
	 * @return the fully qualified path name of this block.
	 */
	public String getClassName();
	/**
	 * @return the block's label
	 */
	public String getName();
	
	/**
	 * @return the Id of the block's diagram (parent).
	 */
	public UUID getParentId();
	/**
	 * @return the id of the project under which this block was created.
	 */
	public long getProjectId() ;
	/**
	 * @return all properties of the block. The array may be used
	 * 			to updated properties directly.
	 */
	public BlockProperty[] getProperties();
	/**
	 * @return a particular property by name.
	 */
	public BlockProperty getProperty(String name);
	/**
	 * @return a list of names of properties known to this class.
	 */
	public Set<String> getPropertyNames() ;
	/**
	 * @return information related to the workings of the block.
	 *        The information returned varies depending on the 
	 *        block. At the very least the data contains the 
	 *        block UUID and class. The data is read-only.
	 */
	public SerializableBlockStateDescriptor getInternalStatus();
	/**
	 * Send status update notifications for any properties
	 * or output connections known to the designer. 
	 * 
	 * In practice, the block properties are all updated
	 * when a diagram is opened. It's the connection
	 * notification for animation that is most necessary.
	 */
	public void notifyOfStatus();

	//===================== PropertyChangeListener ======================
	/**
	 * This is a stricter implementation that enforces QualifiedValue data.
	 */
	public void propertyChange(BlockPropertyChangeEvent event);

	/**
	 * Reset block properties.
	 */
	public void reset();
	
	/**
	 * Set the anchor descriptors.
	 * @param prototypes
	 */
	public void setAnchors(List<AnchorPrototype> prototypes);

	/**
	 * @param name the name of the block. The name
	 *        is guaranteed to be unique within a 
	 *        diagram.
	 */
	public void setName(String name);
	/**
	 * @param id is the project to which this block belongs.
	 */
	public void setProjectId(long id);
	/**
	 * Accept a new value for a block property. It is up to the
	 * block to determine whether or not this triggers block 
	 * evaluation.
	 * @param name of the property to update
	 * @param value new value of the property
	 */
	public void setProperty(String name,Object value);
	
	/**
	 * Start any active monitoring or processing within the block.
	 */
	public void start();
	/**
	 * Terminate any active operations within the block.
	 */
	public void stop();
	/**
	 * Convert the block into a portable, serializable description.
	 * The descriptor holds common attributes of the block.
	 * @return the descriptor
	 */
	public SerializableBlockStateDescriptor toDescriptor();
	/**
	 * @param tagpath
	 * @return true if any property of the block is bound to
	 *         the supplied tagpath. The comparison does not
	 *         consider the provider portion of the path.
	 */
	public boolean usesTag(String tagpath);
	/**
	 * Check the block configuration for missing or conflicting
	 * information.
	 * @return a validation summary. Null if everything checks out.
	 */
	public String validate();
}