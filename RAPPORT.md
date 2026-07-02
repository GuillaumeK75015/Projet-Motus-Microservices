# Projet Motus – Rapport de projet

**Master 2 MIAGE SITN Apprentissage 2025-2026 — Projet Applications Web orientées Services (M. Menceur)**

**Binôme :** Fahd Derradji & Guillaume Karaouane

**Lien GitHub :** [à compléter]

---

## 1. Compilation / exécution du projet

### Prérequis
- Java 26 + Maven (ou `./mvnw` fourni dans chaque module)
- Docker Desktop (méthode recommandée) **ou** PostgreSQL 16 en local **ou** MiniKube

### Méthode recommandée — Docker Compose
```bash
docker-compose up --build
```
Cette commande démarre PostgreSQL et les 4 microservices, initialise les 3 bases de données via `init-db.sql`, puis expose l'application sur **http://localhost:8084**.

### Sans Docker
```sql
CREATE DATABASE db_players;
CREATE DATABASE db_history;
CREATE DATABASE db_motus;
```
```bash
cd player-service       && ./mvnw spring-boot:run
cd dictionary-service   && ./mvnw spring-boot:run
cd history-stat-service && ./mvnw spring-boot:run
cd motus-game-service   && ./mvnw spring-boot:run
```
Puis ouvrir **http://localhost:8084**.

### Avec MiniKube (déploiement Kubernetes)
```bash
minikube start
& minikube -p minikube docker-env --shell powershell | Invoke-Expression
docker build -t player-service:latest       ./player-service
docker build -t dictionary-service:latest   ./dictionary-service
docker build -t history-stat-service:latest ./history-stat-service
docker build -t motus-game-service:latest   ./motus-game-service
kubectl apply -f k8s/
minikube service motus-game-service
```
Les manifests (`k8s/`) définissent un `Deployment` + `Service` par microservice, un `Secret` pour les identifiants PostgreSQL, et une `readinessProbe` HTTP par service.

### Comptes de test
- **Joueur** : inscription libre (pseudo + email + mot de passe), ou bouton *« Jouer en invité »* pour une partie immédiate sans compte.
- **Administrateur** : compte créé automatiquement au démarrage de `player-service` (`admin` / `Admin1234!`, configurable dans `application.properties`).

---

## 2. Documentation technique

### 2.1 Architecture — découpage en microservices

```
                     Navigateur ── http://localhost:8084 (UI intégrée)
                                        │ REST (JSON)
                         ┌──────────────▼───────────────┐
                         │      motus-game-service        │  :8084 — logique du jeu
                         │  (fait aussi office de proxy    │  DB : db_motus
                         │   HTTP pour éviter le CORS)     │
                         └───┬────────────┬───────────┬──┘
                    REST      │            │ REST       │ REST
              ┌────────────▼──┐  ┌──────▼───────┐  ┌──▼──────────────────┐
              │ player-service │  │ dictionary-  │  │ history-stat-service │
              │ :8081          │  │ service :8082│  │ :8083                 │
              │ DB : db_players│  │ (en mémoire, │  │ DB : db_history        │
              │ Auth JWT+BCrypt│  │  pas de BD)  │  │ Historique + stats     │
              └────────────────┘  └──────────────┘  └────────────────────────┘
                         │                                    │
                         └──────────────┬─────────────────────┘
                                    PostgreSQL :5432 (3 bases distinctes)
```

Chaque service possède sa **propre base de données** (pattern *database-per-service*) et n'accède jamais directement à celle d'un autre service : toute communication passe par API REST (`RestTemplate`). Le navigateur ne parle qu'à `motus-game-service`, qui joue le rôle de **façade/proxy** vers `player-service` et `history-stat-service` — ce qui évite tout problème CORS côté client.

### 2.2 Diagramme de classes « métier » (vision conceptuelle)

```
   Joueur  1 ────────── *  Partie              Une partie appartient à un joueur.
     │ pseudo (unique)        │ motSecret          Le résultat (gagné/perdu, nb d'essais)
     │ email                  │ nombreTentatives    est historisé après chaque partie.
     │ rôle (Joueur/Admin)     │ gagné / dateFin
                               │
   Partie  1 ────────── *  Tentative           Chaque tentative est une proposition de
                              │ motPropose          mot, avec le retour lettre par lettre
                              │ feedback            (bien placé / mal placé / absent).

   Dictionnaire  1 ──── *  MotSecret           Le mot mystère est tiré aléatoirement
                                                    (7 à 10 lettres) dans le dictionnaire.
```

