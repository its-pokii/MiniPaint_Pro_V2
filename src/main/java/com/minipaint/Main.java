package com.minipaint;

import com.minipaint.ui.PaintFrame;
import javax.swing.*;
import java.awt.*;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) { }
            UIManager.put("defaultFont", new Font("Segoe UI", Font.PLAIN, 13));
            new PaintFrame().setVisible(true);
        });
    }
}
