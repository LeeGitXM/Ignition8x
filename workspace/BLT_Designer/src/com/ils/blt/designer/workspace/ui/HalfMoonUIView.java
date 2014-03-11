package com.ils.blt.designer.workspace.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;

import javax.swing.SwingUtilities;

import com.ils.blt.designer.workspace.ProcessBlockView;


/**
 * Create a circular "button" with a predefined 48x48 graphic. The first input anchor
 * creates an anchor point on the left. The first output anchor point creates an 
 * anchor point on the top.
 */
public class HalfMoonUIView extends AbstractUIView implements BlockViewUI {
	private static final long serialVersionUID = 2180868310475735865L;
	private static final int DEFAULT_HEIGHT = 80;
	private static final int DEFAULT_WIDTH  = 80;
	
	public HalfMoonUIView(ProcessBlockView view) {
		super(view);
		setOpaque(false);
		int preferredHeight = view.getPreferredHeight();
		if( preferredHeight<=0 ) preferredHeight = DEFAULT_HEIGHT;
		int preferredWidth = view.getPreferredWidth();
		if( preferredWidth<=0 ) preferredWidth = DEFAULT_WIDTH;
		setPreferredSize(new Dimension(preferredWidth,preferredHeight)); 
		initAnchorPoints();
	}
	

	// Draw a rectangle with pointed end
	@Override
	protected void paintComponent(Graphics _g) {
		// Calling the super method effects an "erase".
		Graphics2D g = (Graphics2D) _g;

		// Preserve the original transform to roll back to at the end
		AffineTransform originalTx = g.getTransform();

		// Turn on anti-aliasing
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);

		// Calculate the inner area
		Dimension sz = getPreferredSize();
		Rectangle ifb = new Rectangle(2*INSET,INSET,sz.width-4*INSET,sz.height-2*INSET);   // x,y,width,height

		// Now translate so that 0,0 is is at the inner origin
		g.translate(ifb.x, ifb.y);

		// Create a polygon that is within the component boundaries
		int[] xvertices = new int[] {0,2*ifb.width/3,ifb.width,ifb.width,2*ifb.width/3,0,0};
		int[] yvertices = new int[] {0,0,ifb.height/4,3*ifb.height/4,ifb.height,ifb.height,0};
		Polygon fi = new Polygon(xvertices,yvertices,7);
		g.setColor(getBackground());
		g.fillPolygon(fi);

		// Outline the frame
		float outlineWidth = 1.0f;
		Stroke stroke = new BasicStroke(outlineWidth,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND);
		g.setStroke(stroke);
		g.setPaint(Color.BLACK);
		g.draw(fi);

		// Reverse any transforms we made
		g.setTransform(originalTx);
		drawAnchors(g);
		drawEmbeddedText(g);
	}

}