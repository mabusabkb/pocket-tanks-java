import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class PocketTanks extends JPanel {

    static final int WIDTH = 800;
    static final int HEIGHT = 500;
    static final int TOTAL_ROUNDS = 10;
    static final int MOVE_STEP = 8;
    static final int START_FUEL = 80;

    static final Color RED_TANK = new Color(215, 55, 55);
    static final Color BLUE_TANK = new Color(65, 95, 225);

    enum Kind { NORMAL, DIRT, ROLLER }

    // DATA: weapon table. Add a new weapon = add one line (and a case in WeaponIcon).
    //                 name           crater damage shots bounces kind         description
    enum Weapon {
        SMALL  ("Small Shell",  30,    35,    1,    0,     Kind.NORMAL, "Basic shell"),
        BIG    ("Big Shell",    60,    60,    1,    0,     Kind.NORMAL, "Big crater, big damage"),
        TRIPLE ("Triple Shot",  25,    30,    3,    0,     Kind.NORMAL, "Three shells fanned out"),
        BOUNCER("Bouncer",      35,    40,    1,    2,     Kind.NORMAL, "Bounces twice, then explodes"),
        ROLLER ("Roller",       30,    45,    1,    0,     Kind.ROLLER, "Lands, then rolls toward a tank"),
        DIRT   ("Dirt Mover",   45,     0,    1,    0,     Kind.DIRT,   "Piles up a hill (no damage)"),
        NUKE   ("Nuke",        100,    90,    1,    0,     Kind.NORMAL, "Giant blast");

        final String label, desc;
        final int radius, damage, shots, bounces;
        final Kind kind;

        Weapon(String label, int radius, int damage, int shots, int bounces, Kind kind, String desc) {
            this.label = label;
            this.radius = radius;
            this.damage = damage;
            this.shots = shots;
            this.bounces = bounces;
            this.kind = kind;
            this.desc = desc;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    static class Shell {
        double x, y, vx, vy;
        int bouncesLeft;
        boolean rolling;
        int dir, rollTicks;
        LinkedList<double[]> trail = new LinkedList<>();
    }

    static class Explosion {
        double x, y, maxR;
        int age;
        boolean dirt;
        String text;
    }

    static class Particle {
        double x, y, vx, vy;
        int life;
        Color color;
    }

    // game state
    int[] terrain = new int[WIDTH];
    int tank1X = 120;
    int tank2X = 680;
    int angle1 = 45;
    int angle2 = 135;
    int score1 = 0;
    int score2 = 0;
    int round = 1;
    int wind = 0;
    int fuel = START_FUEL;
    boolean player1Turn = true;
    boolean gameOver = false;
    boolean flying = false;
    Color color1 = RED_TANK;
    Color color2 = BLUE_TANK;

    // ammo left per weapon (index = Weapon.ordinal())
    int[] ammo1 = new int[Weapon.values().length];
    int[] ammo2 = new int[Weapon.values().length];

    List<Shell> shells = new ArrayList<>();
    List<Explosion> explosions = new ArrayList<>();
    List<Particle> particles = new ArrayList<>();
    Weapon currentWeapon = Weapon.SMALL;
    Timer timer;       // game physics (only runs while shells fly)
    Timer animTimer;   // visuals (always running)
    Random rng = new Random();
    int[][] stars = new int[70][3];
    double[][] clouds = new double[6][4]; // x, y, speed, size
    double animTime = 0;
    double camShift = -0.0;   // eases toward the action, drives parallax
    double shake = 0;
    Runnable onNewGame;

    // controls
    JSlider angleSlider = new JSlider(0, 180, 45);
    JSlider powerSlider = new JSlider(10, 100, 60);
    JComboBox<Weapon> weaponBox = new JComboBox<>();
    JCheckBox soundBox = new JCheckBox("Sound", true);
    JCheckBox musicBox = new JCheckBox("Music", true);
    JButton moveLeft = new JButton("Move <");
    JButton moveRight = new JButton("Move >");
    JButton fireButton = new JButton("FIRE");
    JButton newGameButton = new JButton("New Game");
    JButton endGameButton = new JButton("End Game");
    JButton quitButton = new JButton("Quit");
    JLabel turnLabel = new JLabel();
    JLabel fuelLabel = new JLabel();
    JPanel controls = new JPanel();

    public PocketTanks() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        for (int[] s : stars) {
            s[0] = rng.nextInt(WIDTH);
            s[1] = rng.nextInt(260);
            s[2] = 1 + rng.nextInt(2);
        }
        for (double[] c : clouds) {
            c[0] = rng.nextDouble() * WIDTH;
            c[1] = 50 + rng.nextDouble() * 150;
            c[2] = 0.15 + rng.nextDouble() * 0.35;
            c[3] = 40 + rng.nextDouble() * 50;
        }
        generateTerrain();
        newWind();
        buildControls();
        timer = new Timer(16, e -> updateGame());
        animTimer = new Timer(33, e -> animate());
        animTimer.start();
        updateLabels();
    }

    // ---------- SETUP ----------

    void generateTerrain() {
        double p1 = rng.nextDouble() * 6.28;
        double p2 = rng.nextDouble() * 6.28;
        double p3 = rng.nextDouble() * 6.28;
        double f1 = 0.006 + rng.nextDouble() * 0.006;
        double f2 = 0.02 + rng.nextDouble() * 0.02;
        double f3 = 0.05 + rng.nextDouble() * 0.04;
        for (int x = 0; x < WIDTH; x++) {
            double h = 310
                    + 40 * Math.sin(x * f1 + p1)
                    + 22 * Math.sin(x * f2 + p2)
                    + 8 * Math.sin(x * f3 + p3);
            terrain[x] = (int) h;
        }
    }

    void newWind() {
        wind = rng.nextInt(21) - 10; // -10 .. +10
    }

    void buildControls() {
        angleSlider.setPreferredSize(new Dimension(115, 25));
        powerSlider.setPreferredSize(new Dimension(115, 25));

        // dropdown shows icon + "Weapon Name  xAmmo" for the current player
        weaponBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Weapon) {
                    Weapon w = (Weapon) value;
                    int[] ammo = player1Turn ? ammo1 : ammo2;
                    setText(w.label + "  x" + ammo[w.ordinal()]);
                    setIcon(new WeaponIcon(w, 20));
                }
                return this;
            }
        });

        angleSlider.addChangeListener(e -> {
            if (player1Turn) angle1 = angleSlider.getValue();
            else angle2 = angleSlider.getValue();
            repaint();
        });
        powerSlider.addChangeListener(e -> repaint());
        soundBox.addActionListener(e -> Sound.enabled = soundBox.isSelected());
        musicBox.addActionListener(e -> Music.setOn(musicBox.isSelected()));
        endGameButton.addActionListener(e -> endGameNow());
        quitButton.addActionListener(e -> {
            int answer = JOptionPane.showConfirmDialog(this, "Quit the game?", "Quit",
                    JOptionPane.YES_NO_OPTION);
            if (answer == JOptionPane.YES_OPTION) System.exit(0);
        });
        moveLeft.addActionListener(e -> move(-1));
        moveRight.addActionListener(e -> move(1));
        fireButton.addActionListener(e -> fire());
        newGameButton.addActionListener(e -> {
            timer.stop();
            if (onNewGame != null) onNewGame.run();
        });

        JPanel row1 = new JPanel();
        row1.add(new JLabel("Angle:"));
        row1.add(angleSlider);
        row1.add(new JLabel("Power:"));
        row1.add(powerSlider);
        row1.add(new JLabel("Weapon:"));
        row1.add(weaponBox);
        row1.add(soundBox);
        row1.add(musicBox);

        JPanel row2 = new JPanel();
        row2.add(turnLabel);
        row2.add(moveLeft);
        row2.add(moveRight);
        row2.add(fuelLabel);
        row2.add(fireButton);
        row2.add(newGameButton);
        row2.add(endGameButton);
        row2.add(quitButton);

        controls.setLayout(new GridLayout(2, 1));
        controls.add(row1);
        controls.add(row2);
    }

    void updateLabels() {
        turnLabel.setText(player1Turn ? "PLAYER 1's turn   " : "PLAYER 2's turn   ");
        turnLabel.setForeground((player1Turn ? color1 : color2).darker());
        fuelLabel.setText("Fuel: " + fuel + "  ");
    }

    void setControlsEnabled(boolean on) {
        fireButton.setEnabled(on);
        weaponBox.setEnabled(on);
        moveLeft.setEnabled(on);
        moveRight.setEnabled(on);
    }

    // fill the dropdown with weapons the current player still has
    void refreshWeaponBox() {
        int[] ammo = player1Turn ? ammo1 : ammo2;
        List<Weapon> list = new ArrayList<>();
        for (Weapon w : Weapon.values()) {
            if (ammo[w.ordinal()] > 0) list.add(w);
        }
        weaponBox.setModel(new DefaultComboBoxModel<>(list.toArray(new Weapon[0])));
    }

    int ammoLeft(int[] ammo) {
        int total = 0;
        for (int a : ammo) total += a;
        return total;
    }

    // called by the arsenal screen
    void startMatch(int[] a1, int[] a2, Color c1, Color c2) {
        ammo1 = a1.clone();
        ammo2 = a2.clone();
        color1 = c1;
        color2 = c2;

        timer.stop();
        shells.clear();
        explosions.clear();
        particles.clear();
        shake = 0;
        flying = false;
        gameOver = false;
        score1 = 0;
        score2 = 0;
        round = 1;
        player1Turn = true;
        tank1X = 120;
        tank2X = 680;
        angle1 = 45;
        angle2 = 135;
        fuel = START_FUEL;
        generateTerrain();
        newWind();
        angleSlider.setValue(angle1);
        powerSlider.setValue(60);
        refreshWeaponBox();
        setControlsEnabled(true);
        updateLabels();
        repaint();
    }

    // ---------- LOGIC ----------

    int currentTankX() {
        return player1Turn ? tank1X : tank2X;
    }

    // how much the tank leans to match the ground under it
    double tiltOf(int x) {
        int a = Math.max(0, x - 12);
        int b = Math.min(WIDTH - 1, x + 12);
        double t = Math.atan2(terrain[b] - terrain[a], b - a);
        return Math.max(-0.6, Math.min(0.6, t));
    }

    // where the barrel is attached (follows the tilt)
    double[] pivot(int x) {
        double t = tiltOf(x);
        return new double[]{x + 14 * Math.sin(t), terrain[x] - 14 * Math.cos(t)};
    }

    // VALIDATION: stay on the map, keep away from the other tank, limited fuel
    void move(int dir) {
        if (flying || gameOver || fuel <= 0) return;
        int other = player1Turn ? tank2X : tank1X;
        int nx = currentTankX() + dir * MOVE_STEP;
        if (nx < 20 || nx > WIDTH - 20 || Math.abs(nx - other) < 50) return;
        if (player1Turn) tank1X = nx;
        else tank2X = nx;
        fuel -= MOVE_STEP;
        updateLabels();
        repaint();
    }

    void fire() {
        if (flying || gameOver) return; // VALIDATION

        Weapon w = (Weapon) weaponBox.getSelectedItem();
        if (w == null) return;
        int[] ammo = player1Turn ? ammo1 : ammo2;
        if (ammo[w.ordinal()] <= 0) return; // VALIDATION: out of ammo
        ammo[w.ordinal()]--;
        currentWeapon = w;

        double speed = powerSlider.getValue() * 0.12;
        double[] pv = pivot(currentTankX());

        shells.clear();
        for (int i = 0; i < w.shots; i++) {
            double offset = (i - (w.shots - 1) / 2.0) * 6;
            double a = Math.toRadians(angleSlider.getValue() + offset);

            Shell s = new Shell();
            s.x = pv[0] + 26 * Math.cos(a);
            s.y = pv[1] - 26 * Math.sin(a);
            s.vx = speed * Math.cos(a);
            s.vy = -speed * Math.sin(a);
            s.bouncesLeft = w.bounces;
            shells.add(s);
        }

        Sound.play(Sound.FIRE);
        flying = true;
        setControlsEnabled(false);
        timer.start();
    }

    // visuals: runs all the time (clouds, camera, particles, shake)
    void animate() {
        animTime += 0.033;

        for (double[] c : clouds) {
            c[0] += c[2];
            if (c[0] > WIDTH + 120) c[0] = -120;
        }

        // camera eases toward the shell (or the active tank)
        double focus = currentTankX();
        if (!shells.isEmpty()) focus = shells.get(0).x;
        double target = (focus - WIDTH / 2.0) / (WIDTH / 2.0); // -1 .. 1
        camShift += (target - camShift) * 0.06;

        shake *= 0.88;
        if (shake < 0.3) shake = 0;

        Iterator<Particle> pi = particles.iterator();
        while (pi.hasNext()) {
            Particle p = pi.next();
            p.vy += 0.2;
            p.x += p.vx;
            p.y += p.vy;
            p.life--;
            if (p.life <= 0 || p.y > HEIGHT) pi.remove();
        }
        repaint();
    }

    // physics: runs every 16 ms while shells or explosions are on screen
    void updateGame() {
        Iterator<Shell> it = shells.iterator();
        while (it.hasNext()) {
            Shell s = it.next();

            // a roller that has landed follows the ground
            if (s.rolling) {
                if (rollStep(s)) it.remove();
                continue;
            }

            s.vx += wind * 0.003; // wind pushes sideways
            s.vy += 0.15;         // gravity
            s.x += s.vx;
            s.y += s.vy;

            s.trail.add(new double[]{s.x, s.y});
            if (s.trail.size() > 14) s.trail.removeFirst();

            if (s.x < 0 || s.x >= WIDTH || s.y > HEIGHT) {
                it.remove(); // flew off the map
                continue;
            }

            int ix = (int) s.x;
            if (s.y >= terrain[ix]) {
                if (s.bouncesLeft > 0) {
                    s.bouncesLeft--;
                    s.y = terrain[ix] - 1;
                    s.vy = -Math.abs(s.vy) * 0.6;
                    s.vx *= 0.8;
                    Sound.play(Sound.BOUNCE);
                } else if (currentWeapon.kind == Kind.ROLLER) {
                    s.rolling = true;
                    s.dir = s.vx >= 0 ? 1 : -1;
                    s.vx = 0;
                    s.vy = 0;
                    s.y = terrain[ix] - 1;
                } else {
                    explode(ix, terrain[ix], currentWeapon);
                    it.remove();
                }
            }
        }

        Iterator<Explosion> eit = explosions.iterator();
        while (eit.hasNext()) {
            Explosion ex = eit.next();
            ex.age++;
            if (ex.age >= 24) eit.remove();
        }

        if (shells.isEmpty() && explosions.isEmpty()) {
            endShot();
        }
        repaint();
    }

    // roller: move 2 px along the ground. returns true if it exploded.
    boolean rollStep(Shell s) {
        s.rollTicks++;
        int cur = (int) s.x;
        double nx = s.x + s.dir * 2.0;
        int nix = (int) nx;

        boolean wall = nix < 1 || nix >= WIDTH - 1 || terrain[nix] < terrain[cur] - 3;
        boolean nearTank = Math.abs(nx - tank1X) < 16 || Math.abs(nx - tank2X) < 16;

        if (wall || nearTank || s.rollTicks > 300) {
            explode(cur, terrain[cur], currentWeapon);
            return true;
        }

        s.x = nx;
        s.y = terrain[nix] - 1;
        s.trail.add(new double[]{s.x, s.y});
        if (s.trail.size() > 14) s.trail.removeFirst();
        return false;
    }

    void explode(int cx, int cy, Weapon w) {
        int points = 0;

        if (w.kind == Kind.DIRT) {
            // build a mound instead of a crater
            for (int x = cx - w.radius; x <= cx + w.radius; x++) {
                if (x < 0 || x >= WIDTH) continue;
                int dx = x - cx;
                double top = cy - Math.sqrt(w.radius * w.radius - dx * dx);
                if (terrain[x] > top) {
                    terrain[x] = (int) Math.max(40, top);
                }
            }
            Sound.play(Sound.DIRT);
        } else {
            // score first, then dig the crater
            int hit1 = damageTo(tank1X, cx, cy, w);
            int hit2 = damageTo(tank2X, cx, cy, w);
            points = player1Turn ? hit2 : hit1; // points only for hitting the enemy
            if (player1Turn) score1 += points;
            else score2 += points;

            for (int x = cx - w.radius; x <= cx + w.radius; x++) {
                if (x < 0 || x >= WIDTH) continue;
                int dx = x - cx;
                double depth = Math.sqrt(w.radius * w.radius - dx * dx);
                int bottom = (int) Math.min(HEIGHT - 1, cy + depth);
                if (terrain[x] < bottom) {
                    terrain[x] = bottom;
                }
            }
            Sound.play(w.radius >= 60 ? Sound.BIG_EXPLODE : Sound.EXPLODE);
            if (points > 0) Sound.play(Sound.HIT);
        }

        Explosion ex = new Explosion();
        ex.x = cx;
        ex.y = cy;
        ex.maxR = w.radius * 1.3;
        ex.dirt = (w.kind == Kind.DIRT);
        ex.text = points > 0 ? "+" + points : null;
        explosions.add(ex);

        // flying debris + screen shake
        int count = 14 + w.radius / 3;
        for (int i = 0; i < count; i++) {
            Particle p = new Particle();
            double ang = Math.PI * (0.1 + 0.8 * rng.nextDouble());
            double sp = 1.5 + rng.nextDouble() * (2 + w.radius / 15.0);
            p.x = cx;
            p.y = cy;
            p.vx = Math.cos(ang) * sp;
            p.vy = -Math.sin(ang) * sp;
            p.life = 30 + rng.nextInt(30);
            p.color = (ex.dirt || rng.nextBoolean()) ? new Color(125, 90, 50) : new Color(255, 170, 60);
            particles.add(p);
        }
        shake = Math.min(6, Math.max(shake, w.radius / 10.0));
    }

    int damageTo(int tankX, int cx, int cy, Weapon w) {
        double tankY = terrain[tankX] - 8;
        double dist = Math.hypot(tankX - cx, tankY - cy);
        double reach = w.radius * 1.75;
        if (dist >= reach) return 0;
        return (int) (w.damage * (1 - dist / reach));
    }

    void endShot() {
        timer.stop();
        flying = false;
        player1Turn = !player1Turn;
        if (player1Turn) round++; // both players have fired

        // the game is over when every weapon has been used
        int left1 = ammoLeft(ammo1);
        int left2 = ammoLeft(ammo2);
        if (left1 == 0 && left2 == 0) {
            finishGame();
            return;
        }
        // if the next player has nothing left, the other player keeps going
        if ((player1Turn ? left1 : left2) == 0) {
            player1Turn = !player1Turn;
        }

        fuel = START_FUEL;
        newWind();
        angleSlider.setValue(player1Turn ? angle1 : angle2);
        refreshWeaponBox();
        setControlsEnabled(true);
        updateLabels();
    }

    // End Game button: stop right now and show the result
    void endGameNow() {
        if (gameOver) return;
        int answer = JOptionPane.showConfirmDialog(this,
                "End the game now and show the result?", "End Game", JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) return;
        timer.stop();
        shells.clear();
        explosions.clear();
        flying = false;
        finishGame();
        repaint();
    }

    void finishGame() {
        gameOver = true;
        round = Math.min(round, TOTAL_ROUNDS);
        setControlsEnabled(false);
        fuelLabel.setText("");
        if (score1 > score2) {
            turnLabel.setText("PLAYER 1 wins!   ");
            turnLabel.setForeground(color1.darker());
        } else if (score2 > score1) {
            turnLabel.setText("PLAYER 2 wins!   ");
            turnLabel.setForeground(color2.darker());
        } else {
            turnLabel.setText("DRAW!   ");
            turnLabel.setForeground(Color.BLACK);
        }
        Sound.play(Sound.WIN);
    }

    // ---------- DRAWING ----------

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        AffineTransform saved = g2.getTransform();
        if (shake > 0) {
            g2.translate((rng.nextDouble() - 0.5) * shake * 2, (rng.nextDouble() - 0.5) * shake * 2);
        }

        // sky
        g2.setPaint(new GradientPaint(0, 0, new Color(20, 0, 60), 0, HEIGHT, new Color(125, 55, 165)));
        g2.fillRect(-10, -10, WIDTH + 20, HEIGHT + 20);

        // twinkling stars (far away: move a little)
        for (int i = 0; i < stars.length; i++) {
            int[] s = stars[i];
            int alpha = (int) (130 + 90 * Math.sin(animTime * 2 + i));
            g2.setColor(new Color(255, 255, 255, alpha));
            g2.fillOval((int) (s[0] - camShift * 3), s[1], s[2], s[2]);
        }
        drawBall(g2);

        // parallax layers: far things move less than near things
        drawMountains(g2, -camShift * 10, 330, 110, 0.011, 0.03, 1.3,
                new Color(78, 34, 118), new Color(45, 15, 80));
        drawClouds(g2);
        drawMountains(g2, -camShift * 22, 365, 75, 0.017, 0.05, 4.1,
                new Color(105, 50, 135), new Color(60, 25, 95));

        drawTerrain(g2);

        // tanks
        drawTank(g2, tank1X, color1, angle1);
        drawTank(g2, tank2X, color2, angle2);

        // turn marker + aim guide
        if (!flying && !gameOver) {
            int tx = currentTankX();
            int gy = terrain[tx];
            double bounce = Math.sin(animTime * 5) * 3;
            g2.setColor(Color.WHITE);
            g2.fillPolygon(new int[]{tx - 6, tx + 6, tx},
                    new int[]{(int) (gy - 50 + bounce), (int) (gy - 50 + bounce), (int) (gy - 42 + bounce)}, 3);

            double[] pv = pivot(tx);
            double a = Math.toRadians(angleSlider.getValue());
            double len = 32 + powerSlider.getValue() * 0.5;
            g2.setColor(new Color(255, 255, 255, 190));
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10f, new float[]{4f, 6f}, 0f));
            g2.drawLine((int) (pv[0] + 28 * Math.cos(a)), (int) (pv[1] - 28 * Math.sin(a)),
                    (int) (pv[0] + len * Math.cos(a)), (int) (pv[1] - len * Math.sin(a)));
            g2.setStroke(new BasicStroke(1f));
        }

        // shells with trails
        for (Shell s : shells) {
            int n = s.trail.size();
            int i = 0;
            for (double[] p : s.trail) {
                int alpha = (int) (200.0 * (i + 1) / n);
                int sz = 2 + (int) (3.0 * i / n);
                g2.setColor(new Color(255, 220, 120, alpha));
                g2.fillOval((int) p[0] - sz / 2, (int) p[1] - sz / 2, sz, sz);
                i++;
            }
            g2.setColor(Color.WHITE);
            g2.fillOval((int) s.x - 3, (int) s.y - 3, 7, 7);
        }

        // flying debris
        for (Particle p : particles) {
            g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(),
                    Math.min(255, p.life * 9)));
            g2.fillRect((int) p.x, (int) p.y, 3, 3);
        }

        // explosions
        for (Explosion ex : explosions) {
            double p = ex.age / 24.0;
            double r = ex.maxR * (0.35 + 0.65 * p);
            int alpha = (int) (230 * (1 - p));
            g2.setColor(ex.dirt ? new Color(120, 80, 40, alpha) : new Color(255, 120, 20, alpha));
            g2.fill(new Ellipse2D.Double(ex.x - r, ex.y - r, 2 * r, 2 * r));
            double r2 = r * 0.6;
            g2.setColor(ex.dirt ? new Color(185, 145, 85, alpha) : new Color(255, 230, 90, alpha));
            g2.fill(new Ellipse2D.Double(ex.x - r2, ex.y - r2, 2 * r2, 2 * r2));

            // shockwave ring
            double rr = ex.maxR * (0.5 + 1.3 * p);
            g2.setStroke(new BasicStroke(3f));
            g2.setColor(new Color(255, 255, 255, alpha / 2));
            g2.draw(new Ellipse2D.Double(ex.x - rr, ex.y - rr, 2 * rr, 2 * rr));
            g2.setStroke(new BasicStroke(1f));

            if (ex.text != null) {
                g2.setFont(new Font("SansSerif", Font.BOLD, 18));
                g2.setColor(new Color(255, 255, 255, alpha));
                g2.drawString(ex.text, (int) ex.x - 12, (int) (ex.y - ex.maxR - p * 30));
            }
        }

        g2.setTransform(saved); // the HUD does not shake
        drawHud(g2);
        if (gameOver) drawGameOver(g2);
    }

    void drawMountains(Graphics2D g2, double shift, int baseY, double amp, double f1, double f2,
                       double seed, Color top, Color bottom) {
        int step = 8;
        int n = (WIDTH + 200) / step + 1;
        int[] xs = new int[n + 2];
        int[] ys = new int[n + 2];
        for (int i = 0; i < n; i++) {
            double x = -100 + i * step;
            double wx = x - shift;
            double h = baseY
                    - amp * (1 - Math.abs(Math.sin(wx * f1 + seed)))
                    - amp * 0.3 * Math.sin(wx * f2 + seed * 2);
            xs[i] = (int) x;
            ys[i] = (int) h;
        }
        xs[n] = xs[n - 1];
        ys[n] = HEIGHT;
        xs[n + 1] = xs[0];
        ys[n + 1] = HEIGHT;
        g2.setPaint(new GradientPaint(0, baseY - (float) amp, top, 0, HEIGHT, bottom));
        g2.fillPolygon(xs, ys, n + 2);
    }

    void drawClouds(Graphics2D g2) {
        g2.setColor(new Color(255, 205, 245, 55));
        for (double[] c : clouds) {
            double cx = c[0] - camShift * 6;
            double size = c[3];
            g2.fill(new Ellipse2D.Double(cx - size * 0.5, c[1], size, size * 0.35));
            g2.fill(new Ellipse2D.Double(cx - size * 0.3, c[1] - size * 0.12, size * 0.6, size * 0.35));
            g2.fill(new Ellipse2D.Double(cx - size * 0.1, c[1] + size * 0.05, size * 0.7, size * 0.3));
        }
    }

    void drawTerrain(Graphics2D g2) {
        int[] xs = new int[WIDTH + 2];
        int[] ys = new int[WIDTH + 2];
        for (int x = 0; x < WIDTH; x++) {
            xs[x] = x;
            ys[x] = terrain[x];
        }
        xs[WIDTH] = WIDTH - 1;
        ys[WIDTH] = HEIGHT;
        xs[WIDTH + 1] = 0;
        ys[WIDTH + 1] = HEIGHT;
        g2.setPaint(new GradientPaint(0, 230, new Color(95, 190, 80), 0, HEIGHT, new Color(25, 85, 45)));
        g2.fillPolygon(xs, ys, WIDTH + 2);

        // thick dark lip under the edge gives the ground depth
        g2.setColor(new Color(28, 95, 50));
        g2.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int x = 1; x < WIDTH; x++) {
            g2.drawLine(x - 1, terrain[x - 1] + 5, x, terrain[x] + 5);
        }

        // bright edge, lit from the left: slopes facing left are brighter
        g2.setStroke(new BasicStroke(3f));
        for (int x = 1; x < WIDTH; x++) {
            double lit = Math.max(0.35, Math.min(1.0, 0.7 + (terrain[x - 1] - terrain[x]) * 0.15));
            g2.setColor(new Color((int) (160 * lit), (int) (245 * lit), (int) (115 * lit)));
            g2.drawLine(x - 1, terrain[x - 1], x, terrain[x]);
        }
        g2.setStroke(new BasicStroke(1f));
    }

    void drawBall(Graphics2D g2) {
        double cx = 690 - camShift * 5;
        double cy = 85;
        double r = 36;
        Ellipse2D ball = new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
        Shape old = g2.getClip();
        g2.clip(ball);
        g2.setColor(new Color(235, 235, 245));
        g2.fill(ball);

        // checker pattern slides sideways so the ball seems to spin
        g2.setColor(new Color(150, 150, 175));
        int sq = 12;
        double off = (animTime * 10) % (2 * sq);
        for (int i = -2; i < 9; i++) {
            for (int j = 0; j < 7; j++) {
                if (Math.floorMod(i + j, 2) == 0) {
                    g2.fill(new Rectangle2D.Double(cx - r + i * sq + off, cy - r + j * sq, sq, sq));
                }
            }
        }

        // round shading
        g2.setPaint(new RadialGradientPaint(new Point2D.Double(cx - r * 0.35, cy - r * 0.35),
                (float) (r * 1.6), new float[]{0f, 0.55f, 1f},
                new Color[]{new Color(255, 255, 255, 110), new Color(0, 0, 0, 0), new Color(10, 0, 50, 170)}));
        g2.fill(ball);
        g2.setClip(old);
    }

    void drawTank(Graphics2D g2, int x, Color color, int angleDeg) {
        int gy = terrain[x];
        double tilt = tiltOf(x);
        double bob = Math.sin(animTime * 7 + x) * 0.5; // engine idle wobble

        // soft shadow on the ground
        g2.setColor(new Color(0, 0, 0, 70));
        g2.fill(new Ellipse2D.Double(x - 22, gy - 3, 44, 9));

        AffineTransform old = g2.getTransform();
        g2.translate(0, bob);
        g2.rotate(tilt, x, gy); // lean with the slope

        // tracks
        g2.setPaint(new GradientPaint(x, gy - 9, new Color(75, 75, 85), x, gy, new Color(25, 25, 30)));
        g2.fillRoundRect(x - 18, gy - 9, 36, 9, 9, 9);
        g2.setColor(new Color(15, 15, 18));
        for (int i = -15; i <= 15; i += 5) {
            g2.drawLine(x + i, gy - 8, x + i, gy - 1);
        }
        // shiny wheels
        for (int i = -12; i <= 12; i += 8) {
            g2.setPaint(new RadialGradientPaint(new Point2D.Double(x + i - 1, gy - 6), 4f,
                    new float[]{0f, 1f}, new Color[]{new Color(170, 170, 180), new Color(60, 60, 70)}));
            g2.fill(new Ellipse2D.Double(x + i - 3.5, gy - 8, 7, 7));
        }

        // hull with light from the top-left
        g2.setPaint(new GradientPaint(x - 14, gy - 19, color.brighter(), x + 14, gy - 8, color.darker()));
        g2.fillRoundRect(x - 15, gy - 19, 30, 11, 7, 7);
        g2.setColor(new Color(255, 255, 255, 80));
        g2.fillRoundRect(x - 12, gy - 18, 24, 3, 3, 3);
        g2.setColor(new Color(0, 0, 0, 60));
        g2.drawLine(x - 13, gy - 11, x + 13, gy - 11);

        g2.setTransform(old);

        // barrel and dome are drawn upright so the aim angle stays exact
        double[] pv = pivot(x);
        double px = pv[0];
        double py = pv[1] + bob;
        double a = Math.toRadians(angleDeg);
        int bx = (int) (px + 24 * Math.cos(a));
        int by = (int) (py - 24 * Math.sin(a));

        g2.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(35, 35, 40));
        g2.drawLine((int) px, (int) py, bx, by);
        g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(105, 105, 115));
        g2.drawLine((int) px, (int) py, bx, by);
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(195, 195, 205));
        g2.drawLine((int) px, (int) py - 1, bx, by - 1);
        g2.setStroke(new BasicStroke(1f));

        // round dome
        g2.setPaint(new RadialGradientPaint(new Point2D.Double(px - 3, py - 5), 12f,
                new float[]{0f, 1f}, new Color[]{color.brighter().brighter(), color.darker()}));
        g2.fill(new Arc2D.Double(px - 9, py - 9, 18, 18, 0, 180, Arc2D.CHORD));
    }

    void drawHud(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRect(0, 0, WIDTH, 34);
        g2.setFont(new Font("SansSerif", Font.BOLD, 16));

        g2.setColor(color1.brighter());
        g2.drawString("PLAYER 1  " + score1 + "   [" + ammoLeft(ammo1) + " left]", 15, 23);

        g2.setColor(color2.brighter());
        String right = "PLAYER 2  " + score2 + "   [" + ammoLeft(ammo2) + " left]";
        g2.drawString(right, WIDTH - 15 - g2.getFontMetrics().stringWidth(right), 23);

        g2.setColor(Color.WHITE);
        g2.drawString("Round " + Math.min(round, TOTAL_ROUNDS) + " / " + TOTAL_ROUNDS, WIDTH / 2 - 120, 23);

        String windText;
        if (wind == 0) {
            windText = "Wind: calm";
        } else {
            String arrows = (wind > 0 ? ">" : "<").repeat((int) Math.ceil(Math.abs(wind) / 2.0));
            windText = "Wind: " + arrows + "  " + Math.abs(wind);
        }
        g2.drawString(windText, WIDTH / 2 + 30, 23);
    }

    void drawCentered(Graphics2D g2, String text, int y) {
        int w = g2.getFontMetrics().stringWidth(text);
        g2.drawString(text, (WIDTH - w) / 2, y);
    }

    // big result screen shown when the game ends
    void drawGameOver(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 175));
        g2.fillRect(0, 0, WIDTH, HEIGHT);

        String result;
        Color resultColor;
        if (score1 > score2) {
            result = "PLAYER 1 WINS!";
            resultColor = color1.brighter();
        } else if (score2 > score1) {
            result = "PLAYER 2 WINS!";
            resultColor = color2.brighter();
        } else {
            result = "IT'S A DRAW!";
            resultColor = Color.WHITE;
        }

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 58));
        drawCentered(g2, "GAME OVER", 190);

        g2.setColor(resultColor);
        g2.setFont(new Font("SansSerif", Font.BOLD, 36));
        drawCentered(g2, result, 250);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 22));
        drawCentered(g2, "PLAYER 1: " + score1 + "   -   PLAYER 2: " + score2, 302);

        g2.setColor(new Color(210, 200, 235));
        g2.setFont(new Font("SansSerif", Font.PLAIN, 15));
        drawCentered(g2, "All weapons used. Click New Game to pick weapons and play again", 345);
    }

    // ---------- START ----------

    public static void main(String[] args) {
        JFrame frame = new JFrame("Pocket Tanks");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        CardLayout cards = new CardLayout();
        JPanel root = new JPanel(cards);

        PocketTanks game = new PocketTanks();
        JPanel gameCard = new JPanel(new BorderLayout());
        gameCard.add(game, BorderLayout.CENTER);
        gameCard.add(game.controls, BorderLayout.SOUTH);

        ArsenalPanel arsenal = new ArsenalPanel((a1, a2, c1, c2) -> {
            game.startMatch(a1, a2, c1, c2);
            cards.show(root, "game");
        });
        game.onNewGame = () -> cards.show(root, "arsenal");

        root.add(arsenal, "arsenal");
        root.add(gameCard, "game");

        Music.start();
        frame.add(root);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}

