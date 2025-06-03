/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package gui;

import gui.playfield.PlayField;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;

import core.World;

/**
 * Main window for the program. Sets up the main Swing components and layout.
 *
 * @author original Aalto University, ComNet authors
 * @author hendrowunga (modifications to pass DTNSimGUI to SimMenuBar)
 */
public class MainWindow extends JFrame {
    private static final String WINDOW_TITLE = "ONE";
    private static final int WIN_XSIZE = 900;
    private static final int WIN_YSIZE = 700;
    // log panel's initial weight in the split panel
    private static final double SPLIT_PANE_LOG_WEIGHT = 0.2;

    private JScrollPane playFieldScroll; // Scroll pane containing the PlayField

    /**
     * Constructor for the main window.
     *
     * @param scenName   The name of the simulation scenario.
     * @param world      The simulation world object.
     * @param field      The PlayField graphical component.
     * @param guiControls The control panel component.
     * @param infoPanel   The information panel component.
     * @param elp         The event log panel component.
     * @param gui         The main DTNSimGUI object (reference needed for SimMenuBar).
     */
    public MainWindow(String scenName, World world, PlayField field,
                      GUIControls guiControls, InfoPanel infoPanel,
                      EventLogPanel elp, DTNSimGUI gui) { // Accept DTNSimGUI reference
        super(WINDOW_TITLE + " - " + scenName); // Set window title
        JFrame.setDefaultLookAndFeelDecorated(true); // Use OS look and feel
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Handle closing manually in DTNSimGUI

        JPanel leftPane = new JPanel(); // Panel for controls, playfield, info
        leftPane.setLayout(new BoxLayout(leftPane,BoxLayout.Y_AXIS)); // Arrange vertically

        JScrollPane hostListScroll; // Scroll pane for node list chooser
        JSplitPane fieldLogSplit; // Split pane for field/log area
        JSplitPane logControlSplit; // Split pane for log controls/log display
        JSplitPane mainSplit; // Main split pane (field+log vs node list)

        setLayout(new BorderLayout()); // Main window layout

        // --- Pass the 'gui' reference to SimMenuBar constructor ---
        setJMenuBar(new SimMenuBar(field, gui)); // Pass field and gui references
        // --- END NEW ---

        playFieldScroll = new JScrollPane(field); // Wrap playfield in a scroll pane
        // Allow playfield to expand to fill available space
        playFieldScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                Integer.MAX_VALUE));

        // Set up node chooser panel
        hostListScroll = new JScrollPane(new NodeChooser(world.getHosts(),gui));
        hostListScroll.setHorizontalScrollBarPolicy(
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER); // No horizontal scrollbar needed

        // Set up split pane for event log controls and event log display
        logControlSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(elp.getControls()),new JScrollPane(elp));
        logControlSplit.setResizeWeight(0.1); // Log controls panel smaller
        logControlSplit.setOneTouchExpandable(true); // Add expand/collapse buttons

        // Set up vertical split pane for playfield area and log area
        fieldLogSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                leftPane, logControlSplit);
        fieldLogSplit.setResizeWeight(1-SPLIT_PANE_LOG_WEIGHT); // Playfield area takes more space
        fieldLogSplit.setOneTouchExpandable(true);

        setPreferredSize(new Dimension(WIN_XSIZE, WIN_YSIZE)); // Set preferred window size

        // Add components to the left pane (top to bottom)
        leftPane.add(guiControls);
        leftPane.add(playFieldScroll);
        leftPane.add(infoPanel);

        // Set up main horizontal split pane (left pane vs node list)
        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                fieldLogSplit, hostListScroll);
        mainSplit.setOneTouchExpandable(true);
        mainSplit.setResizeWeight(0.60); // Playfield/log area takes more space
        this.getContentPane().add(mainSplit); // Add to window's content pane

        pack(); // Size the window to fit components
    }

    /**
     * Returns a reference of the play field scroll panel.
     *
     * @return a reference of the play field scroll panel.
     */
    public JScrollPane getPlayFieldScroll() {
        return this.playFieldScroll;
    }

}