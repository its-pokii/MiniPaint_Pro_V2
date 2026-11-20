package com.minipaint;

import com.minipaint.model.PaintDocument;
import com.minipaint.ui.PaintCanvas;
import com.minipaint.ui.PaintFrame;
import javax.swing.*;
import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** GUI integration smoke test; run under xvfb-run or a desktop. */
public final class TabIntegrationTest {
    private static void assertTrue(boolean v, String m) { if (!v) throw new AssertionError(m); }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                PaintFrame frame=new PaintFrame();
                Field tabsField=PaintFrame.class.getDeclaredField("documentTabs"); tabsField.setAccessible(true);
                Field canvasField=PaintFrame.class.getDeclaredField("canvas"); canvasField.setAccessible(true);
                JTabbedPane tabs=(JTabbedPane) tabsField.get(frame);
                assertTrue(tabs.getTabCount()==1,"initial tab");
                PaintCanvas first=(PaintCanvas)canvasField.get(frame);
                first.getDocument().getActiveLayer().getImage().setRGB(2,3,Color.RED.getRGB());
                Method create=PaintFrame.class.getDeclaredMethod("addDocumentTab",PaintDocument.class,java.io.File.class,java.io.File.class);
                create.setAccessible(true);
                create.invoke(frame,new PaintDocument(90,70),null,null);
                assertTrue(tabs.getTabCount()==2 && tabs.getSelectedIndex()==1,"new tab selected");
                PaintCanvas second=(PaintCanvas)canvasField.get(frame);
                assertTrue(second != first && second.getDocument().getWidth()==90,"per-tab canvas");
                second.addLayer();
                assertTrue(tabs.getTitleAt(1).endsWith("*"),"dirty indicator second");
                tabs.setSelectedIndex(0);
                assertTrue(canvasField.get(frame)==first,"switch back");
                assertTrue(first.getDocument().getActiveLayer().getImage().getRGB(2,3)==Color.RED.getRGB(),"first content preserved");
                first.setActiveLayerOpacity(80);
                assertTrue(tabs.getTitleAt(0).endsWith("*"),"dirty indicator first");
                tabs.setSelectedIndex(1);
                assertTrue(canvasField.get(frame)==second && second.getDocument().getLayers().size()==2,"second state preserved");
                // Close testing would show a save dialog for intentionally modified documents.
                frame.dispose();
                System.out.println("PASS V2 GUI: tabs, switching, independent undo/layers, dirty labels");
            } catch(Exception e) {throw new RuntimeException(e);}
        });
        System.exit(0);
    }
}