// ================== WEAPON ICONS (drawn in code) ==================
class WeaponIcon implements Icon {
    final PocketTanks.Weapon weapon;
    final int size;

    WeaponIcon(PocketTanks.Weapon weapon, int size) {
        this.weapon = weapon;
        this.size = size;
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }

    static void ball(Graphics2D g2, double cx, double cy, double r, Color light, Color dark) {
        g2.setPaint(new RadialGradientPaint(new Point2D.Double(cx - r * 0.35, cy - r * 0.35),
                (float) (r * 1.5), new float[]{0f, 1f}, new Color[]{light, dark}));
        g2.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.translate(x, y);
        double s = size / 32.0;
        g2.scale(s, s); // everything below is drawn on a 32 x 32 grid

        Color steelLight = new Color(215, 220, 230);
        Color steelDark = new Color(70, 75, 90);

        switch (weapon) {
            case SMALL:
                ball(g2, 16, 18, 7, steelLight, steelDark);
                break;
            case BIG:
                ball(g2, 16, 18, 12, steelLight, steelDark);
                g2.setColor(new Color(255, 170, 40));
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(21, 7, 25, 2);
                break;
            case TRIPLE:
                ball(g2, 9, 22, 5, steelLight, steelDark);
                ball(g2, 16, 9, 5, steelLight, steelDark);
                ball(g2, 23, 22, 5, steelLight, steelDark);
                break;
            case BOUNCER:
                g2.setColor(new Color(90, 220, 255));
                g2.setStroke(new BasicStroke(2f));
                g2.draw(new Arc2D.Double(2, 14, 12, 24, 0, 180, Arc2D.OPEN));
                g2.draw(new Arc2D.Double(14, 14, 12, 24, 0, 180, Arc2D.OPEN));
                ball(g2, 26, 9, 5, new Color(190, 255, 190), new Color(30, 120, 60));
                break;
            case ROLLER:
                g2.setColor(new Color(255, 255, 255, 170));
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(2, 14, 9, 14);
                g2.drawLine(1, 20, 8, 20);
                g2.drawLine(3, 26, 10, 26);
                ball(g2, 19, 19, 9, new Color(255, 200, 120), new Color(190, 80, 20));
                break;
            case DIRT:
                g2.setPaint(new GradientPaint(0, 8, new Color(170, 120, 65), 0, 28, new Color(90, 55, 25)));
                g2.fill(new Arc2D.Double(2, 8, 28, 40, 0, 180, Arc2D.CHORD));
                g2.setColor(new Color(60, 35, 15));
                g2.fillOval(11, 18, 4, 3);
                g2.fillOval(19, 21, 3, 3);
                break;
            case NUKE:
                ball(g2, 16, 16, 13, new Color(255, 140, 90), new Color(150, 20, 20));
                g2.setColor(new Color(255, 225, 60));
                for (int i = 0; i < 3; i++) {
                    g2.fill(new Arc2D.Double(7, 7, 18, 18, 90 + i * 120 - 30, 60, Arc2D.PIE));
                }
                g2.setColor(new Color(40, 10, 10));
                g2.fill(new Ellipse2D.Double(13.5, 13.5, 5, 5));
                break;
            default:
                break;
        }
        g2.dispose();
    }
}

