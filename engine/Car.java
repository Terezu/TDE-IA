package cars.engine;

import java.awt.*;
import java.awt.geom.*;
import java.util.function.Consumer;

import static cars.engine.Vector2.*;
import static java.lang.Math.toRadians;

public abstract class Car implements Cloneable {
    private final Color color;
    private final double mass;
    private final double maxForce;
    private final double maxSpeed;
    private Vector2 position;
    private Vector2 velocity;

    // Store last clamped steering (pre-dt) for debug drawing
    private Vector2 lastSteering = vec2();

    public Car(Consumer<Settings> settings) {
        var cs = new Settings();
        settings.accept(cs);
        this.color = cs.color;
        this.position = cs.position;
        this.velocity = Vector2.byAngle(cs.orientation);
        this.mass = cs.mass;
        this.maxForce = cs.maxForce;
        this.maxSpeed = cs.maxSpeed;
    }

    // ---- Accessors ----
    public Vector2 getPosition() { return position.clone(); }
    public Vector2 getVelocity() { return velocity.clone(); }
    public double getSpeed() { return velocity.size(); }

    public Vector2 getDirection() { return velocity.isZero() ? byAngle(0) : normalize(velocity); }
    public double getMass() { return mass; }
    public double getMaxForce() { return maxForce; }
    public double getMaxSpeed() { return maxSpeed; }
    private Vector2 getLastSteering() { return lastSteering == null ? null : lastSteering.clone(); }

    public abstract Vector2 calculateSteering(World world);

    void update(World world) {
        final var steeringForce = calculateSteering(world);
        if (steeringForce == null) {
            lastSteering = vec2();
            return;
        }

        lastSteering = truncate(steeringForce, maxForce);

        final var impulse = multiply(lastSteering, world.getSecs()); // F * dt
        final var acceleration = divide(impulse, mass);              // (F*dt)/m
        velocity = truncate(add(velocity, acceleration), maxSpeed);
        position = add(position, multiply(velocity, world.getSecs()));

        final var w = world.getWidth() / 2.0;
        final var h = world.getHeight() / 2.0;
        if (position.x < -(w + 20)) position.x =  w;
        if (position.x >  (w + 20)) position.x = -w;
        if (position.y < -(h + 20)) position.y =  h;
        if (position.y >  (h + 20)) position.y = -h;
    }

    // -----------------------------------------------------
    // Drawing (Java2D)
    // -----------------------------------------------------
    void draw(Graphics2D g, boolean debug) {
        // Car in its local (heading) frame
        final var g2 = (Graphics2D) g.create();
        g2.translate(position.x, position.y);
        g2.rotate(velocity.getAngle());               // radians
        g2.scale(-0.5, 0.5);                          // mirror X + scale 50% (match JavaFX)

        drawF1Car80px(g2, color);

        g2.dispose();

        // Debug vectors in WORLD space (no rotation)
        if (debug) {
            drawDebugArrows(g);
        }
    }

