/*
 * PluginUpdateManager.java
 *
 * Copyright (c) 2011, The University of Sheffield. See the file COPYRIGHT.txt
 * in the software or at http://gate.ac.uk/gate/COPYRIGHT.txt
 *
 * This file is part of GATE (see http://gate.ac.uk/), and is free software,
 * licenced under the GNU Library General Public License, Version 2, June 1991
 * (in the distribution as file licence.html, and also available at
 * http://gate.ac.uk/gate/licence.html).
 *
 * Mark A. Greenwood, 29/10/2011
 */

package gate.gui.creole.manager;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gate.Main;
import gate.creole.Plugin;
import gate.gui.MainFrame;

/**
 * The CREOLE plugin manager
 *
 * @author Mark A. Greenwood
 */
@SuppressWarnings("serial")
public class PluginUpdateManager extends JDialog {

  private static final Logger log = LoggerFactory.getLogger(PluginUpdateManager.class);

  private AvailablePlugins installed = new AvailablePlugins();

  private ProgressPanel progressPanel = new ProgressPanel();

  private JPanel panel = new JPanel(new BorderLayout());

  //private JTabbedPane tabs = new JTabbedPane();

  /**
   * Location of the default plugins list online - this is a
   * {@link String#format} pattern with one %s placeholder, which
   * will be replaced with the current GATE version number.
   */
  public static final String DEFAULT_PLUGINS_URL_PATTERN =
          "https://raw.githubusercontent.com/GateNLP/gate-metadata/" +
          "master/default-plugins/plugins-%s.tsv";

