package com.minipaint;

import com.minipaint.io.ProjectIO;
import com.minipaint.model.PaintDocument;
import java.awt.*;
import java.io.File;

public final class ProjectRoundtripTest {
    public static void main(String[] args) throws Exception {
        PaintDocument d = new PaintDocument(80, 65);
        d.getActiveLayer().setName("Fond");
        d.addLayer();
        d.getActiveLayer().setName("Calque avec é à ♥");
        d.getActiveLayer().setVisible(false);
        Graphics2D g = d.getActiveLayer().getImage().createGraphics();
        g.setColor(Color.RED);
        g.fillRect(2,3,20,20);
        g.dispose();
        File tmp = File.createTempFile("minipaint-roundtrip-", ".mpaint");
        tmp.deleteOnExit();
        ProjectIO.save(d,tmp);
        PaintDocument loaded = ProjectIO.load(tmp);
        assert loaded.getWidth() == 80;
        assert loaded.getHeight() == 65;
        assert loaded.getLayers().size() == 2;
        assert loaded.getActiveIndex() == 1;
        assert !loaded.getActiveLayer().isVisible();
        assert loaded.getActiveLayer().getName().equals("Calque avec é à ♥");
        assert loaded.getActiveLayer().getImage().getRGB(3,4) == Color.RED.getRGB();
        System.out.println("TEST OK : native .mpaint save/reload with layers, visibility and Unicode");
    }
}
