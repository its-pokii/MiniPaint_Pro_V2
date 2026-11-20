package com.minipaint;

import com.minipaint.model.PaintDocument;
import com.minipaint.model.Tool;
import com.minipaint.ui.PaintCanvas;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.event.MouseEvent;
import java.io.File;

/** Dependency-free, headless smoke tests. Run with: java -ea -Djava.awt.headless=true ... */
public class MiniPaintSmokeTest {
    private static void event(PaintCanvas canvas, int type, int x, int y) {
        canvas.dispatchEvent(new MouseEvent(canvas, type, System.currentTimeMillis(), 0, x, y,
                type == MouseEvent.MOUSE_PRESSED || type == MouseEvent.MOUSE_RELEASED ? 1 : 0,
                false, MouseEvent.BUTTON1));
    }

    private static void test() throws Exception {
        PaintCanvas canvas = new PaintCanvas();
        canvas.setDocument(new PaintDocument(100, 100));
        canvas.setZoom(1);
        canvas.setSize(100,100);
        canvas.setPaintColor(Color.RED);
        canvas.setBrushSize(7);
        canvas.setTool(Tool.PENCIL);
        event(canvas, MouseEvent.MOUSE_PRESSED, 15, 15);
        event(canvas, MouseEvent.MOUSE_DRAGGED, 35, 35);
        event(canvas, MouseEvent.MOUSE_RELEASED, 35, 35);
        assert canvas.getDocument().render(true).getRGB(25, 25) == Color.RED.getRGB() : "pencil";
        canvas.undo();
        assert canvas.getDocument().render(true).getRGB(25, 25) == Color.WHITE.getRGB() : "undo";
        canvas.redo();
        assert canvas.getDocument().render(true).getRGB(25, 25) == Color.RED.getRGB() : "redo";

        canvas.setTool(Tool.ERASER);
        event(canvas, MouseEvent.MOUSE_PRESSED, 25, 25);
        event(canvas, MouseEvent.MOUSE_RELEASED, 25, 25);
        assert canvas.getDocument().render(true).getRGB(25, 25) == Color.WHITE.getRGB() : "eraser";

        canvas.setPaintColor(Color.BLUE);
        canvas.setTool(Tool.FILLED_RECT);
        event(canvas, MouseEvent.MOUSE_PRESSED, 50, 50);
        event(canvas, MouseEvent.MOUSE_DRAGGED, 80, 80);
        event(canvas, MouseEvent.MOUSE_RELEASED, 80, 80);
        assert canvas.getDocument().render(true).getRGB(65, 65) == Color.BLUE.getRGB() : "shape";

        canvas.addLayer();
        assert canvas.getDocument().getLayers().size() == 2 : "layer add";
        canvas.renameLayer("Texte & détails");
        assert canvas.getDocument().getActiveLayer().getName().equals("Texte & détails") : "layer rename";
        canvas.setTool(Tool.FILL);
        canvas.setPaintColor(Color.GREEN);
        event(canvas, MouseEvent.MOUSE_PRESSED, 90, 90);
        assert canvas.getDocument().render(true).getRGB(92, 92) == Color.GREEN.getRGB() : "fill";
        assert canvas.getDocument().render(true).getRGB(65, 65) == Color.BLUE.getRGB() : "fill bounds";
        canvas.toggleLayerVisibility();
        assert canvas.getDocument().render(true).getRGB(92, 92) == Color.WHITE.getRGB() : "layer visibility";
        canvas.toggleLayerVisibility();
        canvas.selectAll();
        canvas.copySelection();
        canvas.pasteSelection();
        assert canvas.getDocument().render(true).getRGB(92, 92) == Color.GREEN.getRGB() : "paste";
        canvas.resizeCanvas(125, 115);
        assert canvas.getDocument().getWidth() == 125 : "resize";
        File dest = File.createTempFile("minipaint-test-", ".png");
        dest.deleteOnExit();
        ImageIO.write(canvas.getDocument().render(true), "png", dest);
        assert ImageIO.read(dest).getWidth() == 125 : "png save";
        canvas.undo();
        assert canvas.getDocument().getWidth() == 100 : "undo resize";
        System.out.println("TESTS PASSÉS : Pencil, Eraser, Undo/Redo, Shapes, Flood fill, Layers, Visibility, Clipboard, Resize, PNG");
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try { test(); } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
}
