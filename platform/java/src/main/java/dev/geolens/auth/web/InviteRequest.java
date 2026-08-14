package dev.geolens.auth.web;

/** Davet isteği — Go {@code InviteMember} body'si. */
public record InviteRequest(String email, String workspaceId, String role) {
}