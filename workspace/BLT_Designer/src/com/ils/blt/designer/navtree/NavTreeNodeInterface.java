/**
 *   (c) 2013-2014  ILS Automation. All rights reserved. 
 */
package com.ils.blt.designer.navtree;



/**
 * This interface defines a NavTreeNode with methods used by the NodeStatusManager. 
 */
public interface NavTreeNodeInterface  {
	/**
	 * There are times when the project resource has been delete 
	 * and all we have to go on is the resourceId
	 * @return the resource identified with the node when it was created.
	 */
	public long getResourceId();
	/**
	 * Clean up any linkages in preparation for the node being deleted.
	 */
	public void prepareForDeletion();
	/**
	 * @return flag true if the node is locally out-of-sync with the gateway 
	 */
	public boolean isDirty();
	/**
	 * Notify the node if it is in/out of sync with the gateway 
	 * @param flag true if the node is out-of-sync with the gateway 
	 */
	public void updateUI(boolean flag);
}