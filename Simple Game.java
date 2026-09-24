import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.KeyStroke;
import javax.swing.Timer;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.Rectangle;
import java.util.Random;

public class Main {
    public static void main(String[] args) {
        JFrame window = new JFrame("My First Game");

        JPanel menu = new JPanel();
        JLabel title = new JLabel("My First Game", SwingConstants.CENTER);
        JButton startButton = new JButton("Start Game");

        menu.add(title);
        menu.add(startButton);

        startButton.addActionListener(event -> {
            GameScreen gameScreen = new GameScreen();

            window.setContentPane(gameScreen);
            window.revalidate();
            gameScreen.requestFocusInWindow();
        });

        window.setSize(800, 600);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setContentPane(menu);
        window.setLocationRelativeTo(null);
        window.setVisible(true);
    }
}

class GameScreen extends JPanel {
    private static final int TILE_SIZE = 40;
    private static final int WORLD_WIDTH = 60;
    private static final int WORLD_HEIGHT = 45;

    private final int[][] world = new int[WORLD_WIDTH][WORLD_HEIGHT];

    private int playerX = 30 * TILE_SIZE;
    private int playerY = 22 * TILE_SIZE;

    private final int playerSize = 30;
    private final int speed = 5;

    private boolean up;
    private boolean down;
    private boolean left;
    private boolean right;

    private boolean hasSword = false;
    private boolean attacking = false;
    private int attackTimer = 0;

    private String message = "Find the chests to get a sword";
    private long messageUntil = Long.MAX_VALUE;

    private int walkFrame = 0;
    private int walkCounter = 0;

    private int facingX = 0;
    private int facingY = 1; // Initially facing down

    GameScreen() {
        setFocusable(true);

        generateWorld();

        addKeyBinding("pressed UP", () -> up = true);
        addKeyBinding("released UP", () -> up = false);
        addKeyBinding("pressed DOWN", () -> down = true);
        addKeyBinding("released DOWN", () -> down = false);
        addKeyBinding("pressed LEFT", () -> left = true);
        addKeyBinding("released LEFT", () -> left = false);
        addKeyBinding("pressed RIGHT", () -> right = true);
        addKeyBinding("released RIGHT", () -> right = false);
        addKeyBinding("pressed SPACE", this::attack);

        Timer timer = new Timer(16, event -> {
            movePlayer();
            updateAttack();
            repaint();
        });

        timer.start();
    }

    private void generateWorld() {
        Random random = new Random();

        // Start with grass everywhere
        for (int x = 0; x < WORLD_WIDTH; x++) {
            for (int y = 0; y < WORLD_HEIGHT; y++) {
                world[x][y] = 0;
            }
        }

        // Create grouped water areas
        for (int lake = 0; lake < 7; lake++) {
            int centerX = random.nextInt(WORLD_WIDTH);
            int centerY = random.nextInt(WORLD_HEIGHT);
            int radiusX = 2 + random.nextInt(4);
            int radiusY = 2 + random.nextInt(4);

            for (int x = centerX - radiusX; x <= centerX + radiusX; x++) {
                for (int y = centerY - radiusY; y <= centerY + radiusY; y++) {
                    if (x >= 0 && x < WORLD_WIDTH
                            && y >= 0 && y < WORLD_HEIGHT) {

                        double distance =
                                Math.pow((x - centerX) / (double) radiusX, 2)
                                + Math.pow((y - centerY) / (double) radiusY, 2);

                        if (distance <= 1.0) {
                            world[x][y] = 1; // Water
                        }
                    }
                }
            }
        }

        // Create grouped tree areas
        for (int group = 0; group < 18; group++) {
            int centerX = random.nextInt(WORLD_WIDTH);
            int centerY = random.nextInt(WORLD_HEIGHT);
            int radius = 1 + random.nextInt(2);

            for (int x = centerX - radius; x <= centerX + radius; x++) {
                for (int y = centerY - radius; y <= centerY + radius; y++) {
                    if (x >= 0 && x < WORLD_WIDTH
                            && y >= 0 && y < WORLD_HEIGHT
                            && world[x][y] == 0
                            && random.nextInt(100) < 75) {

                        world[x][y] = 2; // Tree
                    }
                }
            }
        }

        // Keep the player's starting area clear
        for (int x = 28; x <= 32; x++) {
            for (int y = 20; y <= 24; y++) {
                world[x][y] = 0;
            }
        }

        placeChests(random);
    }

    private void placeChests(Random random) {
        int chestsPlaced = 0;

        while (chestsPlaced < 8) {
            int x = random.nextInt(WORLD_WIDTH);
            int y = random.nextInt(WORLD_HEIGHT);

            boolean farFromStart =
                    Math.abs(x - 30) > 5 || Math.abs(y - 22) > 5;

            if (world[x][y] == 0 && farFromStart) {
                world[x][y] = 3; // Chest
                chestsPlaced++;
            }
        }
    }

