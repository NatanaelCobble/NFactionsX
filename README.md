# NFactionsX 1.0.0

Plugin de factions **compétitif** pour PowerNukkitX 3.0.6, intégré à **NVaultX 1.0.0**. Formulaires natifs Bedrock, messages français, permissions par grade, économie et protections territoriales.

## Installation

1. Utiliser PowerNukkitX **3.0.6** et Java **21 minimum**. L'ensemble testé avec ShopUI 2.0.1 utilise **Java 25**, requis par ce JAR ShopUI.
2. Installer `NVaultX-1.0.0.jar` puis `NFactionsX-1.0.0.jar` dans `plugins/`.
3. Installer FrenchEconomy pour rendre disponibles les dépôts/retraits de banque. LuckPerms, PlaceholderAPI et SRegionProtector sont optionnels.
4. Redémarrer. Les fichiers `config.yml`, `messages.yml` et `menus.yml` sont créés dans `plugins/NFactionsX/`.
5. Utiliser **`/f`** pour les menus et **`/f help`** pour les commandes. Console : **`f admin status`**.

Les JAR **API** servent à compiler d'autres plugins ; ne pas les placer dans `plugins/`. Les JAR des fournisseurs tiers restent distribués par leurs auteurs.

Si PNX signale un accès refusé à `ClassLoader.defineClass`, le lancement utilisé pour les tests est :

```text
java --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED -jar powernukkitx.jar
```

## Fonctionnalités

- Factions identifiées par UUID, nom unique, description et message de connexion.
- Membres identifiés par UUID, recrutement ouvert ou invitations expirantes, quatre grades : **recrue, membre, officier et chef**.
- Exclusion, transfert de direction, renommage et dissolution avec confirmation. La dissolution exige une banque vide et aucune transaction en attente.
- Claims par chunk sur les mondes chargés, adjacency configurable et limites liées au power.
- Perte de power à la mort ; régénération pour les joueurs connectés. Une déconnexion en combat peut aussi retirer du power, sans supprimer artificiellement des objets.
- Conquête **en bordure** d'un ennemi dont les claims dépassent le power. Une alliance exige l'accord des deux factions ; ennemis et neutralité sont bilatéraux.
- TNT activée dans les claims, protection hors ligne et trêve jusqu'à une date configurables. Les zones sûres et zones de guerre administratives protègent leurs blocs des explosions.
- Protection de construction, interactions, coffres doubles, déplacements d'inventaire après révocation d'accès, véhicules avec inventaire, seaux, fluides et pistons aux frontières, feu et modifications de blocs par les entités.
- Protection du PvP entre membres/alliés et dans les zones sûres ; projectiles pris en compte. Délai de combat, blocage des téléportations et de commandes configurées.
- Base et warps de faction sur les claims, délai de téléportation, annulation au mouvement ou aux dégâts, revérification des permissions/territoires et de la destination.
- Banque en centimes entiers, dépôts et retraits via NVaultX, journal persistant et réservation des retraits.
- Améliorations d'effectif, de limite de claims et de nombre de warps, payées par la banque. Augmenter la limite de claims n'ajoute pas de power.
- Chat de faction et d'alliance ; espionnage explicite du staff.
- Carte des claims, classement par trésorerie, journal des 100 dernières actions et API de lecture pour d'autres plugins.

## Interface

Accueil en huit rubriques : **vue d'ensemble, membres, territoires, base, warps, banque, diplomatie, gestion**. Le menu de gestion rassemble paramètres, permissions, historique, améliorations et actions sensibles. Listes paginées et confirmations pour les opérations destructrices.

`menus.yml` définit les titres, couleurs, textes des boutons, icônes, ordre des rubriques et taille des pages. `messages.yml` contient les messages et libellés, y compris les noms de grades. Les codes `&b`, `&6`, etc. sont convertis en couleurs Minecraft.

Le client Bedrock rend les formulaires : le plugin ne peut pas imposer un fond CSS, une police, des animations ou la géométrie d'une application Web. Les icônes utilisent les textures du client/pack actif. Si un pack remplace un chemin, modifier l'icône correspondante ou la laisser vide.

