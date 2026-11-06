package cars.student;

import cars.engine.Car;
import cars.engine.Vector2;
import cars.engine.World;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;

import static cars.engine.Vector2.*;
import java.util.concurrent.ThreadLocalRandom;

public class StudentCar extends Car {
    public StudentCar() {
        super(settings ->
          settings
            .color(Color.BLUE)
            .randomOrientation()
            .maxSpeed(200)
            .maxForce(400)
        );
    }

    // ---------------------------------------------------------------
    // Modos (trocados pelas teclas 1 a 8 no Window)
    // ---------------------------------------------------------------
    private static final String[] NOMES_MODOS = {
        "1 - Seek (mouse)",
        "2 - Flee (mouse)",
        "3 - Arrive (clique)",
        "4 - Wander",
        "5 - Obstacle Avoidance",
        "6 - Path Following",
        "7 - Combinação Patrulha: Avoidance + Path Following",
        "8 - Combinação Presa: Avoidance + Flee + Wander"
    };

    // volatile porque o teclado roda em outra thread, diferente do game loop
    private static volatile int modo = 1;

    public static void setModo(final int novoModo) {
        if (novoModo >= 1 && novoModo <= NOMES_MODOS.length) {
            modo = novoModo;
        }
    }

    public static String getNomeModo() {
        return NOMES_MODOS[modo - 1];
    }

    // ---------------------------------------------------------------
    // Parâmetros
    // ---------------------------------------------------------------
    private static final double FLEE_AREA = 200;          // raio de pânico do Flee
    private static final double ARRIVE_AREA = 150;        // raio de frenagem do Arrive

    private static final double WANDER_DISTANCE = 100;
    private static final double WANDER_RADIUS = 50;
    private static final double WANDER_ANGULO = 3;

    private static final double MEIA_LARGURA_CARRO = 13;  // o carro desenhado tem ~26 px de largura
    private static final double DETECCAO_MIN = 40;        // alcance mínimo da caixa de detecção
    private static final double DETECCAO_TEMPO = 0.6;     // alcance extra = velocidade * esse tempo

    private static final double PREVISAO_CAMINHO = 0.3;   // segundos à frente para prever a posição
    private static final double AVANCO_CAMINHO = 50;      // distância do alvo à frente no caminho
    private static final double GANHO_CAMINHO = 3;        // multiplica a força do Seek no caminho

    // Pesos das combinações
    private static final double PESO_PATRULHA_DESVIO = 3;
    private static final double PESO_PATRULHA_CAMINHO = 1;
    private static final double PESO_PRESA_DESVIO = 3;
    private static final double PESO_PRESA_FUGA = 2;
    private static final double PESO_PRESA_WANDER = 1;

    // ---------------------------------------------------------------
    // Estado entre quadros
    // ---------------------------------------------------------------
    private double wanderAngle = 0;
    private int segmentoAtual = -1;   // -1 = ainda não sabe em qual segmento do caminho está
    private int ultimoModo = -1;

    // Informações guardadas só para o desenho de debug
    private Vector2 debugCentroWander = null;
    private Vector2 debugAlvoWander = null;
    private double debugAlcance = 0;
    private Vector2 debugFuturo = null;
    private Vector2 debugProjecao = null;
    private Vector2 debugAlvoCaminho = null;

    @Override
    public Vector2 calculateSteering(final World world) {
        final int modoAtual = modo;
        if (modoAtual != ultimoModo) {
            segmentoAtual = -1;   // ao entrar no Path Following, procura de novo o segmento mais próximo
            ultimoModo = modoAtual;
        }
        limparDebug();

        final var mouse = world.getMousePos();
        final var clique = world.getClickPos();

        return switch (modoAtual) {
            case 1 -> mouse == null ? vec2() : seek(mouse);
            case 2 -> mouse == null ? vec2() : flee(mouse, FLEE_AREA);
            case 3 -> clique == null ? vec2() : arrive(clique, ARRIVE_AREA);
            case 4 -> wander(world.getSecs());
            case 5 -> evitarObstaculosAndandoReto();
            case 6 -> seguirCaminho();
            case 7 -> patrulha();
            case 8 -> presa(mouse, world.getSecs());
            default -> vec2();
        };
    }

