/**
 *   (c) 2014-2015  ILS Automation. All rights reserved. 
 */
package com.ils.blt.gateway.test.mock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.ils.block.ProcessBlock;
import com.ils.blt.common.DiagramState;
import com.ils.blt.common.block.AnchorPrototype;
import com.ils.blt.common.block.CoreBlock;
import com.ils.blt.common.connection.ConnectionType;
import com.ils.blt.common.connection.ProcessConnection;
import com.ils.blt.common.notification.BroadcastNotification;
import com.ils.blt.common.notification.SignalNotification;
import com.ils.blt.common.serializable.SerializableDiagram;
import com.ils.blt.gateway.BasicDiagram;

/**
 * A mock diagram is a process diagram, specially created for functional testing
 * of blocks. 
 */
public class MockDiagram extends BasicDiagram {
	private static final long serialVersionUID = 1603451661866792378L;
	private static String TAG = "MockDiagram";
	private ProcessBlock uut = null;    // Unit under test
	
	/**
	 * Constructor: Create a model that encapsulates the structure of the blocks and connections
	 *              of a diagram.
	 * @param diagm the unserialized object that represents the diagram.
	 * @param parent 
	 */
	public MockDiagram(SerializableDiagram diagm,UUID parent,long projId) { 
		super(diagm,parent,projId);
	}

	public void addBlock(ProcessBlock block) {
		if( !(block instanceof MockInputBlock || block instanceof MockOutputBlock) ) {
			uut = block;
			block.setTimer(controller.getTimer());
		}
		try {
			this.blocks.put(block.getBlockId(), block);
		}
		catch(Exception ex) {
			log.errorf("%s.addBlock: Failed for %s=%s (%s)",TAG,block.getClass().getName(),block.getBlockId(),ex.getMessage());
		}
	}
	/**
	 * Compute connections based on the collection of input/output blocks.
	 *  Create connections between these and the unit-under-test.
	 */
	public void analyze() {
		log.infof("%s.analyze: Block-under-test is %s",TAG,uut.getName());
		if(uut!=null) {
			for(CoreBlock block:this.getDiagramBlocks()) {
				if( block instanceof MockInputBlock ) {
					MockInputBlock mib = (MockInputBlock)block;
					AnchorPrototype anchor = getAnchorForPort(mib.getPort());
					if( anchor!=null ) {
						ProcessConnection pc = 
								new ProcessConnection(ConnectionType.connectionTypeForPropertyType(mib.getPropertyType()));
						pc.setSource(mib.getBlockId());
						pc.setTarget(uut.getBlockId());
						pc.setDownstreamPortName(mib.getPort());
						pc.setUpstreamPortName(mib.getPort());
						this.addOutgoingConnection(pc);
					}
					else {
						log.warnf("%s.analyze: Block-under-test does not have an input port %s",TAG,mib.getPort());
					}
					
				}
				else if( block instanceof MockOutputBlock ) {
					MockOutputBlock mob = (MockOutputBlock)block;
					AnchorPrototype anchor = getAnchorForPort(mob.getPort());
					if( anchor!=null ) {
						ProcessConnection pc = 
								new ProcessConnection(ConnectionType.connectionTypeForPropertyType(mob.getPropertyType()));
						pc.setSource(uut.getBlockId());
						pc.setTarget(mob.getBlockId());
						pc.setDownstreamPortName(mob.getPort());
						pc.setUpstreamPortName(mob.getPort());
						this.addOutgoingConnection(pc);
					}
					else {
						log.warnf("%s.analyze: Block-under-test does not have an output port %s",TAG,mob.getPort());
					}
				}
			}
		}
	}

	public ProcessBlock getBlockUnderTest() { return uut; }
	
	/**
	 * For our purposes, the diagram is always active.
	 */
	@Override
	public DiagramState getState() { return DiagramState.ACTIVE; }
	
	/**
	 * Return the nth MockInputBlock connected to the named port. The
	 * connections are numbered in the order in which they were defined.
	 * @param port
	 * @param index of the connection to the named port. Zero-based.
	 * @return
	 */
	public MockInputBlock getInputForPort(String port,int index) {
		MockInputBlock result = null;
		int count = 0;
		for(CoreBlock block:getDiagramBlocks()) {
			if(block instanceof MockInputBlock) {
				MockInputBlock mib = (MockInputBlock)block;
				if(mib.getPort().equalsIgnoreCase(port)) {
					if( count==index) {
						result = mib;
						break;
					}
					count++;
				}
			}
		}
		return result;
	}
	
	public MockOutputBlock getOutputForPort(String port) {
		MockOutputBlock result = null;
		for(CoreBlock block:getDiagramBlocks()) {
			if(block instanceof MockOutputBlock) {
				MockOutputBlock mob = (MockOutputBlock)block;
				if(mob.getPort().equalsIgnoreCase(port)) {
					result = mob;
					break;
				}
			}
		}
		return result;
	}
	/**
	 * Define a new connection. We are guaranteed that there will be only one for a block.
	 * This is a special interface for the testing MockDiagram.
	 */
	private void addOutgoingConnection(ProcessConnection pc) {
		CoreBlock pb = getBlock(pc.getSource());
		if( pb!=null ) {
			BlockPort key = new BlockPort(pb,pc.getUpstreamPortName());
			List<ProcessConnection> list = new ArrayList<ProcessConnection>();
			list.add(pc);
			outgoingConnections.put(key,list);
		}
	}
	/**
	 * Return the anchor associated with the UUT's port of a specified name.
	 * The anchor can be either incoming or outgoing.
	 * @param port
	 * @return
	 */
	private AnchorPrototype getAnchorForPort(String port) {
		AnchorPrototype anchor = null;
		if( uut!=null) {
			for(AnchorPrototype anc:uut.getBlockPrototype().getBlockDescriptor().getAnchors()) {
				if(anc.getName().equalsIgnoreCase(port)) {
					anchor = anc;
					break;
				}
			}
		}
		return anchor;
	}

	@Override
	public void createBlocks(SerializableDiagram diagram) {
	}

	@Override
	public Collection<SignalNotification> getBroadcastNotifications(BroadcastNotification incoming) {
		return null;
	}

	@Override
	public void setState(DiagramState s) {
		this.state = s;  // No side effects
	}

	@Override
	public void updateBlockTimers() {
	}

	@Override
	public void updateProperties(SerializableDiagram diagram) {
	}
}