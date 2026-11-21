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

## Recopie du téléphone (Facebook, Instagram, autres applis)

À l’arrêt, ouvrir Yuto sur le téléphone et toucher **Partager l’écran**. Accepter la demande Android à chaque nouvelle session, puis ouvrir l’appli vidéo voulue. Android 14 permet de choisir une seule appli ou tout l’écran. L’image apparaît automatiquement dans Yuto sur la voiture, en conservant les proportions. Les vidéos verticales ont des bandes noires ; tourner le téléphone si la source propose un mode paysage.

- Arrêt depuis le téléphone, la notification persistante ou le menu de la voiture. Le bouton Retour de Yuto quitte aussi la recopie. Choisir YouTube ou une vidéo locale arrête le partage.
- Pas de contrôle tactile à distance des autres applis : piloter la source depuis le téléphone, à l’arrêt. Les commandes YouTube ne contrôlent pas Facebook/Instagram.
- Capture vidéo uniquement, sans enregistrement ni serveur. Le son reste celui de la source, sur la sortie habituelle du téléphone (Android Auto/Bluetooth selon la configuration). Aucun son ni micro capturé par Yuto.
- Les applis avec protection DRM ou fenêtres sécurisées peuvent afficher du noir. La compatibilité de chaque appli ne peut pas être garantie. Le verrouillage du téléphone ou Android peut arrêter le partage ; une nouvelle autorisation est alors nécessaire.
- Le partage de tout l’écran peut montrer des notifications ou des informations privées. Préférer le partage d’une seule appli si Android le propose.
- Le rideau ParkGuard et le voile de nuit restent au-dessus de la recopie. Les réglages ParkGuard existants restent inchangés.
- Le rendu utilise MediaProjection vers une TextureView du même écran virtuel que YouTube. Un seul jeton de consentement et un seul écran de capture par session, avec redimensionnement pour la rotation.
- Latence et fluidité dépendent du téléphone et de la liaison Android Auto. Les tests automatiques vérifient le calcul des proportions, pas la lecture sur une voiture réelle.
