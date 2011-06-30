package fiji.plugin.trackmate.visualization.hyperstack;

import ij.ImagePlus;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jfree.chart.renderer.InterpolatePaintScale;
import org.jgrapht.graph.DefaultWeightedEdge;

import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotFeature;
import fiji.plugin.trackmate.TrackMateModel;
import fiji.plugin.trackmate.visualization.TrackMateModelView;
import fiji.util.gui.OverlayedImageCanvas.Overlay;

public class TrackOverlay implements Overlay {
	private static final boolean DEBUG = true;
	private float[] calibration;
	private ImagePlus imp;
	private List<Color> edgeColors;
	private Collection<DefaultWeightedEdge> highlight = new HashSet<DefaultWeightedEdge>();
	private Map<String, Object> displaySettings;
	private TrackMateModel model;

	/*
	 * CONSTRUCTOR
	 */

	public TrackOverlay(final TrackMateModel model, final ImagePlus imp, final Map<String, Object> displaySettings) {
		this.model = model;
		this.calibration = model.getSettings().getCalibration();
		this.imp = imp;
		this.displaySettings = displaySettings;
		computeTrackColors();
	}

	/*
	 * PUBLIC METHODS
	 */

	/**
	 * Provide default coloring.
	 */
	public void computeTrackColors() {
		int ntracks = model.getNTracks();
		if (ntracks == 0)
			return;
		InterpolatePaintScale colorMap = (InterpolatePaintScale) displaySettings.get(TrackMateModelView.KEY_COLORMAP);
		edgeColors = new ArrayList<Color>(ntracks);
		for(int i = 0; i < ntracks; i++)
			edgeColors.add(colorMap.getPaint((float) i / (ntracks-1)));
	}

	public void setHighlight(Collection<DefaultWeightedEdge> edges) {
		this.highlight = edges;
	}

