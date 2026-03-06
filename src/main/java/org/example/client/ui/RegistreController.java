package org.example.client.ui;

import com.google.gson.JsonObject;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.util.Duration;
import org.example.client.ServerConnection;

public class RegistreController {

    @FXML private TextField     champNomUtilisateur;
    @FXML private PasswordField champMotDePasse;
    @FXML private PasswordField champConfirmation;
    @FXML private Label         labelErreur;
    @FXML private Button        boutonInscription;

    private ServerConnection connexion;

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

        connexion.sendRegister(nomUtilisateur, motDePasse);
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