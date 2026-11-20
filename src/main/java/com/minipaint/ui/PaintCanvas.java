package com.minipaint.ui;

import com.minipaint.model.PaintDocument;
import com.minipaint.model.PaintLayer;
import com.minipaint.model.Tool;
import com.minipaint.edit.ImageEditor;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;
import java.util.function.Consumer;

/** Interactive raster canvas, drawing previews, zoom, flood fill, selection and undo history. */
public final class PaintCanvas extends JComponent {
    private PaintDocument document = new PaintDocument(1200, 760);
    private final Deque<PaintDocument> undo = new ArrayDeque<>();
    private final Deque<PaintDocument> redo = new ArrayDeque<>();
    private static final int HISTORY_LIMIT = 18;
    private static final Random RANDOM = new Random();

    private Tool tool = Tool.PENCIL;
    private Color color = new Color(40, 92, 210);
    private int brushSize = 5;
    private int opacity = 100;
    private double zoom = 0.75;
    private boolean darkMode;
    private boolean dragging;
    private Point start = new Point();
    private Point current = new Point();
    private Point last = new Point();
    private Rectangle selection;
    private boolean movingSelection;
    private Rectangle movingOriginal;
    private BufferedImage localClipboard;

    private Runnable onDocumentChanged = () -> {};
    private Runnable onLayerChanged = () -> {};
    private Consumer<Color> onColorPicked = c -> {};
    private Consumer<Point> onMousePosition = p -> {};
    private Consumer<String> onNotification = s -> {};