## Configuration des règles

| Fichier / section | Réglages |
| --- | --- |
| `limits` | Nombre de factions, membres, claims et warps de base |
| `power` | Valeurs initiale, minimale et maximale, perte à la mort, régénération par minute |
| `claims` | Connexion des claims, conquête et mondes exclus |
| `raids` | TNT, raids hors ligne et fin absolue d'une période de trêve |
| `combat` | PvP réservé aux ennemis, friendly fire, durée du combat, commandes bloquées, pénalité de déconnexion |
| `economy` | Coût du claim, prix initial des améliorations et plafond de banque |
| `upgrades` | Niveaux maximum et gains par niveau |
| `roles` | Grade minimal par action ; le chef peut aussi régler sa faction dans les menus |
| `features` | Activation banque, diplomatie, améliorations, téléportation et chat |
| `integrations` | PlaceholderAPI et vérification des régions SRegionProtector |

Prix et plafonds monétaires sont des **chaînes décimales**, par exemple `"500.00"`. Deux décimales au maximum. Les valeurs sont validées au démarrage et au rechargement. Un rechargement invalide conserve les réglages précédents. Recharger avec **`/f admin reload`**.

Le power des membres est additionné, y compris les valeurs négatives. Capacité de claims = minimum entre le power total arrondi vers le bas et la limite de claims améliorée, avec un minimum de zéro. Les anciens claims ne sont pas effacés quand le power baisse : ils deviennent potentiellement conquérables.

Les explosions sont autorisées selon la politique **du territoire endommagé**. Les blocs hors des territoires conservent le comportement vanilla. La TNT ne porte pas systématiquement une identité de faction exploitable ; les ennemis servent à la diplomatie et à la conquête, tandis que l'autorisation des dégâts TNT est territoriale.

## Compatibilité

| Plugin | Support |
| --- | --- |
| **NVaultX 1.0.0** | Dépendance obligatoire ; fournisseur économique résolu au moment de l'opération |
| **FrenchEconomy 1.0** | Testé via NVaultX : débit/crédit du joueur contre la banque de sa faction |
| **LuckPerms PNX 5.4.0** | Permissions globales `nfactions.*` via PNX ; les grades de faction restent propres à NFactionsX |
| **PlaceholderAPI 1.2.4** | Sept placeholders enregistrés et vérifiés |
| **SRegionProtector 30.2** | Claims refusés dès qu'une région intersecte la colonne du chunk. Échec de vérification = claim refusé |
| **ShopUI 2.0.1 / StatBoard 1.0.0** | Chargés avec NFactionsX pendant les tests. Ils continuent d'utiliser les portefeuilles joueurs FrenchEconomy ; la banque de faction exige un dépôt explicite |
| **Scoreboard / PlaceholderChat** | Consomment les placeholders par leur propre intégration PlaceholderAPI ; affichage en jeu à vérifier selon leurs réglages |
| **WorldManager** | Les claims et warps utilisent les noms des mondes PNX chargés ; aucune création ou gestion parallèle de mondes |
| **NPC / TapToDo** | Peuvent ouvrir les menus avec la commande `/f` exécutée comme joueur ; configuration propre à ces plugins |
| **EssentialsPNX / autres protections** | Les téléportations utilisent l'événement PNX et les listeners n'annulent jamais une annulation existante. Interaction en jeu à valider pour la combinaison installée |

Cette liste distingue les intégrations vérifiées de la compatibilité attendue via les API natives. Aucun chargement de plugins Bukkit/Spigot et aucune modification des JAR tiers.

### Placeholders

```text
%nfactions_name%
%nfactions_role%
%nfactions_power%
%nfactions_claims%
%nfactions_bank%
%nfactions_members%
%nfactions_territory%
```

## Administration

```text
/f admin status
/f admin reload
/f admin zone safe|war|none
/f admin unclaim
/f admin power <joueur> <valeur>
/f admin bypass
/f admin spy
/f admin pending
/f admin resolve <id> success|failed confirm
/f admin disband <faction> confirm
```

