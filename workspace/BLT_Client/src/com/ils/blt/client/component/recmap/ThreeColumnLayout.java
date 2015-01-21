package com.ils.blt.client.component.recmap;

import java.awt.geom.Rectangle2D;
import java.util.Iterator;

import com.inductiveautomation.ignition.common.util.LogUtil;
import com.inductiveautomation.ignition.common.util.LoggerEx;

import prefuse.action.layout.Layout;
import prefuse.data.Node;
import prefuse.data.tuple.TupleSet;
import prefuse.visual.VisualItem;


/**
 * Based on GridLayout. This layout is specific to a grid containing:
 *    1) Source column
 *    2) Target column
 *    3) Link column - middle column has edges to source and target. 
 *  
 */
public class ThreeColumnLayout extends Layout {
	private static final String TAG = "ThreeColumnLayout";
	private final LoggerEx log = LogUtil.getLogger(getClass().getPackage().getName());

	protected  int nrows;
    protected  int ncols = 3;
    protected  int nrows1;
    protected  int nrows2;
    protected  int nrows3;
    protected  String columnColumn;
    protected  String sourceRefColumn;
    protected  String targetRefColumn;
    

    /**
     * Create a new ThreeColumnLayout using the specified grid dimensions.
     * @param group the data group to layout
     * @param rows1 the number of rows in the source column
     * @param rows2 the number of rows in the link column
     * @param rows3 the number of rows in the target column
     * @param col name of the item column that contains the column index
     * @param sourceCol name of the item column that contains the sourceReference
     * @param targetCol name of the item column that contains the targetReference
     */
    public ThreeColumnLayout(String group, int rows1, int rows2, int rows3, String col, String sourceCol, String targetCol) {
        super(group);
        nrows1 = rows1;
        nrows2 = rows2;
        nrows3 = rows3;
        int maxrows = 2;  // Absolute minimum
        if( rows1>maxrows) maxrows = rows1;
        if( rows2>maxrows) maxrows = rows2;
        if( rows3>maxrows) maxrows = rows3;
        nrows = maxrows;
        columnColumn = col;
        sourceRefColumn = sourceCol;
        targetRefColumn = targetCol;
        setMargin(10,10,10,10);   // top,left,bottom.right
        log.infof("%s.constructor group %s is %dx%d nodes (%s)",TAG,m_group,ncols,nrows,(isEnabled()?"ENABLED":"DISABLED"));
    }
    
    /**
     * @see prefuse.action.Action#run(double)
     */
    public void run(double frac) {
        Rectangle2D b = getLayoutBounds();
        double bx = b.getMinX(), by = b.getMinY();
        double w = b.getWidth(), h = b.getHeight();
        
        TupleSet ts = m_vis.getGroup(m_group);
        log.infof("%s.run group %s has %d nodes",TAG,m_group,ts.getTupleCount());
        
        int sources = (nrows - nrows1)/2;
        int targets = (nrows - nrows3)/2;
        boolean linkSlots[] = new boolean[nrows];
        int row=0;
        while(row<nrows) {
        	linkSlots[row] = false;
        	row++;
        }
        
        @SuppressWarnings("rawtypes")
		Iterator iter = ts.tuples();
        // layout grid contents
        while ( iter.hasNext() ) {
        	Object next = iter.next();
        	if( next instanceof Node ) {
        		double x = 0.0;
            	double y = 0.0;
        		VisualItem item = (VisualItem)next;
        		int coltype = item.getInt(columnColumn);
        		//log.infof("%s.run column type = %d",TAG,coltype);
        		if( coltype==RecMapConstants.SOURCE_KIND) {
                	x = bx + w*((coltype)/2.0);
                	y = by + h*((sources)/(double)(nrows-1));
                	sources++;
                }
                else if( coltype==RecMapConstants.TARGET_KIND) {
                	x = bx + w*((coltype)/2.0);
                	y = by + h*((targets)/(double)(nrows-1));
                	targets++;
                }
                else {
                	// link - try to place midway between source and target
                	x = bx + w*((coltype)/2.0);
                	int src = item.getInt(sourceRefColumn);
                	int tar = item.getInt(targetRefColumn);
                	int preference = (src+tar)/2;
                	int span = 0;
                	row = preference;
                	// Try above and below the preferred, until we get an opening
                	while(span<nrows) {
                		if( preference+span < nrows) {
                			if( linkSlots[preference+span]==false ) {
                				row = preference+span;
                				break;
                			}
                		}
                		else if( preference-span>=0) {
                			if( linkSlots[preference-span]==false ) {
                				row = preference-span;
                				break;
                			}
                		}
                		span++;
                	}
                	linkSlots[row] = true;
                	log.infof("%s.run recommendation = %d:%d->%d",TAG,src,tar,row);
                	y = by + h*((row)/(double)(nrows-1));
                }
                item.setVisible(true);
                
                setX(item,null,x);
                setY(item,null,y);
        	}
        }
        log.infof("%s.run group %s complete ...",TAG,m_group);
    }
    
    
    /**
     * Get the number of grid columns.
     * @return the number of grid columns
     */
    public int getNumCols() {
        return ncols;
    }
   
    /**
     * Get the number of grid rows.
     * @return the number of grid rows
     */
    public int getNumRows() {
        return nrows;
    }
    
    
} // end of class GridLayout