	@Override
	public final void paint(final Graphics g, final int xcorner, final int ycorner, final double magnification) {
		boolean tracksVisible = (Boolean) displaySettings.get(TrackMateModelView.KEY_TRACKS_VISIBLE);
		if (!tracksVisible  || model.getNTracks() == 0)
			return;

		final Graphics2D g2d = (Graphics2D)g;
		// Save graphic device original settings
		final AffineTransform originalTransform = g2d.getTransform();
		final Composite originalComposite = g2d.getComposite();
		final Stroke originalStroke = g2d.getStroke();
		final Color originalColor = g2d.getColor();	
		final float dt = model.getSettings().dt;
		final float mag = (float) magnification;
		Spot source, target;

		// Deal with highlighted edges first: brute and thick display
		g2d.setStroke(new BasicStroke(4.0f,  BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g2d.setColor(TrackMateModelView.DEFAULT_HIGHLIGHT_COLOR);
		for (DefaultWeightedEdge edge : highlight) {
			if (DEBUG)
				System.out.println("[TrackOverlay] paint: highlighting edge "+edge);
			source = model.getEdgeSource(edge);
			target = model.getEdgeTarget(edge);
			drawEdge(g2d, source, target, xcorner, ycorner, mag);
		}

		// The rest
		final int currentFrame = imp.getFrame() - 1;
		final int trackDisplayMode = (Integer) displaySettings.get(TrackMateModelView.KEY_TRACK_DISPLAY_MODE);
		final int trackDisplayDepth = (Integer) displaySettings.get(TrackMateModelView.KEY_TRACK_DISPLAY_DEPTH);
		final List<Set<DefaultWeightedEdge>> allTrackEdges = model.getTrackEdges(); 


		g2d.setStroke(new BasicStroke(2.0f,  BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		if (trackDisplayMode == TrackMateModelView.TRACK_DISPLAY_MODE_LOCAL || trackDisplayMode == TrackMateModelView.TRACK_DISPLAY_MODE_LOCAL_QUICK) 
			g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));

		// Determine bounds for limited view modes
		int minT = 0;
		int maxT = 0;
		switch (trackDisplayMode) {
		case TrackMateModelView.TRACK_DISPLAY_MODE_LOCAL:
		case TrackMateModelView.TRACK_DISPLAY_MODE_LOCAL_QUICK:
			minT = currentFrame - trackDisplayDepth;
			maxT = currentFrame + trackDisplayDepth;
			break;
		case TrackMateModelView.TRACK_DISPLAY_MODE_LOCAL_FORWARD:
		case TrackMateModelView.TRACK_DISPLAY_MODE_LOCAL_FORWARD_QUICK:
			minT = currentFrame;
			maxT = currentFrame + trackDisplayDepth;
			break;
		case TrackMateModelView.TRACK_DISPLAY_MODE_LOCAL_BACKWARD:
		case TrackMateModelView.TRACK_DISPLAY_MODE_LOCAL_BACKWARD_QUICK:
			minT = currentFrame - trackDisplayDepth;
			maxT = currentFrame;
			break;
		}


		float sourceFrame, transparency;
		switch (trackDisplayMode) {

		case TrackMateModelView.TRACK_DISPLAY_MODE_WHOLE: {
			for (int i = 0; i < model.getNTracks(); i++) {
				g2d.setColor(edgeColors.get(i));
				final Set<DefaultWeightedEdge> trackEdges = allTrackEdges.get(i);

				for (DefaultWeightedEdge edge : trackEdges) {
					if (highlight.contains(edge))
						continue;

					source = model.getEdgeSource(edge);
					target = model.getEdgeTarget(edge);
					drawEdge(g2d, source, target, xcorner, ycorner, mag);
				}
			}
			break;
		}

		case TrackMateModelView.TRACK_DISPLAY_MODE_LOCAL_QUICK: 
		case TrackMateModelView.TRACK_DISPLAY_MODE_LOCAL_FORWARD_QUICK: 
		case TrackMateModelView.TRACK_DISPLAY_MODE_LOCAL_BACKWARD_QUICK: {

			for (int i = 0; i < model.getNTracks(); i++) {
				g2d.setColor(edgeColors.get(i));
				final Set<DefaultWeightedEdge> trackEdges = allTrackEdges.get(i);

				for (DefaultWeightedEdge edge : trackEdges) {
					if (highlight.contains(edge))
						continue;

					source = model.getEdgeSource(edge);
					sourceFrame = source.getFeature(SpotFeature.POSITION_T) / dt;
					if (sourceFrame < minT || sourceFrame >= maxT)
						continue;

					target = model.getEdgeTarget(edge);
					drawEdge(g2d, source, target, xcorner, ycorner, mag);
				}
			}
			break;
		}

		case TrackMateModelView.TRACK_DISPLAY_MODE_LOCAL:
		case TrackMateModelView.TRACK_DISPLAY_MODE_LOCAL_FORWARD:
		case TrackMateModelView.TRACK_DISPLAY_MODE_LOCAL_BACKWARD: {

			for (int i = 0; i < model.getNTracks(); i++) {
				g2d.setColor(edgeColors.get(i));
				final Set<DefaultWeightedEdge> trackEdges = allTrackEdges.get(i);

				for (DefaultWeightedEdge edge : trackEdges) {
					if (highlight.contains(edge))
						continue;

					source = model.getEdgeSource(edge);
					sourceFrame = source.getFeature(SpotFeature.POSITION_T) / dt;
					if (sourceFrame < minT || sourceFrame >= maxT)
						continue;

					transparency = 1 - Math.abs(sourceFrame-currentFrame) / trackDisplayDepth;
					target = model.getEdgeTarget(edge);
					drawEdge(g2d, source, target, xcorner, ycorner, mag, transparency);
				}
			}
			break;

		}


		}

		// Restore graphic device original settings
		g2d.setTransform( originalTransform );
		g2d.setComposite(originalComposite);
		g2d.setStroke(originalStroke);
		g2d.setColor(originalColor);


	}

	/* 
	 * PRIVATE METHODS
	 */

	private final void drawEdge(final Graphics2D g2d, final Spot source, final Spot target,
			final int xcorner, final int ycorner, final float magnification, final float transparency) {
		// Find x & y in physical coordinates
		final float x0i = source.getFeature(SpotFeature.POSITION_X);
		final float y0i = source.getFeature(SpotFeature.POSITION_Y);
		final float x1i = target.getFeature(SpotFeature.POSITION_X);
		final float y1i = target.getFeature(SpotFeature.POSITION_Y);
		// In pixel units
		final float x0p = x0i / calibration[0];
		final float y0p = y0i / calibration[1];
		final float x1p = x1i / calibration[0];
		final float y1p = y1i / calibration[1];
		// Scale to image zoom
		final float x0s = (x0p - xcorner) * magnification ;
		final float y0s = (y0p - ycorner) * magnification ;
		final float x1s = (x1p - xcorner) * magnification ;
		final float y1s = (y1p - ycorner) * magnification ;
		// Round
		final int x0 = Math.round(x0s);
		final int y0 = Math.round(y0s);
		final int x1 = Math.round(x1s);
		final int y1 = Math.round(y1s);

		g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, transparency));
		g2d.drawLine(x0, y0, x1, y1);

	}

	private final void drawEdge(final Graphics2D g2d, final Spot source, final Spot target,
			final int xcorner, final int ycorner, final float magnification) {
		// Find x & y in physical coordinates
		final float x0i = source.getFeature(SpotFeature.POSITION_X);
		final float y0i = source.getFeature(SpotFeature.POSITION_Y);
		final float x1i = target.getFeature(SpotFeature.POSITION_X);
		final float y1i = target.getFeature(SpotFeature.POSITION_Y);
		// In pixel units
		final float x0p = x0i / calibration[0];
		final float y0p = y0i / calibration[1];
		final float x1p = x1i / calibration[0];
		final float y1p = y1i / calibration[1];
		// Scale to image zoom
		final float x0s = (x0p - xcorner) * magnification ;
		final float y0s = (y0p - ycorner) * magnification ;
		final float x1s = (x1p - xcorner) * magnification ;
		final float y1s = (y1p - ycorner) * magnification ;
		// Round
		final int x0 = Math.round(x0s);
		final int y0 = Math.round(y0s);
		final int x1 = Math.round(x1s);
		final int y1 = Math.round(y1s);

		g2d.drawLine(x0, y0, x1, y1);

	}

	/**
	 * Ignored.
	 */
	@Override
	public void setComposite(Composite composite) {	}

}