/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.monkeyrunner.recorder;

import com.android.monkeyrunner.MonkeyDevice;
import com.android.monkeyrunner.MonkeyImage;
import com.android.monkeyrunner.recorder.actions.Action;
import com.android.monkeyrunner.recorder.actions.DragAction;
import com.android.monkeyrunner.recorder.actions.DragAction.Direction;
import com.android.monkeyrunner.recorder.actions.PressAction;
import com.android.monkeyrunner.recorder.actions.TouchAction;
import com.android.monkeyrunner.recorder.actions.TypeAction;
import com.android.monkeyrunner.recorder.actions.WaitAction;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * MainFrame for MonkeyRecorder.
 */
public class MonkeyRecorderFrame extends JFrame {
    private static final Logger LOG =
        Logger.getLogger(MonkeyRecorderFrame.class.getName());

    private final MonkeyDevice device;

    private static final long serialVersionUID = 1L;
    private JPanel jContentPane = null;
    private JLabel display = null;
    private JScrollPane historyPanel = null;
    private JPanel actionPanel = null;
    private JButton waitButton = null;
    private JButton pressButton = null;
    private JButton typeButton = null;
    private JButton flingButton = null;
    private JButton exportActionButton = null;

    private JButton refreshButton = null;

    private BufferedImage currentImage;  //  @jve:decl-index=0:
    private BufferedImage scaledImage = new BufferedImage(320, 480,
            BufferedImage.TYPE_INT_ARGB);  //  @jve:decl-index=0:

    private JList historyList;
    private ActionListModel actionListModel;

