package com.minipaint.io;

import com.minipaint.model.PaintDocument;
import com.minipaint.model.PaintLayer;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Native .mpaint format: ZIP containing layer PNGs and a metadata.properties manifest. */
public final class ProjectIO {
    private ProjectIO() { }

    public static void save(PaintDocument document, File output) throws IOException {
        if (document.getLayers().size() > 32) throw new IOException("Nombre maximal de calques : 32.");
        Properties data = new Properties();
        data.setProperty("format", "MiniPaint-Pro-1");
        data.setProperty("width", Integer.toString(document.getWidth()));
        data.setProperty("height", Integer.toString(document.getHeight()));
        data.setProperty("count", Integer.toString(document.getLayers().size()));
        data.setProperty("active", Integer.toString(document.getActiveIndex()));
        List<PaintLayer> layers = document.getLayers();
        for (int i = 0; i < layers.size(); i++) {
            data.setProperty("layer." + i + ".name", layers.get(i).getName());
            data.setProperty("layer." + i + ".visible", Boolean.toString(layers.get(i).isVisible()));
            data.setProperty("layer." + i + ".opacity", Integer.toString(layers.get(i).getOpacity()));
        }

        Path parent = output.toPath().toAbsolutePath().getParent();
        Path temporary = Files.createTempFile(parent, "minipaint-", ".tmp");
        try {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
                zip.putNextEntry(new ZipEntry("metadata.properties"));
                data.store(zip, "MiniPaint Pro project - keep this archive intact");
                zip.closeEntry();
                for (int i = 0; i < layers.size(); i++) {
                    zip.putNextEntry(new ZipEntry("layers/layer-" + i + ".png"));
                    if (!ImageIO.write(layers.get(i).getImage(), "png", zip)) {
                        throw new IOException("Encodeur PNG indisponible.");
                    }
                    zip.closeEntry();
                }
            }
            try {
                Files.move(temporary, output.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temporary, output.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static PaintDocument load(File file) throws IOException {
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry metadata = zip.getEntry("metadata.properties");
            if (metadata == null || metadata.getSize() > 100_000) throw new IOException("Métadonnées absentes ou trop grandes.");
            Properties properties = new Properties();
            try (InputStream input = zip.getInputStream(metadata)) {
                properties.load(new java.io.BufferedInputStream(new LimitedInputStream(input, 100_000)));
            }
            if (!"MiniPaint-Pro-1".equals(properties.getProperty("format"))) {
                throw new IOException("Format de projet inconnu.");
            }
            int w = parse(properties, "width"), h = parse(properties, "height");
            int count = parse(properties, "count"), active = parse(properties, "active");
            if (count < 1 || count > 32) throw new IOException("Nombre de calques invalide.");
            if ((long) w * h * count > 48_000_000L) throw new IOException("Projet trop volumineux.");
            try { PaintDocument.validateSize(w, h); }
            catch (IllegalArgumentException ex) { throw new IOException(ex.getMessage(), ex); }
            PaintDocument document = new PaintDocument(w, h);
            List<PaintLayer> layers = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                ZipEntry layerFile = zip.getEntry("layers/layer-" + i + ".png");
                if (layerFile == null || layerFile.getSize() > 60_000_000) throw new IOException("Calque manquant ou trop grand.");
                BufferedImage img;
                try (InputStream stream = zip.getInputStream(layerFile)) {
                    img = ImageIO.read(stream);
                }
                if (img == null || img.getWidth() != w || img.getHeight() != h) {
                    throw new IOException("Image de calque invalide.");
                }
                PaintLayer layer = new PaintLayer(properties.getProperty("layer." + i + ".name", "Calque " + (i + 1)), w, h);
                layer.setVisible(Boolean.parseBoolean(properties.getProperty("layer." + i + ".visible", "true")));
                int layerOpacity = parse(properties, "layer." + i + ".opacity", 100);
                if (layerOpacity < 0 || layerOpacity > 100) throw new IOException("Opacité de calque invalide.");
                layer.setOpacity(layerOpacity);
                Graphics2D g = layer.getImage().createGraphics();
                g.drawImage(img, 0, 0, null);
                g.dispose();
                layers.add(layer);
            }
            document.replaceLayers(layers, active);
            return document;
        } catch (IllegalArgumentException ex) {
            throw new IOException("Projet invalide : " + ex.getMessage(), ex);
        }
    }

    private static int parse(Properties p, String key, int fallback) throws IOException {
        if (!p.containsKey(key)) return fallback;
        return parse(p, key);
    }

    private static int parse(Properties p, String key) throws IOException {
        try { return Integer.parseInt(p.getProperty(key)); }
        catch (NumberFormatException | NullPointerException ex) { throw new IOException("Métadonnée invalide : " + key, ex); }
    }

    /** Refuse an oversized metadata stream even for entries with unknown decompressed sizes. */
    private static final class LimitedInputStream extends FilterInputStream {
        private long remaining;
        LimitedInputStream(InputStream input, long max) { super(input); remaining = max; }
        @Override public int read() throws IOException {
            if (remaining == 0) throw new IOException("Métadonnées trop grandes.");
            int r = super.read(); if (r != -1) remaining--; return r;
        }
        @Override public int read(byte[] data, int offset, int length) throws IOException {
            if (remaining == 0) throw new IOException("Métadonnées trop grandes.");
            int r = super.read(data, offset, (int) Math.min(length, remaining));
            if (r > 0) remaining -= r;
            return r;
        }
    }
}
