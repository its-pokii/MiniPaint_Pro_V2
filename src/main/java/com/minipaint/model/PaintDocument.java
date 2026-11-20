package com.minipaint.model;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** All mutable document data; layers are stored bottom-to-top. */
public final class PaintDocument {
    private int width;
    private int height;
    private final List<PaintLayer> layers = new ArrayList<>();
    private int activeIndex;

    public PaintDocument(int width, int height) {
        validateSize(width, height);
        this.width = width;
        this.height = height;
        layers.add(new PaintLayer("Calque 1", width, height));
    }

    public static void validateSize(int width, int height) {
        if (width < 1 || height < 1 || width > 5000 || height > 5000 || (long) width * height > 12_000_000) {
            throw new IllegalArgumentException("Dimensions non prises en charge (maximum 12 millions de pixels).");
        }
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public List<PaintLayer> getLayers() { return Collections.unmodifiableList(layers); }
    public int getActiveIndex() { return activeIndex; }
    public PaintLayer getActiveLayer() { return layers.get(activeIndex); }

    public void setActiveIndex(int index) {
        if (index >= 0 && index < layers.size()) activeIndex = index;
    }

    public void addLayer() {
        layers.add(activeIndex + 1, new PaintLayer("Calque " + (layers.size() + 1), width, height));
        activeIndex++;
    }

    public boolean removeActiveLayer() {
        if (layers.size() <= 1) return false;
        layers.remove(activeIndex);
        activeIndex = Math.min(activeIndex, layers.size() - 1);
        return true;
    }

    public boolean moveActiveLayer(int direction) {
        int target = activeIndex + direction;
        if (target < 0 || target >= layers.size()) return false;
        Collections.swap(layers, activeIndex, target);
        activeIndex = target;
        return true;
    }

    /** Restore layers loaded from a native project. Images must match document size. */
    public void replaceLayers(List<PaintLayer> replacement, int selectedIndex) {
        if (replacement == null || replacement.isEmpty() || replacement.size() > 32) {
            throw new IllegalArgumentException("Nombre de calques invalide.");
        }
        for (PaintLayer layer : replacement) {
            if (layer.getImage().getWidth() != width || layer.getImage().getHeight() != height) {
                throw new IllegalArgumentException("Dimensions de calque incohérentes.");
            }
        }
        layers.clear();
        layers.addAll(replacement);
        activeIndex = Math.max(0, Math.min(selectedIndex, layers.size() - 1));
    }

    /** Change all layer raster images atomically after geometry editing. */
    public void replaceImages(List<BufferedImage> images, int newWidth, int newHeight) {
        validateSize(newWidth, newHeight);
        if (images == null || images.size() != layers.size()) throw new IllegalArgumentException("Calques incohérents.");
        for (BufferedImage image : images) {
            if (image == null || image.getWidth() != newWidth || image.getHeight() != newHeight
                    || image.getType() != BufferedImage.TYPE_INT_ARGB)
                throw new IllegalArgumentException("Dimensions des images incohérentes.");
        }
        for (int i = 0; i < images.size(); i++) layers.get(i).setImage(images.get(i));
        width = newWidth;
        height = newHeight;
    }

    public void duplicateActiveLayer() {
        if (layers.size() >= 32) throw new IllegalStateException("Trop de calques.");
        PaintLayer copy = getActiveLayer().copy();
        copy.setName(copy.getName() + " (copie)");
        layers.add(activeIndex + 1, copy);
        activeIndex++;
    }

    /** Merge active layer onto immediately lower layer while preserving composed appearance. */
    public boolean mergeDown() {
        if (activeIndex == 0) return false;
        PaintLayer top = layers.get(activeIndex);
        PaintLayer lower = layers.get(activeIndex - 1);
        // Hidden layers cannot be merged without changing the document's semantics.
        if (!top.isVisible() || !lower.isVisible()) return false;
        BufferedImage base = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = base.createGraphics();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, lower.getOpacity()/100f));
        g.drawImage(lower.getImage(),0,0,null);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, top.getOpacity()/100f));
        g.drawImage(top.getImage(),0,0,null);
        g.dispose();
        lower.setImage(base);
        lower.setOpacity(100);
        layers.remove(activeIndex);
        activeIndex--;
        return true;
    }

    public void resize(int newWidth, int newHeight) {
        validateSize(newWidth, newHeight);
        for (PaintLayer layer : layers) layer.resize(newWidth, newHeight);
        width = newWidth;
        height = newHeight;
    }

    /** @param whiteBackground true to flatten on opaque white (PNG/JPG/BMP export). */
    public BufferedImage render(boolean whiteBackground) {
        BufferedImage result = new BufferedImage(width, height,
                whiteBackground ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        if (whiteBackground) {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
        }
        for (PaintLayer layer : layers) {
            if (layer.isVisible() && layer.getOpacity() > 0) {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, layer.getOpacity() / 100f));
                g.drawImage(layer.getImage(), 0, 0, null);
            }
        }
        g.dispose();
        return result;
    }

    public PaintDocument copy() {
        PaintDocument copy = new PaintDocument(width, height);
        copy.layers.clear();
        for (PaintLayer layer : layers) copy.layers.add(layer.copy());
        copy.activeIndex = activeIndex;
        return copy;
    }
}