### 2.3 Diagrammes de classes — entités JPA par service

```
player-service                         history-stat-service
┌─────────────────────────┐            ┌──────────────────────────┐
│ Joueur                  │            │ Partie                   │
├─────────────────────────┤            ├──────────────────────────┤
│ id            : Long    │            │ id               : Long  │
│ pseudo        : String  │  (unique)  │ joueurId         : Long  │──▶ Joueur.id
│ email         : String  │            │ motSecret        : String│
│ password      : String  │  (BCrypt,  │ nombreTentatives : int   │
│               write-only, nullable   │ gagne            : boolean│
│               pour les comptes invité)│ dateFin         : LocalDateTime│
│ role          : String  │  (ROLE_USER└──────────────────────────┘
│               / ROLE_ADMIN)
│ dateInscription: LocalDateTime
└─────────────────────────┘

motus-game-service
┌───────────────────────────┐        ┌───────────────────────────┐
│ Jeu                       │ 1    * │ Tentative                 │
├───────────────────────────┤───────▶├───────────────────────────┤
│ id            : Long      │        │ id           : Long      │
│ joueurId      : Long      │──▶Joueur.id
│ motSecret     : String    │        │ jeu          : Jeu (FK)   │
│ statut        : StatutJeu │        │ numero       : int        │
│  {EN_COURS|GAGNE|PERDU}   │        │ motPropose   : String     │
│ tentativesMax : int = 6   │        │ feedback     : String     │
│ dateDebut     : LocalDateTime      │  (BIEN_PLACE,MAL_PLACE,   │
│ tentatives    : List<Tentative>   │   ABSENT — un par lettre)  │
└───────────────────────────┘        └───────────────────────────┘

dictionary-service : pas d'entité persistante — dictionnaire chargé en mémoire au démarrage
(≈156 000 mots valides de 7 à 10 lettres, filtrés depuis un lexique brut de ≈336 000 formes).
```

### 2.4 API REST (extrait des endpoints principaux)

| Méthode | Endpoint (exposé côté client via le proxy `/api/proxy/...`) | Accès |
|---|---|---|
| POST | `/api/proxy/players` — inscription (mot de passe requis) | public |
| POST | `/api/proxy/players/guest` — partie en invité, sans compte | public |
| POST | `/api/proxy/auth/login` — connexion (joueur ou admin) → JWT | public |
| GET | `/api/proxy/players` — liste des joueurs | **ADMIN** |
| DELETE | `/api/proxy/players/{id}` — suppression joueur (cascade historique) | **ADMIN** |
| POST | `/api/games/start`, `/api/games/{id}/guess` — jouer | authentifié applicatif (joueurId) |
| GET | `/api/proxy/stats/{id}`, `/api/proxy/classement` — stats / classement | public |
| GET | `/api/proxy/search?joueurId=&date=&gagne=` — liste/recherche des parties (sans filtre = toutes) | **ADMIN** |

### 2.5 Choix techniques

- **Spring Boot 4.1.0 / Java 26**, `spring-boot-starter-webmvc` + Spring Data JPA.
- **PostgreSQL** : une base dédiée par microservice (`db_players`, `db_history`, `db_motus`), `dictionary-service` reste sans base (dictionnaire en mémoire).
- **Spring Security + JWT** (ajouté en cours de projet, cf. bilan) :
  - Authentification **stateless** par jeton JWT (HS384), secret partagé entre `player-service` et `history-stat-service`.
  - Mots de passe hashés en **BCrypt** ; aucun mot de passe n'est jamais renvoyé par l'API (`@JsonProperty(access = WRITE_ONLY)`).
  - Deux rôles : `ROLE_USER` (joueur) et `ROLE_ADMIN` (accès à la liste/recherche des parties, gestion des joueurs).
  - **Mode invité** : partie jouable sans inscription (compte éphémère sans mot de passe), pour ne pas alourdir l'accès au jeu.
  - Protection anti-élévation de privilège : le rôle est forcé à `ROLE_USER` côté serveur à l'inscription, quel que soit le contenu envoyé par le client.
