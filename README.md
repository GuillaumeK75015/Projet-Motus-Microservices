# Projet Motus – Architecture Microservices
Master 2 MIAGE SITN Apprentissage – 2025/2026

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Browser  →  http://localhost:8080  (UI Motus intégrée)     │
└──────────────────────┬──────────────────────────────────────┘
                       │ REST
          ┌────────────▼─────────────┐
          │       api-gateway         │  :8080  (routage + résilience,
          │  (façade réseau, sans BD) │           pas de logique métier)
          └──┬───────────┬─────────┬─┘
             │           │         │  REST
    REST      │    REST   │   REST  │
┌─────────▼──┐  ┌────▼────────┐  ┌▼─────────────────┐   ┌─────────────────────┐
│player-svc  │  │dictionary   │  │history-stat-service│  │motus-game-service    │
│:8081       │  │-service     │  │:8083                │  │:8084 (DB: db_motus) │
│db_players  │  │:8082        │  │db_history           │  │Logique du jeu       │
│Joueurs     │  │Mots (RAM)   │  │Parties + Stats      │  └─────────────────────┘
└────────────┘  └─────────────┘  └─────────────────────┘
          │           │                    │
          └───────────┴────────────────────┘
                         PostgreSQL :5432

  Prometheus :9090  ──(scrape /actuator/prometheus)──  Grafana :3000
```

## Prérequis

- Java 26 + Maven (ou utiliser `./mvnw`)
- PostgreSQL 16 (local) **ou** Docker Desktop **ou** MiniKube

---

## Option 1 – Lancement local (sans Docker)

### 1. Créer les bases de données PostgreSQL

```sql
CREATE DATABASE db_players;
CREATE DATABASE db_history;
CREATE DATABASE db_motus;
```

### 2. Démarrer chaque service (5 terminaux)

```bash
cd player-service       && ./mvnw spring-boot:run
cd dictionary-service   && ./mvnw spring-boot:run
cd history-stat-service && ./mvnw spring-boot:run
cd motus-game-service   && ./mvnw spring-boot:run
cd api-gateway          && ./mvnw spring-boot:run
```

### 3. Jouer

Ouvrir **http://localhost:8080** dans le navigateur.

---

## Option 2 – Docker Compose (recommandé)

```bash
docker-compose up --build
```

- Lance PostgreSQL + les 4 services métier + l'API Gateway + Prometheus/Grafana automatiquement
- Les bases de données sont créées via `init-db.sql`
- Interface : **http://localhost:8080** (Prometheus : `:9090`, Grafana : `:3000`)

Arrêter proprement :
```bash
docker-compose down -v
```

---

## Option 3 – MiniKube (Kubernetes local)

### Prérequis supplémentaires

- [MiniKube](https://minikube.sigs.k8s.io/docs/start/)
- [kubectl](https://kubernetes.io/docs/tasks/tools/)

### Étapes

```bash
# 1. Démarrer MiniKube
minikube start --cpus=4 --memory=6144mb --driver=docker

# 2. Pointer Docker vers le daemon MiniKube (construire les images dedans)
eval $(minikube docker-env)          # Linux/Mac
& minikube -p minikube docker-env --shell powershell | Invoke-Expression  # Windows PowerShell

# 3. Build des images dans MiniKube
docker build -t player-service:latest       ./player-service
docker build -t dictionary-service:latest   ./dictionary-service
docker build -t history-stat-service:latest ./history-stat-service
docker build -t motus-game-service:latest   ./motus-game-service
docker build -t api-gateway:latest          ./api-gateway

# 4. Déployer
kubectl apply -f k8s/postgres.yaml
kubectl apply -f k8s/player-service.yaml
kubectl apply -f k8s/dictionary-service.yaml
kubectl apply -f k8s/history-stat-service.yaml
kubectl apply -f k8s/motus-game-service.yaml
kubectl apply -f k8s/api-gateway.yaml

# 5. Attendre que tout soit prêt (rend la main automatiquement, pas besoin de Ctrl+C)
kubectl wait --for=condition=ready pod --all --timeout=180s

# 6. Obtenir l'URL de l'interface
minikube service api-gateway --url
```

Supprimer le déploiement :
```bash
kubectl delete -f k8s/
minikube stop
```

---

## API REST – Résumé

### player-service (:8081)
| Méthode | URL | Description |
|---|---|---|
| POST | `/api/players` | Créer un joueur |
| GET | `/api/players/{id}` | Joueur par ID |
| GET | `/api/players/pseudo/{pseudo}` | Joueur par pseudo |
| GET | `/api/players` | Tous les joueurs |
| DELETE | `/api/players/{id}` | Supprimer |

### dictionary-service (:8082)
| Méthode | URL | Description |
|---|---|---|
| GET | `/api/dictionary/random` | Mot aléatoire |
| GET | `/api/dictionary/exists/{mot}` | Vérifier existence |
| GET | `/api/dictionary` | Tous les mots |
| POST | `/api/dictionary` | Ajouter un mot |
| DELETE | `/api/dictionary/{mot}` | Supprimer un mot |

### history-stat-service (:8083)
| Méthode | URL | Description |
|---|---|---|
| POST | `/api/history` | Enregistrer une partie |
| GET | `/api/history/player/{id}` | Historique d'un joueur |
| GET | `/api/history/stats/{id}` | Stats d'un joueur |
| GET | `/api/history/classement` | Classement global |
| GET | `/api/history/search?joueurId=&date=&gagne=` | Recherche admin |
| GET | `/api/history` | Toutes les parties |

### motus-game-service (:8084)
| Méthode | URL | Description |
|---|---|---|
| POST | `/api/games/start` | Démarrer une partie |
| POST | `/api/games/{id}/guess` | Proposer un mot |
| GET | `/api/games/{id}` | État d'une partie |
| GET | `/api/games/player/{id}` | Parties d'un joueur |
| GET | `/api/games` | Toutes les parties |

### api-gateway (:8080) — point d'entrée unique côté navigateur
Route `/api/proxy/**` vers player-service/history-stat-service et `/api/games/**` vers motus-game-service ; sert aussi le frontend statique. Expose également `/actuator/health`, `/actuator/prometheus`.

---

## Structure du projet

```
Projet-Motus-Microservices/
├── player-service/          # Gestion des joueurs        (port 8081)
├── dictionary-service/      # Dictionnaire de mots       (port 8082)
├── history-stat-service/    # Historique & statistiques  (port 8083)
├── motus-game-service/      # Logique du jeu             (port 8084)
├── api-gateway/             # Point d'entrée unique + UI (port 8080)
│   └── src/main/resources/static/index.html  ← interface web
├── observability/           # Config Prometheus + Grafana
├── .github/workflows/       # Pipeline CI (GitHub Actions)
├── docker-compose.yml
├── init-db.sql
└── k8s/                     # Manifests Kubernetes
```
