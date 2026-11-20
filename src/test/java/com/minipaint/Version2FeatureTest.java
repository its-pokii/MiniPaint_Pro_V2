package com.minipaint;

import com.minipaint.edit.ImageEditor;
import com.minipaint.io.ProjectIO;
import com.minipaint.model.PaintDocument;
import com.minipaint.model.PaintLayer;
import com.minipaint.ui.PaintCanvas;
import javax.swing.SwingUtilities;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** Headless functional regression suite for MiniPaint Pro V2. */
public final class Version2FeatureTest {
    private static void check(boolean ok, String text) { if (!ok) throw new AssertionError(text); }
    private static void run() throws Exception {
        PaintDocument doc = new PaintDocument(4,3);
        doc.getActiveLayer().getImage().setRGB(0,0,Color.RED.getRGB());
        doc.getActiveLayer().getImage().setRGB(3,2,Color.BLUE.getRGB());
        ImageEditor.rotate(doc,90);
        check(doc.getWidth()==3 && doc.getHeight()==4,"rotate size");
        check(doc.getActiveLayer().getImage().getRGB(2,0)==Color.RED.getRGB(),"rotate pixel 0");
        check(doc.getActiveLayer().getImage().getRGB(0,3)==Color.BLUE.getRGB(),"rotate pixel 1");
        ImageEditor.rotate(doc,-90);
        check(doc.getWidth()==4 && doc.getHeight()==3 && doc.getActiveLayer().getImage().getRGB(0,0)==Color.RED.getRGB(),"reverse rotate");
        ImageEditor.flip(doc,true);
        check(doc.getActiveLayer().getImage().getRGB(3,0)==Color.RED.getRGB(),"mirror horizontal");
        ImageEditor.flip(doc,false);
        check(doc.getActiveLayer().getImage().getRGB(3,2)==Color.RED.getRGB(),"mirror vertical");
        ImageEditor.crop(doc,new Rectangle(2,1,2,2));
        check(doc.getWidth()==2 && doc.getHeight()==2 && doc.getActiveLayer().getImage().getRGB(1,1)==Color.RED.getRGB(),"crop");
        ImageEditor.scale(doc,8,8);
        check(doc.getWidth()==8 && doc.getActiveLayer().getImage().getWidth()==8,"scale");
        doc.addLayer();
        PaintLayer top=doc.getActiveLayer();
        top.getImage().setRGB(0,0,Color.BLUE.getRGB());
        top.setOpacity(50);
        check(doc.render(true).getRGB(0,0)!=Color.BLUE.getRGB(),"opacity applied on render");
        File f = File.createTempFile("minipaint-v2-", ".mpaint"); f.deleteOnExit();
        ProjectIO.save(doc, f);
        PaintDocument restored=ProjectIO.load(f);
        check(restored.getLayers().size()==2 && restored.getActiveLayer().getOpacity()==50,"opacity persistence");
        check(restored.getWidth()==8 && restored.getHeight()==8,"dimensions persistence");
        restored.duplicateActiveLayer();
        check(restored.getLayers().size()==3 && restored.getActiveLayer().getOpacity()==50,"duplicate layer");
        check(restored.mergeDown() && restored.getLayers().size()==2 && restored.getActiveLayer().getOpacity()==100,"merge layer");
        BufferedImage sample=new BufferedImage(3,3,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<3;y++)for(int x=0;x<3;x++)sample.setRGB(x,y,new Color(80,90,100,255).getRGB());
        BufferedImage blurred=ImageEditor.kernel(sample,false,2);
        check(blurred.getRGB(1,1)==sample.getRGB(1,1),"uniform image blur");
        BufferedImage sharpened=ImageEditor.kernel(sample,true,1);
        check(sharpened.getRGB(1,1)==sample.getRGB(1,1),"uniform image sharpen");
        BufferedImage brighter=ImageEditor.brightnessContrast(sample,30,0);
        check((brighter.getRGB(0,0)&255)==130,"brightness");
        BufferedImage transparent = new BufferedImage(3,3,BufferedImage.TYPE_INT_ARGB);
        transparent.setRGB(1,1,0x80FF0000);
        check((ImageEditor.brightnessContrast(transparent,30,5).getRGB(1,1)>>>24)==128,"transparency filter");
        // Undo checks for all new canvas geometry and filter operations.
        PaintCanvas c = new PaintCanvas();
        c.setDocument(new PaintDocument(30,20));
        c.getDocument().getActiveLayer().getImage().setRGB(0,0,Color.BLUE.getRGB());
        c.rotate(90);
        check(c.getDocument().getWidth()==20 && c.getDocument().getHeight()==30,"canvas rotation");
        c.undo();
        check(c.getDocument().getWidth()==30 && c.getDocument().getHeight()==20,"rotation undo");
        c.scaleImage(15,10); c.undo();
        check(c.getDocument().getWidth()==30,"scale undo");
        c.applyBrightnessContrast(15,10); c.undo();
        check(c.getDocument().getActiveLayer().getImage().getRGB(0,0)==Color.BLUE.getRGB(),"filter undo");
        c.duplicateLayer(); c.setActiveLayerOpacity(35); c.undo();
        check(c.getDocument().getActiveLayer().getOpacity()==100,"opacity undo");
        c.mergeLayerDown(); c.undo();
        check(c.getDocument().getLayers().size()==2,"merge undo");
        System.out.println("PASS V2 : crop, rotate, flip, scale, opacity, native save, merge, duplicate, blur, sharpen, brightness, transparency, undo");
    }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> { try {run();} catch(Exception ex) {throw new RuntimeException(ex);} });
    }
}
