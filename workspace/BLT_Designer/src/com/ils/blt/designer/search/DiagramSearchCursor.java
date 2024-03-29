package com.ils.blt.designer.search;

import java.io.IOException;
import java.util.Iterator;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ils.blt.common.ApplicationRequestHandler;
import com.ils.blt.common.serializable.SerializableDiagram;
import com.ils.blt.designer.workspace.ProcessBlockView;
import com.ils.blt.designer.workspace.ProcessDiagramView;
import com.inductiveautomation.ignition.common.project.ProjectResource;
import com.inductiveautomation.ignition.common.util.LogUtil;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.designer.blockandconnector.model.Block;
import com.inductiveautomation.ignition.designer.findreplace.SearchObjectCursor;
import com.inductiveautomation.ignition.designer.model.DesignerContext;

public class DiagramSearchCursor extends SearchObjectCursor {
	private final String TAG = "DiagramSearchCursor";
	private final DesignerContext context;
	private ProcessDiagramView diagram; 
	private final LoggerEx log;
	private final long resId;
	private final boolean searchDiagrams;
	private final boolean searchBlocks;
	private final int searchKey;
	private String familyName;
	private int index = 0;
	
	public DiagramSearchCursor(DesignerContext ctx,long res,int key) {
		this.context = ctx;
		this.resId = res;
		this.searchKey = key;
		this.searchDiagrams = (key&BLTSearchProvider.SEARCH_DIAGRAM)!=0;
		this.searchBlocks = (key & (BLTSearchProvider.SEARCH_BLOCK +BLTSearchProvider.SEARCH_PROPERTY) )!=0;
		this.log = LogUtil.getLogger(getClass().getPackage().getName());
		this.index = 0;
	}
	@Override
	public Object next() {
		Object so = null;   // Search Object
		// Deserialize here - first time through only - return next block cursor
		if( index==0 ) {
			diagram = deserializeResource(resId);
			ApplicationRequestHandler appRequestHandler = new ApplicationRequestHandler();
			familyName = appRequestHandler.getFamilyName(diagram.getId().toString());
		}
		
		if( index==0 && searchDiagrams ) {
			
			so = new DiagramNameSearchObject(context,familyName,diagram);
			log.infof("%s.next %s",TAG,diagram.getDiagramName());
		}
		else if( searchBlocks ) {
			int jndex = (searchDiagrams?1:0);
			Iterator<? extends Block> blockWalker = diagram.getBlocks().iterator();
			while( blockWalker.hasNext() ) {
				Object temp = new BlockSearchCursor(context,diagram,(ProcessBlockView)(blockWalker.next()),searchKey);
				if( jndex==index ) {
					so = temp;
					break;
				}
				jndex++;
			}
		}
		index++;
		return so;
	}

	private ProcessDiagramView deserializeResource(long resourceId) {
		ProjectResource res = context.getProject().getResource(resourceId);	
		String json = new String(res.getData());
		log.debugf("%s: open - diagram = %s",TAG,json);
		SerializableDiagram sd = null;
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		mapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL,true);
		try {
			sd = mapper.readValue(json,SerializableDiagram.class);
			// Synchronize names as the resource may have been re-named since it was serialized
			sd.setName(res.getName());
		} 
		catch (JsonParseException jpe) {
			log.warnf("%s: open parse exception (%s)",TAG,jpe.getLocalizedMessage());
		} 
		catch (JsonMappingException jme) {
			log.warnf("%s: open mapping exception (%s)",TAG,jme.getLocalizedMessage());
		} 
		catch (IOException ioe) {
			log.warnf("%s: open io exception (%s)",TAG,ioe.getLocalizedMessage());
		}
		ProcessDiagramView dgm = new ProcessDiagramView(res.getResourceId(),sd, context);
		return dgm;
	}
}
