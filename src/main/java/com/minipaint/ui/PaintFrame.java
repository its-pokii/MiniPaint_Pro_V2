package com.minipaint.ui;

import com.minipaint.io.ProjectIO;
import com.minipaint.model.PaintDocument;
import com.minipaint.model.PaintLayer;
import com.minipaint.model.Tool;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** MiniPaint Pro — all tools are standard Java Swing; no external libraries are required. */
public final class PaintFrame extends JFrame {
    private static final Color BLUE = new Color(54, 112, 235);
    private static final Color GREEN = new Color(22, 167, 133);
    private static final Color[] SWATCHES = {
            new Color(35, 43, 62), Color.WHITE, new Color(239, 68, 68),
            new Color(249, 115, 22), new Color(245, 158, 11), new Color(34, 197, 94),
            new Color(20, 184, 166), new Color(14, 165, 233), new Color(54, 112, 235),
            new Color(139, 92, 246), new Color(236, 72, 153), new Color(130, 146, 166),
            new Color(111, 78, 55), new Color(253, 230, 138), new Color(167, 243, 208),
            new Color(203, 213, 225)
    };

    private PaintCanvas canvas;
    private JScrollPane canvasScroll;
    private final JTabbedPane documentTabs = new JTabbedPane();
    private final List<DocumentTab> opened = new ArrayList<>();
    private DocumentTab active;
    private int untitledCount = 1;
    private JSlider layerOpacitySlider;
    private JLabel layerOpacityLabel;

    private static final class DocumentTab {
        final PaintCanvas canvas = new PaintCanvas();
        final JScrollPane scroll = new JScrollPane(canvas);
        final String defaultName;
        File imageFile;
        File projectFile;
        boolean dirty;
        DocumentTab(String name) { defaultName = name; }
    }

    private boolean darkMode;
    private boolean autoSaveEnabled = true;
    private boolean updatingLayers;
    private JLabel statusLabel;
    private JLabel zoomLabel;
    private JLabel dimensionLabel;
    private JLabel colorHexLabel;
    private JPanel colorPreview;
    private DefaultListModel<PaintLayer> layersModel;
    private JList<PaintLayer> layersList;
    private final List<JToggleButton> toolButtons = new ArrayList<>();
    private JLabel activeToolLabel;
    private final Timer autoSaveTimer;

