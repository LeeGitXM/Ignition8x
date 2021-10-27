package com.ils.blt.designer.workspace.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

import javax.swing.SwingUtilities;

import com.ils.blt.designer.workspace.BlockAttributeView;
import com.ils.blt.designer.workspace.ProcessBlockView;


/**
 * Create a rectangular display similar to a "readout", or a G2 attribute-display. The
 * subject BlockAttributeView is listening on a specified property of a different block.
 * The view has a large number of properties related to formatting of the output.
 * 
 * There are no anchor points.
 */
public class AttributeUIView extends AbstractBlockUIView implements BlockViewUI {
	private static final long serialVersionUID = 2160868310475735865L; 
	private static final int DEFAULT_HEIGHT = 40;
	private static final int DEFAULT_WIDTH = 80;
	private BlockAttributeView bav = null;
	
	
	// Once we have the view, get the block that is being viewed
	public AttributeUIView(ProcessBlockView view) {
		super(view,DEFAULT_WIDTH,DEFAULT_HEIGHT);
		if( view instanceof BlockAttributeView ) {
			bav = (BlockAttributeView)view;
		}
		setOpaque(false);
	}
	
	@Override
	protected void paintComponent(Graphics _g) {
		// Calling the super method effects an "erase".
		Graphics2D g = (Graphics2D) _g;
		//log.infof("AttributeUIView.paintComponent %s ...(%d:%s)",getBlock().getName(),valueProperty.hashCode(),fncs.coerceToString(valueProperty.getValue()) );
		// Preserve the original transform to roll back to at the end
		AffineTransform originalTx = g.getTransform();
		Color background = colorForString(bav.getBackgroundColor());
		Color foreground = colorForString(bav.getForegroundColor());

		// Turn on anti-aliasing
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
		g.setPaint(foreground);
		
		// Calculate the inner area
		Rectangle ifb = new Rectangle();   // Interior, frame and border
		ifb = SwingUtilities.calculateInnerArea(this,ifb);
		// Now translate so that 0,0 is is at the inner origin
		if( bav==null ) return;
		ifb.x += 80;
		ifb.y += 40;
	
		g.translate(ifb.x, ifb.y);

		ifb.x += BORDER_WIDTH;
		ifb.y += BORDER_WIDTH;
		ifb.width  -= 2*(BORDER_WIDTH);
		ifb.height -= 2*(BORDER_WIDTH);
		// Create a rectangle that is within the border boundaries
		int[] xvertices = new int[] {ifb.x, ifb.x+ifb.width,ifb.x+ifb.width,ifb.x };
		int[] yvertices = new int[] {ifb.y, ifb.y,ifb.y+ifb.height,ifb.y+ifb.height};
		Polygon fi = new Polygon(xvertices,yvertices,4);
		g.setColor(foreground); 
		g.fillPolygon(fi);
		// Outline the inner square
		//g.setPaint(INSET_COLOR);
		g.draw(fi);

		// Reverse any transforms we made
		g.setTransform(originalTx);
		g.setBackground(background);
		
		// NOTE* No longer assume 100px width.  TimeReadout is wider.  The old setting of 8 for small was unreadable
		// Set the font size based on the string length.
		// Assumes 100px block width
		String value = bav.getValue();
		int fontSize = bav.getFontSize();  // large
		
		block.setEmbeddedFontSize(fontSize);
		block.setEmbeddedLabel(value);
		drawEmbeddedText(g,0,0,foreground);
	}
	
	// Draw the text that is part of the rendered box. Recognize \n or \\n as newlines.
	// Left adjust
	protected void drawEmbeddedText(Graphics2D g,int offsetx,int offsety,Color fill) {
		String text = block.getEmbeddedLabel();
		if( text == null || text.length()==0 ) return;
		Dimension sz = getPreferredSize();
		String[] lines = text.split("\n");
		if( lines.length==1 ) lines = text.split("\\n");
		int lineCount = lines.length;
		int dy = 3*block.getEmbeddedFontSize()/4;
		int y = sz.height/2 - (lineCount-1)*dy/2;
		for( String line: lines) {
			paintTextAt(g,line,offsetx,y+offsety,fill,block.getEmbeddedFontSize());
			y+=dy;
		}
	}
	/**
	 * Utility method to paint a text string - left aligned.
	 * @param g
	 * @param text
	 * @param xpos center of the text
	 * @param ypos center of the text
	 * @param fill color of the text
	 */
	protected void paintTextAt(Graphics2D g, String text, float xpos, float ypos, Color fill,int fontSize) {
		Font font = g.getFont();
		font = font.deriveFont((float)fontSize);  // This is, presumably the correct way
		FontRenderContext frc = g.getFontRenderContext();
		GlyphVector vector = font.createGlyphVector(frc, text);
		Rectangle2D bounds = vector.getVisualBounds();
		// xpos, ypos are centers. Adjust to upper left.
		ypos+= bounds.getHeight()/2f;
		//xpos-= bounds.getWidth()/2f;

		Shape textShape = vector.getOutline(xpos, ypos);
		g.setColor(fill);
		g.fill(textShape);
	}
	
	private Color colorForString(String clr) {
		Color color = new Color(255,255,255,0); // TRANSPARENT
		if(clr.equalsIgnoreCase("BLACK")) 		color = Color.BLACK;
		else if(clr.equalsIgnoreCase("RED")) 	color = Color.RED;
		else if(clr.equalsIgnoreCase("GREEN")) 	color = Color.GREEN;
		else if(clr.equalsIgnoreCase("BLUE"))	color = Color.BLUE;
		else if(clr.equalsIgnoreCase("WHITE"))	color = Color.WHITE;
		else if(clr.equalsIgnoreCase("YELLOW"))	color = Color.YELLOW;
		else if(clr.equalsIgnoreCase("GRAY"))	color = Color.GRAY;
		else if(clr.equalsIgnoreCase("LIGHT_GRAY"))	color = Color.LIGHT_GRAY;
		else if(clr.equalsIgnoreCase("DARK_GRAY"))	color = Color.DARK_GRAY;
		else if(clr.equalsIgnoreCase("ORANGE"))	color = Color.ORANGE;
		else if(clr.equalsIgnoreCase("MAGENTA"))color = Color.MAGENTA;
		else if(clr.equalsIgnoreCase("PINK"))	color = Color.PINK;
		else if(clr.equalsIgnoreCase("CYAN"))	color = Color.CYAN;
		return color;
	}
}
