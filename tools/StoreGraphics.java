import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Play 스토어 등록 자산을 만듭니다.
 *
 *   docs/store/icon-512.png      512x512  앱 아이콘
 *   docs/store/feature-1024.png  1024x500 피처 그래픽
 *
 * 앱 아이콘의 도형은 res/drawable/ic_launcher_foreground.xml 을 그대로 옮긴 것입니다.
 * 런처 아이콘과 스토어 아이콘이 달라 보이면 사용자가 다른 앱으로 착각합니다.
 * 어댑티브 아이콘은 108 뷰포트 중 가운데 72 만 보이므로, 그 72 가 512 를 채우도록 키웁니다.
 *
 * 실행:  javac -d out tools/StoreGraphics.java && java -cp out StoreGraphics
 */
public final class StoreGraphics {

    private static final Color BACKGROUND = new Color(0x0B1D2E);
    private static final Color ACCENT = new Color(0x82E0FF);

    public static void main(String[] args) throws Exception {
        File dir = new File("docs/store");
        dir.mkdirs();
        ImageIO.write(icon(512), "png", new File(dir, "icon-512.png"));
        ImageIO.write(feature(1024, 500), "png", new File(dir, "feature-1024.png"));
        System.out.println("docs/store/icon-512.png, docs/store/feature-1024.png");
    }

    // ---------------------------------------------------------------- 아이콘

    private static BufferedImage icon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = prepare(image);

        g.setColor(BACKGROUND);
        g.fillRect(0, 0, size, size);

        // 108 좌표계 중 가운데 72(18..90)가 화면을 채우도록.
        double scale = size / 72.0;
        g.translate(-18 * scale, -18 * scale);
        g.scale(scale, scale);
        drawMark(g, 1.0);

        g.dispose();
        return image;
    }

    // ------------------------------------------------------------ 피처 그래픽

    private static BufferedImage feature(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = prepare(image);

        // 왼쪽 위가 살짝 밝은 대각 그라데이션 — 단색보다 덜 밋밋합니다.
        g.setPaint(new GradientPaint(
                0, 0, new Color(0x123249),
                width, height, BACKGROUND));
        g.fillRect(0, 0, width, height);

        // 배경 장식: 오른쪽에 크게 깔린 레이더. 잘려 나가도 괜찮은 위치입니다.
        Graphics2D bg = (Graphics2D) g.create();
        bg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
        double bigScale = 9.0;
        bg.translate(width - 300, height / 2.0 - 54 * bigScale);
        bg.scale(bigScale, bigScale);
        bg.translate(-54, 0);
        drawMark(bg, 0.6);
        bg.dispose();

        // 왼쪽 정보 블록
        int left = 76;
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 96));
        g.drawString("VATRadar", left, 250);

        g.setColor(ACCENT);
        g.fillRect(left, 282, 120, 5);

        g.setColor(new Color(0xD5E4EF));
        g.setFont(new Font("SansSerif", Font.PLAIN, 34));
        g.drawString("Live VATSIM traffic, controllers and weather", left, 348);

        g.dispose();
        return image;
    }

    // -------------------------------------------------------------- 공통 도형

    /** ic_launcher_foreground.xml 과 같은 도형. 좌표계는 108x108. */
    private static void drawMark(Graphics2D g, double opacity) {
        g.setStroke(new BasicStroke(1.5f));

        g.setColor(white(0x4D, opacity));
        g.draw(new Ellipse2D.Double(24, 24, 60, 60));

        g.setColor(white(0x66, opacity));
        g.draw(new Ellipse2D.Double(36, 36, 36, 36));

        // 레이더 스윕 — 12시에서 시계 방향 45도.
        g.setColor(new Color(0x82, 0xE0, 0xFF, (int) (0x33 * opacity)));
        g.fill(new Arc2D.Double(24, 24, 60, 60, 90, -45, Arc2D.PIE));

        g.setColor(white(0xFF, opacity));
        g.fill(aircraft());
    }

    private static Color white(int alpha, double opacity) {
        return new Color(255, 255, 255, (int) (alpha * opacity));
    }

    private static Shape aircraft() {
        double[][] points = {
                {54, 38}, {57, 47}, {72, 56}, {72, 60}, {57, 56}, {57, 66},
                {61, 70}, {61, 73}, {54, 71}, {47, 73}, {47, 70}, {51, 66},
                {51, 56}, {36, 60}, {36, 56}, {51, 47}
        };
        Path2D.Double path = new Path2D.Double();
        path.moveTo(points[0][0], points[0][1]);
        for (int i = 1; i < points.length; i++) {
            path.lineTo(points[i][0], points[i][1]);
        }
        path.closePath();
        return path;
    }

    private static Graphics2D prepare(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    private StoreGraphics() {
    }
}
