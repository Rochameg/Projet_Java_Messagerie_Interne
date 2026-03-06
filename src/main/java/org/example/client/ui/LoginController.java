package org.example.client.ui;

import com.google.gson.JsonObject;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.example.client.ServerConnection;

public class LoginController {

    @FXML private TextField     champNomUtilisateur;
    @FXML private PasswordField champMotDePasse;
    @FXML private Label         labelErreur;
    @FXML private Button        boutonConnexion;

    private ServerConnection connexion;

    @FXML
    private void gererConnexion() {
        effacerErreur();
        String nomUtilisateur = champNomUtilisateur.getText().trim();
        String motDePasse     = champMotDePasse.getText();

        if (nomUtilisateur.isEmpty() || motDePasse.isEmpty()) {
            afficherErreur("Veuillez remplir tous les champs.");
            return;
        }

        boutonConnexion.setDisable(true);

        connexion = new ServerConnection(this::surReceptionMessage, this::surErreur);
        if (!connexion.connecter()) {
            afficherErreur("Impossible de se connecter au serveur.\nVérifiez qu'il est démarré.");
            boutonConnexion.setDisable(false);
            return;
        }

        connexion.sendLogin(nomUtilisateur, motDePasse);
    }

    private void surReceptionMessage(JsonObject json) {
        String type = json.get("type").getAsString();
        switch (type) {
            case "LOGIN_SUCCESS" -> {
                String nomU = json.get("username").getAsString();
                MainApp.afficherChat(nomU, connexion);
            }
            case "LOGIN_FAIL" -> {
                afficherErreur(json.get("message").getAsString());
                boutonConnexion.setDisable(false);
                connexion.deconnecter();
            }
            default -> boutonConnexion.setDisable(false);
        }
    }

    private void surErreur(String message) {
        afficherErreur(message);
        boutonConnexion.setDisable(false);
    }

    private void afficherErreur(String message) {
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
    private void allerVersInscription() {
        MainApp.afficherInscription();
    }
}