    // ---------------------------------------------------------------
    // Comportamentos simples
    // ---------------------------------------------------------------
    private Vector2 seek(final Vector2 target) {
        final var direcao = subtract(target, getPosition());
        final var direcaoDesejada = multiply(normalize(direcao), getMaxSpeed());
        return subtract(direcaoDesejada, getVelocity());
    }

    private Vector2 flee(final Vector2 ameaca, final double area) {
        final var distancia = subtract(getPosition(), ameaca);
        if (distancia.size() > area) {
            return vec2();
        }
        final var direcaoDesejada = multiply(normalize(distancia), getMaxSpeed());
        return subtract(direcaoDesejada, getVelocity());
    }

    private Vector2 arrive(final Vector2 target, final double areaReducao) {
        final var direcao = subtract(target, getPosition());
        final var velocidade = Math.min(
                getMaxSpeed(),
                getMaxSpeed() * direcao.size() / areaReducao
        );
        final var direcaoDesejada = multiply(normalize(direcao), velocidade);
        // Ganho: sem ele o carro corrige a velocidade devagar demais, passa do alvo e oscila
        final var ganho = 4 * getMaxSpeed() / areaReducao;
        return multiply(subtract(direcaoDesejada, getVelocity()), ganho);
    }

    private Vector2 wander(final double secs) {
        final var random = ThreadLocalRandom.current().nextDouble(-1, 1);
        // raiz de secs: assim o resultado não depende do FPS
        wanderAngle += random * WANDER_ANGULO * Math.sqrt(secs);

        final var direcao = getDirection();
        final var centro = add(getPosition(), multiply(direcao, WANDER_DISTANCE));
        final var offset = multiply(rotate(direcao, wanderAngle), WANDER_RADIUS);
        final var alvo = add(centro, offset);

        debugCentroWander = centro;
        debugAlvoWander = alvo;
        return seek(alvo);
    }

    // ---------------------------------------------------------------
    // Obstacle Avoidance
    // ---------------------------------------------------------------

    /**
     * Procura o obstáculo mais próximo dentro de uma "caixa" à frente do carro
     * e devolve uma força lateral para o lado oposto, mais uma pequena frenagem.
     */
    private Vector2 evitarObstaculos() {
        final var direcao = getDirection();
        final var lateral = perp(direcao);
        final var alcance = DETECCAO_MIN + getSpeed() * DETECCAO_TEMPO;
        debugAlcance = alcance;

        Obstacle maisProximo = null;
        double frenteMaisProximo = Double.MAX_VALUE;
        double ladoMaisProximo = 0;

        for (final var obstaculo : Setup.MAP.getObstacles()) {
            // posição do obstáculo no "referencial" do carro
            final var local = subtract(obstaculo.center(), getPosition());
            final var frente = local.dot(direcao);   // quanto está à frente
            final var lado = local.dot(lateral);     // quanto está para o lado

            final var raioExpandido = obstaculo.radius() + MEIA_LARGURA_CARRO;
            if (frente < -obstaculo.radius() || frente > alcance + obstaculo.radius()) {
                continue;   // atrás do carro ou longe demais
            }
            if (Math.abs(lado) > raioExpandido) {
                continue;   // fora da largura da caixa: o carro passa sem bater
            }
            if (frente < frenteMaisProximo) {
                maisProximo = obstaculo;
                frenteMaisProximo = frente;
                ladoMaisProximo = lado;
            }
        }

        if (maisProximo == null) {
            return vec2();
        }

        // Empurra para o lado oposto ao obstáculo. Se ele estiver bem no centro (lado == 0), escolhe um lado fixo.
        final double sinal = ladoMaisProximo > 0 ? -1 : 1;
        final var forcaLateral = multiply(lateral, sinal * getMaxForce());

        // Quanto mais perto, mais freia (0 no fim da caixa, 1 encostado)
        final var proximidade = 1 - Math.max(0, frenteMaisProximo) / (alcance + maisProximo.radius());
        final var frenagem = multiply(direcao, -0.5 * getMaxForce() * proximidade);

        return add(forcaLateral, frenagem);
    }

