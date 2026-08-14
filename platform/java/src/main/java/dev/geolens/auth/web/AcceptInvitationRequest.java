package dev.geolens.auth.web;

/** Davet kabul isteği — Go {@code AcceptInvitation} body'si. */
public record AcceptInvitationRequest(String token, String email, String password, String name) {
}