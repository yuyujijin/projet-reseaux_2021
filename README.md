# Projet de Programmation Réseaux 2020-2021

## Introduction

> Ce projet a été réalisé par Eugène Valty et Franck Kindia.

## Structure

Le programme contient un Diffuseur, un Gestionnaire et un Client `Java`. Il contient aussi un Diffuseur en `C`.  
*Aucune constante d'ip et de port n'est écrite en dur dans le code, tout est chargé via fichier de config.*

## Compilation

* Un Makefile est présent pour compiler les fichiers `C`. Pour cela, rien de plus simple : executer `make` (à condition que la commande soit installée). `make clean` supprime tout les fichiers produits par le Makefile.
* Pour les fichiers `Java`, un fichier `compile.sh` compile les fichiers en archive `.jar`.
* Enfin, un fichier `clean.sh` permet de supprimer tout le code compilé et executable, `Java` ou `C`.  

## Execution

* Pour les fichiers `C`, `./nom_fichier`.
* Pour les fichiers `Java`, `java -jar nom_archive`.
* Pour les deux :
    - **Diffuseur** : il faut indiquer en premier argument un fichier de settings (quelque uns sont disponible dans `data/`), et en second un fichier de message (même endroit).
    - **Gestionnaire** : comme le diffuseur, un fichier de settings en argument !
    - **Client** : rien à fournir, le shell est interactif :-)

## Extension.s
> Téléchargement de fichier :
* **[LSFI]** permet d'obtenir la liste de tous les fichiers du diffuseur. (contenu dans `data/files`). Le diffuseur répond alors par **[FINB_nbfile]** ou `nbfile` est un entier écrit sur 3 octets paddé avec des zéros. Puis il envoie `nbfile` message de la forme **[FILE filename]** ou `filename` est une chaine de caractère écrite sur 255 octets (taille maximale des noms de fichier sur unix) et fini par un dernier message **[ENDF]**, puis ferme la connexion.
* **[DLFI filename]**, ou `filename` répresente la même chose qu'au dessus, permet de télécharger un fichier de nom `filename` sauvegardé par le diffuseur. Si le fichier
n'existe pas, le diffuseur répond par **[FINF]**. Sinon il répond **[FIOK filesize]** avec `filesize` un entier ecrit sur 7 octets (arbitraire, mais 10^6 B (soit 1MB) nous paraissait raisonnable). Puis le diffuseur envoie `filesize` bits, et fini par envoyer **[ENDL]**, puis ferme la connexion.