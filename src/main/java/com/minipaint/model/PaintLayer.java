package com.minipaint.model;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** A transparent bitmap layer, editable independently from other layers. */
public final class PaintLayer {
    private String name;
    private boolean visible = true;
    private int opacity = 100;
    private BufferedImage image;

    public PaintLayer(String name, int width, int height) {
        this.name = name;
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    private PaintLayer(String name, boolean visible, int opacity, BufferedImage image) {
        this.name = name;
        this.visible = visible;
        this.opacity = opacity;
        this.image = image;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public BufferedImage getImage() { return image; }
    public int getOpacity() { return opacity; }
    public void setOpacity(int value) { opacity = Math.max(0, Math.min(100, value)); }
    public void setImage(BufferedImage replacement) {
        if (replacement == null || replacement.getType() != BufferedImage.TYPE_INT_ARGB)
            throw new IllegalArgumentException("Image de calque non ARGB.");
        image = replacement;
    }

    public PaintLayer copy() {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return new PaintLayer(name, visible, opacity, copy);
    }

    public void resize(int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        image = target;
    }
}
