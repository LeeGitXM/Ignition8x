/**
 *   (c) 2012-2013  ILS Automation. All rights reserved. 
 */
package com.ils.blt.gateway.engine;

import java.util.Hashtable;
import java.util.UUID;

import org.w3c.dom.Element;

import com.ils.block.ProcessBlock;
import com.ils.block.control.NewValueNotification;
import com.ils.blt.common.serializable.SerializableDiagram;
import com.ils.connection.Connection;
import com.inductiveautomation.ignition.common.model.values.QualifiedValue;
import com.inductiveautomation.ignition.common.util.LogUtil;
import com.inductiveautomation.ignition.common.util.LoggerEx;

/**
 * The diagram model is the "model" that encapsulates the structure of the blocks and connections
 * of a ProcessDiagramView as viewed in the Designer.
 *  
 * This class provides answers to questions that the model control may ask about "what's next?".  
 * 
 *  The document is constant for the life of this instance.
 */
public class ProcessDiagram {
	
	private static String TAG = "ProcessDiagram";
	private final LoggerEx log;
	private final SerializableDiagram diagram;
	private boolean valid = false;
	private final long projectId;
	private final long resourceId;
	private final Hashtable<UUID,ProcessBlock> blocks;
	private final Hashtable<String,Connection> connections;   // Key by connection number
	private final Hashtable<String,Connection> connectionsBySource;   // Key by source:port
	
	
	/**
	 * Constructor: Create a model that encapsulates the structure of the blocks and connections
	 *              of a diagram.
	 * @param dom the unserialized object that represents the diagram. 
	 */
	public ProcessDiagram(SerializableDiagram dom,long proj,long res) { 
		this.diagram = dom;
		this.projectId = proj;
		this.resourceId = res;
		log = LogUtil.getLogger(getClass().getPackage().getName());
		blocks = new Hashtable<UUID,ProcessBlock>();
		connections = new Hashtable<String,Connection>();
		connectionsBySource = new Hashtable<String,Connection>();
		analyze(diagram);
	}
	
	public ProcessBlock getBlock(UUID uuid) { return blocks.get(uuid); }
	
	/**
	 * Analyze the diagram for nodes.
	 */
	private void analyze(SerializableDiagram diagram) {
		log.debugf("%s: analyze ....%d:%d",TAG,projectId,resourceId);
		/*
		Node root = doc.getDocumentElement();
		if( root!=null && root.getNodeName().equalsIgnoreCase("mxGraphModel")) {
			// The next node is a gratuitous root
			root = root.getFirstChild();
			if( root!=null && root.getNodeType()==Node.ELEMENT_NODE) {
				NodeList cells = ((Element)root).getElementsByTagName("mxCell");
				Node node = null;
				Element cell = null;
				for( int index=0;index<cells.getLength();index++) {
					node = cells.item(index);
					if( node.getNodeType()==Node.ELEMENT_NODE) {
						cell = (Element)node;
						if( !cell.getAttribute("vertex").isEmpty()) {
							String id = cell.getAttribute("id");
							if( !id.isEmpty() ) {
								log.debugf("%s: analyze adding block %s",TAG,id);
								ProcessBlock block = blockFromElement(cell);
								if( block!=null ) {
									blocks.put(id, block);
								}
							}
						}
						else if( !cell.getAttribute("edge").isEmpty()) {
							String source = cell.getAttribute("source");
							if( !source.isEmpty() ) {
								log.debugf("%s: analyze adding connection from %s",TAG,source);
								Connection cxn = connectionFromElement(cell);
								if( cxn!=null) {
									String key = String.format("%s:%s",source,cxn.getUpstreamPortName());
									connectionsBySource.put(key, cxn);
									String id = cell.getAttribute("id");
									if( !id.isEmpty() ) {
										connections.put(id, cxn);
									}
								}
							}
						}
					}
				}
			}
 		
		}
			*/
	}

	/**
	 * @return a Connection from the diagram given its id.
	 */
	public Connection getConnection(String id) { return connections.get(id); }
	
	/**
	 * The subject block has just placed a new result on an output port. Determine which input block
	 * and port, if any, is connected to the output. There is, at most one. A null return indicates no downstream connection.
	 * @param new value notification of an incoming change
	 * @return a new value notification for the downstream block
	 */
	public NewValueNotification getValueUpdate(NewValueNotification incoming) {
		ProcessBlock block = incoming.getBlock();
		String port = incoming.getPort();
		QualifiedValue value = incoming.getValue();
		
		NewValueNotification nvn = null;
		String key = String.format("%s:%s",String.valueOf(block.getBlockId()),port);
		Connection cxn = connections.get(key);
		if( cxn!=null ) {
			//ProcessBlock blk = BlockExecutionController.getInstance().getBlock(cxn.getSource());
			//if( blk!=null) {
			//	nvn = new NewValueNotification(blk,cxn.getUpstreamPortName(),value);
			//}	
		}
		
		return nvn;
	}
	
	/**
	 * Report on whether or not the DOM contained more than one connected node.
	 */
	public boolean isValid() { return valid; }
}