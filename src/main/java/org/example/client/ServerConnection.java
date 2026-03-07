package org.example.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;


public class ServerConnection {

    private static final Logger journal = LoggerFactory.getLogger(ServerConnection.class);

    private static final String HOTE = "localhost";
    private static final int    PORT = 5000;

    private Socket socket;
    private PrintWriter sortie;
    private Consumer<JsonObject> ecouteurMessages;
    private Consumer<String>     ecouteurErreurs;


    private volatile boolean deconnexionVolontaire = false;

    public ServerConnection(Consumer<JsonObject> ecouteurMessages, Consumer<String> ecouteurErreurs) {
        this.ecouteurMessages = ecouteurMessages;
        this.ecouteurErreurs  = ecouteurErreurs;
    }

    public boolean connecter() {
        try {
            deconnexionVolontaire = false;
            socket = new Socket(HOTE, PORT);
            sortie = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);


            Thread lecteur = new Thread(this::bouctureLecture);
            lecteur.setDaemon(true);
            lecteur.start();
            return true;
        } catch (IOException e) {
            journal.error("Impossible de se connecter au serveur : {}", e.getMessage());
            return false;
        }
    }

    private void bouctureLecture() {
        try (BufferedReader entree = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String ligne;
            while ((ligne = entree.readLine()) != null) {
                final JsonObject json = JsonParser.parseString(ligne).getAsJsonObject();
                Platform.runLater(() -> ecouteurMessages.accept(json));
            }
        } catch (IOException e) {
            journal.warn("Connexion au serveur perdue : {}", e.getMessage());

            if (!deconnexionVolontaire) {
                Platform.runLater(() -> ecouteurErreurs.accept(
                        "Connexion au serveur perdue. Vous êtes hors ligne."));
            }
        }
    }

    public void envoyer(JsonObject obj) {
        if (sortie != null) sortie.println(obj.toString());
    }


    public void setMessageListener(Consumer<JsonObject> ecouteur) {
        this.ecouteurMessages = ecouteur;
    }

    public void setErrorListener(Consumer<String> ecouteur) {
        this.ecouteurErreurs = ecouteur;
    }

    public void deconnecter() {
        deconnexionVolontaire = true;
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignore) {}
    }



    public void sendLogin(String nomUtilisateur, String motDePasse) {
        JsonObject requete = new JsonObject();
        requete.addProperty("type", "LOGIN");
        requete.addProperty("username", nomUtilisateur);
        requete.addProperty("password", motDePasse);
        envoyer(requete);
    }

    public void sendRegister(String nomUtilisateur, String motDePasse, String photoBase64) {
        JsonObject requete = new JsonObject();
        requete.addProperty("type", "REGISTER");
        requete.addProperty("username", nomUtilisateur);
        requete.addProperty("password", motDePasse);
        if (photoBase64 != null && !photoBase64.isEmpty()) {
            requete.addProperty("photoProfil", photoBase64);
        }
        envoyer(requete);
    }

    public void sendMessage(String destinataire, String contenu) {
        JsonObject requete = new JsonObject();
        requete.addProperty("type", "SEND_MESSAGE");
        requete.addProperty("receiver", destinataire);
        requete.addProperty("content", contenu);
        envoyer(requete);
    }

    public void requestHistory(String autreUtilisateur) {
        JsonObject requete = new JsonObject();
        requete.addProperty("type", "GET_HISTORY");
        requete.addProperty("otherUser", autreUtilisateur);
        envoyer(requete);
    }

    public void requestUserList() {
        JsonObject requete = new JsonObject();
        requete.addProperty("type", "GET_USERS");
        envoyer(requete);
    }

    public void sendLogout() {
        JsonObject requete = new JsonObject();
        requete.addProperty("type", "LOGOUT");
        envoyer(requete);
        deconnecter();
    }
}