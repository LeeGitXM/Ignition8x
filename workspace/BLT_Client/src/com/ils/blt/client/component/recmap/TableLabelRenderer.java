/**
 *   (c) 2016 ILS Automation. All rights reserved. 
 */
package com.ils.blt.client.component.recmap;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.RectangularShape;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.inductiveautomation.ignition.common.util.LogUtil;
import com.inductiveautomation.ignition.common.util.LoggerEx;

import prefuse.Constants;
import prefuse.render.LabelRenderer;
import prefuse.util.ColorLib;
import prefuse.util.FontLib;
import prefuse.util.GraphicsLib;
import prefuse.util.StringLib;
import prefuse.visual.VisualItem;
import prefuse.visual.tuple.TableNodeItem;


/**
 * A table-label is a multi-line label which displays text in separate
 * rows. The top-most row, the header, is shaded.
 * 
 * The value in the top row is the name of the block. Subsequent rows 
 * are populated from a list of name-value pairs, the attributes.
 */
public class TableLabelRenderer extends LabelRenderer {
	private static final String CLSS = "TableLabelRenderer";
	private final LoggerEx log;
	private final Map<Integer,TextDelegate> delegates;
	private final RecMapDataModel model;
	private String m_header = "";
	/** Transform used to scale and position header (not visible from base class) */
    private AffineTransform m_headertransform = new AffineTransform();
	
    /**
     */
    public TableLabelRenderer(RecMapDataModel mdl) {
    	this.log = LogUtil.getLogger(getClass().getPackage().getName());
    	this.delegates = new HashMap<>();
    	this.model = mdl;
    	m_imageMargin = 2;
    	m_horizBorder = 2;
    	m_vertBorder = 2;
    	setRenderType(RENDER_TYPE_DRAW_AND_FILL);
    	setManageBounds(true);   // False doesn't work
    }
    
    public void setDelegate(int kind,TextDelegate delegate) {
		delegates.put(new Integer(kind),delegate);
	}
	
    /**
     * @see prefuse.render.Renderer#render(java.awt.Graphics2D, prefuse.visual.VisualItem)
     */
    @Override
    public void render(Graphics2D g, VisualItem item) {
    	log.infof("%s.render ....",CLSS);
    	TextDelegate delegate = delegateFromItem(item);
        if( delegate!=null ) {
        	Properties properties = propertiesFromItem(item);
        	RectangularShape shape = getShape(item,delegate,properties);
		    if (shape != null) {
		         // fill the shape, if requested
		         int type = getRenderType(item);
		         if ( type==RENDER_TYPE_FILL || type==RENDER_TYPE_DRAW_AND_FILL )
		             GraphicsLib.paint(g, item, shape, getStroke(item), RENDER_TYPE_FILL);

		         // now render the image and text
		         String text = m_text;
		         Image  img  = getImage(item);
		         
		         if ( text == null && img == null )
		             return;
		                         
		         double size = item.getSize();
		         boolean useInt = 1.5 > Math.max(g.getTransform().getScaleX(),
		                                         g.getTransform().getScaleY());
		         double x = shape.getMinX() + size*m_horizBorder;
		         double y = shape.getMinY() + size*m_vertBorder;
		         
		         // render image
		         if ( img != null ) {            
		             double w = size * img.getWidth(null);
		             double h = size * img.getHeight(null);
		             double ix=x, iy=y;
		             
		             // determine one co-ordinate based on the image position
		             switch ( m_imagePos ) {
		             case Constants.LEFT:
		                 x += w + size*m_imageMargin;
		                 break;
		             case Constants.RIGHT:
		                 ix = shape.getMaxX() - size*m_horizBorder - w;
		                 break;
		             case Constants.TOP:
		                 y += h + size*m_imageMargin;
		                 break;
		             case Constants.BOTTOM:
		                 iy = shape.getMaxY() - size*m_vertBorder - h;
		                 break;
		             default:
		                 throw new IllegalStateException(
		                         "Unrecognized image alignment setting.");
		             }
		             
		             // determine the other coordinate based on image alignment
		             switch ( m_imagePos ) {
		             case Constants.LEFT:
		             case Constants.RIGHT:
		                 // need to set image y-coordinate
		                 switch ( m_vImageAlign ) {
		                 case Constants.TOP:
		                     break;
		                 case Constants.BOTTOM:
		                     iy = shape.getMaxY() - size*m_vertBorder - h;
		                     break;
		                 case Constants.CENTER:
		                     iy = shape.getCenterY() - h/2;
		                     break;
		                 }
		                 break;
		             case Constants.TOP:
		             case Constants.BOTTOM:
		                 // need to set image x-coordinate
		                 switch ( m_hImageAlign ) {
		                 case Constants.LEFT:
		                     break;
		                 case Constants.RIGHT:
		                     ix = shape.getMaxX() - size*m_horizBorder - w;
		                     break;
		                 case Constants.CENTER:
		                     ix = shape.getCenterX() - w/2;
		                     break;
		                 }
		                 break;
		             }
		             
		             if ( useInt && size == 1.0 ) {
		                 // if possible, use integer precision
		                 // results in faster, flicker-free image rendering
		                 g.drawImage(img, (int)ix, (int)iy, null);
		             } 
		             else {
		                 m_headertransform.setTransform(size,0,0,size,ix,iy);
		                 g.drawImage(img, m_headertransform, null);
		             }
		         }
		         
		         // render text
		         int textColor = item.getTextColor();
		         if ( text != null && ColorLib.alpha(textColor) > 0 ) {
		             g.setPaint(ColorLib.getColor(textColor));
		             g.setFont(m_font);
		             FontMetrics fm = DEFAULT_GRAPHICS.getFontMetrics(m_font);

		             // compute available width
		             double tw;
		             switch ( m_imagePos ) {
		             case Constants.TOP:
		             case Constants.BOTTOM:
		                 tw = shape.getWidth() - 2*size*m_horizBorder;
		                 break;
		             default:
		                 tw = m_textDim.width;
		             }
		             
		             // compute available height
		             double th;
		             switch ( m_imagePos ) {
		             case Constants.LEFT:
		             case Constants.RIGHT:
		                 th = shape.getHeight() - 2*size*m_vertBorder;
		                 break;
		             default:
		                 th = m_textDim.height;
		             }
		             
		             // compute starting y-coordinate
		             y += fm.getAscent();
		             switch ( m_vTextAlign ) {
		             case Constants.TOP:
		                 break;
		             case Constants.BOTTOM:
		                 y += th - m_textDim.height;
		                 break;
		             case Constants.CENTER:
		                 y += (th - m_textDim.height)/2;
		             }
		             
		             // render each line of text
		             int lh = fm.getHeight(); // the line height
		             int start = 0, end = text.indexOf(m_delim);
		             for ( ; end >= 0; y += lh ) {
		                 drawString(g, fm, text.substring(start, end), useInt, x, y, tw);
		                 start = end+1;
		                 end = text.indexOf(m_delim, start);   
		             }
		             drawString(g, fm, text.substring(start), useInt, x, y, tw);
		         }
		     
		         // draw border
		         if (type==RENDER_TYPE_DRAW || type==RENDER_TYPE_DRAW_AND_FILL) {
		             GraphicsLib.paint(g,item,shape,getStroke(item),RENDER_TYPE_DRAW);
		         }
			}
		}
    }
    
