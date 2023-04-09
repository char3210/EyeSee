package xyz.duncanruns.eyesee;

import com.sun.jna.platform.win32.WinDef;
import xyz.duncanruns.eyesee.win32.GDI32Extra;
import xyz.duncanruns.eyesee.win32.HwndUtil;
import xyz.duncanruns.eyesee.win32.KeyboardUtil;
import xyz.duncanruns.eyesee.win32.User32;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EyeSeeGUI extends JFrame implements WindowListener {

    private static final Robot ROBOT;
    private static final WinDef.DWORD SRCCOPY = new WinDef.DWORD(0x00CC0020);

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
    private final int width = 60;
    private final int height = 100;
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
        setSize(300, 500);
        setAlwaysOnTop(true);
        String randTitle = "EyeSee " + new Random().nextInt();
        setTitle(randTitle);
        setVisible(true);
        eyeSeeHwnd = new WinDef.HWND(HwndUtil.waitForWindow(randTitle));
        setTitle("EyeSee");
        tick();
        executor.scheduleAtFixedRate(this::tick, 50_000_000, 1_000_000_000L / options.refreshRate, TimeUnit.NANOSECONDS);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    private void tick() {
        EyeSeeOptions options = EyeSeeOptions.getInstance();
        if (KeyboardUtil.isPressed(options.hotkey)) lastKeyPress = System.currentTimeMillis();
        if (System.currentTimeMillis() - lastKeyPress > options.disappearAfter) {
            if (currentlyShowing) {
                System.out.println("Hiding...");
                currentlyShowing = false;
                HwndUtil.minimize(eyeSeeHwnd.getPointer());
            }
            return;
        } else if (!currentlyShowing) {
            System.out.println("Showing...");
            currentlyShowing = true;
            HwndUtil.unminimizeNoActivate(eyeSeeHwnd.getPointer());
        }

        WinDef.HWND sourceHwnd = null;
        if (useForeground) {
            sourceHwnd = new WinDef.HWND(User32.INSTANCE.GetForegroundWindow());
        }
        Rectangle rectangle = getYoinkArea(sourceHwnd);
        WinDef.HDC sourceHDC = User32.INSTANCE.GetDC(sourceHwnd);
        WinDef.HDC eyeSeeHDC = User32.INSTANCE.GetDC(eyeSeeHwnd);

        GDI32Extra.INSTANCE.SetStretchBltMode(eyeSeeHDC, 3);

        GDI32Extra.INSTANCE.StretchBlt(eyeSeeHDC, 0, 0, 300, 500, sourceHDC, rectangle.x, rectangle.y, rectangle.width, rectangle.height, SRCCOPY);

        User32.INSTANCE.ReleaseDC(sourceHwnd, sourceHDC);
        User32.INSTANCE.ReleaseDC(eyeSeeHwnd, eyeSeeHDC);
    }

    private Rectangle getYoinkArea(WinDef.HWND hwnd) {
        Rectangle rectangle;
        if (hwnd == null) {
            rectangle = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        } else {
            rectangle = HwndUtil.getHwndInnerRectangle(hwnd.getPointer());
        }
        return new Rectangle((int) rectangle.getCenterX() - width / 2, (int) rectangle.getCenterY() - height / 2, width, height);
    }

    public static void main(String[] args) {
        new EyeSeeGUI();
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