    /** Modo 5: o carro anda reto na velocidade máxima e só desvia dos obstáculos. */
    private Vector2 evitarObstaculosAndandoReto() {
        final var andarReto = subtract(multiply(getDirection(), getMaxSpeed()), getVelocity());
        var total = vec2();
        total = acumular(total, evitarObstaculos(), 1);
        total = acumular(total, andarReto, 1);
        return total;
    }

    // ---------------------------------------------------------------
    // Path Following
    // ---------------------------------------------------------------

    /**
     * Versão de Reynolds: prevê a posição futura, projeta no caminho
     * e faz Seek em um ponto um pouco à frente da projeção.
     */
    private Vector2 seguirCaminho() {
        final var caminho = Setup.MAP.getPath();
        final var futuro = add(getPosition(), multiply(getVelocity(), PREVISAO_CAMINHO));

        if (segmentoAtual < 0) {
            segmentoAtual = segmentoMaisProximo(caminho, futuro);
        }

        // Só olha o segmento atual e o próximo, para não "pular" de trecho
        final int proximo = (segmentoAtual + 1) % caminho.segments();
        final var projAtual = projetar(futuro, caminho.start(segmentoAtual), caminho.end(segmentoAtual));
        final var projProximo = projetar(futuro, caminho.start(proximo), caminho.end(proximo));
        if (Vector2.distance(futuro, projProximo) < Vector2.distance(futuro, projAtual)) {
            segmentoAtual = proximo;
        }

        final var projecao = segmentoAtual == proximo ? projProximo : projAtual;
        final var alvo = pontoAFrente(caminho, segmentoAtual, projecao, AVANCO_CAMINHO);

        debugFuturo = futuro;
        debugProjecao = projecao;
        debugAlvoCaminho = alvo;

        // Mesmo motivo do ganho do Arrive: sem ele o carro faz as curvas atrasado e sai da faixa
        return multiply(seek(alvo), GANHO_CAMINHO);
    }

    /** Projeta o ponto p no segmento ab (o resultado fica sempre dentro do segmento). */
    private static Vector2 projetar(final Vector2 p, final Vector2 a, final Vector2 b) {
        final var ab = subtract(b, a);
        final var t = Math.clamp(subtract(p, a).dot(ab) / ab.sizeSqr(), 0.0, 1.0);
        return add(a, multiply(ab, t));
    }

    private static int segmentoMaisProximo(final Path caminho, final Vector2 p) {
        int melhor = 0;
        double menorDistancia = Double.MAX_VALUE;
        for (int i = 0; i < caminho.segments(); i++) {
            final var d = Vector2.distance(p, projetar(p, caminho.start(i), caminho.end(i)));
            if (d < menorDistancia) {
                menorDistancia = d;
                melhor = i;
            }
        }
        return melhor;
    }

    /** Anda "avanco" pixels pelo caminho a partir da projeção, passando para o próximo segmento se precisar. */
    private static Vector2 pontoAFrente(final Path caminho, int segmento, final Vector2 inicio, double avanco) {
        var ponto = inicio;
        for (int i = 0; i < caminho.segments(); i++) {
            final var fim = caminho.end(segmento);
            final var resto = Vector2.distance(ponto, fim);
            if (resto >= avanco) {
                return add(ponto, multiply(normalize(subtract(fim, ponto)), avanco));
            }
            avanco -= resto;
            ponto = fim;
            segmento++;
        }
        return ponto;
    }

    // ---------------------------------------------------------------
    // Combinações
    // ---------------------------------------------------------------