    /**
     * Handle the case where this is called internally.
     */
    @Override
    public Shape getShape(VisualItem item) {
    	log.infof("%s.getShape ....",CLSS);
    	Shape shape = null;
    	TextDelegate delegate = delegateFromItem(item);
        if( delegate!=null ) {
        	Properties properties = propertiesFromItem(item);
        	shape = getShape(item,delegate,properties);
		}
		return shape;
    }
    
    /**
     * Returns the shape describing the boundary of an item. The shape's
     * coordinates should be in absolute (item-space) coordinates.
     * Similar to a call with the same name inAbstractShapeRenderer, except
     * that we pass along the delegate and properties.
     * @param item the item for which to get the Shape
     */
    private RectangularShape getShape(VisualItem item,TextDelegate delegate,Properties properties) {
        AffineTransform at = getTransform(item);
        RectangularShape rawShape = getRawShape(item,delegate,properties);
        return (at==null || rawShape==null ? rawShape : (RectangularShape)at.createTransformedShape(rawShape));
    }

    /**
     * Similar to a call with the same name in LabelRenderer, except that we 
     * have a header string instead of an image. The code is shamelessly 
     * plagiarized from LabelRenderer.
     * @see prefuse.render.LabelRenderer#getRawShape(prefuse.visual.VisualItem)
     */
    private RectangularShape getRawShape(VisualItem item,TextDelegate delegate,Properties properties) {
        m_header = delegate.getHeaderText(item, properties);
        m_text = delegate.getBodyText(item, properties);
        double size = item.getSize();
        
        // get header dimensions
        double iw=0, ih=0;
        if ( m_header != null ) {
        	m_header = computeTextDimensions(item, m_header, size);
            ih = m_textDim.height;
            iw = m_textDim.width;     
        }
        
        // get text dimensions
        int tw=0, th=0;
        if ( m_text != null ) {
            m_text = computeTextDimensions(item, m_text, size);
            th = m_textDim.height;
            tw = m_textDim.width;   
        }
        
        // get bounding box dimensions. The header is always on top.
        double w=0, h=0;
        w = Math.max(tw, size*iw) + size*2*m_horizBorder;
        h = th + size*(ih + 2*m_vertBorder + (th>0 && ih>0 ? m_imageMargin : 0));
      
        // get the top-left point, using the current alignment settings
        getAlignedPoint(m_pt, item, w, h, m_xAlign, m_yAlign);
        
        if ( m_bbox instanceof RoundRectangle2D ) {
            RoundRectangle2D rr = (RoundRectangle2D)m_bbox;
            rr.setRoundRect(m_pt.getX(), m_pt.getY(), w, h,
                            size*m_arcWidth, size*m_arcHeight);
        } 
        else {
            m_bbox.setFrame(m_pt.getX(), m_pt.getY(), w, h);
        }
        return (RectangularShape)m_bbox;
    }
    