// ================== WEAPON + COLOR PICKING SCREEN ==================
interface StartListener {
    void start(int[] ammo1, int[] ammo2, Color color1, Color color2);
}

class ArsenalPanel extends JPanel {

    static final int PICKS = PocketTanks.TOTAL_ROUNDS;
    static final int MAX_EACH = 3;

    static final Color[] PALETTE = {
            new Color(215, 55, 55), new Color(240, 140, 40), new Color(235, 205, 50),
            new Color(70, 180, 80), new Color(40, 190, 200), new Color(65, 95, 225),
            new Color(150, 80, 210), new Color(235, 100, 170), new Color(120, 120, 130)
    };

    JSpinner[] spin1;
    JSpinner[] spin2;
    JLabel total1 = new JLabel();
    JLabel total2 = new JLabel();
    JLabel head1;
    JLabel head2;
    List<JButton> swatches1 = new ArrayList<>();
    List<JButton> swatches2 = new ArrayList<>();
    Color color1 = PocketTanks.RED_TANK;
    Color color2 = PocketTanks.BLUE_TANK;

    ArsenalPanel(StartListener onStart) {
        PocketTanks.Weapon[] ws = PocketTanks.Weapon.values();
        spin1 = new JSpinner[ws.length];
        spin2 = new JSpinner[ws.length];

        setLayout(new BorderLayout(10, 10));
        setBackground(new Color(30, 10, 60));
        setBorder(BorderFactory.createEmptyBorder(15, 25, 15, 25));

        JLabel title = new JLabel("CHOOSE YOUR ARSENAL", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 26));
        title.setForeground(Color.WHITE);
        JLabel sub = new JLabel("Each player picks " + PICKS + " weapons (max " + MAX_EACH
                + " of each kind). The game ends when all weapons are used.", SwingConstants.CENTER);
        sub.setForeground(new Color(200, 190, 230));
        JPanel top = new JPanel(new GridLayout(2, 1));
        top.setOpaque(false);
        top.add(title);
        top.add(sub);
        add(top, BorderLayout.NORTH);