    /**
     * Soma com prioridade: cada força (já multiplicada pelo peso) só usa
     * o que ainda sobra de maxForce. As mais importantes vêm primeiro.
     */
    private Vector2 acumular(final Vector2 total, final Vector2 forca, final double peso) {
        final var restante = getMaxForce() - total.size();
        if (restante <= 0) {
            return total;
        }
        return add(total, truncate(multiply(forca, peso), restante));
    }

    /** Modo 7: segue o caminho e desvia do obstáculo que está em cima dele. */
    private Vector2 patrulha() {
        var total = vec2();
        total = acumular(total, evitarObstaculos(), PESO_PATRULHA_DESVIO);
        total = acumular(total, seguirCaminho(), PESO_PATRULHA_CAMINHO);
        return total;
    }

    /** Modo 8: passeia sem rumo, foge do mouse quando ele chega perto e nunca bate nos obstáculos. */
    private Vector2 presa(final Vector2 mouse, final double secs) {
        var total = vec2();
        total = acumular(total, evitarObstaculos(), PESO_PRESA_DESVIO);
        if (mouse != null) {
            total = acumular(total, flee(mouse, FLEE_AREA), PESO_PRESA_FUGA);
        }
        total = acumular(total, wander(secs), PESO_PRESA_WANDER);
        return total;
    }

    // ---------------------------------------------------------------
    // Desenho de debug (chamado pelo Window quando o debug está ligado)
    // ---------------------------------------------------------------
    private void limparDebug() {
        debugCentroWander = null;
        debugAlvoWander = null;
        debugAlcance = 0;
        debugFuturo = null;
        debugProjecao = null;
        debugAlvoCaminho = null;
    }

    public void desenharDebug(final Graphics2D g) {
        final var g2 = (Graphics2D) g.create();
        g2.setStroke(new BasicStroke(1f));

        // Caixa de detecção do Obstacle Avoidance
        if (debugAlcance > 0) {
            final var pos = getPosition();
            final var lado = multiply(perp(getDirection()), MEIA_LARGURA_CARRO);
            final var fim = add(pos, multiply(getDirection(), debugAlcance));
            final var caixa = new Path2D.Double();
            caixa.moveTo(pos.x + lado.x, pos.y + lado.y);
            caixa.lineTo(fim.x + lado.x, fim.y + lado.y);
            caixa.lineTo(fim.x - lado.x, fim.y - lado.y);
            caixa.lineTo(pos.x - lado.x, pos.y - lado.y);
            caixa.closePath();
            g2.setColor(new Color(200, 0, 0, 120));
            g2.draw(caixa);
        }

        // Círculo e alvo do Wander
        if (debugCentroWander != null) {
            g2.setColor(new Color(0, 130, 0, 150));
            g2.draw(new Ellipse2D.Double(
                debugCentroWander.x - WANDER_RADIUS, debugCentroWander.y - WANDER_RADIUS,
                WANDER_RADIUS * 2, WANDER_RADIUS * 2));
            g2.fill(new Ellipse2D.Double(debugAlvoWander.x - 3, debugAlvoWander.y - 3, 6, 6));
        }

        // Posição futura, projeção e alvo do Path Following
        if (debugFuturo != null) {
            g2.setColor(new Color(120, 0, 160, 170));
            g2.draw(new Line2D.Double(debugFuturo.x, debugFuturo.y, debugProjecao.x, debugProjecao.y));
            g2.fill(new Ellipse2D.Double(debugFuturo.x - 3, debugFuturo.y - 3, 6, 6));
            g2.fill(new Ellipse2D.Double(debugAlvoCaminho.x - 4, debugAlvoCaminho.y - 4, 8, 8));
        }

        // Raio de pânico do Flee
        if (modo == 2 || modo == 8) {
            final var pos = getPosition();
            g2.setColor(new Color(0, 0, 0, 40));
            g2.draw(new Ellipse2D.Double(pos.x - FLEE_AREA, pos.y - FLEE_AREA, FLEE_AREA * 2, FLEE_AREA * 2));
        }

        g2.dispose();
    }
}