    private void addKeyBinding(String key, Runnable action) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(key), key);

        getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
    }

    private void movePlayer() {
        int horizontalMovement = 0;
        int verticalMovement = 0;

        if (left) horizontalMovement -= speed;
        if (right) horizontalMovement += speed;
        if (up) verticalMovement -= speed;
        if (down) verticalMovement += speed;

        if (horizontalMovement != 0 || verticalMovement != 0) {
            facingX = Integer.signum(horizontalMovement);
            facingY = Integer.signum(verticalMovement);
        }

        boolean moved = false;

        if (canMoveTo(playerX + horizontalMovement, playerY)) {
            playerX += horizontalMovement;
            moved = horizontalMovement != 0;
        }

        if (canMoveTo(playerX, playerY + verticalMovement)) {
            playerY += verticalMovement;
            moved = moved || verticalMovement != 0;
        }

        if (moved) {
            walkCounter++;

            if (walkCounter >= 4) {
                walkFrame = (walkFrame + 1) % 4;
                walkCounter = 0;
            }
        } else {
            walkFrame = 0;
            walkCounter = 0;
        }

        if (moved && !hasSword) {
            message = "";
            messageUntil = 0;
        }

        collectChest();
    }

    private boolean canMoveTo(int newX, int newY) {
        Rectangle player = new Rectangle(
                newX, newY, playerSize, playerSize
        );

        int firstTileX = Math.max(0, newX / TILE_SIZE);
        int firstTileY = Math.max(0, newY / TILE_SIZE);

        int lastTileX = Math.min(
                WORLD_WIDTH - 1,
                (newX + playerSize) / TILE_SIZE
        );

        int lastTileY = Math.min(
                WORLD_HEIGHT - 1,
                (newY + playerSize) / TILE_SIZE
        );

        for (int x = firstTileX; x <= lastTileX; x++) {
            for (int y = firstTileY; y <= lastTileY; y++) {
                if (world[x][y] == 1 || world[x][y] == 2) {
                    Rectangle obstacle = new Rectangle(
                            x * TILE_SIZE,
                            y * TILE_SIZE,
                            TILE_SIZE,
                            TILE_SIZE
                    );

                    if (player.intersects(obstacle)) {
                        return false;
                    }
                }
            }
        }

        return newX >= 0
                && newY >= 0
                && newX + playerSize <= WORLD_WIDTH * TILE_SIZE
                && newY + playerSize <= WORLD_HEIGHT * TILE_SIZE;
    }

    private void collectChest() {
        int tileX = (playerX + playerSize / 2) / TILE_SIZE;
        int tileY = (playerY + playerSize / 2) / TILE_SIZE;

        if (world[tileX][tileY] == 3) {
            world[tileX][tileY] = 0;
            hasSword = true;

            message = "You found a sword!";
            messageUntil = System.currentTimeMillis() + 3000;
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        int cameraX = playerX - getWidth() / 2;
        int cameraY = playerY - getHeight() / 2;

        cameraX = Math.max(0, Math.min(
                cameraX,
                WORLD_WIDTH * TILE_SIZE - getWidth()
        ));

        cameraY = Math.max(0, Math.min(
                cameraY,
                WORLD_HEIGHT * TILE_SIZE - getHeight()
        ));

        int startX = cameraX / TILE_SIZE;
        int startY = cameraY / TILE_SIZE;
        int endX = Math.min(WORLD_WIDTH, startX + getWidth() / TILE_SIZE + 2);
        int endY = Math.min(WORLD_HEIGHT, startY + getHeight() / TILE_SIZE + 2);

        for (int x = startX; x < endX; x++) {
            for (int y = startY; y < endY; y++) {
                int screenX = x * TILE_SIZE - cameraX;
                int screenY = y * TILE_SIZE - cameraY;

                // Grass background
                graphics.setColor(new Color(100, 180, 80));
                graphics.fillRect(screenX, screenY, TILE_SIZE, TILE_SIZE);

                // Grass details
                if (world[x][y] == 0) {
                    int pattern = Math.abs((x * 31 + y * 17) % 5);

                    graphics.setColor(new Color(75, 155, 65));

                    if (pattern == 0 || pattern == 2) {
                        graphics.drawLine(
                                screenX + 8,
                                screenY + 30,
                                screenX + 10,
                                screenY + 24
                        );
                        graphics.drawLine(
                                screenX + 10,
                                screenY + 30,
                                screenX + 13,
                                screenY + 25
                        );
                    }

                    if (pattern == 1 || pattern == 3) {
                        graphics.drawLine(
                                screenX + 27,
                                screenY + 15,
                                screenX + 29,
                                screenY + 10
                        );
                        graphics.drawLine(
                                screenX + 29,
                                screenY + 15,
                                screenX + 32,
                                screenY + 11
                        );
                    }
                }

                if (world[x][y] == 1) {
                    // Water
                    graphics.setColor(new Color(50, 150, 220));
                    graphics.fillRect(screenX, screenY, TILE_SIZE, TILE_SIZE);

                    graphics.setColor(new Color(120, 210, 240));
                    graphics.drawLine(
                            screenX + 6, screenY + 12,
                            screenX + 28, screenY + 12
                    );
                    graphics.drawLine(
                            screenX + 15, screenY + 28,
                            screenX + 36, screenY + 28
                    );

                } else if (world[x][y] == 2) {
                    // Tree trunk
                    graphics.setColor(new Color(105, 65, 30));
                    graphics.fillRect(screenX + 16, screenY + 20, 9, 18);

                    // Tree leaves
                    graphics.setColor(new Color(20, 110, 40));
                    graphics.fillOval(screenX + 4, screenY + 2, 32, 30);

                    graphics.setColor(new Color(35, 145, 50));
                    graphics.fillOval(screenX + 10, screenY + 5, 22, 20);

                } else if (world[x][y] == 3) {
                    // Chest
                    graphics.setColor(new Color(120, 65, 25));
                    graphics.fillRect(screenX + 7, screenY + 14, 26, 19);

                    graphics.setColor(new Color(180, 105, 35));
                    graphics.fillRect(screenX + 7, screenY + 10, 26, 10);

                    graphics.setColor(Color.YELLOW);
                    graphics.fillRect(screenX + 18, screenY + 19, 5, 6);
                }

                                
            }
        }

        

        graphics.setColor(Color.WHITE);
        

        

        // Draw the player
        int characterX = playerX - cameraX;
        int characterY = playerY - cameraY;

        // Shadow
        graphics.setColor(new Color(60, 100, 50));
        graphics.fillOval(characterX + 3, characterY + 25, 28, 9);

        // Legs
        int legMovement = 0;
        int armMovement = 0;

        if (walkFrame == 1) {
            legMovement = 3;
            armMovement = -2;
        } else if (walkFrame == 3) {
            legMovement = -3;
            armMovement = 2;
        }

        graphics.setColor(new Color(45, 45, 120));
        graphics.fillRect(characterX + 7, characterY + 21 + legMovement, 7, 10);
        graphics.fillRect(characterX + 17, characterY + 21 - legMovement, 7, 10);

        // Arms
        graphics.setColor(new Color(230, 170, 120));
        graphics.fillRect(characterX + 1, characterY + 12 + armMovement, 5, 12);
        graphics.fillRect(characterX + 24, characterY + 12 - armMovement, 5, 12);

        // Body
        graphics.setColor(new Color(40, 100, 200));
        graphics.fillRect(characterX + 5, characterY + 10, 20, 16);

        // Head
        graphics.setColor(new Color(245, 190, 140));
        graphics.fillOval(characterX + 7, characterY, 16, 16);

        // Hair
        graphics.setColor(new Color(80, 45, 25));
        graphics.fillArc(characterX + 7, characterY - 2, 16, 12, 0, 180);

        // Eyes
        graphics.setColor(Color.BLACK);
        graphics.fillRect(characterX + 11, characterY + 7, 2, 2);
        graphics.fillRect(characterX + 18, characterY + 7, 2, 2);

        // Sword
        int centerX = characterX + 15;
int centerY = characterY + 16;

if (hasSword) {
    graphics.setColor(Color.LIGHT_GRAY);
    graphics.drawLine(
            centerX,
            centerY,
            centerX + facingX * 25,
            centerY + facingY * 25
    );

    graphics.setColor(Color.YELLOW);
    graphics.drawLine(
            centerX - facingY * 5,
            centerY + facingX * 5,
            centerX + facingY * 5,
            centerY - facingX * 5
    );
}

if (attacking) {
    graphics.setColor(Color.WHITE);
    graphics.drawLine(
            centerX - facingY * 10 + facingX * 10,
            centerY + facingX * 10 + facingY * 10,
            centerX + facingY * 10 + facingX * 42,
            centerY - facingX * 10 + facingY * 42
    );
}

        // Message
        if (System.currentTimeMillis() < messageUntil
                && !message.isEmpty()) {
            graphics.setColor(Color.WHITE);
            graphics.drawString(message, 20, 25);
        }
    }

    private void attack() {
        if (hasSword && !attacking) {
            attacking = true;
            attackTimer = 12;
        }
    }

    private void updateAttack() {
        if (attacking) {
            attackTimer--;

            if (attackTimer <= 0) {
                attacking = false;
            }
        }
    }
}