        head1 = makeLabel("PLAYER 1", true, color1.brighter());
        head2 = makeLabel("PLAYER 2", true, color2.brighter());

        JPanel grid = new JPanel(new GridLayout(ws.length + 1, 4, 10, 6));
        grid.setOpaque(false);
        grid.add(makeLabel("Weapon", true, Color.WHITE));
        grid.add(makeLabel("What it does", true, Color.WHITE));
        grid.add(head1);
        grid.add(head2);

        for (int i = 0; i < ws.length; i++) {
            spin1[i] = new JSpinner(new SpinnerNumberModel(0, 0, MAX_EACH, 1));
            spin2[i] = new JSpinner(new SpinnerNumberModel(0, 0, MAX_EACH, 1));
            spin1[i].addChangeListener(e -> updateTotals());
            spin2[i].addChangeListener(e -> updateTotals());

            JLabel name = makeLabel(ws[i].label, true, Color.WHITE);
            name.setIcon(new WeaponIcon(ws[i], 30));
            name.setIconTextGap(10);

            grid.add(name);
            grid.add(makeLabel(ws[i].desc, false, new Color(200, 190, 230)));
            grid.add(spin1[i]);
            grid.add(spin2[i]);
        }
        add(grid, BorderLayout.CENTER);

        JButton randomBtn = new JButton("Random Pick");
        randomBtn.addActionListener(e -> {
            randomFill(spin1);
            randomFill(spin2);
        });

