package com.minipaint.edit;

import com.minipaint.model.PaintDocument;
import com.minipaint.model.PaintLayer;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Image operations preserve transparency and operate on every layer for geometry edits. */
public final class ImageEditor {
    private ImageEditor() { }

    public enum Filter { BLUR, SHARPEN, BRIGHTNESS_CONTRAST }

    public static void crop(PaintDocument document, Rectangle selection) {
        Rectangle area = selection.intersection(new Rectangle(0, 0, document.getWidth(), document.getHeight()));
        if (area.isEmpty()) throw new IllegalArgumentException("Sélection vide.");
        PaintDocument.validateSize(area.width, area.height);
        List<BufferedImage> images = new ArrayList<>();
        for (PaintLayer layer : document.getLayers()) {
            BufferedImage image = new BufferedImage(area.width, area.height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.drawImage(layer.getImage(), 0, 0, area.width, area.height, area.x, area.y,
                    area.x + area.width, area.y + area.height, null);
            g.dispose();
            images.add(image);
        }
        document.replaceImages(images, area.width, area.height);
    }

    public static void rotate(PaintDocument document, int degrees) {
        if (degrees != 90 && degrees != -90 && degrees != 180) throw new IllegalArgumentException("Rotation invalide.");
        int w = document.getWidth(), h = document.getHeight();
        int newW = Math.abs(degrees) == 90 ? h : w;
        int newH = Math.abs(degrees) == 90 ? w : h;
        List<BufferedImage> images = new ArrayList<>();
        for (PaintLayer layer : document.getLayers()) {
            BufferedImage target = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = target.createGraphics();
            if (degrees == 90) { g.translate(h, 0); g.rotate(Math.PI / 2); }
            else if (degrees == -90) { g.translate(0, w); g.rotate(-Math.PI / 2); }
            else { g.translate(w, h); g.rotate(Math.PI); }
            g.drawImage(layer.getImage(), 0, 0, null);
            g.dispose();
            images.add(target);
        }
        document.replaceImages(images, newW, newH);
    }

    public static void flip(PaintDocument document, boolean horizontal) {
        int w = document.getWidth(), h = document.getHeight();
        List<BufferedImage> images = new ArrayList<>();
        for (PaintLayer layer : document.getLayers()) {
            BufferedImage target = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = target.createGraphics();
            if (horizontal) { g.translate(w, 0); g.scale(-1, 1); }
            else { g.translate(0, h); g.scale(1, -1); }
            g.drawImage(layer.getImage(), 0, 0, null);
            g.dispose();
            images.add(target);
        }
        document.replaceImages(images, w, h);
    }

    public static void scale(PaintDocument document, int newW, int newH) {
        PaintDocument.validateSize(newW, newH);
        List<BufferedImage> images = new ArrayList<>();
        for (PaintLayer layer : document.getLayers()) {
            BufferedImage target = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = target.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(layer.getImage(), 0, 0, newW, newH, null);
            g.dispose();
            images.add(target);
        }
        document.replaceImages(images, newW, newH);
    }

    /** Apply a local 3x3 kernel to the active layer, retaining the source alpha channel. */
    public static BufferedImage kernel(BufferedImage source, boolean sharpen, int iterations) {
        int w = source.getWidth(), h = source.getHeight();
        BufferedImage current = source;
        int[] weights = sharpen ? new int[]{0,-1,0,-1,5,-1,0,-1,0}
                : new int[]{1,1,1,1,1,1,1,1,1};
        int divisor = sharpen ? 1 : 9;
        for (int iteration = 0; iteration < iterations; iteration++) {
            BufferedImage output = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int center = current.getRGB(x,y);
                int a = center >>> 24;
                if (a == 0) continue;
                double red=0, green=0, blue=0, weight=0;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int xx = Math.min(w-1, Math.max(0,x+dx));
                    int yy = Math.min(h-1, Math.max(0,y+dy));
                    int p = current.getRGB(xx,yy);
                    int alpha = p >>> 24;
                    int k = weights[(dy+1)*3+(dx+1)];
                    // Premultiplied accumulation prevents transparent pixels from darkening edges.
                    double factor = k * (alpha / 255.0);
                    weight += factor;
                    red += ((p>>>16)&255) * factor;
                    green += ((p>>>8)&255) * factor;
                    blue += (p&255) * factor;
                }
                double norm = sharpen ? 1 : (weight == 0 ? divisor : weight);
                if (sharpen) { // keep the mathematically defined sharpening result
                    norm = weight == 0 ? 1 : weight;
                }
                int r=clip((int)Math.round(red/norm));
                int g=clip((int)Math.round(green/norm));
                int b=clip((int)Math.round(blue/norm));
                output.setRGB(x,y,(a<<24)|(r<<16)|(g<<8)|b);
            }
            current = output;
        }
        return current;
    }

    public static BufferedImage brightnessContrast(BufferedImage source, int brightness, int contrast) {
        int w=source.getWidth(), h=source.getHeight();
        BufferedImage out=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        double factor=(259.0*(contrast+255))/(255.0*(259-contrast));
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
            int p=source.getRGB(x,y);
            int a=p>>>24;
            int r=clip((int)Math.round(factor*(((p>>>16)&255)-128)+128+brightness));
            int g=clip((int)Math.round(factor*(((p>>>8)&255)-128)+128+brightness));
            int b=clip((int)Math.round(factor*((p&255)-128)+128+brightness));
            out.setRGB(x,y,(a<<24)|(r<<16)|(g<<8)|b);
        }
        return out;
    }

    private static int clip(int value) { return Math.max(0, Math.min(255, value)); }
}
