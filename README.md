# Cinq Mille - Le Jeu de Dés

Bienvenue dans le dépôt du jeu "Cinq Mille" ! Ceci est une application Android en cours de développement pour jouer au célèbre jeu de dés du 5000.

## Statut du Projet

Ce projet est actuellement en phase de développement actif. L'objectif principal est de mettre en œuvre les fonctionnalités de base du jeu de Cinq Mille, y compris la logique de score, la gestion des tours, et une interface utilisateur simple.

## À Propos du Jeu

Le 5000 (ou Cinq Mille) est un jeu de dés où les joueurs lancent des dés pour accumuler des points. L'objectif est d'être le premier à atteindre 5000 points. Les points sont marqués en obtenant certaines combinaisons de dés, comme des "1", des "5", des brelans (trois dés identiques), des suites, et plus encore.

## Fonctionnalités Actuelles

*   **Calcul de Score** : La logique de base pour calculer les scores en fonction des lancers de dés est implémentée, incluant la gestion des priorités pour les combinaisons.
*   **Tests Unitaires** : Une suite de tests unitaires valide la logique de calcul de score.

## Fonctionnalités Prévues

*   Interface utilisateur pour lancer les dés et visualiser le score.
*   Gestion des tours de jeu (lancer, garder des dés, relancer).
*   Suivi du score total pour un ou plusieurs joueurs.
*   Mode solo.
*   Mode multijoueur local (sur le même appareil).
*   (Optionnel) Mode multijoueur via Wi-Fi.
*   Sauvegarde des scores et des parties.
*   Et bien plus encore !

## Installation et Lancement (Pour les développeurs)

1.  Clonez ce dépôt : `git clone https://[URL_DE_VOTRE_FUTUR_DEPOT_GIT_ICI]`
2.  Ouvrez le projet avec Android Studio.
3.  Synchronisez le projet avec Gradle.
4.  Lancez l'application sur un émulateur ou un appareil Android.

## Technologies Utilisées

*   Langage : Kotlin
*   Plateforme : Android (Jetpack Compose pour l'UI envisagé)
*   Tests : JUnit
*   Gestion de version : Git

## Règles du jeu (Rappel rapide)

*   **1** = 100 points
*   **5** = 50 points
*   Brelan de **1** (x3) = 1000 points
*   Brelan de **2** (x3) = 200 points
*   Brelan de **3** (x3) = 300 points
*   Brelan de **4** (x3) = 400 points
*   Brelan de **5** (x3) = 500 points
*   Brelan de **6** (x3) = 600 points
*   Suite (1,2,3,4,5) = 500 points
*   Suite (2,3,4,5,6) = 500 points
*   Full (ex: A,A,B,B,B) = `valeurFaciale(A) * valeurFaciale(B) * 100` points
*   Cinq **1** = 5000 points (Gagne la partie immédiatement)
*   Cinq **5** = 5000 points

_Note : Les dés ne comptent qu'une fois pour la combinaison la plus avantageuse. Certaines variantes de règles existent ; celles listées ci-dessus sont celles implémentées dans cette version du jeu._

## Contribution

Les contributions sont les bienvenues ! Veuillez lire `CONTRIBUTING.md` (à créer ultérieurement) pour plus de détails.

## Licence

Ce projet est sous licence MIT. Voir le fichier `LICENSE` pour plus de détails.