        JButton startBtn = new JButton("START GAME");
        startBtn.addActionListener(e -> {
            if (sum(spin1) != PICKS || sum(spin2) != PICKS) {
                JOptionPane.showMessageDialog(this,
                        "Both players must pick exactly " + PICKS + " weapons.");
                return;
            }
            if (color1.equals(color2)) {
                JOptionPane.showMessageDialog(this, "Please give the two tanks different colors.");
                return;
            }
            onStart.start(read(spin1), read(spin2), color1, color2);
        });

        JPanel colorPanel = new JPanel(new GridLayout(2, 1));
        colorPanel.setOpaque(false);
        colorPanel.add(colorRow("PLAYER 1 tank color:", true));
        colorPanel.add(colorRow("PLAYER 2 tank color:", false));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 5));
        buttons.setOpaque(false);
        buttons.add(total1);
        buttons.add(total2);
        buttons.add(randomBtn);
        buttons.add(startBtn);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(colorPanel, BorderLayout.NORTH);
        bottom.add(buttons, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);

        refreshSwatches();
        updateTotals();
    }

    JPanel colorRow(String text, boolean first) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 2));
        row.setOpaque(false);
        row.add(makeLabel(text, true, Color.WHITE));

        for (Color c : PALETTE) {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(26, 26));
            b.setBackground(c);
            b.setOpaque(true);
            b.setFocusPainted(false);
            b.addActionListener(e -> setColor(first, c));
            (first ? swatches1 : swatches2).add(b);
            row.add(b);
        }

        JButton custom = new JButton("More...");
        custom.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(this, "Choose tank color", first ? color1 : color2);
            if (chosen != null) setColor(first, chosen);
        });
        row.add(custom);
        return row;
    }

    void setColor(boolean first, Color c) {
        if (first) {
            color1 = c;
            head1.setForeground(c.brighter());
        } else {
            color2 = c;
            head2.setForeground(c.brighter());
        }
        refreshSwatches();
    }

    void refreshSwatches() {
        Color dark = new Color(30, 10, 60);
        for (JButton b : swatches1) {
            b.setBorder(BorderFactory.createLineBorder(b.getBackground().equals(color1) ? Color.WHITE : dark, 3));
        }
        for (JButton b : swatches2) {
            b.setBorder(BorderFactory.createLineBorder(b.getBackground().equals(color2) ? Color.WHITE : dark, 3));
        }
    }

    JLabel makeLabel(String text, boolean bold, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, 14));
        l.setForeground(color);
        return l;
    }

    int sum(JSpinner[] spinners) {
        int t = 0;
        for (JSpinner s : spinners) t += (Integer) s.getValue();
        return t;
    }

    int[] read(JSpinner[] spinners) {
        int[] out = new int[spinners.length];
        for (int i = 0; i < spinners.length; i++) out[i] = (Integer) spinners[i].getValue();
        return out;
    }

    void randomFill(JSpinner[] spinners) {
        Random r = new Random();
        int[] counts = new int[spinners.length];
        int total = 0;
        while (total < PICKS) {
            int i = r.nextInt(spinners.length);
            if (counts[i] < MAX_EACH) {
                counts[i]++;
                total++;
            }
        }
        for (int i = 0; i < spinners.length; i++) spinners[i].setValue(counts[i]);
    }

    void updateTotals() {
        if (spin1[spin1.length - 1] == null || spin2[spin2.length - 1] == null) return; // still building
        int t1 = sum(spin1);
        int t2 = sum(spin2);
        total1.setText("PLAYER 1: " + t1 + " / " + PICKS);
        total2.setText("PLAYER 2: " + t2 + " / " + PICKS);
        total1.setForeground(t1 == PICKS ? new Color(120, 255, 120) : new Color(255, 140, 140));
        total2.setForeground(t2 == PICKS ? new Color(120, 255, 120) : new Color(255, 140, 140));
    }
}

