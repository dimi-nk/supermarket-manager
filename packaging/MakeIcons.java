import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Draws the app icon (a green basket) and writes icon.png (Linux), icon.ico (Windows) and an .iconset folder
 * (macOS; turn it into icon.icns with `iconutil -c icns icon.iconset`). Run: java packaging/MakeIcons.java packaging/icons
 */
public class MakeIcons {
    static BufferedImage draw(int size) {
        var img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double s = size / 32.0;
        g.setColor(new Color(0xF6F5F1));
        g.fill(new RoundRectangle2D.Double(0, 0, size, size, 7 * s, 7 * s));
        var green = new Color(0x2F7D4F);
        g.setColor(green);
        g.setStroke(new BasicStroke((float) (3 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Double(9 * s, 5 * s, 14 * s, 16 * s, 0, 180, Arc2D.OPEN));
        g.fill(new RoundRectangle2D.Double(3 * s, 12 * s, 26 * s, 16 * s, 10 * s, 10 * s));
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke((float) (2 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int x : new int[]{11, 16, 21}) g.draw(new Line2D.Double(x * s, 17 * s, x * s, 23 * s));
        g.dispose();
        return img;
    }

    static byte[] png(BufferedImage img) throws IOException {
        var out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args.length > 0 ? args[0] : "icons");
        Files.createDirectories(dir.resolve("icon.iconset"));
        Files.write(dir.resolve("icon.png"), png(draw(512)));
        for (int size : new int[]{16, 32, 128, 256, 512}) {
            Files.write(dir.resolve("icon.iconset/icon_" + size + "x" + size + ".png"), png(draw(size)));
            Files.write(dir.resolve("icon.iconset/icon_" + size + "x" + size + "@2x.png"), png(draw(size * 2)));
        }
        // ICO with embedded PNG images (supported since Windows Vista).
        int[] sizes = {16, 32, 48, 256};
        byte[][] images = new byte[sizes.length][];
        for (int i = 0; i < sizes.length; i++) images[i] = png(draw(sizes[i]));
        var ico = new ByteArrayOutputStream();
        var le = new DataOutputStream(ico);
        le.writeShort(0);
        le.writeShort(Short.reverseBytes((short) 1));
        le.writeShort(Short.reverseBytes((short) sizes.length));
        int offset = 6 + 16 * sizes.length;
        for (int i = 0; i < sizes.length; i++) {
            le.writeByte(sizes[i] >= 256 ? 0 : sizes[i]);
            le.writeByte(sizes[i] >= 256 ? 0 : sizes[i]);
            le.writeByte(0);
            le.writeByte(0);
            le.writeShort(Short.reverseBytes((short) 1));
            le.writeShort(Short.reverseBytes((short) 32));
            le.writeInt(Integer.reverseBytes(images[i].length));
            le.writeInt(Integer.reverseBytes(offset));
            offset += images[i].length;
        }
        for (byte[] b : images) le.write(b);
        Files.write(dir.resolve("icon.ico"), ico.toByteArray());
    }
}
