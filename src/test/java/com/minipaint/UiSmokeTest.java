package com.minipaint;

import com.minipaint.model.PaintDocument;
import com.minipaint.ui.PaintCanvas;
import com.minipaint.ui.PaintFrame;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;

/** Manual display preview/smoke test in virtual display (xvfb). */
public final class UiSmokeTest {
    public static void main(String[] args) throws Exception {
        final PaintFrame[] window = new PaintFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            PaintFrame frame = new PaintFrame();
            frame.setSize(1480, 880);
            frame.setLocation(40, 40);
            try {
                Field f = PaintFrame.class.getDeclaredField("canvas");
                f.setAccessible(true);
                PaintCanvas canvas = (PaintCanvas) f.get(frame);
                PaintDocument doc = canvas.getDocument();
                Graphics2D g = doc.getActiveLayer().getImage().createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(242, 246, 255));
                g.fillRoundRect(92, 85, 730, 460, 35, 35);
                g.setColor(new Color(54, 112, 235));
                g.setStroke(new BasicStroke(11, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawOval(168, 180, 195, 195);
                g.setColor(new Color(20, 184, 166));
                g.fillRoundRect(480, 190, 245, 185, 28, 28);
                g.setColor(new Color(245, 158, 11));
                g.fillOval(385, 315, 100, 100);
                g.setColor(new Color(27, 43, 72));
                g.setFont(new Font("SansSerif", Font.BOLD, 42));
                g.drawString("MiniPaint Pro", 245, 145);
                g.setFont(new Font("SansSerif", Font.PLAIN, 18));
                g.drawString("Java Swing • Graphics2D", 305, 460);
                g.dispose();
                canvas.repaint();
            } catch (Exception ex) { throw new RuntimeException(ex); }
            frame.setVisible(true);
            window[0] = frame;
        });
        Thread.sleep(1400);
        Robot robot = new Robot();
        BufferedImage shot = robot.createScreenCapture(new Rectangle(0, 0, 1600, 960));
        ImageIO.write(shot, "png", new java.io.File(args[0]));
        SwingUtilities.invokeAndWait(() -> window[0].dispose());
        System.out.println("UI_SNAPSHOT_SAVED");
        System.exit(0);
    }
}