// ================== SOUND (synthesized, no files needed) ==================
class Sound {
    static final float RATE = 22050f;
    static boolean enabled = true;

    static final byte[] FIRE = tone(700, 180, 0.18, 0.35);
    static final byte[] EXPLODE = noise(0.55, 0.6);
    static final byte[] BIG_EXPLODE = noise(1.1, 0.8);
    static final byte[] HIT = tone(900, 900, 0.3, 0.3);
    static final byte[] DIRT = tone(120, 60, 0.25, 0.6);
    static final byte[] BOUNCE = tone(300, 500, 0.08, 0.3);
    static final byte[] WIN = concat(tone(523, 523, 0.15, 0.3), tone(659, 659, 0.15, 0.3), tone(784, 784, 0.4, 0.3));

    static void put(byte[] b, int i, double sample) {
        sample = Math.max(-1, Math.min(1, sample));
        short v = (short) (sample * 32767);
        b[2 * i] = (byte) (v & 0xff);
        b[2 * i + 1] = (byte) ((v >> 8) & 0xff);
    }

    // a tone that slides from f0 to f1 Hz and fades out
    static byte[] tone(double f0, double f1, double sec, double vol) {
        int n = (int) (RATE * sec);
        byte[] b = new byte[n * 2];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            double f = f0 + (f1 - f0) * t;
            phase += 2 * Math.PI * f / RATE;
            put(b, i, Math.sin(phase) * (1 - t) * vol);
        }
        return b;
    }

    // rumbling noise that fades out (explosion)
    static byte[] noise(double sec, double vol) {
        Random r = new Random(7);
        int n = (int) (RATE * sec);
        byte[] b = new byte[n * 2];
        double lp = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            lp += ((r.nextDouble() * 2 - 1) - lp) * 0.3;
            put(b, i, lp * 2 * Math.pow(1 - t, 2) * vol);
        }
        return b;
    }

    static byte[] concat(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) len += p.length;
        byte[] out = new byte[len];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }

    static void play(byte[] data) {
        if (!enabled) return;
        new Thread(() -> {
            try {
                AudioFormat fmt = new AudioFormat(RATE, 16, 1, true, false);
                Clip clip = AudioSystem.getClip();
                clip.open(fmt, data, 0, data.length);
                clip.addLineListener(ev -> {
                    if (ev.getType() == LineEvent.Type.STOP) clip.close();
                });
                clip.start();
            } catch (Exception ex) {
                // no sound device available: just stay silent
            }
        }).start();
    }
}

