package xyz.duncanruns.eyesee;

import com.sun.jna.platform.win32.WinDef;
import xyz.duncanruns.eyesee.win32.GDI32Extra;
import xyz.duncanruns.eyesee.win32.HwndUtil;
import xyz.duncanruns.eyesee.win32.KeyboardUtil;
import xyz.duncanruns.eyesee.win32.User32;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EyeSeeGUI extends JFrame implements WindowListener {

    private static final Robot ROBOT;
    private static final WinDef.DWORD SRCCOPY = new WinDef.DWORD(0x00CC0020);

    WinDef.HBRUSH GRAY = GDI32Extra.INSTANCE.CreateSolidBrush(0x00aaaaaa);
    WinDef.HBRUSH WHITE = GDI32Extra.INSTANCE.CreateSolidBrush(0x00ffffff);

    WinDef.HBRUSH RED = GDI32Extra.INSTANCE.CreateSolidBrush(0x000000ff);
    WinDef.HPEN hpen = GDI32Extra.INSTANCE.CreatePen(5, 0, 0);

    static {
        try {
            ROBOT = new Robot();
        } catch (AWTException e) {
            throw new RuntimeException(e);
        }
    }

    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private Image image;
    //private final Object imageLock = new Object();
    private final boolean useForeground = true;
    private final WinDef.HWND eyeSeeHwnd;
    private long lastKeyPress = -1L;
    private boolean currentlyShowing = false;

    public EyeSeeGUI() {
        super();
        EyeSeeOptions options = EyeSeeOptions.getInstance();
        setLocation(options.x, options.y);
        addWindowListener(this);
        setResizable(false);
        // make the window size the same units StretchBlt uses (physical pixels)
        AffineTransform transform = getGraphicsConfiguration().getDefaultTransform();
        double scaleX = transform.getScaleX();
        double scaleY = transform.getScaleY();
        setSize((int) (options.displayWidth() / scaleX), (int) (options.displayHeight() / scaleY));
        setAlwaysOnTop(true);
        String randTitle = "EyeSee " + new Random().nextInt();
        setTitle(randTitle);
        setVisible(true);
        eyeSeeHwnd = new WinDef.HWND(HwndUtil.waitForWindow(randTitle));
        setTitle("EyeSee");
        tick();
        executor.scheduleAtFixedRate(this::tick, 50_000_000, 1_000_000_000L / options.refreshRate, TimeUnit.NANOSECONDS);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyChar() == 'o') {
                    editOptions();
                }
            }
        });
    }

    public static void main(String[] args) {
        new EyeSeeGUI();
    }

    private void editOptions() {
        try {
            Desktop.getDesktop().open(EyeSeeOptions.getOptionsPath().toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void tick() {
        EyeSeeOptions options = EyeSeeOptions.getInstance();
        KeyboardUtil.tick(options.hotkey);
        if (options.useToggle) {
            // Toggle style
            if (KeyboardUtil.wasPressed()) {
                currentlyShowing = !currentlyShowing;
                if (currentlyShowing) {
                    showEyeSee();
                } else {
                    hideEyeSee();
                }
            }
        } else {
            // Non toggle style
            long currentTime = System.currentTimeMillis();
            if (KeyboardUtil.isKeyDown()) {
                lastKeyPress = currentTime;
                if (!currentlyShowing) {
                    showEyeSee();
                }
            } else if (currentTime - lastKeyPress > options.disappearAfter) {
                if (currentlyShowing) {
                    hideEyeSee();
                }
            }
        }

        if (!currentlyShowing) return;

        WinDef.HWND sourceHwnd = null;
        if (useForeground) {
            sourceHwnd = new WinDef.HWND(User32.INSTANCE.GetForegroundWindow());
        }
        Rectangle rectangle = getYoinkArea(sourceHwnd, options);
        WinDef.HDC sourceHDC = User32.INSTANCE.GetDC(sourceHwnd);
        WinDef.HDC eyeSeeHDC = User32.INSTANCE.GetDC(eyeSeeHwnd);

        GDI32Extra.INSTANCE.SetStretchBltMode(eyeSeeHDC, 3);

        GDI32Extra.INSTANCE.StretchBlt(eyeSeeHDC, 0, 0, options.displayWidth(), options.displayHeight(), sourceHDC, rectangle.x, rectangle.y, rectangle.width, rectangle.height, SRCCOPY);

        GDI32Extra.INSTANCE.SelectObject(eyeSeeHDC, hpen);

        //grid
        for (int col = 0; col < options.viewportWidth; col++) {
            if ((col - options.viewportWidth/2) % 5 == 0) {
                GDI32Extra.INSTANCE.SelectObject(eyeSeeHDC, WHITE);
            } else {
                GDI32Extra.INSTANCE.SelectObject(eyeSeeHDC, GRAY);
            }
            GDI32Extra.INSTANCE.Rectangle(eyeSeeHDC, (int) (col*options.scaleFactor)-1, 0, (int) (col*options.scaleFactor)+1, options.displayHeight());
        }

        //crosshair
        GDI32Extra.INSTANCE.SelectObject(eyeSeeHDC, RED);
        int mid = options.displayWidth()/2;
        GDI32Extra.INSTANCE.Rectangle(eyeSeeHDC, mid-2, 0, mid+2, options.displayHeight());


        User32.INSTANCE.ReleaseDC(sourceHwnd, sourceHDC);
        User32.INSTANCE.ReleaseDC(eyeSeeHwnd, eyeSeeHDC);
    }

    private void showEyeSee() {
        System.out.println("Showing...");
        currentlyShowing = true;
        HwndUtil.unminimizeNoActivate(eyeSeeHwnd.getPointer());
    }

    private void hideEyeSee() {
        System.out.println("Hiding...");
        currentlyShowing = false;
        HwndUtil.minimize(eyeSeeHwnd.getPointer());
    }

    private Rectangle getYoinkArea(WinDef.HWND hwnd, EyeSeeOptions options) {
        Rectangle rectangle;
        if (hwnd == null) {
            rectangle = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        } else {
            rectangle = HwndUtil.getHwndInnerRectangle(hwnd.getPointer());
        }
        int width = options.viewportWidth;
        int height = options.viewportHeight;
        return new Rectangle((int) rectangle.getCenterX() - width / 2, (int) rectangle.getCenterY() - height / 2, width, height);
    }

    @Override
    public void windowOpened(WindowEvent e) {

    }

    @Override
    public void windowClosing(WindowEvent e) {
        Point p = getLocation();
        EyeSeeOptions.getInstance().x = p.x;
        EyeSeeOptions.getInstance().y = p.y;
        executor.shutdownNow();
        System.out.println("EyeSee Closed.");
        EyeSeeOptions.getInstance().save();
    }

    @Override
    public void windowClosed(WindowEvent e) {
    }

    @Override
    public void windowIconified(WindowEvent e) {
    }

    @Override
    public void windowDeiconified(WindowEvent e) {

    }

    @Override
    public void windowActivated(WindowEvent e) {

    }

    @Override
    public void windowDeactivated(WindowEvent e) {

    }
}
