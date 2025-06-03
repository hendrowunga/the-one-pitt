/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package gui;

import gui.playfield.PlayField;
import gui.playfield.NodeGraphic;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import core.Settings;
import core.SettingsError;

/**
 * Menu bar of the simulator GUI. Provides controls for simulation and visualization.
 * Includes specific controls for all NodeGraphic flags.
 *
 * @author original Aalto University, ComNet authors
 * @author hendrowunga (modifications for detailed NodeGraphic visualization control)
 */
public class SimMenuBar extends JMenuBar implements ActionListener {
	public static final String ABOUT_TITLE = "about ONE";
	public static final String ABOUT_TEXT = "Copyright (C) 2007 TKK/Netlab\n\n"+
			"This program is free software: you can redistribute it and/or modify\n"+
			"it under the terms of the GNU General Public License as published by\n"+
			"the Free Software Foundation, either version 3 of the License, or\n"+
			"(at your option) any later version.\n\n"+
			"This program is distributed in the hope that it will be useful,\n"+
			"but WITHOUT ANY WARRANTY; without even the implied warranty of\n"+
			"MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the\n"+
			"GNU General Public License for more details.\n\n" +
			"You should have received a copy of the GNU General Public License\n"+
			"along with this program.  If not, see <http://www.gnu.org/licenses/>.\n\n"+ // Fixed typo
			"Map data copyright: Maanmittauslaitos, 2007";

	private JCheckBoxMenuItem enableBgImage;
	private JCheckBoxMenuItem enableNodeName;
	private JCheckBoxMenuItem enableNodeCoverage;
	private JCheckBoxMenuItem enableNodeConnections;
	private JCheckBoxMenuItem enableNodeBody; // NEW: Menu item untuk titik node
	private JCheckBoxMenuItem enableMapGraphic;
	private JCheckBoxMenuItem autoClearOverlay;
	private JCheckBoxMenuItem enableNodePath; // Menu item untuk path segment saat ini
	private JCheckBoxMenuItem enableTrajectoryHistory; // Menu item untuk riwayat

	private JMenuItem clearOverlay;
	private JMenuItem about;

	private PlayField field;
	private DTNSimGUI gui;

	private static final String UNDERLAY_NS = "GUI.UnderlayImage";

	public SimMenuBar(PlayField field, DTNSimGUI gui) {
		this.field = field;
		this.gui = gui;
		init();
	}

	private void init() {
		JMenu pfMenu = new JMenu("Playfield graphics");
		JMenu help = new JMenu("Help");

		// Add options for underlay image if configured
		Settings settings = new Settings(UNDERLAY_NS);
		if (settings.contains("fileName")) {
			enableBgImage = createCheckItem(pfMenu,"Show underlay image",false);
		}

		// --- Add all NodeGraphic visualization options ---
		// These match the static boolean flags in NodeGraphic
		enableNodeBody = createCheckItem(pfMenu, "Show node body (dot)", true);
		enableNodeName = createCheckItem(pfMenu, "Show node name string",true);
		enableNodeCoverage = createCheckItem(pfMenu, "Show node radio coverage", true);
		enableNodeConnections = createCheckItem(pfMenu, "Show node's connections", true);
		enableNodePath = createCheckItem(pfMenu, "Show current path segment", true);
		enableTrajectoryHistory = createCheckItem(pfMenu, "Show trajectory history", true); // Menu untuk riwayat

		// Add other graphics options
		enableMapGraphic = createCheckItem(pfMenu,"Show map graphic",true);
		autoClearOverlay = createCheckItem(pfMenu, "Autoclear overlay",true);
		clearOverlay = createMenuItem(pfMenu,"Clear overlays now"); // Clear overlay graphics like temporary paths

		// Add help menu
		about = createMenuItem(help,"about");

		// Add menus to the menu bar
		this.add(pfMenu);
		this.add(Box.createHorizontalGlue()); // Pushes help menu to the right
		this.add(help);

		// --- Set initial states for NodeGraphic flags based on menu defaults ---
		// Ensure the static flags in NodeGraphic are set according to the menu's starting state.
		NodeGraphic.setDrawNodeBody(enableNodeBody.isSelected());
		NodeGraphic.setDrawNodeName(enableNodeName.isSelected());
		NodeGraphic.setDrawCoverage(enableNodeCoverage.isSelected());
		NodeGraphic.setDrawConnections(enableNodeConnections.isSelected());
		NodeGraphic.setDrawPath(enableNodePath.isSelected());
		NodeGraphic.setDrawTrajectoryHistory(enableTrajectoryHistory.isSelected());
		// Note: MapGraphic state is handled by PlayField.setMap() and field.setShowMapGraphic()
		field.setAutoClearOverlay(autoClearOverlay.isSelected());
		// Underlay image is toggled on selection, not set by default here
	}

