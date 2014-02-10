package com.ils.blt.designer.workspace.ui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import com.ils.blt.designer.workspace.ProcessBlockView;
import com.inductiveautomation.ignition.client.images.ImageLoader;
import com.inductiveautomation.ignition.designer.gui.IconUtil;


/**
 * Create a circular "button" with a predefined 48x48 graphic. The first input anchor
 * creates an anchor point on the left. The first output anchor point creates an 
 * anchor point on the top.
 */
public class ReadoutUIView extends AbstractUIView implements BlockViewUI {
	private static final long serialVersionUID = 2130868310475735865L;
	private Icon icon = null;
	private static final Dimension IMAGE_SIZE = new Dimension(48,48);
	
	public ReadoutUIView(ProcessBlockView view) {
		super(view);
		setOpaque(false);
		setPreferredSize(new Dimension(58,58));       // 48 plus 5 for stubs
		Image img = ImageLoader.getInstance().loadImage("Block/icons/48/round_48.png",IMAGE_SIZE);
		if( img !=null) icon = new ImageIcon(img);
		initAnchorPoints();
	}
	

	@Override
	protected void paintComponent(Graphics _g) {
		Graphics2D g = (Graphics2D)_g;
		if( icon!=null ) {
			icon.paintIcon(getBlockComponent(), g, INSET, INSET);
		}
		drawAnchors(g);
	}

}