    public PaintFrame() {
        super("MiniPaint Pro V2");
        setSize(1480, 890);
        setMinimumSize(new Dimension(1080, 660));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                if (confirmCloseAll()) dispose();
            }
        });

        addDocumentTab(new PaintDocument(1200, 760), null, null);
        documentTabs.addChangeListener(e -> activateSelectedTab());
        documentTabs.setFont(font(Font.BOLD, 12));
        documentTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        buildInterface();
        installShortcuts();
        active.dirty = false;
        updateTitle();

        autoSaveTimer = new Timer(120_000, e -> {
            if (autoSaveEnabled && opened.stream().anyMatch(t -> t.dirty)) autoSave();
        });
        autoSaveTimer.start();
    }

    private void addDocumentTab(PaintDocument document, File imageFile, File projectFile) {
        DocumentTab tab = new DocumentTab("Sans titre " + untitledCount++);
        if (canvas != null) {
            tab.canvas.setTool(canvas.getTool());
            tab.canvas.setPaintColor(canvas.getPaintColor());
            tab.canvas.setBrushSize(canvas.getBrushSize());
            tab.canvas.setOpacity(canvas.getOpacity());
            tab.canvas.setZoom(canvas.getZoom());
        }
        tab.canvas.setDocument(document);
        tab.imageFile = imageFile;
        tab.projectFile = projectFile;
        tab.canvas.setOnDocumentChanged(() -> {
            tab.dirty = true;
            updateTabCaption(tab);
            if (tab == active) { updateTitle(); updateBottomInfo(); }
        });
        tab.canvas.setOnLayerChanged(() -> { if (tab == active) refreshLayers(); });
        tab.canvas.setOnColorPicked(c -> { if (tab == active) updateSelectedColor(c); });
        tab.canvas.setOnNotification(s -> { if (tab == active) setStatus(s); });
        tab.canvas.setOnMousePosition(p -> {
            if (tab == active && statusLabel != null && p != null && p.x >= 0 && p.y >= 0
                    && p.x < canvas.getDocument().getWidth() && p.y < canvas.getDocument().getHeight()) {
                statusLabel.setText("Position : " + p.x + ", " + p.y + " px");
            }
        });
        tab.scroll.setBorder(BorderFactory.createEmptyBorder());
        tab.scroll.getVerticalScrollBar().setUnitIncrement(18);
        tab.scroll.getHorizontalScrollBar().setUnitIncrement(18);
        opened.add(tab);
        documentTabs.addTab("", tab.scroll);
        updateTabCaption(tab);
        documentTabs.setSelectedComponent(tab.scroll);
        if (active == null) {
            active = tab;
            canvas = tab.canvas;
            canvasScroll = tab.scroll;
        } else activateSelectedTab();
    }

    private void activateSelectedTab() {
        if (documentTabs.getSelectedIndex() < 0) return;
        DocumentTab chosen = opened.get(documentTabs.getSelectedIndex());
        if (chosen == active) return;
        active = chosen;
        canvas = chosen.canvas;
        canvasScroll = chosen.scroll;
        buildInterface();
        updateTitle();
    }

    private void updateTabCaption(DocumentTab tab) {
        int index = opened.indexOf(tab);
        if (index < 0) return;
        String name = tab.projectFile != null ? tab.projectFile.getName()
                : tab.imageFile != null ? tab.imageFile.getName() : tab.defaultName;
        if (name.length() > 25) name = name.substring(0, 22) + "…";
        documentTabs.setTitleAt(index, name + (tab.dirty ? " *" : ""));
        documentTabs.setToolTipTextAt(index, (tab.projectFile != null ? tab.projectFile.getAbsolutePath()
                : tab.imageFile != null ? tab.imageFile.getAbsolutePath() : tab.defaultName));
    }

    private void closeCurrentTab() {
        if (active.dirty && !confirmDiscard()) return;
        int index = documentTabs.getSelectedIndex();
        opened.remove(index);
        active = null;
        documentTabs.removeTabAt(index);
        if (opened.isEmpty()) {
            addDocumentTab(new PaintDocument(1200,760), null, null);
            buildInterface();
        } else activateSelectedTab();
        updateTitle();
    }

    private boolean confirmCloseAll() {
        long changed = opened.stream().filter(t -> t.dirty).count();
        if (changed == 0) return true;
        return JOptionPane.showConfirmDialog(this,
                changed + " dessin(s) comportent des modifications non enregistrées. Quitter sans sauvegarder ?",
                "Dessins non enregistrés", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                == JOptionPane.YES_OPTION;
    }

    private Color bg() { return darkMode ? new Color(20, 26, 39) : new Color(241, 244, 249); }
    private Color panel() { return darkMode ? new Color(30, 38, 54) : Color.WHITE; }
    private Color surface() { return darkMode ? new Color(40, 50, 69) : new Color(247, 249, 253); }
    private Color line() { return darkMode ? new Color(59, 70, 88) : new Color(226, 231, 241); }
    private Color ink() { return darkMode ? new Color(233, 239, 251) : new Color(34, 47, 71); }
    private Color muted() { return darkMode ? new Color(155, 168, 191) : new Color(117, 131, 153); }

    private void buildInterface() {
        getContentPane().removeAll();
        getContentPane().setLayout(new BorderLayout());
        getContentPane().setBackground(bg());
        setJMenuBar(createMenuBar());
        getContentPane().add(createHeader(), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(14, 0));
        body.setBackground(bg());
        body.setBorder(new EmptyBorder(14, 14, 12, 14));
        body.add(createToolsSidebar(), BorderLayout.WEST);
        body.add(createCanvasArea(), BorderLayout.CENTER);
        body.add(createPropertiesSidebar(), BorderLayout.EAST);
        getContentPane().add(body, BorderLayout.CENTER);
        getContentPane().add(createStatusBar(), BorderLayout.SOUTH);
        canvas.setDarkMode(darkMode);
        refreshLayers();
        updateSelectedColor(canvas.getPaintColor());
        updateBottomInfo();
        getContentPane().revalidate();
        getContentPane().repaint();
    }

    private JPanel createHeader() {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(panel());
        outer.setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, line()),
                new EmptyBorder(15, 21, 15, 21)));

        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        brand.setOpaque(false);
        JLabel logo = new JLabel(new AppLogo());
        brand.add(logo);
        JPanel brandWords = vertical();
        JLabel title = new JLabel("MiniPaint Pro  ·  V2");
        title.setFont(font(Font.BOLD, 20));
        title.setForeground(ink());
        JLabel subtitle = new JLabel("Atelier de dessin  •  Java Swing");
        subtitle.setFont(font(Font.PLAIN, 11));
        subtitle.setForeground(muted());
        brandWords.add(title);
        brandWords.add(subtitle);
        brand.add(brandWords);
        outer.add(brand, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 9, 1));
        actions.setOpaque(false);
        actions.add(makeButton("+ Onglet", this::newDocument, false));
        actions.add(makeButton("Fermer onglet", this::closeCurrentTab, false));
        actions.add(makeButton("Ouvrir", this::openImage, false));
        actions.add(makeButton("Enregistrer", this::saveImage, true));
        actions.add(makeButton("Exporter", this::saveAs, false));
        actions.add(makeButton(darkMode ? "Mode clair" : "Mode sombre", () -> {
            darkMode = !darkMode;
            buildInterface();
        }, false));
        outer.add(actions, BorderLayout.EAST);
        return outer;
    }

    private JPanel createToolsSidebar() {
        JPanel side = vertical();
        side.setPreferredSize(new Dimension(216, 600));
        side.setBackground(panel());
        side.setBorder(new CompoundBorder(new LineBorder(line(), 1, true), new EmptyBorder(17, 13, 17, 13)));
        side.add(heading("OUTILS", "Choisissez votre outil"));
        side.add(Box.createVerticalStrut(17));
        toolButtons.clear();
        ButtonGroup group = new ButtonGroup();
        for (String category : new String[]{"Dessin", "Formes", "Autres"}) {
            JLabel section = tinyLabel(category.toUpperCase());
            section.setAlignmentX(Component.LEFT_ALIGNMENT);
            side.add(section);
            side.add(Box.createVerticalStrut(8));
            JPanel grid = new JPanel(new GridLayout(0, 2, 7, 7));
            grid.setBackground(panel());
            grid.setAlignmentX(Component.LEFT_ALIGNMENT);
            grid.setMaximumSize(new Dimension(190, category.equals("Formes") ? 220 : 170));
            for (Tool tool : Tool.values()) {
                if (!tool.group().equals(category)) continue;
                JToggleButton b = new JToggleButton("<html><center>" + tool.label() + "</center></html>", new ToolIcon(tool));
                b.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI());
                b.setToolTipText(tool.help());
                b.setHorizontalTextPosition(SwingConstants.CENTER);
                b.setVerticalTextPosition(SwingConstants.BOTTOM);
                b.setIconTextGap(5);
                b.setFont(font(Font.PLAIN, 11));
                b.setForeground(ink());
                b.setBackground(surface());
                b.setOpaque(true);
                b.setFocusPainted(false);
                b.setBorder(new CompoundBorder(new LineBorder(line(), 1, true), new EmptyBorder(8, 2, 7, 2)));
                b.setPreferredSize(new Dimension(85, 67));
                b.addActionListener(e -> {
                    canvas.setTool(tool);
                    activeToolLabel.setText("Outil : " + tool.label());
                    for (JToggleButton t : toolButtons) {
                        t.setBackground(t.isSelected() ? BLUE : surface());
                        t.setForeground(t.isSelected() ? Color.WHITE : ink());
                    }
                });
                if (canvas.getTool() == tool) b.setSelected(true);
                group.add(b);
                toolButtons.add(b);
                grid.add(b);
            }
            side.add(grid);
            side.add(Box.createVerticalStrut(17));
        }
        for (JToggleButton b : toolButtons) {
            if (b.isSelected()) { b.setBackground(BLUE); b.setForeground(Color.WHITE); }
        }
        side.add(Box.createVerticalGlue());
        JLabel hint = new JLabel("Ctrl + molette  :  Zoom");
        hint.setForeground(muted());
        hint.setFont(font(Font.PLAIN, 11));
        side.add(hint);

        JScrollPane scroll = new JScrollPane(side);
        scroll.setPreferredSize(new Dimension(218, 100));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setPreferredSize(new Dimension(218, 500));
        wrapper.add(scroll, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel createCanvasArea() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(bg());
        panel.setBorder(new LineBorder(line(), 1, true));
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBackground(panel());
        toolbar.setBorder(new EmptyBorder(10, 13, 10, 13));
        activeToolLabel = new JLabel("Outil : " + canvas.getTool().label());
        activeToolLabel.setForeground(ink());
        activeToolLabel.setFont(font(Font.BOLD, 13));
        toolbar.add(activeToolLabel, BorderLayout.WEST);

        JPanel zoomActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        zoomActions.setOpaque(false);
        JButton zoomOut = makeSmallButton("−", () -> canvas.setZoom(canvas.getZoom() / 1.25));
        JButton zoomIn = makeSmallButton("+", () -> canvas.setZoom(canvas.getZoom() * 1.25));
        zoomLabel = new JLabel(Math.round(canvas.getZoom() * 100) + " %");
        zoomLabel.setForeground(ink());
        zoomLabel.setHorizontalAlignment(SwingConstants.CENTER);
        zoomLabel.setPreferredSize(new Dimension(48, 29));
        JButton resetZoom = makeSmallButton("100 %", () -> canvas.setZoom(1));
        zoomOut.addActionListener(e -> updateBottomInfo());
        zoomIn.addActionListener(e -> updateBottomInfo());
        resetZoom.addActionListener(e -> updateBottomInfo());
        JLabel zoomTitle = new JLabel("Zoom");
        zoomTitle.setForeground(muted());
        zoomActions.add(zoomTitle);
        zoomActions.add(zoomOut);
        zoomActions.add(zoomLabel);
        zoomActions.add(zoomIn);
        zoomActions.add(resetZoom);
        toolbar.add(zoomActions, BorderLayout.EAST);
        panel.add(toolbar, BorderLayout.NORTH);

        for (DocumentTab tab : opened) {
            tab.canvas.setDarkMode(darkMode);
            tab.scroll.getViewport().setBackground(darkMode ? new Color(23,30,43) : new Color(225,231,240));
            tab.scroll.setViewportBorder(new EmptyBorder(14,14,14,14));
            tab.scroll.getViewport().setScrollMode(JViewport.BLIT_SCROLL_MODE);
        }
        documentTabs.setBackground(panel());
        documentTabs.setForeground(ink());
        panel.add(documentTabs, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createPropertiesSidebar() {
        JPanel content = vertical();
        content.setBackground(panel());
        content.setBorder(new EmptyBorder(17, 16, 16, 16));
        content.add(heading("PROPRIÉTÉS", "Personnalisez le dessin"));
        content.add(Box.createVerticalStrut(19));
        content.add(tinyLabel("PALETTE DE COULEURS"));
        content.add(Box.createVerticalStrut(10));
        JPanel swatches = new JPanel(new GridLayout(4, 4, 8, 8));
        swatches.setBackground(panel());
        swatches.setAlignmentX(Component.LEFT_ALIGNMENT);
        swatches.setMaximumSize(new Dimension(270, 150));
        for (Color swatch : SWATCHES) {
            JButton dot = new JButton();
            dot.setToolTipText(toHex(swatch));
            dot.setBackground(swatch);
            dot.setOpaque(true);
            dot.setFocusPainted(false);
            dot.setPreferredSize(new Dimension(37, 31));
            dot.setBorder(new LineBorder(swatch.equals(Color.WHITE) ? line() : swatch.darker(), 1, true));
            dot.addActionListener(e -> updateSelectedColor(swatch));
            swatches.add(dot);
        }
        content.add(swatches);
        content.add(Box.createVerticalStrut(13));

        JPanel selectedColor = new JPanel(new BorderLayout(10, 0));
        selectedColor.setBackground(panel());
        selectedColor.setMaximumSize(new Dimension(300, 47));
        colorPreview = new JPanel();
        colorPreview.setPreferredSize(new Dimension(46, 36));
        colorPreview.setBorder(new LineBorder(line(), 1, true));
        selectedColor.add(colorPreview, BorderLayout.WEST);
        colorHexLabel = new JLabel(toHex(canvas.getPaintColor()));
        colorHexLabel.setForeground(ink());
        colorHexLabel.setFont(font(Font.BOLD, 13));
        selectedColor.add(colorHexLabel, BorderLayout.CENTER);
        JButton choose = makeSmallButton("Choisir", () -> {
            Color c = JColorChooser.showDialog(this, "Choisir une couleur", canvas.getPaintColor());
            if (c != null) updateSelectedColor(c);
        });
        selectedColor.add(choose, BorderLayout.EAST);
        content.add(selectedColor);
        content.add(Box.createVerticalStrut(19));

        content.add(tinyLabel("ÉPAISSEUR DU PINCEAU"));
        JLabel sizeText = infoLabel(canvas.getBrushSize() + " px");
        content.add(sizeText);
        JSlider size = slider(1, 60, canvas.getBrushSize());
        size.addChangeListener(e -> {
            canvas.setBrushSize(size.getValue());
            sizeText.setText(size.getValue() + " px");
        });
        content.add(size);
        content.add(Box.createVerticalStrut(13));

        content.add(tinyLabel("OPACITÉ"));
        JLabel opacityText = infoLabel(canvas.getOpacity() + " %");
        content.add(opacityText);
        JSlider alpha = slider(1, 100, canvas.getOpacity());
        alpha.addChangeListener(e -> {
            canvas.setOpacity(alpha.getValue());
            opacityText.setText(alpha.getValue() + " %");
        });
        content.add(alpha);
        content.add(Box.createVerticalStrut(16));
        JSeparator separator = new JSeparator();
        separator.setMaximumSize(new Dimension(300, 1));
        content.add(separator);
        content.add(Box.createVerticalStrut(16));

        content.add(heading("CALQUES", "Organisez votre image"));
        content.add(Box.createVerticalStrut(10));
        layersModel = new DefaultListModel<>();
        layersList = new JList<>(layersModel);
        layersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        layersList.setCellRenderer(new LayerRenderer());
        layersList.setFixedCellHeight(49);
        layersList.setBackground(surface());
        layersList.addListSelectionListener((ListSelectionEvent e) -> {
            if (updatingLayers || e.getValueIsAdjusting()) return;
            int selected = layersList.getSelectedIndex();
            if (selected >= 0) canvas.setActiveLayerIndex(layersModel.getSize() - 1 - selected);
        });
        JScrollPane listScroll = new JScrollPane(layersList);
        listScroll.setBorder(new LineBorder(line(), 1, true));
        listScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        listScroll.setPreferredSize(new Dimension(252, 160));
        listScroll.setMaximumSize(new Dimension(350, 175));
        content.add(listScroll);
        content.add(Box.createVerticalStrut(9));
        layerOpacityLabel = infoLabel("Opacité du calque : " + canvas.getDocument().getActiveLayer().getOpacity() + " %");
        content.add(layerOpacityLabel);
        layerOpacitySlider = slider(0, 100, canvas.getDocument().getActiveLayer().getOpacity());
        layerOpacitySlider.addChangeListener(e -> {
            if (updatingLayers || layerOpacitySlider.getValueIsAdjusting()) return;
            canvas.setActiveLayerOpacity(layerOpacitySlider.getValue());
        });
        content.add(layerOpacitySlider);
        content.add(Box.createVerticalStrut(10));

        JPanel layerRow1 = new JPanel(new GridLayout(1, 3, 6, 0));
        layerRow1.setMaximumSize(new Dimension(330, 34));
        layerRow1.setAlignmentX(Component.LEFT_ALIGNMENT);
        layerRow1.setBackground(panel());
        layerRow1.add(makeSmallButton("+ Ajouter", canvas::addLayer));
        layerRow1.add(makeSmallButton("Suppr.", canvas::removeLayer));
        layerRow1.add(makeSmallButton("Visibilité", canvas::toggleLayerVisibility));
        content.add(layerRow1);
        content.add(Box.createVerticalStrut(7));
        JPanel layerRow2 = new JPanel(new GridLayout(1, 3, 6, 0));
        layerRow2.setMaximumSize(new Dimension(330, 34));
        layerRow2.setAlignmentX(Component.LEFT_ALIGNMENT);
        layerRow2.setBackground(panel());
        layerRow2.add(makeSmallButton("Monter", () -> canvas.moveLayer(1)));
        layerRow2.add(makeSmallButton("Baisser", () -> canvas.moveLayer(-1)));
        layerRow2.add(makeSmallButton("Renommer", this::renameLayer));
        content.add(layerRow2);
        content.add(Box.createVerticalStrut(7));
        JPanel layerRow3 = new JPanel(new GridLayout(1, 2, 6, 0));
        layerRow3.setMaximumSize(new Dimension(330,34));
        layerRow3.setAlignmentX(Component.LEFT_ALIGNMENT);
        layerRow3.setBackground(panel());
        layerRow3.add(makeSmallButton("Dupliquer", canvas::duplicateLayer));
        layerRow3.add(makeSmallButton("Fusionner ↓", canvas::mergeLayerDown));
        content.add(layerRow3);
        content.add(Box.createVerticalStrut(19));

        JCheckBox autosave = new JCheckBox("Sauvegarde auto (2 min)", autoSaveEnabled);
        autosave.setForeground(muted());
        autosave.setBackground(panel());
        autosave.setFont(font(Font.PLAIN, 12));
        autosave.setAlignmentX(Component.LEFT_ALIGNMENT);
        autosave.addActionListener(e -> autoSaveEnabled = autosave.isSelected());
        content.add(autosave);
        content.add(Box.createVerticalGlue());
        JScrollPane scroller = new JScrollPane(content);
        scroller.setBorder(new LineBorder(line(), 1, true));
        scroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroller.getVerticalScrollBar().setUnitIncrement(17);
        scroller.setPreferredSize(new Dimension(394, 500));
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setPreferredSize(new Dimension(394, 500));
        wrapper.add(scroller, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel createStatusBar() {
        JPanel status = new JPanel(new BorderLayout(10, 0));
        status.setBackground(panel());
        status.setBorder(new CompoundBorder(new MatteBorder(1, 0, 0, 0, line()),
                new EmptyBorder(9, 20, 9, 20)));
        statusLabel = infoLabel("Prêt à dessiner");
        status.add(statusLabel, BorderLayout.WEST);
        dimensionLabel = infoLabel("");
        status.add(dimensionLabel, BorderLayout.EAST);
        return status;
    }

    private JMenuBar createMenuBar() {
        JMenuBar bar = new JMenuBar();
        bar.setBackground(panel());
        bar.setBorder(new MatteBorder(0, 0, 1, 0, line()));
        JMenu file = menu("Fichier");
        addItem(file, "Nouvel onglet", KeyEvent.VK_N, 0, this::newDocument);
        addItem(file, "Ouvrir une image...", KeyEvent.VK_O, 0, this::openImage);
        addItem(file, "Ouvrir un projet MiniPaint...", 0, 0, this::openProject);
        addItem(file, "Enregistrer l'image", KeyEvent.VK_S, 0, this::saveImage);
        addItem(file, "Enregistrer le projet (.mpaint)", KeyEvent.VK_P, 0, this::saveProject);
        addItem(file, "Enregistrer sous / Exporter...", KeyEvent.VK_S, InputEvent.SHIFT_DOWN_MASK, this::saveAs);
        file.addSeparator();
        addItem(file, "Fermer l'onglet", KeyEvent.VK_W, 0, this::closeCurrentTab);
        addItem(file, "Quitter", KeyEvent.VK_Q, 0, () -> { if (confirmCloseAll()) dispose(); });

        JMenu edit = menu("Édition");
        addItem(edit, "Annuler", KeyEvent.VK_Z, 0, canvas::undo);
        addItem(edit, "Rétablir", KeyEvent.VK_Y, 0, canvas::redo);
        edit.addSeparator();
        addItem(edit, "Tout sélectionner", KeyEvent.VK_A, 0, this::selectAllTool);
        addItem(edit, "Copier", KeyEvent.VK_C, 0, canvas::copySelection);
        addItem(edit, "Couper", KeyEvent.VK_X, 0, canvas::cutSelection);
        addItem(edit, "Coller", KeyEvent.VK_V, 0, this::pasteTool);
        edit.addSeparator();
        addItem(edit, "Effacer la sélection", KeyEvent.VK_DELETE, 0, canvas::deleteSelection);
        addItem(edit, "Vider le calque", 0, 0, this::confirmClearLayer);

        JMenu image = menu("Image");
        addItem(image, "Recadrer selon la sélection", 0, 0, this::cropSelection);
        image.addSeparator();
        addItem(image, "Rotation 90° horaire", 0, 0, () -> canvas.rotate(90));
        addItem(image, "Rotation 90° antihoraire", 0, 0, () -> canvas.rotate(-90));
        addItem(image, "Rotation 180°", 0, 0, () -> canvas.rotate(180));
        addItem(image, "Miroir horizontal", 0, 0, () -> canvas.flip(true));
        addItem(image, "Miroir vertical", 0, 0, () -> canvas.flip(false));
        image.addSeparator();
        addItem(image, "Redimensionner l'image (mise à l'échelle)...", 0, 0, this::scaleImage);
        addItem(image, "Redimensionner la zone de dessin (sans étirer)...", 0, 0, this::resizeCanvas);
        JMenu filters = menu("Filtres");
        addItem(filters, "Flou...", 0, 0, () -> applyKernel(false));
        addItem(filters, "Netteté / Sharpen...", 0, 0, () -> applyKernel(true));
        addItem(filters, "Luminosité / Contraste...", 0, 0, this::brightnessContrast);
        JMenu layers = menu("Calques");
        addItem(layers, "Nouveau calque", KeyEvent.VK_N, InputEvent.SHIFT_DOWN_MASK, canvas::addLayer);
        addItem(layers, "Supprimer le calque actif", 0, 0, canvas::removeLayer);
        addItem(layers, "Renommer le calque", 0, 0, this::renameLayer);
        addItem(layers, "Masquer / Afficher le calque", 0, 0, canvas::toggleLayerVisibility);
        addItem(layers, "Dupliquer le calque", 0, 0, canvas::duplicateLayer);
        addItem(layers, "Fusionner vers le bas", 0, 0, canvas::mergeLayerDown);

        JMenu view = menu("Affichage");
        addItem(view, "Zoom avant", KeyEvent.VK_EQUALS, 0, () -> { canvas.setZoom(canvas.getZoom() * 1.25); updateBottomInfo(); });
        addItem(view, "Zoom arrière", KeyEvent.VK_MINUS, 0, () -> { canvas.setZoom(canvas.getZoom() / 1.25); updateBottomInfo(); });
        addItem(view, "Zoom 100 %", KeyEvent.VK_0, 0, () -> { canvas.setZoom(1); updateBottomInfo(); });
        addItem(view, "Basculer le thème", 0, 0, () -> { darkMode = !darkMode; buildInterface(); });
        JMenu help = menu("Aide");
        addItem(help, "Raccourcis clavier", 0, 0, this::showHelp);
        addItem(help, "À propos", 0, 0, () -> JOptionPane.showMessageDialog(this,
                "MiniPaint Pro V2\nProjet pédagogique Java Swing / Graphics2D\nJava 21+ — sans dépendances externes",
                "À propos", JOptionPane.INFORMATION_MESSAGE));
        bar.add(file); bar.add(edit); bar.add(image); bar.add(filters); bar.add(layers); bar.add(view); bar.add(help);
        return bar;
    }

    private JMenu menu(String title) {
        JMenu item = new JMenu(title);
        item.setForeground(ink());
        item.setFont(font(Font.PLAIN, 12));
        return item;
    }

    private void addItem(JMenu parent, String title, int key, int modifier, Runnable action) {
        JMenuItem item = new JMenuItem(title);
        item.addActionListener(e -> action.run());
        if (key != 0) item.setAccelerator(KeyStroke.getKeyStroke(key, InputEvent.CTRL_DOWN_MASK | modifier));
        parent.add(item);
    }

    private void installShortcuts() {
        JRootPane root = getRootPane();
        bind(root, "ESCAPE", "deselect", canvas::deselect);
        bind(root, "ctrl D", "deselect2", canvas::deselect);
        bind(root, "ctrl A", "selectAll2", this::selectAllTool);
        bind(root, "DELETE", "deleteSelection", canvas::deleteSelection);
    }

    private static void bind(JRootPane root, String stroke, String name, Runnable action) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(stroke), name);
        root.getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { action.run(); }
        });
    }

    private void updateSelectedColor(Color color) {
        canvas.setPaintColor(color);
        if (colorPreview != null) colorPreview.setBackground(color);
        if (colorHexLabel != null) colorHexLabel.setText(toHex(color));
    }

    private void refreshLayers() {
        if (layersModel == null || layersList == null) return;
        updatingLayers = true;
        layersModel.clear();
        List<PaintLayer> all = canvas.getDocument().getLayers();
        for (int i = all.size() - 1; i >= 0; i--) layersModel.addElement(all.get(i));
        layersList.setSelectedIndex(all.size() - 1 - canvas.getDocument().getActiveIndex());
        layersList.repaint();
        if (layerOpacitySlider != null) {
            layerOpacitySlider.setValue(canvas.getDocument().getActiveLayer().getOpacity());
            if (layerOpacityLabel != null) layerOpacityLabel.setText("Opacité du calque : " + layerOpacitySlider.getValue() + " %");
        }
        updatingLayers = false;
        updateBottomInfo();
    }

    private void updateBottomInfo() {
        if (dimensionLabel != null) {
            PaintDocument doc = canvas.getDocument();
            dimensionLabel.setText(doc.getWidth() + " × " + doc.getHeight() + " px   •   "
                    + doc.getLayers().size() + " calque(s)   •   " + Math.round(canvas.getZoom() * 100) + " %");
        }
        if (zoomLabel != null) zoomLabel.setText(Math.round(canvas.getZoom() * 100) + " %");
    }

    private void setStatus(String message) {
        if (statusLabel != null) statusLabel.setText(message);
        updateBottomInfo();
    }

    private void updateTitle() {
        setTitle("MiniPaint Pro V2" + (active.projectFile != null ? " — " + active.projectFile.getName()
                : active.imageFile != null ? " — " + active.imageFile.getName() : " — Sans titre")
                + (active.dirty ? " *" : ""));
    }

    private boolean confirmDiscard() {
        if (!active.dirty) return true;
        int reply = JOptionPane.showConfirmDialog(this,
                "Le dessin comporte des modifications non enregistrées.\nVoulez-vous continuer sans enregistrer ?",
                "Modifications non enregistrées", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        return reply == JOptionPane.YES_OPTION;
    }

    private int[] askDimensions(String title, int defaultW, int defaultH) {
        JSpinner width = new JSpinner(new SpinnerNumberModel(defaultW, 1, 5000, 10));
        JSpinner height = new JSpinner(new SpinnerNumberModel(defaultH, 1, 5000, 10));
        JPanel form = new JPanel(new GridLayout(2, 2, 8, 8));
        form.add(new JLabel("Largeur (px)")); form.add(width);
        form.add(new JLabel("Hauteur (px)")); form.add(height);
        while (true) {
            int result = JOptionPane.showConfirmDialog(this, form, title,
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) return null;
            int w = (Integer) width.getValue(), h = (Integer) height.getValue();
            try {
                PaintDocument.validateSize(w, h);
                return new int[]{w, h};
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Dimensions invalides", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private void newDocument() {
        int[] dimensions = askDimensions("Nouveau dessin", 1200, 760);
        if (dimensions == null) return;
        addDocumentTab(new PaintDocument(dimensions[0], dimensions[1]), null, null);
        updateTitle();
        setStatus("Nouveau dessin créé.");
    }

    private void resizeCanvas() {
        PaintDocument doc = canvas.getDocument();
        int[] size = askDimensions("Redimensionner la zone de dessin", doc.getWidth(), doc.getHeight());
        if (size == null) return;
        canvas.resizeCanvas(size[0], size[1]);
        setStatus("Zone de dessin redimensionnée.");
    }

    private void openImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Ouvrir une image");
        chooser.setFileFilter(new FileNameExtensionFilter("Images PNG, JPG, BMP", "png", "jpg", "jpeg", "bmp"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) throw new IOException("Format d'image non pris en charge.");
            PaintDocument doc = new PaintDocument(image.getWidth(), image.getHeight());
            doc.getActiveLayer().setName("Image importée");
            Graphics2D g = doc.getActiveLayer().getImage().createGraphics();
            g.drawImage(image, 0, 0, null);
            g.dispose();
            addDocumentTab(doc, file, null);
            updateTitle();
            setStatus("Image ouverte : " + file.getName());
        } catch (IOException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, "Impossible d'ouvrir l'image :\n" + ex.getMessage(),
                    "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openProject() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Ouvrir un projet MiniPaint");
        chooser.setFileFilter(new FileNameExtensionFilter("Projet MiniPaint (*.mpaint)", "mpaint"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            File file = chooser.getSelectedFile();
            PaintDocument project = ProjectIO.load(file);
            addDocumentTab(project, null, file);
            updateTitle();
            setStatus("Projet ouvert : " + file.getName());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Impossible d'ouvrir le projet :\n" + ex.getMessage(),
                    "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveProject() {
        File target = active.projectFile;
        if (target == null) {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Enregistrer le projet avec ses calques");
            chooser.setFileFilter(new FileNameExtensionFilter("Projet MiniPaint (*.mpaint)", "mpaint"));
            chooser.setSelectedFile(new File("mon-projet.mpaint"));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            target = chooser.getSelectedFile();
            if (!target.getName().toLowerCase().endsWith(".mpaint")) {
                target = new File(target.getAbsolutePath() + ".mpaint");
            }
            if (target.exists() && JOptionPane.showConfirmDialog(this, "Ce projet existe déjà. Remplacer ?",
                    "Confirmer", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        }
        try {
            ProjectIO.save(canvas.getDocument(), target);
            active.projectFile = target;
            active.dirty = false;
            updateTabCaption(active);
            updateTitle();
            setStatus("Projet enregistré : " + target.getAbsolutePath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Impossible d'enregistrer le projet :\n" + ex.getMessage(),
                    "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveImage() {
        if (active.imageFile == null) { saveAs(); return; }
        saveTo(active.imageFile);
    }

    private void saveAs() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Enregistrer / Exporter l'image");
        FileNameExtensionFilter png = new FileNameExtensionFilter("Image PNG (*.png)", "png");
        FileNameExtensionFilter jpg = new FileNameExtensionFilter("Image JPEG (*.jpg)", "jpg", "jpeg");
        FileNameExtensionFilter bmp = new FileNameExtensionFilter("Image BMP (*.bmp)", "bmp");
        chooser.addChoosableFileFilter(png);
        chooser.addChoosableFileFilter(jpg);
        chooser.addChoosableFileFilter(bmp);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(png);
        chooser.setSelectedFile(new File(active.imageFile == null ? "mon-dessin.png" : active.imageFile.getName()));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        String name = file.getName().toLowerCase();
        String extension = chooser.getFileFilter() == jpg ? ".jpg" : chooser.getFileFilter() == bmp ? ".bmp" : ".png";
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".bmp")) {
            file = new File(file.getAbsolutePath().replaceFirst("(?i)\\.(png|jpg|jpeg|bmp)$", "") + extension);
        } else {
            file = new File(file.getAbsolutePath() + extension);
        }
        if (file.exists() && JOptionPane.showConfirmDialog(this,
                "Ce fichier existe déjà. Voulez-vous le remplacer ?", "Confirmer",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        saveTo(file);
    }

    private void saveTo(File file) {
        try {
            String lower = file.getName().toLowerCase();
            String format = lower.endsWith(".jpg") || lower.endsWith(".jpeg") ? "jpg"
                    : lower.endsWith(".bmp") ? "bmp" : "png";
            boolean success = ImageIO.write(canvas.getDocument().render(true), format, file);
            if (!success) throw new IOException("Format non disponible : " + format);
            active.imageFile = file;
            if (active.projectFile == null && canvas.getDocument().getLayers().size() == 1) active.dirty = false;
            updateTabCaption(active);
            updateTitle();
            setStatus("Image enregistrée : " + file.getAbsolutePath());
        } catch (IOException | SecurityException ex) {
            JOptionPane.showMessageDialog(this, "Échec de l'enregistrement :\n" + ex.getMessage(),
                    "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void autoSave() {
        try {
            Path folder = Path.of(System.getProperty("user.home"), "MiniPaint");
            Files.createDirectories(folder);
            int saved = 0;
            for (int i = 0; i < opened.size(); i++) {
                DocumentTab tab = opened.get(i);
                if (!tab.dirty) continue;
                Path file = folder.resolve("autosave-onglet-" + (i + 1) + ".mpaint");
                ProjectIO.save(tab.canvas.getDocument(), file.toFile());
                saved++;
            }
            if (saved > 0) setStatus("Sauvegarde automatique de " + saved + " dessin(s) (.mpaint).");
        } catch (IOException | SecurityException ex) {
            setStatus("Sauvegarde automatique impossible : " + ex.getMessage());
        }
    }

    private void cropSelection() {
        if (canvas.cropToSelection()) setStatus("Image recadrée selon la sélection.");
    }

    private void scaleImage() {
        PaintDocument doc = canvas.getDocument();
        int[] dimensions = askDimensions("Redimensionner l'image : les pixels seront étirés", doc.getWidth(), doc.getHeight());
        if (dimensions == null) return;
        try {
            canvas.scaleImage(dimensions[0], dimensions[1]);
            setStatus("Image redimensionnée à " + dimensions[0] + " × " + dimensions[1]);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Redimensionnement impossible", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void applyKernel(boolean sharpen) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(1, 1, 5, 1));
        JPanel panel = new JPanel(new GridLayout(2,1));
        panel.add(new JLabel("Intensité (1–5) :"));
        panel.add(spinner);
        int answer = JOptionPane.showConfirmDialog(this, panel,
                sharpen ? "Netteté / Sharpen — calque actif" : "Flou / Blur — calque actif",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return;
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            canvas.applyKernel(sharpen, (Integer)spinner.getValue());
            setStatus(sharpen ? "Netteté appliquée." : "Flou appliqué.");
        } finally { setCursor(Cursor.getDefaultCursor()); }
    }

    private void brightnessContrast() {
        JSlider brightness = new JSlider(-100,100,0);
        JSlider contrast = new JSlider(-100,100,0);
        brightness.setMajorTickSpacing(50); brightness.setPaintLabels(true); brightness.setPaintTicks(true);
        contrast.setMajorTickSpacing(50); contrast.setPaintLabels(true); contrast.setPaintTicks(true);
        JPanel form = new JPanel(new GridLayout(4,1,7,7));
        form.add(new JLabel("Luminosité (-100 à +100)")); form.add(brightness);
        form.add(new JLabel("Contraste (-100 à +100)")); form.add(contrast);
        int answer = JOptionPane.showConfirmDialog(this, form, "Luminosité / Contraste — calque actif",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer == JOptionPane.OK_OPTION) {
            canvas.applyBrightnessContrast(brightness.getValue(), contrast.getValue());
            setStatus("Luminosité / contraste appliqués.");
        }
    }

    private void confirmClearLayer() {
        if (JOptionPane.showConfirmDialog(this,
                "Effacer tout le contenu du calque actif ?", "Effacer le calque",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) canvas.clearActiveLayer();
    }

    private void renameLayer() {
        String value = JOptionPane.showInputDialog(this, "Nom du calque :",
                canvas.getDocument().getActiveLayer().getName());
        canvas.renameLayer(value);
    }

    private void selectAllTool() {
        canvas.selectAll();
        selectToolButton(Tool.SELECT);
    }

    private void pasteTool() {
        canvas.pasteSelection();
        selectToolButton(Tool.SELECT);
    }

    private void selectToolButton(Tool tool) {
        for (int i = 0; i < toolButtons.size(); i++) {
            JToggleButton button = toolButtons.get(i);
            if (button.getToolTipText().equals(tool.help())) {
                button.setSelected(true);
            }
            button.setBackground(button.isSelected() ? BLUE : surface());
            button.setForeground(button.isSelected() ? Color.WHITE : ink());
        }
        if (activeToolLabel != null) activeToolLabel.setText("Outil : " + tool.label());
    }

    private void showHelp() {
        JTextArea text = new JTextArea(
                "Ctrl + N  : Nouveau document\n"
                        + "Ctrl + O  : Ouvrir une image\n"
                        + "Ctrl + S  : Enregistrer\n"
                        + "Ctrl + Maj + S  : Enregistrer sous\n"
                        + "Ctrl + Z  : Annuler\n"
                        + "Ctrl + Y  : Rétablir\n"
                        + "Ctrl + C / X / V  : Copier / Couper / Coller\n"
                        + "Ctrl + A  : Tout sélectionner\n"
                        + "Ctrl + D / Échap  : Désélectionner\n"
                        + "Suppr  : Effacer la sélection\n"
                        + "Ctrl + Maj + N  : Ajouter un calque\n"
                        + "Ctrl + molette  : Zoomer / Dézoomer\n"
                        + "Sur une sélection : glisser pour déplacer le contenu du calque actif.");
        text.setEditable(false);
        text.setFont(font(Font.PLAIN, 13));
        text.setBackground(UIManager.getColor("Panel.background"));
        JOptionPane.showMessageDialog(this, text, "Raccourcis et conseils", JOptionPane.INFORMATION_MESSAGE);
    }

    private JLabel heading(String title, String subtitle) {
        return createHeadingLabel(title, subtitle);
    }

    private JLabel createHeadingLabel(String title, String subtitle) {
        JLabel label = new JLabel("<html><b>" + title + "</b><br><span style='font-size:10px'>" + subtitle + "</span></html>");
        label.setForeground(ink());
        label.setFont(font(Font.PLAIN, 13));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JPanel vertical() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(true);
        p.setBackground(panel());
        return p;
    }

    private JLabel tinyLabel(String value) {
        JLabel label = new JLabel(value);
        label.setForeground(muted());
        label.setFont(font(Font.BOLD, 10));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JLabel infoLabel(String value) {
        JLabel label = new JLabel(value);
        label.setForeground(muted());
        label.setFont(font(Font.PLAIN, 12));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JSlider slider(int min, int max, int value) {
        JSlider s = new JSlider(min, max, value);
        s.setOpaque(true);
        s.setBackground(panel());
        s.setForeground(BLUE);
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.setMaximumSize(new Dimension(300, 31));
        return s;
    }

    private JButton makeButton(String text, Runnable action, boolean primary) {
        JButton button = new JButton(text);
        button.setFont(font(Font.BOLD, 12));
        button.setForeground(primary ? Color.WHITE : ink());
        button.setBackground(primary ? BLUE : surface());
        button.setOpaque(true);
        button.setFocusPainted(false);
        button.setBorder(new CompoundBorder(new LineBorder(primary ? BLUE : line(), 1, true),
                new EmptyBorder(9, 13, 9, 13)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(e -> action.run());
        return button;
    }

    private JButton makeSmallButton(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setFont(font(Font.PLAIN, 11));
        button.setForeground(ink());
        button.setBackground(surface());
        button.setOpaque(true);
        button.setFocusPainted(false);
        button.setBorder(new CompoundBorder(new LineBorder(line(), 1, true),
                new EmptyBorder(6, 7, 6, 7)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(e -> action.run());
        return button;
    }

    private Font font(int weight, int size) { return new Font("Segoe UI", weight, size); }
    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private final class LayerRenderer extends JPanel implements ListCellRenderer<PaintLayer> {
        private final JLabel icon = new JLabel("▧");
        private final JLabel text = new JLabel();
        LayerRenderer() {
            setLayout(new BorderLayout(8, 0));
            setBorder(new EmptyBorder(3, 10, 3, 9));
            icon.setFont(font(Font.BOLD, 22));
            icon.setPreferredSize(new Dimension(34, 36));
            add(icon, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
        }
        @Override public Component getListCellRendererComponent(JList<? extends PaintLayer> list,
                                                                   PaintLayer layer, int index,
                                                                   boolean selected, boolean focused) {
            setBackground(selected ? (darkMode ? new Color(43, 77, 132) : new Color(228, 237, 255)) : surface());
            icon.setForeground(layer.isVisible() ? BLUE : muted());
            text.setForeground(ink());
            text.setFont(font(Font.PLAIN, 12));
            String name = escapeHtml(layer.getName());
            text.setText("<html><b>" + name + "</b><br><span style='font-size:9px'>"
                    + (layer.isVisible() ? "Visible" : "Masqué") + "</span></html>");
            return this;
        }
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final class AppLogo implements Icon {
        @Override public int getIconWidth() { return 45; }
        @Override public int getIconHeight() { return 45; }
        @Override public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.translate(x, y);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(BLUE);
            g.fillRoundRect(0, 0, 45, 45, 14, 14);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(13, 31, 32, 12);
            g.fillOval(11, 29, 9, 8);
            g.dispose();
        }
    }

    /** Small hand-drawn vector icons: no icon-font or image assets needed. */
    private static final class ToolIcon implements Icon {
        private final Tool tool;
        private ToolIcon(Tool tool) { this.tool = tool; }
        @Override public int getIconWidth() { return 23; }
        @Override public int getIconHeight() { return 22; }
        @Override public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.translate(x + 2, y + 2);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(c.getForeground());
            g.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (tool) {
                case PENCIL -> { g.drawLine(2, 17, 16, 3); g.drawLine(1, 18, 5, 17); }
                case ERASER -> { g.drawRoundRect(2, 6, 15, 10, 3, 3); g.drawLine(10, 6, 10, 16); }
                case SPRAY -> { for (int n = 0; n < 12; n++) g.fillOval((n * 7) % 18, (n * 11) % 17, 2, 2); }
                case FILL -> { g.drawRect(4, 4, 12, 11); g.drawLine(4, 15, 16, 15); g.fillOval(13, 13, 4, 4); }
                case LINE -> g.drawLine(2, 17, 17, 2);
                case RECTANGLE -> g.drawRect(2, 3, 16, 13);
                case OVAL -> g.drawOval(2, 3, 16, 13);
                case FILLED_RECT -> g.fillRect(2, 3, 16, 13);
                case FILLED_OVAL -> g.fillOval(2, 3, 16, 13);
                case TRIANGLE -> { Path2D p = new Path2D.Double(); p.moveTo(10, 2); p.lineTo(18, 17); p.lineTo(2, 17); p.closePath(); g.draw(p); }
                case ARROW -> { g.drawLine(2, 15, 17, 3); g.drawLine(10, 3, 17, 3); g.drawLine(17, 3, 16, 10); }
                case TEXT -> { g.setFont(new Font("Arial", Font.BOLD, 19)); g.drawString("T", 4, 17); }
                case EYEDROPPER -> { g.drawLine(3, 17, 17, 3); g.drawOval(10, 1, 7, 7); }
                case SELECT -> g.drawRect(2, 2, 15, 15);
            }
            g.dispose();
        }
    }
}
