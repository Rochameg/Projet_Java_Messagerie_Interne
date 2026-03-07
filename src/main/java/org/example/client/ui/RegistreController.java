package org.example.client.ui;

import com.google.gson.JsonObject;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.example.client.ServerConnection;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

public class RegistreController {

    @FXML private TextField     champNomUtilisateur;
    @FXML private PasswordField champMotDePasse;
    @FXML private PasswordField champConfirmation;
    @FXML private Label         labelErreur;
    @FXML private Button        boutonInscription;
    @FXML private ImageView     imagePreview;
    @FXML private Label         labelIconePhoto;
    @FXML private Circle        cerclePhoto;

    private ServerConnection connexion;
    private String photoBase64 = null;

    @FXML
    private void choisirPhoto() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Choisir une photo de profil");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );
        File fichier = fc.showOpenDialog(champNomUtilisateur.getScene().getWindow());
        if (fichier == null) return;

        if (fichier.length() > 500_000) {
            afficherErreur("Image trop grande (max 500 Ko).");
            return;
        }

        try {
            byte[] bytes = Files.readAllBytes(fichier.toPath());
            photoBase64 = Base64.getEncoder().encodeToString(bytes);

            Image img = new Image(fichier.toURI().toString(), 80, 80, true, true);
            imagePreview.setImage(img);
            Circle clip = new Circle(40, 40, 40);
            imagePreview.setClip(clip);
            imagePreview.setVisible(true);
            labelIconePhoto.setVisible(false);
            cerclePhoto.setFill(Color.TRANSPARENT);
            effacerErreur();
        } catch (IOException e) {
            afficherErreur("Impossible de lire l'image.");
        }
    }

    @FXML
    private void gererInscription() {
        effacerErreur();
        String nomUtilisateur = champNomUtilisateur.getText().trim();
        String motDePasse     = champMotDePasse.getText();
        String confirmation   = champConfirmation.getText();

        if (nomUtilisateur.isEmpty() || motDePasse.isEmpty() || confirmation.isEmpty()) {
            afficherErreur("Veuillez remplir tous les champs.");
            return;
        }
        if (nomUtilisateur.length() < 3) {
            afficherErreur("Le nom d'utilisateur doit contenir au moins 3 caractères.");
            return;
        }
        if (!motDePasse.equals(confirmation)) {
            afficherErreur("Les mots de passe ne correspondent pas.");
            return;
        }

        boutonInscription.setDisable(true);

        connexion = new ServerConnection(this::surReceptionMessage, this::surErreur);
        if (!connexion.connecter()) {
            afficherErreur("Impossible de se connecter au serveur.");
            boutonInscription.setDisable(false);
            return;
        }

        connexion.sendRegister(nomUtilisateur, motDePasse, photoBase64);
    }

    private void surReceptionMessage(JsonObject json) {
        String type = json.get("type").getAsString();
        switch (type) {
            case "REGISTER_SUCCESS" -> {
                connexion.deconnecter();
                afficherSucces("✓  Compte créé avec succès ! Redirection…");
                PauseTransition pause = new PauseTransition(Duration.seconds(1.5));
                pause.setOnFinished(e -> MainApp.afficherConnexion());
                pause.play();
            }
            case "REGISTER_FAIL" -> {
                afficherErreur(json.get("message").getAsString());
                boutonInscription.setDisable(false);
                connexion.deconnecter();
            }
            default -> boutonInscription.setDisable(false);
        }
    }

    private void surErreur(String message) {
        afficherErreur(message);
        boutonInscription.setDisable(false);
    }

    private void afficherErreur(String message) {
        labelErreur.getStyleClass().removeAll("success-box");
        if (!labelErreur.getStyleClass().contains("error-box"))
            labelErreur.getStyleClass().add("error-box");
        labelErreur.setText(message);
        labelErreur.setVisible(true);
        labelErreur.setManaged(true);
    }

    private void afficherSucces(String message) {
        labelErreur.getStyleClass().removeAll("error-box");
        if (!labelErreur.getStyleClass().contains("success-box"))
            labelErreur.getStyleClass().add("success-box");
        labelErreur.setText(message);
        labelErreur.setVisible(true);
        labelErreur.setManaged(true);
    }

    private void effacerErreur() {
        labelErreur.setText("");
        labelErreur.setVisible(false);
        labelErreur.setManaged(false);
    }

    @FXML
    private void allerVersConnexion() {
        MainApp.afficherConnexion();
    }
}
