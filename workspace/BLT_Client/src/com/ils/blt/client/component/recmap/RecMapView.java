/**
 * Copyright 2014. ILS Automation. All rights reserved.
 * 
 * Derived from prefuse.org "TreeView" sample code.
 * https://github.com/prefuse/Prefuse
 * 
 * @author <a href="http://jheer.org">jeffrey heer</a>
 */
package com.ils.blt.client.component.recmap;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import javax.swing.BorderFactory;
import javax.swing.border.BevelBorder;

import prefuse.Constants;
import prefuse.Display;
import prefuse.Visualization;
import prefuse.action.Action;
import prefuse.action.ActionList;
import prefuse.action.ItemAction;
import prefuse.action.RepaintAction;
import prefuse.action.animate.ColorAnimator;
import prefuse.action.animate.LocationAnimator;
import prefuse.action.animate.QualityControlAnimator;
import prefuse.action.animate.VisibilityAnimator;
import prefuse.action.assignment.ColorAction;
import prefuse.action.assignment.FontAction;
import prefuse.activity.SlowInSlowOutPacer;
import prefuse.controls.PanControl;
import prefuse.controls.ZoomToFitControl;
import prefuse.data.tuple.TupleSet;
import prefuse.render.AbstractShapeRenderer;
import prefuse.render.DefaultRendererFactory;
import prefuse.render.EdgeRenderer;
import prefuse.render.LabelRenderer;
import prefuse.util.ColorLib;
import prefuse.util.FontLib;
import prefuse.visual.VisualItem;
import prefuse.visual.expression.InGroupPredicate;
import prefuse.visual.tuple.TableNodeItem;

import com.inductiveautomation.ignition.client.model.ClientContext;
import com.inductiveautomation.ignition.common.util.LogUtil;
import com.inductiveautomation.ignition.common.util.LoggerEx;


/**
 * Demonstration of a node-link tree viewer
 *
 * @version 1.0
 * @author <a href="http://jheer.org">jeffrey heer</a>
 */
public class RecMapView extends Display {
	private static final long serialVersionUID = 3253162293683958367L;
	private static final String TAG = "RecMapView";
	private final LoggerEx log = LogUtil.getLogger(getClass().getPackage().getName());
    
    private static final String map = "map";
    private static final String mapNodes = "map.nodes";
    private static final String mapEdges = "map.edges";
    private LabelRenderer m_nodeRenderer;
    private EdgeRenderer m_edgeRenderer;
    private final ThreeColumnLayout columnLayout;
   
