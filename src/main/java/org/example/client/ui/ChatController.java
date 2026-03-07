package org.example.client.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Popup;
import org.example.client.ServerConnection;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ChatController {

    @FXML private Label          labelUtilisateurCourant;
    @FXML private Label          labelDiscussionAvec;
    @FXML private Label          labelStatutDiscussion;
    @FXML private StackPane      avatarContact;
    @FXML private StackPane      avatarMoi;
    @FXML private Button         boutonTheme;
    @FXML private Button         boutonEmoji;
    @FXML private ListView<HBox> listeVueMessages;
    @FXML private ListView<HBox> listeVueUtilisateurs;
    @FXML private TextField      champMessage;
    @FXML private TextField      champRecherche;

    private String           utilisateurCourant;
    private String           utilisateurSelectionne;
    private ServerConnection connexion;
    private boolean          modeSombre = false;
    private Popup            emojiPopup = null;

    private final Map<Long, Label>    labelsMetaEnvoyes   = new HashMap<>();
    private final Map<String, String> photosUtilisateurs  = new HashMap<>();
    private final List<JsonObject>    tousLesUtilisateurs = new ArrayList<>();

    // ── Emojis disponibles ──────────────────────────────────────────────────
    private static final String[] EMOJIS = {
        "😊","😂","😍","🤩","😎","🥰","😘","😜","🤣","😁",
        "😢","😭","😤","😡","🤔","🤗","🥺","😴","🤯","😇",
        "❤️","💕","💯","🎉","🔥","👍","👎","👏","🙌","🎊",
        "😆","🤝","💪","🤞","✌️","👀","💀","🙈","😏","🫡",
        "🍕","🎵","⭐","🌙","☀️","🌈","🦋","🐶","🐱","🍀"
    };

    // ── Initialisation ─────────────────────────────────────────────────────

    public void initialiser(String nomUtilisateur, ServerConnection conn) {
        this.utilisateurCourant = nomUtilisateur;
        this.connexion          = conn;
        labelUtilisateurCourant.setText(nomUtilisateur);
        conn.setMessageListener(this::surReceptionMessage);
        conn.setErrorListener(this::surErreur);

        listeVueUtilisateurs.setOnMouseClicked(e -> {
            HBox selection = listeVueUtilisateurs.getSelectionModel().getSelectedItem();
            if (selection != null) {
                String nomU = (String) selection.getUserData();
                if (nomU != null && !nomU.equals(utilisateurCourant)) ouvrirConversation(nomU);
            }
        });

        champMessage.setOnAction(e -> gererEnvoi());
        champRecherche.textProperty().addListener((obs, ancien, nouveau) ->
                filtrerUtilisateurs(nouveau));

        conn.requestUserList();
    }

    // ── Thème sombre / clair ────────────────────────────────────────────────

    @FXML
    private void basculerTheme() {
        java.net.URL url = getClass().getResource("/css/dark.css");
        if (url == null) {
            System.err.println("[Theme] dark.css introuvable dans le classpath !");
            return;
        }
        String darkCss = url.toExternalForm();
        modeSombre = !modeSombre;

        /*
         * IMPORTANT : on ajoute dark.css dans les stylesheets du NŒUD RACINE
         * (même niveau que style.css chargé via FXML). Si on l'ajoutait dans
         * scene.getStylesheets(), les feuilles du nœud auraient une priorité
         * supérieure et écraserait dark.css → les backgrounds ne changeraient pas.
         */
        Parent root = champMessage.getScene().getRoot();
        if (modeSombre) {
            if (!root.getStylesheets().contains(darkCss))
                root.getStylesheets().add(darkCss);
            boutonTheme.setText("☀️");
        } else {
            root.getStylesheets().remove(darkCss);
            boutonTheme.setText("🌙");
        }
        // Fermer le popup emoji pour le recréer avec le bon thème
        if (emojiPopup != null && emojiPopup.isShowing()) emojiPopup.hide();
        emojiPopup = null;

        // Reconstruire la liste pour que les lignes Java adoptent les bonnes couleurs
        filtrerUtilisateurs(champRecherche != null ? champRecherche.getText() : "");
    }

    // ── Emoji picker ────────────────────────────────────────────────────────

    @FXML
    private void afficherEmojiPicker() {
        if (emojiPopup != null && emojiPopup.isShowing()) {
            emojiPopup.hide();
            return;
        }
        emojiPopup = construireEmojiPopup();
        javafx.geometry.Bounds b = boutonEmoji.localToScreen(boutonEmoji.getBoundsInLocal());
        // Afficher au-dessus du bouton (la hauteur ~270px est estimée)
        emojiPopup.show(boutonEmoji, b.getMinX() - 10, b.getMinY() - 276);
    }

    private Popup construireEmojiPopup() {
        FlowPane grille = new FlowPane(3, 3);
        grille.setPrefWrapLength(290);
        grille.setPadding(new Insets(10));
        grille.setStyle(modeSombre
                ? "-fx-background-color: #233138; -fx-background-radius: 14;"
                  + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 18, 0, 0, 6);"
                : "-fx-background-color: white; -fx-background-radius: 14;"
                  + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 18, 0, 0, 6);");

        String hoverColor   = modeSombre ? "#2A3942" : "#E8F5E9";
        String normalBg     = "transparent";

        for (String emoji : EMOJIS) {
            Button btn = new Button(emoji);
            btn.setStyle(
                "-fx-background-color: " + normalBg + ";"
                + " -fx-font-size: 21px; -fx-cursor: hand;"
                + " -fx-border-color: transparent; -fx-padding: 4 5;"
                + " -fx-background-radius: 8;");
            btn.setOnMouseEntered(e ->
                btn.setStyle("-fx-background-color: " + hoverColor + ";"
                    + " -fx-font-size: 21px; -fx-cursor: hand;"
                    + " -fx-border-color: transparent; -fx-padding: 4 5;"
                    + " -fx-background-radius: 8;"));
            btn.setOnMouseExited(e ->
                btn.setStyle("-fx-background-color: " + normalBg + ";"
                    + " -fx-font-size: 21px; -fx-cursor: hand;"
                    + " -fx-border-color: transparent; -fx-padding: 4 5;"
                    + " -fx-background-radius: 8;"));
            btn.setOnAction(e -> {
                champMessage.appendText(emoji);
                champMessage.requestFocus();
                emojiPopup.hide();
            });
            grille.getChildren().add(btn);
        }

        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.getContent().add(grille);
        return popup;
    }

    // ── Conversation ────────────────────────────────────────────────────────

    private void ouvrirConversation(String nomU) {
        utilisateurSelectionne = nomU;
        labelDiscussionAvec.setText(nomU);
        labelStatutDiscussion.setText("chargement...");
        listeVueMessages.getItems().clear();
        labelsMetaEnvoyes.clear();
        avatarContact.getChildren().setAll(construireAvatarCercle(nomU, 20));
        connexion.requestHistory(nomU);
    }

    @FXML
    private void gererEnvoi() {
        if (utilisateurSelectionne == null) { afficherAlerte("Sélectionnez un utilisateur."); return; }
        String texte = champMessage.getText().trim();
        if (texte.isEmpty()) return;
        if (texte.length() > 1000) { afficherAlerte("Maximum 1000 caractères."); return; }
        connexion.sendMessage(utilisateurSelectionne, texte);
        champMessage.clear();
    }

    // ── Réception messages serveur ──────────────────────────────────────────

    private void surReceptionMessage(JsonObject json) {
        String type = json.get("type").getAsString();
        switch (type) {
            case "USER_LIST"         -> gererListeUtilisateurs(json);
            case "MESSAGE"           -> gererMessageEntrant(json);
            case "MESSAGE_SENT"      -> gererConfirmationEnvoi(json);
            case "HISTORY"           -> gererHistorique(json);
            case "MESSAGE_DELIVERED" -> gererMessageLivre(json);
            case "ERROR"             -> afficherAlerte(json.get("message").getAsString());
        }
    }

    // ── Liste utilisateurs ──────────────────────────────────────────────────

    private void gererListeUtilisateurs(JsonObject json) {
        tousLesUtilisateurs.clear();
        photosUtilisateurs.clear();
        JsonArray utilisateurs = json.getAsJsonArray("users");
        for (int i = 0; i < utilisateurs.size(); i++) {
            JsonObject u = utilisateurs.get(i).getAsJsonObject();
            tousLesUtilisateurs.add(u);
            String photo = u.has("photoProfil") ? u.get("photoProfil").getAsString() : "";
            if (!photo.isEmpty()) {
                photosUtilisateurs.put(u.get("username").getAsString(), photo);
            }
            if (u.get("username").getAsString().equals(utilisateurCourant) && avatarMoi != null) {
                StackPane avatar = construireAvatarCercle(utilisateurCourant, 20);
                avatarMoi.getChildren().setAll(avatar);
                avatarMoi.setMinWidth(40);
                avatarMoi.setMinHeight(40);
            }
        }
        filtrerUtilisateurs(champRecherche != null ? champRecherche.getText() : "");
    }

    private void filtrerUtilisateurs(String texte) {
        String filtre = texte == null ? "" : texte.trim().toLowerCase();
        List<JsonObject> filtres = tousLesUtilisateurs.stream()
                .filter(u -> u.get("username").getAsString().toLowerCase().contains(filtre))
                .collect(Collectors.toList());
        afficherUtilisateurs(filtres);
    }

    private void afficherUtilisateurs(List<JsonObject> liste) {
        listeVueUtilisateurs.getItems().clear();
        for (JsonObject u : liste) {
            String nomU        = u.get("username").getAsString();
            boolean estEnLigne = "EN_LIGNE".equals(u.get("status").getAsString());
            boolean cEstMoi    = nomU.equals(utilisateurCourant);
            boolean estSelec   = nomU.equals(utilisateurSelectionne);

            HBox ligne = new HBox(12);
            ligne.setAlignment(Pos.CENTER_LEFT);
            ligne.setPadding(new Insets(10, 16, 10, 14));
            ligne.setUserData(nomU);

            String couleurSelec = modeSombre ? "#182229" : "#E8F5E9";
            if (estSelec) {
                ligne.setStyle("-fx-background-color: " + couleurSelec + "; -fx-cursor: hand;"
                        + " -fx-border-color: #00A884 transparent transparent transparent;"
                        + " -fx-border-width: 0 0 0 3;");
            } else {
                ligne.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
            }

            VBox infos = new VBox(2);
            HBox.setHgrow(infos, javafx.scene.layout.Priority.ALWAYS);

            String couleurNom = modeSombre ? "#E9EDEF" : "#111B21";
            Label labelNom = new Label(nomU + (cEstMoi ? "  (moi)" : ""));
            labelNom.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + couleurNom + ";"
                    + (cEstMoi ? " -fx-font-style: italic;" : ""));

            Label labelStatut = new Label(estEnLigne ? "En ligne" : "Hors ligne");
            labelStatut.setStyle("-fx-font-size: 11px; -fx-text-fill: "
                    + (estEnLigne ? "#00A884;" : "#8696A0;"));

            infos.getChildren().addAll(labelNom, labelStatut);

            Circle point = new Circle(4);
            point.setFill(Color.web(estEnLigne ? "#25D366" : "#B0BEC5"));

            ligne.getChildren().addAll(construireAvatarCercle(nomU, 20), infos, point);
            listeVueUtilisateurs.getItems().add(ligne);

            if (estSelec) labelStatutDiscussion.setText(estEnLigne ? "En ligne" : "Hors ligne");
        }
    }

    // ── Messages ────────────────────────────────────────────────────────────

    private void gererMessageEntrant(JsonObject json) {
        String expediteur = json.get("sender").getAsString();
        if (expediteur.equals(utilisateurSelectionne))
            ajouterMessage(expediteur,
                    json.get("content").getAsString(),
                    json.get("dateEnvoi").getAsString(),
                    false, "RECU", null);
    }

    private void gererConfirmationEnvoi(JsonObject json) {
        String statut = json.has("statut") ? json.get("statut").getAsString() : "ENVOYE";
        Long   idMsg  = json.has("id")     ? json.get("id").getAsLong()        : null;
        ajouterMessage(utilisateurCourant,
                json.get("content").getAsString(),
                json.get("dateEnvoi").getAsString(),
                true, statut, idMsg);
    }

    private void gererHistorique(JsonObject json) {
        listeVueMessages.getItems().clear();
        labelsMetaEnvoyes.clear();
        labelStatutDiscussion.setText("");
        JsonArray messages = json.getAsJsonArray("messages");
        for (int i = 0; i < messages.size(); i++) {
            JsonObject m    = messages.get(i).getAsJsonObject();
            boolean cEstMoi = m.get("sender").getAsString().equals(utilisateurCourant);
            String statut   = m.has("statut") ? m.get("statut").getAsString() : "ENVOYE";
            Long   idMsg    = m.has("id")     ? m.get("id").getAsLong()        : null;
            ajouterMessage(m.get("sender").getAsString(),
                    m.get("content").getAsString(),
                    m.get("dateEnvoi").getAsString(),
                    cEstMoi, statut, idMsg);
        }
    }

    private void gererMessageLivre(JsonObject json) {
        if (!json.has("id")) return;
        Long idMsg      = json.get("id").getAsLong();
        Label labelMeta = labelsMetaEnvoyes.get(idMsg);
        if (labelMeta != null) {
            String contenu = labelMeta.getText();
            if (contenu.endsWith(" \u2713") && !contenu.endsWith(" \u2713\u2713")) {
                labelMeta.setText(contenu.replace(" \u2713", " \u2713\u2713"));
                labelMeta.setStyle("-fx-font-size: 10px; -fx-text-fill: #53BDEB; -fx-padding: 1 4 0 4;");
            }
        }
    }

    private void ajouterMessage(String expediteur, String contenu, String date,
                                boolean cEstMoi, String statut, Long idMsg) {
        Label bulle = new Label(contenu);
        bulle.setWrapText(true);
        bulle.setMaxWidth(460);
        bulle.setPadding(new Insets(9, 14, 9, 14));
        bulle.getStyleClass().add(cEstMoi ? "message-envoye" : "message-recu");

        String heureSeule   = date.length() >= 16 ? date.substring(11, 16) : date;
        String coches       = "";
        String couleurCoche = "#8696A0";
        if (cEstMoi) {
            if ("RECU".equals(statut)) {
                coches      = "  \u2713\u2713";
                couleurCoche = "#53BDEB";
            } else {
                coches = "  \u2713";
            }
        }

        Label labelMeta = new Label(heureSeule + coches);
        labelMeta.setStyle("-fx-font-size: 10px; -fx-text-fill: " + couleurCoche + "; -fx-padding: 1 4 0 4;");

        if (cEstMoi && idMsg != null) labelsMetaEnvoyes.put(idMsg, labelMeta);

        VBox boite = new VBox(2, bulle, labelMeta);
        boite.setAlignment(cEstMoi ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        HBox ligne = new HBox();
        ligne.setPadding(new Insets(4, 16, 4, 16));
        ligne.setStyle("-fx-background-color: transparent;");

        if (cEstMoi) {
            ligne.setAlignment(Pos.CENTER_RIGHT);
            ligne.getChildren().add(boite);
        } else {
            ligne.setAlignment(Pos.CENTER_LEFT);
            ligne.setSpacing(8);
            ligne.getChildren().addAll(construireAvatarCercle(expediteur, 14), boite);
        }

        listeVueMessages.getItems().add(ligne);
        listeVueMessages.scrollTo(listeVueMessages.getItems().size() - 1);
    }

    // ── Avatar ──────────────────────────────────────────────────────────────

    private StackPane construireAvatarCercle(String nom, double rayon) {
        String photo = photosUtilisateurs.get(nom);
        if (photo != null && !photo.isEmpty()) {
            try {
                byte[] bytes = Base64.getDecoder().decode(photo);
                Image img = new Image(new ByteArrayInputStream(bytes),
                        rayon * 2, rayon * 2, true, true);
                if (!img.isError()) {
                    ImageView iv = new ImageView(img);
                    iv.setFitWidth(rayon * 2);
                    iv.setFitHeight(rayon * 2);
                    Circle clip = new Circle(rayon, rayon, rayon);
                    iv.setClip(clip);
                    StackPane sp = new StackPane(iv);
                    sp.setMinWidth(rayon * 2);
                    sp.setMinHeight(rayon * 2);
                    return sp;
                }
            } catch (Exception ignored) { }
        }
        StackPane sp = new StackPane();
        sp.setMinWidth(rayon * 2);
        sp.setMinHeight(rayon * 2);
        return sp;
    }

    // ── Utilitaires ─────────────────────────────────────────────────────────

    private void surErreur(String message) { afficherAlerte(message); }

    private void afficherAlerte(String message) {
        Alert alerte = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
        alerte.setHeaderText(null);
        alerte.showAndWait();
    }

    @FXML
    private void gererDeconnexion() {
        connexion.sendLogout();
        MainApp.afficherConnexion();
    }
}