    public PaintCanvas() {
        setOpaque(true);
        setBackground(new Color(226, 231, 239));
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        setFocusable(true);
        updateCanvasSize();

        MouseAdapter handler = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { handlePress(e); }
            @Override public void mouseDragged(MouseEvent e) { handleDrag(e); }
            @Override public void mouseReleased(MouseEvent e) { handleRelease(e); }
            @Override public void mouseMoved(MouseEvent e) { onMousePosition.accept(toDoc(e.getPoint())); }
            @Override public void mouseExited(MouseEvent e) { onMousePosition.accept(null); }
            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                if (e.isControlDown()) {
                    setZoom(zoom * (e.getWheelRotation() < 0 ? 1.2 : 1.0 / 1.2));
                    e.consume();
                }
            }
        };
        addMouseListener(handler);
        addMouseMotionListener(handler);
        addMouseWheelListener(handler);
    }

    public PaintDocument getDocument() { return document; }
    public Tool getTool() { return tool; }
    public Color getPaintColor() { return color; }
    public int getBrushSize() { return brushSize; }
    public int getOpacity() { return opacity; }
    public double getZoom() { return zoom; }
    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }
    public void setOnDocumentChanged(Runnable callback) { onDocumentChanged = callback; }
    public void setOnLayerChanged(Runnable callback) { onLayerChanged = callback; }
    public void setOnColorPicked(Consumer<Color> callback) { onColorPicked = callback; }
    public void setOnMousePosition(Consumer<Point> callback) { onMousePosition = callback; }
    public void setOnNotification(Consumer<String> callback) { onNotification = callback; }

    public void setTool(Tool tool) {
        this.tool = tool;
        dragging = false;
        setCursor(Cursor.getPredefinedCursor(tool == Tool.TEXT ? Cursor.TEXT_CURSOR : Cursor.CROSSHAIR_CURSOR));
        repaint();
    }

    public void setPaintColor(Color color) { if (color != null) this.color = color; }
    public void setBrushSize(int brushSize) { this.brushSize = Math.max(1, Math.min(60, brushSize)); }
    public void setOpacity(int opacity) { this.opacity = Math.max(1, Math.min(100, opacity)); }
    public void setDarkMode(boolean darkMode) {
        this.darkMode = darkMode;
        setBackground(darkMode ? new Color(27, 34, 49) : new Color(224, 230, 238));
        repaint();
    }

    public void setZoom(double value) {
        double newZoom = Math.max(0.25, Math.min(4, Math.round(value * 100.0) / 100.0));
        if (Math.abs(newZoom - zoom) < 0.001) return;
        zoom = newZoom;
        updateCanvasSize();
        onNotification.accept("Zoom : " + Math.round(zoom * 100) + " %");
    }

    private void updateCanvasSize() {
        setPreferredSize(new Dimension((int) Math.ceil(document.getWidth() * zoom),
                (int) Math.ceil(document.getHeight() * zoom)));
        revalidate();
        repaint();
    }

    public void setDocument(PaintDocument newDocument) {
        document = newDocument;
        undo.clear();
        redo.clear();
        dragging = false;
        selection = null;
        updateCanvasSize();
        onLayerChanged.run();
        onDocumentChanged.run();
    }

    private Point toDoc(Point mouse) {
        return new Point((int) (mouse.x / zoom), (int) (mouse.y / zoom));
    }

    private Point clamp(Point point) {
        return new Point(Math.max(0, Math.min(document.getWidth() - 1, point.x)),
                Math.max(0, Math.min(document.getHeight() - 1, point.y)));
    }

    private boolean inBounds(Point point) {
        return point.x >= 0 && point.y >= 0 && point.x < document.getWidth() && point.y < document.getHeight();
    }

    private void pushUndo() {
        if (undo.size() == HISTORY_LIMIT) undo.removeFirst();
        undo.addLast(document.copy());
        redo.clear();
    }

    private void notifyEdited() {
        onDocumentChanged.run();
        repaint();
    }

    public void undo() {
        if (undo.isEmpty()) return;
        redo.addLast(document.copy());
        document = undo.removeLast();
        selection = null;
        updateCanvasSize();
        onLayerChanged.run();
        notifyEdited();
    }

    public void redo() {
        if (redo.isEmpty()) return;
        undo.addLast(document.copy());
        document = redo.removeLast();
        selection = null;
        updateCanvasSize();
        onLayerChanged.run();
        notifyEdited();
    }

    private Graphics2D brushGraphics() {
        Graphics2D g = document.getActiveLayer().getImage().createGraphics();
        configureBrush(g, tool == Tool.ERASER);
        return g;
    }

    private void configureBrush(Graphics2D g, boolean erase) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setStroke(new BasicStroke(brushSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (erase) {
            g.setComposite(AlphaComposite.Clear);
        } else {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity / 100f));
            g.setColor(color);
        }
    }

    private void handlePress(MouseEvent e) {
        if (!SwingUtilities.isLeftMouseButton(e)) return;
        requestFocusInWindow();
        Point point = toDoc(e.getPoint());
        onMousePosition.accept(point);
        if (!inBounds(point)) return;
        point = clamp(point);

        if (tool == Tool.EYEDROPPER) {
            Color sampled = new Color(document.render(true).getRGB(point.x, point.y));
            setPaintColor(sampled);
            onColorPicked.accept(sampled);
            onNotification.accept("Couleur sélectionnée : " + toHex(sampled));
            return;
        }
        if (tool == Tool.FILL) {
            fillAt(point);
            return;
        }
        if (tool == Tool.TEXT) {
            addTextAt(point);
            return;
        }
        start = point;
        current = point;
        last = point;
        dragging = true;

        if (tool == Tool.SELECT) {
            if (selection != null && selection.contains(point)) {
                movingSelection = true;
                movingOriginal = new Rectangle(selection);
            } else {
                selection = null;
                movingSelection = false;
            }
            repaint();
            return;
        }
        if (tool == Tool.PENCIL || tool == Tool.ERASER || tool == Tool.SPRAY) {
            pushUndo();
            if (tool == Tool.SPRAY) sprayAt(point);
            else drawSegment(point, point);
            notifyEdited();
        }
    }

    private void handleDrag(MouseEvent e) {
        Point point = clamp(toDoc(e.getPoint()));
        onMousePosition.accept(point);
        if (!dragging) return;
        current = point;
        if (tool == Tool.PENCIL || tool == Tool.ERASER) {
            drawSegment(last, point);
            last = point;
            notifyEdited();
        } else if (tool == Tool.SPRAY) {
            sprayAt(point);
            notifyEdited();
        } else if (tool == Tool.SELECT) {
            if (movingSelection && movingOriginal != null) {
                int dx = point.x - start.x;
                int dy = point.y - start.y;
                selection = new Rectangle(movingOriginal);
                selection.translate(dx, dy);
            } else {
                selection = selectedRectangle(start, point);
            }
            repaint();
        } else {
            repaint();
        }
    }

    private void handleRelease(MouseEvent e) {
        if (!dragging) return;
        current = clamp(toDoc(e.getPoint()));
        dragging = false;
        if (tool == Tool.SELECT) {
            if (movingSelection && movingOriginal != null) {
                int dx = current.x - start.x;
                int dy = current.y - start.y;
                if (dx != 0 || dy != 0) moveSelection(movingOriginal, dx, dy);
                movingSelection = false;
                movingOriginal = null;
            } else {
                selection = selectedRectangle(start, current);
                if (selection.width < 2 || selection.height < 2) selection = null;
            }
        } else if (isShape(tool)) {
            if (!start.equals(current)) {
                pushUndo();
                Graphics2D g = brushGraphics();
                drawShape(g, start, current, tool);
                g.dispose();
                notifyEdited();
            }
        }
        repaint();
    }

    private void drawSegment(Point a, Point b) {
        Graphics2D g = brushGraphics();
        g.drawLine(a.x, a.y, b.x, b.y);
        g.dispose();
    }

    private void sprayAt(Point point) {
        Graphics2D g = brushGraphics();
        int radius = Math.max(5, brushSize * 2);
        for (int i = 0; i < 26; i++) {
            double theta = RANDOM.nextDouble() * Math.PI * 2;
            double r = Math.sqrt(RANDOM.nextDouble()) * radius;
            int x = point.x + (int) Math.round(Math.cos(theta) * r);
            int y = point.y + (int) Math.round(Math.sin(theta) * r);
            g.fillOval(x, y, Math.max(1, brushSize / 3), Math.max(1, brushSize / 3));
        }
        g.dispose();
    }

    private static boolean isShape(Tool tool) {
        return tool == Tool.LINE || tool == Tool.RECTANGLE || tool == Tool.OVAL
                || tool == Tool.FILLED_RECT || tool == Tool.FILLED_OVAL
                || tool == Tool.TRIANGLE || tool == Tool.ARROW;
    }

    private static Rectangle selectedRectangle(Point a, Point b) {
        return new Rectangle(Math.min(a.x, b.x), Math.min(a.y, b.y),
                Math.abs(a.x - b.x), Math.abs(a.y - b.y));
    }

    private void drawShape(Graphics2D g, Point a, Point b, Tool chosen) {
        Rectangle r = selectedRectangle(a, b);
        switch (chosen) {
            case LINE -> g.drawLine(a.x, a.y, b.x, b.y);
            case RECTANGLE -> g.drawRect(r.x, r.y, r.width, r.height);
            case OVAL -> g.drawOval(r.x, r.y, r.width, r.height);
            case FILLED_RECT -> g.fillRect(r.x, r.y, r.width, r.height);
            case FILLED_OVAL -> g.fillOval(r.x, r.y, r.width, r.height);
            case TRIANGLE -> {
                Path2D p = new Path2D.Double();
                p.moveTo(r.x + r.width / 2.0, r.y);
                p.lineTo(r.x, r.y + r.height);
                p.lineTo(r.x + r.width, r.y + r.height);
                p.closePath();
                g.draw(p);
            }
            case ARROW -> {
                g.drawLine(a.x, a.y, b.x, b.y);
                double angle = Math.atan2(b.y - a.y, b.x - a.x);
                int length = Math.max(12, brushSize * 3);
                for (double offset : new double[]{Math.PI / 5, -Math.PI / 5}) {
                    int x = b.x - (int) (length * Math.cos(angle + offset));
                    int y = b.y - (int) (length * Math.sin(angle + offset));
                    g.drawLine(b.x, b.y, x, y);
                }
            }
            default -> { }
        }
    }

    private void fillAt(Point p) {
        BufferedImage visible = document.render(true);
        BufferedImage layer = document.getActiveLayer().getImage();
        int target = visible.getRGB(p.x, p.y);
        int replacement = ((Math.round(opacity * 255 / 100f) & 0xff) << 24)
                | (color.getRGB() & 0x00ffffff);
        if (target == replacement && opacity == 100) return;

        // Examine the visible composite, then write to the current layer.
        // A queue of pixel indexes and a visited mask avoid recursion/stack overflow.
        int w = document.getWidth(), h = document.getHeight();
        int count = w * h;
        boolean[] visited = new boolean[count];
        int[] queue = new int[count];
        int begin = 0, end = 0;
        int startIndex = p.y * w + p.x;
        visited[startIndex] = true;
        queue[end++] = startIndex;
        int changed = 0;
        pushUndo();
        while (begin < end) {
            int index = queue[begin++];
            int x = index % w, y = index / w;
            if (visible.getRGB(x, y) != target) continue;
            layer.setRGB(x, y, replacement);
            changed++;
            if (x > 0) end = enqueue(queue, visited, end, index - 1);
            if (x + 1 < w) end = enqueue(queue, visited, end, index + 1);
            if (y > 0) end = enqueue(queue, visited, end, index - w);
            if (y + 1 < h) end = enqueue(queue, visited, end, index + w);
        }
        if (changed == 0) undo.removeLast();
        else notifyEdited();
    }

    private static int enqueue(int[] queue, boolean[] visited, int end, int index) {
        if (!visited[index]) {
            visited[index] = true;
            queue[end++] = index;
        }
        return end;
    }

    private void addTextAt(Point p) {
        JTextArea field = new JTextArea(4, 25);
        field.setLineWrap(true);
        JComboBox<String> fontBox = new JComboBox<>(GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames());
        fontBox.setSelectedItem("SansSerif");
        JSpinner sizeSpinner = new JSpinner(new SpinnerNumberModel(32, 8, 240, 1));
        JCheckBox bold = new JCheckBox("Gras");
        JCheckBox italic = new JCheckBox("Italique");
        JCheckBox shadow = new JCheckBox("Ombre");
        JComboBox<String> align = new JComboBox<>(new String[]{"Gauche", "Centre", "Droite"});
        JPanel controls = new JPanel(new GridLayout(0, 2, 8, 7));
        controls.add(new JLabel("Police")); controls.add(fontBox);
        controls.add(new JLabel("Taille (px)")); controls.add(sizeSpinner);
        controls.add(bold); controls.add(italic);
        controls.add(shadow); controls.add(align);
        JPanel form = new JPanel(new BorderLayout(5,10));
        form.add(new JLabel("Texte (plusieurs lignes possibles)"), BorderLayout.NORTH);
        form.add(new JScrollPane(field), BorderLayout.CENTER);
        form.add(controls, BorderLayout.SOUTH);
        int choice = JOptionPane.showConfirmDialog(this, form, "Texte avancé",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION || field.getText().isBlank()) return;
        pushUndo();
        Graphics2D g = brushGraphics();
        int style = (bold.isSelected() ? Font.BOLD : 0) | (italic.isSelected() ? Font.ITALIC : 0);
        g.setFont(new Font((String) fontBox.getSelectedItem(), style, (Integer) sizeSpinner.getValue()));
        FontMetrics metrics = g.getFontMetrics();
        int baseline = p.y;
        int lineHeight = metrics.getHeight();
        for (String textLine : field.getText().split("\\R", -1)) {
            int lineWidth = metrics.stringWidth(textLine);
            int drawX = p.x - (align.getSelectedIndex() == 1 ? lineWidth/2
                    : align.getSelectedIndex() == 2 ? lineWidth : 0);
            if (shadow.isSelected()) {
                g.setColor(new Color(26,29,43));
                g.drawString(textLine, drawX + 2, baseline + 2);
                g.setColor(color);
            }
            g.drawString(textLine, drawX, baseline);
            baseline += lineHeight;
        }
        g.dispose();
        notifyEdited();
    }

    /** Crop all layers to current rectangular selection. */
    public boolean cropToSelection() {
        Rectangle r = selectionInsideCanvas();
        if (r == null || r.isEmpty()) { onNotification.accept("Sélectionnez une zone avant le recadrage."); return false; }
        if (r.width == document.getWidth() && r.height == document.getHeight()) return false;
        pushUndo();
        ImageEditor.crop(document, r);
        selection = null;
        updateCanvasSize();
        notifyEdited();
        return true;
    }

    public void rotate(int degrees) {
        pushUndo();
        ImageEditor.rotate(document, degrees);
        selection = null;
        updateCanvasSize();
        notifyEdited();
    }

    public void flip(boolean horizontal) {
        pushUndo();
        ImageEditor.flip(document, horizontal);
        selection = null;
        notifyEdited();
    }

    /** Unlike resizeCanvas(), this resamples every layer's pixels. */
    public void scaleImage(int newWidth, int newHeight) {
        if (newWidth == document.getWidth() && newHeight == document.getHeight()) return;
        PaintDocument.validateSize(newWidth, newHeight);
        if ((long)newWidth * newHeight * document.getLayers().size() > 48_000_000L)
            throw new IllegalArgumentException("Image redimensionnée trop grande pour les calques.");
        pushUndo();
        ImageEditor.scale(document, newWidth, newHeight);
        selection = null;
        updateCanvasSize();
        notifyEdited();
    }

    public void applyKernel(boolean sharpen, int strength) {
        if (strength < 1 || strength > 5) throw new IllegalArgumentException("Intensité invalide.");
        pushUndo();
        PaintLayer active = document.getActiveLayer();
        active.setImage(ImageEditor.kernel(active.getImage(), sharpen, strength));
        notifyEdited();
    }

    public void applyBrightnessContrast(int brightness, int contrast) {
        if (brightness == 0 && contrast == 0) return;
        pushUndo();
        PaintLayer active = document.getActiveLayer();
        active.setImage(ImageEditor.brightnessContrast(active.getImage(), brightness, contrast));
        notifyEdited();
    }

    public void setActiveLayerOpacity(int value) {
        if (document.getActiveLayer().getOpacity() == value) return;
        pushUndo();
        document.getActiveLayer().setOpacity(value);
        onLayerChanged.run();
        notifyEdited();
    }

    public void duplicateLayer() {
        if (document.getLayers().size() >= 32 ||
                (long)document.getWidth()*document.getHeight()*(document.getLayers().size()+1) > 48_000_000L) {
            onNotification.accept("Nombre maximal de calques atteint."); return;
        }
        pushUndo();
        document.duplicateActiveLayer();
        onLayerChanged.run();
        notifyEdited();
    }

    public void mergeLayerDown() {
        if (document.getActiveIndex() == 0) {
            onNotification.accept("Ce calque n'a pas de calque inférieur."); return;
        }
        PaintLayer top = document.getActiveLayer();
        PaintLayer lower = document.getLayers().get(document.getActiveIndex()-1);
        if (!top.isVisible() || !lower.isVisible()) {
            onNotification.accept("Affichez les deux calques pour les fusionner."); return;
        }
        pushUndo();
        document.mergeDown();
        onLayerChanged.run();
        notifyEdited();
    }

    public void clearActiveLayer() {
        pushUndo();
        BufferedImage image = document.getActiveLayer().getImage();
        Graphics2D g = image.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.dispose();
        selection = null;
        notifyEdited();
    }

    public void resizeCanvas(int width, int height) {
        if (width == document.getWidth() && height == document.getHeight()) return;
        PaintDocument.validateSize(width, height);
        pushUndo();
        document.resize(width, height);
        selection = null;
        updateCanvasSize();
        notifyEdited();
    }

    public void addLayer() {
        if (document.getLayers().size() >= 32 ||
                (long) document.getWidth() * document.getHeight() * (document.getLayers().size() + 1) > 48_000_000L) {
            onNotification.accept("Limite de calques atteinte pour cette taille d'image.");
            return;
        }
        pushUndo();
        document.addLayer();
        onLayerChanged.run();
        notifyEdited();
    }

    public void removeLayer() {
        if (document.getLayers().size() <= 1) {
            onNotification.accept("Il faut conserver au moins un calque.");
            return;
        }
        pushUndo();
        document.removeActiveLayer();
        onLayerChanged.run();
        notifyEdited();
    }

    public void moveLayer(int direction) {
        int index = document.getActiveIndex();
        if (index + direction < 0 || index + direction >= document.getLayers().size()) return;
        pushUndo();
        document.moveActiveLayer(direction);
        onLayerChanged.run();
        notifyEdited();
    }

    public void setActiveLayerIndex(int index) {
        document.setActiveIndex(index);
        onLayerChanged.run();
    }

    public void toggleLayerVisibility() {
        pushUndo();
        PaintLayer layer = document.getActiveLayer();
        layer.setVisible(!layer.isVisible());
        onLayerChanged.run();
        notifyEdited();
    }

    public void renameLayer(String name) {
        if (name == null || name.isBlank()) return;
        pushUndo();
        document.getActiveLayer().setName(name.trim());
        onLayerChanged.run();
        notifyEdited();
    }

    public void selectAll() {
        selection = new Rectangle(0, 0, document.getWidth(), document.getHeight());
        setTool(Tool.SELECT);
        repaint();
    }

    public void deselect() {
        selection = null;
        repaint();
    }

    private Rectangle selectionInsideCanvas() {
        if (selection == null) return null;
        return selection.intersection(new Rectangle(0, 0, document.getWidth(), document.getHeight()));
    }

    public void copySelection() {
        Rectangle r = selectionInsideCanvas();
        if (r == null || r.isEmpty()) {
            onNotification.accept("Sélectionnez d'abord une zone.");
            return;
        }
        localClipboard = new BufferedImage(r.width, r.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = localClipboard.createGraphics();
        g.drawImage(document.render(true), 0, 0, r.width, r.height,
                r.x, r.y, r.x + r.width, r.y + r.height, null);
        g.dispose();
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageTransferable(localClipboard), null);
        } catch (IllegalStateException | HeadlessException | SecurityException ex) {
            onNotification.accept("Zone copiée dans le presse-papiers interne.");
        }
        onNotification.accept("Zone copiée.");
    }

    public void cutSelection() {
        Rectangle r = selectionInsideCanvas();
        if (r == null || r.isEmpty()) return;
        copySelection();
        deleteSelection();
    }

    public void deleteSelection() {
        Rectangle r = selectionInsideCanvas();
        if (r == null || r.isEmpty()) return;
        pushUndo();
        Graphics2D g = document.getActiveLayer().getImage().createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(r.x, r.y, r.width, r.height);
        g.dispose();
        selection = null;
        notifyEdited();
    }

    public void pasteSelection() {
        BufferedImage pasted = null;
        try {
            Transferable clipboard = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (clipboard != null && clipboard.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                Image image = (Image) clipboard.getTransferData(DataFlavor.imageFlavor);
                if (image != null) {
                    int w = image.getWidth(null), h = image.getHeight(null);
                    if (w > 0 && h > 0 && (long) w * h <= 12_000_000) {
                        pasted = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g = pasted.createGraphics();
                        g.drawImage(image, 0, 0, null);
                        g.dispose();
                    }
                }
            }
        } catch (Exception ignored) { }
        if (pasted == null) pasted = localClipboard;
        if (pasted == null) {
            onNotification.accept("Aucune image à coller.");
            return;
        }
        int x = Math.max(0, (document.getWidth() - pasted.getWidth()) / 2);
        int y = Math.max(0, (document.getHeight() - pasted.getHeight()) / 2);
        pushUndo();
        Graphics2D g = document.getActiveLayer().getImage().createGraphics();
        g.drawImage(pasted, x, y, null);
        g.dispose();
        selection = new Rectangle(x, y, pasted.getWidth(), pasted.getHeight());
        setTool(Tool.SELECT);
        notifyEdited();
    }

    private void moveSelection(Rectangle from, int dx, int dy) {
        Rectangle clipped = from.intersection(new Rectangle(0, 0, document.getWidth(), document.getHeight()));
        if (clipped.isEmpty()) return;
        BufferedImage layerImage = document.getActiveLayer().getImage();
        BufferedImage piece = new BufferedImage(clipped.width, clipped.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = piece.createGraphics();
        g.drawImage(layerImage, 0, 0, clipped.width, clipped.height,
                clipped.x, clipped.y, clipped.x + clipped.width, clipped.y + clipped.height, null);
        g.dispose();
        pushUndo();
        Graphics2D dest = layerImage.createGraphics();
        dest.setComposite(AlphaComposite.Clear);
        dest.fillRect(clipped.x, clipped.y, clipped.width, clipped.height);
        dest.setComposite(AlphaComposite.SrcOver);
        dest.drawImage(piece, clipped.x + dx, clipped.y + dy, null);
        dest.dispose();
        selection = new Rectangle(clipped);
        selection.translate(dx, dy);
        notifyEdited();
    }

    private static String toHex(Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    @Override protected void paintComponent(Graphics base) {
        super.paintComponent(base);
        Graphics2D g = (Graphics2D) base.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.scale(zoom, zoom);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, document.getWidth(), document.getHeight());
        for (PaintLayer layer : document.getLayers()) {
            if (layer.isVisible() && layer.getOpacity() > 0) {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, layer.getOpacity()/100f));
                g.drawImage(layer.getImage(), 0, 0, null);
            }
        }
        g.setComposite(AlphaComposite.SrcOver);
        if (dragging && isShape(tool)) {
            Graphics2D preview = (Graphics2D) g.create();
            configureBrush(preview, false);
            preview.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .7f));
            drawShape(preview, start, current, tool);
            preview.dispose();
        }
        if (selection != null && tool == Tool.SELECT) {
            g.setColor(new Color(39, 105, 240, 35));
            g.fill(selection);
            g.setStroke(new BasicStroke((float) (1.5 / zoom), BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_MITER, 10f, new float[]{(float) (6 / zoom), (float) (4 / zoom)}, 0f));
            g.setColor(new Color(36, 103, 228));
            g.draw(selection);
        }
        g.dispose();
    }

    private static final class ImageTransferable implements Transferable {
        private final Image image;
        private ImageTransferable(Image image) { this.image = image; }
        @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.imageFlavor}; }
        @Override public boolean isDataFlavorSupported(DataFlavor flavor) { return DataFlavor.imageFlavor.equals(flavor); }
        @Override public Object getTransferData(DataFlavor flavor) throws java.awt.datatransfer.UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) throw new java.awt.datatransfer.UnsupportedFlavorException(flavor);
            return image;
        }
    }
}
