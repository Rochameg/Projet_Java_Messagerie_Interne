package org.example.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.dao.MessageDAO;
import org.example.dao.UserDAO;
import org.example.model.Message;
import org.example.model.User;
import org.example.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.*;
import java.net.Socket;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;


public class ClientHandler implements Runnable {

    private static final Logger journal   = LoggerFactory.getLogger(ClientHandler.class);
    private static final DateTimeFormatter FORMATEUR = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Gson GSON = new Gson();

    private final Socket socket;
    private final Map<String, ClientHandler> sessionsActives;
    private final UserDAO    daoUtilisateur = new UserDAO();
    private final MessageDAO daoMessage     = new MessageDAO();

    private PrintWriter sortie;
    private String nomUtilisateur;

    public ClientHandler(Socket socket, Map<String, ClientHandler> sessionsActives) {
        this.socket          = socket;
        this.sessionsActives = sessionsActives;
    }


    @Override
    public void run() {
        try (BufferedReader entree  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter    ecrivain = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)) {

            this.sortie = ecrivain;
            String ligne;
            while ((ligne = entree.readLine()) != null) {
                traiterRequete(ligne.trim());
            }
        } catch (IOException e) {
            journal.warn("Connexion perdue pour {} : {}", nomUtilisateur, e.getMessage());
        } finally {
            deconnecter();
        }
    }


    private void traiterRequete(String json) {
        try {
            JsonObject requete = JsonParser.parseString(json).getAsJsonObject();
            String type = requete.get("type").getAsString();

            switch (type) {
                case "REGISTER"     -> gererInscription(requete);
                case "LOGIN"        -> gererConnexion(requete);
                case "SEND_MESSAGE" -> gererEnvoiMessage(requete);
                case "GET_HISTORY"  -> gererHistorique(requete);
                case "GET_USERS"    -> envoyerListeUtilisateurs();
                case "LOGOUT"       -> deconnecter();
                default             -> envoyerErreur("Type de requête inconnu : " + type);
            }
        } catch (Exception e) {
            envoyerErreur("Erreur serveur : " + e.getMessage());
        }
    }


    private void gererInscription(JsonObject requete) {
        String nomU = requete.get("username").getAsString().trim();
        String mdp  = requete.get("password").getAsString();

        if (nomU.isEmpty() || mdp.isEmpty()) {
            envoyerErreur("Nom d'utilisateur et mot de passe requis.");
            return;
        }
        if (daoUtilisateur.existeParNomUtilisateur(nomU)) {  // RG1
            envoyer(erreur("REGISTER_FAIL", "Ce nom d'utilisateur est déjà utilisé."));
            return;
        }

        User utilisateur = new User(nomU, PasswordUtil.hash(mdp));  // RG9
        daoUtilisateur.sauvegarder(utilisateur);
        journal.info("Nouvel utilisateur inscrit : {}", nomU);
        envoyer(succes("REGISTER_SUCCESS", "Inscription réussie."));
    }

    private void gererConnexion(JsonObject requete) {
        String nomU = requete.get("username").getAsString().trim();
        String mdp  = requete.get("password").getAsString();


        if (sessionsActives.containsKey(nomU)) {
            envoyer(erreur("LOGIN_FAIL", "Cet utilisateur est déjà connecté."));
            return;
        }

        User utilisateur = daoUtilisateur.trouverParNomUtilisateur(nomU).orElse(null);
        if (utilisateur == null || !PasswordUtil.verify(mdp, utilisateur.getMotDePasse())) {
            envoyer(erreur("LOGIN_FAIL", "Identifiants incorrects."));
            return;
        }


        this.nomUtilisateur = nomU;
        daoUtilisateur.mettreAJourStatut(utilisateur.getId(), User.Statut.EN_LIGNE);
        Server.register(nomU, this);
        journal.info("Connexion : {}", nomU);

        JsonObject reponse = new JsonObject();
        reponse.addProperty("type", "LOGIN_SUCCESS");
        reponse.addProperty("username", nomU);
        envoyer(reponse.toString());


        livrerMessagesEnAttente(nomU);


        Server.broadcastUserList();
    }

    private void gererEnvoiMessage(JsonObject requete) {
        if (!estAuthentifie()) return;

        String destinataire = requete.get("receiver").getAsString().trim();
        String contenu      = requete.get("content").getAsString().trim();


        if (contenu.isEmpty()) {
            envoyerErreur("Le contenu du message ne peut pas être vide.");
            return;
        }
        if (contenu.length() > 1000) {
            envoyerErreur("Le message dépasse 1000 caractères.");
            return;
        }


        User expediteur      = daoUtilisateur.trouverParNomUtilisateur(nomUtilisateur).orElse(null);
        User utilisateurDest = daoUtilisateur.trouverParNomUtilisateur(destinataire).orElse(null);
        if (utilisateurDest == null) {
            envoyerErreur("Destinataire introuvable : " + destinataire);
            return;
        }

        Message message = new Message(expediteur, utilisateurDest, contenu);
        daoMessage.sauvegarder(message);
        journal.info("Message de {} à {} : {}", nomUtilisateur, destinataire, contenu);


        JsonObject charge = construireChargeMessage(nomUtilisateur, contenu, message.getDateEnvoi().format(FORMATEUR));


        ClientHandler gestionnaireDestinataire = Server.getHandler(destinataire);
        boolean livreMaintenant = gestionnaireDestinataire != null;
        if (livreMaintenant) {
            gestionnaireDestinataire.envoyer(charge.toString());
            daoMessage.mettreAJourStatut(message.getId(), Message.Statut.RECU);
        }


        JsonObject confirmation = new JsonObject();
        confirmation.addProperty("type",      "MESSAGE_SENT");
        confirmation.addProperty("id",        message.getId());
        confirmation.addProperty("receiver",  destinataire);
        confirmation.addProperty("content",   contenu);
        confirmation.addProperty("dateEnvoi", message.getDateEnvoi().format(FORMATEUR));
        confirmation.addProperty("statut",    livreMaintenant ? "RECU" : "ENVOYE");
        envoyer(confirmation.toString());
    }

    private void gererHistorique(JsonObject requete) {
        if (!estAuthentifie()) return;
        String autreUtilisateur = requete.get("otherUser").getAsString().trim();

        List<Message> messages = daoMessage.trouverConversation(nomUtilisateur, autreUtilisateur);

        JsonObject reponse = new JsonObject();
        reponse.addProperty("type", "HISTORY");
        reponse.addProperty("otherUser", autreUtilisateur);

        com.google.gson.JsonArray tableau = new com.google.gson.JsonArray();
        for (Message m : messages) {
            JsonObject o = new JsonObject();
            o.addProperty("id",        m.getId());
            o.addProperty("sender",    m.getExpediteur().getNomUtilisateur());
            o.addProperty("receiver",  m.getDestinataire().getNomUtilisateur());
            o.addProperty("content",   m.getContenu());
            o.addProperty("dateEnvoi", m.getDateEnvoi().format(FORMATEUR));
            o.addProperty("statut",    m.getStatut().name());
            tableau.add(o);
        }
        reponse.add("messages", tableau);
        envoyer(reponse.toString());
    }


    private void livrerMessagesEnAttente(String nomU) {
        List<Message> enAttente = daoMessage.trouverMessagesEnAttente(nomU);
        for (Message m : enAttente) {

            envoyer(construireChargeMessage(
                    m.getExpediteur().getNomUtilisateur(),
                    m.getContenu(),
                    m.getDateEnvoi().format(FORMATEUR)).toString());


            daoMessage.mettreAJourStatut(m.getId(), Message.Statut.RECU);


            ClientHandler gestionnaireExpediteur = Server.getHandler(m.getExpediteur().getNomUtilisateur());
            if (gestionnaireExpediteur != null) {
                JsonObject livre = new JsonObject();
                livre.addProperty("type", "MESSAGE_DELIVERED");
                livre.addProperty("id",   m.getId());
                gestionnaireExpediteur.envoyer(livre.toString());
            }
        }
        if (!enAttente.isEmpty()) {
            journal.info("{} messages en attente livrés à {}", enAttente.size(), nomU);
        }
    }


    public void envoyerListeUtilisateurs() {
        List<User> tous = daoUtilisateur.trouverTous();
        JsonObject reponse = new JsonObject();
        reponse.addProperty("type", "USER_LIST");

        com.google.gson.JsonArray tableau = new com.google.gson.JsonArray();
        for (User u : tous) {
            JsonObject o = new JsonObject();
            o.addProperty("username", u.getNomUtilisateur());


            boolean estConnecte = Server.getHandler(u.getNomUtilisateur()) != null;
            o.addProperty("status", estConnecte ? "EN_LIGNE" : "HORS_LIGNE");

            tableau.add(o);
        }
        reponse.add("users", tableau);
        envoyer(reponse.toString());
    }

    private void deconnecter() {
        if (nomUtilisateur != null) {
            daoUtilisateur.trouverParNomUtilisateur(nomUtilisateur).ifPresent(u ->
                    daoUtilisateur.mettreAJourStatut(u.getId(), User.Statut.HORS_LIGNE));  // RG4
            Server.unregister(nomUtilisateur);
            journal.info("Déconnexion : {}", nomUtilisateur);  // RG12
            nomUtilisateur = null;
            Server.broadcastUserList();
        }
        try { socket.close(); } catch (IOException ignore) {}
    }

    private boolean estAuthentifie() {
        if (nomUtilisateur == null) {
            envoyerErreur("Non authentifié."); // RG2
            return false;
        }
        return true;
    }

    public synchronized void envoyer(String json) {
        if (sortie != null) sortie.println(json);
    }

    private void envoyerErreur(String message) { envoyer(erreur("ERROR", message)); }

    private static String erreur(String type, String message) {
        JsonObject o = new JsonObject();
        o.addProperty("type", type);
        o.addProperty("message", message);
        return o.toString();
    }

    private static String succes(String type, String message) {
        JsonObject o = new JsonObject();
        o.addProperty("type", type);
        o.addProperty("message", message);
        return o.toString();
    }

    private static JsonObject construireChargeMessage(String expediteur, String contenu, String dateEnvoi) {
        JsonObject o = new JsonObject();
        o.addProperty("type",      "MESSAGE");
        o.addProperty("sender",    expediteur);
        o.addProperty("content",   contenu);
        o.addProperty("dateEnvoi", dateEnvoi);
        return o;
    }
}