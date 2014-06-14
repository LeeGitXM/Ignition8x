package com.ils.blt.designer.workspace;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;

import com.ils.block.common.BlockProperty;
import com.inductiveautomation.ignition.common.util.LogUtil;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.designer.blockandconnector.AbstractBlockWorkspace;
import com.inductiveautomation.ignition.designer.blockandconnector.BlockDesignableContainer;
import com.inductiveautomation.ignition.designer.blockandconnector.model.Block;
import com.inductiveautomation.ignition.designer.blockandconnector.model.BlockDiagramModel;
import com.inductiveautomation.ignition.designer.blockandconnector.model.ConnectionPainter;
import com.inductiveautomation.ignition.designer.blockandconnector.routing.EdgeRouter;

public class DiagramContainer extends BlockDesignableContainer {
	private static final long serialVersionUID = 7484274138362308991L;
	private LoggerEx log = LogUtil.getLogger(getClass().getPackage().getName());

	public DiagramContainer(AbstractBlockWorkspace workspace,BlockDiagramModel model,EdgeRouter router,ConnectionPainter painter) {
		super(workspace, model, router, painter);
	}
	
	@Override
	protected void paintComponent(Graphics _g) {
		super.paintComponent(_g);
		Graphics2D g = (Graphics2D) _g;
		
		// Paint "displayed" properties.
		for(Block blk:getModel().getBlocks() ) {
			ProcessBlockView pbv = (ProcessBlockView)blk;
			float xpos = pbv.getLocation().x;
			float ypos = pbv.getLocation().y;
			if(pbv.isNameDisplayed() ) {
				paintTextAt(g,pbv.getName(),xpos+pbv.getNameOffsetX(),ypos+pbv.getNameOffsetY(),Color.DARK_GRAY,18);
			}
			
			for(BlockProperty bp:pbv.getProperties()) {
				if(bp.isDisplayed() && bp.getValue()!=null) {
					paintTextAt(g,bp.getValue().toString(),xpos+bp.getDisplayOffsetX(),ypos+bp.getDisplayOffsetY(),Color.DARK_GRAY,18);
				}
			}
		}
		
	}
	
	/**
	 * Utility method to paint a text string.
	 * @param g
	 * @param text
	 * @param xpos center of the text
	 * @param ypos center of the text
	 * @param fill color of the text
	 */
	private void paintTextAt(Graphics2D g, String text, float xpos, float ypos, Color fill,int fontSize) {
		Font font = g.getFont();
		font = font.deriveFont(fontSize);
		FontRenderContext frc = g.getFontRenderContext();
		GlyphVector vector = font.createGlyphVector(frc, text);
		Rectangle2D bounds = vector.getVisualBounds();
		// xpos, ypos are centers. Adjust to upper left.
		ypos+= bounds.getHeight()/2f;
		xpos-= bounds.getWidth()/2f;

		Shape textShape = vector.getOutline(xpos, ypos);
		g.setColor(fill);
		g.fill(textShape);
	}
}
