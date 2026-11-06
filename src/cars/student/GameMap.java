package cars.student;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.List;

import static cars.engine.Vector2.vec2;

/**
 * Mapa de testes: obstáculos circulares e um caminho fechado.
 * Coordenadas relativas ao centro da janela (y cresce para baixo).
 */
public class GameMap {
    private final List<Obstacle> obstacles = List.of(
        new Obstacle(vec2(0, -200), 35),    // em cima do caminho, para a combinação "Patrulha"
        new Obstacle(vec2(-150, 20), 50),
        new Obstacle(vec2(160, 40), 45),
        new Obstacle(vec2(-20, 110), 35)
    );

    // Octógono com os cantos cortados, para o carro conseguir fazer as curvas
    private final Path path = new Path(List.of(
        vec2(-250, -200),
        vec2(250, -200),
        vec2(350, -100),
        vec2(350, 100),
        vec2(250, 200),
        vec2(-250, 200),
        vec2(-350, 100),
        vec2(-350, -100)
    ), 25);

    public List<Obstacle> getObstacles() { return obstacles; }
    public Path getPath() { return path; }

    public void draw(Graphics2D g) {
        final var g2 = (Graphics2D) g.create();

        // Faixa do caminho (largura total = 2 * radius)
        g2.setColor(new Color(120, 180, 120, 90));
        g2.setStroke(new BasicStroke((float) (path.radius() * 2),
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        drawPathLines(g2);

        // Linha central
        g2.setColor(new Color(60, 120, 60));
        g2.setStroke(new BasicStroke(1f));
        drawPathLines(g2);

        // Obstáculos
        for (final var o : obstacles) {
            final var shape = new Ellipse2D.Double(
                o.center().x - o.radius(), o.center().y - o.radius(),
                o.radius() * 2, o.radius() * 2);
            g2.setColor(new Color(150, 80, 60));
            g2.fill(shape);
            g2.setColor(Color.DARK_GRAY);
            g2.draw(shape);
        }

        g2.dispose();
    }

    private void drawPathLines(Graphics2D g) {
        for (int i = 0; i < path.segments(); i++) {
            final var a = path.start(i);
            final var b = path.end(i);
            g.draw(new Line2D.Double(a.x, a.y, b.x, b.y));
        }
    }
}