    /**
     * Draws the specified shape into the provided Graphics context, using
     * stroke and fill color values from the specified VisualItem. This method
     * can be called by subclasses in custom rendering routines. 
     */
    @Override
    protected void drawShape(Graphics2D g, VisualItem item, Shape shape) {
        GraphicsLib.paint(g, item, shape, getStroke(item), getRenderType(item));
    }
    
    

    /**
     * We should not be using this method. Throw exception
     * @param item the item to represent as a <code>String</code>
     * @return a <code>String</code> to draw
     */
    protected String getText(VisualItem item) {
        throw new IllegalArgumentException("getText not applicable to this class");
    }
    /**
     * @see prefuse.render.Renderer#setBounds(prefuse.visual.VisualItem)
     */
    @Override
    public void setBounds(VisualItem item) {
        if ( !m_manageBounds ) return;   // Things don't go well
        TextDelegate delegate = delegateFromItem(item);
        if( delegate!=null ) {
        	Properties properties = propertiesFromItem(item);
            Shape shape = getShape(item,delegate,properties);
            if ( shape == null ) {
                item.setBounds(item.getX(), item.getY(), 0, 0);
            } 
            else {
                GraphicsLib.setBounds(item, shape, getStroke(item));
            }
        }
    }
    // Stolen from LabelRenderer where it is a private method.
    // This potential shortens the text. As a side effect, it sets 
    // class members that hold dimensions and font sizes. 
    private String computeTextDimensions(VisualItem item, String text,double size) {
        // put item font in temp member variable
        m_font = item.getFont();
        // scale the font as needed
        if ( size != 1 ) {
            m_font = FontLib.getFont(m_font.getName(), m_font.getStyle(),
                                     size*m_font.getSize());
        }
        
        FontMetrics fm = DEFAULT_GRAPHICS.getFontMetrics(m_font);
        StringBuffer str = null;
        
        // compute the number of lines and the maximum width
        int nlines = 1, w = 0, start = 0, end = text.indexOf(m_delim);
        m_textDim.width = 0;
        String line;
        for ( ; end >= 0; ++nlines ) {
            w = fm.stringWidth(line=text.substring(start,end));
            // abbreviate line as needed
            if ( m_maxTextWidth > -1 && w > m_maxTextWidth ) {
                if ( str == null )
                    str = new StringBuffer(text.substring(0,start));
                str.append(StringLib.abbreviate(line, fm, m_maxTextWidth));
                str.append(m_delim);
                w = m_maxTextWidth;
            } else if ( str != null ) {
                str.append(line).append(m_delim);
            }
            // update maximum width and substring indices
            m_textDim.width = Math.max(m_textDim.width, w);
            start = end+1;
            end = text.indexOf(m_delim, start);
        }
        w = fm.stringWidth(line=text.substring(start));
        // abbreviate line as needed
        if ( m_maxTextWidth > -1 && w > m_maxTextWidth ) {
            if ( str == null )
                str = new StringBuffer(text.substring(0,start));
            str.append(StringLib.abbreviate(line, fm, m_maxTextWidth));
            w = m_maxTextWidth;
        } else if ( str != null ) {
            str.append(line);
        }
        // update maximum width
        m_textDim.width = Math.max(m_textDim.width, w);
        
        // compute the text height
        m_textDim.height = fm.getHeight() * nlines;
        
        return str==null ? text : str.toString();
    }
    
    private TextDelegate delegateFromItem(VisualItem item) {
    	TextDelegate delegate = null;
    	if( item instanceof TableNodeItem ) {
    		int kind = item.getInt(RecMapConstants.KIND);
    		delegate = delegates.get(new Integer(kind));
    	}
    	return delegate;
    }
    
    // Stolen directly from LabelRenderer (it was private final)
    private void drawString(Graphics2D g, FontMetrics fm, String text,
            boolean useInt, double x, double y, double w)  {
        // compute the x-coordinate
        double tx;
        switch ( m_hTextAlign ) {
        case Constants.LEFT:
            tx = x;
            break;
        case Constants.RIGHT:
            tx = x + w - fm.stringWidth(text);
            break;
        case Constants.CENTER:
            tx = x + (w - fm.stringWidth(text)) / 2;
            break;
        default:
            throw new IllegalStateException(
                    "Unrecognized text alignment setting.");
        }
        // use integer precision unless zoomed-in
        // results in more stable drawing
        if ( useInt ) {
            g.drawString(text, (int)tx, (int)y);
        } else {
            g.drawString(text, (float)tx, (float)y);
        }
    }
    private Properties propertiesFromItem(VisualItem item) {
    	Properties properties = null;
    	if( item instanceof TableNodeItem ) {
    		int row = item.getInt(RecMapConstants.ROW);
    		properties = model.getAttributes(row);
    	}
    	return properties;
    }
    			
} 
