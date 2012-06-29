package vib.segment;

import ij.measure.Calibration;
import ij.ImagePlus;
import ij.IJ;
import vib.SegmentationViewerCanvas;
import vib.segment.CustomCanvas.CanvasListener;

import java.awt.*;
import java.awt.event.*;
import java.util.Vector;

public class CustomCanvas extends SegmentationViewerCanvas {

	private Vector<CanvasListener> listener = new Vector<CanvasListener>();
	
	public CustomCanvas(ImagePlus imp) {
		super(imp);
	}

	public ImagePlus getImage() {
		return imp;
	}

	public void releaseImage() {
		super.imp = null;
		super.labels = null;
	}	

	public Dimension getMinimumSize() {
		return getSize();
	}

	public void setMagnification(double magnification) {
		super.setMagnification(magnification);
		processCanvasEvent(magnification);
	}

	public void addCanvasListener(CanvasListener l) {
		listener.add(l);
	}

	public void removeCanvasListener(CanvasListener l) {
		listener.remove(l);
	}

	public void processCanvasEvent(double magn) {
		for(int i = 0; i < listener.size(); i++) {
			listener.get(i).magnificationChanged(magn);
		}
	}
	
	public static interface CanvasListener {

		public void magnificationChanged(double magnification);

	}
}