	/** Helper to create a simple menu item */
	private JMenuItem createMenuItem(Container c, String txt) {
		JMenuItem i = new JMenuItem(txt);
		i.addActionListener(this);
		c.add(i);
		return i;
	}

	/** Helper to create a checkbox menu item */
	private JCheckBoxMenuItem createCheckItem(Container c,String txt,
											  boolean selected) {
		JCheckBoxMenuItem i = new JCheckBoxMenuItem(txt);
		i.setSelected(selected);
		i.addActionListener(this);
		c.add(i);
		return i;
	}

	/** Handles menu item actions */
	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();

		// Handle NodeGraphic visualization options
		if (source == this.enableNodeBody) {
			NodeGraphic.setDrawNodeBody(enableNodeBody.isSelected());
		}
		else if (source == this.enableNodeName) {
			NodeGraphic.setDrawNodeName(enableNodeName.isSelected());
		}
		else if (source == this.enableNodeCoverage) {
			NodeGraphic.setDrawCoverage(enableNodeCoverage.isSelected());
		}
		else if (source == this.enableNodeConnections) {
			NodeGraphic.setDrawConnections(enableNodeConnections.isSelected());
		}
		else if (source == this.enableNodePath) {
			NodeGraphic.setDrawPath(enableNodePath.isSelected());
		}
		else if (source == this.enableTrajectoryHistory) {
			NodeGraphic.setDrawTrajectoryHistory(enableTrajectoryHistory.isSelected());
		}
		// Handle other graphics options
		else if (source == enableBgImage) { // Underlay image
			toggleUnderlayImage();
		}
		else if (source == this.enableMapGraphic) { // Map graphic
			field.setShowMapGraphic(enableMapGraphic.isSelected());
		}
		else if (source == this.autoClearOverlay) { // Autoclear overlay
			field.setAutoClearOverlay(autoClearOverlay.isSelected());
		}
		else if (source == this.clearOverlay) { // Clear overlays manually
			field.clearOverlays();
		}
		// Handle Help menu options
		else if (source == this.about) { // About box
			JOptionPane.showMessageDialog(this, ABOUT_TEXT, ABOUT_TITLE,
					JOptionPane.INFORMATION_MESSAGE);
		}

		// Request a repaint on the playfield after changing graphics options
		field.repaint();
	}

	/**
	 * Toggles the showing of underlay image. Image is read from the file only
	 * when it is enabled to save some memory.
	 */
	private void toggleUnderlayImage() {
		if (enableBgImage.isSelected()) {
			String imgFile = null;
			int[] offsets;
			double scale, rotate;
			BufferedImage image;
			try {
				Settings settings = new Settings(UNDERLAY_NS);
				imgFile = settings.getSetting("fileName");
				offsets = settings.getCsvInts("offset", 2);
				scale = settings.getDouble("scale");
				rotate = settings.getDouble("rotate");
				image = ImageIO.read(new File(imgFile));
			} catch (IOException ex) {
				warn("Couldn't set underlay image " + imgFile + ". " +
						ex.getMessage());
				enableBgImage.setSelected(false);
				return;
			}
			catch (SettingsError er) {
				warn("Problem with the underlay image settings: " +
						er.getMessage());
				return;
			}
			field.setUnderlayImage(image, offsets[0], offsets[1],
					scale, rotate);
		}
		else {
			// disable the image
			field.setUnderlayImage(null, 0, 0, 0, 0);
		}
	}

	/** Helper to show a warning message dialog */
	private void warn(String txt) {
		JOptionPane.showMessageDialog(null, txt, "warning",
				JOptionPane.WARNING_MESSAGE);
	}
}