- **Conteneurisation** : un `Dockerfile` multi-stage (build Maven puis image d'exécution) par service, orchestré par `docker-compose.yml`.
- **Déploiement Kubernetes** : manifests `Deployment` + `Service` par microservice dans `k8s/`, `Secret` pour les identifiants PostgreSQL, `readinessProbe` HTTP (dirigées vers des endpoints publics pour rester compatibles avec les nouvelles règles de sécurité).
- **Frontend** : une unique page HTML/CSS/JS (sans framework), servie statiquement par `motus-game-service`, qui fait aussi office de proxy vers les deux autres services pour éviter le CORS.
- **Proxy — gestion centralisée des erreurs** : `ProxyController` relayait initialement chaque appel vers `player-service`/`history-stat-service` avec son propre bloc `try/catch` quasi identique (une dizaine de fois) :
  ```java
  try {
      ResponseEntity<String> r = restTemplate.exchange(url, method, new HttpEntity<>(body, headers), String.class);
      return ResponseEntity.status(r.getStatusCode()).body(r.getBody());
  } catch (HttpClientErrorException e) {
      return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
  }
  ```
  Cette logique a été factorisée dans une seule méthode `forward(HttpMethod, url, body, authorization)`, appelée par toutes les méthodes du contrôleur (`login`, `registerPlayer`, `registerGuest`, `getPlayerById`, `getPlayerByPseudo`, `getAllPlayers`, `deletePlayer`, `getStats`, `getClassement`, `searchParties`…). Elle gère en un seul endroit : le transfert du header `Authorization` vers le service interne, les erreurs HTTP du service appelé (4xx/5xx, renvoyées telles quelles au client), et le cas où le service est injoignable (`RestClientException` → `502 Bad Gateway` au lieu d'une exception non gérée). Un correctif futur (ex. gérer un nouveau type d'erreur) ne se fait donc qu'à un seul endroit au lieu d'une dizaine.
- **Tests** : JUnit 5 + Mockito pour les contrôleurs (tests unitaires), tests d'intégration Spring Boot sur `player-service`.

---

## 3. Bilan du projet

**Ce que nous avons aimé** — Découper une application en microservices indépendants, chacun avec sa base de données et sa responsabilité propre, et les faire collaborer par API REST. Voir concrètement l'intérêt du pattern *proxy/façade* (`motus-game-service`) pour donner au frontend un point d'entrée unique sans exposer ni CORSer les autres services.

**Ce que nous avons appris** — La mise en place de l'authentification JWT stateless partagée entre plusieurs services (un même secret, vérifié indépendamment par chaque service qui en a besoin) ; les pièges classiques de Jackson (`@JsonIgnore` bloque aussi la désérialisation, pas seulement la sérialisation — il faut `@JsonProperty(access = WRITE_ONLY)` pour un mot de passe accepté en entrée mais jamais renvoyé) ; l'importance de `@Transactional` pour les méthodes de suppression dérivées de Spring Data JPA (`deleteByXxx`), qui échouent silencieusement en 500/403 sans transaction active ; la configuration Kubernetes (probes, secrets, `Deployment`/`Service`) et son couplage avec les règles de sécurité applicative (une probe qui ping un endpoint devenu admin-only casse le déploiement) ; l'importance de toujours échapper les données utilisateur affichées côté client (pseudo, email) pour éviter les failles XSS.

**Ce que nous avons moins aimé** — Le dictionnaire de mots fourni (`mots.txt`) est un lexique brut de formes fléchies, pas une liste de mots courants : on y trouve beaucoup de conjugaisons rares, ce qui rend le jeu localement plus difficile qu'un Motus « grand public ». C'est un point identifié mais non traité dans cette version (nécessite soit un filtrage lexical, soit une liste de mots plus qualitative).

**Réussites** — Architecture microservices fonctionnelle de bout en bout (inscription, partie, historisation, statistiques, classement, administration) ; sécurisation ajoutée sans casser le parcours joueur existant (mode invité conservé) ; suppression en cascade d'un compte et de son historique à travers deux services indépendants ; déploiement testé aussi bien en local, qu'en Docker Compose et en manifests Kubernetes.

**Difficultés** — Plusieurs bugs subtils découverts en testant réellement chaque fonctionnalité plutôt qu'en se fiant à la seule compilation : une dépendance Jackson manquante en scope *compile* (uniquement apportée en *runtime* par une autre librairie) faisait planter `motus-game-service` au démarrage ; le renommage de certains packages d'auto-configuration Spring Security entre les versions de Spring Boot ; une image Docker reconstruite mais pas redéployée (cache) qui masquait un correctif déjà appliqué dans le code ; une faille XSS stockée détectée en revue de code (pseudo affiché sans échappement dans plusieurs vues) puis corrigée.

---
