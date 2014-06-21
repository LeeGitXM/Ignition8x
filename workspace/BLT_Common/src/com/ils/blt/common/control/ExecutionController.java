/**
 *   (c) 2014  ILS Automation. All rights reserved.
 *  
 *   The block controller is designed to be called from the client
 *   via RPC. All methods must be thread safe,
 */
package com.ils.blt.common.control;

import java.util.UUID;

import com.ils.common.watchdog.Watchdog;
import com.inductiveautomation.ignition.common.model.values.QualifiedValue;


/**
 *  This interface describes a controller that accepts change notifications
 *  from the blocks and acts as a delegate for facilities that are not
 *  within the Block Definition project. 
 */
public interface ExecutionController  {

	public void acceptBroadcastNotification(BroadcastNotification note);
	public void acceptCompletionNotification(OutgoingNotification note);
	public void acceptConnectionPostNotification(ConnectionPostNotification note);
	public void sendPropertyNotification(String id, String propertyName, QualifiedValue val);
	public void pet(Watchdog dog);
	public void removeWatchdog(Watchdog dog);
	public void updateTag(UUID diagramId,String path,QualifiedValue val);
}