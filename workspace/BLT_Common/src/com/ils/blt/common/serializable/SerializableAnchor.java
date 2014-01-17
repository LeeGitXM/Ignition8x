package com.ils.blt.common.serializable;


import java.util.UUID;

import com.ils.block.common.AnchorDirection;
import com.inductiveautomation.ignition.common.util.LogUtil;
import com.inductiveautomation.ignition.common.util.LoggerEx;


/**
 * Implement a plain-old-java-object representing an anchor point
 * that is serializable via the Ignition XML serializer.
 */
public class SerializableAnchor {
	private final static String TAG = "SerializableAnchor";
	private AnchorDirection direction;   // 0=>Origin, 1=>Terminus
	private Object id = null;
	private String display = null;
	private UUID parentId = null;
	private LoggerEx log = LogUtil.getLogger(getClass().getPackage().getName());
	
	public SerializableAnchor() {
	}
	
	public Object getId() { return id; }
	public AnchorDirection getDirection()   { return direction; }
	public String getDisplay(){ return display; }
	public UUID getParentId() { return parentId; }
	
	public void setId(Object identifier) { id=identifier; }
	public void setDirection(AnchorDirection t)   { direction=t; }
	public void setDisplay(String text){display=text; }
	public void setParentId(UUID id) { parentId = id; };

	// So that class may be used as a map key
	// Same name and parent is sufficient to prove equality
	@Override
	public boolean equals(Object arg) {
		boolean result = false;
		if( arg instanceof SerializableAnchor) {
			SerializableAnchor that = (SerializableAnchor)arg;
			if( this.id.equals(that.id) &&
				this.parentId.equals(that.getParentId())   ) {
				result = true;
			}
		}
		log.info(toString()+" equals "+arg.toString()+" "+result);
		return result;
	}
	
	@Override
	public String toString() {
		return String.format("%s: %s (%s)",TAG,id.toString(),(direction==AnchorDirection.INCOMING?"Incoming":"Outgoing"));
	}

}