// ================== BACKGROUND MUSIC (synthesized loop) ==================
class Music {
    static Clip clip;
    static boolean wanted = true;

    static double freq(int midi) {
        return 440.0 * Math.pow(2, (midi - 69) / 12.0);
    }

    // 4 chords (Am - F - C - G), 8 eighth notes each, ~9 second loop
    static byte[] build() {
        double eighth = 60.0 / 110 / 2;
        int eighthLen = (int) (Sound.RATE * eighth);
        int total = eighthLen * 32;
        double[] mix = new double[total];

        int[][] chords = {{57, 60, 64, 69}, {53, 57, 60, 65}, {60, 64, 67, 72}, {55, 59, 62, 67}};
        int[] bass = {45, 41, 48, 43};
        int[] pattern = {0, 2, 1, 2, 3, 2, 1, 2};

        for (int c = 0; c < 4; c++) {
            for (int k = 0; k < 8; k++) {
                int start = (c * 8 + k) * eighthLen;
                // arpeggio note on every eighth
                addNote(mix, start, (int) (eighthLen * 0.9),
                        freq(chords[c][pattern[k]] + 12), 0.22, 7.0, false);
                // bass note on every beat
                if (k % 2 == 0) {
                    addNote(mix, start, (int) (eighthLen * 1.8), freq(bass[c]), 0.30, 3.0, true);
                }
            }
        }

        byte[] out = new byte[total * 2];
        for (int i = 0; i < total; i++) {
            Sound.put(out, i, mix[i]);
        }
        return out;
    }

