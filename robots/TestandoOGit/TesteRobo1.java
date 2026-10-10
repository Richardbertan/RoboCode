package TestandoOGit;

import robocode.*;
import robocode.util.Utils;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;

public class TesteRobo1 extends AdvancedRobot {

     private final Map<String, Point2D.Double> enemyPositions = new HashMap<>();

    // Multiplicador base para os inimigos
    private static final double ENEMY_REPULSION_MULTIPLIER = 50000.0;
    
    // Distância de segurança da parede (5% da menor dimensão do mapa)
    private double wallSafeDistance;
    // Força da parede adaptada ao tamanho do terreno
    private double wallRepulsionMultiplier;

    @Override
    public void run() {
        setAdjustRadarForGunTurn(true);
        setAdjustGunForRobotTurn(true);

        // --- ADAPTABILIDADE AO TERRENO ---
        // Calcula a margem de segurança baseada no tamanho real do mapa
        double minDimension = Math.min(getBattleFieldWidth(), getBattleFieldHeight());
        this.wallSafeDistance = minDimension * 0.06; // 6% do menor eixo
        
        // Escala a força da parede proporcionalmente à área do mapa
        this.wallRepulsionMultiplier = (getBattleFieldWidth() * getBattleFieldHeight()) * 0.15;

        while (true) {
            setTurnRadarRight(Double.POSITIVE_INFINITY);
            
            double[] forceVector = calculateAntiGravityForces();
            goTo(getX() + forceVector[0], getY() + forceVector[1]);
            
            execute();
        }
    }

    private double[] calculateAntiGravityForces() {
    double xForce = 0;
    double yForce = 0;
    double myX = getX();
    double myY = getY();

    // 1. Força dos Inimigos (Repulsão)
    for (Point2D.Double enemyPos : enemyPositions.values()) {
        double distance = Point2D.distance(myX, myY, enemyPos.x, enemyPos.y);
        if (distance > 0) {
            double force = ENEMY_REPULSION_MULTIPLIER / (distance * distance);
            double angle = Math.atan2(enemyPos.x - myX, enemyPos.y - myY);
            xForce -= force * Math.sin(angle);
            yForce -= force * Math.cos(angle);
        }
    }

    // 2. Força Suave das Paredes (Quadrática em vez de Cúbica para não congelar)
    // Se o robô chegar muito perto, a força aumenta, mas permite que ele ande de lado
    xForce += (wallRepulsionMultiplier / 2) / Math.pow(Math.max(wallSafeDistance, myX), 2);
    xForce -= (wallRepulsionMultiplier / 2) / Math.pow(Math.max(wallSafeDistance, getBattleFieldWidth() - myX), 2);

    yForce += (wallRepulsionMultiplier / 2) / Math.pow(Math.max(wallSafeDistance, myY), 2);
    yForce -= (wallRepulsionMultiplier / 2) / Math.pow(Math.max(wallSafeDistance, getBattleFieldHeight() - myY), 2);

    // 3. NOVO: Força de Gravidade do Centro (Atração Suave)
    // Isso puxa o robô de volta para o meio do mapa se as paredes e inimigos o isolarem nos cantos
    double centerX = getBattleFieldWidth() / 2.0;
    double centerY = getBattleFieldHeight() / 2.0;
    double distanceToCenter = Point2D.distance(myX, myY, centerX, centerY);
    
    if (distanceToCenter > 0) {
        // Uma força constante e leve empurrando para o centro
        double centerPull = 15.0; 
        double angleToCenter = Math.atan2(centerX - myX, centerY - myY);
        xForce += centerPull * Math.sin(angleToCenter);
        yForce += centerPull * Math.cos(angleToCenter);
    }

    return new double[]{xForce, yForce};
}

    private void goTo(double targetX, double targetY) {
        double myX = getX();
        double myY = getY();
        double distance = Point2D.distance(myX, myY, targetX, targetY);
        double angle = Math.toDegrees(Math.atan2(targetX - myX, targetY - myY));
        double turnAngle = Utils.normalRelativeAngleDegrees(angle - getHeading());

        int direction = 1;
        if (Math.abs(turnAngle) > 90) {
            turnAngle = turnAngle > 0 ? turnAngle - 180 : turnAngle + 180;
            direction = -1;
        }

        setTurnRight(turnAngle);
        setAhead(distance * direction);
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        // 1. Atualiza a posição para o movimento
        double angleToEnemy = getHeadingRadians() + e.getBearingRadians();
        double enemyX = getX() + e.getDistance() * Math.sin(angleToEnemy);
        double enemyY = getY() + e.getDistance() * Math.cos(angleToEnemy);
        enemyPositions.put(e.getName(), new Point2D.Double(enemyX, enemyY));

        // 2. SISTEMA DE TIRO (Mira Linear Simples)
        // Só atira se a arma estiver pronta (cooldown = 0)
        if (getGunHeat() == 0) {
            // Calcula o poder do tiro com base na distância (mais perto = tiro mais forte)
            double firePower = Math.min(500 / e.getDistance(), 3.0);
            
            // Descobre para onde o inimigo está indo
            double enemyHeading = e.getHeadingRadians();
            double enemyVelocity = e.getVelocity();
            
            // Velocidade da bala depende do poder do tiro
            double bulletSpeed = 20 - 3 * firePower; 
            double travelTime = e.getDistance() / bulletSpeed;

            // Previsão de onde o inimigo estará quando a bala chegar lá
            double predictedX = enemyX + Math.sin(enemyHeading) * enemyVelocity * travelTime;
            double predictedY = enemyY + Math.cos(enemyHeading) * enemyVelocity * travelTime;

            // Mira e atira no ponto previsto
            double absoluteAngleToTarget = Math.atan2(predictedX - getX(), predictedY - getY());
            double gunTurnAngle = Utils.normalRelativeAngle(absoluteAngleToTarget - getGunHeadingRadians());
            
            setTurnGunRightRadians(gunTurnAngle);
            setFire(firePower);
        }
    }

    @Override
    public void onRobotDeath(RobotDeathEvent e) {
        enemyPositions.remove(e.getName());
    }
}