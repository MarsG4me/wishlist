package de.marsg.wishlist.wishlist.data.jpa.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "wishes", indexes = {
        @Index(name = "idx_wishes_name", columnList = "name"),
        @Index(name = "idx_wishes_wishlist", columnList = "wishlist_id"),
        @Index(name = "idx_wishes_claimer", columnList = "claimer_id"),
        @Index(name = "idx_wishes_wishlist_name", columnList = "wishlist_id,name")
})
public class Wish {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "wishlist_id", nullable = false, updatable = false)
    private Wishlist wishlist;
    
    @ManyToOne(optional = true)
    @JoinColumn(name = "claimer_id", nullable = true, updatable = true)
    private User claimer;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "expires_at", nullable = true)
    private LocalDateTime expiresAt = null;

    @Column(nullable = false, length = 60)
    private String description = "";

    @Column(name = "is_unlimited")
    private boolean isUnlimited = false;

    
    @Column(name = "is_bought_by_claimer")
    private boolean isBoughtByClaimer = false;

    /*
     * Getters
     */

    public UUID getId() {
        return id;
    }

    public Wishlist getWishlist() {
        return wishlist;
    }

    public String getName() {
        return name;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public String getDescription() {
        return description;
    }

    public boolean isUnlimited() {
        return isUnlimited;
    }

    public boolean isClaimed(){
        return claimer != null;
    }

    public User getClaimer(){
        return claimer;
    }

    public boolean isBoughtByClaimer(){
        return isBoughtByClaimer;
    }

    /*
     * Setters
     */

    public void setWishlist(Wishlist wishlist) {
        this.wishlist = wishlist;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setUnlimited(boolean isUnlimited) {
        this.isUnlimited = isUnlimited;
    }

    public void setClaimer(User claimer){
        this.claimer = claimer;
    }

    public void setIsBoughtByClaimer(boolean bought){
        this.isBoughtByClaimer = bought;
    }

}