    public RecMapView(RecommendationMap recmap) {
        super(new Visualization());
        
        Dimension sz = recmap.getSize();
        RecMapDataModel model = recmap.getModel();
        
        // NOTE: Returns a VisualGraph, node/edge tables are VisualTables
        //                             node items are TableNodeItems
        m_vis.addGraph(map, model.getGraph());
        setSize(sz);
        setBackground(new Color(230,228,227));
        setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createBevelBorder(BevelBorder.RAISED), 
                                BorderFactory.createBevelBorder(BevelBorder.LOWERED)));
        
        // NOTE: No images to render.
        m_nodeRenderer = new ThreeColumnLabelRenderer();
        //m_nodeRenderer.setRenderType(AbstractShapeRenderer.RENDER_TYPE_FILL);
        m_nodeRenderer.setRenderType(AbstractShapeRenderer.RENDER_TYPE_DRAW_AND_FILL);
        m_nodeRenderer.setHorizontalAlignment(Constants.LEFT);
        m_nodeRenderer.setRoundedCorner(2,2);
        m_nodeRenderer.setVerticalPadding(3);
        m_nodeRenderer.setHorizontalPadding(4);

        m_edgeRenderer = new EdgeRenderer(Constants.EDGE_TYPE_LINE);
        m_edgeRenderer.setDefaultLineWidth(2.0);
        
        DefaultRendererFactory rf = new DefaultRendererFactory(m_nodeRenderer);
        rf.add(new InGroupPredicate(mapEdges), m_edgeRenderer);
        m_vis.setRendererFactory(rf);
               
        // colors
        ItemAction nodeColor = new NodeColorAction(mapNodes);
        ItemAction textColor = new ColorAction(mapNodes,
                VisualItem.TEXTCOLOR, ColorLib.rgb(0,0,0));
        ItemAction strokeColor = new ColorAction(mapNodes,
                VisualItem.STROKECOLOR, ColorLib.rgb(0,0,0));
        m_vis.putAction("strokeColor", strokeColor);
        m_vis.putAction("textColor", textColor);
        
        ItemAction edgeColor = new ColorAction(mapEdges,
                VisualItem.STROKECOLOR, ColorLib.rgb(150,150,150));
        
        // quick repaint
        ActionList repaint = new ActionList();
        repaint.add(nodeColor);
        repaint.add(new RepaintAction());
        m_vis.putAction("repaint", repaint);
        
        // full paint
        ActionList fullPaint = new ActionList();
        fullPaint.add(nodeColor);
        m_vis.putAction("fullPaint", fullPaint);
        
        // animate paint change
        ActionList animatePaint = new ActionList(400);
        animatePaint.add(new ColorAnimator(mapNodes));
        animatePaint.add(new RepaintAction());
        m_vis.putAction("animatePaint", animatePaint);

        // create a grid layout action
        columnLayout = new ThreeColumnLayout(map,model.getSourceRowCount(),model.getRecommendationCount(),model.getTargetRowCount(),
        									RecMapConstants.KIND,RecMapConstants.SOURCEROW,RecMapConstants.TARGETROW);
        // Rectangle(x,y,width,height)
        columnLayout.setLayoutBounds(new Rectangle2D.Double(0.,0.,sz.width,sz.height));
        columnLayout.setLayoutAnchor(new Point2D.Double(sz.getWidth()/2.,sz.getHeight()/2.));
        m_vis.putAction("columnLayout", columnLayout);
        
        AutoPanAction autoPan = new AutoPanAction();
        
        // create the filtering and layout
        ActionList filter = new ActionList();
        filter.add(new FontAction(mapNodes, FontLib.getFont("Tahoma", 16)));
        filter.add(columnLayout);
        filter.add(textColor);
        filter.add(nodeColor);
        filter.add(strokeColor);
        filter.add(edgeColor);
        m_vis.putAction("filter", filter);
        
        // animated transition
        ActionList animate = new ActionList(1000);
        animate.setPacingFunction(new SlowInSlowOutPacer());
        animate.add(autoPan);
        animate.add(new QualityControlAnimator());
        animate.add(new VisibilityAnimator(map));
        animate.add(new LocationAnimator(mapNodes));
        animate.add(new ColorAnimator(mapNodes));
        animate.add(new RepaintAction());
        m_vis.putAction("animate", animate);
        m_vis.alwaysRunAfter("filter", "animate");

        
        // ------------------------------------------------
        log.infof("%s.constructor: layout bounds %2.1f x %2.1f (%f,%f)",TAG,columnLayout.getLayoutBounds().getWidth(),
        		                                          columnLayout.getLayoutBounds().getHeight(),
        		                                          columnLayout.getLayoutBounds().getX(),
        		                                          columnLayout.getLayoutBounds().getY()
        		                                          );
        //setSize(getWidth(),getHeight());
        
        // initialize the display
        // WARNING: Use of Focus/ZoomToFit/Zoom Controls freeze the VisionUI
        addControlListener(new RecMapSelector(recmap,1));    // Control-click
        addControlListener(new ZoomToFitControl());
        //addControlListener(new ZoomControl());
        //addControlListener(new WheelZoomControl());
        addControlListener(new PanControl());                // Drag
        
        
        // ------------------------------------------------
        
        // filter graph and perform layout
        orient();
        //DisplayLib.fitViewToBounds(this, columnLayout.getLayoutBounds(), RecMapConstants.ZOOM_DURATION);
        m_vis.run("filter");
        log.infof("%s.constructor: controls complete",TAG);

    }
    
    @Override
    public void setSize(int w,int h) {
    	super.setSize(w,h);
    	if( columnLayout!=null ) columnLayout.setLayoutAnchor(new Point2D.Double(w/2,h/2));
    }
    
    // ------------------------------------------------------------------------
   // Set orientation left-to-right.
    public void orient() {
            m_nodeRenderer.setHorizontalAlignment(Constants.LEFT);
            m_edgeRenderer.setHorizontalAlignment1(Constants.RIGHT);
            m_edgeRenderer.setHorizontalAlignment2(Constants.LEFT);
            m_edgeRenderer.setVerticalAlignment1(Constants.CENTER);
            m_edgeRenderer.setVerticalAlignment2(Constants.CENTER);
    }

    
    // ------------------------------------------------------------------------
   
    // ------------------------------------------------------------------------
   
    
    public class AutoPanAction extends Action {
        private Point2D m_start = new Point2D.Double();
        private Point2D m_end   = new Point2D.Double();
        private Point2D m_cur   = new Point2D.Double();
        private int     m_bias  = 150;
        
        public void run(double frac) {
            TupleSet ts = m_vis.getFocusGroup(Visualization.FOCUS_ITEMS);
            if ( ts.getTupleCount() == 0 )
                return;
            // Left-to-right orientation
            if ( frac == 0.0 ) {
                int xbias=0, ybias=0;
                xbias = m_bias;

                VisualItem vi = (VisualItem)ts.tuples().next();
                m_cur.setLocation(getWidth()/2, getHeight()/2);
                getAbsoluteCoordinate(m_cur, m_start);
                m_end.setLocation(vi.getX()+xbias, vi.getY()+ybias);
            } 
            else {
                m_cur.setLocation(m_start.getX() + frac*(m_end.getX()-m_start.getX()),
                                  m_start.getY() + frac*(m_end.getY()-m_start.getY()));
                panToAbs(m_cur);
            }
        }
    }
    
    public static class NodeColorAction extends ColorAction {
    	private final LoggerEx log = LogUtil.getLogger(getClass().getPackage().getName());
        public NodeColorAction(String group) {
            super(group, VisualItem.FILLCOLOR);
        }
        @Override
        public int getColor(VisualItem item) {
        	int color = ColorLib.rgba(220,220,220,100);  // Gray
            if ( item instanceof TableNodeItem ) {
            	int kind = item.getInt(RecMapConstants.KIND);
            	if( kind==RecMapConstants.SOURCE_KIND )      color= ColorLib.rgb(204,215,235);
            	else if( kind==RecMapConstants.TARGET_KIND ) color= ColorLib.rgb(195,207,235);
            	else                                         color= ColorLib.rgb(172,185,190);
            }
            return color;
        }
        @Override
        public int getDefaultColor() {
        	return  ColorLib.rgb(198,229,229);
        }
    } 
} 