    // ----- F1 car, authored at 80px, drawn with 50% scale above -----
    private static void drawF1Car80px(Graphics2D g, Color bodyColor) {
        final var L = 80.0;
        final var W = 36.0;
        final var halfL = L / 2.0;
        final var halfW = W / 2.0;

        final var wingColor = Color.DARK_GRAY;
        final var tireColor = Color.BLACK;

        // Body gradient along length (maps like JavaFX)
        final var start = new Point2D.Double(-halfL, 0);
        final var end   = new Point2D.Double( halfL, 0);
        final var fractions = new float[] {0f, 0.5f, 1f};
        final var colors    = new Color[] { bodyColor.darker(), bodyColor, bodyColor.darker() };
        final var bodyGrad = new LinearGradientPaint(start, end, fractions, colors, MultipleGradientPaint.CycleMethod.NO_CYCLE);

        final var oldPaint = g.getPaint();
        final var oldStroke = g.getStroke();

        // Tires (RoundRect to match FX corners)
        g.setPaint(tireColor);
        final var tireW = 12.0;
        final var tireH = 7.0;
        final var frontX =  halfL * 0.55;
        final var rearX  = -halfL * 0.55;
        final var tireY = halfW;
        g.fill(new RoundRectangle2D.Double(frontX - tireW / 2, -tireY,     tireW, tireH, 3, 3));
        g.fill(new RoundRectangle2D.Double(frontX - tireW / 2,  tireY - 7, tireW, tireH, 3, 3));
        g.fill(new RoundRectangle2D.Double(rearX  - tireW / 2, -tireY,     tireW, tireH, 3, 3));
        g.fill(new RoundRectangle2D.Double(rearX  - tireW / 2,  tireY - 7, tireW, tireH, 3, 3));

        // Central tub
        g.setPaint(bodyGrad);
        g.fill(new RoundRectangle2D.Double(-halfL * 0.45, -W * 0.22, L * 0.70, W * 0.44, 6, 6));
        // Nose cone
        g.fill(new RoundRectangle2D.Double(-halfL * 0.45, -W * 0.10, L * 0.50, W * 0.20, 10, 10));

        // Sidepods (slightly darker)
        g.setPaint(bodyColor.darker());
        g.fill(new RoundRectangle2D.Double(-L * 0.10, -W * 0.34, L * 0.30, W * 0.20, 6, 6));
        g.fill(new RoundRectangle2D.Double(-L * 0.10,  W * 0.14, L * 0.30, W * 0.20, 6, 6));

        // Cockpit / halo area (opaque-ish dark oval)
        g.setPaint(new Color(25, 25, 32, 230));
        g.fill(new Ellipse2D.Double(-L * 0.12, -W * 0.18, L * 0.28, W * 0.36));

        // Front wing
        g.setPaint(wingColor);
        g.fill(new RoundRectangle2D.Double(halfL - 10, -W * 0.70, 12, W * 1.40, 6, 6));

        // Rear wing
        g.fill(new RoundRectangle2D.Double(-halfL - 4, -W * 0.50, 8, W * 0.98, 6, 6));

        // Center highlight
        g.setPaint(new Color(255, 255, 255, 72)); // ~0.28 alpha
        g.setStroke(new BasicStroke(2f));
        g.draw(new Line2D.Double(-halfL, 0, halfL, 0));

        g.setPaint(oldPaint);
        g.setStroke(oldStroke);
    }

    // ----- Debug arrows (WORLD space) -----
    private void drawDebugArrows(Graphics2D g) {
        final var origin = add(position, multiply(getDirection(), 12));
        drawArrow(g, origin, velocity, 0.20, new Color(0, 0, 255));           // BLUE
        drawArrow(g, origin, getLastSteering(), 0.10, new Color(255, 69, 0)); // ORANGERED
    }

    private static void drawArrow(Graphics2D g, Vector2 origin, Vector2 vector, double scale, Color color) {
        if (Vector2.isZero(vector)) return;

        final var tip = add(origin, multiply(vector, scale));
        final var dir = subtract(tip, origin);

        final var oldComp = g.getComposite();
        final var oldStroke = g.getStroke();
        final var oldColor = g.getColor();

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
        g.setColor(color);
        g.setStroke(new BasicStroke(3f));

        g.draw(new Line2D.Double(origin.x, origin.y, tip.x, tip.y));

        final var angle = dir.getAngle();
        final var a = toRadians(30);
        final var head = 8.0;
        final var p1 = subtract(tip, byAngleSize(angle - a, head));
        final var p2 = subtract(tip, byAngleSize(angle + a, head));
        g.draw(new Line2D.Double(tip.x, tip.y, p1.x, p1.y));
        g.draw(new Line2D.Double(tip.x, tip.y, p2.x, p2.y));

        g.setComposite(oldComp);
        g.setStroke(oldStroke);
        g.setColor(oldColor);
    }

    @Override
    public Car clone() {
        try {
            final var other = (Car) super.clone();
            other.position = position.clone();
            other.velocity = velocity.clone();
            return other;
        } catch (CloneNotSupportedException ignored) {
            return null;
        }
    }

    public static double distance(Car car1, Car car2) {
        return Vector2.distance(car1.position, car2.position);
    }
}
