package com.ils.blt.gateway.wicket;

import java.util.List;

import org.apache.wicket.model.LoadableDetachableModel;

import com.ils.blt.gateway.BasicDiagram;
import com.ils.blt.gateway.engine.BlockExecutionController;
import com.ils.blt.gateway.engine.ModelManager;


public class SimpleDiagramListModel extends LoadableDetachableModel<List<BasicDiagram>> {
	private static final long serialVersionUID = 3174385295574411202L;

	public SimpleDiagramListModel() {	
	}


	@Override
	protected List<BasicDiagram> load() {
		ModelManager modelManager = BlockExecutionController.getInstance().getDelegate();
		return modelManager.getDiagrams(); 
	}
}
