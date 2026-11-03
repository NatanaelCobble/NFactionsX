# Vérifications NFactionsX 1.0.0

Effectuées le 3 novembre 2026. Code, configuration, tests et documentation réalisés avec l'assistance de Codex (GPT-6).

## Tests du cœur : 96 assertions

Exécutés sous Java 21 contre le JAR PNX 3.0.6 et NVaultX-API 1.0.0 :

- identité par UUID, noms uniques, invitations, adhésion et hiérarchie des grades ;
- interdiction d'escalade de privilèges et protection des coffres/constructions selon le grade ;
- consentement bilatéral des alliances et mise à jour des ennemis ;
- PvP allié, de faction et zones sûres ;
- coordonnées négatives, claims adjacents, conquête d'un ennemi affaibli, trêve et protection hors ligne ;
- suppression des bases/warps après libération ou conquête de leur chunk ;
- power à la mort et régénération uniquement en ligne ;
- parsing monétaire exact, journal bancaire, réservation des fonds, redémarrage avec opération en attente, résolution unique et refus des dépenses concurrentes ;
- améliorations, transfert de direction, exclusion et dissolution ;
- snapshots détachés, fichiers invalides, restauration requise et passage en lecture seule après erreur d'écriture ;
- lecture des quatre YAML et sérialisation des formulaires Bedrock simples, personnalisés et de confirmation.

## Tests sur serveur local : 26 assertions

Serveur PNX 3.0.6 lié à 127.0.0.1, Java 25, arrêté à la fin du test. Plugins chargés ensemble :

| Plugin | Version |
| --- | --- |
| NVaultX | 1.0.0 |
| FrenchEconomy | 1.0 |
| LuckPerms PNX | 5.4.0 |
| ShopUI | 2.0.1 |
| StatBoard | 1.0.0 |
| PlaceholderAPI | 1.2.4 |
| SRegionProtector | 30.2 |
| NFactionsX | 1.0.0 |

La sonde a utilisé les objets et API réellement chargés :

- création d'une faction de test, deux claims et lecture par le service natif FactionsService ;
- débit de 50 du portefeuille FrenchEconomy et crédit de la banque de faction, puis retrait inverse de 20, avec conservation des montants ;
- enregistrement des sept placeholders ;
- création d'une vraie région SRegionProtector, détection du chevauchement et absence de faux positif dans un autre chunk ;
- appels directs aux listeners de fluides et d'explosion avec les blocs du monde de test : protection de la frontière, fluides internes autorisés, retrait des blocs d'une zone sûre de la liste d'explosion, protection de claims hors ligne ;
- commandes de statut et de rechargement, activation et arrêt propres.

Ces appels aux événements ne reproduisent pas toutes les étapes d'un combat ou d'une transaction d'inventaire avec un client. Les objets de menus ont été sérialisés, sans rendu sur un client Bedrock.

## Recette en jeu à effectuer

1. Avec au moins trois joueurs, créer deux factions, inviter, accepter, promouvoir et transférer la direction. Tester un ancien menu encore ouvert après exclusion/changement de grade.
2. Vérifier que les permissions de coffre s'appliquent aux deux moitiés d'un coffre double, même si l'accès est retiré pendant qu'il est ouvert. Tester les inventaires mobiles et les menus Invsee/InvMenu utilisés par le serveur.
3. Tester les claims négatifs et dans plusieurs mondes, ainsi que la superposition interdite avec SRegionProtector.
4. Vérifier les explosions réelles de TNT/creepers aux frontières, en zone sûre, en guerre, avec protection hors ligne et pendant la trêve. Tester aussi pistons collants, entonnoirs, eau/lave, feu et blocs déplacés par des entités.
5. Tester armes, flèches et projectiles entre membres, alliés, ennemis et joueurs sans faction, avec attaquant ou cible en zone sûre.
6. Tester `/f home` et les warps, le mouvement, les dégâts, les déconnexions en combat, les mondes déchargés, les destinations obstruées et les annulations provenant d'autres plugins.
7. Vérifier les formulaires sur mobile et Windows : lisibilité, icônes, pagination, boutons de retour et doubles validations.
8. Vérifier Scoreboard/PlaceholderChat avec les placeholders et les rôles LuckPerms globaux.
9. Sauvegarder et redémarrer proprement les données factions et économie. Sur une copie de test uniquement, simuler une interruption pendant un transfert et suivre la procédure PENDING du README.

Ces essais en jeu restent à réaliser avant l'ouverture publique. Aucune connexion de client Bedrock n'a été simulée ou présentée comme un test en jeu.

## Serveur local dédié : 63 vérifications supplémentaires

26 assertions d'intégration et 37 assertions avec quatre joueurs simulés passent sur le serveur PowerNukkitX 3.0.6 avec les plugins tiers chargés. Avec les 96 assertions du cœur, le total est de 159. Les commandes enregistrées, callbacks de formulaires, grades, coffres doubles, banque, diplomatie et PvP ont été exercés sur le serveur. Aucun client Bedrock ne s'est connecté ; rendu et échanges réseau restent à vérifier. Voir TEST-RESULTS.txt. Les outils de simulation restent exclus du JAR de production.