Les zones et l'unclaim administratif visent le chunk actuel. Le bypass exige `nfactions.bypass` **et** son activation explicite ; être op n'active pas automatiquement ce mode. `nfactions.admin` contrôle les commandes administratives. Les deux permissions sont op par défaut.

## Banque, incidents et sauvegardes

La banque de faction est distincte du compte joueur. Une préparation est sauvegardée **avant** l'appel à NVaultX. Un retrait réserve d'abord les fonds. Une opération confirmée ne peut pas être appliquée deux fois.

Si le fournisseur renvoie `UNKNOWN`, lève une exception, ou si le serveur s'arrête entre les deux écritures, l'opération reste **PENDING**. La banque, les dépenses de claims/améliorations et la dissolution sont alors bloquées pour cette faction.

1. Consulter `/f admin pending` : identifiant, compte, sens, montant, fournisseur et solde du joueur avant l'appel.
2. Vérifier les comptes et journaux du fournisseur ; tenir compte des autres opérations éventuelles.
3. Si l'opération externe a effectivement abouti : `/f admin resolve <id> success confirm`.
4. Si elle n'a pas eu lieu : `/f admin resolve <id> failed confirm`.

**Ces commandes ne rappellent pas l'économie** : elles finalisent ou libèrent la réservation interne après la vérification humaine. Ne pas deviner le résultat ni répéter un transfert incertain.

NVaultX/FrenchEconomy confirme un solde **en mémoire**, sans garantie de transaction durable commune avec NFactionsX. Le journal réduit le risque des reprises et signale les cas incertains ; il ne constitue pas une transaction distribuée atomique. Sauvegarder ensemble les données des deux plugins, serveur arrêté. Une coupure brutale peut nécessiter une réconciliation avec l'économie.

`state.json` utilise des écritures atomiques sur le même disque et conserve `state.json.bak`. Une erreur de stockage en cours d'exécution bloque les modifications et maintient les dernières protections en mémoire. Un snapshot invalide au démarrage provoque l'arrêt du serveur. Avant toute restauration, arrêter le serveur et conserver les deux fichiers ; ne jamais supprimer les données pour « réparer » une erreur. La restauration d'une sauvegarde ancienne exige aussi une vérification des opérations économiques.

## API développeur

Compiler avec `NFactionsX-API-1.0.0.jar`, sans l'embarquer dans le plugin consommateur. Déclarer `depend: [NFactionsX]` et récupérer le service natif :

```java
var registration = getServer().getServiceManager().getProvider(
        org.nfactionsx.api.FactionsService.class);
if (registration != null) {
    var faction = registration.getProvider().faction(player.getUniqueId());
    // faction est un Optional<FactionView>, copie de lecture.
}
```

Cette API expose faction et territoire en lecture ; les snapshots sont détachés. Les changements de politique et les commandes passent par la boucle principale du serveur. Le moteur sérialise ses mutations et lectures de protection pour supporter les callbacks de niveaux parallèles. Le stockage de cette version est un snapshot JSON local ; dimensionner et mesurer sur le serveur cible avant un déploiement important.

## Compilation et tests

Sous Windows, avec un JDK 21 ou plus récent :

```powershell
./download-dependencies.ps1
./build.ps1 -JavaHome 'C:/chemin/vers/jdk-21'
```

Le script vérifie les dépendances par SHA-256, compile, teste et produit les deux JAR. Aucun serveur ni fournisseur tiers n'est embarqué.

Voir [TESTING.md](TESTING.md) pour les vérifications effectuées et la recette en jeu restante. Le source de la sonde de serveur est fourni dans `src/integration/` ; il n'est pas inclus dans le plugin distribué.

## Sources d'inspiration

[FactionsUUID](https://factions.support/introduction/) pour le power, les claims et la diplomatie ; [SaberFactions](https://github.com/SaberLLC/Saber-Factions) pour les grades, améliorations et outils de gestion. Implémentation originale pour PNX, sans copie de leur code.

Licence MIT. Projet indépendant, sans affiliation avec les auteurs de Vault, FactionsUUID, SaberFactions, PowerNukkitX ou Mojang.
