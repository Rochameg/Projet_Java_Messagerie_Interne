package org.example.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String nomUtilisateur;

    @Column(name = "password", nullable = false)
    private String motDePasse;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Statut statut = Statut.HORS_LIGNE;

    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation = LocalDateTime.now();

    /**
     * Photo de profil en binaire brut (PostgreSQL : bytea).
     *
     * Pourquoi bytea et pas TEXT/base64 ?
     *  - 33 % plus compact en base (pas de surcoût d'encodage base64)
     *  - Hibernate 6 / Jakarta Persistence mappe byte[] ↔ bytea nativement
     *  - Lecture directe depuis la DB sans re-décodage côté serveur
     *  - Pas de risque de corruption de caractères (données purement binaires)
     *
     * Transport réseau (JSON TCP) : on encode/décode en base64 uniquement
     * lors de l'envoi/réception (dans ClientHandler et ChatController).
     */
    @Column(name = "photo_profil", columnDefinition = "bytea")
    private byte[] photoProfil;

    public enum Statut { EN_LIGNE, HORS_LIGNE }

    public User() {}

    public User(String nomUtilisateur, String motDePasse) {
        this.nomUtilisateur = nomUtilisateur;
        this.motDePasse     = motDePasse;
        this.statut         = Statut.HORS_LIGNE;
        this.dateCreation   = LocalDateTime.now();
    }

    public Long getId()                                    { return id; }
    public String getNomUtilisateur()                      { return nomUtilisateur; }
    public void setNomUtilisateur(String nomUtilisateur)   { this.nomUtilisateur = nomUtilisateur; }
    public String getMotDePasse()                          { return motDePasse; }
    public void setMotDePasse(String motDePasse)           { this.motDePasse = motDePasse; }
    public Statut getStatut()                              { return statut; }
    public void setStatut(Statut statut)                   { this.statut = statut; }
    public LocalDateTime getDateCreation()                 { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation){ this.dateCreation = dateCreation; }
    public byte[] getPhotoProfil()                         { return photoProfil; }
    public void setPhotoProfil(byte[] photoProfil)         { this.photoProfil = photoProfil; }
}