    static void addNote(double[] mix, int start, int len, double f, double vol, double decay, boolean bassWave) {
        int release = (int) (Sound.RATE * 0.01); // short fade so notes don't click
        for (int i = 0; i < len && start + i < mix.length; i++) {
            double t = (double) i / Sound.RATE;
            double env = Math.min(1.0, t / 0.005) * Math.exp(-t * decay);
            env *= Math.min(1.0, (double) (len - i) / release);
            double ph = 2 * Math.PI * f * t;
            double wave = bassWave
                    ? Math.sin(ph) + 0.4 * Math.sin(3 * ph)
                    : Math.sin(ph) + 0.25 * Math.sin(2 * ph);
            mix[start + i] += wave * env * vol;
        }
    }

    static void start() {
        try {
            if (clip == null) {
                byte[] data = build();
                AudioFormat fmt = new AudioFormat(Sound.RATE, 16, 1, true, false);
                clip = AudioSystem.getClip();
                clip.open(fmt, data, 0, data.length);
                try {
                    FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                    gain.setValue(-10f); // keep the music quieter than the effects
                } catch (Exception ignore) {
                    // volume control not available: play at normal volume
                }
            }
            if (wanted) clip.loop(Clip.LOOP_CONTINUOUSLY);
        } catch (Exception ex) {
            clip = null; // no sound device: stay silent
        }
    }

    static void setOn(boolean on) {
        wanted = on;
        if (on) start();
        else if (clip != null) clip.stop();
    }
}