    private final Timer refreshTimer = new Timer(1000, new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            refreshDisplay();  //  @jve:decl-index=0:
        }
    });

    /**
     * This is the default constructor
     */
    public MonkeyRecorderFrame(MonkeyDevice device) {
        this.device = device;
        initialize();
    }

    private void initialize() {
        this.setSize(400, 600);
        this.setContentPane(getJContentPane());
        this.setTitle("MonkeyRecorder");

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                refreshDisplay();
            }});
        refreshTimer.start();
    }

    private void refreshDisplay() {
        MonkeyImage snapshot = device.takeSnapshot();
        currentImage = snapshot.createBufferedImage();

        Graphics2D g = scaledImage.createGraphics();
        g.drawImage(currentImage, 0, 0,
                scaledImage.getWidth(), scaledImage.getHeight(),
                null);
        g.dispose();

        display.setIcon(new ImageIcon(scaledImage));

        pack();
    }

    /**
     * This method initializes jContentPane
     *
     * @return javax.swing.JPanel
     */
    private JPanel getJContentPane() {
        if (jContentPane == null) {
            display = new JLabel();
            jContentPane = new JPanel();
            jContentPane.setLayout(new BorderLayout());
            jContentPane.add(display, BorderLayout.CENTER);
            jContentPane.add(getHistoryPanel(), BorderLayout.EAST);
            jContentPane.add(getActionPanel(), BorderLayout.NORTH);

            display.setPreferredSize(new Dimension(320, 480));

            display.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    touch(event);
                }
            });
        }
        return jContentPane;
    }

    /**
     * This method initializes historyPanel
     *
     * @return javax.swing.JScrollPane
     */
    private JScrollPane getHistoryPanel() {
        if (historyPanel == null) {
            historyPanel = new JScrollPane();
            historyPanel.getViewport().setView(getHistoryList());
        }
        return historyPanel;
    }

    private JList getHistoryList() {
        if (historyList == null) {
            actionListModel = new ActionListModel();
            historyList = new JList(actionListModel);
        }
        return historyList;
    }

    /**
     * This method initializes actionPanel
     *
     * @return javax.swing.JPanel
     */
    private JPanel getActionPanel() {
        if (actionPanel == null) {
            actionPanel = new JPanel();
            actionPanel.setLayout(new BoxLayout(getActionPanel(), BoxLayout.X_AXIS));
            actionPanel.add(getWaitButton(), null);
            actionPanel.add(getPressButton(), null);
            actionPanel.add(getTypeButton(), null);
            actionPanel.add(getFlingButton(), null);
            actionPanel.add(getExportActionButton(), null);
            actionPanel.add(getRefreshButton(), null);
        }
        return actionPanel;
    }

    /**
     * This method initializes waitButton
     *
     * @return javax.swing.JButton
     */
    private JButton getWaitButton() {
        if (waitButton == null) {
            waitButton = new JButton();
            waitButton.setText("Wait");
            waitButton.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    String howLongStr = JOptionPane.showInputDialog("How many seconds to wait?");
                    if (howLongStr != null) {
                        float howLong = Float.parseFloat(howLongStr);
                        addAction(new WaitAction(howLong));
                    }
                }
            });
        }
        return waitButton;
    }

    /**
     * This method initializes pressButton
     *
     * @return javax.swing.JButton
     */
    private JButton getPressButton() {
        if (pressButton == null) {
            pressButton = new JButton();
            pressButton.setText("Press a Button");
            pressButton.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    JPanel panel = new JPanel();
                    JLabel text = new JLabel("What button to press?");
                    JComboBox keys = new JComboBox(PressAction.KEYS);
                    keys.setEditable(true);
                    JComboBox direction = new JComboBox(PressAction.DOWNUP_FLAG_MAP.values().toArray());
                    panel.add(text);
                    panel.add(keys);
                    panel.add(direction);

                    int result = JOptionPane.showConfirmDialog(null, panel, "Input", JOptionPane.OK_CANCEL_OPTION);
                    if (result == JOptionPane.OK_OPTION) {
                        // Look up the "flag" value for the press choice
                        Map<String, String> lookupMap = PressAction.DOWNUP_FLAG_MAP.inverse();
                        String flag = lookupMap.get(direction.getSelectedItem());
                        addAction(new PressAction((String) keys.getSelectedItem(), flag));
                    }
                }
            });
        }
        return pressButton;
    }

    /**
     * This method initializes typeButton
     *
     * @return javax.swing.JButton
     */
    private JButton getTypeButton() {
        if (typeButton == null) {
            typeButton = new JButton();
            typeButton.setText("Type Something");
            typeButton.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    String whatToType = JOptionPane.showInputDialog("What to type?");
                    if (whatToType != null) {
                        addAction(new TypeAction(whatToType));
                    }
                }
            });
        }
        return typeButton;
    }

    /**
     * This method initializes flingButton
     *
     * @return javax.swing.JButton
     */
    private JButton getFlingButton() {
        if (flingButton == null) {
            flingButton = new JButton();
            flingButton.setText("Fling");
            flingButton.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    JPanel panel = new JPanel();
                    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
                    panel.add(new JLabel("Which Direction to fling?"));
                    JComboBox directionChooser = new JComboBox(DragAction.Direction.getNames());
                    panel.add(directionChooser);
                    panel.add(new JLabel("How long to drag (in ms)?"));
                    JTextField ms = new JTextField();
                    ms.setText("1000");
                    panel.add(ms);
                    panel.add(new JLabel("How many steps to do it in?"));
                    JTextField steps = new JTextField();
                    steps.setText("10");
                    panel.add(steps);



                    int result = JOptionPane.showConfirmDialog(null, panel, "Input", JOptionPane.OK_CANCEL_OPTION);
                    if (result == JOptionPane.OK_OPTION) {
                        DragAction.Direction dir =
                            DragAction.Direction.valueOf((String) directionChooser.getSelectedItem());
                        long millis = Long.parseLong(ms.getText());
                        int numSteps = Integer.parseInt(steps.getText());

                        addAction(newFlingAction(dir, numSteps, millis));
                    }
                }
            });
        }
        return flingButton;
    }

    private DragAction newFlingAction(Direction dir, int numSteps, long millis) {
        int width = Integer.parseInt(device.getProperty("display.width"));
        int height = Integer.parseInt(device.getProperty("display.height"));

        // Adjust the w/h to a pct of the total size, so we don't hit things on the "outside"
        width = (int) (width * 0.8f);
        height = (int) (height * 0.8f);
        int minW = (int) (width * 0.2f);
        int minH = (int) (height * 0.2f);

        int midWidth = width / 2;
        int midHeight = height / 2;

        int startx = minW;
        int starty = minH;
        int endx = minW;
        int endy = minH;

        switch (dir) {
            case NORTH:
                startx = endx = midWidth;
                starty = height;
                break;
            case SOUTH:
                startx = endx = midWidth;
                endy = height;
                break;
            case EAST:
                starty = endy = midHeight;
                endx = width;
                break;
            case WEST:
                starty = endy = midHeight;
                startx = width;
                break;
        }

        return new DragAction(dir, startx, starty, endx, endy, numSteps, millis);
    }

    /**
     * This method initializes exportActionButton
     *
     * @return javax.swing.JButton
     */
    private JButton getExportActionButton() {
        if (exportActionButton == null) {
            exportActionButton = new JButton();
            exportActionButton.setText("Export Actions");
            exportActionButton.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent ev) {
                    JFileChooser fc = new JFileChooser();
                    if (fc.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                        try {
                            actionListModel.export(fc.getSelectedFile());
                        } catch (FileNotFoundException e) {
                            LOG.log(Level.SEVERE, "Unable to save file", e);
                        }
                    }
                }
            });
        }
        return exportActionButton;
    }

    /**
     * This method initializes refreshButton
     *
     * @return javax.swing.JButton
     */
    private JButton getRefreshButton() {
        if (refreshButton == null) {
            refreshButton = new JButton();
            refreshButton.setText("Refresh Display");
            refreshButton.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    refreshDisplay();
                }
            });
        }
        return refreshButton;
    }

    private void touch(MouseEvent event) {
        int x = event.getX();
        int y = event.getY();

        // Since we scaled the image down, our x/y are scaled as well.
        double scalex = ((double) currentImage.getWidth()) / ((double) scaledImage.getWidth());
        double scaley = ((double) currentImage.getHeight()) / ((double) scaledImage.getHeight());

        x = (int) (x * scalex);
        y = (int) (y * scaley);

        switch (event.getID()) {
            case MouseEvent.MOUSE_CLICKED:
                addAction(new TouchAction(x, y, MonkeyDevice.DOWN_AND_UP));
                break;
            case MouseEvent.MOUSE_PRESSED:
                addAction(new TouchAction(x, y, MonkeyDevice.DOWN));
                break;
            case MouseEvent.MOUSE_RELEASED:
                addAction(new TouchAction(x, y, MonkeyDevice.UP));
                break;
        }
    }

    public void addAction(Action a) {
        actionListModel.add(a);
        try {
            a.execute(device);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Unable to execute action!", e);
        }
    }
}
