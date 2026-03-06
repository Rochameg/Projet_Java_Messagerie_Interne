package org.example.client.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.example.client.ServerConnection;

import java.util.HashMap;
import java.util.Map;

public class ChatController {

    @FXML private Label          labelUtilisateurCourant;
    @FXML private Label          labelDiscussionAvec;
    @FXML private Label          labelStatutDiscussion;
    @FXML private StackPane      avatarContact;
    @FXML private ListView<HBox> listeVueMessages;
    @FXML private ListView<HBox> listeVueUtilisateurs;
    @FXML private TextField      champMessage;

    private String           utilisateurCourant;
    private String           utilisateurSelectionne;
    private ServerConnection connexion;


    private final Map<Long, Label> labelsMetaEnvoyes = new HashMap<>();

    private static final String[] COULEURS_AVATAR = {
            "#F44336", "#E91E63", "#9C27B0", "#673AB7",
            "#3F51B5", "#2196F3", "#009688", "#4CAF50",
            "#FF9800", "#FF5722", "#795548", "#607D8B"
    };

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
        conn.requestUserList();
    }

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

    private void gererListeUtilisateurs(JsonObject json) {
        listeVueUtilisateurs.getItems().clear();
        JsonArray utilisateurs = json.getAsJsonArray("users");
        for (int i = 0; i < utilisateurs.size(); i++) {
            JsonObject u       = utilisateurs.get(i).getAsJsonObject();
            String nomU        = u.get("username").getAsString();
            boolean estEnLigne = "EN_LIGNE".equals(u.get("status").getAsString());
            boolean cEstMoi    = nomU.equals(utilisateurCourant);
            boolean estSelec   = nomU.equals(utilisateurSelectionne);

            HBox ligne = new HBox(12);
            ligne.setAlignment(Pos.CENTER_LEFT);
            ligne.setPadding(new Insets(10, 16, 10, 14));
            ligne.setUserData(nomU);
            if (estSelec) {
                ligne.setStyle("-fx-background-color: #E8F5E9; -fx-cursor: hand;"
                        + " -fx-border-color: #00A884 transparent transparent transparent;"
                        + " -fx-border-width: 0 0 0 3;");
            } else {
                ligne.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
            }

            VBox infos = new VBox(2);
            HBox.setHgrow(infos, javafx.scene.layout.Priority.ALWAYS);

            Label labelNom = new Label(nomU + (cEstMoi ? "  (moi)" : ""));
            labelNom.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #111B21;"
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

    private void gererMessageEntrant(JsonObject json) {
        String expediteur = json.get("sender").getAsString();
        if (expediteur.equals(utilisateurSelectionne))
            ajouterMessage(
                    expediteur,
                    json.get("content").getAsString(),
                    json.get("dateEnvoi").getAsString(),
                    false,
                    "RECU",
                    null
            );
    }

    private void gererConfirmationEnvoi(JsonObject json) {
        // Le serveur indique si le destinataire était en ligne (RECU) ou non (ENVOYE)
        String statut = json.has("statut") ? json.get("statut").getAsString() : "ENVOYE";
        Long   idMsg  = json.has("id")     ? json.get("id").getAsLong()        : null;

        ajouterMessage(
                utilisateurCourant,
                json.get("content").getAsString(),
                json.get("dateEnvoi").getAsString(),
                true,
                statut,
                idMsg
        );
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

            ajouterMessage(
                    m.get("sender").getAsString(),
                    m.get("content").getAsString(),
                    m.get("dateEnvoi").getAsString(),
                    cEstMoi,
                    statut,
                    idMsg
            );
        }
    }


    private void gererMessageLivre(JsonObject json) {
        if (!json.has("id")) return;
        Long idMsg    = json.get("id").getAsLong();
        Label labelMeta = labelsMetaEnvoyes.get(idMsg);
        if (labelMeta != null) {

            String contenuActuel = labelMeta.getText();

            if (contenuActuel.endsWith(" \u2713") && !contenuActuel.endsWith(" \u2713\u2713")) {
                labelMeta.setText(contenuActuel.replace(" \u2713", " \u2713\u2713"));
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
        if (cEstMoi) {
            bulle.setStyle("-fx-background-color: #D9FDD3;"
                    + " -fx-background-radius: 16 2 16 16; -fx-font-size: 13.5px;"
                    + " -fx-text-fill: #111B21;"
                    + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 3, 0, 0, 1);");
        } else {
            bulle.setStyle("-fx-background-color: white;"
                    + " -fx-background-radius: 2 16 16 16; -fx-font-size: 13.5px;"
                    + " -fx-text-fill: #111B21;"
                    + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 3, 0, 0, 1);");
        }

        String heureSeule = date.length() >= 16 ? date.substring(11, 16) : date;


        String coches     = "";
        String couleurCoche = "#8696A0"; // gris par défaut
        if (cEstMoi) {
            if ("RECU".equals(statut)) {
                coches      = "  \u2713\u2713"; // ✓✓ bleu
                couleurCoche = "#53BDEB";
            } else {
                coches = "  \u2713";             // ✓ gris
            }
        }

        Label labelMeta = new Label(heureSeule + coches);
        labelMeta.setStyle("-fx-font-size: 10px; -fx-text-fill: " + couleurCoche + "; -fx-padding: 1 4 0 4;");


        if (cEstMoi && idMsg != null) {
            labelsMetaEnvoyes.put(idMsg, labelMeta);
        }

        VBox boite = new VBox(2, bulle, labelMeta);
        boite.setAlignment(cEstMoi ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        HBox ligne = new HBox();
        ligne.setPadding(new Insets(3, 16, 3, 16));
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

    private StackPane construireAvatarCercle(String nom, double rayon) {
        String couleur = COULEURS_AVATAR[Math.abs(nom.hashCode()) % COULEURS_AVATAR.length];
        Circle cercle  = new Circle(rayon);
        cercle.setFill(Color.web(couleur));
        Label initiale = new Label(nom.substring(0, 1).toUpperCase());
        initiale.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: "
                + (int)(rayon * 0.85) + "px;");
        StackPane sp = new StackPane(cercle, initiale);
        sp.setMinWidth(rayon * 2);
        sp.setMinHeight(rayon * 2);
        return sp;
    }

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