# Yuto

YouTube sur l'écran Android Auto de la voiture, **à l'arrêt par défaut** (une option permet de garder la vidéo visible en roulant, au choix et sous la responsabilité de l'utilisateur).

- Appli Android Auto de catégorie NAVIGATION (même technique que Mapo) : un écran virtuel
  branché sur la Surface de la voiture affiche le site officiel `m.youtube.com` dans une WebView.
- Les touchers de l'écran de la voiture sont rejoués sur la page ; boutons Retour, Accueil,
  Lecture/Pause, Recherche (clavier de la voiture ou vocale, micro du téléphone), défilement
  et recul de 10 s.
- **Verrou de stationnement** (`ParkGuard`) : vitesse de la voiture transmise par Android Auto,
  sinon vitesse GPS du téléphone. La moindre vitesse cache l'image tout de suite ; la vidéo ne
  revient qu'après 10 s d'arrêt continu. Sans mesure fiable, l'image reste cachée. Le son continue.
- Option « Autoriser la vidéo en roulant » sur le téléphone (décochée par défaut, avec
  avertissement) : la vidéo reste visible même en conduisant. Le verrou de stationnement
  reste actif tant qu'elle est décochée.
- **Anti-pubs gratuit** (`AdBlock`) : les requêtes vers les régies pub (doubleclick…) et les pings
  pub de YouTube sont bloquées, et un script injecté retire les emplacements pub du lecteur, cache
  les encarts pub de l'accueil et passe les pubs vidéo restantes en accéléré (« Ignorer » cliqué
  tout seul). Aucun abonnement.
- En passant à une autre appli dans la voiture (Mapo…), la page reste vivante : le son continue.
- Connexion au compte YouTube depuis le téléphone (cookies partagés avec la voiture).

## Installation

1. Télécharger `Yuto.apk` depuis la dernière release et l'installer (KingInstaller).
2. Ouvrir Yuto sur le téléphone, autoriser la position.
3. Android Auto : paramètres développeur, « Sources inconnues » activé.
4. Dans la voiture, ouvrir Yuto depuis la liste des applis.

Créé par Ahmad Ahmad.