  /**
   * Load all the data about available plugins/updates from the remote update
   * sites as well as checking what is installed in the user plugin directory
   */
  private void loadData() {
    // display the progress panel to stop user input and show progress
    progressPanel.messageChanged("Loading CREOLE Plugin Information...");

    // the assumption is that this code is run from the EDT so we need to run
    // the time consuming stuff in a different thread to stop things locking up
    new Thread() {
      @Override
      public void run() {
        // reset the info ready for a reload
                SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            // update all the tables
            installed.reInit();
        
            // remove the progress panel
            showProgressPanel(false);
          }
        });
      }
    }.start();
  }

  private void showProgressPanel(final boolean visible) {
    if(visible == getRootPane().getGlassPane().isVisible()) return;
    
    progressPanel.reset();
    
    if(visible) {
      remove(panel);
      add(progressPanel, BorderLayout.CENTER);
    } else {
      remove(progressPanel);
      add(panel, BorderLayout.CENTER);
    }
    getRootPane().getGlassPane().setVisible(visible);
    validate();
  }

  private void applyChanges() {
    progressPanel.messageChanged("Updating CREOLE Plugin Configuration...");
    showProgressPanel(true);

    // the assumption is that this code is run from the EDT so we need to run
    // the time consuming stuff in a different thread to stop things locking up
    new Thread() {
      @Override
      public void run() {

        // (un)load already installed plugins
        progressPanel.messageChanged("Updating CREOLE Plugin Configuration...");
        
        Set<Plugin> failedPlugins = installed.updateAvailablePlugins(progressPanel);
        if (!failedPlugins.isEmpty()) {
          JOptionPane
          .showMessageDialog(
              PluginUpdateManager.this,
              "<html><body style='width: 350px;'><b>Loading of "
                  + failedPlugins.size()
                  + " plugins failed!</b><br><br>"
                  + "See the message pane for more details.</body></html>",
              PluginUpdateManager.this.getTitle(),
              JOptionPane.ERROR_MESSAGE);
        }
        
        // refresh the tables to reflect what we have just done
        loadData();
      }
    }.start();
  }

  @Override
  public void dispose() {
    MainFrame.getGuiRoots().remove(this);
    super.dispose();
  }

  public PluginUpdateManager(JFrame owner) {
    super(owner, true);

    // set up the main window
    setTitle("CREOLE Plugin Manager");
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setLayout(new BorderLayout());
    //setIconImages(Arrays.asList(new Image[]{new GATEIcon(64, 64).getImage(),
    //    new GATEIcon(48, 48).getImage(), new GATEIcon(32, 32).getImage(),
    //    new GATEIcon(22, 22).getImage(), new GATEIcon(16, 16).getImage()}));

    // set up the panel that displays the main GUI elements
    panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    //panel.add(tabs, BorderLayout.CENTER);

    // initialize all the different tabs
    //tabs.addTab("Installed Plugins", new AvailableIcon(MainFrame.ICON_DIMENSION), installed);
    panel.add(installed, BorderLayout.CENTER);

    // setup the row of buttons at the bottom of the screen...
    JPanel pnlButtons = new JPanel();
    pnlButtons.setLayout(new BoxLayout(pnlButtons, BoxLayout.X_AXIS));
    pnlButtons.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));

    // ... the apply button
    JButton btnApply = new JButton("Apply All");
    getRootPane().setDefaultButton(btnApply);
    btnApply.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        PluginUpdateManager.this.applyChanges();
      }
    });

    // ... the close button
    Action cancelAction = new AbstractAction("Close") {
      @Override
      public void actionPerformed(ActionEvent e) {

        boolean changes = false;

        if(!changes) changes = installed.unsavedChanges();

        if(changes
            && JOptionPane
                .showConfirmDialog(
                    PluginUpdateManager.this,
                    "<html><body style='width: 350px;'><b>Changes Have Not Yet Been Applied!</b><br><br>"
                        + "Would you like to apply your changes now?</body></html>",
                    "CREOLE Plugin Manager", JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION) {
          applyChanges();
        }

        PluginUpdateManager.this.setVisible(false);
      }
    };
    JButton btnCancel = new JButton(cancelAction);

    // ... and the help button
    Action helpAction = new AbstractAction("Help") {
      @Override
      public void actionPerformed(ActionEvent e) {
        MainFrame.getInstance().showHelpFrame("sec:howto:plugins",
            "gate.gui.creole.PluginUpdateManager");
      }
    };
    JButton btnHelp = new JButton(helpAction);

    // add the buttons to the panel
    pnlButtons.add(btnHelp);
    pnlButtons.add(Box.createHorizontalGlue());
    pnlButtons.add(btnApply);
    pnlButtons.add(Box.createHorizontalStrut(5));
    pnlButtons.add(btnCancel);

    // and the panel to the main GUI
    panel.add(pnlButtons, BorderLayout.SOUTH);

    // make the main GUI the currently visisble dialog item
    add(panel, BorderLayout.CENTER);

    // define keystrokes action bindings at the level of the main window
    getRootPane().registerKeyboardAction(cancelAction,
        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
        JComponent.WHEN_IN_FOCUSED_WINDOW);
    getRootPane().registerKeyboardAction(helpAction,
        KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0),
        JComponent.WHEN_IN_FOCUSED_WINDOW);

    // make sure the dialog is a reasonable size
    pack();
    Dimension screenSize = getGraphicsConfiguration().getBounds().getSize();
    Dimension dialogSize = getPreferredSize();
    int width =
        dialogSize.width > screenSize.width
            ? screenSize.width * 3 / 4
            : dialogSize.width;
    int height =
        dialogSize.height > screenSize.height
            ? screenSize.height * 2 / 3
            : dialogSize.height;
    setSize(width, height);
    validate();

    // place the dialog somewhere sensible
    setLocationRelativeTo(owner);
  }
  
  @Override
  public void setVisible(boolean visible) {
    if(visible) {
      MainFrame.getGuiRoots().add(this);
      // if the window is about to be made visible then do some quick setup
      //tabs.setSelectedIndex(0);
      if(defaultPluginsWorker != null && !defaultPluginsWorker.isDone()) {
        showProgressPanel(true);
        progressPanel.messageChanged("Downloading default plugins list");
        defaultPluginsWorker.addPropertyChangeListener((evt) -> {
          if("progress".equals(evt.getPropertyName())) {
            // progress property changes are notified on the EDT
            progressPanel.downloadProgressed("Default plugins", 100, (Integer)evt.getNewValue());
          } else if("state".equals(evt.getPropertyName())) {
            if(evt.getNewValue() == SwingWorker.StateValue.DONE) {
              // state property changes are notified on the background thread, not the EDT
              SwingUtilities.invokeLater(() -> {
                showProgressPanel(false);
                installed.reInit();
              });
            }
          }
        });
      } else {
        installed.reInit();
      }
    }

    // now actually show/hide the window
    super.setVisible(visible);

    dispose();
  }

  private static Set<Plugin> defaultPlugins = null;

  private static SwingWorker<Void, Void> defaultPluginsWorker = null;

  public static void loadDefaultPlugins() {
    if(defaultPlugins != null) return;

    defaultPluginsWorker = new SwingWorker<Void, Void>() {
      @Override
      protected Void doInBackground() {
        MainFrame.getInstance().downloadWithCache(String.format(DEFAULT_PLUGINS_URL_PATTERN, Main.version)).ifPresent((is) -> {
          List<String> pluginsToLoad = new ArrayList<>();
          try(BufferedReader in = new BufferedReader(
                  new InputStreamReader(is, "UTF-8"))) {
            for(String line = in.readLine(); line != null; line = in.readLine()) {
              pluginsToLoad.add(line);
            }
          } catch(IOException e) {
            log.warn("Unable to load list of default plugins", e);
            return;
          }

          Set<Plugin> loadedSoFar = new HashSet<Plugin>();
          
          int loaded = 0;
          for(String pluginLine : pluginsToLoad) {
            try {
              String[] parts = pluginLine.split("\t", 3);

              // if we don't have three parts then skip this line
              if(parts.length != 3) continue;

              Plugin.Maven plugin = new Plugin.Maven(parts[0], parts[1], parts[2]);

              // this triggers the resolve of the creole metadata jar so we have
              // all the details to fill the table -- but it slows us down!
              plugin.getMetadataXML();

              loadedSoFar.add(plugin);
            } catch(Exception e) {
              e.printStackTrace();
            } finally {
              loaded++;
              setProgress((100*loaded) / pluginsToLoad.size());
            }
          }
          
          defaultPlugins = loadedSoFar;
        });

        return null;
      }
    };

    defaultPluginsWorker.execute();
  }

  /**
   * Returns the list of default plugins for this version of GATE.
   */
  public synchronized static Set<Plugin> getDefaultPlugins() {
    if(defaultPlugins == null) return new HashSet<Plugin>();

    return Collections.unmodifiableSet(defaultPlugins);

  }
}
